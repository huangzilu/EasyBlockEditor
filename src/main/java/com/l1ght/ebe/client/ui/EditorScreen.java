package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.KeyRecordingManager;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

public class EditorScreen extends Screen {
    private final ModularUI modularUI;
    private long fpsWindowStart = System.nanoTime();
    private int fpsFrames = 0;
    private boolean closeHandled = false;

    public EditorScreen() {
        super(Component.translatable("ebe.screen.editor"));
        this.modularUI = EditorUI.createModularUI();
    }

    @Override
    protected void init() {
        super.init();
        modularUI.setScreenAndInit(this);
        this.addRenderableWidget(modularUI.getWidget());
    }

    @Override
    public void onClose() {
        handleCloseOnce("editor-close");
        super.onClose();
    }

    @Override
    public void removed() {
        handleCloseOnce("editor-removed");
        super.removed();
    }

    private void handleCloseOnce(String stage) {
        if (closeHandled) {
            return;
        }
        closeHandled = true;
        ViewportFlightController.disable();
        EditorUI.resetMouseCursor();
        ViewportFactory.saveCameraState();
        ViewportFactory.releaseViewportSession(stage);
        BlockPaletteUI.saveState();
        saveHistory();
    }

    private void saveHistory() {
        var session = EditorUI.getSession();
        if (session != null && session.getCurrentFile() != null) {
            session.saveHistory();
        }
    }

    @Override
    public void tick() {
        super.tick();
        ViewportFactory.tickCamera();
        EditorUI.updateStatusBar();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (ViewportFlightController.isFlying()) {
            int[] c = ViewportFactory.getViewportCenterScreen();
            if (c != null) {
                int cx = c[0], cy = c[1];
                int color = 0xFFFFFFFF;
                guiGraphics.fill(cx - 5, cy - 1, cx + 5, cy + 1, color);
                guiGraphics.fill(cx - 1, cy - 5, cx + 1, cy + 5, color);
            }
        }
        fpsFrames++;
        long now = System.nanoTime();
        long elapsed = now - fpsWindowStart;
        if (elapsed >= 1_000_000_000L) {
            EditorUI.setMeasuredFps(Math.max(1, Math.round(fpsFrames * 1_000_000_000f / elapsed)));
            fpsFrames = 0;
            fpsWindowStart = now;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyRecordingManager.isRecording()) {
            KeyRecordingManager.onKeyPress(keyCode, scanCode, modifiers);
            return true;
        }
        if (EditorUI.handleKeyPress(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (KeyRecordingManager.isRecording()) {
            KeyRecordingManager.onKeyRelease(keyCode);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (KeyRecordingManager.isRecording()) {
            KeyRecordingManager.onMousePress(button, getActiveModifiers());
            return true;
        }
        if (ViewportFlightController.isFlying()) {
            if (button == 0) { ViewportFactory.executeCrosshairAction(false); return true; }
            if (button == 1) { ViewportFactory.executeCrosshairAction(true); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (KeyRecordingManager.isRecording()) {
            KeyRecordingManager.onMouseRelease(button);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (BoxSelectionController.isActive() && BoxSelectionController.handleScroll(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        EditorUI.importDroppedFiles(files);
    }

    private int getActiveModifiers() {
        long window = minecraft.getWindow().getWindow();
        int mods = 0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
            mods |= GLFW.GLFW_MOD_CONTROL;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
            mods |= GLFW.GLFW_MOD_SHIFT;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) {
            mods |= GLFW.GLFW_MOD_ALT;
        }
        return mods;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
