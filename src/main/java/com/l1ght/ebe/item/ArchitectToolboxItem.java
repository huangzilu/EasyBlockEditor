package com.l1ght.ebe.item;

import com.l1ght.ebe.EBEMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ArchitectToolboxItem extends Item {
    public ArchitectToolboxItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), false);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("ebe.item.architect_toolbox");
    }
}
