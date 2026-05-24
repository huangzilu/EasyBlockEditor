package com.l1ght.ebe.editor;

import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.editor.history.HistoryActionType;
import com.l1ght.ebe.editor.history.HistoryEntry;
import com.l1ght.ebe.editor.history.HistoryManager;
import com.l1ght.ebe.editor.selection.SelectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClipboardManager {

    private final Map<BlockPos, Object> clipboard = new LinkedHashMap<>();
    private final Map<BlockPos, CompoundTag> clipboardNbt = new LinkedHashMap<>();
    private int clipboardOriginX;
    private int clipboardOriginY;
    private int clipboardOriginZ;
    private int clipboardSizeX;
    private int clipboardSizeY;
    private int clipboardSizeZ;

    public int getClipboardOriginX() { return clipboardOriginX; }
    public int getClipboardOriginY() { return clipboardOriginY; }
    public int getClipboardOriginZ() { return clipboardOriginZ; }
    public int getClipboardSizeX() { return clipboardSizeX; }
    public int getClipboardSizeY() { return clipboardSizeY; }
    public int getClipboardSizeZ() { return clipboardSizeZ; }

    public void copy(BuildingModel model, SelectionManager selection) {
        clipboard.clear();
        clipboardNbt.clear();
        if (selection.isEmpty()) return;

        var positions = selection.getPositions();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<long[]> posList = new ArrayList<>();

        for (var p : positions) {
            int x = (int) p[0], y = (int) p[1], z = (int) p[2];
            var state = model.getBlockAt(x, y, z);
            if (BuildingModel.isAirLike(state)) continue;

            posList.add(new long[]{x, y, z});
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        if (posList.isEmpty()) return;

        clipboardOriginX = minX;
        clipboardOriginY = minY;
        clipboardOriginZ = minZ;
        clipboardSizeX = maxX - minX + 1;
        clipboardSizeY = maxY - minY + 1;
        clipboardSizeZ = maxZ - minZ + 1;

        for (var p : posList) {
            int wx = (int) p[0], wy = (int) p[1], wz = (int) p[2];
            int lx = wx - minX, ly = wy - minY, lz = wz - minZ;
            var localPos = new BlockPos(lx, ly, lz);
            clipboard.put(localPos, model.getBlockAt(wx, wy, wz));
            var nbt = model.copyBlockEntityNbt(wx, wy, wz);
            if (nbt != null) clipboardNbt.put(localPos, nbt);
        }
    }

    public boolean hasContent() {
        return !clipboard.isEmpty();
    }

    public void paste(BuildingModel model, BlockPos targetOrigin, HistoryManager history) {
        if (clipboard.isEmpty()) return;

        List<Object[]> snapshots = new ArrayList<>();
        Object repBlock = null;

        for (var entry : clipboard.entrySet()) {
            var localPos = entry.getKey();
            int wx = targetOrigin.getX() + localPos.getX();
            int wy = targetOrigin.getY() + localPos.getY();
            int wz = targetOrigin.getZ() + localPos.getZ();

            var oldState = model.getBlockAt(wx, wy, wz);
            var newState = entry.getValue();
            var oldNbt = model.copyBlockEntityNbt(wx, wy, wz);
            var newNbt = copyNbt(clipboardNbt.get(localPos));

            snapshots.add(snapshot(wx, wy, wz, oldState, newState, oldNbt, newNbt));
            model.setBlockAtWithNbt(wx, wy, wz, newState, newNbt);
            if (repBlock == null) repBlock = newState;
        }

        if (!snapshots.isEmpty()) {
            pushHistory(history, HistoryActionType.PASTE, snapshots, null, model,
                    targetOrigin.getX(), targetOrigin.getY(), targetOrigin.getZ(), repBlock);
        }
    }

    public void cut(BuildingModel model, SelectionManager selection, HistoryManager history) {
        copy(model, selection);
        deleteInternal(model, selection, history, HistoryActionType.CUT);
    }

    public void deleteSelected(BuildingModel model, SelectionManager selection, HistoryManager history) {
        deleteInternal(model, selection, history, HistoryActionType.DELETE);
    }

    private void deleteInternal(BuildingModel model, SelectionManager selection,
                                HistoryManager history, HistoryActionType actionType) {
        if (selection.isEmpty()) return;

        var beforeLayerState = model.captureLayerState();
        List<Object[]> snapshots = new ArrayList<>();
        var positions = selection.getPositions();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;

        for (var p : positions) {
            int x = (int) p[0], y = (int) p[1], z = (int) p[2];
            var oldState = model.getBlockAt(x, y, z);
            if (BuildingModel.isAirLike(oldState)) continue;
            var oldNbt = model.copyBlockEntityNbt(x, y, z);

            snapshots.add(snapshot(x, y, z, oldState, "minecraft:air", oldNbt, null));
            model.setBlockAt(x, y, z, "minecraft:air");
            if (repBlock == null) { repX = x; repY = y; repZ = z; repBlock = oldState; }
        }

        selection.clear();

        if (!snapshots.isEmpty()) {
            pushHistory(history, actionType, snapshots, beforeLayerState, model, repX, repY, repZ, repBlock);
        }
    }

    public void replace(BuildingModel model, SelectionManager selection, Object fromState, Object toState,
                        HistoryManager history) {
        List<Object[]> snapshots = new ArrayList<>();
        boolean useSelection = !selection.isEmpty();
        int repX = 0, repY = 0, repZ = 0;

        var beforeLayerState = model.captureLayerState();
        if (useSelection) {
            for (var p : selection.getPositions()) {
                int x = (int) p[0], y = (int) p[1], z = (int) p[2];
                var current = model.getBlockAt(x, y, z);
                if (statesMatch(current, fromState)) {
                    var oldNbt = model.copyBlockEntityNbt(x, y, z);
                    snapshots.add(snapshot(x, y, z, current, toState, oldNbt, null));
                    model.setBlockAtWithNbt(x, y, z, toState, null);
                    if (snapshots.size() == 1) { repX = x; repY = y; repZ = z; }
                }
            }
        } else {
            for (var region : model.getRegions()) {
                for (int y = 0; y < region.getSizeY(); y++) {
                    for (int z = 0; z < region.getSizeZ(); z++) {
                        for (int x = 0; x < region.getSizeX(); x++) {
                            int wx = x + region.getOffsetX();
                            int wy = y + region.getOffsetY();
                            int wz = z + region.getOffsetZ();
                            var current = region.getBlocks().get(x, y, z);
                            if (statesMatch(current, fromState)) {
                                var oldNbt = model.copyBlockEntityNbt(wx, wy, wz);
                                snapshots.add(snapshot(wx, wy, wz, current, toState, oldNbt, null));
                                model.setBlockAtWithNbt(wx, wy, wz, toState, null);
                                if (snapshots.size() == 1) { repX = wx; repY = wy; repZ = wz; }
                            }
                        }
                    }
                }
            }
        }

        if (!snapshots.isEmpty()) {
            pushHistory(history, HistoryActionType.REPLACE, snapshots, beforeLayerState, model,
                    repX, repY, repZ, toState);
        }
    }

    public void rotate(BuildingModel model, SelectionManager selection, int angle,
                       HistoryManager history) {
        if (selection.isEmpty()) return;
        var beforeLayerState = model.captureLayerState();

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (var p : selection.getPositions()) {
            minX = Math.min(minX, (int) p[0]);
            minZ = Math.min(minZ, (int) p[2]);
        }

        var snapshots = new ArrayList<Object[]>();
        var positions = selection.getPositions();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;
        var oldStates = new LinkedHashMap<Long, Object>();
        var oldLayerIds = new LinkedHashMap<Long, String>();
        var oldNbt = new LinkedHashMap<Long, CompoundTag>();
        var targetMappings = new ArrayList<long[]>();
        var sourceKeys = new java.util.HashSet<Long>();

        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            sourceKeys.add(sourceKey);
            var oldState = model.getBlockAt(ox, oy, oz);
            oldStates.put(sourceKey, oldState);
            oldLayerIds.put(sourceKey, model.getLayerIdAt(ox, oy, oz));
            oldNbt.put(sourceKey, model.copyBlockEntityNbt(ox, oy, oz));

            int rx = ox - minX, rz = oz - minZ;
            int nx = switch (angle) {
                case 0 -> -rz; case 1 -> -rx; default -> rz;
            };
            int nz = switch (angle) {
                case 0 -> rx; case 1 -> -rz; default -> -rx;
            };
            int wx = minX + nx, wy = oy, wz = minZ + nz;
            targetMappings.add(new long[]{ox, oy, oz, wx, wy, wz});
            if (repBlock == null) { repX = ox; repY = oy; repZ = oz; repBlock = oldState; }
        }

        var existingAtTarget = new LinkedHashMap<Long, Object>();
        var existingNbtAtTarget = new LinkedHashMap<Long, CompoundTag>();
        for (var mapping : targetMappings) {
            int wx = (int) mapping[3], wy = (int) mapping[4], wz = (int) mapping[5];
            long targetKey = SelectionManager.packPos(wx, wy, wz);
            if (!sourceKeys.contains(targetKey)) {
                existingAtTarget.put(targetKey, model.getBlockAt(wx, wy, wz));
                existingNbtAtTarget.put(targetKey, model.copyBlockEntityNbt(wx, wy, wz));
            }
        }

        for (var mapping : targetMappings) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            var oldState = oldStates.get(sourceKey);
            snapshots.add(snapshot(ox, oy, oz, oldState, "minecraft:air", oldNbt.get(sourceKey), null));
        }
        for (var mapping : targetMappings) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            int wx = (int) mapping[3], wy = (int) mapping[4], wz = (int) mapping[5];
            long targetKey = SelectionManager.packPos(wx, wy, wz);
            var existing = existingAtTarget.getOrDefault(targetKey, "minecraft:air");
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            var oldState = oldStates.get(sourceKey);
            snapshots.add(snapshot(wx, wy, wz, existing, oldState, existingNbtAtTarget.get(targetKey), oldNbt.get(sourceKey)));
        }

        for (var p : positions) {
            model.setBlockAt((int) p[0], (int) p[1], (int) p[2], "minecraft:air");
        }
        for (var mapping : targetMappings) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            int wx = (int) mapping[3], wy = (int) mapping[4], wz = (int) mapping[5];
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            var oldState = oldStates.get(sourceKey);
            model.setBlockAtWithNbt(wx, wy, wz, oldState, oldNbt.get(sourceKey));
            if (!BuildingModel.isAirLike(oldState)) {
                model.setBlockLayerOverride(wx, wy, wz, oldLayerIds.get(sourceKey));
            }
        }

        selection.clear();
        for (var mapping : targetMappings) {
            selection.add((int) mapping[3], (int) mapping[4], (int) mapping[5]);
        }

        if (!snapshots.isEmpty()) {
            pushHistory(history, HistoryActionType.ROTATE, snapshots, beforeLayerState, model,
                    repX, repY, repZ, repBlock);
        }
    }

    public void mirror(BuildingModel model, SelectionManager selection, int axis,
                       int centerX, int centerY, int centerZ, HistoryManager history) {
        if (selection.isEmpty()) return;
        var beforeLayerState = model.captureLayerState();

        var snapshots = new ArrayList<Object[]>();
        var positions = selection.getPositions();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;
        var oldStates = new LinkedHashMap<Long, Object>();
        var oldLayerIds = new LinkedHashMap<Long, String>();
        var oldNbt = new LinkedHashMap<Long, CompoundTag>();
        var targetMappings = new ArrayList<long[]>();
        var sourceKeys = new java.util.HashSet<Long>();

        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            sourceKeys.add(sourceKey);
            var oldState = model.getBlockAt(ox, oy, oz);
            oldStates.put(sourceKey, oldState);
            oldLayerIds.put(sourceKey, model.getLayerIdAt(ox, oy, oz));
            oldNbt.put(sourceKey, model.copyBlockEntityNbt(ox, oy, oz));

            int nx = switch (axis) { case 0 -> 2 * centerX - ox; default -> ox; };
            int ny = switch (axis) { case 1 -> 2 * centerY - oy; default -> oy; };
            int nz = switch (axis) { case 2 -> 2 * centerZ - oz; default -> oz; };

            targetMappings.add(new long[]{ox, oy, oz, nx, ny, nz});
            if (repBlock == null) { repX = ox; repY = oy; repZ = oz; repBlock = oldState; }
        }

        var existingAtTarget = new LinkedHashMap<Long, Object>();
        var existingNbtAtTarget = new LinkedHashMap<Long, CompoundTag>();
        for (var mapping : targetMappings) {
            int nx = (int) mapping[3], ny = (int) mapping[4], nz = (int) mapping[5];
            long targetKey = SelectionManager.packPos(nx, ny, nz);
            if (!sourceKeys.contains(targetKey)) {
                existingAtTarget.put(targetKey, model.getBlockAt(nx, ny, nz));
                existingNbtAtTarget.put(targetKey, model.copyBlockEntityNbt(nx, ny, nz));
            }
        }

        for (var mapping : targetMappings) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            var oldState = oldStates.get(sourceKey);
            snapshots.add(snapshot(ox, oy, oz, oldState, "minecraft:air", oldNbt.get(sourceKey), null));
        }
        for (var mapping : targetMappings) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            int nx = (int) mapping[3], ny = (int) mapping[4], nz = (int) mapping[5];
            long targetKey = SelectionManager.packPos(nx, ny, nz);
            var existing = existingAtTarget.getOrDefault(targetKey, "minecraft:air");
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            var oldState = oldStates.get(sourceKey);
            snapshots.add(snapshot(nx, ny, nz, existing, oldState, existingNbtAtTarget.get(targetKey), oldNbt.get(sourceKey)));
        }

        for (var p : positions) {
            model.setBlockAt((int) p[0], (int) p[1], (int) p[2], "minecraft:air");
        }
        for (var mapping : targetMappings) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            int nx = (int) mapping[3], ny = (int) mapping[4], nz = (int) mapping[5];
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            var oldState = oldStates.get(sourceKey);
            model.setBlockAtWithNbt(nx, ny, nz, oldState, oldNbt.get(sourceKey));
            if (!BuildingModel.isAirLike(oldState)) {
                model.setBlockLayerOverride(nx, ny, nz, oldLayerIds.get(sourceKey));
            }
        }

        selection.clear();
        for (var mapping : targetMappings) {
            selection.add((int) mapping[3], (int) mapping[4], (int) mapping[5]);
        }

        if (!snapshots.isEmpty()) {
            pushHistory(history, HistoryActionType.MIRROR, snapshots, beforeLayerState, model,
                    repX, repY, repZ, repBlock);
        }
    }

    public void translateSelection(BuildingModel model, SelectionManager selection, int dx, int dy, int dz,
                                   HistoryManager history) {
        if (selection.isEmpty()) return;
        var beforeLayerState = model.captureLayerState();

        var snapshots = new ArrayList<Object[]>();
        var positions = selection.getPositions();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;

        var oldStates = new LinkedHashMap<Long, Object>();
        var oldLayerIds = new LinkedHashMap<Long, String>();
        var oldNbt = new LinkedHashMap<Long, CompoundTag>();
        var sourceKeys = new java.util.HashSet<Long>();
        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            sourceKeys.add(sourceKey);
            oldStates.put(sourceKey, model.getBlockAt(ox, oy, oz));
            oldLayerIds.put(sourceKey, model.getLayerIdAt(ox, oy, oz));
            oldNbt.put(sourceKey, model.copyBlockEntityNbt(ox, oy, oz));
        }

        var existingAtTarget = new LinkedHashMap<Long, Object>();
        var existingNbtAtTarget = new LinkedHashMap<Long, CompoundTag>();
        for (var p : positions) {
            int nx = (int) p[0] + dx, ny = (int) p[1] + dy, nz = (int) p[2] + dz;
            long packed = SelectionManager.packPos(nx, ny, nz);
            if (!sourceKeys.contains(packed)) {
                existingAtTarget.put(packed, model.getBlockAt(nx, ny, nz));
                existingNbtAtTarget.put(packed, model.copyBlockEntityNbt(nx, ny, nz));
            }
        }

        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            var oldState = oldStates.get(sourceKey);
            snapshots.add(snapshot(ox, oy, oz, oldState, "minecraft:air", oldNbt.get(sourceKey), null));
        }
        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            int nx = ox + dx, ny = oy + dy, nz = oz + dz;
            long sourceKey = SelectionManager.packPos(ox, oy, oz);
            long targetKey = SelectionManager.packPos(nx, ny, nz);
            var oldState = oldStates.get(sourceKey);
            var existing = existingAtTarget.getOrDefault(targetKey, "minecraft:air");
            snapshots.add(snapshot(nx, ny, nz, existing, oldState, existingNbtAtTarget.get(targetKey), oldNbt.get(sourceKey)));
        }

        for (var p : positions) {
            model.setBlockAt((int) p[0], (int) p[1], (int) p[2], "minecraft:air");
        }
        for (var p : positions) {
            int nx = (int) p[0] + dx, ny = (int) p[1] + dy, nz = (int) p[2] + dz;
            long sourceKey = SelectionManager.packPos((int) p[0], (int) p[1], (int) p[2]);
            var oldState = oldStates.get(sourceKey);
            model.setBlockAtWithNbt(nx, ny, nz, oldState, oldNbt.get(sourceKey));
            if (!BuildingModel.isAirLike(oldState)) {
                model.setBlockLayerOverride(nx, ny, nz, oldLayerIds.get(sourceKey));
            }
        }

        selection.clear();
        for (var p : positions) {
            selection.add((int) p[0] + dx, (int) p[1] + dy, (int) p[2] + dz);
        }

        if (!snapshots.isEmpty()) {
            if (repBlock == null) { repX = (int) positions.iterator().next()[0] + dx; repY = (int) positions.iterator().next()[1] + dy; repZ = (int) positions.iterator().next()[2] + dz; repBlock = oldStates.values().iterator().next(); }
            pushHistory(history, HistoryActionType.TRANSLATE, snapshots, beforeLayerState, model,
                    repX, repY, repZ, repBlock);
        }
    }

    public void fill(BuildingModel model, SelectionManager selection, Object fillState,
                     HistoryManager history) {
        if (selection.isEmpty()) return;

        var beforeLayerState = model.captureLayerState();
        List<Object[]> snapshots = new ArrayList<>();
        int repX = 0, repY = 0, repZ = 0;
        for (var p : selection.getPositions()) {
            int x = (int) p[0], y = (int) p[1], z = (int) p[2];
            if (!model.canEditAt(x, y, z)) continue;
            var oldState = model.getBlockAt(x, y, z);
            if (statesMatch(oldState, fillState)) continue;
            var oldNbt = model.copyBlockEntityNbt(x, y, z);

            snapshots.add(snapshot(x, y, z, oldState, fillState, oldNbt, null));
            model.setBlockAtWithNbt(x, y, z, fillState, null);
            if (snapshots.size() == 1) { repX = x; repY = y; repZ = z; }
        }

        if (!snapshots.isEmpty()) {
            pushHistory(history, HistoryActionType.FILL, snapshots, beforeLayerState, model,
                    repX, repY, repZ, fillState);
        }
    }

    public void fillRandom(BuildingModel model, SelectionManager selection,
                           Map<Object, Double> ratios, HistoryManager history) {
        if (selection.isEmpty() || ratios.isEmpty()) return;

        double totalWeight = ratios.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0) return;

        var beforeLayerState = model.captureLayerState();
        List<Object[]> snapshots = new ArrayList<>();
        var random = new java.util.Random();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;

        for (var p : selection.getPositions()) {
            int x = (int) p[0], y = (int) p[1], z = (int) p[2];
            double r = random.nextDouble() * totalWeight;
            Object chosen = null;
            double cumulative = 0;
            for (var entry : ratios.entrySet()) {
                cumulative += entry.getValue();
                if (r <= cumulative) {
                    chosen = entry.getKey();
                    break;
                }
            }
            if (chosen == null) chosen = ratios.keySet().iterator().next();

            var oldState = model.getBlockAt(x, y, z);
            if (statesMatch(oldState, chosen)) continue;
            var oldNbt = model.copyBlockEntityNbt(x, y, z);

            snapshots.add(snapshot(x, y, z, oldState, chosen, oldNbt, null));
            model.setBlockAtWithNbt(x, y, z, chosen, null);
            if (repBlock == null) { repX = x; repY = y; repZ = z; repBlock = chosen; }
        }

        if (!snapshots.isEmpty()) {
            pushHistory(history, HistoryActionType.FILL, snapshots, beforeLayerState, model,
                    repX, repY, repZ, repBlock);
        }
    }

    private boolean statesMatch(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return stateKey(a).equals(stateKey(b));
    }

    private void pushHistory(HistoryManager history, HistoryActionType actionType, List<Object[]> snapshots,
                             BuildingModel.LayerState beforeLayerState, BuildingModel model,
                             int primaryX, int primaryY, int primaryZ, Object primaryBlock) {
        BuildingModel.LayerState afterLayerState = null;
        if (beforeLayerState != null) {
            afterLayerState = model.captureLayerState();
            if (beforeLayerState.equals(afterLayerState)) {
                beforeLayerState = null;
                afterLayerState = null;
            }
        }
        history.push(new HistoryEntry(
                history.nextId(), actionType,
                snapshots.toArray(Object[][]::new),
                beforeLayerState, afterLayerState,
                primaryX, primaryY, primaryZ,
                primaryBlock, snapshots.size(), System.currentTimeMillis()));
    }

    private String stateKey(Object state) {
        if (state == null) return "";
        if (isMinecraftBlockState(state)) {
            try {
                var block = state.getClass().getMethod("getBlock").invoke(state);
                return String.valueOf(block.getClass().getMethod("getDescriptionId").invoke(block));
            } catch (ReflectiveOperationException ignored) {
                return state.toString();
            }
        }
        return state.toString();
    }

    private boolean isMinecraftBlockState(Object state) {
        return state != null && "net.minecraft.world.level.block.state.BlockState".equals(state.getClass().getName());
    }

    private static Object[] snapshot(int x, int y, int z, Object oldState, Object newState,
                                     CompoundTag oldNbt, CompoundTag newNbt) {
        return new Object[]{x, y, z, oldState, newState, copyNbt(oldNbt), copyNbt(newNbt)};
    }

    private static CompoundTag copyNbt(CompoundTag tag) {
        return tag == null ? null : tag.copy();
    }
}
