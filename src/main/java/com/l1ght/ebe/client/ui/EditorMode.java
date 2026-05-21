package com.l1ght.ebe.client.ui;

public enum EditorMode {
    VIEW("ebe.mode.view"),
    EDIT("ebe.mode.edit");

    private final String key;

    EditorMode(String key) { this.key = key; }

    public String getKey() { return key; }
}
