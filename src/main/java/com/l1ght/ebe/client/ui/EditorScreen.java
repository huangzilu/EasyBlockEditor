package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        ViewportFactory.saveCameraState();
        ViewportFactory.releaseViewportSession("editor-close");
        BlockPaletteUI.saveState();
        saveHistory();
        super.onClose();
    }

    @Override
    public void removed() {
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
        if (EditorUI.handleKeyPress(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
