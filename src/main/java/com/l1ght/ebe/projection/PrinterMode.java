package com.l1ght.ebe.projection;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.network.PrinterPlacePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public enum PrinterMode {
    OFF,
    MANUAL,
    AUTO;

    private static final PrinterMode[] VALUES = values();
    public static PrinterMode fromOrdinal(int o) { return o >= 0 && o < VALUES.length ? VALUES[o] : OFF; }
    public String getTranslationKey() { return "ebe.printer.mode." + name().toLowerCase(); }
}
