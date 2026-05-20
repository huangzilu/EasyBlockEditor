package com.l1ght.ebe.data;

public class Region {
    private final String name;
    private final int offsetX, offsetY, offsetZ;
    private final int sizeX, sizeY, sizeZ;
    private final BlockDataContainer blocks;

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
