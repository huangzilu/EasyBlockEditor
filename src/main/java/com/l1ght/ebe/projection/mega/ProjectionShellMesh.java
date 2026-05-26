package com.l1ght.ebe.projection.mega;

import java.util.List;

public record ProjectionShellMesh(int cellSize, List<Face> faces) {
    public static ProjectionShellMesh empty() {
        return new ProjectionShellMesh(1, List.of());
    }

    public boolean isEmpty() {
        return faces == null || faces.isEmpty();
    }

    public enum Direction {
        NORTH,
        SOUTH,
        WEST,
        EAST,
        UP,
        DOWN
    }

    public record Face(
            Direction direction,
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
