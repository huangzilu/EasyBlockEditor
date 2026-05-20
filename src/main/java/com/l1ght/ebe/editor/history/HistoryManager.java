package com.l1ght.ebe.editor.history;

import java.util.ArrayList;
import java.util.List;

public class HistoryManager<T> {
    private final List<T> undoStack = new ArrayList<>();
    private final List<T> redoStack = new ArrayList<>();
    private final int maxSize;

    public HistoryManager(int maxSize) {
        this.maxSize = maxSize;
    }

    public void push(T snapshot) {
        undoStack.add(snapshot);
        redoStack.clear();
        if (undoStack.size() > maxSize) undoStack.removeFirst();
    }

    public T undo() {
        if (undoStack.isEmpty()) return null;
        var snapshot = undoStack.removeLast();
        redoStack.add(snapshot);
        return snapshot;
    }

    public T redo() {
        if (redoStack.isEmpty()) return null;
        var snapshot = redoStack.removeLast();
        undoStack.add(snapshot);
        return snapshot;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    public int undoSize() { return undoStack.size(); }
    public int redoSize() { return redoStack.size(); }
    public void clear() { undoStack.clear(); redoStack.clear(); }
}
