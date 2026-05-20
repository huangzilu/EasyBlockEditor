package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class EditorDialogs {

    public static Dialog newProjectDialog(UIElement parent, Consumer<String> onConfirm) {
        var dialog = Dialog.stringEditorDialog(
                "New Project",
                "untitled",
                s -> !s.isBlank() && !s.matches(".*[\\\\/:*?\"<>|].*"),
                onConfirm
        );
        dialog.overlay.layout(l -> l.width(220));
        dialog.show(parent);
        return dialog;
    }

    public static Dialog saveAsDialog(UIElement parent, String currentName, Consumer<String> onConfirm) {
        var dialog = Dialog.stringEditorDialog(
                "Save As",
                currentName,
                s -> !s.isBlank() && !s.matches(".*[\\\\/:*?\"<>|].*"),
                onConfirm
        );
        dialog.overlay.layout(l -> l.width(220));
        dialog.show(parent);
        return dialog;
    }

    public static Dialog overwriteConfirmDialog(UIElement parent, String filename, Runnable onConfirm) {
        var dialog = new Dialog();
        dialog.setTitle("Confirm");
        dialog.overlay.layout(l -> l.width(250));

        var msg = new Label();
        msg.setText(Component.translatable("ebe.editor.overwrite_confirm"));
        msg.textStyle(ts -> ts.adaptiveWidth(true));
        dialog.addContent(msg);

        var filenameLabel = new Label();
        filenameLabel.setText(Component.literal(filename));
        filenameLabel.textStyle(ts -> ts.adaptiveWidth(true));
        dialog.addContent(filenameLabel);

        dialog.addButton(new Button()
                .setOnClick(e -> {
                    onConfirm.run();
                    dialog.close();
                })
                .setText(Component.literal("OK")));

        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText(Component.literal("Cancel")));

        dialog.show(parent);
        return dialog;
    }

    public static Dialog confirmDialog(UIElement parent, Component message, Runnable onConfirm) {
        var dialog = new Dialog();
        dialog.setTitle("Confirm");
        dialog.overlay.layout(l -> l.width(280));

        var msg = new Label();
        msg.setText(message);
        msg.textStyle(ts -> ts.adaptiveWidth(true));
        dialog.addContent(msg);

        dialog.addButton(new Button()
                .setOnClick(e -> {
                    onConfirm.run();
                    dialog.close();
                })
                .setText(Component.literal("OK")));

        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText(Component.literal("Cancel")));

        dialog.show(parent);
        return dialog;
    }
}
