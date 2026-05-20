package com.l1ght.ebe.command;

import com.l1ght.ebe.client.ui.EditorScreen;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class EBEClientCommands {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRegisterClientCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("ebe")
                        .then(net.minecraft.commands.Commands.literal("open")
                                .executes(ctx -> {
                                    Minecraft.getInstance().execute(() ->
                                            Minecraft.getInstance().setScreen(new EditorScreen()));
                                    return 1;
                                }))
        );
    }
}
