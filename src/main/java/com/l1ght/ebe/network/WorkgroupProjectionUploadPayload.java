package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.permission.PermissionFeature;
import com.l1ght.ebe.server.permission.PermissionManager;
import com.l1ght.ebe.server.workgroup.Workgroup;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorkgroupProjectionUploadPayload implements CustomPacketPayload {
    private static final Map<UUID, PendingUpload> UPLOADS = new HashMap<>();

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_projection_upload");
    public static final Type<WorkgroupProjectionUploadPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupProjectionUploadPayload> STREAM_CODEC =
            StreamCodec.ofMember(WorkgroupProjectionUploadPayload::write, WorkgroupProjectionUploadPayload::decode);

    private final UUID uploadId;
    private final UUID groupId;
    private final UUID projectionId;
    private final String fileName;
    private final BlockPos origin;
    private final boolean visible;
    private final int total;
    private final int offset;
    private final boolean done;
    private final List<Entry> entries;

    public WorkgroupProjectionUploadPayload(UUID uploadId, UUID groupId, UUID projectionId, String fileName,
                                            BlockPos origin, boolean visible, int total, int offset, boolean done,
                                            List<Entry> entries) {
        this.uploadId = uploadId == null ? UUID.randomUUID() : uploadId;
        this.groupId = groupId == null ? new UUID(0L, 0L) : groupId;
        this.projectionId = projectionId == null ? new UUID(0L, 0L) : projectionId;
        this.fileName = fileName == null ? "" : fileName;
        this.origin = origin == null ? BlockPos.ZERO : origin;
        this.visible = visible;
        this.total = Math.max(0, total);
        this.offset = Math.max(0, offset);
        this.done = done;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(uploadId);
        buf.writeUUID(groupId);
        buf.writeUUID(projectionId);
        buf.writeUtf(NetworkLimits.bounded(fileName, NetworkLimits.MAX_SHORT_TEXT_CHARS), NetworkLimits.MAX_SHORT_TEXT_CHARS);
        buf.writeBlockPos(origin);
        buf.writeBoolean(visible);
        buf.writeVarInt(total);
        buf.writeVarInt(offset);
        buf.writeBoolean(done);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeBlockPos(entry.pos());
            buf.writeVarInt(entry.stateId());
            buf.writeUtf(NetworkLimits.bounded(entry.nbt(), NetworkLimits.MAX_BLOCK_NBT_CHARS), NetworkLimits.MAX_BLOCK_NBT_CHARS);
        }
    }

    public static WorkgroupProjectionUploadPayload decode(RegistryFriendlyByteBuf buf) {
        UUID uploadId = buf.readUUID();
        UUID groupId = buf.readUUID();
        UUID projectionId = buf.readUUID();
        String fileName = buf.readUtf(NetworkLimits.MAX_SHORT_TEXT_CHARS);
        BlockPos origin = buf.readBlockPos();
        boolean visible = buf.readBoolean();
        int total = buf.readVarInt();
        int offset = buf.readVarInt();
        boolean done = buf.readBoolean();
        int rawSize = buf.readVarInt();
        if (rawSize < 0 || rawSize > NetworkLimits.MAX_WORKGROUP_UPLOAD_BATCH) {
            throw new IllegalArgumentException("Invalid workgroup projection upload batch size: " + rawSize);
        }
        List<Entry> entries = new ArrayList<>(rawSize);
        long nbtChars = 0L;
        for (int i = 0; i < rawSize; i++) {
            var pos = buf.readBlockPos();
            int stateId = buf.readVarInt();
            var nbt = buf.readUtf(NetworkLimits.MAX_BLOCK_NBT_CHARS);
            nbtChars += nbt.length();
            if (nbtChars > NetworkLimits.MAX_BATCH_NBT_CHARS) {
                throw new IllegalArgumentException("Workgroup projection upload NBT payload is too large");
            }
            entries.add(new Entry(pos, stateId, nbt));
        }
        return new WorkgroupProjectionUploadPayload(uploadId, groupId, projectionId, fileName, origin, visible,
                total, offset, done, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WorkgroupProjectionUploadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!PermissionManager.canUse(player, PermissionFeature.COLLABORATE)) return;
            Workgroup group = WorkgroupManager.groupFor(player);
            if (group == null || !group.id.equals(payload.groupId)) return;
            if (payload.total <= 0 || payload.total > NetworkLimits.MAX_WORKGROUP_UPLOAD_TOTAL
                    || payload.entries.size() > NetworkLimits.MAX_WORKGROUP_UPLOAD_BATCH) {
                UPLOADS.remove(payload.uploadId);
                return;
            }

            PendingUpload upload = UPLOADS.compute(payload.uploadId, (ignored, existing) -> {
                if (existing == null || payload.offset == 0 || !existing.ownerId.equals(player.getUUID())) {
                    return new PendingUpload(player.getUUID(), group.id, payload.projectionId, payload.fileName,
                            payload.origin, payload.visible, payload.total);
                }
                return existing;
            });

            if (upload == null || !upload.groupId.equals(group.id) || !upload.ownerId.equals(player.getUUID())) return;
            if (payload.offset != upload.entries.size()) {
                UPLOADS.remove(payload.uploadId);
                return;
            }
            for (Entry entry : payload.entries) {
                upload.entries.add(new WorkgroupProjectionStore.StoredBlock(entry.pos().immutable(), entry.stateId(), entry.nbt()));
            }

            if (payload.done && upload.entries.size() != upload.total) {
                UPLOADS.remove(payload.uploadId);
                return;
            }

            if (payload.done || upload.entries.size() >= upload.total) {
                UPLOADS.remove(payload.uploadId);
                WorkgroupManager.upsertProjection(group.id, new Workgroup.ProjectionState(
                        upload.projectionId,
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        upload.fileName,
                        upload.origin.getX(),
                        upload.origin.getY(),
                        upload.origin.getZ(),
                        upload.visible,
                        System.currentTimeMillis()
                ));
                WorkgroupProjectionStore.store(group.id, upload.projectionId, upload.fileName, upload.origin,
                        upload.visible, upload.entries);
                WorkgroupNetworkSync.syncGroup(player.server, group.id);
                WorkgroupProjectionDownloadPayload.broadcast(player.server, group.id, upload.projectionId, player.getUUID());
            }
        });
    }

    public record Entry(BlockPos pos, int stateId, String nbt) {
        public Entry {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            nbt = nbt == null ? "" : nbt;
        }
    }

    private static class PendingUpload {
        final UUID ownerId;
        final UUID groupId;
        final UUID projectionId;
        final String fileName;
        final BlockPos origin;
        final boolean visible;
        final int total;
        final List<WorkgroupProjectionStore.StoredBlock> entries = new ArrayList<>();

        PendingUpload(UUID ownerId, UUID groupId, UUID projectionId, String fileName, BlockPos origin,
                      boolean visible, int total) {
            this.ownerId = ownerId;
            this.groupId = groupId;
            this.projectionId = projectionId;
            this.fileName = fileName == null ? "" : fileName;
            this.origin = origin == null ? BlockPos.ZERO : origin.immutable();
            this.visible = visible;
            this.total = total;
        }
    }
}
