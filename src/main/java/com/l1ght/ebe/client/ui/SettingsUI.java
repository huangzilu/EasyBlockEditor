package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.EBEKeyBinding;
import com.l1ght.ebe.client.keybind.EBEKeyBindings;
import com.l1ght.ebe.client.keybind.KeyRecordingManager;
import com.l1ght.ebe.config.EBEClientConfig;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.AlignItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Map;

public class SettingsUI {

    public static void showSettings(UIElement parent) {
        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.editor.settings").getString());
        dialog.setAutoClose(false);
        dialog.overlay.layout(l -> l.width(380));

        var tabView = new TabView();
        tabView.layout(l -> l.flexDirection(FlexDirection.COLUMN).widthPercent(100));

        var header = tabView.tabHeaderContainer;
        var content = tabView.tabContentContainer;
        tabView.removeChild(header);
        tabView.removeChild(content);
        tabView.addChild(header);
        tabView.addChild(content);

        content.layout(l -> l.widthPercent(100).height(320));

        tabView.addTab(new Tab().setText(Component.translatable("ebe.editor.settings.general")),
                createGeneralTab());
        tabView.addTab(new Tab().setText(Component.translatable("ebe.editor.settings.editor")),
                createEditorTab());
        tabView.addTab(new Tab().setText(Component.translatable("ebe.editor.settings.projection")),
                createProjectionTab());
        tabView.addTab(new Tab().setText(Component.translatable("ebe.editor.settings.printer")),
                createPrinterTab());
        tabView.addTab(new Tab().setText(Component.translatable("ebe.editor.settings.keybindings")),
                createKeybindingsTab());

        dialog.addContent(tabView);

        dialog.addButton(new Button()
                .setText(Component.translatable("ebe.editor.settings.ok"))
                .setOnClick(e -> {
                    KeyRecordingManager.cancelRecording();
                    dialog.close();
                }));

        dialog.show(parent);
    }

    private static UIElement createGeneralTab() {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));

        var group = new ConfiguratorGroup(Component.translatable("ebe.settings.general").getString(), false);

        group.addConfigurator(new StringConfigurator(
                Component.translatable("ebe.settings.schematic_dir").getString(),
                EBEClientConfig.schematicDir::get,
                v -> { EBEClientConfig.schematicDir.set(v); EBEClientConfig.SPEC.save(); },
                "config/ebe/client/schematics", false
        ));

        group.addConfigurator(new SelectorConfigurator<>(
                Component.translatable("ebe.settings.theme").getString(),
                () -> EBEClientConfig.theme.get(),
                v -> { EBEClientConfig.theme.set(v); EBEClientConfig.SPEC.save(); },
                "dark", false,
                List.of("dark", "mc", "modern"),
                Object::toString
        ));

        scroller.addScrollViewChild(group);
        return scroller;
    }

    private static UIElement createEditorTab() {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));

        var group = new ConfiguratorGroup(Component.translatable("ebe.settings.editor").getString(), false);

        group.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.fov").getString(),
                EBEClientConfig.editorFov::get,
                v -> { EBEClientConfig.editorFov.set(v.doubleValue()); EBEClientConfig.SPEC.save(); },
                60.0, false
        ).setRange(30.0, 120.0).setWheel(5.0));

        group.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.flight_speed").getString(),
                EBEClientConfig.flightSpeed::get,
                v -> { EBEClientConfig.flightSpeed.set(v.doubleValue()); EBEClientConfig.SPEC.save(); },
                1.0, true
        ).setRange(0.05, 50.0).setWheel(0.1));

        group.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.history_max").getString(),
                EBEClientConfig.historyMaxEntries::get,
                v -> { EBEClientConfig.historyMaxEntries.set(v.intValue()); EBEClientConfig.SPEC.save(); },
                100, false
        ).setRange(0, 10000).setWheel(10));

        group.addConfigurator(new SelectorConfigurator<>(
                Component.translatable("ebe.settings.viewport_shader_mode").getString(),
                () -> normalizeViewportShaderMode(EBEClientConfig.viewportShaderMode.get()),
                v -> {
                    EBEClientConfig.viewportShaderMode.set(v);
                    EBEClientConfig.SPEC.save();
                    ViewportFactory.onShaderModeChanged();
                },
                "auto", false,
                List.of("off", "auto", "iris"),
                mode -> Component.translatable("ebe.settings.viewport_shader_mode." + mode).getString()
        ));

        scroller.addScrollViewChild(group);

        var performanceGroup = new ConfiguratorGroup(Component.translatable("ebe.settings.viewport_performance").getString(), false);

        performanceGroup.addConfigurator(new SelectorConfigurator<>(
                Component.translatable("ebe.settings.viewport_performance.mode").getString(),
                () -> normalizeViewportPerformanceMode(EBEClientConfig.viewportPerformanceMode.get()),
                v -> {
                    EBEClientConfig.viewportPerformanceMode.set(v);
                    EBEClientConfig.SPEC.save();
                    ViewportFactory.onViewportPerformanceSettingsChanged();
                },
                "balanced", false,
                List.of("quality", "balanced", "performance"),
                mode -> Component.translatable("ebe.settings.viewport_performance.mode." + mode).getString()
        ));

        performanceGroup.addConfigurator(new BooleanConfigurator(
                Component.translatable("ebe.settings.viewport_performance.degrade_while_moving").getString(),
                EBEClientConfig.viewportDegradeWhileMoving::get,
                v -> {
                    EBEClientConfig.viewportDegradeWhileMoving.set(v);
                    EBEClientConfig.SPEC.save();
                    ViewportFactory.onViewportPerformanceSettingsChanged();
                },
                true, false
        ));

        performanceGroup.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.viewport_performance.render_distance").getString(),
                EBEClientConfig.viewportRenderDistance::get,
                v -> {
                    EBEClientConfig.viewportRenderDistance.set(v.intValue());
                    EBEClientConfig.SPEC.save();
                    ViewportFactory.onViewportPerformanceSettingsChanged();
                },
                0, false
        ).setRange(0, Integer.MAX_VALUE).setWheel(32));

        performanceGroup.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.viewport_performance.compile_budget").getString(),
                EBEClientConfig.viewportCompileBudgetMs::get,
                v -> {
                    EBEClientConfig.viewportCompileBudgetMs.set(v.doubleValue());
                    EBEClientConfig.SPEC.save();
                },
                2.5, true
        ).setRange(0.0, 20.0).setWheel(0.25));

        performanceGroup.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.viewport_performance.moving_compile_budget").getString(),
                EBEClientConfig.viewportMovingCompileBudgetMs::get,
                v -> {
                    EBEClientConfig.viewportMovingCompileBudgetMs.set(v.doubleValue());
                    EBEClientConfig.SPEC.save();
                },
                0.25, true
        ).setRange(0.0, 20.0).setWheel(0.25));

        performanceGroup.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.viewport_performance.load_budget").getString(),
                EBEClientConfig.viewportLoadBudgetMs::get,
                v -> {
                    EBEClientConfig.viewportLoadBudgetMs.set(v.doubleValue());
                    EBEClientConfig.SPEC.save();
                },
                2.0, true
        ).setRange(0.25, 20.0).setWheel(0.25));

        performanceGroup.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.viewport_performance.moving_load_budget").getString(),
                EBEClientConfig.viewportMovingLoadBudgetMs::get,
                v -> {
                    EBEClientConfig.viewportMovingLoadBudgetMs.set(v.doubleValue());
                    EBEClientConfig.SPEC.save();
                },
                0.75, true
        ).setRange(0.0, 20.0).setWheel(0.25));

        performanceGroup.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.viewport_performance.load_blocks").getString(),
                EBEClientConfig.viewportLoadBlocksPerFrame::get,
                v -> {
                    EBEClientConfig.viewportLoadBlocksPerFrame.set(v.intValue());
                    EBEClientConfig.SPEC.save();
                },
                2048, false
        ).setRange(128, 16384).setWheel(256));

        performanceGroup.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.viewport_performance.fallback_blocks").getString(),
                EBEClientConfig.viewportFallbackBlocks::get,
                v -> {
                    EBEClientConfig.viewportFallbackBlocks.set(v.intValue());
                    EBEClientConfig.SPEC.save();
                },
                1536, false
        ).setRange(0, 16384).setWheel(256));

        performanceGroup.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.viewport_performance.moving_fallback_blocks").getString(),
                EBEClientConfig.viewportMovingFallbackBlocks::get,
                v -> {
                    EBEClientConfig.viewportMovingFallbackBlocks.set(v.intValue());
                    EBEClientConfig.SPEC.save();
                },
                128, false
        ).setRange(0, 16384).setWheel(128));

        performanceGroup.addConfigurator(new BooleanConfigurator(
                Component.translatable("ebe.settings.viewport_performance.dynamic_fbo").getString(),
                EBEClientConfig.viewportDynamicFboScale::get,
                v -> {
                    EBEClientConfig.viewportDynamicFboScale.set(v);
                    EBEClientConfig.SPEC.save();
                    ViewportFactory.onViewportPerformanceSettingsChanged();
                },
                true, false
        ));

        performanceGroup.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.viewport_performance.moving_fbo_scale").getString(),
                EBEClientConfig.viewportMovingFboScale::get,
                v -> {
                    EBEClientConfig.viewportMovingFboScale.set(v.doubleValue());
                    EBEClientConfig.SPEC.save();
                    ViewportFactory.onViewportPerformanceSettingsChanged();
                },
                0.65, true
        ).setRange(0.25, 1.0).setWheel(0.05));

        scroller.addScrollViewChild(performanceGroup);

        var probeButton = new Button()
                .setText(Component.translatable("ebe.settings.shader_probe"))
                .setOnClick(e -> ViewportFactory.loadShaderProbeScene());
        probeButton.layout(l -> l.widthPercent(100).height(24));
        scroller.addScrollViewChild(probeButton);

        return scroller;
    }

    private static String normalizeViewportShaderMode(String mode) {
        if ("off".equals(mode) || "auto".equals(mode) || "iris".equals(mode)) {
            return mode;
        }
        EBEClientConfig.viewportShaderMode.set("off");
        EBEClientConfig.SPEC.save();
        return "off";
    }

    private static String normalizeViewportPerformanceMode(String mode) {
        if ("quality".equals(mode) || "balanced".equals(mode) || "performance".equals(mode)) {
            return mode;
        }
        EBEClientConfig.viewportPerformanceMode.set("balanced");
        EBEClientConfig.SPEC.save();
        return "balanced";
    }

    private static UIElement createProjectionTab() {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));

        var group = new ConfiguratorGroup(Component.translatable("ebe.settings.projection").getString(), false);

        group.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.opacity").getString(),
                EBEClientConfig.projectionOpacity::get,
                v -> { EBEClientConfig.projectionOpacity.set(v.doubleValue()); EBEClientConfig.SPEC.save(); },
                0.5, false
        ).setRange(0.0, 1.0).setWheel(0.05));

        group.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.render_distance").getString(),
                EBEClientConfig.projectionRenderDistance::get,
                v -> { EBEClientConfig.projectionRenderDistance.set(v.intValue()); EBEClientConfig.SPEC.save(); },
                64, false
        ).setRange(0, Integer.MAX_VALUE).setWheel(16));

        scroller.addScrollViewChild(group);
        return scroller;
    }

    private static UIElement createPrinterTab() {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));

        var group = new ConfiguratorGroup(Component.translatable("ebe.settings.printer").getString(), false);

        group.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.auto_range").getString(),
                EBEClientConfig.printerRange::get,
                v -> { EBEClientConfig.printerRange.set(v.intValue()); EBEClientConfig.SPEC.save(); },
                3, false
        ).setRange(0, Integer.MAX_VALUE).setWheel(1));

        group.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.settings.material_source_range").getString(),
                EBEClientConfig.printerMaterialSourceRange::get,
                v -> { EBEClientConfig.printerMaterialSourceRange.set(v.intValue()); EBEClientConfig.SPEC.save(); },
                64, false
        ).setRange(0, Integer.MAX_VALUE).setWheel(16));

        group.addConfigurator(new NumberConfigurator(
                Component.translatable("ebe.printer.parallelism").getString(),
                EBEClientConfig.printerParallelism::get,
                v -> { EBEClientConfig.printerParallelism.set(v.intValue()); EBEClientConfig.SPEC.save(); },
                1, false
        ).setRange(1, 8).setWheel(1));

        scroller.addScrollViewChild(group);
        return scroller;
    }

    private static UIElement createKeybindingsTab() {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));

        Map<String, List<EBEKeyBinding>> byCategory = EBEKeyBindings.getByCategory();
        String[] catOrder = {EBEKeyBindings.CAT_EDIT, EBEKeyBindings.CAT_TOOLS, EBEKeyBindings.CAT_FLIGHT, EBEKeyBindings.CAT_MOUSE, EBEKeyBindings.CAT_REMOTE};

        for (String cat : catOrder) {
            List<EBEKeyBinding> bindings = byCategory.get(cat);
            if (bindings == null || bindings.isEmpty()) continue;

            var group = new ConfiguratorGroup(Component.translatable(cat).getString(), false);

            for (EBEKeyBinding binding : bindings) {
                var row = createKeybindingRow(binding);
                group.addConfigurator(new com.lowdragmc.lowdraglib2.configurator.ui.Configurator("")
                        .addInlineChild(row));
            }

            scroller.addScrollViewChild(group);
        }

        var resetAllBtn = new Button()
                .setText(Component.translatable("ebe.settings.keybindings.reset_all"))
                .setOnClick(e -> {
                    for (var b : EBEKeyBindings.getAll()) b.resetToDefault();
                    EBEClientConfig.saveAllKeybindings();
                    EditorUI.refreshKeybindHints();
                });
        resetAllBtn.layout(l -> l.widthPercent(100).height(24).marginTop(8));
        scroller.addScrollViewChild(resetAllBtn);

        return scroller;
    }

    private static UIElement createKeybindingRow(EBEKeyBinding binding) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).height(24).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).paddingHorizontal(4));

        var nameLbl = new Label();
        nameLbl.setText(Component.translatable(binding.getId()));
        nameLbl.layout(l -> l.flex(1).height(20));
        row.addChild(nameLbl);

        var bindBtn = new Button();
        bindBtn.setText(Component.literal(binding.getDisplayName()));
        bindBtn.layout(l -> l.width(120).height(20).flexShrink(0));
        bindBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                bindBtn.setText(Component.translatable("ebe.settings.keybindings.recording")
                        .withStyle(ChatFormatting.YELLOW));
                KeyRecordingManager.startRecording(binding, () -> {
                    bindBtn.setText(Component.literal(binding.getDisplayName()));
                    EditorUI.refreshKeybindHints();
                });
            }
        });
        row.addChild(bindBtn);

        var resetBtn = new Button();
        resetBtn.setText(Component.literal("R").withStyle(ChatFormatting.GRAY));
        resetBtn.layout(l -> l.width(24).height(20).flexShrink(0).marginLeft(4));
        resetBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                binding.resetToDefault();
                bindBtn.setText(Component.literal(binding.getDisplayName()));
                EBEClientConfig.saveAllKeybindings();
                EditorUI.refreshKeybindHints();
            }
        });
        row.addChild(resetBtn);

        return row;
    }
}
