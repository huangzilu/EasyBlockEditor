package com.l1ght.ebe.client.renderer;

import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public class FastTrackedDummyWorld extends TrackedDummyWorld {
    public void addBlockFast(BlockPos pos, BlockInfo info) {
        BlockState state = info.getBlockState();
        if (state.getBlock() == Blocks.AIR) {
            return;
        }
        ChunkAccess chunk = getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, true);
        chunk.setBlockState(pos, state, false);
        if (info.hasBlockEntity()) {
            info.postEntity(getBlockEntity(pos));
        }
    }
}
