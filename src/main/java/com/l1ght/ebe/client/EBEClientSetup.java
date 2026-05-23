package com.l1ght.ebe.client;

import com.l1ght.ebe.config.EBEClientConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "ebe", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EBEClientSetup {

    @SubscribeEvent
    public static void onClientStarted(FMLClientSetupEvent event) {
        EBEClientConfig.loadKeybindings();
    }
}
