package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.workgroup.Workgroup;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class WorkgroupProjectionPayload implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_projection");
    public static final Type<WorkgroupProjectionPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupProjectionPayload> STREAM_CODEC = StreamCodec.ofMember(WorkgroupProjectionPayload::write, WorkgroupProjectionPayload::decode);

    private final String action;
    private final UUID groupId;
    private final UUID projectionId;
    private final String fileName;
    private final BlockPos origin;
    private final boolean visible;

    public WorkgroupProjectionPayload(String action, UUID groupId, UUID projectionId, String fileName, BlockPos origin, boolean visible) {
        this.action = action == null ? "update" : action;
        this.groupId = groupId;
        this.projectionId = projectionId;
        this.fileName = fileName == null ? "" : fileName;
        this.origin = origin == null ? BlockPos.ZERO : origin;
        this.visible = visible;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(action);
        buf.writeUUID(groupId);
        buf.writeUUID(projectionId);
        buf.writeUtf(fileName);
        buf.writeBlockPos(origin);
        buf.writeBoolean(visible);
    }

    public static WorkgroupProjectionPayload decode(RegistryFriendlyByteBuf buf) {
        return new WorkgroupProjectionPayload(buf.readUtf(), buf.readUUID(), buf.readUUID(), buf.readUtf(), buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WorkgroupProjectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if ("remove".equalsIgnoreCase(payload.action)) {
                WorkgroupManager.removeProjection(payload.groupId, payload.projectionId);
            } else {
                WorkgroupManager.upsertProjection(payload.groupId, new Workgroup.ProjectionState(
                        payload.projectionId,
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        payload.fileName,
                        payload.origin.getX(),
                        payload.origin.getY(),
                        payload.origin.getZ(),
                        payload.visible,
                        System.currentTimeMillis()
                ));
            }
            PacketDistributor.sendToPlayer(player, new WorkgroupSyncPayload(WorkgroupManager.toClientJson(player)));
        });
    }
}
