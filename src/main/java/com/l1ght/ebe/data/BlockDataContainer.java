package com.l1ght.ebe.data;

public class BlockDataContainer {
    private final int sizeX, sizeY, sizeZ;
    private BitArray data;
    private BlockStatePalette palette;

    public BlockDataContainer(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = new BlockStatePalette(2);
        this.palette.idFor("minecraft:air");
        this.data = new BitArray(2, sizeX * sizeY * sizeZ);
    }

    private int index(int x, int y, int z) {
        return y * (sizeX * sizeZ) + z * sizeX + x;
    }

    public void set(int x, int y, int z, Object state) {
        int idx = index(x, y, z);
        int id = palette.idFor(state);
        if (id == -1) {
            resize();
            id = palette.idFor(state);
        }
        data.set(idx, id);
    }

    public Object get(int x, int y, int z) {
        int id = data.get(index(x, y, z));
        return palette.get(id);
    }

    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public int getTotalSize() { return sizeX * sizeY * sizeZ; }

    public BlockStatePalette getPalette() { return palette; }

    private void resize() {
        int newBits = palette.getBits() + 1;
        var newPalette = new BlockStatePalette(newBits);
        for (Object state : palette.allStates()) {
            newPalette.idFor(state);
        }
        var newData = new BitArray(newBits, getTotalSize());
        for (int i = 0; i < getTotalSize(); i++) {
            newData.set(i, data.get(i));
        }
        this.palette = newPalette;
        this.data = newData;
    }
}
