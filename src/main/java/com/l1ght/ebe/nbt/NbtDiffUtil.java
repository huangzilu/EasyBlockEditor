package com.l1ght.ebe.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class NbtDiffUtil {
    private NbtDiffUtil() {
    }

    public static List<DiffEntry> diff(CompoundTag left, CompoundTag right, Collection<String> ignoreRules) {
        var out = new ArrayList<DiffEntry>();
        compare("", left == null ? new CompoundTag() : left, right == null ? new CompoundTag() : right,
                NbtPathRules.normalize(ignoreRules), out);
        return out;
    }

    private static void compare(String path, Tag left, Tag right, List<String> ignoreRules, List<DiffEntry> out) {
        if (NbtPathRules.matches(path, ignoreRules)) return;
        if (left == null && right == null) return;
        if (left == null) {
            out.add(new DiffEntry(path, DiffKind.ADDED, "", render(right), "", typeName(right)));
            return;
        }
        if (right == null) {
            out.add(new DiffEntry(path, DiffKind.REMOVED, render(left), "", typeName(left), ""));
            return;
        }
        if (left.getId() != right.getId()) {
            out.add(new DiffEntry(path, DiffKind.TYPE_CHANGED, render(left), render(right), typeName(left), typeName(right)));
            return;
        }
        if (left instanceof CompoundTag lc && right instanceof CompoundTag rc) {
            var keys = new LinkedHashSet<String>();
            keys.addAll(lc.getAllKeys());
            keys.addAll(rc.getAllKeys());
            for (String key : keys) {
                compare(append(path, key), lc.get(key), rc.get(key), ignoreRules, out);
            }
            return;
        }
        if (left instanceof ListTag ll && right instanceof ListTag rl) {
            int max = Math.max(ll.size(), rl.size());
            for (int i = 0; i < max; i++) {
                Tag l = i < ll.size() ? ll.get(i) : null;
                Tag r = i < rl.size() ? rl.get(i) : null;
                compare(path + "[" + i + "]", l, r, ignoreRules, out);
            }
            return;
        }
        if (!Objects.equals(left, right)) {
            out.add(new DiffEntry(path, DiffKind.CHANGED, render(left), render(right), typeName(left), typeName(right)));
        }
    }

    private static String append(String base, String key) {
        if (base == null || base.isEmpty()) return "/" + key;
        return base + "/" + key;
    }

    private static String render(Tag tag) {
        return tag == null ? "" : tag.toString();
    }

    private static String typeName(Tag tag) {
        return tag == null ? "" : tag.getType().getName();
    }

    public enum DiffKind {
        ADDED,
        REMOVED,
        CHANGED,
        TYPE_CHANGED
    }

    public record DiffEntry(String path, DiffKind kind, String before, String after, String beforeType, String afterType) {
    }
}
