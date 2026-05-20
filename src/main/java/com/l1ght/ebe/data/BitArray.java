package com.l1ght.ebe.data;

public class BitArray {
    private final long[] data;
    private final int bitsPerEntry;
    private final int size;
    private final long maxEntryValue;

    public BitArray(int bitsPerEntry, int size) {
        this.bitsPerEntry = Math.max(2, bitsPerEntry);
        this.size = size;
        this.maxEntryValue = (1L << this.bitsPerEntry) - 1;
        this.data = new long[(int) (roundUp(size * this.bitsPerEntry, 64) / 64)];
    }

    public int get(int index) {
        int bitStart = index * bitsPerEntry;
        int arrIndex = bitStart / 64;
        int bitOffset = bitStart % 64;
        long value = data[arrIndex] >>> bitOffset;
        int endArrIndex = (bitStart + bitsPerEntry - 1) / 64;
        if (arrIndex != endArrIndex) {
            value |= data[endArrIndex] << (64 - bitOffset);
        }
        return (int) (value & maxEntryValue);
    }

    public void set(int index, int value) {
        int bitStart = index * bitsPerEntry;
        int arrIndex = bitStart / 64;
        int bitOffset = bitStart % 64;
        data[arrIndex] = (data[arrIndex] & ~(maxEntryValue << bitOffset)) | ((long) value << bitOffset);
        int endArrIndex = (bitStart + bitsPerEntry - 1) / 64;
        if (arrIndex != endArrIndex) {
            int bitsInFirst = 64 - bitOffset;
            int bitsInSecond = bitsPerEntry - bitsInFirst;
            long secondMask = (1L << bitsInSecond) - 1;
            data[endArrIndex] = (data[endArrIndex] & ~secondMask) | (value >>> bitsInFirst);
        }
    }

    public int size() {
        return size;
    }

    public int getBitsPerEntry() {
        return bitsPerEntry;
    }

    public long[] getData() {
        return data;
    }

    private static long roundUp(long value, long alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }
}
