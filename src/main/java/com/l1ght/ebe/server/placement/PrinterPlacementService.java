package com.l1ght.ebe.server.placement;

import com.l1ght.ebe.server.ServerSettingsManager;
import com.l1ght.ebe.server.permission.PermissionFeature;
import com.l1ght.ebe.server.permission.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PrinterPlacementService {
    private static final Map<UUID, TickCounter> PRINTER_RATE = new HashMap<>();

    private PrinterPlacementService() {
    }

    public static Result place(ServerPlayer player, ServerLevel level, BlockPos pos, int stateId, String nbtStr,
                               BlockPos materialSourcePos, boolean requireHeldItem, int materialSourceRange) {
        if (!PermissionManager.canUse(player, PermissionFeature.PRINTER)) return Result.NO_PERMISSION;
        if (!consumePrinterBudget(player, level)) return Result.RATE_LIMITED;

        BlockState state = Block.stateById(stateId);
        if (state == null || state.isAir()) return Result.INVALID_STATE;

        BlockState existing = level.getBlockState(pos);
        if (!existing.isAir()) {
            return existing.getBlock() == state.getBlock() ? Result.PLACED : Result.BLOCKED;
        }

        var block = state.getBlock();
        var requiredItem = block.asItem();
        if (requiredItem == null) return Result.INVALID_STATE;

        boolean canPlace = player.isCreative();
        if (!canPlace) {
            CompoundTag targetNbt = parseCleanTargetNbt(nbtStr);
            int range = Math.max(0, materialSourceRange);
            if (requireHeldItem) {
                canPlace = consumeFromHeldItem(player, level, requiredItem, targetNbt);
            } else if (materialSourcePos != null) {
                canPlace = materialSourcePos.closerThan(player.blockPosition(), 128.0)
                        && (range <= 0 || isWithinBlockRange(pos, materialSourcePos, range))
                        && consumeFromMaterialSource(level, materialSourcePos, requiredItem, targetNbt);
            } else {
                canPlace = consumeFromInventory(player, level, requiredItem, targetNbt);
            }
        }

        if (!canPlace) return Result.MISSING_MATERIAL;

        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return Result.FAILED;
        }

        applyBlockEntityNbt(level, pos, state, nbtStr);
        return Result.PLACED;
    }

    private static void applyBlockEntityNbt(ServerLevel level, BlockPos pos, BlockState state, String nbtStr) {
        if (nbtStr == null || nbtStr.isEmpty()) return;
        try {
            var beNbt = TagParser.parseTag(nbtStr);
            beNbt.remove("x");
            beNbt.remove("y");
            beNbt.remove("z");
            beNbt.remove("id");
            var be = level.getBlockEntity(pos);
            if (be != null) {
                beNbt.putInt("x", pos.getX());
                beNbt.putInt("y", pos.getY());
                beNbt.putInt("z", pos.getZ());
                be.loadWithComponents(beNbt, level.registryAccess());
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean consumeFromHeldItem(ServerPlayer player, ServerLevel level, net.minecraft.world.item.Item requiredItem, CompoundTag targetNbt) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.isEmpty() && stack.is(requiredItem) && nbtMatches(level, stack, targetNbt)) {
            stack.shrink(1);
            return true;
        }
        return false;
    }

    private static boolean isWithinBlockRange(BlockPos pos, BlockPos center, int range) {
        double dx = pos.getX() - center.getX();
        double dy = pos.getY() - center.getY();
        double dz = pos.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz <= (double) range * range;
    }

    private static boolean consumeFromInventory(ServerPlayer player, ServerLevel level, net.minecraft.world.item.Item requiredItem, CompoundTag targetNbt) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(requiredItem) && nbtMatches(level, stack, targetNbt)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean consumeFromMaterialSource(ServerLevel level, BlockPos sourcePos, net.minecraft.world.item.Item requiredItem, CompoundTag targetNbt) {
        if (consumeFromContainer(level, sourcePos, requiredItem, targetNbt)) {
            return true;
        }

        var state = level.getBlockState(sourcePos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.hasProperty(ChestBlock.FACING)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.getValue(ChestBlock.FACING);
                Direction neighborDirection = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
                return consumeFromContainer(level, sourcePos.relative(neighborDirection), requiredItem, targetNbt);
            }
        }
        return false;
    }

    private static boolean consumeFromContainer(ServerLevel level, BlockPos pos, net.minecraft.world.item.Item requiredItem, CompoundTag targetNbt) {
        if (!(level.getBlockEntity(pos) instanceof Container container)) {
            return false;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.is(requiredItem) && nbtMatches(level, stack, targetNbt)) {
                stack.shrink(1);
                container.setChanged();
                return true;
            }
        }
        return false;
    }

    private static CompoundTag parseCleanTargetNbt(String nbtStr) {
        if (nbtStr == null || nbtStr.isEmpty()) {
            return null;
        }
        try {
            var targetNbt = TagParser.parseTag(nbtStr);
            targetNbt.remove("x");
            targetNbt.remove("y");
            targetNbt.remove("z");
            targetNbt.remove("id");
            return targetNbt;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean nbtMatches(ServerLevel level, ItemStack stack, CompoundTag targetNbt) {
        CompoundTag cleanedTarget = targetNbt == null ? new CompoundTag() : cleanPlacementNbt(targetNbt);
        CompoundTag stackBlockEntityTag = extractStackBlockEntityTag(level, stack);
        if (cleanedTarget.isEmpty()) {
            return stackBlockEntityTag == null || cleanPlacementNbt(stackBlockEntityTag).isEmpty();
        }
        return stackBlockEntityTag != null && cleanPlacementNbt(stackBlockEntityTag).equals(cleanedTarget);
    }

    private static CompoundTag extractStackBlockEntityTag(ServerLevel level, ItemStack stack) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            return data.copyTag();
        }
        Tag saved = stack.saveOptional(level.registryAccess());
        if (saved instanceof CompoundTag ct && ct.contains("BlockEntityTag")) {
            return ct.getCompound("BlockEntityTag").copy();
        }
        return null;
    }

    private static CompoundTag cleanPlacementNbt(CompoundTag tag) {
        CompoundTag cleaned = tag.copy();
        cleaned.remove("x");
        cleaned.remove("y");
        cleaned.remove("z");
        cleaned.remove("id");
        return cleaned;
    }

    private static boolean consumePrinterBudget(ServerPlayer player, ServerLevel level) {
        long tick = level.getGameTime();
        int limit = ServerSettingsManager.get().printerBlocksPerTick;
        TickCounter counter = PRINTER_RATE.computeIfAbsent(player.getUUID(), ignored -> new TickCounter(tick));
        if (counter.tick != tick) {
            counter.tick = tick;
            counter.count = 0;
        }
        if (counter.count >= limit) return false;
        counter.count++;
        return true;
    }

    public enum Result {
        PLACED,
        NO_PERMISSION,
        RATE_LIMITED,
        INVALID_STATE,
        BLOCKED,
        MISSING_MATERIAL,
        FAILED
    }

    private static class TickCounter {
        long tick;
        int count;

        TickCounter(long tick) {
            this.tick = tick;
        }
    }
}
