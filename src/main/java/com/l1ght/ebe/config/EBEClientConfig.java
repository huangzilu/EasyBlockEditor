package com.l1ght.ebe.config;

import com.l1ght.ebe.client.keybind.EBEKeyBindings;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class EBEClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> schematicDir;
    public static final ModConfigSpec.ConfigValue<String> theme;
    public static final ModConfigSpec.DoubleValue projectionOpacity;
    public static final ModConfigSpec.IntValue projectionRenderDistance;
    public static final ModConfigSpec.DoubleValue editorFov;
    public static final ModConfigSpec.DoubleValue flightSpeed;
    public static final ModConfigSpec.IntValue historyMaxEntries;
    public static final ModConfigSpec.ConfigValue<String> viewportShaderMode;
    public static final ModConfigSpec.IntValue printerRange;
    public static final ModConfigSpec.IntValue printerMaterialSourceRange;
    public static final ModConfigSpec.IntValue printerParallelism;
    public static final ModConfigSpec.ConfigValue<String> KEYBINDINGS;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.comment("EasyBlockEditor Client Configuration").push("client");

        schematicDir = builder
                .comment("Directory for schematic files")
                .define("schematic_dir", "config/ebe/client/schematics");

        theme = builder
                .comment("UI theme: dark, mc, modern")
                .define("theme", "dark");

        builder.push("projection");
        projectionOpacity = builder
                .comment("Default projection opacity (0.0 - 1.0)")
                .defineInRange("default_opacity", 0.5, 0.0, 1.0);
        projectionRenderDistance = builder
                .comment("Projection render distance in blocks (0 = unlimited)")
                .defineInRange("render_distance", 64, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("editor");
        editorFov = builder
                .comment("Editor camera FOV")
                .defineInRange("fov", 60.0, 30.0, 120.0);
        flightSpeed = builder
                .comment("Free flight camera speed")
                .defineInRange("flight_speed", 1.0, 0.05, 50.0);
        historyMaxEntries = builder
                .comment("Maximum history entries to keep (0 = unlimited)")
                .defineInRange("history_max_entries", 100, 0, Integer.MAX_VALUE);
        viewportShaderMode = builder
                .comment("3D viewport shader mode: off, auto, iris, iris_full. Auto only probes when a shader pack is active.")
                .define("viewport_shader_mode", "auto");
        builder.pop();

        builder.push("printer");
        printerRange = builder
                .comment("Auto printer range in blocks (0 = unlimited)")
                .defineInRange("auto_range", 3, 0, Integer.MAX_VALUE);
        printerMaterialSourceRange = builder
                .comment("Auto printer range around the bound material source in blocks (0 = unlimited)")
                .defineInRange("material_source_range", 64, 0, Integer.MAX_VALUE);
        printerParallelism = builder
                .comment("Auto printer parallel placement lanes. World writes still run on the server thread.")
                .defineInRange("parallelism", 1, 1, 8);
        builder.pop();

        KEYBINDINGS = builder
                .comment("Keybindings (format: id1=SERIAL;id2=SERIAL;...)")
                .define("keybindings", "");

        builder.pop();
        SPEC = builder.build();
    }

    private EBEClientConfig() {}

    public static void register(net.neoforged.fml.ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    public static void loadKeybindings() {
        String raw = KEYBINDINGS.get();
        if (raw == null || raw.isEmpty()) return;
        Map<String, String> map = deserializeKeybindings(raw);
        for (var binding : EBEKeyBindings.getAll()) {
            String serial = map.get(binding.getId());
            if (serial != null) {
                binding.deserialize(serial);
            }
        }
    }

    public static void saveAllKeybindings() {
        Map<String, String> map = new LinkedHashMap<>();
        for (var binding : EBEKeyBindings.getAll()) {
            map.put(binding.getId(), binding.serialize());
        }
        KEYBINDINGS.set(serializeKeybindings(map));
        SPEC.save();
    }

    public static Map<String, String> deserializeKeybindings(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        String[] entries = raw.split(";");
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq > 0) {
                map.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
            }
        }
        return map;
    }

    public static String serializeKeybindings(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(';');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
}
