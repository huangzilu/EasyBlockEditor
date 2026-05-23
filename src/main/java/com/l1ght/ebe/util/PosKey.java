package com.l1ght.ebe.util;

public final class PosKey {
    private static final int X_BITS = 26;
    private static final int Y_BITS = 12;
    private static final int Z_BITS = 26;
    private static final int Z_SHIFT = Y_BITS;
    private static final int X_SHIFT = Y_BITS + Z_BITS;
    private static final long X_MASK = (1L << X_BITS) - 1L;
    private static final long Y_MASK = (1L << Y_BITS) - 1L;
    private static final long Z_MASK = (1L << Z_BITS) - 1L;

    private PosKey() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & X_MASK) << X_SHIFT
                | ((long) z & Z_MASK) << Z_SHIFT
                | ((long) y & Y_MASK);
    }

    public static int unpackX(long packed) {
        return signExtend(packed >> X_SHIFT, X_BITS);
    }

    public static int unpackY(long packed) {
        return signExtend(packed, Y_BITS);
    }

    public static int unpackZ(long packed) {
        return signExtend(packed >> Z_SHIFT, Z_BITS);
    }

    private static int signExtend(long value, int bits) {
        long shift = Long.SIZE - bits;
        return (int) (value << shift >> shift);
    }
}
