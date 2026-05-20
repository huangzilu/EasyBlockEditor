package com.l1ght.ebe.editor.history;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryManagerTest {

    @Test
    void testPushUndo() {
        var hm = new HistoryManager<String>(10);
        hm.push("state1");
        hm.push("state2");
        hm.push("state3");
        assertEquals(3, hm.undoSize());
        assertEquals("state3", hm.undo());
        assertEquals("state2", hm.undo());
        assertEquals("state1", hm.undo());
        assertNull(hm.undo());
    }

    @Test
    void testRedo() {
        var hm = new HistoryManager<String>(10);
        hm.push("a");
        hm.push("b");
        hm.undo();
        hm.undo();
        assertEquals(0, hm.undoSize());
        assertEquals(2, hm.redoSize());
        assertEquals("a", hm.redo());
        assertEquals("b", hm.redo());
        assertNull(hm.redo());
    }

    @Test
    void testPushClearsRedo() {
        var hm = new HistoryManager<String>(10);
        hm.push("a");
        hm.push("b");
        hm.undo();
        assertEquals(1, hm.redoSize());
        hm.push("c");
        assertEquals(0, hm.redoSize());
        assertFalse(hm.canRedo());
    }

    @Test
    void testMaxSize() {
        var hm = new HistoryManager<Integer>(3);
        hm.push(1);
        hm.push(2);
        hm.push(3);
        hm.push(4);
        assertEquals(3, hm.undoSize());
    }

    @Test
    void testCanUndoRedo() {
        var hm = new HistoryManager<String>(5);
        assertFalse(hm.canUndo());
        assertFalse(hm.canRedo());
        hm.push("x");
        assertTrue(hm.canUndo());
        hm.undo();
        assertFalse(hm.canUndo());
        assertTrue(hm.canRedo());
    }

    @Test
    void testClear() {
        var hm = new HistoryManager<String>(5);
        hm.push("a");
        hm.push("b");
        hm.undo();
        hm.clear();
        assertEquals(0, hm.undoSize());
        assertEquals(0, hm.redoSize());
    }
}
