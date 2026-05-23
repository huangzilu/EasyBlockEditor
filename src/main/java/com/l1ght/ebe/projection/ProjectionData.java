package com.l1ght.ebe.projection;

import com.l1ght.ebe.projection.compute.ComputedProjection;
import com.l1ght.ebe.projection.compute.ProjectionComputePlanner;
import com.l1ght.ebe.data.BuildingModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class ProjectionData {

    private final BuildingModel model;
    private BlockPos origin;
    private BlockPos centerPoint;
    private Rotation rotation = Rotation.NONE;
    private Mirror mirror = Mirror.NONE;
    private List<ProjectionBlock> blocks;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int renderVersion = 0;
    private int meshVersion = 0;

    public ProjectionData(BuildingModel model, BlockPos origin) {
        this.model = model;
        this.origin = origin;
        this.blocks = new ArrayList<>();
        this.centerPoint = origin;
        computeBlocks(true);
    }

    public ProjectionData(BuildingModel model, BlockPos origin, ComputedProjection computed) {
        this.model = model;
        this.origin = origin;
        this.blocks = new ArrayList<>();
        this.centerPoint = computed != null ? computed.getCenterPoint() : origin;
        if (computed != null) {
            applyComputed(computed, true);
        } else {
            computeBlocks(true);
        }
    }

    private void computeBlocks(boolean meshChanged) {
        var computed = ProjectionComputePlanner.compute(
                model,
                origin,
                rotation,
                mirror,
                centerPoint == null ? origin : centerPoint,
                false
        );
        applyComputed(computed, meshChanged);
    }

    public void applyComputed(ComputedProjection computed, boolean meshChanged) {
        blocks.clear();
        for (var entry : computed.getBlocks()) {
            blocks.add(new ProjectionBlock(entry.getPos(), entry.getState(), entry.getNbt()));
        }
        minX = computed.getMinX();
        minY = computed.getMinY();
        minZ = computed.getMinZ();
        maxX = computed.getMaxX();
        maxY = computed.getMaxY();
        maxZ = computed.getMaxZ();
        renderVersion++;
        if (meshChanged) {
            meshVersion++;
        }
    }

    public void applyComputedTransform(ComputedProjection computed, BlockPos origin, Rotation rotation,
                                       Mirror mirror, BlockPos centerPoint, boolean meshChanged) {
        this.origin = origin;
        this.rotation = rotation;
        this.mirror = mirror;
        this.centerPoint = centerPoint;
        applyComputed(computed, meshChanged);
    }

    private static BlockPos rotatePos(BlockPos pos, Rotation rot) {
        return switch (rot) {
            case CLOCKWISE_90 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default -> pos;
        };
    }

    private static BlockPos mirrorPos(BlockPos pos, Mirror mir) {
        return switch (mir) {
            case LEFT_RIGHT -> new BlockPos(pos.getX(), pos.getY(), -pos.getZ());
            case FRONT_BACK -> new BlockPos(-pos.getX(), pos.getY(), pos.getZ());
            default -> pos;
        };
    }

    private static BlockState transformState(BlockState state, Rotation rot, Mirror mir) {
        if (mir != Mirror.NONE) state = state.mirror(mir);
        if (rot != Rotation.NONE) state = state.rotate(rot);
        return state;
    }

    public void setOrigin(BlockPos origin) {
        this.origin = origin;
        computeBlocks(rotation != Rotation.NONE || mirror != Mirror.NONE);
    }
    public void setRotation(Rotation rotation) { this.rotation = rotation; computeBlocks(true); }
    public void setMirror(Mirror mirror) { this.mirror = mirror; computeBlocks(true); }
    public void setCenterPoint(BlockPos center) { this.centerPoint = center; computeBlocks(true); }

    public void rotateClockwise90() {
        this.rotation = switch (this.rotation) {
            case NONE -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.NONE;
        };
        computeBlocks(true);
    }

    public void rotateCounterClockwise90() {
        this.rotation = switch (this.rotation) {
            case NONE -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.NONE;
        };
        computeBlocks(true);
    }

    public void rotate180() {
        this.rotation = this.rotation == Rotation.NONE ? Rotation.CLOCKWISE_180 :
                this.rotation == Rotation.CLOCKWISE_180 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        computeBlocks(true);
    }

    public void toggleMirrorLeftRight() {
        this.mirror = this.mirror == Mirror.LEFT_RIGHT ? Mirror.NONE : Mirror.LEFT_RIGHT;
        computeBlocks(true);
    }

    public void toggleMirrorFrontBack() {
        this.mirror = this.mirror == Mirror.FRONT_BACK ? Mirror.NONE : Mirror.FRONT_BACK;
        computeBlocks(true);
    }

    public Direction getFacing() {
        return switch (rotation) {
            case CLOCKWISE_90 -> Direction.EAST;
            case CLOCKWISE_180 -> Direction.SOUTH;
            case COUNTERCLOCKWISE_90 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    public BuildingModel getModel() { return model; }
    public BlockPos getOrigin() { return origin; }
    public List<ProjectionBlock> getBlocks() { return blocks; }
    public int getBlockCount() { return blocks.size(); }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public Rotation getRotation() { return rotation; }
    public Mirror getMirror() { return mirror; }
    public BlockPos getCenterPoint() { return centerPoint; }
    public int getRenderVersion() { return renderVersion; }
    public int getMeshVersion() { return meshVersion; }

    public record ProjectionBlock(BlockPos pos, BlockState state, CompoundTag nbt) {
        public boolean hasNbt() { return nbt != null && !nbt.isEmpty(); }
        public String nbtKey() {
            if (!hasNbt()) return "";
            return nbt.toString();
        }
    }
}
