package com.l1ght.ebe.editor.selection;

import net.minecraft.core.BlockPos;

public class BoxSelection {

    private boolean active = false;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;

    public boolean isActive() {
        return active;
    }

    public void clear() {
        active = false;
    }

    public void set(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.active = true;
    }

    public void set(BlockPos a, BlockPos b) {
        set(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    public void ensureActiveAt(int x, int y, int z) {
        if (!active) {
            set(x, y, z, x, y, z);
        }
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public void move(int dx, int dy, int dz) {
        if (!active) return;
        minX += dx; maxX += dx;
        minY += dy; maxY += dy;
        minZ += dz; maxZ += dz;
    }

    public void stretchFace(int dirX, int dirY, int dirZ, int amount) {
        if (!active) return;
        if (dirX > 0) maxX = Math.max(minX, maxX + amount);
        else if (dirX < 0) minX = Math.min(maxX, minX - amount);
        if (dirY > 0) maxY = Math.max(minY, maxY + amount);
        else if (dirY < 0) minY = Math.min(maxY, minY - amount);
        if (dirZ > 0) maxZ = Math.max(minZ, maxZ + amount);
        else if (dirZ < 0) minZ = Math.min(maxZ, minZ - amount);
    }
}
