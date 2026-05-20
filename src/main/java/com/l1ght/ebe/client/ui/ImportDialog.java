package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.io.FileManager;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public class ImportDialog {

    public static void show(UIElement parent, Consumer<Path> onFileSelected) {
        new Thread(() -> {
            var chooser = new JFileChooser();
            chooser.setDialogTitle("Import Schematic");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "Schematic Files (*.litematic, *.schem, *.nbt, *.schematic, *.ebe)",
                    "litematic", "schem", "nbt", "schematic", "ebe"));

            var result = chooser.showOpenDialog(null);
            if (result != JFileChooser.APPROVE_OPTION) return;

            var dir = Path.of(EBEClientConfig.schematicDir.get());
            try {
                Files.createDirectories(dir);
            } catch (Exception ignored) {}

            for (var file : chooser.getSelectedFiles()) {
                var src = file.toPath();
                var fileName = src.getFileName().toString();
                var dest = dir.resolve(fileName);

                try {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    onFileSelected.accept(dest);
                } catch (Exception ignored) {}
            }
        }).start();
    }
}
