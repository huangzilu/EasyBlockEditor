package com.l1ght.ebe.network;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.permission.PermissionFeature;
import com.l1ght.ebe.server.permission.PermissionManager;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class WorkgroupActionPayload implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "workgroup_action");
    public static final Type<WorkgroupActionPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WorkgroupActionPayload> STREAM_CODEC = StreamCodec.ofMember(WorkgroupActionPayload::write, WorkgroupActionPayload::decode);

    private final String action;
    private final String groupName;
    private final String password;
    private final String target;

    public WorkgroupActionPayload(String action, String groupName, String password, String target) {
        this.action = action == null ? "sync" : action;
        this.groupName = groupName == null ? "" : groupName;
        this.password = password == null ? "" : password;
        this.target = target == null ? "" : target;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(NetworkLimits.bounded(action, NetworkLimits.MAX_ACTION_CHARS), NetworkLimits.MAX_ACTION_CHARS);
        buf.writeUtf(NetworkLimits.bounded(groupName, NetworkLimits.MAX_SHORT_TEXT_CHARS), NetworkLimits.MAX_SHORT_TEXT_CHARS);
        buf.writeUtf(NetworkLimits.bounded(password, NetworkLimits.MAX_SHORT_TEXT_CHARS), NetworkLimits.MAX_SHORT_TEXT_CHARS);
        buf.writeUtf(NetworkLimits.bounded(target, NetworkLimits.MAX_MEDIUM_TEXT_CHARS), NetworkLimits.MAX_MEDIUM_TEXT_CHARS);
    }

    public static WorkgroupActionPayload decode(RegistryFriendlyByteBuf buf) {
        return new WorkgroupActionPayload(buf.readUtf(NetworkLimits.MAX_ACTION_CHARS),
                buf.readUtf(NetworkLimits.MAX_SHORT_TEXT_CHARS),
                buf.readUtf(NetworkLimits.MAX_SHORT_TEXT_CHARS),
                buf.readUtf(NetworkLimits.MAX_MEDIUM_TEXT_CHARS));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WorkgroupActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!"sync".equals(payload.action) && !PermissionManager.canUse(player, PermissionFeature.COLLABORATE)) {
                PacketDistributor.sendToPlayer(player, new WorkgroupSyncPayload(WorkgroupManager.toClientJson(player)));
                return;
            }
            boolean changed = false;
            try {
                switch (payload.action) {
                    case "create" -> {
                        changed = WorkgroupManager.create(payload.groupName, payload.password, player) != null;
                    }
                    case "join" -> changed = WorkgroupManager.join(payload.groupName, payload.password, player);
                    case "leave" -> changed = WorkgroupManager.leave(payload.groupName, player);
                    case "disband" -> changed = WorkgroupManager.disband(payload.groupName, player);
                    case "kick" -> changed = WorkgroupManager.kick(payload.groupName, payload.target, player);
                    case "chat" -> changed = WorkgroupManager.addChatMessage(player, payload.target);
                    case "sync" -> {
                    }
                    default -> {
                    }
                }
            } catch (Exception e) {
                EBEMod.LOGGER.warn("Failed to handle workgroup action {}", payload.action, e);
            }
            if (changed) syncAll(player.server);
            else PacketDistributor.sendToPlayer(player, new WorkgroupSyncPayload(WorkgroupManager.toClientJson(player)));
        });
    }

    private static void syncAll(MinecraftServer server) {
        WorkgroupNetworkSync.syncAll(server);
        WorkgroupNetworkSync.syncAdmins(server);
    }
}
