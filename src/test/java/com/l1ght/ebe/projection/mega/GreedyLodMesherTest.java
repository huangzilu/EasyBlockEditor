package com.l1ght.ebe.projection.mega;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreedyLodMesherTest {
    @Test
    void mergesTwoDimensionalRectanglesByVisualGroup() {
        List<GreedyLodMesher.Cell> cells = new ArrayList<>();
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 5; x++) {
                cells.add(new GreedyLodMesher.Cell(x, 2, z, x % 2, 7, 1, 1.0F));
            }
        }

        var boxes = GreedyLodMesher.mesh(cells, 16);

        assertEquals(1, boxes.size());
        var box = boxes.getFirst();
        assertEquals(0, box.minX());
        assertEquals(32, box.minY());
        assertEquals(0, box.minZ());
        assertEquals(80, box.maxX());
        assertEquals(48, box.maxY());
        assertEquals(64, box.maxZ());
        assertEquals(20, box.blockCount());
    }

    @Test
    void keepsDifferentVisualGroupsSeparate() {
        var boxes = GreedyLodMesher.mesh(List.of(
                new GreedyLodMesher.Cell(0, 0, 0, 1, 10, 1, 1.0F),
                new GreedyLodMesher.Cell(1, 0, 0, 2, 20, 1, 1.0F)
        ), 16);

        assertEquals(2, boxes.size());
    }
}
