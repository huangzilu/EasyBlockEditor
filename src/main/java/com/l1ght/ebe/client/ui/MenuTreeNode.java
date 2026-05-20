package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.util.ITreeNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MenuTreeNode implements ITreeNode<String, Runnable> {
    private final String key;
    private final Runnable action;
    private final List<MenuTreeNode> children = new ArrayList<>();
    private MenuTreeNode parent;

    public MenuTreeNode(String key) {
        this(key, null);
    }

    public MenuTreeNode(String key, @Nullable Runnable action) {
        this.key = key;
        this.action = action;
    }

    public MenuTreeNode child(String key, @Nullable Runnable action) {
        var node = new MenuTreeNode(key, action);
        node.parent = this;
        children.add(node);
        return node;
    }

    public MenuTreeNode child(String key) {
        return child(key, null);
    }

    @Override
    public int getDimension() {
        return parent == null ? 0 : parent.getDimension() + 1;
    }

    @Override
    @Nonnull
    public String getKey() {
        return key;
    }

    @Override
    @Nullable
    public Runnable getContent() {
        return action;
    }

    @Override
    @Nullable
    public ITreeNode<String, Runnable> getParent() {
        return parent;
    }

    @Override
    @Nonnull
    public List<MenuTreeNode> getChildren() {
        return children;
    }
}
