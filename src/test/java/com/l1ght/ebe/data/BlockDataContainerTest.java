package com.l1ght.ebe.data;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class BlockDataContainerTest {

    @Test
    void testDefaultAir() {
        var c = new BlockDataContainer(4, 4, 4);
        assertEquals("minecraft:air", c.get(0, 0, 0));
        assertEquals("minecraft:air", c.get(3, 3, 3));
    }

    @Test
    void testSetGet() {
        var c = new BlockDataContainer(8, 8, 8);
        c.set(3, 5, 2, "minecraft:stone");
        assertEquals("minecraft:stone", c.get(3, 5, 2));
        assertEquals("minecraft:air", c.get(0, 0, 0));
    }

    @Test
    void testMultipleTypes() {
        var c = new BlockDataContainer(4, 4, 4);
        c.set(0, 0, 0, "minecraft:stone");
        c.set(1, 1, 1, "minecraft:dirt");
        c.set(2, 2, 2, "minecraft:oak_planks");
        assertEquals("minecraft:stone", c.get(0, 0, 0));
        assertEquals("minecraft:dirt", c.get(1, 1, 1));
        assertEquals("minecraft:oak_planks", c.get(2, 2, 2));
    }

    @Test
    void testAutoResize() {
        var c = new BlockDataContainer(16, 1, 1);
        for (int i = 0; i < 16; i++) {
            c.set(i, 0, 0, "block_" + i);
        }
        for (int i = 0; i < 16; i++) {
            assertEquals("block_" + i, c.get(i, 0, 0));
        }
    }

    @Test
    void testRandomRoundTrip() {
        var rng = new Random(42);
        int s = 16;
        var c = new BlockDataContainer(s, s, s);
        String[][][] expected = new String[s][s][s];
        String[] types = {"minecraft:air", "minecraft:stone", "minecraft:dirt", "minecraft:oak_planks",
                "minecraft:cobblestone", "minecraft:sand", "minecraft:glass", "minecraft:bricks"};
        for (int x = 0; x < s; x++) {
            for (int y = 0; y < s; y++) {
                for (int z = 0; z < s; z++) {
                    String type = types[rng.nextInt(types.length)];
                    expected[x][y][z] = type;
                    c.set(x, y, z, type);
                }
            }
        }
        for (int x = 0; x < s; x++) {
            for (int y = 0; y < s; y++) {
                for (int z = 0; z < s; z++) {
                    assertEquals(expected[x][y][z], c.get(x, y, z),
                            "mismatch at (" + x + "," + y + "," + z + ")");
                }
            }
        }
    }

    @Test
    void testOverwrite() {
        var c = new BlockDataContainer(4, 4, 4);
        c.set(1, 1, 1, "minecraft:stone");
        assertEquals("minecraft:stone", c.get(1, 1, 1));
        c.set(1, 1, 1, "minecraft:dirt");
        assertEquals("minecraft:dirt", c.get(1, 1, 1));
    }

    @Test
    void testDimensions() {
        var c = new BlockDataContainer(10, 20, 30);
        assertEquals(10, c.getSizeX());
        assertEquals(20, c.getSizeY());
        assertEquals(30, c.getSizeZ());
        assertEquals(6000, c.getTotalSize());
    }
}
