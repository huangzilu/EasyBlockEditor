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
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.List;

public class SettingsUI {

    public static void showSettings(UIElement parent) {
        var dialog = new Dialog();
        dialog.setTitle("Settings");
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
                .setText(Component.literal("OK"))
                .setOnClick(e -> dialog.close()));

        dialog.show(parent);
    }

    private static UIElement createGeneralTab() {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));

        var group = new ConfiguratorGroup("General", false);

        group.addConfigurator(new StringConfigurator(
                "Schematic Directory",
                EBEClientConfig.schematicDir::get,
                v -> EBEClientConfig.schematicDir.set(v),
                "config/ebe/client/schematics", false
        ));

        group.addConfigurator(new SelectorConfigurator<>(
                "Theme",
                () -> EBEClientConfig.theme.get(),
                v -> EBEClientConfig.theme.set(v),
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

        var group = new ConfiguratorGroup("Editor", false);

        group.addConfigurator(new NumberConfigurator(
                "FOV",
                EBEClientConfig.editorFov::get,
                v -> EBEClientConfig.editorFov.set(v.doubleValue()),
                60.0, false
        ).setRange(30.0, 120.0).setWheel(5.0));

        group.addConfigurator(new NumberConfigurator(
                "Flight Speed",
                EBEClientConfig.flightSpeed::get,
                v -> EBEClientConfig.flightSpeed.set(v.doubleValue()),
                10.0, false
        ).setRange(1.0, 50.0).setWheel(1.0));

        scroller.addScrollViewChild(group);
        return scroller;
    }

    private static UIElement createProjectionTab() {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));

        var group = new ConfiguratorGroup("Projection", false);

        group.addConfigurator(new NumberConfigurator(
                "Opacity",
                EBEClientConfig.projectionOpacity::get,
                v -> EBEClientConfig.projectionOpacity.set(v.doubleValue()),
                0.5, false
        ).setRange(0.0, 1.0).setWheel(0.05));

        group.addConfigurator(new NumberConfigurator(
                "Render Distance",
                EBEClientConfig.projectionRenderDistance::get,
                v -> EBEClientConfig.projectionRenderDistance.set(v.intValue()),
                64, false
        ).setRange(16, 256).setWheel(16));

        scroller.addScrollViewChild(group);
        return scroller;
    }

    private static UIElement createPrinterTab() {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));

        var group = new ConfiguratorGroup("Printer", false);

        group.addConfigurator(new NumberConfigurator(
                "Auto Range",
                EBEClientConfig.printerRange::get,
                v -> EBEClientConfig.printerRange.set(v.intValue()),
                3, false
        ).setRange(1, 16).setWheel(1));

        scroller.addScrollViewChild(group);
        return scroller;
    }
}
