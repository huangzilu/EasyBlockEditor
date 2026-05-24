package com.l1ght.ebe.client.renderer;

import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;

public class FastTrackedDummyWorld extends TrackedDummyWorld {
    private final boolean sparseStorage;
    private final Long2ObjectOpenHashMap<BlockInfo> sparseBlocks;
    private final Long2ObjectOpenHashMap<BlockEntity> sparseBlockEntities;

    public FastTrackedDummyWorld() {
        this(false);
    }

    public FastTrackedDummyWorld(boolean sparseStorage) {
        super();
        this.sparseStorage = sparseStorage;
        this.sparseBlocks = sparseStorage ? new Long2ObjectOpenHashMap<>() : null;
        this.sparseBlockEntities = sparseStorage ? new Long2ObjectOpenHashMap<>() : null;
    }

    public boolean isSparseStorage() {
        return sparseStorage;
    }

    public void addBlockFast(BlockPos pos, BlockInfo info) {
        BlockState state = info.getBlockState();
        if (state.getBlock() == Blocks.AIR) {
            return;
        }
        if (sparseStorage) {
            long key = pos.asLong();
            sparseBlocks.put(key, info);
            addFilledBlock(pos);
            BlockEntity blockEntity = createSparseBlockEntity(pos, state, info);
            if (blockEntity != null) {
                sparseBlockEntities.put(key, blockEntity);
            } else {
                sparseBlockEntities.remove(key);
            }
            return;
        }
        ChunkAccess chunk = getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, true);
        chunk.setBlockState(pos, state, false);
        addFilledBlock(pos);
        if (info.hasBlockEntity()) {
            info.postEntity(getBlockEntity(pos));
        }
    }

    @Override
    public void addBlock(BlockPos pos, BlockInfo info) {
        if (sparseStorage) {
            addBlockFast(pos, info);
        } else {
            super.addBlock(pos, info);
        }
    }

    @Override
    public void removeBlock(BlockPos pos) {
        if (sparseStorage) {
            long key = pos.asLong();
            sparseBlocks.remove(key);
            sparseBlockEntities.remove(key);
            removeFilledBlock(pos);
        } else {
            super.removeBlock(pos);
        }
    }

    @Override
    public void clear() {
        if (sparseStorage) {
            sparseBlocks.clear();
            sparseBlockEntities.clear();
            filledBlocks.clear();
        } else {
            super.clear();
        }
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (sparseStorage) {
            BlockInfo info = sparseBlocks.get(pos.asLong());
            return info == null ? Blocks.AIR.defaultBlockState() : info.getBlockState();
        }
        return super.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (sparseStorage) {
            return getBlockState(pos).getFluidState();
        }
        return super.getFluidState(pos);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        if (sparseStorage) {
            return sparseBlockEntities.get(pos.asLong());
        }
        return super.getBlockEntity(pos);
    }

    private BlockEntity createSparseBlockEntity(BlockPos pos, BlockState state, BlockInfo info) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
        if (blockEntity == null) {
            return null;
        }
        blockEntity.setLevel(this);
        blockEntity.setBlockState(state);
        info.postEntity(blockEntity);
        return blockEntity;
    }
}
