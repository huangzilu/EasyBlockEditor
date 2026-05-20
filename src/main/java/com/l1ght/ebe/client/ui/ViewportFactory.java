package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ViewportFactory {

    private static TrackedDummyWorld currentWorld;
    private static Scene currentScene;

    public static UIElement create3DViewport() {
        currentWorld = new TrackedDummyWorld();

        currentScene = new Scene();
        currentScene.layout(l -> l.flex(1));
        currentScene.setId("viewport");

        currentScene.createScene(currentWorld);

        addDemoBlocks(currentWorld);
        refreshRenderedCore();

        currentScene.setCameraYawAndPitch(-135, 25);
        currentScene.setZoom(8);
        currentScene.setCenter(new org.joml.Vector3f(3, 2, 3));

        currentScene.setOnSelected((pos, face) -> {
            handleBlockClick(pos, face);
        });

        return currentScene;
    }

    private static void handleBlockClick(BlockPos pos, net.minecraft.core.Direction face) {
        var state = EditorUI.getState();
        var tool = state.getActiveTool();
        switch (tool) {
            case SELECT -> {
                var blockState = currentWorld.getBlockState(pos);
                state.setSelectedBlock(pos.toShortString());
                state.setActiveBlockState(blockState);
            }
            case PLACE -> {
                var material = state.getActiveBlockState();
                placeBlock(pos.relative(face), material != null ? material : Blocks.STONE.defaultBlockState());
            }
            case DELETE -> deleteBlock(pos);
            case REPLACE -> {
                var material = state.getActiveBlockState();
                replaceBlock(pos, material != null ? material : Blocks.GLASS.defaultBlockState());
            }
            case GRAB -> {
                var blockState = currentWorld.getBlockState(pos);
                state.setSelectedBlock(pos.toShortString());
                state.setActiveBlockState(blockState);
            }
            case MEASURE -> state.setCursorPosition(pos.toShortString());
            case FILL -> {}
        }
    }

    public static void placeBlock(BlockPos pos, BlockState state) {
        if (currentWorld == null) return;
        currentWorld.addBlock(pos, new BlockInfo(state));
        refreshRenderedCore();
        EditorUI.getSession().markDirty();
    }

    public static void deleteBlock(BlockPos pos) {
        if (currentWorld == null) return;
        currentWorld.removeBlock(pos);
        refreshRenderedCore();
        EditorUI.getSession().markDirty();
    }

    public static void replaceBlock(BlockPos pos, BlockState state) {
        if (currentWorld == null) return;
        currentWorld.removeBlock(pos);
        currentWorld.addBlock(pos, new BlockInfo(state));
        refreshRenderedCore();
        EditorUI.getSession().markDirty();
    }

    public static void clearAndLoad(TrackedDummyWorld world) {
        if (currentScene == null) return;
        currentWorld = world;
        currentScene.createScene(world);
        refreshRenderedCore();
    }

    public static void refreshRenderedCore() {
        if (currentScene == null || currentWorld == null) return;
        List<BlockPos> positions = new ArrayList<>();
        currentWorld.getFilledBlocks().forEach(packed -> positions.add(BlockPos.of(packed)));
        currentScene.setRenderedCore(positions);
    }

    private static void addDemoBlocks(TrackedDummyWorld world) {
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                world.addBlock(new BlockPos(x, 0, z), new BlockInfo(Blocks.STONE_BRICKS.defaultBlockState()));
            }
        }
        for (int x = 1; x < 6; x++) {
            for (int z = 1; z < 6; z++) {
                world.addBlock(new BlockPos(x, 1, z), new BlockInfo(Blocks.OAK_PLANKS.defaultBlockState()));
            }
        }
        for (int x = 1; x < 6; x++) {
            world.addBlock(new BlockPos(x, 2, 1), new BlockInfo(Blocks.OAK_LOG.defaultBlockState()));
            world.addBlock(new BlockPos(x, 2, 5), new BlockInfo(Blocks.OAK_LOG.defaultBlockState()));
        }
        for (int z = 2; z < 5; z++) {
            world.addBlock(new BlockPos(1, 2, z), new BlockInfo(Blocks.OAK_LOG.defaultBlockState()));
            world.addBlock(new BlockPos(5, 2, z), new BlockInfo(Blocks.OAK_LOG.defaultBlockState()));
        }
        world.addBlock(new BlockPos(3, 2, 3), new BlockInfo(Blocks.REDSTONE_LAMP.defaultBlockState()));
        world.addBlock(new BlockPos(3, 3, 3), new BlockInfo(Blocks.GLASS.defaultBlockState()));
        world.addBlock(new BlockPos(2, 1, 2), new BlockInfo(Blocks.CRAFTING_TABLE.defaultBlockState()));
        world.addBlock(new BlockPos(4, 1, 2), new BlockInfo(Blocks.FURNACE.defaultBlockState()));
        world.addBlock(new BlockPos(3, 1, 4), new BlockInfo(Blocks.CHEST.defaultBlockState()));
    }
}
