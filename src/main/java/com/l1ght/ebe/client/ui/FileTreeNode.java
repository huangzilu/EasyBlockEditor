package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.util.ITreeNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileTreeNode implements ITreeNode<Path, Path> {
    private final Path path;
    private final boolean directory;
    private final List<FileTreeNode> children = new ArrayList<>();
    private FileTreeNode parent;

    public FileTreeNode(Path path, boolean directory) {
        this.path = path;
        this.directory = directory;
    }

    public static FileTreeNode ofDirectory(Path dir) {
        return new FileTreeNode(dir, true);
    }

    public static FileTreeNode ofFile(Path file) {
        return new FileTreeNode(file, false);
    }

    public FileTreeNode addChild(FileTreeNode child) {
        child.parent = this;
        children.add(child);
        return this;
    }

    public Path getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    @Override
    public int getDimension() {
        return parent == null ? 0 : parent.getDimension() + 1;
    }

    @Override
    @Nonnull
    public Path getKey() {
        return path;
    }

    @Override
    @Nullable
    public Path getContent() {
        return path;
    }

    @Override
    @Nullable
    public ITreeNode<Path, Path> getParent() {
        return parent;
    }

    @Override
    @Nonnull
    public List<FileTreeNode> getChildren() {
        return children;
    }
}
