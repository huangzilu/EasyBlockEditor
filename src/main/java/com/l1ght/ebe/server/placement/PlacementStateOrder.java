package com.l1ght.ebe.server.placement;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class PlacementStateOrder {
    public static final int NORMAL = 0;
    public static final int WATERLOGGED = 1;
    public static final int FLUID = 2;

    private PlacementStateOrder() {
    }

    public static int phaseFromStateId(int stateId) {
        return phase(Block.stateById(stateId));
    }

    public static int phase(BlockState state) {
        if (state == null || state.isAir()) return NORMAL;
        if (isPureFluidBlock(state)) return FLUID;
        if (isWaterlogged(state)) return WATERLOGGED;
        return NORMAL;
    }

    public static boolean isPureFluidBlock(BlockState state) {
        if (state == null) return false;
        var block = state.getBlock();
        return block == Blocks.WATER || block == Blocks.LAVA;
    }

    public static boolean isWaterlogged(BlockState state) {
        return state != null
                && state.hasProperty(BlockStateProperties.WATERLOGGED)
                && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED));
    }
}
