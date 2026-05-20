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
        scene.setZoom(5);
        scene.setCenter(new org.joml.Vector3f(2, 1, 2));

        return scene;
    }

    private static void addDemoBlocks(TrackedDummyWorld world) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                world.setBlockAndUpdate(new BlockPos(x, 0, z),
                        Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        world.setBlockAndUpdate(new BlockPos(2, 1, 2), Blocks.REDSTONE_LAMP.defaultBlockState());
        world.setBlockAndUpdate(new BlockPos(1, 1, 1), Blocks.OAK_PLANKS.defaultBlockState());
        world.setBlockAndUpdate(new BlockPos(3, 1, 3), Blocks.GLASS.defaultBlockState());
    }
}
