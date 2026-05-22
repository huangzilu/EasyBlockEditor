package com.l1ght.ebe.projection;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.network.PlaceBlocksPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ProjectionManager {

    private static ProjectionData activeProjection;
    private static boolean projectionVisible = true;
    private static boolean projectionLoaded = false;
    private static BlockPos projectionOrigin = BlockPos.ZERO;
    private static int progressPlaced = 0;
    private static int progressTotal = 0;

    public static void setProjection(BuildingModel model) {
        if (model == null) {
            activeProjection = null;
            projectionLoaded = false;
            return;
        }
        activeProjection = new ProjectionData(model, projectionOrigin);
        projectionLoaded = true;
    }

    public static void selectProjection(BuildingModel model) {
        if (model == null) return;
        activeProjection = new ProjectionData(model, projectionOrigin);
        projectionLoaded = false;
    }

    public static void loadProjection() {
        if (activeProjection != null) {
            projectionLoaded = true;
        }
    }

    public static void unloadProjection() {
        projectionLoaded = false;
    }

    public static void removeProjection() {
        activeProjection = null;
        projectionLoaded = false;
    }

    public static boolean hasProjection() {
        return activeProjection != null;
    }

    public static ProjectionData getProjection() {
        return activeProjection;
    }

    public static boolean isProjectionVisible() {
        return projectionVisible && activeProjection != null && projectionLoaded;
    }

    public static boolean isProjectionLoaded() {
        return projectionLoaded;
    }

    public static void setProjectionVisible(boolean visible) {
        projectionVisible = visible;
    }

    public static BlockPos getProjectionOrigin() {
        return projectionOrigin;
    }

    public static void setProjectionOrigin(BlockPos origin) {
        projectionOrigin = origin;
        if (activeProjection != null) {
            activeProjection.setOrigin(origin);
        }
    }

    public static void moveOrigin(int dx, int dy, int dz) {
        setProjectionOrigin(projectionOrigin.offset(dx, dy, dz));
    }

    public static float getOpacity() {
        return EBEClientConfig.projectionOpacity.get().floatValue();
    }

    public static void setProgress(int placed, int total) {
        progressPlaced = placed;
        progressTotal = total;
    }

    public static int getProgressPlaced() { return progressPlaced; }
    public static int getProgressTotal() { return progressTotal; }
    public static float getProgressPercent() {
        return progressTotal > 0 ? (float) progressPlaced / progressTotal : 0;
    }

    public static void placeAll() {
        if (activeProjection == null || !projectionLoaded) return;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        if (!player.isCreative()) return;

        var blocks = activeProjection.getBlocks();
        if (blocks.isEmpty()) return;

        List<PlaceBlocksPayload.Entry> entries = new ArrayList<>();
        for (var pb : blocks) {
            int stateId = Block.getId(pb.state());
            entries.add(new PlaceBlocksPayload.Entry(pb.pos(), stateId));
        }

        PacketDistributor.sendToServer(new PlaceBlocksPayload(entries));
    }

    public static void calculateProgress() {
        if (activeProjection == null || !projectionLoaded) {
            progressPlaced = 0;
            progressTotal = 0;
            return;
        }

        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        var blocks = activeProjection.getBlocks();
        int placed = 0;
        int total = blocks.size();

        for (var pb : blocks) {
            var existing = level.getBlockState(pb.pos());
            if (!existing.isAir() && existing.getBlock() == pb.state().getBlock()) {
                placed++;
            }
        }

        progressPlaced = placed;
        progressTotal = total;
    }
}
