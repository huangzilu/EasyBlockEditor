package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.editor.selection.DisplayFilter;
import net.minecraft.world.level.block.state.BlockState;

public class EditorState {
    private EditorTool activeTool = EditorTool.SELECT;
    private EditorMode mode = EditorMode.VIEW;
    private String cursorPosition = "0, 0, 0";
    private int cursorX = 0;
    private int cursorY = 0;
    private int cursorZ = 0;
    private int selectedCount = 0;
    private String selectedBlock = "";
    private BlockState activeBlockState;
    private BlockState inspectedBlockState;
    private int fps = 0;
    private final DisplayFilter displayFilter = new DisplayFilter();

    public EditorTool getActiveTool() { return activeTool; }
    public void setActiveTool(EditorTool tool) { this.activeTool = tool; }

    public EditorMode getMode() { return mode; }
    public void setMode(EditorMode mode) { this.mode = mode; }

    public String getCursorPosition() { return cursorPosition; }
    public void setCursorPosition(String pos) { this.cursorPosition = pos; }

    public int getCursorX() { return cursorX; }
    public void setCursorX(int x) { this.cursorX = x; }
    public int getCursorY() { return cursorY; }
    public void setCursorY(int y) { this.cursorY = y; }
    public int getCursorZ() { return cursorZ; }
    public void setCursorZ(int z) { this.cursorZ = z; }

    public int getSelectedCount() { return selectedCount; }
    public void setSelectedCount(int count) { this.selectedCount = count; }

    public String getSelectedBlock() { return selectedBlock; }
    public void setSelectedBlock(String block) { this.selectedBlock = block; }

    public BlockState getActiveBlockState() { return activeBlockState; }
    public void setActiveBlockState(BlockState state) { this.activeBlockState = state; }

    public BlockState getInspectedBlockState() { return inspectedBlockState; }
    public void setInspectedBlockState(BlockState state) { this.inspectedBlockState = state; }

    public int getFps() { return fps; }
    public void setFps(int fps) { this.fps = fps; }

    public DisplayFilter getDisplayFilter() { return displayFilter; }

    public String buildStatusText() {
        var sb = new StringBuilder();
        sb.append(net.minecraft.network.chat.Component.translatable(mode.getKey()).getString());
        sb.append(" | ")
                .append(net.minecraft.network.chat.Component.translatable("ebe.status.tool").getString())
                .append(": ")
                .append(net.minecraft.network.chat.Component.translatable(activeTool.getTranslationKey()).getString());
        sb.append(" | ")
                .append(net.minecraft.network.chat.Component.translatable("ebe.status.position").getString())
                .append(": ")
                .append(cursorPosition);
        if (!selectedBlock.isEmpty()) {
            sb.append(" | ").append(selectedBlock);
        }
        if (activeBlockState != null) {
            var name = activeBlockState.getBlock().getName().getString();
            sb.append(" | ")
                    .append(net.minecraft.network.chat.Component.translatable("ebe.status.material").getString())
                    .append(": ")
                    .append(name);
        }
        if (selectedCount > 0) {
            sb.append(" | ")
                    .append(net.minecraft.network.chat.Component.translatable("ebe.status.selected").getString())
                    .append(": ")
                    .append(selectedCount);
        }
        sb.append(" | ")
                .append(net.minecraft.network.chat.Component.translatable("ebe.status.fps").getString())
                .append(": ")
                .append(fps);
        return sb.toString();
    }
}
