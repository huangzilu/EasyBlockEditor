package com.l1ght.ebe.command;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class EBEClientCommands {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRegisterClientCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("ebe")
                        .then(net.minecraft.commands.Commands.literal("open")
                                .executes(ctx -> {
                                    if (FMLEnvironment.dist == Dist.CLIENT) {
                                        com.l1ght.ebe.client.ClientOnlyHooks.openEditorScreen();
                                    }
                                    return 1;
                                }))
        );
    }
}
