package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ViewportFactory {

    private static final Logger LOG = LoggerFactory.getLogger("EBE/Viewport");

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

    public static void loadFromModel(BuildingModel model) {
        if (currentScene == null) {
            LOG.error("loadFromModel: currentScene is null");
            return;
        }

        currentWorld = new TrackedDummyWorld();
        currentScene.createScene(currentWorld);

        int totalBlocksAdded = 0;
        for (var region : model.getRegions()) {
            int count = loadRegion(region);
            totalBlocksAdded += count;
            LOG.info("Loaded region '{}' (offset={},{},{} size={},{},{}) : {} non-air blocks",
                    region.getName(), region.getOffsetX(), region.getOffsetY(), region.getOffsetZ(),
                    region.getSizeX(), region.getSizeY(), region.getSizeZ(), count);
        }

        LOG.info("Total blocks added to world: {}", totalBlocksAdded);
        LOG.info("Filled blocks in world: {}", currentWorld.getFilledBlocks().size());

        refreshRenderedCore();

        if (!model.getRegions().isEmpty()) {
            var meta = model.getMetadata();
            LOG.info("Model metadata size: {}x{}x{}", meta.getSizeX(), meta.getSizeY(), meta.getSizeZ());
        }
    }

    private static int loadRegion(Region region) {
        var container = region.getBlocks();
        int count = 0;
        for (int y = 0; y < region.getSizeY(); y++) {
            for (int z = 0; z < region.getSizeZ(); z++) {
                for (int x = 0; x < region.getSizeX(); x++) {
                    var obj = container.get(x, y, z);
                    var blockState = resolveBlockState(obj);
                    if (blockState != null && !blockState.isAir()) {
                        int wx = x + region.getOffsetX();
                        int wy = y + region.getOffsetY();
                        int wz = z + region.getOffsetZ();
                        currentWorld.addBlock(new BlockPos(wx, wy, wz), new BlockInfo(blockState));
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static BlockState resolveBlockState(Object obj) {
        if (obj instanceof BlockState bs) return bs;
        if (obj instanceof String s) {
            if (s.isEmpty() || s.equals("minecraft:air") || s.equals("air")) {
                return Blocks.AIR.defaultBlockState();
            }
            try {
                var bracketIdx = s.indexOf('[');
                var idStr = bracketIdx >= 0 ? s.substring(0, bracketIdx) : s;
                var loc = ResourceLocation.parse(idStr);
                var block = BuiltInRegistries.BLOCK.getOptional(loc);
                if (block.isEmpty()) {
                    LOG.warn("Unknown block ID: {}", s);
                    return Blocks.AIR.defaultBlockState();
                }
                return block.get().defaultBlockState();
            } catch (Exception e) {
                LOG.warn("Failed to resolve block state: {}", s, e);
                return Blocks.AIR.defaultBlockState();
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static void handleBlockClick(BlockPos pos, net.minecraft.core.Direction face) {
        var state = EditorUI.getState();
        var tool = state.getActiveTool();
        switch (tool) {
            case SELECT -> {
                var blockState = currentWorld.getBlockState(pos);
                state.setSelectedBlock(pos.toShortString());
                state.setActiveBlockState(blockState);
                EditorUI.updateActiveBlockIndicator();
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
                EditorUI.updateActiveBlockIndicator();
            }
            case MEASURE -> state.setCursorPosition(pos.toShortString());
            case FILL -> {}
        }
    }

    public static void placeBlock(BlockPos pos, BlockState blockState) {
        if (currentWorld == null) return;
        currentWorld.addBlock(pos, new BlockInfo(blockState));
        refreshRenderedCore();

        var model = EditorUI.getSession().getModel();
        syncBlockToModel(model, pos, blockState);
        EditorUI.getSession().markDirty();
    }

    public static void deleteBlock(BlockPos pos) {
        if (currentWorld == null) return;
        currentWorld.removeBlock(pos);
        refreshRenderedCore();

        var model = EditorUI.getSession().getModel();
        syncBlockToModel(model, pos, Blocks.AIR.defaultBlockState());
        EditorUI.getSession().markDirty();
    }

    public static void replaceBlock(BlockPos pos, BlockState blockState) {
        if (currentWorld == null) return;
        currentWorld.removeBlock(pos);
        currentWorld.addBlock(pos, new BlockInfo(blockState));
        refreshRenderedCore();

        var model = EditorUI.getSession().getModel();
        syncBlockToModel(model, pos, blockState);
        EditorUI.getSession().markDirty();
    }

    private static void syncBlockToModel(BuildingModel model, BlockPos pos, BlockState blockState) {
        var region = findOrCreateRegion(model, pos);
        if (region != null) {
            var id = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
            region.setWorldBlock(pos.getX(), pos.getY(), pos.getZ(), id);
        }
    }

    private static Region findOrCreateRegion(BuildingModel model, BlockPos pos) {
        for (var region : model.getRegions()) {
            if (region.containsWorldPos(pos.getX(), pos.getY(), pos.getZ())) {
                return region;
            }
        }

        int minX = pos.getX() - 2;
        int minY = Math.max(0, pos.getY() - 2);
        int minZ = pos.getZ() - 2;
        int sizeX = 16;
        int sizeY = 16;
        int sizeZ = 16;

        var region = model.addRegion(sizeX, sizeY, sizeZ);
        var meta = model.getMetadata();
        meta.setSize(
            Math.max(meta.getSizeX(), minX + sizeX),
            Math.max(meta.getSizeY(), minY + sizeY),
            Math.max(meta.getSizeZ(), minZ + sizeZ)
        );
        return region;
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
        LOG.info("refreshRenderedCore: {} positions from filledBlocks", positions.size());
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
