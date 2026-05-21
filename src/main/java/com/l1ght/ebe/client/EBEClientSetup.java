package com.l1ght.ebe.client;

import com.l1ght.ebe.client.keybind.EBEKeyMappings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = "ebe", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class EBEClientSetup {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(EBEKeyMappings.TOOL_SELECT);
        event.register(EBEKeyMappings.TOOL_PLACE);
        event.register(EBEKeyMappings.TOOL_DELETE);
        event.register(EBEKeyMappings.TOOL_REPLACE);
        event.register(EBEKeyMappings.TOOL_GRAB);
        event.register(EBEKeyMappings.TOOL_MEASURE);
        event.register(EBEKeyMappings.TOOL_FILL);
        event.register(EBEKeyMappings.FREE_FLIGHT_FORWARD);
        event.register(EBEKeyMappings.FREE_FLIGHT_BACK);
        event.register(EBEKeyMappings.FREE_FLIGHT_LEFT);
        event.register(EBEKeyMappings.FREE_FLIGHT_RIGHT);
        event.register(EBEKeyMappings.FREE_FLIGHT_UP);
        event.register(EBEKeyMappings.FREE_FLIGHT_DOWN);
    }
}
