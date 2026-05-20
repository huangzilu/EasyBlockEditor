package com.l1ght.ebe.data;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlockStatePalette {
    private Object[] linear;
    private Map<Object, Integer> map;
    private int size;
    private int bits;

    public BlockStatePalette(int bits) {
        this.bits = bits;
        if (bits <= 4) {
            linear = new Object[1 << bits];
        } else {
            map = new LinkedHashMap<>(1 << bits);
        }
    }

    public int idFor(Object state) {
        if (linear != null) {
            for (int i = 0; i < size; i++) {
                if (linear[i].equals(state)) return i;
            }
            if (size >= linear.length) return -1;
            linear[size] = state;
            return size++;
        }
        Integer existing = map.get(state);
        if (existing != null) return existing;
        if (size >= (1 << bits)) return -1;
        map.put(state, size);
        return size++;
    }

    public Object get(int id) {
        if (id < 0 || id >= size) throw new IndexOutOfBoundsException("id=" + id + " size=" + size);
        if (linear != null) return linear[id];
        int i = 0;
        for (Object key : map.keySet()) {
            if (i == id) return key;
            i++;
        }
        throw new IndexOutOfBoundsException(id);
    }

    public int size() {
        return size;
    }

    public int getBits() {
        return bits;
    }

    public boolean needsResize() {
        return size >= (1 << bits);
    }

    public List<Object> allStates() {
        if (linear != null) return Arrays.asList(linein()).subList(0, size);
        return List.copyOf(map.keySet());
    }

    private Object[] linein() {
        return linear;
    }
}
