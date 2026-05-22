package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.config.EBEClientConfig;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public class ImportDialog {

    public static void showOpen(UIElement parent, Consumer<Path> onFileSelected) {
        var dir = Path.of(EBEClientConfig.schematicDir.get()).toFile();
        if (!dir.exists()) dir.mkdirs();
        Dialog.showFileDialog("ebe.editor.open", dir, true,
                Dialog.suffixFilter(".litematic", ".schem", ".nbt", ".schematic", ".ebe"),
                file -> {
                    if (file != null && file.isFile()) {
                        onFileSelected.accept(file.toPath());
                    }
                }).show(parent);
    }

    public static void showImport(UIElement parent, Consumer<Path> onFileSelected) {
        var dir = Path.of(EBEClientConfig.schematicDir.get()).toFile();
        if (!dir.exists()) dir.mkdirs();
        Dialog.showFileDialog("ebe.editor.import", dir, true,
                Dialog.suffixFilter(".litematic", ".schem", ".nbt", ".schematic", ".ebe"),
                file -> {
                    if (file != null && file.isFile()) {
                        try {
                            var destDir = Path.of(EBEClientConfig.schematicDir.get());
                            Files.createDirectories(destDir);
                            var dest = destDir.resolve(file.getName());
                            if (!file.toPath().equals(dest)) {
                                Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                            }
                            onFileSelected.accept(dest);
                        } catch (IOException e) {
                            onFileSelected.accept(file.toPath());
                        }
                    }
                }).show(parent);
    }

    public static void openSchematicFolder() {
        try {
            var dir = Path.of(EBEClientConfig.schematicDir.get());
            Files.createDirectories(dir);
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                try {
                    java.awt.Desktop.getDesktop().open(dir.toFile());
                } catch (Exception e) {
                    try {
                        Runtime.getRuntime().exec("explorer " + dir.toAbsolutePath());
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
