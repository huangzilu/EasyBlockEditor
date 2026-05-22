package com.l1ght.ebe.client.event;

import com.l1ght.ebe.projection.PrinterController;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = "ebe", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            com.l1ght.ebe.projection.ProjectionRenderer.renderProjection(event.getPoseStack(), event.getProjectionMatrix());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        PrinterController.tick();
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (PrinterController.isActive() && PrinterController.getMode() == com.l1ght.ebe.projection.PrinterMode.MANUAL) {
            PrinterController.tryManualPlace();
        }
    }
}
