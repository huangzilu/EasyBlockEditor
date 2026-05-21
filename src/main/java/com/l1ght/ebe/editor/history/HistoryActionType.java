package com.l1ght.ebe.editor.history;

public enum HistoryActionType {
    PLACE("ebe.history.place"),
    DELETE("ebe.history.delete"),
    REPLACE("ebe.history.replace"),
    PASTE("ebe.history.paste"),
    CUT("ebe.history.cut"),
    FILL("ebe.history.fill"),
    ROTATE("ebe.history.rotate"),
    MIRROR("ebe.history.mirror"),
    TRANSLATE("ebe.history.translate");

    private final String key;

    HistoryActionType(String key) { this.key = key; }

    public String getKey() { return key; }
}
