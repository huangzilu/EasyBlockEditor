package com.l1ght.ebe.editor;

import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.editor.history.HistoryManager;
import com.l1ght.ebe.editor.selection.SelectionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardManagerTest {

    @Test
    void testTranslateOverlapPreservesRedoOrderAndLayerOwnership() {
        var model = new BuildingModel();
        model.addRegion(4, 1, 1);
        model.setBlockAt(0, 0, 0, "minecraft:stone");
        model.setBlockAt(1, 0, 0, "minecraft:dirt");
        var detail = model.addLayer("detail", true, false);
        assertTrue(model.assignBlockToLayer(0, 0, 0, detail.getId()));

        var selection = new SelectionManager();
        selection.add(0, 0, 0);
        selection.add(1, 0, 0);
        var history = new HistoryManager(20);

        new ClipboardManager().translateSelection(model, selection, 1, 0, 0, history);
        var entry = history.getLastEntry();

        assertNotNull(entry);
        assertTrue(entry.isLayerChange());
        assertEquals("minecraft:air", model.getBlockAt(0, 0, 0));
        assertEquals("minecraft:stone", model.getBlockAt(1, 0, 0));
        assertEquals("minecraft:dirt", model.getBlockAt(2, 0, 0));
        assertEquals(detail.getId(), model.getLayerIdAt(1, 0, 0));

        for (int i = entry.getSnapshots().length - 1; i >= 0; i--) {
            var s = entry.getSnapshots()[i];
            model.setBlockAt((int) s[0], (int) s[1], (int) s[2], s[3]);
        }
        model.restoreLayerState(entry.getBeforeLayerState());
        assertEquals("minecraft:stone", model.getBlockAt(0, 0, 0));
        assertEquals("minecraft:dirt", model.getBlockAt(1, 0, 0));
        assertEquals("minecraft:air", model.getBlockAt(2, 0, 0));
        assertEquals(detail.getId(), model.getLayerIdAt(0, 0, 0));

        for (var s : entry.getSnapshots()) {
            model.setBlockAt((int) s[0], (int) s[1], (int) s[2], s[4]);
        }
        model.restoreLayerState(entry.getAfterLayerState());
        assertEquals("minecraft:air", model.getBlockAt(0, 0, 0));
        assertEquals("minecraft:stone", model.getBlockAt(1, 0, 0));
        assertEquals("minecraft:dirt", model.getBlockAt(2, 0, 0));
        assertEquals(detail.getId(), model.getLayerIdAt(1, 0, 0));
    }
}
