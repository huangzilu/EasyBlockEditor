package com.l1ght.ebe.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ServerSettingsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "ebe", "server", "settings.json");
    private static ServerSettings settings = new ServerSettings();
    private static long lastModified = Long.MIN_VALUE;

    public static synchronized void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                save();
                return;
            }
            ServerSettings loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), ServerSettings.class);
            settings = loaded == null ? new ServerSettings() : loaded.clamped();
            lastModified = Files.getLastModifiedTime(FILE).toMillis();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load EBE server settings", e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(settings.clamped()), StandardCharsets.UTF_8);
            lastModified = Files.getLastModifiedTime(FILE).toMillis();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save EBE server settings", e);
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

    public static synchronized ServerSettings get() {
        return settings;
    }

    public static synchronized void set(String key, int value) {
        settings = switch (key) {
            case "projection_timeout_seconds" -> settings.withProjectionTimeoutSeconds(value);
            case "place_chunks_per_tick" -> settings.withPlaceChunksPerTick(value);
            case "printer_blocks_per_tick" -> settings.withPrinterBlocksPerTick(value);
            case "max_edit_size" -> settings.withMaxEditSize(value);
            default -> settings;
        };
        save();
    }

    public static synchronized Map<String, Integer> asMap() {
        var map = new LinkedHashMap<String, Integer>();
        map.put("projection_timeout_seconds", settings.projectionTimeoutSeconds);
        map.put("place_chunks_per_tick", settings.placeChunksPerTick);
        map.put("printer_blocks_per_tick", settings.printerBlocksPerTick);
        map.put("max_edit_size", settings.maxEditSize);
        return map;
    }

    public static class ServerSettings {
        public int projectionTimeoutSeconds = 0;
        public int placeChunksPerTick = 4;
        public int printerBlocksPerTick = 1;
        public int maxEditSize = 256;

        public ServerSettings clamped() {
            projectionTimeoutSeconds = clamp(projectionTimeoutSeconds, 0, 86400);
            placeChunksPerTick = clamp(placeChunksPerTick, 1, 32);
            printerBlocksPerTick = clamp(printerBlocksPerTick, 1, 10);
            maxEditSize = clamp(maxEditSize, 16, 512);
            return this;
        }

        public ServerSettings withProjectionTimeoutSeconds(int value) {
            var copy = copy();
            copy.projectionTimeoutSeconds = value;
            return copy.clamped();
        }

        public ServerSettings withPlaceChunksPerTick(int value) {
            var copy = copy();
            copy.placeChunksPerTick = value;
            return copy.clamped();
        }

        public ServerSettings withPrinterBlocksPerTick(int value) {
            var copy = copy();
            copy.printerBlocksPerTick = value;
            return copy.clamped();
        }

        public ServerSettings withMaxEditSize(int value) {
            var copy = copy();
            copy.maxEditSize = value;
            return copy.clamped();
        }

        private ServerSettings copy() {
            var copy = new ServerSettings();
            copy.projectionTimeoutSeconds = projectionTimeoutSeconds;
            copy.placeChunksPerTick = placeChunksPerTick;
            copy.printerBlocksPerTick = printerBlocksPerTick;
            copy.maxEditSize = maxEditSize;
            return copy;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
