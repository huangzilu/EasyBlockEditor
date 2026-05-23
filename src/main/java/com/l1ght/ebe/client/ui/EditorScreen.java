package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.KeyRecordingManager;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class EditorScreen extends Screen {
    private final ModularUI modularUI;

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
        EditorUI.resetMouseCursor();
        ViewportFactory.saveCameraState();
        ViewportFactory.releaseViewportSession("editor-close");
        BlockPaletteUI.saveState();
        saveHistory();
        super.onClose();
    }

    @Override
    public void removed() {
        EditorUI.resetMouseCursor();
        ViewportFactory.saveCameraState();
        ViewportFactory.releaseViewportSession("editor-removed");
        BlockPaletteUI.saveState();
        saveHistory();
        super.removed();
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
