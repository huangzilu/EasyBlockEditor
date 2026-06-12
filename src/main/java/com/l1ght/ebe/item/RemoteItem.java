package com.l1ght.ebe.item;

import com.l1ght.ebe.clientbridge.EBEClientBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RemoteItem extends Item {
    public RemoteItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        // Litematica-style: the remote is live whenever it's held, so there is no mode to toggle.
        // Holding Alt + scroll / arrows / R / M drives the projection (see ProjectionController).
        return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        EBEClientBridge.appendRemoteTooltip(stack, tooltipFlag, tooltipComponents);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("ebe.item.remote");
    }
}
