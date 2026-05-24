package com.l1ght.ebe.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.ModConfig;

public class EBEServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue projectionTimeoutSeconds;
    public static final ModConfigSpec.IntValue maxWorkgroupsPerPlayer;
    public static final ModConfigSpec.IntValue placeChunksPerTick;
    public static final ModConfigSpec.IntValue printerBlocksPerTick;
    public static final ModConfigSpec.IntValue maxEditSize;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.comment("EasyBlockEditor Server Configuration").push("server");

        projectionTimeoutSeconds = builder
                .comment("Projection timeout in seconds (0 = never timeout)")
                .defineInRange("projection_timeout_seconds", 0, 0, 86400);

        maxWorkgroupsPerPlayer = builder
                .comment("Max workgroups per player")
                .defineInRange("max_workgroups_per_player", 5, 1, 50);

        placeChunksPerTick = builder
                .comment("Chunks processed per tick during place-all")
                .defineInRange("place_chunks_per_tick", 4, 1, 32);

        printerBlocksPerTick = builder
                .comment("Blocks placed per tick by auto printer")
                .defineInRange("printer_blocks_per_tick", 8, 1, 64);

        maxEditSize = builder
                .comment("Max single edit region size (blocks per axis)")
                .defineInRange("max_edit_size", 256, 16, 512);

        builder.pop();
        SPEC = builder.build();
    }

    private EBEServerConfig() {}

    public static void register(net.neoforged.fml.ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }
}
