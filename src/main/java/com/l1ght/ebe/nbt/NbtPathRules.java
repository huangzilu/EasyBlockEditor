package com.l1ght.ebe.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class NbtPathRules {
    private static final List<String> DEFAULT_IGNORES = List.of("/x", "/y", "/z", "/id");

    private NbtPathRules() {
    }

    public static List<String> normalize(Collection<String> rules) {
        var normalized = new ArrayList<String>();
        if (rules != null) {
            for (String rule : rules) {
                String value = normalizeRule(rule);
                if (!value.isEmpty() && !normalized.contains(value)) normalized.add(value);
            }
        }
        for (String value : DEFAULT_IGNORES) {
            if (!normalized.contains(value)) normalized.add(value);
        }
        return normalized;
    }

    public static boolean matches(String path, Collection<String> rules) {
        String normalizedPath = normalizePath(path);
        for (String rule : normalize(rules)) {
            if (rule.equals("/**")) return true;
            if (rule.endsWith("/**")) {
                String prefix = rule.substring(0, rule.length() - 3);
                if (normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/")) return true;
            } else if (rule.contains("[*]")) {
                if (wildcardListMatch(normalizedPath, rule)) return true;
            } else if (normalizedPath.equals(rule)) {
                return true;
            }
        }
        return false;
    }

    public static CompoundTag filteredCopy(CompoundTag tag, Collection<String> rules) {
        if (tag == null) return new CompoundTag();
        var copy = filterTag(tag, "", normalize(rules));
        return copy instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    private static Tag filterTag(Tag tag, String path, List<String> rules) {
        if (matches(path, rules)) return null;
        if (tag instanceof CompoundTag compound) {
            var out = new CompoundTag();
            for (String key : compound.getAllKeys()) {
                String childPath = append(path, key);
                Tag child = filterTag(compound.get(key), childPath, rules);
                if (child != null) out.put(key, child);
            }
            return out;
        }
        if (tag instanceof ListTag list) {
            var out = new ListTag();
            for (int i = 0; i < list.size(); i++) {
                String childPath = path + "[" + i + "]";
                Tag child = filterTag(list.get(i), childPath, rules);
                if (child != null) out.add(child);
            }
            return out;
        }
        return tag.copy();
    }

    private static String append(String base, String key) {
        if (base == null || base.isEmpty()) return "/" + key;
        return base + "/" + key;
    }

    private static String normalizeRule(String rule) {
        if (rule == null) return "";
        String value = normalizePath(rule.trim());
        while (value.contains("//")) value = value.replace("//", "/");
        return value;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        String value = path.trim().replace('\\', '/');
        if (!value.startsWith("/")) value = "/" + value;
        return value;
    }

    private static boolean wildcardListMatch(String path, String rule) {
        String[] pathParts = path.toLowerCase(Locale.ROOT).split("/");
        String[] ruleParts = rule.toLowerCase(Locale.ROOT).split("/");
        if (pathParts.length != ruleParts.length) return false;
        for (int i = 0; i < pathParts.length; i++) {
            String rp = ruleParts[i];
            if (rp.contains("[*]")) {
                String prefix = rp.substring(0, rp.indexOf("[*]"));
                String suffix = rp.substring(rp.indexOf("[*]") + 3);
                String pp = pathParts[i];
                if (!pp.startsWith(prefix) || !pp.endsWith(suffix)) return false;
            } else if (!rp.equals(pathParts[i])) {
                return false;
            }
        }
        return true;
    }
}
