package com.l1ght.ebe.editor.selection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionManagerTest {

    @Test
    void testAddContains() {
        var sm = new SelectionManager();
        sm.add(10, 20, 30);
        assertTrue(sm.contains(10, 20, 30));
        assertFalse(sm.contains(10, 20, 31));
        assertEquals(1, sm.size());
    }

    @Test
    void testRemove() {
        var sm = new SelectionManager();
        sm.add(1, 2, 3);
        sm.remove(1, 2, 3);
        assertFalse(sm.contains(1, 2, 3));
        assertEquals(0, sm.size());
    }

    @Test
    void testToggle() {
        var sm = new SelectionManager();
        sm.toggle(5, 5, 5);
        assertTrue(sm.contains(5, 5, 5));
        sm.toggle(5, 5, 5);
        assertFalse(sm.contains(5, 5, 5));
    }

    @Test
    void testClear() {
        var sm = new SelectionManager();
        for (int i = 0; i < 100; i++) sm.add(i, 0, 0);
        assertEquals(100, sm.size());
        sm.clear();
        assertEquals(0, sm.size());
        assertTrue(sm.isEmpty());
    }

    @Test
    void testPackUnpackRoundTrip() {
        int[][] coords = {{0,0,0}, {100,64,200}, {-50,0,-50}, {1000,255,1000}};
        for (int[] c : coords) {
            long packed = SelectionManager.packPos(c[0], c[1], c[2]);
            assertEquals(c[0], SelectionManager.unpackX(packed));
            assertEquals(c[1], SelectionManager.unpackY(packed));
            assertEquals(c[2], SelectionManager.unpackZ(packed));
        }
    }

    @Test
    void testLargeSelection() {
        var sm = new SelectionManager();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    sm.add(x, y, z);
                }
            }
        }
        assertEquals(4096, sm.size());
        assertTrue(sm.contains(8, 8, 8));
    }
}
