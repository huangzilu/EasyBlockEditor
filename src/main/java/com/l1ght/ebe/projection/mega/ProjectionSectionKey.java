package com.l1ght.ebe.projection.mega;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public record ProjectionSectionKey(int x, int y, int z) {
    public static final int SIZE = 16;

    public static ProjectionSectionKey fromBlockPos(BlockPos pos) {
        return new ProjectionSectionKey(
                Math.floorDiv(pos.getX(), SIZE),
                Math.floorDiv(pos.getY(), SIZE),
                Math.floorDiv(pos.getZ(), SIZE)
        );
    }

    public static ProjectionSectionKey fromLong(long packed) {
        return new ProjectionSectionKey(
                SectionPos.x(packed),
                SectionPos.y(packed),
                SectionPos.z(packed)
        );
    }

    public long asLong() {
        return SectionPos.asLong(x, y, z);
    }

    public int minBlockX() {
        return x << 4;
    }

    public int minBlockY() {
        return y << 4;
    }

    public int minBlockZ() {
        return z << 4;
    }

    public BlockPos centerBlockPos() {
        return new BlockPos(minBlockX() + 8, minBlockY() + 8, minBlockZ() + 8);
    }

    public double distanceToSqr(double px, double py, double pz) {
        double dx = minBlockX() + 8.0D - px;
        double dy = minBlockY() + 8.0D - py;
        double dz = minBlockZ() + 8.0D - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
