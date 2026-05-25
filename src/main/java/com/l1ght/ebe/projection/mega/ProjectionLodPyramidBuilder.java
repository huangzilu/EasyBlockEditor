package com.l1ght.ebe.projection.mega;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectionLodPyramidBuilder {
    private static final int[] DEFAULT_CELL_SIZES = {32, 16, 8, 4};

    private ProjectionLodPyramidBuilder() {
    }

    public static ProjectionLodPyramid build(ProjectionSparseStore store) {
        return build(store, DEFAULT_CELL_SIZES);
    }

    public static ProjectionLodPyramid build(ProjectionSparseStore store, int[] cellSizes) {
        if (store == null || store.isEmpty()) {
            return ProjectionLodPyramid.empty();
        }
        var levels = new ArrayList<ProjectionLodPyramid.LodLevel>();
        for (int cellSize : cellSizes) {
            if (cellSize <= 0) {
                continue;
            }
            levels.add(buildLevel(store, cellSize));
        }
        return new ProjectionLodPyramid(List.copyOf(levels));
    }

    private static ProjectionLodPyramid.LodLevel buildLevel(ProjectionSparseStore store, int cellSize) {
        var cells = new LinkedHashMap<CellKey, CellAccumulator>();
        int cellVolume = Math.max(1, cellSize * cellSize * cellSize);

        for (var entry : store.entries()) {
            var pos = entry.pos();
            int cellX = Math.floorDiv(pos.getX(), cellSize);
            int cellY = Math.floorDiv(pos.getY(), cellSize);
            int cellZ = Math.floorDiv(pos.getZ(), cellSize);
            var key = new CellKey(cellX, cellY, cellZ);
            cells.computeIfAbsent(key, ignored -> new CellAccumulator(cellX, cellY, cellZ))
                    .add(entry.stateId());
        }

        var mesherCells = new ArrayList<GreedyLodMesher.Cell>(cells.size());
        for (var accumulator : cells.values()) {
            mesherCells.add(accumulator.toCell(cellVolume, store));
        }
        var boxes = GreedyLodMesher.mesh(mesherCells, cellSize);
        return new ProjectionLodPyramid.LodLevel(cellSize, boxes);
    }

    private record CellKey(int x, int y, int z) {
    }

    private static final class CellAccumulator {
        private final int cellX;
        private final int cellY;
        private final int cellZ;
        private final Map<Integer, Integer> stateCounts = new LinkedHashMap<>();
        private int total;

        private CellAccumulator(int cellX, int cellY, int cellZ) {
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellZ = cellZ;
        }

        private void add(int stateId) {
            stateCounts.merge(stateId, 1, Integer::sum);
            total++;
        }

        private GreedyLodMesher.Cell toCell(int cellVolume, ProjectionSparseStore store) {
            int dominantState = 0;
            int dominantCount = -1;
            for (var entry : stateCounts.entrySet()) {
                if (entry.getValue() > dominantCount) {
                    dominantState = entry.getKey();
                    dominantCount = entry.getValue();
                }
            }
            float density = Math.min(1.0F, total / (float) cellVolume);
            return new GreedyLodMesher.Cell(cellX, cellY, cellZ, dominantState,
                    visualGroup(store.stateById(dominantState)), total, density);
        }
    }

    private static int visualGroup(net.minecraft.world.level.block.state.BlockState state) {
        if (state == null) return 0;
        try {
            MapColor color = state.getMapColor(null, BlockPos.ZERO);
            if (color != null) return color.id;
        } catch (Exception ignored) {
        }
        return Math.max(0, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(state.getBlock()));
    }

}
