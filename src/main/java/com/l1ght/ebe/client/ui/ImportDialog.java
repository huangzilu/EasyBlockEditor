package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.io.FileManager;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class ImportDialog {

    public static void show(UIElement parent, Consumer<Path> onFileSelected) {
        var dialog = new Dialog();
        dialog.setTitle("Import");
        dialog.overlay.layout(l -> l.width(300).heightPercent(60));

        var list = new ScrollerView();
        list.layout(l -> l.widthPercent(100).flex(1));

        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));

        var dir = Path.of(EBEClientConfig.schematicDir.get());
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(file -> {
                            var ext = FileManager.getFileExtension(file).toLowerCase();
                            if (!FileManager.SUPPORTED_EXTENSIONS.contains(ext) && !ext.equals(".ebe")) return;

                            var item = new Button();
                            item.setText(Component.literal(file.getFileName().toString()));
                            item.layout(l -> l.widthPercent(100).height(18));
                            item.addEventListener(UIEvents.CLICK, e -> {
                                onFileSelected.accept(file);
                                dialog.close();
                            });
                            container.addChild(item);
                        });
            } catch (Exception ignored) {
            }
        }

        if (container.getChildren().isEmpty()) {
            var empty = new Label();
            empty.setText(Component.literal("No files found"));
            container.addChild(empty);
        }

        list.addScrollViewChild(container);
        dialog.addContent(list);

        dialog.addButton(new Button()
                .setText(Component.literal("Cancel"))
                .setOnClick(e -> dialog.close()));

        dialog.show(parent);
    }
}
