package com.l1ght.ebe.client.projection;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.network.PrinterPlacePayload;
import com.l1ght.ebe.projection.PrinterMode;
import com.l1ght.ebe.projection.ProjectionData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PrinterController {

    private static PrinterMode mode = PrinterMode.OFF;
    private static boolean active = false;
    private static int tickCounter = 0;
    private static final int PENDING_TIMEOUT_TICKS = 40;
    private static final int AUTO_SCAN_INTERVAL_TICKS = 2;
    private static final int MAX_CANDIDATE_SCAN_PER_TICK = 512;
    private static final Map<BlockPos, Integer> pendingPlacements = new HashMap<>();
    private static final Map<Long, List<ProjectionData.ProjectionBlock>> chunkBuckets = new HashMap<>();
    private static ProjectionData bucketProjection;
    private static int bucketVersion = -1;
    private static int candidateCursor = 0;
    private static BlockPos materialSourcePos;
    private static boolean selectingMaterialSource = false;

    public static void setMode(PrinterMode m) { mode = m; active = false; }
    public static PrinterMode getMode() { return mode; }
    public static boolean isActive() { return mode != PrinterMode.OFF && active; }
    public static void setActive(boolean a) { active = a; }
    public static void toggle() { if (mode != PrinterMode.OFF) active = !active; }
    public static BlockPos getMaterialSourcePos() { return materialSourcePos; }
    public static boolean isSelectingMaterialSource() { return selectingMaterialSource; }
    public static void clearMaterialSource() { materialSourcePos = null; selectingMaterialSource = false; }

    public static void requestMaterialSourceSelection() {
        selectingMaterialSource = true;
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("ebe.printer.select_chest_hint"), true);
        }
        mc.setScreen(null);
    }

    public static boolean bindMaterialSource(BlockPos pos) {
        if (!selectingMaterialSource) return false;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof Container)) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("ebe.printer.select_chest_invalid"), true);
            }
            return true;
        }
        selectingMaterialSource = false;
        materialSourcePos = pos.immutable();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("ebe.printer.bound_chest")
                    .append(Component.literal(": " + formatPos(materialSourcePos))), false);
        }
        return true;
    }

    public static void tick() {
        if (!active || mode == PrinterMode.OFF) return;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return;

        var projection = ProjectionManager.getProjection();
        if (projection == null) return;

        tickCounter++;
        cleanupPendingPlacements();

        if (mode == PrinterMode.AUTO) {
            if (tickCounter % AUTO_SCAN_INTERVAL_TICKS != 0) {
                return;
            }
            rebuildBucketsIfNeeded(projection);
            int blocksPerTick = Math.max(1, Math.min(8, EBEClientConfig.printerParallelism.get()));
            BlockPos playerPos = player.blockPosition();
            BlockPos scanCenter = materialSourcePos != null ? materialSourcePos : playerPos;
            int range = materialSourcePos != null ? EBEClientConfig.printerMaterialSourceRange.get() : EBEClientConfig.printerRange.get();
            boolean unlimitedRange = range <= 0;
            List<ProjectionData.ProjectionBlock> candidates = collectNearbyCandidates(scanCenter, range);
            if (candidates.isEmpty()) return;

            int placed = 0;
            int scanned = 0;
            int start = Math.floorMod(candidateCursor, candidates.size());
            for (int i = 0; i < candidates.size() && scanned < MAX_CANDIDATE_SCAN_PER_TICK; i++) {
                if (placed >= blocksPerTick) break;
                scanned++;

                var pb = candidates.get((start + i) % candidates.size());
                BlockPos pos = pb.pos();
                if (pendingPlacements.containsKey(pos)) continue;
                if (!unlimitedRange && !isWithinBlockRange(pos, scanCenter, range)) continue;

                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) continue;

                if (tryPlaceBlock(player, pos, pb.state(), pb.nbt(), false, materialSourcePos, range)) {
                    placed++;
                }
            }
            candidateCursor = start + scanned;
        }
    }

    public static boolean tryPlaceBlock(LocalPlayer player, BlockPos pos, BlockState targetState, CompoundTag targetNbt) {
        return tryPlaceBlock(player, pos, targetState, targetNbt, false, materialSourcePos, EBEClientConfig.printerMaterialSourceRange.get());
    }

    private static boolean tryPlaceBlock(LocalPlayer player, BlockPos pos, BlockState targetState, CompoundTag targetNbt,
                                         boolean requireHeldItem, BlockPos sourcePos, int sourceRange) {
        var level = player.level();
        var existing = level.getBlockState(pos);
        if (!existing.isAir()) return false;

        var block = targetState.getBlock();
        var requiredItem = block.asItem();
        if (requiredItem == null) return false;

        boolean hasMaterial = player.isCreative();
        if (!hasMaterial && requireHeldItem) {
            var stack = player.getMainHandItem();
            hasMaterial = stack.is(requiredItem) && nbtMatchesForPlacement(stack, targetNbt, level.registryAccess());
        } else if (!hasMaterial && sourcePos != null) {
            // The authoritative material check happens on the server against the bound container.
            hasMaterial = true;
        } else if (!hasMaterial) {
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                var stack = player.getInventory().items.get(i);
                if (stack.is(requiredItem) && nbtMatchesForPlacement(stack, targetNbt, level.registryAccess())) {
                    hasMaterial = true;
                    break;
                }
            }
        }

        if (!hasMaterial) return false;

        int stateId = Block.getId(targetState);
        String nbtStr = targetNbt != null ? targetNbt.toString() : "";
        PacketDistributor.sendToServer(new PrinterPlacePayload(pos, stateId, nbtStr, sourcePos, requireHeldItem, sourceRange));
        pendingPlacements.put(pos.immutable(), PENDING_TIMEOUT_TICKS);
        return true;
    }

    private static void rebuildBucketsIfNeeded(ProjectionData projection) {
        if (bucketProjection == projection && bucketVersion == projection.getRenderVersion()) {
            return;
        }
        chunkBuckets.clear();
        for (var pb : projection.getBlocks()) {
            long key = ChunkPos.asLong(pb.pos().getX() >> 4, pb.pos().getZ() >> 4);
            chunkBuckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pb);
        }
        bucketProjection = projection;
        bucketVersion = projection.getRenderVersion();
        candidateCursor = 0;
    }

    private static List<ProjectionData.ProjectionBlock> collectNearbyCandidates(BlockPos centerPos, int range) {
        if (range <= 0) {
            List<ProjectionData.ProjectionBlock> candidates = new ArrayList<>();
            for (var blocks : chunkBuckets.values()) {
                candidates.addAll(blocks);
            }
            return candidates;
        }
        int chunkRadius = Math.max(1, (range >> 4) + 1);
        int centerX = centerPos.getX() >> 4;
        int centerZ = centerPos.getZ() >> 4;
        List<ProjectionData.ProjectionBlock> candidates = new ArrayList<>();
        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                var blocks = chunkBuckets.get(ChunkPos.asLong(centerX + dx, centerZ + dz));
                if (blocks != null) {
                    candidates.addAll(blocks);
                }
            }
        }
        return candidates;
    }

    private static boolean isWithinBlockRange(BlockPos pos, BlockPos center, int range) {
        double dx = pos.getX() - center.getX();
        double dy = pos.getY() - center.getY();
        double dz = pos.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz <= (double) range * range;
    }

    private static void cleanupPendingPlacements() {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || pendingPlacements.isEmpty()) {
            pendingPlacements.clear();
            return;
        }
        Iterator<Map.Entry<BlockPos, Integer>> it = pendingPlacements.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!level.getBlockState(entry.getKey()).isAir()) {
                it.remove();
                continue;
            }
            int ttl = entry.getValue() - 1;
            if (ttl <= 0) {
                it.remove();
            } else {
                entry.setValue(ttl);
            }
        }
    }

    private static boolean nbtMatchesForPlacement(net.minecraft.world.item.ItemStack stack, CompoundTag targetNbt, RegistryAccess registryAccess) {
        var cleanedTarget = targetNbt == null ? new CompoundTag() : cleanPlacementNbt(targetNbt);

        CompoundTag stackBlockEntityTag = extractStackBlockEntityTag(stack, registryAccess);
        if (cleanedTarget.isEmpty()) {
            return stackBlockEntityTag == null || cleanPlacementNbt(stackBlockEntityTag).isEmpty();
        }

        return stackBlockEntityTag != null && cleanPlacementNbt(stackBlockEntityTag).equals(cleanedTarget);
    }

    private static CompoundTag extractStackBlockEntityTag(net.minecraft.world.item.ItemStack stack, RegistryAccess registryAccess) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            return data.copyTag();
        }
        Tag saved = stack.saveOptional(registryAccess);
        if (saved instanceof CompoundTag ct && ct.contains("BlockEntityTag")) {
            return ct.getCompound("BlockEntityTag").copy();
        }
        return null;
    }

    private static CompoundTag cleanPlacementNbt(CompoundTag tag) {
        var cleaned = tag.copy();
        cleaned.remove("x");
        cleaned.remove("y");
        cleaned.remove("z");
        cleaned.remove("id");
        return cleaned;
    }

    public static boolean tryManualPlace() {
        if (mode != PrinterMode.MANUAL || !active) return false;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return false;

        var projection = ProjectionManager.getProjection();
        if (projection == null) return false;

        var stack = player.getMainHandItem();
        if (stack.isEmpty()) return false;

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 view = player.getViewVector(1.0F);
        double reach = EBEClientConfig.printerRange.get() <= 0 ? 256.0 : Math.max(16.0, EBEClientConfig.printerRange.get());
        Vec3 end = eye.add(view.scale(reach));

        ProjectionData.ProjectionBlock best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var pb : projection.getBlocks()) {
            if (!player.level().getBlockState(pb.pos()).isAir()) continue;

            var hit = new AABB(pb.pos()).clip(eye, end);
            if (hit.isPresent()) {
                double distance = eye.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pb;
                }
            }
        }

        if (best == null) return false;

        var requiredItem = best.state().getBlock().asItem();
        if (requiredItem == null || !stack.is(requiredItem)
                || !nbtMatchesForPlacement(stack, best.nbt(), player.level().registryAccess())) {
            player.displayClientMessage(Component.translatable("ebe.printer.no_materials"), true);
            return true;
        }

        return tryPlaceBlock(player, best.pos(), best.state(), best.nbt(), true, null, 0);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
