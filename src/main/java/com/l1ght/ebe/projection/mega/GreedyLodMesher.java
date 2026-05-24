package com.l1ght.ebe.projection.mega;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class GreedyLodMesher {
    private GreedyLodMesher() {
    }

    public static List<ProjectionLodPyramid.LodBox> mesh(Collection<Cell> cells, int cellSize) {
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        var sorted = new ArrayList<>(cells);
        sorted.sort(Comparator
                .comparingInt(Cell::cellY)
                .thenComparingInt(Cell::cellZ)
                .thenComparingInt(Cell::stateId)
                .thenComparingInt(Cell::cellX));

        var boxes = new ArrayList<ProjectionLodPyramid.LodBox>();
        Cell runStart = null;
        Cell previous = null;
        int runBlockCount = 0;
        float runDensity = 0.0F;
        int runCells = 0;

        for (var cell : sorted) {
            if (runStart == null) {
                runStart = cell;
                previous = cell;
                runBlockCount = cell.blockCount();
                runDensity = cell.density();
                runCells = 1;
                continue;
            }

            boolean contiguous = cell.cellY() == runStart.cellY()
                    && cell.cellZ() == runStart.cellZ()
                    && cell.stateId() == runStart.stateId()
                    && previous != null
                    && cell.cellX() == previous.cellX() + 1;
            if (contiguous) {
                previous = cell;
                runBlockCount += cell.blockCount();
                runDensity += cell.density();
                runCells++;
                continue;
            }

            boxes.add(toBox(runStart, previous, cellSize, runBlockCount, runDensity / Math.max(1, runCells)));
            runStart = cell;
            previous = cell;
            runBlockCount = cell.blockCount();
            runDensity = cell.density();
            runCells = 1;
        }

        if (runStart != null) {
            boxes.add(toBox(runStart, previous, cellSize, runBlockCount, runDensity / Math.max(1, runCells)));
        }
        return List.copyOf(boxes);
    }

    private static ProjectionLodPyramid.LodBox toBox(
            Cell start,
            Cell end,
            int cellSize,
            int blockCount,
            float density
    ) {
        return new ProjectionLodPyramid.LodBox(
                start.cellX() * cellSize,
                start.cellY() * cellSize,
                start.cellZ() * cellSize,
                (end.cellX() + 1) * cellSize,
                (start.cellY() + 1) * cellSize,
                (start.cellZ() + 1) * cellSize,
                start.stateId(),
                blockCount,
                density
        );
    }

    public record Cell(int cellX, int cellY, int cellZ, int stateId, int blockCount, float density) {
    }
}
