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
                .comment("Projection timeout in seconds (0 = never timeout)", "投影超时秒数（0=永不超时）")
                .defineInRange("projection_timeout_seconds", 0, 0, 86400);

        maxWorkgroupsPerPlayer = builder
                .comment("Max workgroups per player", "每玩家最大工作组数")
                .defineInRange("max_workgroups_per_player", 5, 1, 50);

        placeChunksPerTick = builder
                .comment("Chunks processed per tick during place-all", "一键放置每tick处理Chunk数")
                .defineInRange("place_chunks_per_tick", 4, 1, 32);

        printerBlocksPerTick = builder
                .comment("Blocks placed per tick by auto printer", "自动打印机每tick放置方块数")
                .defineInRange("printer_blocks_per_tick", 1, 1, 10);

        maxEditSize = builder
                .comment("Max single edit region size (blocks per axis)", "单次编辑最大区域尺寸（每轴方块数）")
                .defineInRange("max_edit_size", 256, 16, 512);

        builder.pop();
        SPEC = builder.build();
    }

    private EBEServerConfig() {}

    public static void register(net.neoforged.fml.ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }
}
