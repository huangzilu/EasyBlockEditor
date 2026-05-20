package com.l1ght.ebe.editor.selection;

import java.util.*;
import java.util.stream.Collectors;

public class SelectionManager {
    private final Set<Long> selected = new HashSet<>();
    private boolean nbtSensitive = false;

    public static long packPos(int x, int y, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) y & 0xFFFFL) << 32) | (((long) z & 0xFFFFFFFFL) << 48);
    }

    public static int unpackX(long packed) { return (int) packed; }
    public static int unpackY(long packed) { return (int) ((packed >> 32) & 0xFFFF); }
    public static int unpackZ(long packed) { return (int) (packed >> 48); }

    public void add(int x, int y, int z) { selected.add(packPos(x, y, z)); }
    public void remove(int x, int y, int z) { selected.remove(packPos(x, y, z)); }
    public void toggle(int x, int y, int z) {
        long p = packPos(x, y, z);
        if (!selected.remove(p)) selected.add(p);
    }
    public boolean contains(int x, int y, int z) { return selected.contains(packPos(x, y, z)); }
    public void clear() { selected.clear(); }
    public int size() { return selected.size(); }
    public boolean isEmpty() { return selected.isEmpty(); }
    public Set<long[]> getPositions() {
        return selected.stream()
            .map(p -> new long[]{unpackX(p), unpackY(p), unpackZ(p)})
            .collect(Collectors.toSet());
    }

    public boolean isNbtSensitive() { return nbtSensitive; }
    public void setNbtSensitive(boolean v) { this.nbtSensitive = v; }
}
