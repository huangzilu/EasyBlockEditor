package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.config.EBEClientConfig;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

public class ImportDialog {

    public static void show(UIElement parent, Consumer<Path> onFileSelected) {
        var dir = new File(EBEClientConfig.schematicDir.get());
        Dialog.showFileDialog("ebe.editor.import", dir, true,
                Dialog.suffixFilter(".litematic", ".schem", ".nbt", ".schematic", ".ebe"),
                file -> {
                    if (file != null && file.isFile()) {
                        onFileSelected.accept(file.toPath());
                    }
                }).show(parent);
    }
}
