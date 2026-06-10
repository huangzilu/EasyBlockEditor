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
    public static final ModConfigSpec.ConfigValue<String> viewportPerformanceMode;
    public static final ModConfigSpec.BooleanValue viewportDegradeWhileMoving;
    public static final ModConfigSpec.IntValue viewportRenderDistance;
    public static final ModConfigSpec.DoubleValue viewportCompileBudgetMs;
    public static final ModConfigSpec.DoubleValue viewportMovingCompileBudgetMs;
    public static final ModConfigSpec.DoubleValue viewportLoadBudgetMs;
    public static final ModConfigSpec.DoubleValue viewportMovingLoadBudgetMs;
    public static final ModConfigSpec.DoubleValue viewportSynchronousLoadBelowMb;
    public static final ModConfigSpec.IntValue viewportMegaExactBlockCap;
    public static final ModConfigSpec.IntValue viewportLoadBlocksPerFrame;
    public static final ModConfigSpec.IntValue viewportFallbackBlocks;
    public static final ModConfigSpec.IntValue viewportMovingFallbackBlocks;
    public static final ModConfigSpec.BooleanValue viewportDynamicFboScale;
    public static final ModConfigSpec.DoubleValue viewportMovingFboScale;
    public static final ModConfigSpec.IntValue viewportMdiFullDetailDist;
    public static final ModConfigSpec.IntValue viewportMdiLodDist;
    public static final ModConfigSpec.IntValue printerRange;
    public static final ModConfigSpec.IntValue printerMaterialSourceRange;
    public static final ModConfigSpec.IntValue printerParallelism;
    public static final ModConfigSpec.ConfigValue<String> KEYBINDINGS;
    public static final ModConfigSpec.ConfigValue<String> splashMode;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.comment("EasyBlockEditor Client Configuration").push("client");

        schematicDir = builder
                .comment("Directory for schematic files")
                .define("schematic_dir", "config/ebe/client/schematics");

        theme = builder
                .comment("UI theme: dark, mc, modern")
                .define("theme", "dark");

        splashMode = builder
                .comment("Splash/intro animation: always (every open), per_session (first open each launch), first_ever (only the very first time), off")
                .define("splash_mode", "first_ever");

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

        builder.push("viewport_performance");
        viewportPerformanceMode = builder
                .comment("3D editor viewport performance mode: quality, balanced, performance")
                .define("mode", "balanced");
        viewportDegradeWhileMoving = builder
                .comment("Temporarily reduce expensive viewport work while the camera is moving")
                .define("degrade_while_moving", true);
        viewportRenderDistance = builder
                .comment("Editor viewport render distance in blocks (0 = unlimited)")
                .defineInRange("render_distance", 0, 0, Integer.MAX_VALUE);
        viewportCompileBudgetMs = builder
                .comment("Milliseconds of section compilation allowed per rendered frame while the camera is steady")
                .defineInRange("compile_budget_ms", 2.5, 0.0, 20.0);
        viewportMovingCompileBudgetMs = builder
                .comment("Milliseconds of section compilation allowed per rendered frame while the camera is moving")
                .defineInRange("moving_compile_budget_ms", 0.25, 0.0, 20.0);
        viewportLoadBudgetMs = builder
                .comment("Milliseconds of viewport block loading allowed per frame while the camera is steady")
                .defineInRange("load_budget_ms", 2.0, 0.25, 20.0);
        viewportMovingLoadBudgetMs = builder
                .comment("Milliseconds of viewport block loading allowed per frame while the camera is moving")
                .defineInRange("moving_load_budget_ms", 0.75, 0.0, 20.0);
        viewportSynchronousLoadBelowMb = builder
                .comment("Use synchronous viewport loading for projection files at or below this size in MB. 0 = always use synchronous loading.")
                .defineInRange("sync_load_below_mb", 1.0, 0.0, 1024.0);
        viewportMegaExactBlockCap = builder
                .comment("Maximum exact blocks loaded into the 3D viewport for huge projections. 0 = no limit. Lower values improve FPS and memory use; higher values preserve more detail.")
                .defineInRange("mega_exact_block_cap", 0, 0, 100_000_000);
        viewportLoadBlocksPerFrame = builder
                .comment("Maximum blocks inserted into the editor viewport per frame")
                .defineInRange("load_blocks_per_frame", 2048, 128, 16384);
        viewportFallbackBlocks = builder
                .comment("Maximum uncompiled blocks rendered directly per frame while the camera is steady")
                .defineInRange("fallback_blocks", 1536, 0, 16384);
        viewportMovingFallbackBlocks = builder
                .comment("Maximum uncompiled blocks rendered directly per frame while the camera is moving")
                .defineInRange("moving_fallback_blocks", 128, 0, 16384);
        viewportDynamicFboScale = builder
                .comment("Use a lower Iris/FBO viewport resolution while the camera is moving")
                .define("dynamic_fbo_scale", true);
        viewportMovingFboScale = builder
                .comment("Iris/FBO viewport resolution scale while the camera is moving")
                .defineInRange("moving_fbo_scale", 0.65, 0.25, 1.0);
        viewportMdiFullDetailDist = builder
                .comment("Full detail render distance for large projections (blocks). Blocks within this distance render with full models.")
                .defineInRange("mdi_full_detail_dist", 1024, 64, 4096);
        viewportMdiLodDist = builder
                .comment("LOD render distance for large projections (blocks). Blocks beyond this distance are covered by Shell LOD only.")
                .defineInRange("mdi_lod_dist", 2048, 128, 8192);
        builder.pop();
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
