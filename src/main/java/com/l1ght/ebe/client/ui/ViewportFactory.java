package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.EBEKeyMappings;
import com.l1ght.ebe.config.EBEClientConfig;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class ViewportFactory {

    private static final Logger LOG = LoggerFactory.getLogger("EBE/Viewport");
    private static final int FBO_THRESHOLD = 5000;

    private static TrackedDummyWorld currentWorld;
    private static Scene currentScene;
    private static boolean hasLoadedModel = false;
    private static boolean firstOpen = true;

    private static float savedYaw = -135;
    private static float savedPitch = 25;
    private static float savedZoom = 8;
    private static Vector3f savedCenter = new Vector3f(3, 2, 3);

    private static Field sceneCoreField;
    private static boolean sceneReflectionInit = false;

    @SuppressWarnings("unchecked")
    private static Set<BlockPos> getSceneCore() {
        if (!sceneReflectionInit) initSceneReflection();
        if (sceneCoreField == null || currentScene == null) return null;
        try {
            return (Set<BlockPos>) sceneCoreField.get(currentScene);
        } catch (Exception e) {
            LOG.warn("Failed to get Scene.core", e);
            return null;
        }
    }

    private static void initSceneReflection() {
        if (sceneReflectionInit) return;
        try {
            for (var field : Scene.class.getDeclaredFields()) {
                if (java.util.Set.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    sceneCoreField = field;
                    LOG.info("Found Scene.core field: {}", field.getName());
                    break;
                }
            }
            if (sceneCoreField == null) {
                LOG.warn("Could not find Scene.core field by type");
            }
        } catch (Exception e) {
            LOG.warn("Scene reflection failed", e);
        }
        sceneReflectionInit = true;
    }

    public static UIElement create3DViewport() {
        currentWorld = new TrackedDummyWorld();

        currentScene = new Scene();
        currentScene.layout(l -> l.flex(1));
        currentScene.setId("viewport");

        currentScene.createScene(currentWorld);

        var session = EditorUI.getSession();
        boolean hasContent = hasLoadedModel || (session != null && !session.getModel().getRegions().isEmpty());

        if (hasContent && session != null) {
            loadFromModel(session.getModel(), false);
            currentScene.setCameraYawAndPitch(savedYaw, savedPitch);
            currentScene.setZoom(savedZoom);
            currentScene.setCenter(savedCenter);
        } else if (firstOpen) {
            addDemoBlocks(currentWorld);
            refreshRenderedCore();
            currentScene.setCameraYawAndPitch(savedYaw, savedPitch);
            currentScene.setZoom(savedZoom);
            currentScene.setCenter(savedCenter);
        } else {
            refreshRenderedCore(true);
        }

        currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));
        firstOpen = false;
        return currentScene;
    }

    public static void loadFromModel(BuildingModel model) {
        loadFromModel(model, true);
    }

    public static void loadFromModel(BuildingModel model, boolean autoCamera) {
        if (currentScene == null) {
            LOG.error("loadFromModel: currentScene is null");
            return;
        }

        currentWorld = new TrackedDummyWorld();

        int totalBlocksAdded = 0;
        for (var region : model.getRegions()) {
            int count = loadRegion(region);
            totalBlocksAdded += count;
            LOG.info("Loaded region '{}' : {} blocks", region.getName(), count);
        }

        LOG.info("Total blocks: {}", totalBlocksAdded);

        currentScene.createScene(currentWorld);
        currentScene.useCacheBuffer(totalBlocksAdded > FBO_THRESHOLD);

        refreshRenderedCore(autoCamera);
        currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));

        if (autoCamera) {
            savedYaw = -135;
            savedPitch = 25;
        }

        hasLoadedModel = true;
        LOG.info("Model loaded: {} blocks, autoCamera={}", totalBlocksAdded, autoCamera);
    }

    public static void saveCameraState() {
        if (currentScene == null) return;
        try {
            savedYaw = currentScene.getRotationYaw();
            savedPitch = currentScene.getRotationPitch();
            savedZoom = currentScene.getZoom();
            savedCenter = new Vector3f(currentScene.getCenter());
        } catch (Exception e) {
            LOG.warn("Failed to save camera state", e);
        }
    }

    public static void tickCamera() {
        if (currentScene == null) return;

        float yaw = (float) Math.toRadians(currentScene.getRotationYaw());
        float pitch = (float) Math.toRadians(currentScene.getRotationPitch());
        float speed = EBEClientConfig.flightSpeed.get().floatValue();
        var center = new Vector3f(currentScene.getCenter());

        float fx = (float) (Math.cos(pitch) * Math.cos(yaw));
        float fy = (float) Math.sin(pitch);
        float fz = (float) (Math.cos(pitch) * Math.sin(yaw));

        float rx = (float) -Math.sin(yaw);
        float rz = (float) Math.cos(yaw);

        if (EBEKeyMappings.FREE_FLIGHT_FORWARD.isDown()) {
            center.add(fx * speed, fy * speed, fz * speed);
        }
        if (EBEKeyMappings.FREE_FLIGHT_BACK.isDown()) {
            center.add(-fx * speed, -fy * speed, -fz * speed);
        }
        if (EBEKeyMappings.FREE_FLIGHT_LEFT.isDown()) {
            center.add(-rx * speed, 0, -rz * speed);
        }
        if (EBEKeyMappings.FREE_FLIGHT_RIGHT.isDown()) {
            center.add(rx * speed, 0, rz * speed);
        }
        if (EBEKeyMappings.FREE_FLIGHT_UP.isDown()) {
            center.add(0, speed, 0);
        }
        if (EBEKeyMappings.FREE_FLIGHT_DOWN.isDown()) {
            center.add(0, -speed, 0);
        }

        currentScene.setCenter(center);
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
                String idStr = s;
                String propsStr = null;

                int bracketIdx = s.indexOf('[');
                if (bracketIdx >= 0) {
                    idStr = s.substring(0, bracketIdx);
                    int endBracket = s.indexOf(']', bracketIdx);
                    if (endBracket > bracketIdx + 1) {
                        propsStr = s.substring(bracketIdx + 1, endBracket);
                    }
                }

                if (idStr.startsWith("Block{")) {
                    idStr = idStr.substring(6, idStr.length() - 1);
                }

                var loc = ResourceLocation.parse(idStr);
                var block = BuiltInRegistries.BLOCK.getOptional(loc);
                if (block.isEmpty()) {
                    return Blocks.STONE.defaultBlockState();
                }

                var state = block.get().defaultBlockState();
                if (propsStr != null) {
                    for (var pair : propsStr.split(",")) {
                        var kv = pair.split("=");
                        if (kv.length == 2) {
                            state = applyProperty(state, kv[0], kv[1]);
                        }
                    }
                }
                return state;
            } catch (Exception e) {
                return Blocks.STONE.defaultBlockState();
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static void handleBlockClick(BlockPos pos, net.minecraft.core.Direction face) {
        var state = EditorUI.getState();
        state.setCursorX(pos.getX());
        state.setCursorY(pos.getY());
        state.setCursorZ(pos.getZ());
        state.setCursorPosition(pos.toShortString());
        var tool = state.getActiveTool();
        switch (tool) {
            case SELECT -> {
                var blockState = currentWorld.getBlockState(pos);
                state.setSelectedBlock(pos.toShortString());
                state.setInspectedBlockState(blockState);
                EditorUI.updateBlockInspection();
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
                state.setActiveBlockState(blockState);
                EditorUI.updateActiveBlockIndicator();
            }
            case MEASURE -> state.setCursorPosition(pos.toShortString());
            case FILL -> {}
        }
        EditorUI.updateStatusBar();
    }

    public static void placeBlock(BlockPos pos, BlockState blockState) {
        if (currentWorld == null) return;
        currentWorld.addBlock(pos, new BlockInfo(blockState));
        incrementalUpdateCore(pos, true);

        var model = EditorUI.getSession().getModel();
        syncBlockToModel(model, pos, blockState);
        EditorUI.getSession().markDirty();
        hasLoadedModel = true;
    }

    public static void deleteBlock(BlockPos pos) {
        if (currentWorld == null) return;
        currentWorld.removeBlock(pos);
        incrementalUpdateCore(pos, false);

        var model = EditorUI.getSession().getModel();
        syncBlockToModel(model, pos, Blocks.AIR.defaultBlockState());
        EditorUI.getSession().markDirty();
        hasLoadedModel = true;
    }

    public static void replaceBlock(BlockPos pos, BlockState blockState) {
        if (currentWorld == null) return;
        currentWorld.removeBlock(pos);
        currentWorld.addBlock(pos, new BlockInfo(blockState));

        var model = EditorUI.getSession().getModel();
        syncBlockToModel(model, pos, blockState);
        EditorUI.getSession().markDirty();
        hasLoadedModel = true;

        if (currentScene != null) currentScene.needCompileCache();
    }

    private static void incrementalUpdateCore(BlockPos pos, boolean add) {
        var core = getSceneCore();
        if (core != null) {
            if (add) core.add(pos.immutable());
            else core.remove(pos);
            if (currentScene != null) currentScene.needCompileCache();
        } else {
            refreshRenderedCore();
        }
    }

    private static void syncBlockToModel(BuildingModel model, BlockPos pos, BlockState blockState) {
        var region = findOrCreateRegion(model, pos);
        if (region != null) {
            region.setWorldBlock(pos.getX(), pos.getY(), pos.getZ(), blockState);
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

    public static void clearModel() {
        hasLoadedModel = false;
        savedYaw = -135;
        savedPitch = 25;
        savedZoom = 8;
        savedCenter = new Vector3f(3, 2, 3);
        if (currentScene != null && currentWorld != null) {
            currentWorld = new TrackedDummyWorld();
            currentScene.createScene(currentWorld);
            refreshRenderedCore(true);
        }
    }

    public static void refreshFromModel(BuildingModel model) {
        if (currentScene == null) return;
        currentWorld = new TrackedDummyWorld();
        int totalBlocks = 0;
        for (var region : model.getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = region.getBlocks().get(x, y, z);
                        var blockState = resolveBlockState(obj);
                        if (blockState != null && !blockState.isAir()) {
                            int wx = x + region.getOffsetX();
                            int wy = y + region.getOffsetY();
                            int wz = z + region.getOffsetZ();
                            currentWorld.addBlock(new BlockPos(wx, wy, wz), new BlockInfo(blockState));
                            totalBlocks++;
                        }
                    }
                }
            }
        }
        currentScene.createScene(currentWorld);
        currentScene.useCacheBuffer(totalBlocks > FBO_THRESHOLD);
        refreshRenderedCore(false);
        currentScene.setCameraYawAndPitch(savedYaw, savedPitch);
        currentScene.setZoom(savedZoom);
        currentScene.setCenter(savedCenter);
        currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));
    }

    public static void refreshRenderedCore() {
        refreshRenderedCore(false);
    }

    public static void refreshRenderedCore(boolean autoCamera) {
        if (currentScene == null || currentWorld == null) return;
        List<BlockPos> positions = new ArrayList<>();
        currentWorld.getFilledBlocks().forEach(packed -> positions.add(BlockPos.of(packed)));
        currentScene.setRenderedCore(positions, null, autoCamera);
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

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, String propName, String propValue) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals(propName)) {
                var opt = ((Property<T>) prop).getValue(propValue);
                if (opt.isPresent()) {
                    return state.setValue((Property<T>) prop, opt.get());
                }
                break;
            }
        }
        return state;
    }
}
