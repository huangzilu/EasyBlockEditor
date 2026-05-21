package com.l1ght.ebe.editor.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryManager {
    private final List<HistoryEntry> undoStack = new ArrayList<>();
    private final List<HistoryEntry> redoStack = new ArrayList<>();
    private final int maxSize;
    private int nextId;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public HistoryManager(int maxSize) {
        this.maxSize = maxSize;
        this.nextId = 0;
    }

    public void push(HistoryEntry entry) {
        undoStack.add(entry);
        redoStack.clear();
        if (undoStack.size() > maxSize) undoStack.removeFirst();
    }

    public HistoryEntry undo() {
        if (undoStack.isEmpty()) return null;
        var entry = undoStack.removeLast();
        redoStack.add(entry);
        return entry;
    }

    public HistoryEntry redo() {
        if (redoStack.isEmpty()) return null;
        var entry = redoStack.removeLast();
        undoStack.add(entry);
        return entry;
    }

    public int goToEntryCount(int displayIdx) {
        int targetIndex = displayIdx - 1;
        return Math.max(0, undoStack.size() - 1 - targetIndex);
    }

    public List<HistoryEntry> popUndoEntries(int count) {
        var entries = new ArrayList<HistoryEntry>();
        for (int i = 0; i < count; i++) {
            if (undoStack.isEmpty()) break;
            entries.add(undoStack.removeLast());
        }
        return entries;
    }

    public int nextId() { return nextId++; }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    public int undoSize() { return undoStack.size(); }
    public int redoSize() { return redoStack.size(); }
    public List<HistoryEntry> getUndoEntries() { return Collections.unmodifiableList(undoStack); }
    public List<HistoryEntry> getRedoEntries() { return Collections.unmodifiableList(redoStack); }
    public void clear() { undoStack.clear(); redoStack.clear(); nextId = 0; }

    private static String stateToString(Object state) {
        if (state instanceof BlockState bs) {
            var key = BuiltInRegistries.BLOCK.getKey(bs.getBlock());
            var props = bs.getValues();
            if (props.isEmpty()) return key.toString();
            var sb = new StringBuilder(key.toString()).append('[');
            boolean first = true;
            for (var e : props.entrySet()) {
                if (!first) sb.append(',');
                sb.append(e.getKey().getName()).append('=').append(e.getValue());
                first = false;
            }
            return sb.append(']').toString();
        }
        return state != null ? state.toString() : "minecraft:air";
    }

    public void saveHistory(Path file) {
        try {
            var json = new JsonObject();
            json.addProperty("nextId", nextId);

            var undoArr = new JsonArray();
            for (var entry : undoStack) {
                var eObj = new JsonObject();
                eObj.addProperty("id", entry.getId());
                eObj.addProperty("actionType", entry.getActionType().name());
                eObj.addProperty("primaryX", entry.getPrimaryX());
                eObj.addProperty("primaryY", entry.getPrimaryY());
                eObj.addProperty("primaryZ", entry.getPrimaryZ());
                eObj.addProperty("primaryBlock", stateToString(entry.getPrimaryBlock()));
                eObj.addProperty("affectedCount", entry.getAffectedCount());

                var snapArr = new JsonArray();
                for (var s : entry.getSnapshots()) {
                    var sArr = new JsonArray();
                    sArr.add((int) s[0]);
                    sArr.add((int) s[1]);
                    sArr.add((int) s[2]);
                    sArr.add(stateToString(s[3]));
                    sArr.add(stateToString(s[4]));
                    snapArr.add(sArr);
                }
                eObj.add("snapshots", snapArr);
                undoArr.add(eObj);
            }
            json.add("undoStack", undoArr);

            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(json, w);
            }
        } catch (Exception ignored) {}
    }

    public void loadHistory(Path file) {
        try {
            if (!Files.exists(file)) return;
            JsonObject json;
            try (Reader r = Files.newBufferedReader(file)) {
                json = GSON.fromJson(r, JsonObject.class);
            }
            if (json == null) return;

            undoStack.clear();
            redoStack.clear();
            nextId = json.get("nextId").getAsInt();

            var undoArr = json.getAsJsonArray("undoStack");
            for (var elem : undoArr) {
                var eObj = elem.getAsJsonObject();
                int id = eObj.get("id").getAsInt();
                var type = HistoryActionType.valueOf(eObj.get("actionType").getAsString());
                int px = eObj.get("primaryX").getAsInt();
                int py = eObj.get("primaryY").getAsInt();
                int pz = eObj.get("primaryZ").getAsInt();
                String pBlock = eObj.get("primaryBlock").getAsString();
                int count = eObj.get("affectedCount").getAsInt();

                var snapArr = eObj.getAsJsonArray("snapshots");
                var snapshots = new Object[snapArr.size()][5];
                int si = 0;
                for (var sElem : snapArr) {
                    var sArr = sElem.getAsJsonArray();
                    snapshots[si][0] = sArr.get(0).getAsInt();
                    snapshots[si][1] = sArr.get(1).getAsInt();
                    snapshots[si][2] = sArr.get(2).getAsInt();
                    snapshots[si][3] = sArr.get(3).getAsString();
                    snapshots[si][4] = sArr.get(4).getAsString();
                    si++;
                }

                undoStack.add(new HistoryEntry(id, type, snapshots, px, py, pz, pBlock, count));
            }
        } catch (Exception ignored) {}
    }
}
