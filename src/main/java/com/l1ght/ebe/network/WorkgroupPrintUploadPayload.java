package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.permission.PermissionFeature;
import com.l1ght.ebe.server.permission.PermissionManager;
import com.l1ght.ebe.server.workgroup.Workgroup;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import com.l1ght.ebe.server.workgroup.print.WorkgroupPrintPlanner;
import com.l1ght.ebe.server.workgroup.print.WorkgroupPrintSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorkgroupPrintUploadPayload implements CustomPacketPayload {
    private static final int MAX_BATCH_ENTRIES = 1024;
    private static final int MAX_TOTAL_ENTRIES = 1_000_000;
    private static final Map<UUID, PendingUpload> UPLOADS = new HashMap<>();

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_print_upload");
    public static final Type<WorkgroupPrintUploadPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupPrintUploadPayload> STREAM_CODEC =
            StreamCodec.ofMember(WorkgroupPrintUploadPayload::write, WorkgroupPrintUploadPayload::decode);

    private final UUID uploadId;
    private final String fileName;
    private final int total;
    private final int offset;
    private final boolean done;
    private final List<Entry> entries;

    public WorkgroupPrintUploadPayload(UUID uploadId, String fileName, int total, int offset, boolean done, List<Entry> entries) {
        this.uploadId = uploadId == null ? UUID.randomUUID() : uploadId;
        this.fileName = fileName == null ? "" : fileName;
        this.total = Math.max(0, total);
        this.offset = Math.max(0, offset);
        this.done = done;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(uploadId);
        buf.writeUtf(fileName, 255);
        buf.writeVarInt(total);
        buf.writeVarInt(offset);
        buf.writeBoolean(done);
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeBlockPos(entry.pos());
            buf.writeVarInt(entry.stateId());
            buf.writeUtf(entry.nbt(), 32767);
        }
    }

    public static WorkgroupPrintUploadPayload decode(RegistryFriendlyByteBuf buf) {
        UUID uploadId = buf.readUUID();
        String fileName = buf.readUtf(255);
        int total = buf.readVarInt();
        int offset = buf.readVarInt();
        boolean done = buf.readBoolean();
        int rawSize = buf.readVarInt();
        if (rawSize < 0 || rawSize > MAX_BATCH_ENTRIES) {
            throw new IllegalArgumentException("Invalid workgroup print upload batch size: " + rawSize);
        }
        int size = rawSize;
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(32767)));
        }
        return new WorkgroupPrintUploadPayload(uploadId, fileName, total, offset, done, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WorkgroupPrintUploadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!PermissionManager.canUse(player, PermissionFeature.PRINTER)
                    || !PermissionManager.canUse(player, PermissionFeature.COLLABORATE)) {
                return;
            }
            Workgroup group = WorkgroupManager.groupFor(player);
            if (group == null || payload.total <= 0 || payload.total > MAX_TOTAL_ENTRIES || payload.entries.size() > MAX_BATCH_ENTRIES) {
                return;
            }

            PendingUpload upload = UPLOADS.compute(payload.uploadId, (ignored, existing) -> {
                if (existing == null || payload.offset == 0 || !existing.ownerId.equals(player.getUUID())) {
                    return new PendingUpload(player.getUUID(), group.id, payload.fileName, payload.total,
                            dimensionKey(player.level()));
                }
                return existing;
            });

            if (upload == null || !upload.groupId.equals(group.id) || !upload.ownerId.equals(player.getUUID())) return;
            if (payload.offset != upload.entries.size()) {
                UPLOADS.remove(payload.uploadId);
                return;
            }
            for (Entry entry : payload.entries) {
                upload.entries.add(new WorkgroupPrintPlanner.RawPrintEntry(entry.pos().immutable(), entry.stateId(), entry.nbt()));
            }

            if (payload.done && upload.entries.size() != upload.total) {
                UPLOADS.remove(payload.uploadId);
                return;
            }

            if (payload.done || upload.entries.size() >= upload.total) {
                UPLOADS.remove(payload.uploadId);
                var server = player.server;
                var ownerId = player.getUUID();
                var ownerName = player.getGameProfile().getName();
                var groupId = group.id;
                var fileName = upload.fileName;
                var dimension = upload.dimension;
                WorkgroupPrintPlanner.buildTargetsAsync(upload.entries).whenComplete((targets, error) -> {
                    if (error != null) {
                        EBEMod.LOGGER.warn("Failed to plan workgroup print upload {}", payload.uploadId, error);
                        return;
                    }
                    server.execute(() -> {
                        Workgroup currentGroup = WorkgroupManager.groupFor(player);
                        if (currentGroup == null || !currentGroup.id.equals(groupId)) return;
                        WorkgroupPrintSessionManager.startSession(groupId, ownerId, ownerName, fileName, dimension, targets);
                        WorkgroupNetworkSync.syncGroup(server, groupId);
                    });
                });
            }
        });
    }

    private static String dimensionKey(Level level) {
        return level.dimension().location().toString();
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
        final String fileName;
        final int total;
        final String dimension;
        final List<WorkgroupPrintPlanner.RawPrintEntry> entries = new ArrayList<>();

        PendingUpload(UUID ownerId, UUID groupId, String fileName, int total, String dimension) {
            this.ownerId = ownerId;
            this.groupId = groupId;
            this.fileName = fileName == null ? "" : fileName;
            this.total = total;
            this.dimension = dimension == null ? "" : dimension;
        }
    }
}
