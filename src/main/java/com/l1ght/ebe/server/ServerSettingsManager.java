package com.l1ght.ebe.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.nbt.NbtPathRules;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to hot-reload EBE server settings", e);
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
            case "place_blocks_per_tick" -> settings.withPlaceBlocksPerTick(value);
            case "max_edit_size" -> settings.withMaxEditSize(value);
            case "strict_nbt_matching" -> settings.withStrictNbtMatching(value != 0);
            default -> settings;
        };
        save();
    }

    public static synchronized void addNbtIgnoreRule(String rule) {
        var copy = settings.copy();
        var rules = new ArrayList<>(NbtPathRules.normalize(copy.nbtIgnoreRules));
        String normalized = NbtPathRules.normalize(List.of(rule)).stream()
                .filter(r -> !"/x".equals(r) && !"/y".equals(r) && !"/z".equals(r) && !"/id".equals(r))
                .findFirst().orElse("");
        if (!normalized.isEmpty() && !rules.contains(normalized)) {
            rules.add(normalized);
            copy.nbtIgnoreRules = rules;
            settings = copy.clamped();
            save();
        }
    }

    public static synchronized void removeNbtIgnoreRule(String rule) {
        var copy = settings.copy();
        var rules = new ArrayList<>(copy.nbtIgnoreRules == null ? List.of() : copy.nbtIgnoreRules);
        String normalized = NbtPathRules.normalize(List.of(rule)).stream().findFirst().orElse("");
        rules.removeIf(existing -> existing.equals(normalized));
        copy.nbtIgnoreRules = rules;
        settings = copy.clamped();
        save();
    }

    public static synchronized Map<String, Integer> asMap() {
        var map = new LinkedHashMap<String, Integer>();
        map.put("projection_timeout_seconds", settings.projectionTimeoutSeconds);
        map.put("place_chunks_per_tick", settings.placeChunksPerTick);
        map.put("place_blocks_per_tick", settings.placeBlocksPerTick);
        map.put("printer_blocks_per_tick", settings.printerBlocksPerTick);
        map.put("max_edit_size", settings.maxEditSize);
        map.put("strict_nbt_matching", settings.strictNbtMatching ? 1 : 0);
        return map;
    }

    public static synchronized List<String> nbtIgnoreRules() {
        return List.copyOf(NbtPathRules.normalize(settings.nbtIgnoreRules));
    }

    public static class ServerSettings {
        public int projectionTimeoutSeconds = 0;
        public int placeChunksPerTick = 4;
        public int placeBlocksPerTick = 4096;
        public int printerBlocksPerTick = 1;
        public int maxEditSize = 256;
        public boolean strictNbtMatching = true;
        public List<String> nbtIgnoreRules = new ArrayList<>();

        public ServerSettings clamped() {
            projectionTimeoutSeconds = clamp(projectionTimeoutSeconds, 0, 86400);
            placeChunksPerTick = clamp(placeChunksPerTick, 1, 32);
            placeBlocksPerTick = clamp(placeBlocksPerTick, 128, 65_536);
            printerBlocksPerTick = clamp(printerBlocksPerTick, 1, 10);
            maxEditSize = clamp(maxEditSize, 16, 512);
            nbtIgnoreRules = new ArrayList<>(NbtPathRules.normalize(nbtIgnoreRules));
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

        public ServerSettings withPlaceBlocksPerTick(int value) {
            var copy = copy();
            copy.placeBlocksPerTick = value;
            return copy.clamped();
        }

        public ServerSettings withMaxEditSize(int value) {
            var copy = copy();
            copy.maxEditSize = value;
            return copy.clamped();
        }

        public ServerSettings withStrictNbtMatching(boolean value) {
            var copy = copy();
            copy.strictNbtMatching = value;
            return copy.clamped();
        }

        private ServerSettings copy() {
            var copy = new ServerSettings();
            copy.projectionTimeoutSeconds = projectionTimeoutSeconds;
            copy.placeChunksPerTick = placeChunksPerTick;
            copy.placeBlocksPerTick = placeBlocksPerTick;
            copy.printerBlocksPerTick = printerBlocksPerTick;
            copy.maxEditSize = maxEditSize;
            copy.strictNbtMatching = strictNbtMatching;
            copy.nbtIgnoreRules = new ArrayList<>(nbtIgnoreRules == null ? List.of() : nbtIgnoreRules);
            return copy;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
