package com.l1ght.ebe.server.permission;

import java.util.Locale;

public enum PermissionFeature {
    EDITOR(true),
    PROJECTION(true),
    PRINTER(true),
    PLACE_ALL(false),
    COLLABORATE(true),
    FILE_LIBRARY(true),
    IMPORT(true),
    EXPORT(true);

    private final boolean defaultAllowed;

    PermissionFeature(boolean defaultAllowed) {
        this.defaultAllowed = defaultAllowed;
    }

    public boolean defaultAllowed() {
        return defaultAllowed;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PermissionFeature parse(String raw) {
        for (var feature : values()) {
            if (feature.id().equalsIgnoreCase(raw)) return feature;
        }
        throw new IllegalArgumentException("Unknown feature: " + raw);
    }
}
