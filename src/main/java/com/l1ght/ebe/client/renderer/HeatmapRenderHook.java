package com.l1ght.ebe.client.renderer;

import com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HeatmapRenderHook implements ISceneBlockRenderHook {

    private HeatmapMode mode = HeatmapMode.OFF;
    private int minY = 0;
    private int maxY = 255;
    private final Map<Block, Integer> typeColorMap = new ConcurrentHashMap<>();
    private final AtomicInteger typeColorIndex = new AtomicInteger(0);

    private static final int[] TYPE_PALETTE = {
            0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFF9C27B0,
            0xFFF44336, 0xFF00BCD4, 0xFFFFEB3B, 0xFF795548,
            0xFF607D8B, 0xFFE91E63, 0xFF8BC34A, 0xFF03A9F4,
            0xFFFF5722, 0xFF673AB7, 0xFFCDDC39, 0xFF009688,
            0xFFFFC107, 0xFF3F51B5, 0xFF4DB6AC, 0xFFAED581
    };

    private static final int[] FACING_COLORS = {
            0xFFFF4444, 0xFF44FF44, 0xFF4444FF, 0xFFFFFF44,
            0xFFFF44FF, 0xFF44FFFF
    };

    private static final float[] RARITY_COLORS = {
            0.85f, 0.85f, 0.85f, 1.0f,
            0.3f, 0.8f, 0.3f, 1.0f,
            0.2f, 0.5f, 1.0f, 1.0f,
            0.7f, 0.2f, 0.9f, 1.0f,
            1.0f, 0.85f, 0.0f, 1.0f
    };

    public void setMode(HeatmapMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.typeColorMap.clear();
            this.typeColorIndex.set(0);
        }
    }

    public HeatmapMode getMode() {
        return mode;
    }

    public boolean isActive() {
        return mode != HeatmapMode.OFF;
    }

    @Override
    public void applyVertexConsumerWrapper(Level world, BlockPos pos, BlockState state,
                                            WorldSceneRenderer.VertexConsumerWrapper wrapperBuffer,
                                            RenderType layer, float partialTicks) {
        if (mode == HeatmapMode.OFF) return;

        switch (mode) {
            case BY_TYPE -> applyTypeColor(state, wrapperBuffer);
            case BY_RARITY -> applyRarityColor(state, wrapperBuffer);
            case BY_HEIGHT -> applyHeightColor(pos.getY(), wrapperBuffer);
            case BY_FACING -> applyFacingColor(state, wrapperBuffer);
        }
    }

    private void applyTypeColor(BlockState state, WorldSceneRenderer.VertexConsumerWrapper wrapper) {
        var block = state.getBlock();
        int color = typeColorMap.computeIfAbsent(block, b -> {
            int idx = typeColorIndex.getAndIncrement();
            return TYPE_PALETTE[idx % TYPE_PALETTE.length];
        });
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        wrapper.setColorMultiplier(r, g, b, 1.0f);
    }

    private void applyRarityColor(BlockState state, WorldSceneRenderer.VertexConsumerWrapper wrapper) {
        float speed = state.getBlock().defaultBlockState().getDestroySpeed(null, BlockPos.ZERO);
        int tier;
        if (speed < 0) {
            tier = 4;
        } else if (speed == 0) {
            tier = 0;
        } else if (speed <= 1.5f) {
            tier = 1;
        } else if (speed <= 5.0f) {
            tier = 2;
        } else if (speed <= 15.0f) {
            tier = 3;
        } else {
            tier = 4;
        }
        int offset = tier * 4;
        wrapper.setColorMultiplier(RARITY_COLORS[offset], RARITY_COLORS[offset + 1],
                RARITY_COLORS[offset + 2], RARITY_COLORS[offset + 3]);
    }

    private void applyHeightColor(int y, WorldSceneRenderer.VertexConsumerWrapper wrapper) {
        float range = Math.max(1, maxY - minY);
        float t = Math.clamp((y - minY) / range, 0.0f, 1.0f);
        float r, g, b;
        if (t < 0.25f) {
            float s = t / 0.25f;
            r = 0; g = s; b = 1;
        } else if (t < 0.5f) {
            float s = (t - 0.25f) / 0.25f;
            r = 0; g = 1; b = 1 - s;
        } else if (t < 0.75f) {
            float s = (t - 0.5f) / 0.25f;
            r = s; g = 1; b = 0;
        } else {
            float s = (t - 0.75f) / 0.25f;
            r = 1; g = 1 - s; b = 0;
        }
        wrapper.setColorMultiplier(r, g, b, 1.0f);
    }

    private void applyFacingColor(BlockState state, WorldSceneRenderer.VertexConsumerWrapper wrapper) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dirProp && prop.getName().equals("facing")) {
                var dir = state.getValue(dirProp);
                int idx = switch (dir) {
                    case NORTH -> 0;
                    case SOUTH -> 1;
                    case WEST -> 2;
                    case EAST -> 3;
                    case UP -> 4;
                    case DOWN -> 5;
                };
                int color = FACING_COLORS[idx];
                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;
                wrapper.setColorMultiplier(r, g, b, 1.0f);
                return;
            }
        }
        wrapper.setColorMultiplier(0.5f, 0.5f, 0.5f, 1.0f);
    }

    public void updateHeightRange(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
    }
}
