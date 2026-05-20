package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.io.EBEFormatIO;

import java.nio.file.Files;
import java.nio.file.Path;

public class EditorSession {
    private BuildingModel model;
    private Path currentFile;
    private boolean dirty;

    public EditorSession() {
        this.model = new BuildingModel();
        this.dirty = false;
    }

    public BuildingModel getModel() { return model; }

    public Path getCurrentFile() { return currentFile; }

    public boolean isDirty() { return dirty; }

    public void markDirty() { this.dirty = true; }

    public String getCurrentName() {
        return currentFile != null ? currentFile.getFileName().toString() : "untitled";
    }

    public void newProject(String name) {
        this.model = new BuildingModel();
        this.model.getMetadata().setName(name);
        this.currentFile = null;
        this.dirty = false;
    }

    public void save() throws Exception {
        if (currentFile == null) throw new IllegalStateException("No file set, use saveAs");
        model.getMetadata().setModified(System.currentTimeMillis());
        EBEFormatIO.write(model, currentFile);
        dirty = false;
    }

    public void saveAs(String name) throws Exception {
        var dir = Path.of(EBEClientConfig.schematicDir.get());
        Files.createDirectories(dir);
        var filename = name.endsWith(".ebe") ? name : name + ".ebe";
        this.currentFile = dir.resolve(filename);
        model.getMetadata().setName(name.replace(".ebe", ""));
        save();
    }

    public void load(Path file) throws Exception {
        this.model = EBEFormatIO.read(file);
        this.currentFile = file;
        this.dirty = false;
    }
}
