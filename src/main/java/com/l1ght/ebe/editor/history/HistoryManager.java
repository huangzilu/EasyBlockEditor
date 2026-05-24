package com.l1ght.ebe.editor.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.io.EBEFormatIO;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HistoryManager {

    private final List<HistoryEntry> undoStack = new ArrayList<>();
    private final List<HistoryEntry> redoStack = new ArrayList<>();
    private final Map<Integer, HistoryEntry> entriesById = new LinkedHashMap<>();
    private final List<VersionTag> versionTags = new ArrayList<>();
    private final List<Branch> branches = new ArrayList<>();
    private final Map<String, BranchState> branchStates = new LinkedHashMap<>();
    private String currentBranch = "main";
    private final int maxSize;
    private int nextId;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public HistoryManager(int maxSize) {
        this.maxSize = maxSize;
        this.nextId = 0;
        branches.add(new Branch("main", 0));
        saveBranchState("main", null);
    }

    public void push(HistoryEntry entry) {
        undoStack.add(entry);
        entriesById.put(entry.getId(), entry);
        redoStack.clear();
        if (undoStack.size() > maxSize) undoStack.removeFirst();
        updateBranchHead();
    }

    public HistoryEntry getLastEntry() {
        if (undoStack.isEmpty()) return null;
        return undoStack.getLast();
    }

    public HistoryEntry undo() {
        if (undoStack.isEmpty()) return null;
        var entry = undoStack.removeLast();
        redoStack.add(entry);
        updateBranchHead();
        return entry;
    }

    public HistoryEntry redo() {
        if (redoStack.isEmpty()) return null;
        var entry = redoStack.removeLast();
        undoStack.add(entry);
        entriesById.put(entry.getId(), entry);
        updateBranchHead();
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
        updateBranchHead();
        return entries;
    }

    public int nextId() { return nextId++; }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    public int undoSize() { return undoStack.size(); }
    public int redoSize() { return redoStack.size(); }
    public List<HistoryEntry> getUndoEntries() { return Collections.unmodifiableList(undoStack); }
    public List<HistoryEntry> getRedoEntries() { return Collections.unmodifiableList(redoStack); }

    public void clear() {
        clear(null);
    }

    public void clear(BuildingModel currentModel) {
        undoStack.clear();
        redoStack.clear();
        entriesById.clear();
        versionTags.clear();
        branches.clear();
        branchStates.clear();
        nextId = 0;
        currentBranch = "main";
        branches.add(new Branch("main", 0));
        saveBranchState("main", currentModel);
    }

    public VersionTag createVersionTag(String label, String description) {
        int entryId = undoStack.isEmpty() ? 0 : undoStack.getLast().getId();
        var tag = new VersionTag(nextId(), label, description, System.currentTimeMillis(), entryId, currentBranch);
        versionTags.add(tag);
        return tag;
    }

    public void removeVersionTag(int tagId) {
        versionTags.removeIf(t -> t.id() == tagId);
    }

    public List<VersionTag> getVersionTags() {
        return Collections.unmodifiableList(versionTags);
    }

    public List<VersionTag> getVersionTagsForCurrentBranch() {
        var result = new ArrayList<VersionTag>();
        for (var tag : versionTags) {
            if (currentBranch.equals(tag.branch())) result.add(tag);
        }
        return Collections.unmodifiableList(result);
    }

    public Branch createBranch(String name) {
        return createBranch(name, null);
    }

    public Branch createBranch(String name, BuildingModel model) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Branch name is blank");
        for (var existing : branches) {
            if (existing.name().equals(name)) return existing;
        }
        saveBranchState(currentBranch, model);
        int headEntryId = undoStack.isEmpty() ? 0 : undoStack.getLast().getId();
        var branch = new Branch(name, headEntryId);
        branches.add(branch);
        branchStates.put(name, new BranchState(currentUndoIds(), currentRedoIds(),
                model != null ? model.deepCopy() : currentBranchModel()));
        return branch;
    }

    public void switchBranch(String name) {
        switchBranch(name, null);
    }

    public BuildingModel switchBranch(String name, BuildingModel currentModel) {
        for (var b : branches) {
            if (b.name().equals(name)) {
                saveBranchState(currentBranch, currentModel);
                currentBranch = name;
                restoreBranchState(name);
                var state = branchStates.get(name);
                return state != null && state.model() != null ? state.model().deepCopy() : null;
            }
        }
        return null;
    }

    public void captureCurrentBranchModel(BuildingModel model) {
        saveBranchState(currentBranch, model);
    }

    public String getCurrentBranch() { return currentBranch; }
    public List<Branch> getBranches() { return Collections.unmodifiableList(branches); }

    private void saveBranchState(String branchName, BuildingModel model) {
        var existing = branchStates.get(branchName);
        BuildingModel snapshot = model != null ? model.deepCopy() : existing != null ? existing.model() : null;
        branchStates.put(branchName, new BranchState(currentUndoIds(), currentRedoIds(), snapshot));
    }

    private List<Integer> currentUndoIds() {
        var undoIds = new ArrayList<Integer>();
        for (var e : undoStack) undoIds.add(e.getId());
        return undoIds;
    }

    private List<Integer> currentRedoIds() {
        var redoIds = new ArrayList<Integer>();
        for (var e : redoStack) redoIds.add(e.getId());
        return redoIds;
    }

    private BuildingModel currentBranchModel() {
        var state = branchStates.get(currentBranch);
        return state != null && state.model() != null ? state.model().deepCopy() : null;
    }

    private void restoreBranchState(String branchName) {
        var state = branchStates.get(branchName);
        if (state == null) return;

        undoStack.clear();
        redoStack.clear();

        for (int id : state.undoEntryIds()) {
            var e = entriesById.get(id);
            if (e != null) undoStack.add(e);
        }
        for (int id : state.redoEntryIds()) {
            var e = entriesById.get(id);
            if (e != null) redoStack.add(e);
        }
    }

    private void updateBranchHead() {
        for (var b : branches) {
            if (b.name().equals(currentBranch)) {
                int headId = undoStack.isEmpty() ? 0 : undoStack.getLast().getId();
                branches.set(branches.indexOf(b), new Branch(b.name(), headId));
                return;
            }
        }
    }

    public int findEntryIndexById(int entryId) {
        for (int i = 0; i < undoStack.size(); i++) {
            if (undoStack.get(i).getId() == entryId) return i;
        }
        return -1;
    }

    public Map<String, Integer> computeMaterialDiffFromTag(VersionTag tag) {
        int tagEntryIndex = findEntryIndexById(tag.entryId());
        if (tagEntryIndex < 0) return Map.of();

        Map<String, Integer> diff = new LinkedHashMap<>();
        for (int i = tagEntryIndex + 1; i < undoStack.size(); i++) {
            var entry = undoStack.get(i);
            for (var snapshot : entry.getSnapshots()) {
                var oldState = snapshot[3];
                var newState = snapshot[4];
                var oldKey = stateToBlockKey(oldState);
                var newKey = stateToBlockKey(newState);

                if (!oldKey.isEmpty()) diff.merge(oldKey, -1, Integer::sum);
                if (!newKey.isEmpty()) diff.merge(newKey, 1, Integer::sum);
            }
        }
        diff.values().removeIf(v -> v == 0);
        return diff;
    }

    private static String stateToBlockKey(Object state) {
        if (isMinecraftBlockState(state)) {
            BlockState bs = (BlockState) state;
            if (bs.isAir()) return "";
            return BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString();
        }
        if (state instanceof String s && !s.isEmpty() && !s.equals("minecraft:air")) {
            return s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
        }
        return "";
    }

    public record VersionTag(int id, String label, String description, long timestamp, int entryId, String branch) {}
    public record Branch(String name, int headEntryId) {}
    public record BranchState(List<Integer> undoEntryIds, List<Integer> redoEntryIds, BuildingModel model) {}

    private static String stateToString(Object state) {
        if (isMinecraftBlockState(state)) {
            BlockState bs = (BlockState) state;
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

    private static boolean isMinecraftBlockState(Object state) {
        return state != null && "net.minecraft.world.level.block.state.BlockState".equals(state.getClass().getName());
    }

    public void saveHistory(Path file) {
        saveHistory(file, null);
    }

    public void saveHistory(Path file, BuildingModel currentModel) {
        try {
            captureCurrentBranchModel(currentModel);
            var json = new JsonObject();
            json.addProperty("nextId", nextId);
            json.addProperty("currentBranch", currentBranch);

            var allEntries = new JsonArray();
            entriesById.values().stream()
                    .sorted(Comparator.comparingInt(HistoryEntry::getId))
                    .forEach(entry -> allEntries.add(serializeEntry(entry)));
            json.add("allEntries", allEntries);

            var undoArr = new JsonArray();
            for (var entry : undoStack) undoArr.add(serializeEntry(entry));
            json.add("undoStack", undoArr);

            var redoArr = new JsonArray();
            for (var entry : redoStack) redoArr.add(serializeEntry(entry));
            json.add("redoStack", redoArr);

            var tagsArr = new JsonArray();
            for (var tag : versionTags) {
                var tObj = new JsonObject();
                tObj.addProperty("id", tag.id());
                tObj.addProperty("label", tag.label());
                tObj.addProperty("description", tag.description());
                tObj.addProperty("timestamp", tag.timestamp());
                tObj.addProperty("entryId", tag.entryId());
                tObj.addProperty("branch", tag.branch());
                tagsArr.add(tObj);
            }
            json.add("versionTags", tagsArr);

            var branchesArr = new JsonArray();
            for (var b : branches) {
                var bObj = new JsonObject();
                bObj.addProperty("name", b.name());
                bObj.addProperty("headEntryId", b.headEntryId());
                branchesArr.add(bObj);
            }
            json.add("branches", branchesArr);

            var bsArr = new JsonArray();
            for (var entry : branchStates.entrySet()) {
                var bsObj = new JsonObject();
                bsObj.addProperty("branch", entry.getKey());
                var uArr = new JsonArray();
                for (int id : entry.getValue().undoEntryIds()) uArr.add(id);
                bsObj.add("undoIds", uArr);
                var rArr = new JsonArray();
                for (int id : entry.getValue().redoEntryIds()) rArr.add(id);
                bsObj.add("redoIds", rArr);
                if (entry.getValue().model() != null) {
                    bsObj.add("model", EBEFormatIO.toJson(entry.getValue().model()));
                }
                bsArr.add(bsObj);
            }
            json.add("branchStates", bsArr);

            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(json, w);
            }
        } catch (Exception ignored) {}
    }

    public void loadHistory(Path file) {
        loadHistory(file, null);
    }

    public void loadHistory(Path file, BuildingModel currentModel) {
        try {
            if (!Files.exists(file)) return;
            JsonObject json;
            try (Reader r = Files.newBufferedReader(file)) {
                json = GSON.fromJson(r, JsonObject.class);
            }
            if (json == null) return;

            undoStack.clear();
            redoStack.clear();
            entriesById.clear();
            versionTags.clear();
            branches.clear();
            branchStates.clear();
            nextId = json.get("nextId").getAsInt();
            currentBranch = json.has("currentBranch") ? json.get("currentBranch").getAsString() : "main";

            if (json.has("allEntries")) {
                for (var elem : json.getAsJsonArray("allEntries")) {
                    var e = deserializeEntry(elem.getAsJsonObject());
                    entriesById.put(e.getId(), e);
                }
            }

            if (json.has("undoStack")) {
                for (var elem : json.getAsJsonArray("undoStack")) {
                    var e = deserializeEntry(elem.getAsJsonObject());
                    entriesById.putIfAbsent(e.getId(), e);
                    undoStack.add(e);
                }
            }
            if (json.has("redoStack")) {
                for (var elem : json.getAsJsonArray("redoStack")) {
                    var e = deserializeEntry(elem.getAsJsonObject());
                    entriesById.putIfAbsent(e.getId(), e);
                    redoStack.add(e);
                }
            }

            if (json.has("versionTags")) {
                for (var elem : json.getAsJsonArray("versionTags")) {
                    var tObj = elem.getAsJsonObject();
                    versionTags.add(new VersionTag(
                            tObj.get("id").getAsInt(),
                            tObj.get("label").getAsString(),
                            tObj.has("description") ? tObj.get("description").getAsString() : "",
                            tObj.get("timestamp").getAsLong(),
                            tObj.get("entryId").getAsInt(),
                            tObj.has("branch") ? tObj.get("branch").getAsString() : "main"
                    ));
                }
            }

            if (json.has("branches")) {
                for (var elem : json.getAsJsonArray("branches")) {
                    var bObj = elem.getAsJsonObject();
                    branches.add(new Branch(bObj.get("name").getAsString(), bObj.get("headEntryId").getAsInt()));
                }
            }
            if (branches.isEmpty()) branches.add(new Branch("main", 0));

            if (json.has("branchStates")) {
                for (var elem : json.getAsJsonArray("branchStates")) {
                    var bsObj = elem.getAsJsonObject();
                    var branchName = bsObj.get("branch").getAsString();
                    var undoIds = new ArrayList<Integer>();
                    var redoIds = new ArrayList<Integer>();
                    for (var id : bsObj.getAsJsonArray("undoIds")) undoIds.add(id.getAsInt());
                    for (var id : bsObj.getAsJsonArray("redoIds")) redoIds.add(id.getAsInt());
                    BuildingModel model = null;
                    if (bsObj.has("model") && bsObj.get("model").isJsonObject()) {
                        model = EBEFormatIO.fromJson(bsObj.getAsJsonObject("model"));
                    } else if (branchName.equals(currentBranch) && currentModel != null) {
                        model = currentModel.deepCopy();
                    }
                    branchStates.put(branchName, new BranchState(undoIds, redoIds, model));
                }
            }

            if (!branchStates.containsKey(currentBranch)) {
                saveBranchState(currentBranch, currentModel);
            }
            restoreBranchState(currentBranch);
        } catch (Exception ignored) {}
    }

    private JsonObject serializeEntry(HistoryEntry entry) {
        var eObj = new JsonObject();
        eObj.addProperty("id", entry.getId());
        eObj.addProperty("actionType", entry.getActionType().name());
        eObj.addProperty("timestamp", entry.getTimestamp());
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
            if (s.length > 5) {
                sArr.add(nbtToString(s[5]));
                sArr.add(s.length > 6 ? nbtToString(s[6]) : "");
            }
            snapArr.add(sArr);
        }
        eObj.add("snapshots", snapArr);

        if (entry.isLayerChange()) {
            eObj.add("beforeLayerState", GSON.toJsonTree(entry.getBeforeLayerState()));
            eObj.add("afterLayerState", GSON.toJsonTree(entry.getAfterLayerState()));
        }
        return eObj;
    }

    private HistoryEntry deserializeEntry(JsonObject eObj) {
        int id = eObj.get("id").getAsInt();
        var type = HistoryActionType.valueOf(eObj.get("actionType").getAsString());
        long timestamp = eObj.has("timestamp") ? eObj.get("timestamp").getAsLong() : System.currentTimeMillis();
        int px = eObj.get("primaryX").getAsInt();
        int py = eObj.get("primaryY").getAsInt();
        int pz = eObj.get("primaryZ").getAsInt();
        String pBlock = eObj.get("primaryBlock").getAsString();
        int count = eObj.get("affectedCount").getAsInt();

        var snapArr = eObj.has("snapshots") ? eObj.getAsJsonArray("snapshots") : new JsonArray();
        var snapshots = new Object[snapArr.size()][];
        int si = 0;
        for (var sElem : snapArr) {
            var sArr = sElem.getAsJsonArray();
            Object[] snapshot = new Object[sArr.size() >= 7 ? 7 : 5];
            snapshot[0] = sArr.get(0).getAsInt();
            snapshot[1] = sArr.get(1).getAsInt();
            snapshot[2] = sArr.get(2).getAsInt();
            snapshot[3] = sArr.get(3).getAsString();
            snapshot[4] = sArr.get(4).getAsString();
            if (snapshot.length >= 7) {
                snapshot[5] = parseNbtSnapshot(sArr.get(5).getAsString());
                snapshot[6] = parseNbtSnapshot(sArr.get(6).getAsString());
            }
            snapshots[si] = snapshot;
            si++;
        }

        BuildingModel.LayerState beforeLayerState = null;
        BuildingModel.LayerState afterLayerState = null;
        if (eObj.has("beforeLayerState") && eObj.has("afterLayerState")) {
            beforeLayerState = GSON.fromJson(eObj.get("beforeLayerState"), BuildingModel.LayerState.class);
            afterLayerState = GSON.fromJson(eObj.get("afterLayerState"), BuildingModel.LayerState.class);
        }
        return new HistoryEntry(id, type, snapshots, beforeLayerState, afterLayerState,
                px, py, pz, pBlock, count, timestamp);
    }

    private static String nbtToString(Object nbt) {
        return nbt instanceof CompoundTag tag ? tag.toString() : "";
    }

    private static CompoundTag parseNbtSnapshot(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return TagParser.parseTag(raw);
        } catch (Exception ignored) {
            return null;
        }
    }
}
