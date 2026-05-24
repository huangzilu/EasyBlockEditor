package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.projection.ProjectionLoadProfile;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public class EditorSession {
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

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

    public void restoreModel(BuildingModel restoredModel, boolean markDirty) {
        if (restoredModel == null) return;
        this.model = restoredModel.deepCopy();
        this.computedProjection = null;
        ProjectionComputePlanner.clearCache();
        if (markDirty) {
            this.dirty = true;
        }
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
        EditorUI.getHistory().clear(this.model);
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
        return new LoadedFile(file, loadedModel, null, null);
    }

    public static LoadedFile readFileWithComputed(Path file) throws Exception {
        LoadedFile loaded = readFile(file);
        ComputedProjection computed = null;
        ProjectionLoadProfile profile = null;
        try {
            if (shouldPreferSynchronousViewportLoad(file)) {
                profile = ProjectionLoadProfile.fromModel(file, loaded.model());
                return new LoadedFile(file, loaded.model(), null, profile);
            }
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
            profile = ProjectionLoadProfile.fromComputed(file, loaded.model(), computed);
        } catch (Throwable t) {
            com.l1ght.ebe.EBEMod.LOGGER.warn("Failed to precompute projection for {}, falling back to legacy viewport load",
                    file.getFileName(), t);
        }
        if (profile == null) {
            profile = ProjectionLoadProfile.fromModel(file, loaded.model());
        }
        return new LoadedFile(file, loaded.model(), computed, profile);
    }

    public static boolean shouldPreferSynchronousViewportLoad(Path file) {
        double thresholdMb = EBEClientConfig.viewportSynchronousLoadBelowMb.get();
        if (thresholdMb <= 0.0D) {
            return true;
        }
        try {
            long thresholdBytes = Math.max(1L, Math.round(thresholdMb * BYTES_PER_MEGABYTE));
            return Files.size(file) <= thresholdBytes;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void applyLoaded(LoadedFile loaded) {
        this.model = loaded.model();
        this.computedProjection = shouldRetainComputedProjection(loaded.profile(), loaded.computed()) ? loaded.computed() : null;
        Path file = loaded.file();
        this.currentFile = file;
        this.dirty = false;
        EditorUI.getHistory().clear(this.model);
        loadHistory();
        EditorUI.refreshHistoryList();
    }

    private static boolean shouldRetainComputedProjection(ProjectionLoadProfile profile, ComputedProjection computed) {
        if (computed == null) return false;
        if (profile != null && profile.isLargeOrAbove()) return false;
        return computed.blockCount() <= 50_000;
    }

    public record LoadedFile(Path file, BuildingModel model, ComputedProjection computed, ProjectionLoadProfile profile) {
        public LoadedFile(Path file, BuildingModel model) {
            this(file, model, null, null);
        }
    }

    private static String fileComputeKey(Path file) throws Exception {
        Path absolute = file.toAbsolutePath().normalize();
        long size = Files.size(file);
        long modified = Files.getLastModifiedTime(file).toMillis();
        return absolute + "|" + size + "|" + modified;
    }

    private Path getHistoryPath() {
        return Path.of("config", "ebe", "client", "history", getHistoryFileName());
    }

    private String getHistoryFileName() {
        if (currentFile != null) {
            Path identityPath = resolveHistoryIdentityPath(currentFile);
            String fileName = identityPath.getFileName() != null ? identityPath.getFileName().toString() : currentFile.getFileName().toString();
            String readableName = sanitizeHistoryName(stripSupportedExtension(fileName));
            return readableName + "-" + shortHash(identityPath.toString()) + ".json";
        }

        String name = model.getMetadata().getName();
        if (name == null || name.isBlank()) name = "untitled";
        return sanitizeHistoryName(name) + "-unsaved.json";
    }

    private static Path resolveHistoryIdentityPath(Path file) {
        try {
            return file.toRealPath().normalize();
        } catch (Exception ignored) {
            return file.toAbsolutePath().normalize();
        }
    }

    private static String sanitizeHistoryName(String name) {
        String source = name == null || name.isBlank() ? "untitled" : name.strip();
        StringBuilder clean = new StringBuilder(source.length());
        boolean previousSeparator = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-';
            if (allowed) {
                clean.append(c);
                previousSeparator = false;
            } else if (!previousSeparator) {
                clean.append('_');
                previousSeparator = true;
            }
        }
        String result = clean.toString();
        while (result.startsWith(".") || result.startsWith("_")) result = result.substring(1);
        while (result.endsWith(".") || result.endsWith("_")) result = result.substring(0, result.length() - 1);
        if (result.isBlank()) result = "untitled";
        return result.length() > 64 ? result.substring(0, 64) : result;
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception ignored) {
            return Integer.toUnsignedString(value.hashCode(), 16);
        }
    }

    void saveHistory() {
        EditorUI.getHistory().saveHistory(getHistoryPath(), model);
    }

    private void backupCurrentFile() throws Exception {
        if (currentFile == null || !Files.exists(currentFile)) return;
        Path backup = currentFile.resolveSibling(currentFile.getFileName().toString() + ".bak");
        Files.copy(currentFile, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void loadHistory() {
        EditorUI.getHistory().loadHistory(getHistoryPath(), model);
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
