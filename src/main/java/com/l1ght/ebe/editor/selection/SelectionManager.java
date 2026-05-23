package com.l1ght.ebe.editor.selection;

import com.l1ght.ebe.util.PosKey;

import java.util.*;
import java.util.stream.Collectors;

public class SelectionManager {
    private final Set<Long> selected = new HashSet<>();
    private boolean nbtSensitive = false;

    public static long packPos(int x, int y, int z) {
        return PosKey.pack(x, y, z);
    }

    public static int unpackX(long packed) { return PosKey.unpackX(packed); }
    public static int unpackY(long packed) { return PosKey.unpackY(packed); }
    public static int unpackZ(long packed) { return PosKey.unpackZ(packed); }

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

    public Set<Long> getAllPacked() { return Collections.unmodifiableSet(selected); }

    public boolean isNbtSensitive() { return nbtSensitive; }
    public void setNbtSensitive(boolean v) { this.nbtSensitive = v; }
}
