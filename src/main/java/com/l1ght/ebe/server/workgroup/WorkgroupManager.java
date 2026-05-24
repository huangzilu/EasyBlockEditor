package com.l1ght.ebe.server.workgroup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.server.workgroup.print.WorkgroupPrintSessionManager;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WorkgroupManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = Path.of("config", "ebe", "server", "workgroups");
    private static final Map<UUID, Workgroup> GROUPS = new LinkedHashMap<>();
    private static long lastSignature = Long.MIN_VALUE;

    public static synchronized void load() {
        GROUPS.clear();
        try {
            Files.createDirectories(DIR);
            try (var stream = Files.list(DIR)) {
                for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                    Workgroup group = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Workgroup.class);
                    if (group != null && group.id != null) GROUPS.put(group.id, group);
                }
            }
            cleanupDuplicateMemberships();
            cleanupEmptyGroups();
            lastSignature = computeSignature();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load EBE workgroups", e);
        }
    }

    public static synchronized void saveAll() {
        try {
            cleanupDuplicateMemberships();
            cleanupEmptyGroups();
            Files.createDirectories(DIR);
            for (Workgroup group : GROUPS.values()) {
                Files.writeString(DIR.resolve(group.id + ".json"), GSON.toJson(group), StandardCharsets.UTF_8);
            }
            lastSignature = computeSignature();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save EBE workgroups", e);
        }
    }

    public static synchronized Workgroup create(String name, String password, ServerPlayer leader) {
        if (name == null || name.isBlank() || findByName(name) != null || groupFor(leader) != null) return null;
        Workgroup group = new Workgroup();
        group.name = name;
        group.password = password == null ? "" : password;
        group.leader = leader.getUUID();
        group.leaderName = leader.getGameProfile().getName();
        group.members.put(leader.getUUID(), group.leaderName);
        GROUPS.put(group.id, group);
        saveAll();
        return group;
    }

    public static synchronized boolean join(String name, String password, ServerPlayer player) {
        Workgroup group = findByName(name);
        if (group == null) return false;
        if (!group.password.equals(password == null ? "" : password)) return false;
        if (groupFor(player) != null) return false;
        group.members.put(player.getUUID(), player.getGameProfile().getName());
        saveAll();
        return true;
    }

    public static synchronized boolean leave(String name, ServerPlayer player) {
        Workgroup group = findByName(name);
        if (group == null || !group.members.containsKey(player.getUUID())) return false;
        if (group.isLeader(player.getUUID())) return false;
        group.members.remove(player.getUUID());
        saveAll();
        return true;
    }

    public static synchronized boolean kick(String name, String targetName, ServerPlayer actor) {
        Workgroup group = findByName(name);
        if (group == null || (!group.isLeader(actor.getUUID()) && !actor.hasPermissions(2))) return false;
        UUID targetId = findMemberId(group, targetName);
        if (targetId == null || group.isLeader(targetId)) return false;
        group.members.remove(targetId);
        saveAll();
        return true;
    }

    public static synchronized boolean disband(String name, ServerPlayer player) {
        Workgroup group = findByName(name);
        if (group == null || (!group.isLeader(player.getUUID()) && !player.hasPermissions(2))) return false;
        GROUPS.remove(group.id);
        WorkgroupPrintSessionManager.cancelGroup(group.id);
        try {
            Files.deleteIfExists(DIR.resolve(group.id + ".json"));
        } catch (IOException e) {
            EBEMod.LOGGER.warn("Failed to delete workgroup file for {}", group.id, e);
        }
        saveAll();
        return true;
    }

    public static synchronized Workgroup findByName(String name) {
        for (Workgroup group : GROUPS.values()) {
            if (group.name.equalsIgnoreCase(name)) return group;
        }
        return null;
    }

    public static synchronized List<Workgroup> groupsFor(ServerPlayer player) {
        List<Workgroup> result = new ArrayList<>();
        for (Workgroup group : GROUPS.values()) {
            if (group.members.containsKey(player.getUUID())) result.add(group);
        }
        return result;
    }

    public static synchronized Workgroup groupFor(ServerPlayer player) {
        for (Workgroup group : GROUPS.values()) {
            if (group.members.containsKey(player.getUUID())) return group;
        }
        return null;
    }

    public static synchronized Collection<Workgroup> allGroups() {
        return List.copyOf(GROUPS.values());
    }

    public static synchronized String toClientJson(ServerPlayer player) {
        Workgroup group = groupFor(player);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("player", player.getGameProfile().getName());
        root.put("playerId", player.getUUID().toString());
        root.put("group", group == null ? null : toMap(group, player));
        return GSON.toJson(root);
    }

    public static synchronized String toAdminJson() {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Workgroup group : GROUPS.values()) {
            groups.add(toMap(group, null));
        }
        return GSON.toJson(groups);
    }

    public static synchronized void upsertProjection(UUID groupId, Workgroup.ProjectionState state) {
        Workgroup group = GROUPS.get(groupId);
        if (group == null) return;
        group.projections.put(state.id(), state);
        saveAll();
    }

    public static synchronized void removeProjection(UUID groupId, UUID projectionId) {
        Workgroup group = GROUPS.get(groupId);
        if (group == null) return;
        group.projections.remove(projectionId);
        saveAll();
    }

    public static synchronized boolean addChatMessage(ServerPlayer player, String message) {
        Workgroup group = groupFor(player);
        if (group == null || message == null || message.isBlank()) return false;
        if (group.chat == null) group.chat = new ArrayList<>();
        String clean = message.trim();
        if (clean.length() > 240) clean = clean.substring(0, 240);
        group.chat.add(new Workgroup.ChatMessage(
                player.getUUID(),
                player.getGameProfile().getName(),
                clean,
                System.currentTimeMillis()
        ));
        while (group.chat.size() > 80) group.chat.remove(0);
        saveAll();
        return true;
    }

    public static synchronized boolean pollHotReload() {
        long signature = computeSignature();
        if (signature != lastSignature) {
            load();
            return true;
        }
        return false;
    }

    public static synchronized boolean expireProjections(int timeoutSeconds) {
        if (timeoutSeconds <= 0) return false;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Workgroup group : GROUPS.values()) {
            var iterator = group.projections.entrySet().iterator();
            while (iterator.hasNext()) {
                var projection = iterator.next().getValue();
                long updated = projection.updatedAt() <= 0 ? now : projection.updatedAt();
                if (now - updated > timeoutSeconds * 1000L) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        if (changed) saveAll();
        return changed;
    }

    private static Map<String, Object> toMap(Workgroup group, ServerPlayer viewer) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", group.id.toString());
        item.put("name", group.name);
        item.put("leader", group.leaderName);
        item.put("leaderId", group.leader == null ? "" : group.leader.toString());
        item.put("isLeader", viewer != null && group.isLeader(viewer.getUUID()));
        List<Map<String, String>> members = new ArrayList<>();
        for (var entry : group.members.entrySet()) {
            var m = new LinkedHashMap<String, String>();
            m.put("id", entry.getKey().toString());
            m.put("name", entry.getValue());
            m.put("role", group.isLeader(entry.getKey()) ? "leader" : "member");
            members.add(m);
        }
        item.put("members", members);
        List<Map<String, Object>> projections = new ArrayList<>();
        for (var projection : group.projections.values()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", projection.id().toString());
            p.put("owner", projection.ownerName());
            p.put("file", projection.fileName());
            p.put("x", projection.x());
            p.put("y", projection.y());
            p.put("z", projection.z());
            p.put("visible", projection.visible());
            p.put("updatedAt", projection.updatedAt());
            projections.add(p);
        }
        item.put("projections", projections);
        List<Map<String, Object>> chat = new ArrayList<>();
        if (group.chat != null) {
            for (var message : group.chat) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sender", message.senderName());
                row.put("message", message.message());
                row.put("sentAt", message.sentAt());
                chat.add(row);
            }
        }
        item.put("chat", chat);
        Map<String, Object> printSession = WorkgroupPrintSessionManager.snapshotForGroup(group.id);
        item.put("printSession", printSession == null ? null : printSession);
        return item;
    }

    private static UUID findMemberId(Workgroup group, String name) {
        for (var entry : group.members.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name) || entry.getKey().toString().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static void cleanupEmptyGroups() {
        for (Workgroup group : new ArrayList<>(GROUPS.values())) {
            if (group.members == null || group.members.isEmpty() || group.leader == null || !group.members.containsKey(group.leader)) {
                GROUPS.remove(group.id);
                WorkgroupPrintSessionManager.cancelGroup(group.id);
                deleteGroupFile(group.id);
            }
        }
    }

    private static void cleanupDuplicateMemberships() {
        Map<UUID, UUID> ownerGroup = new LinkedHashMap<>();
        for (Workgroup group : new ArrayList<>(GROUPS.values())) {
            if (group.members == null) {
                GROUPS.remove(group.id);
                WorkgroupPrintSessionManager.cancelGroup(group.id);
                deleteGroupFile(group.id);
                continue;
            }
            for (UUID member : new ArrayList<>(group.members.keySet())) {
                UUID existingGroup = ownerGroup.get(member);
                if (existingGroup == null) {
                    ownerGroup.put(member, group.id);
                } else if (!existingGroup.equals(group.id)) {
                    group.members.remove(member);
                }
            }
        }
    }

    private static void deleteGroupFile(UUID id) {
        try {
            Files.deleteIfExists(DIR.resolve(id + ".json"));
        } catch (IOException e) {
            EBEMod.LOGGER.warn("Failed to delete workgroup file for {}", id, e);
        }
    }

    private static long computeSignature() {
        long hash = 1125899906842597L;
        try {
            Files.createDirectories(DIR);
            try (var stream = Files.list(DIR)) {
                for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                    hash = 31 * hash + path.getFileName().toString().hashCode();
                    hash = 31 * hash + Files.size(path);
                    hash = 31 * hash + Files.getLastModifiedTime(path).toMillis();
                }
            }
        } catch (IOException e) {
            EBEMod.LOGGER.warn("Failed to compute workgroup file signature", e);
        }
        return hash;
    }
}
