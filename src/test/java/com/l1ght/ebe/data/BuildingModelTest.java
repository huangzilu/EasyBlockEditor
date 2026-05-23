package com.l1ght.ebe.data;

import com.l1ght.ebe.util.PosKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildingModelTest {

    @Test
    void testCreateEmpty() {
        var model = new BuildingModel();
        assertEquals("Untitled", model.getMetadata().getName());
        assertEquals(0, model.getRegions().size());
        assertEquals(1, model.getLayers().size());
        assertEquals("default", model.getLayers().get(0).getName());
    }

    @Test
    void testAddRegion() {
        var model = new BuildingModel();
        var r = model.addRegion(16, 16, 16);
        assertEquals(16, r.getSizeX());
        assertEquals("region_1", r.getName());
        assertEquals(1, model.getRegions().size());
    }

    @Test
    void testBlockAccess() {
        var model = new BuildingModel();
        model.addRegion(8, 8, 8);
        model.setBlockAt(3, 5, 2, "minecraft:stone");
        assertEquals("minecraft:stone", model.getBlockAt(3, 5, 2));
        assertEquals("minecraft:air", model.getBlockAt(0, 0, 0));
    }

    @Test
    void testMultipleRegions() {
        var model = new BuildingModel();
        model.addRegion("bottom", 0, 0, 0, 8, 4, 8);
        model.addRegion("top", 0, 4, 0, 8, 4, 8);
        model.setBlockAt(3, 2, 3, "minecraft:stone");
        model.setBlockAt(3, 6, 3, "minecraft:dirt");
        assertEquals("minecraft:stone", model.getBlockAt(3, 2, 3));
        assertEquals("minecraft:dirt", model.getBlockAt(3, 6, 3));
    }

    @Test
    void testRegionContainsWorldPos() {
        var r = new Region("test", 10, 0, 10, 8, 8, 8);
        assertTrue(r.containsWorldPos(10, 0, 10));
        assertTrue(r.containsWorldPos(17, 7, 17));
        assertFalse(r.containsWorldPos(9, 0, 10));
        assertFalse(r.containsWorldPos(18, 0, 10));
    }

    @Test
    void testMetadata() {
        var model = new BuildingModel();
        model.getMetadata().setName("我的建筑");
        model.getMetadata().setAuthor("L1ghT");
        assertEquals("我的建筑", model.getMetadata().getName());
        assertEquals("L1ghT", model.getMetadata().getAuthor());
    }

    @Test
    void testLayers() {
        var model = new BuildingModel();
        model.addLayer("roof", true, false);
        model.addLayer("walls", true, false);
        model.addLayer("foundation", true, true);
        assertEquals(4, model.getLayers().size());
        var foundation = model.getLayers().stream()
                .filter(l -> "foundation".equals(l.getName())).findFirst().orElse(null);
        assertNotNull(foundation);
        assertTrue(foundation.isLocked());
        var roof = model.getLayers().stream()
                .filter(l -> "roof".equals(l.getName())).findFirst().orElse(null);
        assertNotNull(roof);
        assertFalse(roof.isLocked());
    }

    @Test
    void testSparseBlockLayerAssignment() {
        var model = new BuildingModel();
        var region = model.addRegion(2, 1, 1);
        region.getBlocks().set(0, 0, 0, "minecraft:stone");
        region.getBlocks().set(1, 0, 0, "minecraft:dirt");
        var detail = model.addLayer("detail", true, false);

        assertTrue(model.assignBlockToLayer(0, 0, 0, detail.getId()));
        assertEquals(detail.getId(), model.getLayerIdAt(0, 0, 0));
        assertNotEquals(detail.getId(), model.getLayerIdAt(1, 0, 0));
        assertEquals(1, model.countBlocksInLayer(detail.getId()));

        detail.setVisible(false);
        assertFalse(model.isLayerVisibleAt(region, 0, 0, 0));
        assertTrue(model.isLayerVisibleAt(region, 1, 0, 0));
    }

    @Test
    void testMergeLayerIntoTransfersSparseAssignments() {
        var model = new BuildingModel();
        var region = model.addRegion(2, 1, 1);
        region.getBlocks().set(0, 0, 0, "minecraft:stone");
        region.getBlocks().set(1, 0, 0, "minecraft:dirt");
        var source = model.addLayer("source", true, false);
        var target = model.addLayer("target", true, false);

        assertTrue(model.assignBlockToLayer(0, 0, 0, source.getId()));
        model.mergeLayerInto(source.getId(), target.getId());

        assertNull(model.getLayer(source.getId()));
        assertEquals(target.getId(), model.getLayerIdAt(0, 0, 0));
        assertEquals(1, model.countBlocksInLayer(target.getId()));
    }

    @Test
    void testBlockOutsideRegions() {
        var model = new BuildingModel();
        model.addRegion(4, 4, 4);
        assertEquals("minecraft:air", model.getBlockAt(100, 100, 100));
    }

    @Test
    void testPositionKeyRoundTrip() {
        long packed = PosKey.pack(-12345, -64, 54321);
        assertEquals(-12345, PosKey.unpackX(packed));
        assertEquals(-64, PosKey.unpackY(packed));
        assertEquals(54321, PosKey.unpackZ(packed));
    }
}
