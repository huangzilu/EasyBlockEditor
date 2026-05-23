package com.l1ght.ebe.server.permission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class PermissionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "ebe", "server", "permissions.json");
    private static final EnumMap<PermissionFeature, PermissionDecision> GLOBAL = new EnumMap<>(PermissionFeature.class);
    private static final Map<String, EnumMap<PermissionFeature, PermissionDecision>> PLAYER_OVERRIDES = new LinkedHashMap<>();
    private static long lastModified = Long.MIN_VALUE;

    static {
        resetDefaults();
    }

    public static synchronized void load() {
        resetDefaults();
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<PermissionFile>() {}.getType();
            PermissionFile file = GSON.fromJson(reader, type);
            if (file == null) return;
            if (file.global != null) {
                for (var entry : file.global.entrySet()) {
                    GLOBAL.put(PermissionFeature.parse(entry.getKey()), PermissionDecision.parse(entry.getValue()));
                }
            }
            if (file.players != null) {
                for (var playerEntry : file.players.entrySet()) {
                    var map = new EnumMap<PermissionFeature, PermissionDecision>(PermissionFeature.class);
                    for (var entry : playerEntry.getValue().entrySet()) {
                        map.put(PermissionFeature.parse(entry.getKey()), PermissionDecision.parse(entry.getValue()));
                    }
                    PLAYER_OVERRIDES.put(normalizePlayer(playerEntry.getKey()), map);
                }
            }
            lastModified = Files.getLastModifiedTime(FILE).toMillis();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load EBE permissions", e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            PermissionFile file = new PermissionFile();
            file.global = new LinkedHashMap<>();
            for (var feature : PermissionFeature.values()) {
                file.global.put(feature.id(), GLOBAL.getOrDefault(feature, PermissionDecision.DEFAULT).name().toLowerCase(Locale.ROOT));
            }
            file.players = new LinkedHashMap<>();
            for (var playerEntry : PLAYER_OVERRIDES.entrySet()) {
                var map = new LinkedHashMap<String, String>();
                for (var entry : playerEntry.getValue().entrySet()) {
                    map.put(entry.getKey().id(), entry.getValue().name().toLowerCase(Locale.ROOT));
                }
                file.players.put(playerEntry.getKey(), map);
            }
            Files.writeString(FILE, GSON.toJson(file), StandardCharsets.UTF_8);
            lastModified = Files.getLastModifiedTime(FILE).toMillis();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save EBE permissions", e);
        }
    }

    public static synchronized boolean pollHotReload() {
        try {
            if (!Files.exists(FILE)) {
                save();
                return true;
            }
            long modified = Files.getLastModifiedTime(FILE).toMillis();
            if (modified != lastModified) {
                load();
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static synchronized void setGlobal(PermissionFeature feature, PermissionDecision decision) {
        GLOBAL.put(feature, decision);
        save();
    }

    public static synchronized void setPlayer(String playerName, PermissionFeature feature, PermissionDecision decision) {
        var key = normalizePlayer(playerName);
        var map = PLAYER_OVERRIDES.computeIfAbsent(key, ignored -> new EnumMap<>(PermissionFeature.class));
        if (decision == PermissionDecision.DEFAULT) {
            map.remove(feature);
        } else {
            map.put(feature, decision);
        }
        if (map.isEmpty()) PLAYER_OVERRIDES.remove(key);
        save();
    }

    public static synchronized PermissionDecision getGlobal(PermissionFeature feature) {
        return GLOBAL.getOrDefault(feature, PermissionDecision.DEFAULT);
    }

    public static synchronized PermissionDecision getPlayer(String playerName, PermissionFeature feature) {
        var map = PLAYER_OVERRIDES.get(normalizePlayer(playerName));
        return map == null ? PermissionDecision.DEFAULT : map.getOrDefault(feature, PermissionDecision.DEFAULT);
    }

    public static synchronized Map<String, String> globalSnapshot() {
        var map = new LinkedHashMap<String, String>();
        for (var feature : PermissionFeature.values()) {
            map.put(feature.id(), getGlobal(feature).name().toLowerCase(Locale.ROOT));
        }
        return map;
    }

    public static synchronized Map<String, Map<String, String>> playerSnapshot() {
        var players = new LinkedHashMap<String, Map<String, String>>();
        for (var playerEntry : PLAYER_OVERRIDES.entrySet()) {
            var map = new LinkedHashMap<String, String>();
            for (var entry : playerEntry.getValue().entrySet()) {
                map.put(entry.getKey().id(), entry.getValue().name().toLowerCase(Locale.ROOT));
            }
            players.put(playerEntry.getKey(), map);
        }
        return players;
    }

    public static boolean canUse(ServerPlayer player, PermissionFeature feature) {
        if (player.hasPermissions(2)) return true;
        PermissionDecision playerDecision = getPlayer(player.getGameProfile().getName(), feature);
        if (playerDecision == PermissionDecision.ALLOW) return true;
        if (playerDecision == PermissionDecision.DENY) return false;
        PermissionDecision globalDecision = getGlobal(feature);
        if (globalDecision == PermissionDecision.ALLOW) return true;
        if (globalDecision == PermissionDecision.DENY) return false;
        return feature.defaultAllowed();
    }

    private static void resetDefaults() {
        GLOBAL.clear();
        PLAYER_OVERRIDES.clear();
        for (var feature : PermissionFeature.values()) {
            GLOBAL.put(feature, PermissionDecision.DEFAULT);
        }
    }

    private static String normalizePlayer(String playerName) {
        return playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
    }

    private static class PermissionFile {
        Map<String, String> global;
        Map<String, Map<String, String>> players;
    }
}
