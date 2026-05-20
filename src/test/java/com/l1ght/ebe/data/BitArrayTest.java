package com.l1ght.ebe.data;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class BitArrayTest {

    @Test
    void testBasicGetSet() {
        var arr = new BitArray(4, 16);
        arr.set(0, 5);
        assertEquals(5, arr.get(0));
        arr.set(15, 12);
        assertEquals(12, arr.get(15));
        assertEquals(0, arr.get(1));
    }

    @Test
    void testCrossLongBoundary() {
        var arr = new BitArray(5, 100);
        int index = 12;
        arr.set(index, 31);
        assertEquals(31, arr.get(index));
        arr.set(index, 0);
        assertEquals(0, arr.get(index));
    }

    @Test
    void testRandomRoundTrip() {
        var rng = new Random(42);
        int bitsPerEntry = 4;
        int size = 10000;
        var arr = new BitArray(bitsPerEntry, size);
        int maxVal = (1 << bitsPerEntry) - 1;
        int[] expected = new int[size];

        for (int i = 0; i < size; i++) {
            expected[i] = rng.nextInt(maxVal + 1);
            arr.set(i, expected[i]);
        }
        for (int i = 0; i < size; i++) {
            assertEquals(expected[i], arr.get(i), "mismatch at index " + i);
        }
    }

    @Test
    void testLargeBitsPerEntry() {
        var arr = new BitArray(16, 1000);
        var rng = new Random(123);
        int[] expected = new int[1000];
        for (int i = 0; i < 1000; i++) {
            expected[i] = rng.nextInt(65536);
            arr.set(i, expected[i]);
        }
        for (int i = 0; i < 1000; i++) {
            assertEquals(expected[i], arr.get(i), "mismatch at index " + i);
        }
    }

    @Test
    void testOverwrite() {
        var arr = new BitArray(4, 10);
        arr.set(5, 7);
        assertEquals(7, arr.get(5));
        arr.set(5, 3);
        assertEquals(3, arr.get(5));
        arr.set(5, 0);
        assertEquals(0, arr.get(5));
    }

    @Test
    void testMaxValue() {
        int bits = 8;
        var arr = new BitArray(bits, 10);
        int maxVal = (1 << bits) - 1;
        for (int i = 0; i < 10; i++) {
            arr.set(i, maxVal);
        }
        for (int i = 0; i < 10; i++) {
            assertEquals(maxVal, arr.get(i));
        }
    }

    @Test
    void testSize() {
        assertEquals(100, new BitArray(4, 100).size());
        assertEquals(1, new BitArray(2, 1).size());
    }

    @Test
    void testBitsPerEntryMin2() {
        var arr = new BitArray(1, 10);
        assertEquals(2, arr.getBitsPerEntry());
    }
}
