package com.l1ght.ebe.item;

import com.l1ght.ebe.client.keybind.EBEKeyBindings;
import com.l1ght.ebe.client.projection.ProjectionController;

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
        if (level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                ProjectionController.toggleControlMode();
                return InteractionResultHolder.sidedSuccess(player.getItemInHand(usedHand), true);
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        String fwd = EBEKeyBindings.REMOTE_FORWARD.getDisplayName();
        String back = EBEKeyBindings.REMOTE_BACK.getDisplayName();
        String left = EBEKeyBindings.REMOTE_LEFT.getDisplayName();
        String right = EBEKeyBindings.REMOTE_RIGHT.getDisplayName();
        String ccw = EBEKeyBindings.REMOTE_ROTATE_CCW.getDisplayName();
        String cw = EBEKeyBindings.REMOTE_ROTATE_CW.getDisplayName();

        tooltipComponents.add(Component.translatable("ebe.item.remote.desc.toggle").withStyle(s -> s.withColor(0xAAAAAA)));
        tooltipComponents.add(Component.translatable("ebe.item.remote.desc.move", fwd, back, left, right).withStyle(s -> s.withColor(0xAAAAAA)));
        tooltipComponents.add(Component.translatable("ebe.item.remote.desc.vertical").withStyle(s -> s.withColor(0xAAAAAA)));
        tooltipComponents.add(Component.translatable("ebe.item.remote.desc.rotate", ccw, cw).withStyle(s -> s.withColor(0xAAAAAA)));
        tooltipComponents.add(Component.translatable("ebe.item.remote.desc.fast").withStyle(s -> s.withColor(0x888888)));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("ebe.item.remote");
    }
}
