package com.l1ght.ebe.client.projection;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import com.l1ght.ebe.network.WorkgroupProjectionDownloadPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WorkgroupProjectionReceiver {
    private static UUID currentId;
    private static String currentFileName = "";
    private static BlockPos currentOrigin = BlockPos.ZERO;
    private static boolean currentVisible = true;
    private static int expectedTotal = 0;
    private static final List<WorkgroupProjectionDownloadPayload.Entry> buffer = new ArrayList<>();

    private static UUID loadedId;

    private WorkgroupProjectionReceiver() {
    }

    public static void accept(UUID projectionId, String fileName, BlockPos origin, boolean visible,
                              int total, int offset, boolean done,
                              List<WorkgroupProjectionDownloadPayload.Entry> entries) {
        if (projectionId == null) return;
        // Ignore stray broadcasts when we are not actually in a workgroup (e.g. the server still
        // sent one after we left). Without this, a late packet could replace a projection the
        // user placed on their own after leaving.
        if (!com.l1ght.ebe.client.ClientOnlyHooks.isInWorkgroup()) {
            reset();
            return;
        }

        if (offset == 0 || !projectionId.equals(currentId)) {
            currentId = projectionId;
            currentFileName = fileName == null ? "" : fileName;
            currentOrigin = origin == null ? BlockPos.ZERO : origin;
            currentVisible = visible;
            expectedTotal = total;
            buffer.clear();
        }

        if (offset != buffer.size()) {
            reset();
            return;
        }
        buffer.addAll(entries);

        if (done) {
            if (expectedTotal > 0 && buffer.size() != expectedTotal) {
                reset();
                return;
            }
            apply(projectionId, currentFileName, currentOrigin, currentVisible, new ArrayList<>(buffer));
            reset();
        }
    }

    private static void reset() {
        currentId = null;
        currentFileName = "";
        currentOrigin = BlockPos.ZERO;
        currentVisible = true;
        expectedTotal = 0;
        buffer.clear();
    }

    /** Called when the local player leaves the workgroup, so a later re-join re-applies cleanly. */
    public static void clearLoadedId() {
        loadedId = null;
        reset();
    }

    private static void apply(UUID projectionId, String fileName, BlockPos origin, boolean visible,
                              List<WorkgroupProjectionDownloadPayload.Entry> entries) {
        if (entries.isEmpty()) return;
        if (projectionId.equals(loadedId)) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var entry : entries) {
            BlockPos p = entry.pos();
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }

        BuildingModel model = new BuildingModel();
        Region region = model.addRegion("synced", minX, minY, minZ,
                maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        int placed = 0;
        for (var entry : entries) {
            BlockState state = Block.stateById(entry.stateId());
            if (state == null || state.isAir()) continue;
            BlockPos p = entry.pos();
            region.setWorldBlock(p.getX(), p.getY(), p.getZ(), state);
            String nbt = entry.nbt();
            if (nbt != null && !nbt.isEmpty()) {
                try {
                    CompoundTag tag = TagParser.parseTag(nbt);
                    region.setWorldBlockEntity(p.getX(), p.getY(), p.getZ(), tag);
                } catch (Exception ignored) {
                }
            }
            placed++;
        }

        if (placed == 0) return;
        final int finalPlaced = placed;

        BlockPos zero = BlockPos.ZERO;
        String cacheKey = "workgroup:" + projectionId + ":" + System.identityHashCode(model);
        com.l1ght.ebe.projection.compute.ProjectionComputePlanner.computeAsync(
                        cacheKey, model, zero,
                        net.minecraft.world.level.block.Rotation.NONE,
                        net.minecraft.world.level.block.Mirror.NONE,
                        zero, false)
                .whenComplete((computed, error) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (error != null || computed == null) {
                        EBEMod.LOGGER.warn("Failed to compute synced workgroup projection {}", fileName, error);
                        return;
                    }
                    try {
                        // Replace atomically: setProjection() overwrites the active projection on
                        // its own. Do NOT removeProjection() first — if anything below threw, the
                        // old removeProjection() left the client with no projection AND a deleted
                        // persisted state, which never recovered (the reported bug).
                        ProjectionManager.setProjection(model, computed);
                        ProjectionManager.setProjectionSourceName(fileName);
                        ProjectionManager.loadProjection();
                        ProjectionManager.setProjectionVisible(visible);
                        loadedId = projectionId;
                        EBEMod.LOGGER.info("Loaded synced workgroup projection {} ({} blocks)", fileName, finalPlaced);
                    } catch (Exception e) {
                        EBEMod.LOGGER.warn("Failed to load synced workgroup projection {}", fileName, e);
                    }
                }));
    }
}
