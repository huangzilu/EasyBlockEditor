package com.l1ght.ebe.projection;

import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
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

    public ProjectionData(BuildingModel model, BlockPos origin) {
        this.model = model;
        this.origin = origin;
        this.blocks = new ArrayList<>();
        computeBlocks();
        this.centerPoint = new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    private void computeBlocks() {
        blocks.clear();
        minX = Integer.MAX_VALUE; minY = Integer.MAX_VALUE; minZ = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE; maxY = Integer.MIN_VALUE; maxZ = Integer.MIN_VALUE;

        for (Region region : model.getRegions()) {
            var container = region.getBlocks();
            int ox = region.getOffsetX(), oy = region.getOffsetY(), oz = region.getOffsetZ();
            int sx = region.getSizeX(), sy = region.getSizeY(), sz = region.getSizeZ();

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        Object obj = container.get(x, y, z);
                        BlockState state = obj instanceof BlockState bs ? bs : null;
                        if (state == null || state.isAir()) continue;

                        int worldX = ox + x, worldY = oy + y, worldZ = oz + z;
                        CompoundTag nbt = region.getBlockEntity(x, y, z);

                        BlockPos localPos = new BlockPos(worldX, worldY, worldZ);
                        BlockPos worldPos = origin.offset(localPos);

                        if (rotation != Rotation.NONE || mirror != Mirror.NONE) {
                            BlockPos relToCenter = localPos.subtract(centerPoint == null ? BlockPos.ZERO : centerPoint.subtract(origin));
                            BlockPos rotated = rotatePos(relToCenter, rotation);
                            BlockPos mirrored = mirrorPos(rotated, mirror);
                            worldPos = (centerPoint != null ? centerPoint : origin).offset(mirrored);
                            state = transformState(state, rotation, mirror);
                        }

                        blocks.add(new ProjectionBlock(worldPos, state, nbt));

                        minX = Math.min(minX, worldPos.getX());
                        minY = Math.min(minY, worldPos.getY());
                        minZ = Math.min(minZ, worldPos.getZ());
                        maxX = Math.max(maxX, worldPos.getX());
                        maxY = Math.max(maxY, worldPos.getY());
                        maxZ = Math.max(maxZ, worldPos.getZ());
                    }
                }
            }
        }
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

    public void setOrigin(BlockPos origin) { this.origin = origin; computeBlocks(); }
    public void setRotation(Rotation rotation) { this.rotation = rotation; computeBlocks(); }
    public void setMirror(Mirror mirror) { this.mirror = mirror; computeBlocks(); }
    public void setCenterPoint(BlockPos center) { this.centerPoint = center; computeBlocks(); }

    public void rotateClockwise90() {
        this.rotation = switch (this.rotation) {
            case NONE -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.NONE;
        };
        computeBlocks();
    }

    public void rotateCounterClockwise90() {
        this.rotation = switch (this.rotation) {
            case NONE -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.NONE;
        };
        computeBlocks();
    }

    public void rotate180() {
        this.rotation = this.rotation == Rotation.NONE ? Rotation.CLOCKWISE_180 :
                this.rotation == Rotation.CLOCKWISE_180 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        computeBlocks();
    }

    public void toggleMirrorLeftRight() {
        this.mirror = this.mirror == Mirror.LEFT_RIGHT ? Mirror.NONE : Mirror.LEFT_RIGHT;
        computeBlocks();
    }

    public void toggleMirrorFrontBack() {
        this.mirror = this.mirror == Mirror.FRONT_BACK ? Mirror.NONE : Mirror.FRONT_BACK;
        computeBlocks();
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

    public record ProjectionBlock(BlockPos pos, BlockState state, CompoundTag nbt) {
        public boolean hasNbt() { return nbt != null && !nbt.isEmpty(); }
        public String nbtKey() {
            if (!hasNbt()) return "";
            return nbt.toString();
        }
    }
}
