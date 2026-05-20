package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ViewportFactory {

    public static UIElement create3DViewport() {
        var scene = new Scene();
        scene.layout(l -> l.flex(1));
        scene.setId("viewport");

        var world = new TrackedDummyWorld();
        scene.createScene(world);

        addDemoBlocks(world);

        List<BlockPos> positions = new ArrayList<>();
        world.getFilledBlocks().forEach(packed -> positions.add(BlockPos.of(packed)));
        scene.setRenderedCore(positions);

        scene.setCameraYawAndPitch(-135, 25);
        scene.setZoom(8);
        scene.setCenter(new org.joml.Vector3f(3, 2, 3));

        scene.setOnSelected((pos, face) -> {
            EditorUI.getState().setSelectedBlock(pos.toShortString());
        });

        return scene;
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
