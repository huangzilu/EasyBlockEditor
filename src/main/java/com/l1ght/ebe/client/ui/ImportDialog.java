package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.io.FileManager;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ImportDialog {
    private static final String[] PROJECTION_FILTERS = {"*.ebe", "*.litematic", "*.schem", "*.schematic", "*.nbt"};

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
        var dir = Path.of(EBEClientConfig.schematicDir.get());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            EBEMod.LOGGER.warn("Failed to create schematic import directory {}", dir, e);
        }

        CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var filters = stack.mallocPointer(PROJECTION_FILTERS.length);
                for (String filter : PROJECTION_FILTERS) {
                    filters.put(stack.UTF8(filter));
                }
                filters.flip();
                return TinyFileDialogs.tinyfd_openFileDialog(
                        I18n.get("ebe.editor.import.file_picker"),
                        dir.toAbsolutePath().toString(),
                        filters,
                        I18n.get("ebe.editor.import.supported_formats"),
                        false
                );
            }
        }).whenComplete((selected, error) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (error != null) {
                EBEMod.LOGGER.warn("Import file dialog failed", error);
                return;
            }
            if (selected == null || selected.isBlank()) return;
            File file = new File(selected);
            if (!file.isFile()) return;
            importFile(file.toPath(), onFileSelected);
        }));
    }

    public static void importFile(Path source, Consumer<Path> onFileSelected) {
        if (source == null || !Files.isRegularFile(source)) return;
        if (!FileManager.SUPPORTED_EXTENSIONS.contains(FileManager.getFileExtension(source).toLowerCase())) return;
        try {
            var destDir = Path.of(EBEClientConfig.schematicDir.get());
            Files.createDirectories(destDir);
            var dest = destDir.resolve(source.getFileName().toString());
            if (!source.toAbsolutePath().normalize().equals(dest.toAbsolutePath().normalize())) {
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            onFileSelected.accept(dest);
        } catch (IOException e) {
            EBEMod.LOGGER.warn("Failed to copy imported schematic {}; loading source directly", source, e);
            onFileSelected.accept(source);
        }
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
                    } catch (Exception fallback) {
                        EBEMod.LOGGER.warn("Failed to open schematic folder {}", dir, fallback);
                    }
                }
            });
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to prepare schematic folder", e);
        }
    }
}
