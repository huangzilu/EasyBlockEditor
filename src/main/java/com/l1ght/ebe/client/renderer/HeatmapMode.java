package com.l1ght.ebe.client.renderer;

public enum HeatmapMode {
    OFF,
    BY_TYPE,
    BY_RARITY,
    BY_HEIGHT,
    BY_FACING;

    private static final HeatmapMode[] VALUES = values();

    public static HeatmapMode fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : OFF;
    }

    public String getTranslationKey() {
        return "ebe.heatmap." + name().toLowerCase();
    }
}
