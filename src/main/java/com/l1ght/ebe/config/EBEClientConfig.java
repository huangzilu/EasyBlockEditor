package com.l1ght.ebe.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;

public class EBEClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> schematicDir;
    public static final ModConfigSpec.ConfigValue<String> theme;
    public static final ModConfigSpec.DoubleValue projectionOpacity;
    public static final ModConfigSpec.IntValue projectionRenderDistance;
    public static final ModConfigSpec.DoubleValue editorFov;
    public static final ModConfigSpec.DoubleValue flightSpeed;
    public static final ModConfigSpec.IntValue historyMaxEntries;
    public static final ModConfigSpec.IntValue printerRange;

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
                .comment("Projection render distance in blocks")
                .defineInRange("render_distance", 64, 16, 256);
        builder.pop();

        builder.push("editor");
        editorFov = builder
                .comment("Editor camera FOV")
                .defineInRange("fov", 60.0, 30.0, 120.0);
        flightSpeed = builder
                .comment("Free flight camera speed")
                .defineInRange("flight_speed", 10.0, 1.0, 50.0);
        historyMaxEntries = builder
                .comment("Maximum history entries to keep (0 = unlimited)")
                .defineInRange("history_max_entries", 100, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("printer");
        printerRange = builder
                .comment("Auto printer range in blocks")
                .defineInRange("auto_range", 3, 1, 16);
        builder.pop();

        builder.pop();
        SPEC = builder.build();
    }

    private EBEClientConfig() {}

    public static void register(net.neoforged.fml.ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
}
