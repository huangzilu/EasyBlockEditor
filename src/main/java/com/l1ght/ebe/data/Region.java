package com.l1ght.ebe.data;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Region {
    private final String name;
    private final int offsetX, offsetY, offsetZ;
    private final int sizeX, sizeY, sizeZ;
    private final BlockDataContainer blocks;
    private final Map<Long, CompoundTag> blockEntities = new HashMap<>();
    private String layerId;

    public Region(String name, int offsetX, int offsetY, int offsetZ, int sizeX, int sizeY, int sizeZ) {
        this.name = name;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new BlockDataContainer(sizeX, sizeY, sizeZ);
    }

    public String getName() { return name; }
    public int getOffsetX() { return offsetX; }
    public int getOffsetY() { return offsetY; }
    public int getOffsetZ() { return offsetZ; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public BlockDataContainer getBlocks() { return blocks; }
    public String getLayerId() { return layerId; }
    public void setLayerId(String layerId) { this.layerId = layerId; }

    public Map<Long, CompoundTag> getBlockEntities() { return blockEntities; }

    private long localIndex(int x, int y, int z) {
        return ((long) x) | (((long) y) << 12) | (((long) z) << 24);
    }

    public void setBlockEntity(int lx, int ly, int lz, CompoundTag tag) {
        if (tag == null) {
            blockEntities.remove(localIndex(lx, ly, lz));
        } else {
            blockEntities.put(localIndex(lx, ly, lz), tag);
        }
    }

    public CompoundTag getBlockEntity(int lx, int ly, int lz) {
        return blockEntities.get(localIndex(lx, ly, lz));
    }

    public CompoundTag getWorldBlockEntity(int wx, int wy, int wz) {
        return blockEntities.get(localIndex(wx - offsetX, wy - offsetY, wz - offsetZ));
    }

    public void setWorldBlockEntity(int wx, int wy, int wz, CompoundTag tag) {
        setBlockEntity(wx - offsetX, wy - offsetY, wz - offsetZ, tag);
    }

    public boolean containsWorldPos(int wx, int wy, int wz) {
        int lx = wx - offsetX;
        int ly = wy - offsetY;
        int lz = wz - offsetZ;
        return lx >= 0 && lx < sizeX && ly >= 0 && ly < sizeY && lz >= 0 && lz < sizeZ;
    }

    public void setWorldBlock(int wx, int wy, int wz, Object state) {
        blocks.set(wx - offsetX, wy - offsetY, wz - offsetZ, state);
    }

    public Object getWorldBlock(int wx, int wy, int wz) {
        return blocks.get(wx - offsetX, wy - offsetY, wz - offsetZ);
    }
}
