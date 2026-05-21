package com.l1ght.ebe.editor;

import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.editor.history.HistoryActionType;
import com.l1ght.ebe.editor.history.HistoryEntry;
import com.l1ght.ebe.editor.history.HistoryManager;
import com.l1ght.ebe.editor.selection.SelectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClipboardManager {

    private final Map<BlockPos, Object> clipboard = new LinkedHashMap<>();
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
        if (selection.isEmpty()) return;

        var positions = selection.getPositions();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<long[]> posList = new ArrayList<>();

        for (var p : positions) {
            int x = (int) p[0], y = (int) p[1], z = (int) p[2];
            var state = model.getBlockAt(x, y, z);
            if (state instanceof BlockState bs && bs.isAir()) continue;
            if (state instanceof String s && (s.isEmpty() || s.equals("minecraft:air"))) continue;

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
            clipboard.put(new BlockPos(lx, ly, lz), model.getBlockAt(wx, wy, wz));
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

            snapshots.add(new Object[]{wx, wy, wz, oldState, newState});
            model.setBlockAt(wx, wy, wz, newState);
            if (repBlock == null) repBlock = newState;
        }

        if (!snapshots.isEmpty()) {
            history.push(new HistoryEntry(
                    history.nextId(),
                    HistoryActionType.PASTE,
                    snapshots.toArray(Object[][]::new),
                    targetOrigin.getX(), targetOrigin.getY(), targetOrigin.getZ(),
                    repBlock, snapshots.size()));
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

        List<Object[]> snapshots = new ArrayList<>();
        var positions = selection.getPositions();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;

        for (var p : positions) {
            int x = (int) p[0], y = (int) p[1], z = (int) p[2];
            var oldState = model.getBlockAt(x, y, z);
            if (oldState instanceof BlockState bs && bs.isAir()) continue;
            if (oldState instanceof String s && s.equals("minecraft:air")) continue;

            snapshots.add(new Object[]{x, y, z, oldState, "minecraft:air"});
            model.setBlockAt(x, y, z, "minecraft:air");
            if (repBlock == null) { repX = x; repY = y; repZ = z; repBlock = oldState; }
        }

        selection.clear();

        if (!snapshots.isEmpty()) {
            history.push(new HistoryEntry(
                    history.nextId(), actionType,
                    snapshots.toArray(Object[][]::new),
                    repX, repY, repZ, repBlock, snapshots.size()));
        }
    }

    public void replace(BuildingModel model, SelectionManager selection, Object fromState, Object toState,
                        HistoryManager history) {
        List<Object[]> snapshots = new ArrayList<>();
        boolean useSelection = !selection.isEmpty();
        int repX = 0, repY = 0, repZ = 0;

        if (useSelection) {
            for (var p : selection.getPositions()) {
                int x = (int) p[0], y = (int) p[1], z = (int) p[2];
                var current = model.getBlockAt(x, y, z);
                if (statesMatch(current, fromState)) {
                    snapshots.add(new Object[]{x, y, z, current, toState});
                    model.setBlockAt(x, y, z, toState);
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
                                snapshots.add(new Object[]{wx, wy, wz, current, toState});
                                region.setWorldBlock(wx, wy, wz, toState);
                                if (snapshots.size() == 1) { repX = wx; repY = wy; repZ = wz; }
                            }
                        }
                    }
                }
            }
        }

        if (!snapshots.isEmpty()) {
            history.push(new HistoryEntry(
                    history.nextId(), HistoryActionType.REPLACE,
                    snapshots.toArray(Object[][]::new),
                    repX, repY, repZ, toState, snapshots.size()));
        }
    }

    public void rotate(BuildingModel model, SelectionManager selection, int angle,
                       HistoryManager history) {
        if (selection.isEmpty()) return;

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (var p : selection.getPositions()) {
            minX = Math.min(minX, (int) p[0]);
            minZ = Math.min(minZ, (int) p[2]);
        }

        var snapshots = new ArrayList<Object[]>();
        var positions = selection.getPositions();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;

        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            int rx = ox - minX, rz = oz - minZ;
            int nx = switch (angle) {
                case 0 -> -rz; case 1 -> -rx; default -> rz;
            };
            int nz = switch (angle) {
                case 0 -> rx; case 1 -> -rz; default -> -rx;
            };
            int wx = minX + nx, wy = oy, wz = minZ + nz;

            var oldState = model.getBlockAt(ox, oy, oz);
            var oldAtNew = model.getBlockAt(wx, wy, wz);

            snapshots.add(new Object[]{ox, oy, oz, oldState, "minecraft:air"});
            snapshots.add(new Object[]{wx, wy, wz, oldAtNew, oldState});

            model.setBlockAt(ox, oy, oz, "minecraft:air");
            model.setBlockAt(wx, wy, wz, oldState);
            if (repBlock == null) { repX = ox; repY = oy; repZ = oz; repBlock = oldState; }
        }

        selection.clear();
        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            int rx = ox - minX, rz = oz - minZ;
            int nx = switch (angle) {
                case 0 -> -rz; case 1 -> -rx; default -> rz;
            };
            int nz = switch (angle) {
                case 0 -> rx; case 1 -> -rz; default -> -rx;
            };
            selection.add(minX + nx, oy, minZ + nz);
        }

        if (!snapshots.isEmpty()) {
            history.push(new HistoryEntry(
                    history.nextId(), HistoryActionType.ROTATE,
                    snapshots.toArray(Object[][]::new),
                    repX, repY, repZ, repBlock, snapshots.size()));
        }
    }

    public void mirror(BuildingModel model, SelectionManager selection, int axis,
                       HistoryManager history) {
        if (selection.isEmpty()) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (var p : selection.getPositions()) {
            minX = Math.min(minX, (int) p[0]); maxX = Math.max(maxX, (int) p[0]);
            minY = Math.min(minY, (int) p[1]); maxY = Math.max(maxY, (int) p[1]);
            minZ = Math.min(minZ, (int) p[2]); maxZ = Math.max(maxZ, (int) p[2]);
        }

        var snapshots = new ArrayList<Object[]>();
        var positions = selection.getPositions();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;

        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            int nx = switch (axis) { case 0 -> maxX - (ox - minX); default -> ox; };
            int ny = switch (axis) { case 1 -> maxY - (oy - minY); default -> oy; };
            int nz = switch (axis) { case 2 -> maxZ - (oz - minZ); default -> oz; };

            if (nx == ox && ny == oy && nz == oz) continue;

            var oldState = model.getBlockAt(ox, oy, oz);
            var oldAtNew = model.getBlockAt(nx, ny, nz);

            snapshots.add(new Object[]{ox, oy, oz, oldState, "minecraft:air"});
            snapshots.add(new Object[]{nx, ny, nz, oldAtNew, oldState});

            model.setBlockAt(ox, oy, oz, "minecraft:air");
            model.setBlockAt(nx, ny, nz, oldState);
            if (repBlock == null) { repX = ox; repY = oy; repZ = oz; repBlock = oldState; }
        }

        selection.clear();
        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            int nx = switch (axis) { case 0 -> maxX - (ox - minX); default -> ox; };
            int ny = switch (axis) { case 1 -> maxY - (oy - minY); default -> oy; };
            int nz = switch (axis) { case 2 -> maxZ - (oz - minZ); default -> oz; };
            selection.add(nx, ny, nz);
        }

        if (!snapshots.isEmpty()) {
            history.push(new HistoryEntry(
                    history.nextId(), HistoryActionType.MIRROR,
                    snapshots.toArray(Object[][]::new),
                    repX, repY, repZ, repBlock, snapshots.size()));
        }
    }

    public void translateSelection(BuildingModel model, SelectionManager selection, int dx, int dy, int dz,
                                   HistoryManager history) {
        if (selection.isEmpty()) return;

        var snapshots = new ArrayList<Object[]>();
        var positions = selection.getPositions();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;

        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            int nx = ox + dx, ny = oy + dy, nz = oz + dz;

            if (!model.canEditAt(nx, ny, nz)) continue;

            var oldState = model.getBlockAt(ox, oy, oz);
            var oldAtNew = model.getBlockAt(nx, ny, nz);

            snapshots.add(new Object[]{ox, oy, oz, oldState, "minecraft:air"});
            snapshots.add(new Object[]{nx, ny, nz, oldAtNew, oldState});

            model.setBlockAt(ox, oy, oz, "minecraft:air");
            model.setBlockAt(nx, ny, nz, oldState);
            if (repBlock == null) { repX = nx; repY = ny; repZ = nz; repBlock = oldState; }
        }

        selection.clear();
        for (var p : positions) {
            selection.add((int) p[0] + dx, (int) p[1] + dy, (int) p[2] + dz);
        }

        if (!snapshots.isEmpty()) {
            history.push(new HistoryEntry(
                    history.nextId(), HistoryActionType.TRANSLATE,
                    snapshots.toArray(Object[][]::new),
                    repX, repY, repZ, repBlock, snapshots.size()));
        }
    }

    public void fill(BuildingModel model, SelectionManager selection, Object fillState,
                     HistoryManager history) {
        if (selection.isEmpty()) return;

        List<Object[]> snapshots = new ArrayList<>();
        int repX = 0, repY = 0, repZ = 0;
        for (var p : selection.getPositions()) {
            int x = (int) p[0], y = (int) p[1], z = (int) p[2];
            if (!model.canEditAt(x, y, z)) continue;
            var oldState = model.getBlockAt(x, y, z);
            if (statesMatch(oldState, fillState)) continue;

            snapshots.add(new Object[]{x, y, z, oldState, fillState});
            model.setBlockAt(x, y, z, fillState);
            if (snapshots.size() == 1) { repX = x; repY = y; repZ = z; }
        }

        if (!snapshots.isEmpty()) {
            history.push(new HistoryEntry(
                    history.nextId(), HistoryActionType.FILL,
                    snapshots.toArray(Object[][]::new),
                    repX, repY, repZ, fillState, snapshots.size()));
        }
    }

    public void fillRandom(BuildingModel model, SelectionManager selection,
                           Map<Object, Double> ratios, HistoryManager history) {
        if (selection.isEmpty() || ratios.isEmpty()) return;

        double totalWeight = ratios.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalWeight <= 0) return;

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

            snapshots.add(new Object[]{x, y, z, oldState, chosen});
            model.setBlockAt(x, y, z, chosen);
            if (repBlock == null) { repX = x; repY = y; repZ = z; repBlock = chosen; }
        }

        if (!snapshots.isEmpty()) {
            history.push(new HistoryEntry(
                    history.nextId(), HistoryActionType.FILL,
                    snapshots.toArray(Object[][]::new),
                    repX, repY, repZ, repBlock, snapshots.size()));
        }
    }

    private boolean statesMatch(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        String sa = a instanceof BlockState bs ? bs.getBlock().getDescriptionId() : a.toString();
        String sb = b instanceof BlockState bs ? bs.getBlock().getDescriptionId() : b.toString();
        return sa.equals(sb);
    }
}
