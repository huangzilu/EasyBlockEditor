package com.l1ght.ebe.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class EBECommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ebe")
                .executes(context -> {
                    context.getSource().sendSuccess(
                            () -> Component.translatable("ebe.command.ebe"), false);
                    return 1;
                })
        );
    }
}
