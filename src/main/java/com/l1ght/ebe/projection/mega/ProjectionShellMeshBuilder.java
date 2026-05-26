package com.l1ght.ebe.projection.mega;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProjectionShellMeshBuilder {
    private ProjectionShellMeshBuilder() {
    }

    public static ProjectionShellMesh build(ProjectionLodPyramid.LodLevel level) {
        if (level == null || level.isEmpty()) {
            return ProjectionShellMesh.empty();
        }

        int cellSize = Math.max(1, level.cellSize());
        var cells = expandCells(level, cellSize);
        if (cells.isEmpty()) {
            return ProjectionShellMesh.empty();
        }

        var faces = new ArrayList<ProjectionShellMesh.Face>();
        for (var direction : ProjectionShellMesh.Direction.values()) {
            faces.addAll(meshDirection(direction, cells, cellSize));
        }
        faces.sort(Comparator
                .comparing((ProjectionShellMesh.Face face) -> face.direction().ordinal())
                .thenComparingInt(ProjectionShellMesh.Face::minY)
                .thenComparingInt(ProjectionShellMesh.Face::minZ)
                .thenComparingInt(ProjectionShellMesh.Face::minX));
        return new ProjectionShellMesh(cellSize, List.copyOf(faces));
    }

    private static Map<CellKey, Cell> expandCells(ProjectionLodPyramid.LodLevel level, int cellSize) {
        var cells = new LinkedHashMap<CellKey, Cell>();
        for (var box : level.boxes()) {
            int minCellX = Math.floorDiv(box.minX(), cellSize);
            int minCellY = Math.floorDiv(box.minY(), cellSize);
            int minCellZ = Math.floorDiv(box.minZ(), cellSize);
            int maxCellX = Math.floorDiv(Math.max(box.minX(), box.maxX() - 1), cellSize);
            int maxCellY = Math.floorDiv(Math.max(box.minY(), box.maxY() - 1), cellSize);
            int maxCellZ = Math.floorDiv(Math.max(box.minZ(), box.maxZ() - 1), cellSize);
            int cellCount = Math.max(1, (maxCellX - minCellX + 1) * (maxCellY - minCellY + 1) * (maxCellZ - minCellZ + 1));
            int perCellBlocks = Math.max(1, box.blockCount() / cellCount);
            for (int cy = minCellY; cy <= maxCellY; cy++) {
                for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                    for (int cx = minCellX; cx <= maxCellX; cx++) {
                        var key = new CellKey(cx, cy, cz);
                        cells.put(key, new Cell(cx, cy, cz, box.stateId(), perCellBlocks, box.density()));
                    }
                }
            }
        }
        return cells;
    }

    private static List<ProjectionShellMesh.Face> meshDirection(
            ProjectionShellMesh.Direction direction,
            Map<CellKey, Cell> cells,
            int cellSize
    ) {
        var planes = new LinkedHashMap<PlaneKey, Map<Long, FaceCell>>();
        for (var cell : cells.values()) {
            if (cells.containsKey(neighbor(cell, direction))) {
                continue;
            }
            var face = FaceCell.from(cell, direction);
            planes.computeIfAbsent(face.planeKey(), ignored -> new LinkedHashMap<>())
                    .put(pack2d(face.u(), face.v()), face);
        }

        var result = new ArrayList<ProjectionShellMesh.Face>();
        for (var plane : planes.values()) {
            result.addAll(meshPlane(direction, plane, cellSize));
        }
        return result;
    }

    private static List<ProjectionShellMesh.Face> meshPlane(
            ProjectionShellMesh.Direction direction,
            Map<Long, FaceCell> plane,
            int cellSize
    ) {
        if (plane.isEmpty()) return List.of();

        var sorted = new ArrayList<>(plane.values());
        sorted.sort(Comparator
                .comparingInt(FaceCell::v)
                .thenComparingInt(FaceCell::u));
        Set<Long> used = new HashSet<>(Math.max(16, plane.size()));
        var faces = new ArrayList<ProjectionShellMesh.Face>();

        for (var start : sorted) {
            long startKey = pack2d(start.u(), start.v());
            if (used.contains(startKey)) continue;

            int width = 1;
            while (canMerge(plane, used, start, start.u() + width, start.v())) {
                width++;
            }

            int height = 1;
            boolean canGrow = true;
            while (canGrow) {
                int nextV = start.v() + height;
                for (int du = 0; du < width; du++) {
                    if (!canMerge(plane, used, start, start.u() + du, nextV)) {
                        canGrow = false;
                        break;
                    }
                }
                if (canGrow) height++;
            }

            int blocks = 0;
            float densitySum = 0.0F;
            int count = 0;
            for (int dv = 0; dv < height; dv++) {
                for (int du = 0; du < width; du++) {
                    long key = pack2d(start.u() + du, start.v() + dv);
                    var cell = plane.get(key);
                    if (cell == null) continue;
                    used.add(key);
                    blocks += cell.blockCount();
                    densitySum += cell.density();
                    count++;
                }
            }

            faces.add(toFace(direction, start, width, height, cellSize, blocks, densitySum / Math.max(1, count)));
        }
        return faces;
    }

    private static boolean canMerge(Map<Long, FaceCell> plane, Set<Long> used, FaceCell start, int u, int v) {
        long key = pack2d(u, v);
        if (used.contains(key)) return false;
        var cell = plane.get(key);
        return cell != null && cell.stateId() == start.stateId();
    }

    private static ProjectionShellMesh.Face toFace(
            ProjectionShellMesh.Direction direction,
            FaceCell start,
            int width,
            int height,
            int cellSize,
            int blockCount,
            float density
    ) {
        int x0 = start.cellX() * cellSize;
        int y0 = start.cellY() * cellSize;
        int z0 = start.cellZ() * cellSize;
        int x1 = x0 + cellSize;
        int y1 = y0 + cellSize;
        int z1 = z0 + cellSize;
        int w = width * cellSize;
        int h = height * cellSize;

        return switch (direction) {
            case NORTH -> new ProjectionShellMesh.Face(direction, x0, y0, z0, x0 + w, y0 + h, z0,
                    start.stateId(), blockCount, density);
            case SOUTH -> new ProjectionShellMesh.Face(direction, x0, y0, z1, x0 + w, y0 + h, z1,
                    start.stateId(), blockCount, density);
            case WEST -> new ProjectionShellMesh.Face(direction, x0, y0, z0, x0, y0 + h, z0 + w,
                    start.stateId(), blockCount, density);
            case EAST -> new ProjectionShellMesh.Face(direction, x1, y0, z0, x1, y0 + h, z0 + w,
                    start.stateId(), blockCount, density);
            case UP -> new ProjectionShellMesh.Face(direction, x0, y1, z0, x0 + w, y1, z0 + h,
                    start.stateId(), blockCount, density);
            case DOWN -> new ProjectionShellMesh.Face(direction, x0, y0, z0, x0 + w, y0, z0 + h,
                    start.stateId(), blockCount, density);
        };
    }

    private static CellKey neighbor(Cell cell, ProjectionShellMesh.Direction direction) {
        return switch (direction) {
            case NORTH -> new CellKey(cell.cellX(), cell.cellY(), cell.cellZ() - 1);
            case SOUTH -> new CellKey(cell.cellX(), cell.cellY(), cell.cellZ() + 1);
            case WEST -> new CellKey(cell.cellX() - 1, cell.cellY(), cell.cellZ());
            case EAST -> new CellKey(cell.cellX() + 1, cell.cellY(), cell.cellZ());
            case UP -> new CellKey(cell.cellX(), cell.cellY() + 1, cell.cellZ());
            case DOWN -> new CellKey(cell.cellX(), cell.cellY() - 1, cell.cellZ());
        };
    }

    private static long pack2d(int a, int b) {
        return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
    }

    private record CellKey(int x, int y, int z) {
    }

    private record PlaneKey(ProjectionShellMesh.Direction direction, int plane, int stateId) {
    }

    private record Cell(int cellX, int cellY, int cellZ, int stateId, int blockCount, float density) {
    }

    private record FaceCell(
            int cellX,
            int cellY,
            int cellZ,
            ProjectionShellMesh.Direction direction,
            int u,
            int v,
            int plane,
            int stateId,
            int blockCount,
            float density
    ) {
        private static FaceCell from(Cell cell, ProjectionShellMesh.Direction direction) {
            return switch (direction) {
                case NORTH -> new FaceCell(cell.cellX(), cell.cellY(), cell.cellZ(),
                        direction,
                        cell.cellX(), cell.cellY(), cell.cellZ(), cell.stateId(), cell.blockCount(), cell.density());
                case SOUTH -> new FaceCell(cell.cellX(), cell.cellY(), cell.cellZ(),
                        direction,
                        cell.cellX(), cell.cellY(), cell.cellZ() + 1, cell.stateId(), cell.blockCount(), cell.density());
                case WEST -> new FaceCell(cell.cellX(), cell.cellY(), cell.cellZ(),
                        direction,
                        cell.cellZ(), cell.cellY(), cell.cellX(), cell.stateId(), cell.blockCount(), cell.density());
                case EAST -> new FaceCell(cell.cellX(), cell.cellY(), cell.cellZ(),
                        direction,
                        cell.cellZ(), cell.cellY(), cell.cellX() + 1, cell.stateId(), cell.blockCount(), cell.density());
                case UP -> new FaceCell(cell.cellX(), cell.cellY(), cell.cellZ(),
                        direction,
                        cell.cellX(), cell.cellZ(), cell.cellY() + 1, cell.stateId(), cell.blockCount(), cell.density());
                case DOWN -> new FaceCell(cell.cellX(), cell.cellY(), cell.cellZ(),
                        direction,
                        cell.cellX(), cell.cellZ(), cell.cellY(), cell.stateId(), cell.blockCount(), cell.density());
            };
        }

        private PlaneKey planeKey() {
            return new PlaneKey(direction, plane, stateId);
        }
    }
}
