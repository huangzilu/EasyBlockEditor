package com.l1ght.ebe.client.ui;

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
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.List;

public class SettingsUI {

    public static void showSettings(UIElement parent) {
        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.editor.settings").getString());
        dialog.setAutoClose(false);
        dialog.overlay.layout(l -> l.width(380).heightPercent(80));

        var tabView = new TabView();
        tabView.layout(l -> l.flexDirection(FlexDirection.COLUMN).flex(1).widthPercent(100));

        var header = tabView.tabHeaderContainer;
        var content = tabView.tabContentContainer;
        tabView.removeChild(header);
        tabView.removeChild(content);
        tabView.addChild(header);
        tabView.addChild(content);

        tabView.addTab(new Tab().setText(Component.translatable("ebe.editor.settings.general")),
                createGeneralTab());
        tabView.addTab(new Tab().setText(Component.translatable("ebe.editor.settings.editor")),
                createEditorTab());
        tabView.addTab(new Tab().setText(Component.translatable("ebe.editor.settings.projection")),
                createProjectionTab());
        tabView.addTab(new Tab().setText(Component.translatable("ebe.editor.settings.printer")),
                createPrinterTab());

        dialog.addContent(tabView);

        dialog.addButton(new Button()
                .setText(Component.translatable("ebe.editor.settings.ok"))
                .setOnClick(e -> dialog.close()));

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
        ).setRange(16, 256).setWheel(16));

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
        ).setRange(1, 16).setWheel(1));

        scroller.addScrollViewChild(group);
        return scroller;
    }
}
