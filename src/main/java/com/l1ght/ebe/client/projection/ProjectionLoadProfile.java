package com.l1ght.ebe.client.projection;

import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import com.l1ght.ebe.projection.compute.ComputedProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public record ProjectionLoadProfile(
        long fileSizeBytes,
        long totalVolume,
        int nonAirBlocks,
        int blockEntityCount,
        int entityCount,
        int regionCount,
        int chunkColumnCount,
        Risk risk
) {
    public enum Risk {
        NORMAL,
        LARGE,
        HUGE,
        EXTREME
    }

    private static final long LARGE_FILE_BYTES = 512L * 1024L;
    private static final long HUGE_FILE_BYTES = 3L * 1024L * 1024L;
    private static final long EXTREME_FILE_BYTES = 10L * 1024L * 1024L;

    public static ProjectionLoadProfile fromModel(Path file, BuildingModel model) {
        if (model == null) {
            long fileSize = fileSize(file);
            return new ProjectionLoadProfile(fileSize, 0L, 0, 0, 0, 0, 0, classify(fileSize, 0L, 0));
        }

        long totalVolume = 0L;
        int nonAirBlocks = 0;
        int blockEntityCount = 0;
        Set<Long> chunks = new HashSet<>();

        for (Region region : model.getRegions()) {
            totalVolume += (long) region.getSizeX() * region.getSizeY() * region.getSizeZ();
            blockEntityCount += region.getBlockEntities().size();
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        Object state = region.getBlocks().get(x, y, z);
                        if (BuildingModel.isAirLike(state)) continue;
                        int wx = region.getOffsetX() + x;
                        int wz = region.getOffsetZ() + z;
                        chunks.add(ChunkPos.asLong(wx >> 4, wz >> 4));
                        nonAirBlocks++;
                    }
                }
            }
        }

        long fileSize = fileSize(file);
        return new ProjectionLoadProfile(
                fileSize,
                totalVolume,
                nonAirBlocks,
                blockEntityCount,
                model.getEntities().size(),
                model.getRegions().size(),
                chunks.size(),
                classify(fileSize, totalVolume, nonAirBlocks)
        );
    }

    public static ProjectionLoadProfile fromComputed(Path file, BuildingModel model, ComputedProjection computed) {
        if (computed == null) return fromModel(file, model);

        int blockEntityCount = 0;
        int regionCount = 0;
        int entityCount = 0;
        if (model != null) {
            regionCount = model.getRegions().size();
            entityCount = model.getEntities().size();
            for (Region region : model.getRegions()) {
                blockEntityCount += region.getBlockEntities().size();
            }
        }

        Set<Long> chunks = new HashSet<>();
        for (var entry : computed.getBlocks()) {
            BlockPos pos = entry.getPos();
            chunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }

        long fileSize = fileSize(file);
        long totalVolume = computed.getTotalVolume();
        int nonAirBlocks = computed.blockCount();
        return new ProjectionLoadProfile(
                fileSize,
                totalVolume,
                nonAirBlocks,
                blockEntityCount,
                entityCount,
                regionCount,
                chunks.size(),
                classify(fileSize, totalVolume, nonAirBlocks)
        );
    }

    public boolean isLargeOrAbove() {
        return risk.ordinal() >= Risk.LARGE.ordinal();
    }

    public boolean isHugeOrAbove() {
        return risk.ordinal() >= Risk.HUGE.ordinal();
    }

    public boolean shouldPreferProgressiveViewport() {
        return risk.ordinal() >= Risk.HUGE.ordinal()
                || fileSizeBytes >= HUGE_FILE_BYTES
                || nonAirBlocks >= 150_000
                || totalVolume >= 5_000_000L;
    }

    public double steadyLoadBudgetFloorMs() {
        return switch (risk) {
            case EXTREME -> 5.0D;
            case HUGE -> 4.0D;
            case LARGE -> 2.5D;
            case NORMAL -> 0.0D;
        };
    }

    public int steadyBlockLimitFloor() {
        return switch (risk) {
            case EXTREME -> 8192;
            case HUGE -> 6144;
            case LARGE -> 3072;
            case NORMAL -> 0;
        };
    }

    public int movingBlockLimitCeiling(int configuredLimit) {
        if (risk == Risk.EXTREME) return Math.max(128, configuredLimit / 8);
        if (risk == Risk.HUGE) return Math.max(128, configuredLimit / 6);
        if (risk == Risk.LARGE) return Math.max(128, configuredLimit / 4);
        return Math.max(128, configuredLimit / 4);
    }

    public String riskKey() {
        return switch (risk) {
            case EXTREME -> "ebe.editor.large_projection.risk.extreme";
            case HUGE -> "ebe.editor.large_projection.risk.huge";
            case LARGE -> "ebe.editor.large_projection.risk.large";
            case NORMAL -> "ebe.editor.large_projection.risk.normal";
        };
    }

    private static long fileSize(Path file) {
        try {
            return file == null ? -1L : Files.size(file);
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static Risk classify(long fileSizeBytes, long totalVolume, int nonAirBlocks) {
        if (fileSizeBytes >= EXTREME_FILE_BYTES || totalVolume >= 20_000_000L || nonAirBlocks >= 500_000) {
            return Risk.EXTREME;
        }
        if (fileSizeBytes >= HUGE_FILE_BYTES || totalVolume >= 5_000_000L || nonAirBlocks >= 150_000) {
            return Risk.HUGE;
        }
        if (fileSizeBytes >= LARGE_FILE_BYTES || totalVolume >= 250_000L || nonAirBlocks >= 50_000) {
            return Risk.LARGE;
        }
        return Risk.NORMAL;
    }
}
