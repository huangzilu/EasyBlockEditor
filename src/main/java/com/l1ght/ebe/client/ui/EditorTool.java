package com.l1ght.ebe.client.ui;

public enum EditorTool {
    SELECT("ebe.editor.tool.select"),
    PLACE("ebe.editor.tool.place"),
    DELETE("ebe.editor.tool.delete"),
    REPLACE("ebe.editor.tool.replace"),
    GRAB("ebe.editor.tool.grab"),
    MEASURE("ebe.editor.tool.measure"),
    FILL("ebe.editor.tool.fill");

    private final String translationKey;

    EditorTool(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }
}
