package com.l1ght.ebe.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.l1ght.ebe.EBEMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class NbtTemplateManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("config", "ebe", "client", "nbt_templates.json");
    private static final Map<String, String> TEMPLATES = new LinkedHashMap<>();

    public static Map<String, String> all() {
        load();
        return Map.copyOf(TEMPLATES);
    }

    public static void saveTemplate(String name, CompoundTag tag) {
        if (name == null || name.isBlank() || tag == null) return;
        load();
        TEMPLATES.put(name.trim(), tag.toString());
        save();
    }

    public static CompoundTag get(String name) {
        load();
        String raw = TEMPLATES.get(name);
        if (raw == null) return null;
        try {
            return TagParser.parseTag(raw);
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to parse NBT template {}", name, e);
            return null;
        }
    }

    public static void delete(String name) {
        load();
        TEMPLATES.remove(name);
        save();
    }

    private static void load() {
        if (!TEMPLATES.isEmpty() || !Files.exists(FILE)) return;
        try {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), type);
            if (loaded != null) TEMPLATES.putAll(loaded);
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to load NBT templates", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(TEMPLATES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to save NBT templates", e);
        }
    }
}
