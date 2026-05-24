package com.l1ght.ebe.network;

import com.google.gson.Gson;
import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.ServerSettingsManager;
import com.l1ght.ebe.server.permission.PermissionDecision;
import com.l1ght.ebe.server.permission.PermissionFeature;
import com.l1ght.ebe.server.permission.PermissionManager;
import com.l1ght.ebe.server.library.ServerFileLibraryManager;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;

public class AdminActionPayload implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "admin_action");
    public static final Type<AdminActionPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminActionPayload> STREAM_CODEC = StreamCodec.ofMember(AdminActionPayload::write, AdminActionPayload::decode);

    private final String action;
    private final String a;
    private final String b;
    private final String c;

    public AdminActionPayload(String action, String a, String b, String c) {
        this.action = action == null ? "sync" : action;
        this.a = a == null ? "" : a;
        this.b = b == null ? "" : b;
        this.c = c == null ? "" : c;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(NetworkLimits.bounded(action, NetworkLimits.MAX_ACTION_CHARS), NetworkLimits.MAX_ACTION_CHARS);
        buf.writeUtf(NetworkLimits.bounded(a, NetworkLimits.MAX_MEDIUM_TEXT_CHARS), NetworkLimits.MAX_MEDIUM_TEXT_CHARS);
        buf.writeUtf(NetworkLimits.bounded(b, NetworkLimits.MAX_MEDIUM_TEXT_CHARS), NetworkLimits.MAX_MEDIUM_TEXT_CHARS);
        buf.writeUtf(NetworkLimits.bounded(c, NetworkLimits.MAX_MEDIUM_TEXT_CHARS), NetworkLimits.MAX_MEDIUM_TEXT_CHARS);
    }

    public static AdminActionPayload decode(RegistryFriendlyByteBuf buf) {
        return new AdminActionPayload(buf.readUtf(NetworkLimits.MAX_ACTION_CHARS), buf.readUtf(NetworkLimits.MAX_MEDIUM_TEXT_CHARS),
                buf.readUtf(NetworkLimits.MAX_MEDIUM_TEXT_CHARS), buf.readUtf(NetworkLimits.MAX_MEDIUM_TEXT_CHARS));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(AdminActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            boolean workgroupsChanged = false;
            try {
                switch (payload.action) {
                    case "permission_global" -> PermissionManager.setGlobal(PermissionFeature.parse(payload.a), PermissionDecision.parse(payload.b));
                    case "permission_player" -> PermissionManager.setPlayer(payload.a, PermissionFeature.parse(payload.b), PermissionDecision.parse(payload.c));
                    case "setting" -> ServerSettingsManager.set(payload.a, Integer.parseInt(payload.b));
                    case "nbt_ignore_add" -> ServerSettingsManager.addNbtIgnoreRule(payload.a);
                    case "nbt_ignore_remove" -> ServerSettingsManager.removeNbtIgnoreRule(payload.a);
                    case "group_disband" -> workgroupsChanged = WorkgroupManager.disband(payload.a, player);
                    case "group_kick" -> workgroupsChanged = WorkgroupManager.kick(payload.a, payload.b, player);
                    case "library_delete" -> ServerFileLibraryManager.delete(payload.a);
                    case "library_rename" -> ServerFileLibraryManager.rename(payload.a, payload.b);
                    case "sync" -> {
                    }
                    default -> {
                    }
                }
            } catch (Exception e) {
                EBEMod.LOGGER.warn("Failed to handle admin action {}", payload.action, e);
            }
            sendAdminSync(player);
            if (workgroupsChanged) syncAllWorkgroups(player.server);
        });
    }

    public static void sendAdminSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new AdminSyncPayload(buildAdminJson()));
    }

    public static String buildAdminJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("permissions", PermissionManager.globalSnapshot());
        root.put("playerOverrides", PermissionManager.playerSnapshot());
        root.put("settings", ServerSettingsManager.asMap());
        root.put("nbtIgnoreRules", ServerSettingsManager.nbtIgnoreRules());
        root.put("library", GSON.fromJson(ServerFileLibraryManager.toJson(), Object.class));
        root.put("workgroups", GSON.fromJson(WorkgroupManager.toAdminJson(), Object.class));
        return GSON.toJson(root);
    }

    private static void syncAllWorkgroups(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new WorkgroupSyncPayload(WorkgroupManager.toClientJson(p)));
        }
    }
}
