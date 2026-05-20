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
    public boolean isPauseScreen() {
        return false;
    }
}
