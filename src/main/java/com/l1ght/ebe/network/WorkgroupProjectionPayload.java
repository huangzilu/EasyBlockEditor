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
        this.groupId = groupId == null ? new UUID(0L, 0L) : groupId;
        this.projectionId = projectionId == null ? new UUID(0L, 0L) : projectionId;
        this.fileName = fileName == null ? "" : fileName;
        this.origin = origin == null ? BlockPos.ZERO : origin;
        this.visible = visible;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(NetworkLimits.bounded(action, NetworkLimits.MAX_ACTION_CHARS), NetworkLimits.MAX_ACTION_CHARS);
        buf.writeUUID(groupId);
        buf.writeUUID(projectionId);
        buf.writeUtf(NetworkLimits.bounded(fileName, NetworkLimits.MAX_SHORT_TEXT_CHARS), NetworkLimits.MAX_SHORT_TEXT_CHARS);
        buf.writeBlockPos(origin);
        buf.writeBoolean(visible);
    }

    public static WorkgroupProjectionPayload decode(RegistryFriendlyByteBuf buf) {
        return new WorkgroupProjectionPayload(buf.readUtf(NetworkLimits.MAX_ACTION_CHARS), buf.readUUID(), buf.readUUID(),
                buf.readUtf(NetworkLimits.MAX_SHORT_TEXT_CHARS), buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WorkgroupProjectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!PermissionManager.canUse(player, PermissionFeature.COLLABORATE)) return;
            Workgroup group = WorkgroupManager.groupFor(player);
            if (group == null || payload.groupId == null || !group.id.equals(payload.groupId)) return;
            if ("remove".equalsIgnoreCase(payload.action)) {
                WorkgroupManager.removeProjection(payload.groupId, payload.projectionId);
                WorkgroupProjectionStore.remove(payload.groupId, payload.projectionId);
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
            WorkgroupNetworkSync.syncGroup(player.server, group.id);
            WorkgroupNetworkSync.syncAdmins(player.server);
        });
    }
}
