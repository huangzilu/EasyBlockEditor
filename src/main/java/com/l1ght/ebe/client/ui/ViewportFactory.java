package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
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
        var tool = EditorUI.getState().getActiveTool();
        switch (tool) {
            case SELECT -> EditorUI.getState().setSelectedBlock(pos.toShortString());
            case PLACE -> placeBlock(pos.relative(face), Blocks.STONE.defaultBlockState());
            case DELETE -> deleteBlock(pos);
            case REPLACE -> replaceBlock(pos, Blocks.GLASS.defaultBlockState());
            case GRAB -> EditorUI.getState().setSelectedBlock(pos.toShortString());
            case MEASURE -> EditorUI.getState().setCursorPosition(pos.toShortString());
            default -> {}
        }
    }

    public static void placeBlock(BlockPos pos, BlockState state) {
        if (currentWorld == null) return;
        currentWorld.setBlockAndUpdate(pos, state);
        refreshRenderedCore();
        EditorUI.getSession().markDirty();
    }

    public static void deleteBlock(BlockPos pos) {
        if (currentWorld == null) return;
        currentWorld.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        refreshRenderedCore();
        EditorUI.getSession().markDirty();
    }

    public static void replaceBlock(BlockPos pos, BlockState state) {
        if (currentWorld == null) return;
        currentWorld.setBlockAndUpdate(pos, state);
        refreshRenderedCore();
        EditorUI.getSession().markDirty();
    }

    public static void loadWorld(TrackedDummyWorld world) {
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
                world.setBlockAndUpdate(new BlockPos(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int x = 1; x < 6; x++) {
            for (int z = 1; z < 6; z++) {
                world.setBlockAndUpdate(new BlockPos(x, 1, z), Blocks.OAK_PLANKS.defaultBlockState());
            }
        }
        for (int x = 1; x < 6; x++) {
            world.setBlockAndUpdate(new BlockPos(x, 2, 1), Blocks.OAK_LOG.defaultBlockState());
            world.setBlockAndUpdate(new BlockPos(x, 2, 5), Blocks.OAK_LOG.defaultBlockState());
        }
        for (int z = 2; z < 5; z++) {
            world.setBlockAndUpdate(new BlockPos(1, 2, z), Blocks.OAK_LOG.defaultBlockState());
            world.setBlockAndUpdate(new BlockPos(5, 2, z), Blocks.OAK_LOG.defaultBlockState());
        }
        world.setBlockAndUpdate(new BlockPos(3, 2, 3), Blocks.REDSTONE_LAMP.defaultBlockState());
        world.setBlockAndUpdate(new BlockPos(3, 3, 3), Blocks.GLASS.defaultBlockState());
        world.setBlockAndUpdate(new BlockPos(2, 1, 2), Blocks.CRAFTING_TABLE.defaultBlockState());
        world.setBlockAndUpdate(new BlockPos(4, 1, 2), Blocks.FURNACE.defaultBlockState());
        world.setBlockAndUpdate(new BlockPos(3, 1, 4), Blocks.CHEST.defaultBlockState());
    }
}
