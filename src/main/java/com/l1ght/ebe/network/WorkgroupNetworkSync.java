package com.l1ght.ebe.network;

import com.google.gson.Gson;
import com.l1ght.ebe.server.workgroup.Workgroup;
import com.l1ght.ebe.server.workgroup.WorkgroupManager;
import com.l1ght.ebe.server.workgroup.print.WorkgroupPrintSessionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;

public final class WorkgroupNetworkSync {
    private static final Gson GSON = new Gson();

    private WorkgroupNetworkSync() {
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
    }

    public static void syncAdmins(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                AdminActionPayload.sendAdminSync(player);
            }
        }
    }

    public static void syncGroup(MinecraftServer server, UUID groupId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Workgroup group = WorkgroupManager.groupFor(player);
            if (group != null && group.id.equals(groupId)) {
                syncPlayer(player);
            }
        }
    }

    public static void syncPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new WorkgroupSyncPayload(WorkgroupManager.toClientJson(player)));
        Workgroup group = WorkgroupManager.groupFor(player);
        Map<String, Object> snapshot = group == null ? null : WorkgroupPrintSessionManager.snapshotForGroup(group.id);
        if (snapshot != null) {
            int[] progress = WorkgroupPrintSessionManager.progressForGroup(group.id);
            PacketDistributor.sendToPlayer(player, new WorkgroupPrintSyncPayload(true, progress[0], progress[1], GSON.toJson(snapshot)));
        } else {
            PacketDistributor.sendToPlayer(player, new WorkgroupPrintSyncPayload(false, 0, 0, "{}"));
        }
    }
}
