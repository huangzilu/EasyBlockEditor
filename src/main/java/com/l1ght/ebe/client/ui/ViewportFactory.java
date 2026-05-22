package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.EBEKeyMappings;
import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import com.lowdragmc.lowdraglib2.client.utils.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static Field sceneRendererField;
    private static Field rendererTraceField;
    private static boolean sceneReflectionInit = false;

    private static boolean dragSelecting = false;
    private static int dragStartX, dragStartY;
    private static int dragCurrentX, dragCurrentY;
    private static UIElement selectionRectOverlay;

    public static boolean isDragSelecting() { return dragSelecting; }

    private static void setSceneDraggable(boolean value) {
        if (currentScene == null) return;
        currentScene.setDraggable(value);
    }

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
                }
                if (field.getType().getName().contains("WorldSceneRenderer")) {
                    field.setAccessible(true);
                    sceneRendererField = field;
                    LOG.info("Found Scene.renderer field: {}", field.getName());
                }
            }
            if (sceneCoreField == null) LOG.warn("Could not find Scene.core field by type");
            if (sceneRendererField == null) LOG.warn("Could not find Scene.renderer field by type");
            else {
                for (var field : sceneRendererField.getType().getDeclaredFields()) {
                    if (field.getType().getName().contains("BlockHitResult")) {
                        field.setAccessible(true);
                        rendererTraceField = field;
                        LOG.info("Found WorldSceneRenderer.trace field: {}", field.getName());
                        break;
                    }
                }
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
        setupSceneMiddleClick(currentScene);
        setupDragSelection(currentScene);

        currentScene.setRenderSelect(false);
        currentScene.setAfterWorldRender(scene -> {
            var sel = EditorUI.getSelection();
            if (sel.isEmpty()) return;
            var poseStack = new PoseStack();
            for (var packed : sel.getAllPacked()) {
                int bx = com.l1ght.ebe.editor.selection.SelectionManager.unpackX(packed);
                int by = com.l1ght.ebe.editor.selection.SelectionManager.unpackY(packed);
                int bz = com.l1ght.ebe.editor.selection.SelectionManager.unpackZ(packed);
                RenderUtils.renderBlockOverLay(poseStack, new BlockPos(bx, by, bz), 0.2f, 0.5f, 1.0f, 1.005f);
            }
        });

        selectionRectOverlay = createSelectionRectOverlay();
        currentScene.addChild(selectionRectOverlay);

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
        if (EditorUI.isTextFieldFocused()) return;

        var mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        double yawDeg = currentScene.getRotationYaw();
        double pitchDeg = currentScene.getRotationPitch();
        float yawRad = (float) Math.toRadians(yawDeg);
        float pitchRad = (float) Math.toRadians(pitchDeg);
        float speed = EBEClientConfig.flightSpeed.get().floatValue();
        var center = new Vector3f(currentScene.getCenter());

        boolean shiftHeld = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        setSceneDraggable(!shiftHeld);

        float cp = (float) Math.cos(pitchRad);
        float fx = (float) (cp * Math.cos(yawRad));
        float fy = (float) Math.sin(pitchRad);
        float fz = (float) (cp * Math.sin(yawRad));

        float rx = fz;
        float ry = 0;
        float rz = -fx;

        boolean moved = false;

        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_W) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            center.add(-fx * speed, -fy * speed, -fz * speed);
            moved = true;
        }
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_S) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            center.add(fx * speed, fy * speed, fz * speed);
            moved = true;
        }
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_A) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            center.add(-rx * speed, -ry * speed, -rz * speed);
            moved = true;
        }
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_D) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            center.add(rx * speed, ry * speed, rz * speed);
            moved = true;
        }
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            center.add(0, speed, 0);
            moved = true;
        }
        if (org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            center.add(0, -speed, 0);
            moved = true;
        }

        if (moved) {
            currentScene.setCenter(center);
        }
    }

    private static int loadRegion(Region region) {
        var container = region.getBlocks();
        var model = EditorUI.getSession().getModel();
        var layer = model.getLayer(region.getLayerId());
        var displayFilter = EditorUI.getState().getDisplayFilter();
        boolean layerVisible = layer == null || layer.isVisible();
        float layerOpacity = layer != null ? layer.getOpacity() : 1.0f;
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

                        if (!layerVisible) continue;
                        if (!displayFilter.shouldDisplay(wx, wy, wz, obj)) continue;

                        currentWorld.addBlock(new BlockPos(wx, wy, wz), new BlockInfo(blockState));
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static BlockState resolveBlockStatePublic(Object obj) {
        return resolveBlockState(obj);
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
        var selection = EditorUI.getSelection();
        var history = EditorUI.getHistory();
        var mode = state.getMode();
        boolean isEditMode = mode == EditorMode.EDIT;

        if (isEditMode) {
            var model = EditorUI.getSession().getModel();
            if (!model.canEditAt(pos.getX(), pos.getY(), pos.getZ())) return;

            switch (tool) {
                case PLACE -> {
                    var material = state.getActiveBlockState();
                    placeBlock(pos.relative(face), material != null ? material : Blocks.STONE.defaultBlockState(), history);
                }
                case DELETE -> {
                    deleteBlock(pos, history);
                    selection.remove(pos.getX(), pos.getY(), pos.getZ());
                }
                case REPLACE -> {
                    var material = state.getActiveBlockState();
                    replaceBlock(pos, material != null ? material : Blocks.GLASS.defaultBlockState(), history);
                }
            }
            EditorUI.refreshHistoryList();
            EditorUI.refreshMaterialList();
        }

        switch (tool) {
            case SELECT -> {
                var blockState = currentWorld.getBlockState(pos);
                state.setInspectedBlockState(blockState);
                EditorUI.updateBlockInspection();
                EditorUI.refreshPropertiesPanel();

                long window = Minecraft.getInstance().getWindow().getWindow();
                boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

                if (ctrl && shift) {
                    selectAllSameType(blockState);
                } else if (ctrl) {
                    selection.toggle(pos.getX(), pos.getY(), pos.getZ());
                } else {
                    selection.clear();
                    selection.add(pos.getX(), pos.getY(), pos.getZ());
                }
                state.setSelectedCount(selection.size());
                state.setSelectedBlock(pos.toShortString());
            }
            case GRAB -> {
                var blockState = currentWorld.getBlockState(pos);
                state.setActiveBlockState(blockState);
                EditorUI.updateActiveBlockIndicator();
            }
            case MEASURE -> state.setCursorPosition(pos.toShortString());
            case FILL -> {
                selection.toggle(pos.getX(), pos.getY(), pos.getZ());
                state.setSelectedCount(selection.size());
            }
        }
        EditorUI.updateStatusBar();
    }

    public static void handleMiddleClick(BlockPos pos) {
        if (currentWorld == null) return;
        var state = EditorUI.getState();
        var blockState = currentWorld.getBlockState(pos);
        if (blockState != null && !blockState.isAir()) {
            state.setActiveBlockState(blockState);
            state.setCursorX(pos.getX());
            state.setCursorY(pos.getY());
            state.setCursorZ(pos.getZ());
            state.setCursorPosition(pos.toShortString());
            EditorUI.updateActiveBlockIndicator();
            EditorUI.updateBlockInspection();
            EditorUI.refreshPropertiesPanel();
            EditorUI.updateStatusBar();
        }
    }

    public static void setupSceneMiddleClick(Scene scene) {
        if (scene == null) return;
        scene.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 2) return;
            onSceneMiddleClick();
        });
    }

    private static void onSceneMiddleClick() {
        if (!sceneReflectionInit) initSceneReflection();
        if (currentScene == null || sceneRendererField == null) return;
        try {
            var renderer = sceneRendererField.get(currentScene);
            if (renderer == null) return;
            Object traceResult = null;
            if (rendererTraceField != null) {
                traceResult = rendererTraceField.get(renderer);
            }
            if (traceResult instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS) {
                handleMiddleClick(bhr.getBlockPos());
            }
        } catch (Exception ex) {
            LOG.debug("Middle-click hit-test failed", ex);
        }
    }

    public static void jumpToPosition(int x, int y, int z) {
        if (currentScene == null) return;
        currentScene.setCenter(new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f));
    }

    public static void placeBlock(BlockPos pos, BlockState blockState,
                                  com.l1ght.ebe.editor.history.HistoryManager history) {
        if (currentWorld == null) return;
        var oldState = currentWorld.getBlockState(pos);
        currentWorld.addBlock(pos, new BlockInfo(blockState));
        incrementalUpdateCore(pos, true);

        var model = EditorUI.getSession().getModel();
        syncBlockToModel(model, pos, blockState);
        EditorUI.getSession().markDirty();
        hasLoadedModel = true;

        if (history != null) {
            history.push(new com.l1ght.ebe.editor.history.HistoryEntry(
                    history.nextId(),
                    com.l1ght.ebe.editor.history.HistoryActionType.PLACE,
                    new Object[][]{{pos.getX(), pos.getY(), pos.getZ(), oldState, blockState}},
                    pos.getX(), pos.getY(), pos.getZ(), blockState, 1));
        }
    }

    public static void deleteBlock(BlockPos pos,
                                   com.l1ght.ebe.editor.history.HistoryManager history) {
        if (currentWorld == null) return;
        var oldState = currentWorld.getBlockState(pos);
        currentWorld.removeBlock(pos);
        incrementalUpdateCore(pos, false);

        var model = EditorUI.getSession().getModel();
        syncBlockToModel(model, pos, Blocks.AIR.defaultBlockState());
        EditorUI.getSession().markDirty();
        hasLoadedModel = true;

        if (history != null) {
            history.push(new com.l1ght.ebe.editor.history.HistoryEntry(
                    history.nextId(),
                    com.l1ght.ebe.editor.history.HistoryActionType.DELETE,
                    new Object[][]{{pos.getX(), pos.getY(), pos.getZ(), oldState, Blocks.AIR.defaultBlockState()}},
                    pos.getX(), pos.getY(), pos.getZ(), oldState, 1));
        }
    }

    public static void replaceBlock(BlockPos pos, BlockState blockState,
                                    com.l1ght.ebe.editor.history.HistoryManager history) {
        if (currentWorld == null) return;
        var oldState = currentWorld.getBlockState(pos);
        currentWorld.removeBlock(pos);
        currentWorld.addBlock(pos, new BlockInfo(blockState));

        var model = EditorUI.getSession().getModel();
        syncBlockToModel(model, pos, blockState);
        EditorUI.getSession().markDirty();
        hasLoadedModel = true;

        if (currentScene != null) currentScene.needCompileCache();

        if (history != null) {
            history.push(new com.l1ght.ebe.editor.history.HistoryEntry(
                    history.nextId(),
                    com.l1ght.ebe.editor.history.HistoryActionType.REPLACE,
                    new Object[][]{{pos.getX(), pos.getY(), pos.getZ(), oldState, blockState}},
                    pos.getX(), pos.getY(), pos.getZ(), blockState, 1));
        }
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
        saveCameraState();
        currentWorld = new TrackedDummyWorld();
        var displayFilter = EditorUI.getState().getDisplayFilter();
        int totalBlocks = 0;
        for (var region : model.getRegions()) {
            if (!model.isLayerVisible(region.getLayerId())) continue;
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = region.getBlocks().get(x, y, z);
                        var blockState = resolveBlockState(obj);
                        if (blockState != null && !blockState.isAir()) {
                            int wx = x + region.getOffsetX();
                            int wy = y + region.getOffsetY();
                            int wz = z + region.getOffsetZ();
                            if (!displayFilter.shouldDisplay(wx, wy, wz, obj)) continue;
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
        currentScene.setRenderedCore(positions, selectionRenderHook, autoCamera);
    }

    private static final com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook selectionRenderHook =
            new com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook() {
                @Override
                public void applyVertexConsumerWrapper(net.minecraft.world.level.Level world, BlockPos pos,
                                                        BlockState state,
                                                        com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer.VertexConsumerWrapper wrapperBuffer,
                                                        net.minecraft.client.renderer.RenderType layer, float partialTicks) {
                    var selection = EditorUI.getSelection();
                    if (selection.contains(pos.getX(), pos.getY(), pos.getZ())) {
                        wrapperBuffer.setColorMultiplier(0.5f, 0.7f, 1.0f, 0.85f);
                    }
                }
            };

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

    private static void selectAllSameType(BlockState targetType) {
        if (currentWorld == null) return;
        var selection = EditorUI.getSelection();
        var model = EditorUI.getSession().getModel();
        var targetBlock = targetType.getBlock();
        boolean nbtSensitive = selection.isNbtSensitive();
        var state = EditorUI.getState();
        var targetNbt = nbtSensitive ? model.getBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ()) : null;

        for (var region : model.getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = region.getBlocks().get(x, y, z);
                        if (obj == null) continue;
                        var bs = resolveBlockState(obj);
                        if (!bs.isAir() && bs.getBlock() == targetBlock) {
                            if (nbtSensitive) {
                                int wx = x + region.getOffsetX();
                                int wy = y + region.getOffsetY();
                                int wz = z + region.getOffsetZ();
                                var nbt = model.getBlockEntityNbt(wx, wy, wz);
                                if (!nbtEquals(targetNbt, nbt)) continue;
                            }
                            selection.add(x + region.getOffsetX(), y + region.getOffsetY(), z + region.getOffsetZ());
                        }
                    }
                }
            }
        }
    }

    private static boolean nbtEquals(net.minecraft.nbt.CompoundTag a, net.minecraft.nbt.CompoundTag b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private static UIElement createSelectionRectOverlay() {
        var container = new UIElement();
        container.setId("selectionRect");
        container.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).width(0).height(0));
        container.setDisplay(false);

        var top = new UIElement();
        top.setId("selRectTop");
        top.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).widthPercent(100).height(2));
        top.style(s -> s.background(Sprites.RECT_RD));
        container.addChild(top);

        var bottom = new UIElement();
        bottom.setId("selRectBottom");
        bottom.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(0).bottom(0).widthPercent(100).height(2));
        bottom.style(s -> s.background(Sprites.RECT_RD));
        container.addChild(bottom);

        var left = new UIElement();
        left.setId("selRectLeft");
        left.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).width(2).heightPercent(100));
        left.style(s -> s.background(Sprites.RECT_RD));
        container.addChild(left);

        var right = new UIElement();
        right.setId("selRectRight");
        right.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .right(0).top(0).width(2).heightPercent(100));
        right.style(s -> s.background(Sprites.RECT_RD));
        container.addChild(right);

        return container;
    }

    private static void setupDragSelection(Scene scene) {
        scene.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 0) return;
            long window = Minecraft.getInstance().getWindow().getWindow();
            boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (ctrl && !shift) {
                dragSelecting = false;
                return;
            }
            if (!shift) return;

            dragSelecting = true;

            dragStartX = (int) e.x;
            dragStartY = (int) e.y;
            dragCurrentX = dragStartX;
            dragCurrentY = dragStartY;
            updateSelectionRect();
            selectionRectOverlay.setDisplay(true);
        });

        scene.addEventListener(UIEvents.MOUSE_MOVE, e -> {
            if (!dragSelecting) return;
            dragCurrentX = (int) e.x;
            dragCurrentY = (int) e.y;
            updateSelectionRect();
        });

        scene.addEventListener(UIEvents.MOUSE_UP, e -> {
            if (!dragSelecting) return;
            dragSelecting = false;
            selectionRectOverlay.setDisplay(false);

            int minX = Math.min(dragStartX, dragCurrentX);
            int maxX = Math.max(dragStartX, dragCurrentX);
            int minY = Math.min(dragStartY, dragCurrentY);
            int maxY = Math.max(dragStartY, dragCurrentY);

            if (maxX - minX < 3 && maxY - minY < 3) {
                return;
            }

            selectBlocksInScreenRect(minX, minY, maxX, maxY);
        });
    }

    private static void updateSelectionRect() {
        if (currentScene == null) return;
        int scenePosX = (int) currentScene.getPositionX();
        int scenePosY = (int) currentScene.getPositionY();
        int x = Math.min(dragStartX, dragCurrentX) - scenePosX;
        int y = Math.min(dragStartY, dragCurrentY) - scenePosY;
        int w = Math.abs(dragCurrentX - dragStartX);
        int h = Math.abs(dragCurrentY - dragStartY);
        selectionRectOverlay.layout(l -> l.left(x).top(y).width(w).height(h));
    }

    private static void selectBlocksInScreenRect(int minX, int minY, int maxX, int maxY) {
        if (currentWorld == null || currentScene == null) return;

        float fov = (float) Math.toRadians(EBEClientConfig.editorFov.get());
        var window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getGuiScaledWidth() / (float) window.getGuiScaledHeight();
        float near = 0.1f, far = 1000f;

        float yawRad = (float) Math.toRadians(currentScene.getRotationYaw());
        float pitchRad = (float) Math.toRadians(currentScene.getRotationPitch());
        float zoom = currentScene.getZoom();
        var center = currentScene.getCenter();

        float cp = (float) Math.cos(pitchRad);
        float forwardX = (float) (cp * Math.cos(yawRad));
        float forwardY = (float) Math.sin(pitchRad);
        float forwardZ = (float) (cp * Math.sin(yawRad));
        var camPos = new Vector3f(
                center.x - forwardX * zoom,
                center.y - forwardY * zoom,
                center.z - forwardZ * zoom);

        var viewMatrix = new Matrix4f().lookAt(camPos, center, new Vector3f(0, 1, 0));
        var projMatrix = new Matrix4f().perspective(fov, aspect, near, far);
        var viewProj = new Matrix4f(projMatrix).mul(viewMatrix);

        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();

        int sceneOffX = (int) currentScene.getPositionX();
        int sceneOffY = (int) currentScene.getPositionY();
        float sceneW = currentScene.getSizeWidth();
        float sceneH = currentScene.getSizeHeight();

        int localMinX = minX - sceneOffX;
        int localMinY = minY - sceneOffY;
        int localMaxX = maxX - sceneOffX;
        int localMaxY = maxY - sceneOffY;

        var selection = EditorUI.getSelection();
        selection.clear();
        var model = EditorUI.getSession().getModel();

        for (var region : model.getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = region.getBlocks().get(x, y, z);
                        if (obj == null) continue;
                        boolean isAir = (obj instanceof BlockState bs && bs.isAir())
                                || (obj instanceof String s && (s.isEmpty() || s.equals("minecraft:air")));
                        if (isAir) continue;

                        int wx = x + region.getOffsetX();
                        int wy = y + region.getOffsetY();
                        int wz = z + region.getOffsetZ();

                        if (blockFaceIntersectsScreenRect(wx, wy, wz, viewProj, screenW, screenH,
                                localMinX, localMinY, localMaxX, localMaxY, sceneW, sceneH)) {
                            selection.add(wx, wy, wz);
                        }
                    }
                }
            }
        }

        var state = EditorUI.getState();
        state.setSelectedCount(selection.size());
        EditorUI.updateStatusBar();
    }

    private static boolean blockFaceIntersectsScreenRect(int wx, int wy, int wz, Matrix4f viewProj,
                                                          int screenW, int screenH,
                                                          int minX, int minY, int maxX, int maxY,
                                                          float sceneW, float sceneH) {
        float cx = wx + 0.5f, cy = wy + 0.5f, cz = wz + 0.5f;
        float[][] faceCenters = {
                {cx - 0.5f, cy, cz}, {cx + 0.5f, cy, cz},
                {cx, cy - 0.5f, cz}, {cx, cy + 0.5f, cz},
                {cx, cy, cz - 0.5f}, {cx, cy, cz + 0.5f}
        };
        for (var fc : faceCenters) {
            var pt = new Vector4f(fc[0], fc[1], fc[2], 1f);
            var clip = new Vector4f();
            pt.mul(viewProj, clip);
            if (clip.w <= 0) continue;
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            float sx = (ndcX + 1f) * 0.5f * sceneW;
            float sy = (1f - ndcY) * 0.5f * sceneH;
            if (sx >= minX && sx <= maxX && sy >= minY && sy <= maxY) {
                return true;
            }
        }
        return false;
    }

    private static int[] findBestBlockAtScreen(int sx, int sy, Matrix4f viewProj,
                                                BuildingModel model, int screenW, int screenH) {
        int[] best = null;
        float bestDepth = Float.MAX_VALUE;

        for (var region : model.getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = region.getBlocks().get(x, y, z);
                        if (obj == null) continue;
                        boolean isAir = (obj instanceof BlockState bs && bs.isAir())
                                || (obj instanceof String s && (s.isEmpty() || s.equals("minecraft:air")));
                        if (isAir) continue;

                        int wx = x + region.getOffsetX();
                        int wy = y + region.getOffsetY();
                        int wz = z + region.getOffsetZ();

                        var worldPos = new Vector4f(wx + 0.5f, wy + 0.5f, wz + 0.5f, 1f);
                        var clipPos = new Vector4f();
                        worldPos.mul(viewProj, clipPos);

                        if (clipPos.w <= 0) continue;
                        float ndcX = clipPos.x / clipPos.w;
                        float ndcY = clipPos.y / clipPos.w;

                        int psx = (int) ((ndcX + 1f) * 0.5f * screenW);
                        int psy = (int) ((1f - ndcY) * 0.5f * screenH);

                        if (psx == sx && psy == sy) {
                            float depth = clipPos.z / clipPos.w;
                            if (depth < bestDepth) {
                                bestDepth = depth;
                                best = new int[]{wx, wy, wz};
                            }
                        }
                    }
                }
            }
        }
        return best;
    }
}
