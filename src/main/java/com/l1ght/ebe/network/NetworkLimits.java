package com.l1ght.ebe.network;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class NetworkLimits {
    public static final int MAX_JSON_CHARS = 32_767;
    public static final int MAX_ACTION_CHARS = 64;
    public static final int MAX_SHORT_TEXT_CHARS = 255;
    public static final int MAX_MEDIUM_TEXT_CHARS = 4_096;
    public static final int MAX_BLOCK_NBT_CHARS = 16_384;
    public static final int MAX_BATCH_NBT_CHARS = 128_000;
    public static final int MAX_PLACE_BLOCKS_PER_PACKET = 4_096;
    public static final int MAX_PLACE_BLOCKS = 262_144;
    public static final int MAX_ENTITY_NBT_CHARS = 32_768;
    public static final int MAX_PLACE_ENTITIES_PER_PACKET = 64;
    public static final int MAX_PLACE_ENTITIES = 4_096;
    public static final int MAX_PRINTER_PLACE_BATCH = 64;
    public static final int MAX_WORKGROUP_UPLOAD_BATCH = 768;
    public static final int MAX_WORKGROUP_UPLOAD_TOTAL = 262_144;
    public static final int MAX_LIBRARY_CHUNK_BYTES = 24 * 1024;
    public static final int MAX_LIBRARY_UPLOAD_BYTES = 16 * 1024 * 1024;

    private NetworkLimits() {
    }

    public static String bounded(String value, int maxChars) {
        if (value == null) return "";
        if (value.length() <= maxChars) return value;
        return value.substring(0, maxChars);
    }

    public static boolean validNbt(String nbt) {
        return nbt == null || nbt.length() <= MAX_BLOCK_NBT_CHARS;
    }

    public static boolean validTotalNbt(List<String> nbtValues) {
        long total = 0L;
        for (String nbt : nbtValues) {
            if (nbt == null || nbt.isEmpty()) continue;
            if (nbt.length() > MAX_BLOCK_NBT_CHARS) return false;
            total += nbt.getBytes(StandardCharsets.UTF_8).length;
            if (total > MAX_BATCH_NBT_CHARS) return false;
        }
        return true;
    }
}
