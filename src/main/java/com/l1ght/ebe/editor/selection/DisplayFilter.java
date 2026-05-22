package com.l1ght.ebe.editor.selection;

import com.l1ght.ebe.data.BuildingModel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class DisplayFilter {
    private FilterMode mode = FilterMode.ALL;
    private final Set<Block> visibleBlockTypes = new HashSet<>();
    private int minY = Integer.MIN_VALUE;
    private int maxY = Integer.MAX_VALUE;
    private int minX = Integer.MIN_VALUE;
    private int maxX = Integer.MAX_VALUE;
    private int minZ = Integer.MIN_VALUE;
    private int maxZ = Integer.MAX_VALUE;
    private boolean nbtSensitive = false;
    private boolean onlySelected = false;
    private final Set<Long> selectedPositions = new HashSet<>();

    public enum FilterMode {
        ALL,
        BY_BLOCK_TYPE,
        BY_Y_LAYER,
        BY_ROW_COLUMN,
        BY_SELECTION
    }

    public FilterMode getMode() { return mode; }
    public void setMode(FilterMode mode) { this.mode = mode; }

    public Set<Block> getVisibleBlockTypes() { return visibleBlockTypes; }
    public void addVisibleBlockType(Block block) { visibleBlockTypes.add(block); mode = FilterMode.BY_BLOCK_TYPE; }
    public void removeVisibleBlockType(Block block) { visibleBlockTypes.remove(block); }
    public void clearVisibleBlockTypes() { visibleBlockTypes.clear(); }

    public void setYRange(int minY, int maxY) { this.minY = minY; this.maxY = maxY; this.mode = FilterMode.BY_Y_LAYER; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }

    public void setXZRange(int minX, int maxX, int minZ, int maxZ) { this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ; this.mode = FilterMode.BY_ROW_COLUMN; }

    public boolean isNbtSensitive() { return nbtSensitive; }
    public void setNbtSensitive(boolean nbtSensitive) { this.nbtSensitive = nbtSensitive; }

    public boolean isOnlySelected() { return onlySelected; }
    public void setOnlySelected(boolean onlySelected) { this.onlySelected = onlySelected; if (onlySelected) mode = FilterMode.BY_SELECTION; }

    public void setSelectedPositions(Set<Long> positions) { selectedPositions.clear(); selectedPositions.addAll(positions); }

    public void reset() {
        mode = FilterMode.ALL;
        visibleBlockTypes.clear();
        minY = Integer.MIN_VALUE;
        maxY = Integer.MAX_VALUE;
        minX = Integer.MIN_VALUE;
        maxX = Integer.MAX_VALUE;
        minZ = Integer.MIN_VALUE;
        maxZ = Integer.MAX_VALUE;
        nbtSensitive = false;
        onlySelected = false;
        selectedPositions.clear();
    }

    public boolean shouldDisplay(int wx, int wy, int wz, Object blockData) {
        if (mode == FilterMode.ALL) return true;

        if (mode == FilterMode.BY_Y_LAYER) {
            if (wy < minY || wy > maxY) return false;
        }

        if (mode == FilterMode.BY_ROW_COLUMN) {
            if (wy < minY || wy > maxY) return false;
            if (wx < minX || wx > maxX) return false;
            if (wz < minZ || wz > maxZ) return false;
        }

        if (mode == FilterMode.BY_BLOCK_TYPE) {
            if (blockData instanceof BlockState bs) {
                if (!visibleBlockTypes.contains(bs.getBlock())) return false;
            } else if (blockData instanceof String s) {
                boolean found = false;
                for (var block : visibleBlockTypes) {
                    var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
                    if (id.equals(s)) { found = true; break; }
                }
                if (!found) return false;
            }
        }

        if (mode == FilterMode.BY_SELECTION) {
            long packed = SelectionManager.packPos(wx, wy, wz);
            if (!selectedPositions.contains(packed)) return false;
        }

        return true;
    }
}
