package com.l1ght.ebe.projection.mega;

import java.util.List;

public record ProjectionLodPyramid(List<LodLevel> levels) {
    public static ProjectionLodPyramid empty() {
        return new ProjectionLodPyramid(List.of());
    }

    public boolean isEmpty() {
        return levels == null || levels.isEmpty();
    }

    public LodLevel coarsestLevel() {
        if (isEmpty()) {
            return null;
        }
        return levels.get(0);
    }

    public record LodLevel(int cellSize, List<LodBox> boxes) {
        public boolean isEmpty() {
            return boxes == null || boxes.isEmpty();
        }
    }

    public record LodBox(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            int stateId,
            int blockCount,
            float density
    ) {
    }
}
