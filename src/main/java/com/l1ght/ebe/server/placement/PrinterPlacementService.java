package com.l1ght.ebe.server.placement;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.nbt.NbtPathRules;
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
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        CompoundTag targetNbt = parseCleanTargetNbt(nbtStr);

        BlockState existing = level.getBlockState(pos);
        if (!existing.isAir()) {
            if (existingMatchesTarget(level, pos, existing, state, targetNbt)) {
                return Result.PLACED;
            }
            if (!(existing.getBlock() == net.minecraft.world.level.block.Blocks.WATER && PlacementStateOrder.isWaterlogged(state))) {
                return Result.BLOCKED;
            }
        }

        List<ItemRequirement> requirements = materialRequirements(state);

        boolean canPlace = player.isCreative();
        List<BlockPos> materialContainers = null;
        if (!canPlace) {
            int range = Math.max(0, materialSourceRange);
            if (requireHeldItem) {
                canPlace = hasHeldMaterials(player, level, requirements, targetNbt);
            } else if (materialSourcePos != null) {
                if (materialSourcePos.closerThan(player.blockPosition(), 128.0)
                        && (range <= 0 || isWithinBlockRange(pos, materialSourcePos, range))) {
                    materialContainers = findMaterialContainers(level, materialSourcePos, requirements, targetNbt);
                    canPlace = materialContainers != null;
                }
            } else {
                canPlace = hasInventoryMaterials(player, level, requirements, targetNbt);
            }
        }

        if (!canPlace) return Result.MISSING_MATERIAL;

        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return Result.FAILED;
        }

        if (!player.isCreative()) {
            if (requireHeldItem) {
                consumeHeldMaterials(player, level, requirements, targetNbt);
            } else if (materialContainers != null) {
                consumeFromContainers(level, materialContainers, requirements, targetNbt);
            } else {
                consumeFromInventory(player, level, requirements, targetNbt);
            }
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
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to apply printer block entity NBT at {}", pos, e);
        }
    }

    private static boolean hasHeldMaterials(ServerPlayer player, ServerLevel level, List<ItemRequirement> requirements, CompoundTag targetNbt) {
        if (requirements.isEmpty()) return false;
        ItemRequirement primary = requirements.getFirst();
        ItemStack stack = player.getMainHandItem();
        if (!stackMatches(level, stack, primary, targetNbt)) {
            return false;
        }
        if (requirements.size() == 1) {
            return true;
        }
        return hasInventoryMaterials(player, level, requirements.subList(1, requirements.size()), targetNbt);
    }

    private static void consumeHeldMaterials(ServerPlayer player, ServerLevel level, List<ItemRequirement> requirements, CompoundTag targetNbt) {
        if (requirements.isEmpty()) return;
        ItemRequirement primary = requirements.getFirst();
        if (stackMatches(level, player.getMainHandItem(), primary, targetNbt)) {
            player.getMainHandItem().shrink(1);
            returnEmptyBuckets(player, primary.emptyBucketsReturned());
        }
        if (requirements.size() > 1) {
            consumeFromInventory(player, level, requirements.subList(1, requirements.size()), targetNbt);
        }
    }

    private static boolean isWithinBlockRange(BlockPos pos, BlockPos center, int range) {
        double dx = pos.getX() - center.getX();
        double dy = pos.getY() - center.getY();
        double dz = pos.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz <= (double) range * range;
    }

    private static boolean hasInventoryMaterials(ServerPlayer player, ServerLevel level, List<ItemRequirement> requirements, CompoundTag targetNbt) {
        if (requirements.isEmpty()) return false;
        for (var requirement : requirements) {
            if (!hasInventoryMaterial(player, level, requirement, targetNbt)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasInventoryMaterial(ServerPlayer player, ServerLevel level, ItemRequirement requirement, CompoundTag targetNbt) {
        for (ItemStack stack : player.getInventory().items) {
            if (stackMatches(level, stack, requirement, targetNbt)) {
                return true;
            }
        }
        return false;
    }

    private static void consumeFromInventory(ServerPlayer player, ServerLevel level, List<ItemRequirement> requirements, CompoundTag targetNbt) {
        int bucketsToReturn = 0;
        for (var requirement : requirements) {
            if (consumeOneFromInventory(player, level, requirement, targetNbt)) {
                bucketsToReturn += requirement.emptyBucketsReturned();
            }
        }
        returnEmptyBuckets(player, bucketsToReturn);
    }

    private static boolean consumeOneFromInventory(ServerPlayer player, ServerLevel level, ItemRequirement requirement, CompoundTag targetNbt) {
        for (ItemStack stack : player.getInventory().items) {
            if (stackMatches(level, stack, requirement, targetNbt)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> findMaterialContainers(ServerLevel level, BlockPos sourcePos, List<ItemRequirement> requirements, CompoundTag targetNbt) {
        var positions = materialContainerPositions(level, sourcePos);
        if (hasContainerMaterials(level, positions, requirements, targetNbt)) {
            return positions;
        }
        return null;
    }

    private static List<BlockPos> materialContainerPositions(ServerLevel level, BlockPos sourcePos) {
        var positions = new ArrayList<BlockPos>(2);
        positions.add(sourcePos);
        var state = level.getBlockState(sourcePos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.hasProperty(ChestBlock.FACING)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.getValue(ChestBlock.FACING);
                Direction neighborDirection = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
                BlockPos neighbor = sourcePos.relative(neighborDirection);
                positions.add(neighbor);
            }
        }
        return positions;
    }

    private static boolean hasContainerMaterials(ServerLevel level, List<BlockPos> positions, List<ItemRequirement> requirements, CompoundTag targetNbt) {
        for (var requirement : requirements) {
            boolean found = false;
            for (var pos : positions) {
                if (level.getBlockEntity(pos) instanceof Container container
                        && hasContainerMaterial(level, container, requirement, targetNbt)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return !requirements.isEmpty();
    }

    private static boolean hasContainerMaterial(ServerLevel level, Container container, ItemRequirement requirement, CompoundTag targetNbt) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stackMatches(level, stack, requirement, targetNbt)) {
                return true;
            }
        }
        return false;
    }

    private static void consumeFromContainers(ServerLevel level, List<BlockPos> positions, List<ItemRequirement> requirements, CompoundTag targetNbt) {
        int bucketsToReturn = 0;
        for (var requirement : requirements) {
            if (consumeOneFromContainers(level, positions, requirement, targetNbt)) {
                bucketsToReturn += requirement.emptyBucketsReturned();
            }
        }
        if (!positions.isEmpty() && level.getBlockEntity(positions.getFirst()) instanceof Container container) {
            returnEmptyBuckets(level, positions.getFirst(), container, bucketsToReturn);
        }
    }

    private static boolean consumeOneFromContainers(ServerLevel level, List<BlockPos> positions, ItemRequirement requirement, CompoundTag targetNbt) {
        for (var pos : positions) {
            if (level.getBlockEntity(pos) instanceof Container container
                    && consumeOneFromContainer(level, container, requirement, targetNbt)) {
                container.setChanged();
                return true;
            }
        }
        return false;
    }

    private static boolean consumeOneFromContainer(ServerLevel level, Container container, ItemRequirement requirement, CompoundTag targetNbt) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stackMatches(level, stack, requirement, targetNbt)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean stackMatches(ServerLevel level, ItemStack stack, ItemRequirement requirement, CompoundTag targetNbt) {
        return !stack.isEmpty()
                && stack.is(requirement.item())
                && (!requirement.matchBlockEntityNbt() || nbtMatches(level, stack, targetNbt));
    }

    private static void returnEmptyBuckets(ServerPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
        }
    }

    private static void returnEmptyBuckets(ServerLevel level, BlockPos sourcePos, Container container, int count) {
        for (int i = 0; i < count; i++) {
            ItemStack bucket = new ItemStack(Items.BUCKET);
            for (int slot = 0; slot < container.getContainerSize() && !bucket.isEmpty(); slot++) {
                ItemStack existing = container.getItem(slot);
                if (existing.isEmpty()) {
                    container.setItem(slot, bucket);
                    bucket = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(existing, bucket) && existing.getCount() < existing.getMaxStackSize()) {
                    int move = Math.min(bucket.getCount(), existing.getMaxStackSize() - existing.getCount());
                    existing.grow(move);
                    bucket.shrink(move);
                }
            }
            if (!bucket.isEmpty()) {
                Containers.dropItemStack(level, sourcePos.getX() + 0.5D, sourcePos.getY() + 0.5D, sourcePos.getZ() + 0.5D, bucket);
            }
        }
    }

    private static List<ItemRequirement> materialRequirements(BlockState state) {
        var requirements = new ArrayList<ItemRequirement>(2);
        if (state == null || state.isAir()) return requirements;

        if (state.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
            requirements.add(new ItemRequirement(Items.WATER_BUCKET, false, 1));
            return requirements;
        }
        if (state.getBlock() == net.minecraft.world.level.block.Blocks.LAVA) {
            requirements.add(new ItemRequirement(Items.LAVA_BUCKET, false, 1));
            return requirements;
        }
        if (state.getBlock() == net.minecraft.world.level.block.Blocks.POWDER_SNOW) {
            requirements.add(new ItemRequirement(Items.POWDER_SNOW_BUCKET, false, 1));
            return requirements;
        }

        Item blockItem = state.getBlock().asItem();
        if (blockItem != Items.AIR) {
            requirements.add(new ItemRequirement(blockItem, true, 0));
        }
        if (PlacementStateOrder.isWaterlogged(state)) {
            requirements.add(new ItemRequirement(Items.WATER_BUCKET, false, 1));
        }
        return requirements;
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
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to parse target printer NBT", e);
            return null;
        }
    }

    private static boolean nbtMatches(ServerLevel level, ItemStack stack, CompoundTag targetNbt) {
        if (!ServerSettingsManager.get().strictNbtMatching) return true;
        CompoundTag cleanedTarget = targetNbt == null ? new CompoundTag() : filteredPlacementNbt(targetNbt);
        CompoundTag stackBlockEntityTag = extractStackBlockEntityTag(level, stack);
        if (cleanedTarget.isEmpty()) {
            return stackBlockEntityTag == null || filteredPlacementNbt(stackBlockEntityTag).isEmpty();
        }
        return stackBlockEntityTag != null && filteredPlacementNbt(stackBlockEntityTag).equals(cleanedTarget);
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

    private static CompoundTag filteredPlacementNbt(CompoundTag tag) {
        return NbtPathRules.filteredCopy(cleanPlacementNbt(tag), ServerSettingsManager.nbtIgnoreRules());
    }

    private static boolean existingMatchesTarget(ServerLevel level, BlockPos pos, BlockState existing,
                                                 BlockState target, CompoundTag targetNbt) {
        if (!existing.equals(target)) return false;
        if (!ServerSettingsManager.get().strictNbtMatching) return true;
        CompoundTag cleanedTarget = targetNbt == null ? new CompoundTag() : filteredPlacementNbt(targetNbt);
        var be = level.getBlockEntity(pos);
        CompoundTag existingNbt = null;
        if (be != null) {
            try {
                existingNbt = be.saveWithId(level.registryAccess());
            } catch (Exception e) {
                EBEMod.LOGGER.warn("Failed to read existing block entity NBT at {}", pos, e);
                existingNbt = null;
            }
        }
        if (cleanedTarget.isEmpty()) {
            return existingNbt == null || filteredPlacementNbt(existingNbt).isEmpty();
        }
        return existingNbt != null && filteredPlacementNbt(existingNbt).equals(cleanedTarget);
    }

    private static boolean consumePrinterBudget(ServerPlayer player, ServerLevel level) {
        long tick = level.getGameTime();
        int limit = ServerPlacementBudget.scaledInt(ServerSettingsManager.get().printerBlocksPerTick, 1);
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

    private record ItemRequirement(Item item, boolean matchBlockEntityNbt, int emptyBucketsReturned) {
    }
}
