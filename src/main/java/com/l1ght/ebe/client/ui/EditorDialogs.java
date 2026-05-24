package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class EditorDialogs {

    public static Dialog newProjectDialog(UIElement parent, Consumer<String> onConfirm) {
        var dialog = Dialog.stringEditorDialog(
                Component.translatable("ebe.editor.new_project").getString(),
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
                Component.translatable("ebe.editor.save_as").getString(),
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
        dialog.setTitle(Component.translatable("ebe.editor.dialog.confirm").getString());
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
                .setText(Component.translatable("ebe.history.dialog.confirm")));

        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText(Component.translatable("ebe.history.dialog.cancel")));

        dialog.show(parent);
        return dialog;
    }

    public static Dialog confirmDialog(UIElement parent, Component message, Runnable onConfirm) {
        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.editor.dialog.confirm").getString());
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
                .setText(Component.translatable("ebe.history.dialog.confirm")));

        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText(Component.translatable("ebe.history.dialog.cancel")));

        dialog.show(parent);
        return dialog;
    }

    public static Dialog largeProjectionWarningDialog(UIElement parent, String filename, String sizeText, Runnable onConfirm) {
        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.editor.large_projection.title").getString());
        dialog.overlay.layout(l -> l.width(460).maxHeight(260));

        var content = new UIElement();
        content.layout(l -> l.flexDirection(FlexDirection.COLUMN).width(430));

        var headline = new Label();
        headline.setText(Component.translatable("ebe.editor.large_projection.headline", filename, sizeText));
        headline.layout(l -> l.width(420));
        headline.textStyle(ts -> ts.textColor(0xFFFFD166).fontSize(10).textShadow(false)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        content.addChild(headline);

        content.addChild(largeProjectionLine("ebe.editor.large_projection.body.performance"));
        content.addChild(largeProjectionLine("ebe.editor.large_projection.body.wait"));
        content.addChild(largeProjectionLine("ebe.editor.large_projection.body.warning"));
        content.addChild(largeProjectionLine("ebe.editor.large_projection.body.tip"));
        var scroller = new ScrollerView();
        scroller.layout(l -> l.width(430).height(148));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.addScrollViewChild(content);
        dialog.addContent(scroller);

        dialog.addButton(new Button()
                .setOnClick(e -> {
                    onConfirm.run();
                    dialog.close();
                })
                .setText(Component.translatable("ebe.editor.large_projection.continue")));

        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText(Component.translatable("ebe.history.dialog.cancel")));

        dialog.show(parent);
        return dialog;
    }

    private static Label largeProjectionLine(String translationKey) {
        var label = new Label();
        label.setText(Component.translatable(translationKey));
        label.layout(l -> l.width(420).paddingTop(4));
        label.textStyle(ts -> ts.textColor(0xFFE8E8E8).fontSize(9).textShadow(false)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        return label;
    }
}
