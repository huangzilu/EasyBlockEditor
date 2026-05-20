package com.l1ght.ebe.data;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class BlockStatePaletteTest {

    @Test
    void testLinearBasic() {
        var palette = new BlockStatePalette(2);
        int id0 = palette.idFor("air");
        assertEquals(0, id0);
        int id1 = palette.idFor("stone");
        assertEquals(1, id1);
        assertEquals("air", palette.get(0));
        assertEquals("stone", palette.get(1));
        assertEquals(2, palette.size());
    }

    @Test
    void testLinearDuplicate() {
        var palette = new BlockStatePalette(4);
        palette.idFor("stone");
        int id2 = palette.idFor("stone");
        assertEquals(0, id2);
        assertEquals(1, palette.size());
    }

    @Test
    void testLinearFull() {
        var palette = new BlockStatePalette(2);
        palette.idFor("a");
        palette.idFor("b");
        palette.idFor("c");
        palette.idFor("d");
        assertTrue(palette.needsResize());
        assertEquals(-1, palette.idFor("e"));
    }

    @Test
    void testHashMapBasic() {
        var palette = new BlockStatePalette(5);
        for (int i = 0; i < 20; i++) {
            assertEquals(i, palette.idFor("block_" + i));
        }
        assertEquals(20, palette.size());
        for (int i = 0; i < 20; i++) {
            assertEquals("block_" + i, palette.get(i));
        }
    }

    @Test
    void testHashMapDuplicate() {
        var palette = new BlockStatePalette(5);
        palette.idFor("x");
        palette.idFor("y");
        assertEquals(0, palette.idFor("x"));
        assertEquals(1, palette.idFor("y"));
        assertEquals(2, palette.size());
    }

    @Test
    void testHashMapFull() {
        var palette = new BlockStatePalette(3);
        for (int i = 0; i < 8; i++) {
            palette.idFor("b" + i);
        }
        assertTrue(palette.needsResize());
        assertEquals(-1, palette.idFor("extra"));
    }

    @Test
    void testRandomRoundTrip() {
        var rng = new Random(42);
        int bits = 8;
        var palette = new BlockStatePalette(bits);
        List<String> states = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String state = "state_" + rng.nextInt(200);
            int id = palette.idFor(state);
            if (!states.contains(state)) {
                states.add(state);
                assertEquals(states.size() - 1, id);
            }
        }
        for (int i = 0; i < states.size(); i++) {
            assertEquals(states.get(i), palette.get(i));
        }
    }

    @Test
    void testAllStates() {
        var palette = new BlockStatePalette(4);
        palette.idFor("a");
        palette.idFor("b");
        palette.idFor("c");
        var all = palette.allStates();
        assertEquals(3, all.size());
        assertTrue(all.contains("a"));
        assertTrue(all.contains("b"));
        assertTrue(all.contains("c"));
    }

    @Test
    void testIndexOutOfBounds() {
        var palette = new BlockStatePalette(4);
        palette.idFor("x");
        assertThrows(IndexOutOfBoundsException.class, () -> palette.get(5));
        assertThrows(IndexOutOfBoundsException.class, () -> palette.get(-1));
    }
}
