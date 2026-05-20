package com.l1ght.ebe.client.ui;

public class EditorState {
    private EditorTool activeTool = EditorTool.SELECT;
    private String cursorPosition = "0, 0, 0";
    private int selectedCount = 0;
    private String selectedBlock = "";
    private int fps = 0;

    public EditorTool getActiveTool() { return activeTool; }
    public void setActiveTool(EditorTool tool) { this.activeTool = tool; }

    public String getCursorPosition() { return cursorPosition; }
    public void setCursorPosition(String pos) { this.cursorPosition = pos; }

    public int getSelectedCount() { return selectedCount; }
    public void setSelectedCount(int count) { this.selectedCount = count; }

    public String getSelectedBlock() { return selectedBlock; }
    public void setSelectedBlock(String block) { this.selectedBlock = block; }

    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }

    public String buildStatusText() {
        var sb = new StringBuilder();
        sb.append(cursorPosition);
        if (!selectedBlock.isEmpty()) {
            sb.append(" | ").append(selectedBlock);
        }
        if (selectedCount > 0) {
            sb.append(" | x").append(selectedCount);
        }
        sb.append(" | ").append(fps).append(" FPS");
        return sb.toString();
    }
}
