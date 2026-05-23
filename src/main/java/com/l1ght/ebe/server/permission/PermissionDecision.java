package com.l1ght.ebe.server.permission;

public enum PermissionDecision {
    DEFAULT,
    ALLOW,
    DENY;

    public static PermissionDecision parse(String raw) {
        for (var decision : values()) {
            if (decision.name().equalsIgnoreCase(raw)) return decision;
        }
        throw new IllegalArgumentException("Unknown decision: " + raw);
    }
}
