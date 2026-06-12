package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.workgroup.Workgroup;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WorkgroupProjectionDownloadPayload implements CustomPacketPayload {
    private static final int BATCH_SIZE = 512;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_projection_download");
    public static final Type<WorkgroupProjectionDownloadPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupProjectionDownloadPayload> STREAM_CODEC =
            StreamCodec.ofMember(WorkgroupProjectionDownloadPayload::write, WorkgroupProjectionDownloadPayload::decode);

    private final UUID projectionId;
    private final String fileName;
    private final BlockPos origin;
    private final boolean visible;
    private final int total;
    private final int offset;
    private final boolean done;
    private final List<Entry> entries;

    public WorkgroupProjectionDownloadPayload(UUID projectionId, String fileName, BlockPos origin, boolean visible,
                                              int total, int offset, boolean done, List<Entry> entries) {
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

    public static WorkgroupProjectionDownloadPayload decode(RegistryFriendlyByteBuf buf) {
        UUID projectionId = buf.readUUID();
        String fileName = buf.readUtf(NetworkLimits.MAX_SHORT_TEXT_CHARS);
        BlockPos origin = buf.readBlockPos();
        boolean visible = buf.readBoolean();
        int total = buf.readVarInt();
        int offset = buf.readVarInt();
        boolean done = buf.readBoolean();
        int rawSize = buf.readVarInt();
        if (rawSize < 0 || rawSize > NetworkLimits.MAX_WORKGROUP_UPLOAD_BATCH) {
            throw new IllegalArgumentException("Invalid workgroup projection download batch size: " + rawSize);
        }
        List<Entry> entries = new ArrayList<>(rawSize);
        long nbtChars = 0L;
        for (int i = 0; i < rawSize; i++) {
            var pos = buf.readBlockPos();
            int stateId = buf.readVarInt();
            var nbt = buf.readUtf(NetworkLimits.MAX_BLOCK_NBT_CHARS);
            nbtChars += nbt.length();
            if (nbtChars > NetworkLimits.MAX_BATCH_NBT_CHARS) {
                throw new IllegalArgumentException("Workgroup projection download NBT payload is too large");
            }
            entries.add(new Entry(pos, stateId, nbt));
        }
        return new WorkgroupProjectionDownloadPayload(projectionId, fileName, origin, visible, total, offset, done, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void broadcast(MinecraftServer server, UUID groupId, UUID projectionId) {
        broadcast(server, groupId, projectionId, null);
    }

    public static void broadcast(MinecraftServer server, UUID groupId, UUID projectionId, UUID excludePlayerId) {
        WorkgroupProjectionStore.StoredProjection stored = WorkgroupProjectionStore.get(groupId, projectionId);
        if (stored == null) return;
        Workgroup group = WorkgroupManager.allGroups().stream().filter(g -> g.id.equals(groupId)).findFirst().orElse(null);
        if (group == null) return;

        List<WorkgroupProjectionDownloadPayload> packets = buildPackets(stored);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!group.members.containsKey(player.getUUID())) continue;
            // The uploader already has this projection locally; re-sending it would force an
            // unnecessary remove/reload on their client (the source of the "projection vanishes
            // after sync" bug). Skip them.
            if (excludePlayerId != null && player.getUUID().equals(excludePlayerId)) continue;
            for (WorkgroupProjectionDownloadPayload packet : packets) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }

    private static List<WorkgroupProjectionDownloadPayload> buildPackets(WorkgroupProjectionStore.StoredProjection stored) {
        List<WorkgroupProjectionDownloadPayload> packets = new ArrayList<>();
        List<WorkgroupProjectionStore.StoredBlock> blocks = stored.blocks();
        int total = blocks.size();
        if (total == 0) {
            packets.add(new WorkgroupProjectionDownloadPayload(stored.projectionId(), stored.fileName(),
                    stored.origin(), stored.visible(), 0, 0, true, List.of()));
            return packets;
        }
        int start = 0;
        while (start < total) {
            int end = Math.min(total, start + BATCH_SIZE);
            List<Entry> entries = new ArrayList<>(end - start);
            for (int i = start; i < end; i++) {
                var b = blocks.get(i);
                entries.add(new Entry(b.pos(), b.stateId(), b.nbt()));
            }
            boolean done = end >= total;
            packets.add(new WorkgroupProjectionDownloadPayload(stored.projectionId(), stored.fileName(),
                    stored.origin(), stored.visible(), total, start, done, entries));
            start = end;
        }
        return packets;
    }

    public static void handleClient(WorkgroupProjectionDownloadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) return;
            com.l1ght.ebe.client.projection.WorkgroupProjectionReceiver.accept(
                    payload.projectionId, payload.fileName, payload.origin, payload.visible,
                    payload.total, payload.offset, payload.done, payload.entries);
        });
    }

    public record Entry(BlockPos pos, int stateId, String nbt) {
        public Entry {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            nbt = nbt == null ? "" : nbt;
        }
    }
}
