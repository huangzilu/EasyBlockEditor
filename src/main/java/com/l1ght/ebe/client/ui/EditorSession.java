package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.projection.compute.ComputedProjection;
import com.l1ght.ebe.projection.compute.ProjectionComputePlanner;
import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.io.EBEFormatIO;
import com.l1ght.ebe.data.io.FileManager;
import com.l1ght.ebe.data.io.SchematicReaders;
import com.l1ght.ebe.data.io.SchematicWriters;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.nio.file.Files;
import java.nio.file.Path;

public class EditorSession {
    private BuildingModel model;
    private Path currentFile;
    private ComputedProjection computedProjection;
    private boolean dirty;

    public EditorSession() {
        this.model = new BuildingModel();
        this.dirty = false;
    }

    public BuildingModel getModel() { return model; }

    public ComputedProjection getComputedProjection() { return computedProjection; }

    public Path getCurrentFile() { return currentFile; }

    public boolean isDirty() { return dirty; }

    public void markDirty() {
        this.dirty = true;
        this.computedProjection = null;
        ProjectionComputePlanner.clearCache();
    }

    public String getCurrentName() {
        return currentFile != null ? currentFile.getFileName().toString() : "untitled";
    }

    public void newProject(String name) {
        this.model = new BuildingModel();
        this.computedProjection = null;
        this.model.getMetadata().setName(name);
        this.currentFile = null;
        this.dirty = false;
        EditorUI.getHistory().clear();
        EditorUI.refreshHistoryList();
    }

    public void save() throws Exception {
        if (currentFile == null) throw new IllegalStateException("No file set, use saveAs");
        model.getMetadata().setModified(System.currentTimeMillis());
        backupCurrentFile();
        SchematicWriters.write(model, currentFile);
        saveHistory();
        dirty = false;
    }

    public void saveAs(String name) throws Exception {
        var dir = Path.of(EBEClientConfig.schematicDir.get());
        Files.createDirectories(dir);
        String ext = FileManager.getFileExtension(Path.of(name)).toLowerCase();
        var filename = FileManager.SUPPORTED_EXTENSIONS.contains(ext) ? name : name + ".ebe";
        this.currentFile = dir.resolve(filename);
        model.getMetadata().setName(stripSupportedExtension(filename));
        save();
    }

    public void load(Path file) throws Exception {
        applyLoaded(readFile(file));
    }

    public static LoadedFile readFile(Path file) throws Exception {
        var ext = FileManager.getFileExtension(file).toLowerCase();
        var loadedModel = switch (ext) {
            case ".ebe" -> EBEFormatIO.read(file);
            case ".litematic" -> SchematicReaders.readLitematic(file);
            case ".nbt" -> SchematicReaders.readNbtStructure(file);
            case ".schem" -> SchematicReaders.readSpongeSchem(file);
            case ".schematic" -> SchematicReaders.readSchematica(file);
            default -> throw new UnsupportedOperationException("Unknown format: " + ext);
        };
        return new LoadedFile(file, loadedModel);
    }

    public static LoadedFile readFileWithComputed(Path file) throws Exception {
        LoadedFile loaded = readFile(file);
        ComputedProjection computed = null;
        try {
            String cacheKey = fileComputeKey(file);
            computed = ProjectionComputePlanner.computeAsync(
                    cacheKey,
                    loaded.model(),
                    BlockPos.ZERO,
                    Rotation.NONE,
                    Mirror.NONE,
                    BlockPos.ZERO,
                    true
            ).join();
        } catch (Throwable t) {
            com.l1ght.ebe.EBEMod.LOGGER.warn("Failed to precompute projection for {}, falling back to legacy viewport load",
                    file.getFileName(), t);
        }
        return new LoadedFile(file, loaded.model(), computed);
    }

    public void applyLoaded(LoadedFile loaded) {
        this.model = loaded.model();
        this.computedProjection = loaded.computed();
        Path file = loaded.file();
        this.currentFile = file;
        this.dirty = false;
        EditorUI.getHistory().clear();
        loadHistory();
        EditorUI.refreshHistoryList();
    }

    public record LoadedFile(Path file, BuildingModel model, ComputedProjection computed) {
        public LoadedFile(Path file, BuildingModel model) {
            this(file, model, null);
        }
    }

    private static String fileComputeKey(Path file) throws Exception {
        Path absolute = file.toAbsolutePath().normalize();
        long size = Files.size(file);
        long modified = Files.getLastModifiedTime(file).toMillis();
        return absolute + "|" + size + "|" + modified;
    }

    private Path getHistoryPath() {
        String name = model.getMetadata().getName();
        if (name == null || name.isEmpty()) name = "untitled";
        return Path.of("config", "ebe", "client", "history", name + ".json");
    }

    void saveHistory() {
        EditorUI.getHistory().saveHistory(getHistoryPath());
    }

    private void backupCurrentFile() throws Exception {
        if (currentFile == null || !Files.exists(currentFile)) return;
        Path backup = currentFile.resolveSibling(currentFile.getFileName().toString() + ".bak");
        Files.copy(currentFile, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void loadHistory() {
        EditorUI.getHistory().loadHistory(getHistoryPath());
    }

    private static String stripSupportedExtension(String filename) {
        String lower = filename.toLowerCase();
        for (String ext : FileManager.SUPPORTED_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return filename.substring(0, filename.length() - ext.length());
            }
        }
        return filename;
    }
}
