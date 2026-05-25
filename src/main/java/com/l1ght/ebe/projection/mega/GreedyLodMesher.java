package com.l1ght.ebe.projection.mega;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GreedyLodMesher {
    private GreedyLodMesher() {
    }

    public static List<ProjectionLodPyramid.LodBox> mesh(Collection<Cell> cells, int cellSize) {
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        var boxes = new ArrayList<ProjectionLodPyramid.LodBox>();
        var layers = new LinkedHashMap<LayerKey, Map<Long, Cell>>();
        for (var cell : cells) {
            layers.computeIfAbsent(new LayerKey(cell.cellY(), cell.groupId()), ignored -> new LinkedHashMap<>())
                    .put(pack2d(cell.cellX(), cell.cellZ()), cell);
        }

        for (var layer : layers.values()) {
            boxes.addAll(meshLayer(layer, cellSize));
        }
        boxes.sort(Comparator
                .comparingInt(ProjectionLodPyramid.LodBox::minY)
                .thenComparingInt(ProjectionLodPyramid.LodBox::minZ)
                .thenComparingInt(ProjectionLodPyramid.LodBox::minX));
        return List.copyOf(boxes);
    }

    private static List<ProjectionLodPyramid.LodBox> meshLayer(Map<Long, Cell> layer, int cellSize) {
        if (layer.isEmpty()) return List.of();
        var sorted = new ArrayList<>(layer.values());
        sorted.sort(Comparator.comparingInt(Cell::cellZ).thenComparingInt(Cell::cellX));
        Set<Long> used = new HashSet<>(Math.max(16, layer.size()));
        var boxes = new ArrayList<ProjectionLodPyramid.LodBox>();

        for (Cell start : sorted) {
            long startKey = pack2d(start.cellX(), start.cellZ());
            if (used.contains(startKey)) continue;

            int width = 1;
            while (layer.containsKey(pack2d(start.cellX() + width, start.cellZ()))
                    && !used.contains(pack2d(start.cellX() + width, start.cellZ()))) {
                width++;
            }

            int depth = 1;
            boolean canGrow = true;
            while (canGrow) {
                int nextZ = start.cellZ() + depth;
                for (int dx = 0; dx < width; dx++) {
                    long key = pack2d(start.cellX() + dx, nextZ);
                    if (!layer.containsKey(key) || used.contains(key)) {
                        canGrow = false;
                        break;
                    }
                }
                if (canGrow) depth++;
            }

            int blockCount = 0;
            float densitySum = 0.0F;
            int cellCount = 0;
            for (int dz = 0; dz < depth; dz++) {
                for (int dx = 0; dx < width; dx++) {
                    long key = pack2d(start.cellX() + dx, start.cellZ() + dz);
                    Cell cell = layer.get(key);
                    used.add(key);
                    blockCount += cell.blockCount();
                    densitySum += cell.density();
                    cellCount++;
                }
            }

            boxes.add(toBox(start, start.cellX() + width, start.cellZ() + depth, cellSize,
                    blockCount, densitySum / Math.max(1, cellCount)));
        }
        return boxes;
    }

    private static ProjectionLodPyramid.LodBox toBox(Cell start, int maxCellXExclusive, int maxCellZExclusive,
                                                     int cellSize, int blockCount, float density) {
        return new ProjectionLodPyramid.LodBox(
                start.cellX() * cellSize,
                start.cellY() * cellSize,
                start.cellZ() * cellSize,
                maxCellXExclusive * cellSize,
                (start.cellY() + 1) * cellSize,
                maxCellZExclusive * cellSize,
                start.stateId(),
                blockCount,
                density
        );
    }

    private static long pack2d(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private record LayerKey(int cellY, int groupId) {
    }

    public record Cell(int cellX, int cellY, int cellZ, int stateId, int groupId, int blockCount, float density) {
    }
}
