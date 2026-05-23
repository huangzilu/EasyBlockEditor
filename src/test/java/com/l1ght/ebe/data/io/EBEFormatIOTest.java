package com.l1ght.ebe.data.io;

import com.l1ght.ebe.data.BuildingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EBEFormatIOTest {

    private BuildingModel.Layer findLayer(BuildingModel model, String name) {
        return model.getLayers().stream()
                .filter(l -> name.equals(l.getName())).findFirst().orElse(null);
    }

    @Test
    void testRoundTrip(@TempDir Path dir) throws Exception {
        var file = dir.resolve("test.ebe");
        var model = new BuildingModel();
        model.getMetadata().setName("测试建筑");
        model.getMetadata().setAuthor("L1ghT");
        model.getMetadata().setDescription("中英混合 description");

        var region = model.addRegion(4, 4, 4);
        region.getBlocks().set(0, 0, 0, "minecraft:stone");
        region.getBlocks().set(1, 2, 3, "minecraft:oak_planks");
        region.getBlocks().set(3, 3, 3, "minecraft:glass");

        EBEFormatIO.write(model, file);
        assertTrue(file.toFile().exists());

        var loaded = EBEFormatIO.read(file);
        assertEquals("测试建筑", loaded.getMetadata().getName());
        assertEquals("L1ghT", loaded.getMetadata().getAuthor());
        assertEquals("中英混合 description", loaded.getMetadata().getDescription());
        assertEquals(1, loaded.getRegions().size());

        var r = loaded.getRegions().get(0);
        assertEquals("minecraft:stone", r.getWorldBlock(0, 0, 0));
        assertEquals("minecraft:oak_planks", r.getWorldBlock(1, 2, 3));
        assertEquals("minecraft:glass", r.getWorldBlock(3, 3, 3));
        assertEquals("minecraft:air", r.getWorldBlock(2, 2, 2));
    }

    @Test
    void testMultipleRegions(@TempDir Path dir) throws Exception {
        var file = dir.resolve("multi.ebe");
        var model = new BuildingModel();
        model.addRegion("bottom", 0, 0, 0, 4, 4, 4);
        model.addRegion("top", 0, 4, 0, 4, 4, 4);
        model.setBlockAt(1, 1, 1, "minecraft:stone");
        model.setBlockAt(1, 5, 1, "minecraft:dirt");

        EBEFormatIO.write(model, file);
        var loaded = EBEFormatIO.read(file);

        assertEquals(2, loaded.getRegions().size());
        assertEquals("minecraft:stone", loaded.getBlockAt(1, 1, 1));
        assertEquals("minecraft:dirt", loaded.getBlockAt(1, 5, 1));
    }

    @Test
    void testLayers(@TempDir Path dir) throws Exception {
        var file = dir.resolve("layers.ebe");
        var model = new BuildingModel();
        model.addLayer("walls", true, false);
        model.addLayer("roof", false, true);

        EBEFormatIO.write(model, file);
        var loaded = EBEFormatIO.read(file);

        assertEquals(3, loaded.getLayers().size());
        var defaultLayer = findLayer(loaded, "default");
        assertNotNull(defaultLayer);
        assertTrue(defaultLayer.isVisible());
        var walls = findLayer(loaded, "walls");
        assertNotNull(walls);
        assertTrue(walls.isVisible());
        var roof = findLayer(loaded, "roof");
        assertNotNull(roof);
        assertFalse(roof.isVisible());
        assertTrue(roof.isLocked());
    }

    @Test
    void testBlockLayerOverridesRoundTrip(@TempDir Path dir) throws Exception {
        var file = dir.resolve("layer-overrides.ebe");
        var model = new BuildingModel();
        var region = model.addRegion(2, 1, 1);
        region.getBlocks().set(0, 0, 0, "minecraft:stone");
        region.getBlocks().set(1, 0, 0, "minecraft:dirt");
        var layer = model.addLayer("detail", false, true);
        assertTrue(model.assignBlockToLayer(1, 0, 0, layer.getId()));

        EBEFormatIO.write(model, file);
        var loaded = EBEFormatIO.read(file);
        var loadedLayer = findLayer(loaded, "detail");

        assertNotNull(loadedLayer);
        assertFalse(loadedLayer.isVisible());
        assertTrue(loadedLayer.isLocked());
        assertEquals(loadedLayer.getId(), loaded.getLayerIdAt(1, 0, 0));
        assertNotEquals(loadedLayer.getId(), loaded.getLayerIdAt(0, 0, 0));
    }

    @Test
    void testUTF8Filename(@TempDir Path dir) throws Exception {
        var file = dir.resolve("中文文件名.ebe");
        var model = new BuildingModel();
        model.getMetadata().setName("中文建筑");
        EBEFormatIO.write(model, file);
        var loaded = EBEFormatIO.read(file);
        assertEquals("中文建筑", loaded.getMetadata().getName());
    }
}
