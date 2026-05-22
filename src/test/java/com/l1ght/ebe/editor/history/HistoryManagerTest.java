package com.l1ght.ebe.editor.history;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryManagerTest {

    private HistoryEntry makeEntry(int id, HistoryActionType type, int count) {
        return new HistoryEntry(id, type, new Object[0][5], 0, 0, 0, "minecraft:stone", count);
    }

    @Test
    void testPushUndo() {
        var hm = new HistoryManager(10);
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        assertEquals(3, hm.undoSize());
        assertNotNull(hm.undo());
        assertNotNull(hm.undo());
        assertNotNull(hm.undo());
        assertNull(hm.undo());
    }

    @Test
    void testRedo() {
        var hm = new HistoryManager(10);
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.undo();
        hm.undo();
        assertEquals(0, hm.undoSize());
        assertEquals(2, hm.redoSize());
        assertNotNull(hm.redo());
        assertNotNull(hm.redo());
        assertNull(hm.redo());
    }

    @Test
    void testPushClearsRedo() {
        var hm = new HistoryManager(10);
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.undo();
        assertEquals(1, hm.redoSize());
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        assertEquals(0, hm.redoSize());
        assertFalse(hm.canRedo());
    }

    @Test
    void testMaxSize() {
        var hm = new HistoryManager(3);
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        assertEquals(3, hm.undoSize());
    }

    @Test
    void testCanUndoRedo() {
        var hm = new HistoryManager(5);
        assertFalse(hm.canUndo());
        assertFalse(hm.canRedo());
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        assertTrue(hm.canUndo());
        hm.undo();
        assertFalse(hm.canUndo());
        assertTrue(hm.canRedo());
    }

    @Test
    void testClear() {
        var hm = new HistoryManager(5);
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.undo();
        hm.clear();
        assertEquals(0, hm.undoSize());
        assertEquals(0, hm.redoSize());
    }

    @Test
    void testVersionTag() {
        var hm = new HistoryManager(10);
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        var tag = hm.createVersionTag("v1", "first version");
        assertEquals(1, hm.getVersionTags().size());
        assertEquals("v1", tag.label());
        assertEquals("first version", tag.description());
    }

    @Test
    void testBranch() {
        var hm = new HistoryManager(10);
        hm.push(makeEntry(hm.nextId(), HistoryActionType.PLACE, 1));
        hm.createBranch("feature");
        hm.switchBranch("feature");
        assertEquals("feature", hm.getCurrentBranch());
        hm.switchBranch("main");
        assertEquals("main", hm.getCurrentBranch());
    }
}
