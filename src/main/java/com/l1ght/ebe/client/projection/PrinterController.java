package com.l1ght.ebe.client.projection;

import com.l1ght.ebe.client.ClientOnlyHooks;
import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.network.PrinterPlaceBatchPayload;
import com.l1ght.ebe.network.PrinterPlacePayload;
import com.l1ght.ebe.network.WorkgroupPrintReservationPayload;
import com.l1ght.ebe.network.WorkgroupPrintReservePayload;
import com.l1ght.ebe.network.WorkgroupPrintUploadPayload;
import com.l1ght.ebe.network.WorkgroupPrinterPlacePayload;
import com.l1ght.ebe.projection.PrinterMode;
import com.l1ght.ebe.projection.ProjectionData;
import com.l1ght.ebe.server.placement.PlacementStateOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class PrinterController {

    private static PrinterMode mode = PrinterMode.OFF;
    private static boolean active = false;
    private static int tickCounter = 0;
    private static final int PENDING_TIMEOUT_TICKS = 40;
    private static final int AUTO_SCAN_INTERVAL_TICKS = 1;
    private static final int MAX_CANDIDATE_SCAN_PER_TICK = 4096;
    private static final int COMPLETION_CHECK_INTERVAL_TICKS = 10;
    private static final int WORKGROUP_UPLOAD_BATCH_SIZE = 512;
    private static final int WORKGROUP_UPLOAD_MAX_NBT_CHARS_PER_BATCH = 64_000;
    private static final int WORKGROUP_UPLOAD_MAX_SINGLE_NBT_CHARS = 32_000;
    private static final int WORKGROUP_RESERVATION_REQUEST_INTERVAL_TICKS = 5;
    private static final Map<BlockPos, Integer> pendingPlacements = new HashMap<>();
    private static final PrinterCandidatePlanner candidatePlanner = new PrinterCandidatePlanner();
    private static final Queue<WorkgroupPrintReservationPayload.Entry> workgroupReservationQueue = new ArrayDeque<>();
    private static final Set<UUID> workgroupPendingTokens = new HashSet<>();
    private static BlockPos materialSourcePos;
    private static boolean selectingMaterialSource = false;
    private static UUID workgroupUploadId;
    private static String workgroupUploadKey = "";
    private static int workgroupUploadOffset = 0;
    private static boolean workgroupUploadInProgress = false;
    private static boolean awaitingWorkgroupSession = false;
    private static int awaitingWorkgroupSessionTicks = 0;
    private static String workgroupUploadReplacesSessionId = "";
    private static int lastWorkgroupReservationRequestTick = 0;
    private static int completionCheckCooldown = 0;

    public static void setMode(PrinterMode m) { mode = m; active = false; }
    public static PrinterMode getMode() { return mode; }
    public static boolean isActive() { return mode != PrinterMode.OFF && active; }
    public static void setActive(boolean a) { active = a; }
    public static void toggle() { if (mode != PrinterMode.OFF) active = !active; }
    public static BlockPos getMaterialSourcePos() { return materialSourcePos; }
    public static boolean isSelectingMaterialSource() { return selectingMaterialSource; }
    public static void clearMaterialSource() { materialSourcePos = null; selectingMaterialSource = false; }

    public static void stopAfterProjectionCompleted() {
        active = false;
        pendingPlacements.clear();
        candidatePlanner.reset();
        resetWorkgroupUpload("");
        completionCheckCooldown = 0;
    }

    public static void handleWorkgroupPrintSessionEnded() {
        if (!active || mode != PrinterMode.AUTO) return;
        if (ProjectionManager.isPlacementComplete()) {
            ProjectionManager.finishPlacementAndUnload();
            return;
        }
        stopAfterProjectionCompleted();
    }

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
        if (projection == null || !ProjectionManager.isProjectionLoaded()) {
            stopAfterProjectionCompleted();
            return;
        }

        tickCounter++;
        cleanupPendingPlacements();
        if (finishIfProjectionComplete(false)) {
            return;
        }

        if (mode == PrinterMode.AUTO) {
            if (tickCounter % AUTO_SCAN_INTERVAL_TICKS != 0) {
                return;
            }

            if (ClientOnlyHooks.isInWorkgroup()) {
                tickWorkgroupAuto(player, projection);
                return;
            }

            int blocksPerTick = Math.max(1, Math.min(8, EBEClientConfig.printerParallelism.get()));
            BlockPos playerPos = player.blockPosition();
            BlockPos scanCenter = materialSourcePos != null ? materialSourcePos : playerPos;
            int range = materialSourcePos != null ? EBEClientConfig.printerMaterialSourceRange.get() : EBEClientConfig.printerRange.get();
            List<ProjectionData.ProjectionBlock> candidates = candidatePlanner.nextCandidates(
                    projection,
                    scanCenter,
                    range,
                    blocksPerTick,
                    MAX_CANDIDATE_SCAN_PER_TICK,
                    pendingPlacements.keySet(),
                    pb -> {
                        var existing = level.getBlockState(pb.pos());
                        return existing.isAir()
                                || (existing.getBlock() == net.minecraft.world.level.block.Blocks.WATER
                                && PlacementStateOrder.isWaterlogged(pb.state()));
                    }
            );
            if (candidates.isEmpty()) {
                finishIfProjectionComplete(true);
                return;
            }

            int placed = 0;
            List<PrinterPlaceBatchPayload.Entry> batch = new ArrayList<>(blocksPerTick);
            for (var pb : candidates) {
                if (placed >= blocksPerTick) break;
                BlockPos pos = pb.pos();

                var entry = preparePlaceEntry(player, pos, pb.state(), pb.nbt(), false, materialSourcePos);
                if (entry != null) {
                    batch.add(entry);
                    pendingPlacements.put(pos.immutable(), PENDING_TIMEOUT_TICKS);
                    placed++;
                }
            }
            if (!batch.isEmpty()) {
                PacketDistributor.sendToServer(new PrinterPlaceBatchPayload(batch, materialSourcePos, false, range));
            }
        }
    }

    public static void acceptWorkgroupReservations(List<WorkgroupPrintReservationPayload.Entry> reservations) {
        if (reservations == null || reservations.isEmpty()) return;
        for (var reservation : reservations) {
            if (workgroupPendingTokens.add(reservation.token())) {
                workgroupReservationQueue.add(reservation);
            }
        }
    }

    private static void tickWorkgroupAuto(LocalPlayer player, ProjectionData projection) {
        ensureWorkgroupUpload(projection);

        int blocksPerTick = Math.max(1, Math.min(8, EBEClientConfig.printerParallelism.get()));
        int placed = 0;
        while (placed < blocksPerTick && !workgroupReservationQueue.isEmpty()) {
            var reservation = workgroupReservationQueue.poll();
            workgroupPendingTokens.remove(reservation.token());
            if (tryPlaceWorkgroupReservation(reservation)) {
                placed++;
            }
        }

        if (!workgroupUploadInProgress && ClientOnlyHooks.hasWorkgroupPrintSession()
                && tickCounter - lastWorkgroupReservationRequestTick >= WORKGROUP_RESERVATION_REQUEST_INTERVAL_TICKS
                && workgroupReservationQueue.size() < blocksPerTick * 2) {
            requestWorkgroupReservations(player, blocksPerTick);
        }
    }

    private static void ensureWorkgroupUpload(ProjectionData projection) {
        String key = projection.getRenderVersion() + ":" + projection.getBlockCount() + ":" + projection.getOrigin().asLong();
        boolean projectionChanged = !key.equals(workgroupUploadKey);
        if (projectionChanged) {
            resetWorkgroupUpload(key);
        }

        if (ClientOnlyHooks.hasWorkgroupPrintSession() && !projectionChanged
                && !workgroupUploadInProgress && !awaitingWorkgroupSession) {
            awaitingWorkgroupSession = false;
            awaitingWorkgroupSessionTicks = 0;
            return;
        }

        if (awaitingWorkgroupSession) {
            String currentSessionId = ClientOnlyHooks.getWorkgroupPrintSessionId();
            if (ClientOnlyHooks.hasWorkgroupPrintSession()
                    && !currentSessionId.isEmpty()
                    && !currentSessionId.equals(workgroupUploadReplacesSessionId)) {
                awaitingWorkgroupSession = false;
                awaitingWorkgroupSessionTicks = 0;
                return;
            }
            awaitingWorkgroupSessionTicks++;
            if (awaitingWorkgroupSessionTicks < 200) {
                return;
            }
            awaitingWorkgroupSession = false;
            awaitingWorkgroupSessionTicks = 0;
            resetWorkgroupUpload(key);
        }

        if (!workgroupUploadInProgress) {
            workgroupUploadId = UUID.randomUUID();
            workgroupUploadOffset = 0;
            workgroupUploadInProgress = true;
            workgroupUploadReplacesSessionId = ClientOnlyHooks.getWorkgroupPrintSessionId();
        }

        List<ProjectionData.ProjectionBlock> blocks = projection.getBlocks();
        if (blocks.isEmpty()) return;

        int start = workgroupUploadOffset;
        int hardEnd = Math.min(blocks.size(), start + WORKGROUP_UPLOAD_BATCH_SIZE);
        if (start >= hardEnd) {
            workgroupUploadInProgress = false;
            awaitingWorkgroupSession = true;
            return;
        }

        List<WorkgroupPrintUploadPayload.Entry> entries = new ArrayList<>(hardEnd - start);
        int nbtChars = 0;
        for (int i = start; i < hardEnd; i++) {
            var block = blocks.get(i);
            String nbtString = encodeNbtForUpload(block.nbt());
            if (!entries.isEmpty() && nbtChars + nbtString.length() > WORKGROUP_UPLOAD_MAX_NBT_CHARS_PER_BATCH) {
                break;
            }
            nbtChars += nbtString.length();
            entries.add(new WorkgroupPrintUploadPayload.Entry(
                    block.pos(),
                    Block.getId(block.state()),
                    nbtString
            ));
        }
        int end = start + entries.size();

        boolean done = end >= blocks.size();
        PacketDistributor.sendToServer(new WorkgroupPrintUploadPayload(
                workgroupUploadId,
                "projection",
                blocks.size(),
                start,
                done,
                entries
        ));
        workgroupUploadOffset = end;

        if (done) {
            workgroupUploadInProgress = false;
            awaitingWorkgroupSession = true;
            awaitingWorkgroupSessionTicks = 0;
        }
    }

    private static String encodeNbtForUpload(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return "";
        String encoded = tag.toString();
        return encoded.length() <= WORKGROUP_UPLOAD_MAX_SINGLE_NBT_CHARS ? encoded : "";
    }

    private static void resetWorkgroupUpload(String key) {
        workgroupUploadKey = key;
        workgroupUploadId = null;
        workgroupUploadOffset = 0;
        workgroupUploadInProgress = false;
        awaitingWorkgroupSession = false;
        awaitingWorkgroupSessionTicks = 0;
        workgroupUploadReplacesSessionId = "";
        lastWorkgroupReservationRequestTick = 0;
        workgroupReservationQueue.clear();
        workgroupPendingTokens.clear();
    }

    private static void requestWorkgroupReservations(LocalPlayer player, int blocksPerTick) {
        BlockPos playerPos = player.blockPosition();
        BlockPos scanCenter = materialSourcePos != null ? materialSourcePos : playerPos;
        int range = materialSourcePos != null ? EBEClientConfig.printerMaterialSourceRange.get() : EBEClientConfig.printerRange.get();
        int maxReservations = Math.max(1, Math.min(64, blocksPerTick * 4));
        PacketDistributor.sendToServer(new WorkgroupPrintReservePayload(maxReservations, scanCenter, range));
        lastWorkgroupReservationRequestTick = tickCounter;
    }

    private static boolean tryPlaceWorkgroupReservation(WorkgroupPrintReservationPayload.Entry reservation) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return false;

        BlockState targetState = Block.stateById(reservation.stateId());
        if (targetState == null || targetState.isAir()) return false;

        int range = materialSourcePos != null ? EBEClientConfig.printerMaterialSourceRange.get() : EBEClientConfig.printerRange.get();
        PacketDistributor.sendToServer(new WorkgroupPrinterPlacePayload(
                reservation.sessionId(),
                reservation.token(),
                materialSourcePos,
                false,
                range
        ));
        pendingPlacements.put(reservation.pos().immutable(), PENDING_TIMEOUT_TICKS);
        return true;
    }

    public static boolean tryPlaceBlock(LocalPlayer player, BlockPos pos, BlockState targetState, CompoundTag targetNbt) {
        return tryPlaceBlock(player, pos, targetState, targetNbt, false, materialSourcePos, EBEClientConfig.printerMaterialSourceRange.get());
    }

    private static boolean tryPlaceBlock(LocalPlayer player, BlockPos pos, BlockState targetState, CompoundTag targetNbt,
                                         boolean requireHeldItem, BlockPos sourcePos, int sourceRange) {
        var entry = preparePlaceEntry(player, pos, targetState, targetNbt, requireHeldItem, sourcePos);
        if (entry == null) return false;
        PacketDistributor.sendToServer(new PrinterPlacePayload(pos, entry.stateId(), entry.nbtStr(), sourcePos, requireHeldItem, sourceRange));
        pendingPlacements.put(pos.immutable(), PENDING_TIMEOUT_TICKS);
        return true;
    }

    private static PrinterPlaceBatchPayload.Entry preparePlaceEntry(LocalPlayer player, BlockPos pos,
                                                                    BlockState targetState, CompoundTag targetNbt,
                                                                    boolean requireHeldItem, BlockPos sourcePos) {
        var level = player.level();
        var existing = level.getBlockState(pos);
        if (!existing.isAir()
                && !(existing.getBlock() == net.minecraft.world.level.block.Blocks.WATER
                && PlacementStateOrder.isWaterlogged(targetState))) {
            return null;
        }

        List<Item> requiredItems = materialItems(targetState);

        boolean hasMaterial = player.isCreative();
        if (!hasMaterial && requireHeldItem) {
            hasMaterial = hasHeldMaterials(player, requiredItems);
        } else if (!hasMaterial && sourcePos != null) {
            // The authoritative material check happens on the server against the bound container.
            hasMaterial = true;
        } else if (!hasMaterial) {
            hasMaterial = hasInventoryMaterials(player, requiredItems);
        }

        if (!hasMaterial) return null;

        int stateId = Block.getId(targetState);
        String nbtStr = targetNbt != null ? targetNbt.toString() : "";
        return new PrinterPlaceBatchPayload.Entry(pos, stateId, nbtStr);
    }

    private static boolean hasHeldMaterials(LocalPlayer player, List<Item> requiredItems) {
        if (requiredItems.isEmpty()) return false;
        if (!player.getMainHandItem().is(requiredItems.getFirst())) return false;
        if (requiredItems.size() == 1) return true;
        return hasInventoryMaterials(player, requiredItems.subList(1, requiredItems.size()));
    }

    private static boolean hasInventoryMaterials(LocalPlayer player, List<Item> requiredItems) {
        if (requiredItems.isEmpty()) return false;
        for (var item : requiredItems) {
            boolean found = false;
            for (var stack : player.getInventory().items) {
                if (stack.is(item)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static List<Item> materialItems(BlockState state) {
        var items = new ArrayList<Item>(2);
        if (state == null || state.isAir()) return items;
        if (state.getBlock() == net.minecraft.world.level.block.Blocks.WATER) {
            items.add(Items.WATER_BUCKET);
            return items;
        }
        if (state.getBlock() == net.minecraft.world.level.block.Blocks.LAVA) {
            items.add(Items.LAVA_BUCKET);
            return items;
        }
        if (state.getBlock() == net.minecraft.world.level.block.Blocks.POWDER_SNOW) {
            items.add(Items.POWDER_SNOW_BUCKET);
            return items;
        }
        var blockItem = state.getBlock().asItem();
        if (blockItem != Items.AIR) {
            items.add(blockItem);
        }
        if (PlacementStateOrder.isWaterlogged(state)) {
            items.add(Items.WATER_BUCKET);
        }
        return items;
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

    private static boolean finishIfProjectionComplete(boolean force) {
        if (!pendingPlacements.isEmpty()) return false;
        if (!force) {
            if (completionCheckCooldown > 0) {
                completionCheckCooldown--;
                return false;
            }
            completionCheckCooldown = COMPLETION_CHECK_INTERVAL_TICKS;
        }

        if (!ProjectionManager.isPlacementComplete()) {
            return false;
        }

        ProjectionManager.finishPlacementAndUnload();
        return true;
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
            var existing = player.level().getBlockState(pb.pos());
            if (!existing.isAir()
                    && !(existing.getBlock() == net.minecraft.world.level.block.Blocks.WATER
                    && PlacementStateOrder.isWaterlogged(pb.state()))) {
                continue;
            }

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

        var requiredItems = materialItems(best.state());
        if (!hasHeldMaterials(player, requiredItems)) {
            player.displayClientMessage(Component.translatable("ebe.printer.no_materials"), true);
            return true;
        }

        return tryPlaceBlock(player, best.pos(), best.state(), best.nbt(), true, null, 0);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
