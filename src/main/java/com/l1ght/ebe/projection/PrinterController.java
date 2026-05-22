package com.l1ght.ebe.projection;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.config.EBEServerConfig;
import com.l1ght.ebe.network.PrinterPlacePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public class PrinterController {

    private static PrinterMode mode = PrinterMode.OFF;
    private static boolean active = false;
    private static int tickCounter = 0;

    public static void setMode(PrinterMode m) { mode = m; active = false; }
    public static PrinterMode getMode() { return mode; }
    public static boolean isActive() { return mode != PrinterMode.OFF && active; }
    public static void setActive(boolean a) { active = a; }
    public static void toggle() { if (mode != PrinterMode.OFF) active = !active; }

    public static void tick() {
        if (!active || mode == PrinterMode.OFF) return;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return;

        var projection = ProjectionManager.getProjection();
        if (projection == null) return;

        tickCounter++;

        if (mode == PrinterMode.AUTO) {
            int blocksPerTick = EBEServerConfig.printerBlocksPerTick.get();
            int range = EBEClientConfig.printerRange.get();
            BlockPos playerPos = player.blockPosition();

            int placed = 0;
            for (var pb : projection.getBlocks()) {
                if (placed >= blocksPerTick) break;

                BlockPos pos = pb.pos();
                if (pos.distManhattan(playerPos) > range) continue;

                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) continue;

                if (tryPlaceBlock(player, pos, pb.state(), pb.nbt())) {
                    placed++;
                }
            }
        }
    }

    public static boolean tryPlaceBlock(LocalPlayer player, BlockPos pos, BlockState targetState, CompoundTag targetNbt) {
        var level = player.level();
        var existing = level.getBlockState(pos);
        if (!existing.isAir()) return false;

        var block = targetState.getBlock();
        var requiredItem = block.asItem();
        if (requiredItem == null) return false;

        ItemStack foundStack = null;
        int slotIdx = -1;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            var stack = player.getInventory().items.get(i);
            if (stack.is(requiredItem)) {
                if (targetNbt != null && !targetNbt.isEmpty()) {
                    var mc = Minecraft.getInstance();
                    var stackTag = stack.saveOptional(mc.level.registryAccess());
                    if (!(stackTag instanceof CompoundTag ct) || ct.isEmpty()) continue;
                    if (!nbtMatchesForPlacement(ct, targetNbt)) continue;
                }
                foundStack = stack;
                slotIdx = i;
                break;
            }
        }

        if (foundStack == null) return false;

        int stateId = Block.getId(targetState);
        String nbtStr = targetNbt != null ? targetNbt.toString() : "";
        PacketDistributor.sendToServer(new PrinterPlacePayload(pos, stateId, nbtStr));

        if (mode == PrinterMode.MANUAL) {
            player.getInventory().items.set(slotIdx, foundStack.copy());
        }
        foundStack.shrink(1);
        return true;
    }

    private static boolean nbtMatchesForPlacement(CompoundTag stackTag, CompoundTag targetNbt) {
        var cleanedTarget = targetNbt.copy();
        cleanedTarget.remove("x");
        cleanedTarget.remove("y");
        cleanedTarget.remove("z");
        cleanedTarget.remove("id");

        var cleanedStack = stackTag.copy();
        cleanedStack.remove("x");
        cleanedStack.remove("y");
        cleanedStack.remove("z");
        cleanedStack.remove("id");

        if (cleanedTarget.isEmpty()) return true;

        if (cleanedStack.contains("BlockEntityTag")) {
            var beTag = cleanedStack.getCompound("BlockEntityTag");
            return beTag.equals(cleanedTarget);
        }

        return cleanedStack.equals(cleanedTarget);
    }

    public static boolean tryManualPlace() {
        if (mode != PrinterMode.MANUAL || !active) return false;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return false;

        var projection = ProjectionManager.getProjection();
        if (projection == null) return false;

        var hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHit)) return false;

        BlockPos adjacentPos = blockHit.getBlockPos().relative(blockHit.getDirection());
        BlockState existing = player.level().getBlockState(adjacentPos);
        if (!existing.isAir()) return false;

        for (var pb : projection.getBlocks()) {
            if (pb.pos().equals(adjacentPos)) {
                return tryPlaceBlock(player, adjacentPos, pb.state(), pb.nbt());
            }
        }

        return false;
    }
}
