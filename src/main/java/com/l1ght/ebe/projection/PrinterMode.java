package com.l1ght.ebe.projection;

public enum PrinterMode {
    OFF,
    MANUAL,
    AUTO;

    private static final PrinterMode[] VALUES = values();
    public static PrinterMode fromOrdinal(int o) { return o >= 0 && o < VALUES.length ? VALUES[o] : OFF; }
    public String getTranslationKey() { return "ebe.printer.mode." + name().toLowerCase(); }
}
