package com.l1ght.ebe.client.event;

import com.l1ght.ebe.client.projection.PrinterController;
import com.l1ght.ebe.client.projection.ProjectionController;
import com.l1ght.ebe.client.projection.ProjectionManager;
import com.l1ght.ebe.projection.PrinterMode;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "ebe", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            com.l1ght.ebe.client.projection.ProjectionRenderer.renderProjection(event.getPoseStack(), event.getProjectionMatrix(), event.getFrustum());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ProjectionManager.loadPersistentStateIfNeeded();
        ProjectionManager.tickPlaceAllUploads();
        com.l1ght.ebe.client.ui.EditorUI.pollFileTreeRefresh();
        PrinterController.tick();
        if (ProjectionController.isControlMode()) {
            ProjectionController.tick();
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (PrinterController.isActive() && PrinterController.getMode() == PrinterMode.MANUAL) {
            if (PrinterController.tryManualPlace()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (PrinterController.isSelectingMaterialSource() && event.getLevel().isClientSide()) {
            if (PrinterController.bindMaterialSource(event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == GLFW.GLFW_PRESS
                && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && PrinterController.isActive()
                && PrinterController.getMode() == PrinterMode.MANUAL
                && PrinterController.tryManualPlace()) {
            event.setCanceled(true);
            return;
        }
        if (ProjectionController.isControlMode() && event.getAction() == 1) {
            if (ProjectionController.handleMouseClick(event.getButton(), event.getModifiers())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (ProjectionController.handleMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }
}
