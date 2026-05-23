package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.EBEKeyBindings;
import com.l1ght.ebe.client.compat.IrisCompat;
import com.l1ght.ebe.client.renderer.CachedIrisWorldSceneRenderer;
import com.l1ght.ebe.client.renderer.FastTrackedDummyWorld;
import com.l1ght.ebe.projection.compute.ComputedProjection;
import com.l1ght.ebe.client.renderer.HeatmapMode;
import com.l1ght.ebe.client.renderer.HeatmapRenderHook;
import com.l1ght.ebe.client.renderer.SectionedWorldSceneRenderer;
import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.client.scene.FBOWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import com.lowdragmc.lowdraglib2.client.utils.RenderUtils;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
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
    private static final int DELTA_OVERLAY_THRESHOLD = 256;
    private static final int DEFERRED_COMPILE_FRAMES = 60;
    private static final int DELTA_OVERLAY_FRAMES = 20;
    private static final int AUTO_MATERIAL_REFRESH_BLOCK_LIMIT = 100_000;
    private static final int SYNC_MODEL_LOAD_VOLUME_LIMIT = 250_000;
    private static final int SYNC_COMPUTED_LOAD_BLOCK_LIMIT = 80_000;
    private static final int PROGRESSIVE_LOAD_BLOCKS_PER_TICK = 4096;
    private static final long PROGRESSIVE_LOAD_NANOS_PER_TICK = 4_000_000L;
    private static final int IRIS_VIEWPORT_FBO_DEFAULT_SIZE = 1080;
    private static final int IRIS_VIEWPORT_FBO_MIN_SIZE = 64;
    private static final int IRIS_VIEWPORT_RESIZE_STABLE_FRAMES = 8;

    private static final List<PendingBlockDelta> pendingDeltas = new ArrayList<>();
    private static int framesSinceLastEdit = 0;
    private static boolean deferredCompileScheduled = false;
    private static int pendingDeltaOverlayFrames = 0;
    private static ProgressiveModelLoad progressiveLoad;
    private static ProgressiveComputedLoad progressiveComputedLoad;
    private static int progressiveStatusCooldown = 0;
    private static final List<BlockPos> progressiveLoadedScratch = new ArrayList<>(4096);
    private static boolean progressiveCoreAttached = false;

    private static class PendingBlockDelta {
        final BlockPos pos;
        final BlockState state;
        PendingBlockDelta(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }

    private static class ProgressiveModelLoad {
        final BuildingModel model;
        final boolean autoCamera;
        final int totalVolume;
        int regionIndex;
        int x;
        int y;
        int z;
        int visited;
        int added;

        ProgressiveModelLoad(BuildingModel model, boolean autoCamera) {
            this.model = model;
            this.autoCamera = autoCamera;
            int volume = 0;
            for (var region : model.getRegions()) {
                volume += region.getSizeX() * region.getSizeY() * region.getSizeZ();
            }
            this.totalVolume = Math.max(1, volume);
        }
    }

    private static class ProgressiveComputedLoad {
        final ComputedProjection computed;
        final boolean autoCamera;
        int batchIndex;
        int entryIndex;
        int visited;
        int added;

        ProgressiveComputedLoad(ComputedProjection computed, boolean autoCamera) {
            this.computed = computed;
            this.autoCamera = autoCamera;
        }
    }

    private static TrackedDummyWorld currentWorld;
    private static Scene currentScene;
    private static SectionedWorldSceneRenderer sectionedRenderer;
    private static final HeatmapRenderHook heatmapHook = new HeatmapRenderHook();
    private static boolean hasLoadedModel = false;
    private static boolean firstOpen = true;
    private static boolean shaderProbeLoggedForViewport = false;
    private static boolean shaderProbeSceneActive = false;
    private static boolean irisViewportPassActive = false;
    private static int irisViewportPassFrame = 0;
    private static boolean irisBeginProbeLogged = false;
    private static boolean irisFinalizeProbeLogged = false;
    private static int irisFboWidth = -1;
    private static int irisFboHeight = -1;
    private static int irisOffscreenBeginFailures = 0;
    private static boolean irisOffscreenDisabledForSession = false;
    private static int pendingIrisFboWidth = -1;
    private static int pendingIrisFboHeight = -1;
    private static int pendingIrisFboStableFrames = 0;
    private static int viewportInteractionFrames = 0;
    private static float lastViewportYaw = Float.NaN;
    private static float lastViewportPitch = Float.NaN;
    private static float lastViewportZoom = Float.NaN;
    private static Vector3f lastViewportCenter = null;

    private static float savedYaw = -135;
    private static float savedPitch = 25;
    private static float savedZoom = 8;
    private static Vector3f savedCenter = new Vector3f(3, 2, 3);

    private static Field sceneCoreField;
    private static Field sceneRendererField;
    private static Field rendererTraceField;
    private static boolean sceneReflectionInit = false;

    private static boolean dragSelecting = false;
    private static boolean penetrateSelect = false;
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
        stopIrisViewportPass("viewport-create");
        resetShaderProbeLogState();

        currentScene = new Scene();
        currentScene.layout(l -> l.flex(1));
        currentScene.setId("viewport");

        var session = EditorUI.getSession();
        boolean hasContent = hasLoadedModel || (session != null && !session.getModel().getRegions().isEmpty());

        boolean attachedCachedRenderer = tryAttachCachedSectionedRenderer();
        if (attachedCachedRenderer) {
            currentScene.setCameraYawAndPitch(savedYaw, savedPitch);
            currentScene.setZoom(savedZoom);
            currentScene.setCenter(savedCenter);
        } else if (!hasContent || session == null) {
            currentWorld = newViewportWorld();
            createSceneRenderer();
        }

        if (hasContent && session != null && !attachedCachedRenderer) {
            loadFromModel(session.getModel(), false);
            currentScene.setCameraYawAndPitch(savedYaw, savedPitch);
            currentScene.setZoom(savedZoom);
            currentScene.setCenter(savedCenter);
        } else if (!hasContent && firstOpen) {
            addDemoBlocks(currentWorld);
            refreshRenderedCore();
            currentScene.setCameraYawAndPitch(savedYaw, savedPitch);
            currentScene.setZoom(savedZoom);
            currentScene.setCenter(savedCenter);
        } else if (!hasContent) {
            refreshRenderedCore(true);
        }

        currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));
        setupSceneMiddleClick(currentScene);
        setupDragSelection(currentScene);

        currentScene.setRenderSelect(false);
        installViewportCallbacks();

        selectionRectOverlay = createSelectionRectOverlay();
        currentScene.addChild(selectionRectOverlay);

        logShaderProbe("viewport-created");
        firstOpen = false;
        return currentScene;
    }

    private static void installViewportCallbacks() {
        if (currentScene == null) {
            return;
        }
        currentScene.setBeforeWorldRender(ViewportFactory::handleBeforeWorldRender);
        currentScene.setAfterWorldRender(ViewportFactory::handleAfterWorldRender);
    }

    private static void handleBeforeWorldRender(Scene scene) {
        if (!shouldRunExperimentalIrisViewportPass()) {
            return;
        }
        if (currentScene == null || !(currentScene.getRenderer() instanceof FBOWorldSceneRenderer)) {
            return;
        }
        if (irisViewportPassActive) {
            IrisCompat.forceRestoreViewportShaderState("stale-before-world-render");
            irisViewportPassActive = false;
        }
        RenderTarget viewportTarget = getActiveViewportRenderTarget();
        if (viewportTarget == null) {
            LOG.warn("Iris viewport pass requested, but the active LDLib2 renderer is not an FBO renderer");
            return;
        }
        irisViewportPassActive = IrisCompat.beginOffscreenViewportShaderPass("before-world-render", viewportTarget);
        irisViewportPassFrame = 0;
        if (!irisViewportPassActive) {
            irisOffscreenBeginFailures++;
            if (irisOffscreenBeginFailures >= 3) {
                irisOffscreenDisabledForSession = true;
                LOG.warn("Disabling Iris offscreen viewport renderer for this editor session after {} failed begin attempts", irisOffscreenBeginFailures);
            }
            return;
        }
        irisOffscreenBeginFailures = 0;
        if (!irisBeginProbeLogged) {
            irisBeginProbeLogged = true;
            logShaderProbe("after-iris-begin");
        }
    }

    private static void handleAfterWorldRender(Scene scene) {
        var sel = EditorUI.getSelection();
        if (!sel.isEmpty()) {
            var poseStack = new PoseStack();
            for (var packed : sel.getAllPacked()) {
                int bx = com.l1ght.ebe.editor.selection.SelectionManager.unpackX(packed);
                int by = com.l1ght.ebe.editor.selection.SelectionManager.unpackY(packed);
                int bz = com.l1ght.ebe.editor.selection.SelectionManager.unpackZ(packed);
                RenderUtils.renderBlockOverLay(poseStack, new BlockPos(bx, by, bz), 0.15f, 0.35f, 0.85f, 1.005f);
            }
        }

        if (!pendingDeltas.isEmpty()) {
            renderPendingDeltasOverlay();
            pendingDeltaOverlayFrames++;
            if (pendingDeltaOverlayFrames >= DELTA_OVERLAY_FRAMES) {
                pendingDeltas.clear();
                pendingDeltaOverlayFrames = 0;
            }
        }

        if (deferredCompileScheduled) {
            framesSinceLastEdit++;
            if (framesSinceLastEdit >= DEFERRED_COMPILE_FRAMES) {
                deferredCompileScheduled = false;
                pendingDeltas.clear();
                if (currentScene != null) {
                    currentScene.needCompileCache();
                }
            }
        }

        if (!shaderProbeLoggedForViewport && shouldProbeViewportShaders()) {
            shaderProbeLoggedForViewport = true;
            logShaderProbe("after-world-render");
        }

        if (irisViewportPassActive) {
            IrisCompat.endOffscreenViewportShaderPass("after-world-render", shouldRunFullIrisViewportComposite());
            irisViewportPassActive = false;
            irisViewportPassFrame = 0;
            if (!irisFinalizeProbeLogged) {
                irisFinalizeProbeLogged = true;
                logShaderProbe("after-iris-finalize");
            }
        }
    }

    public static void onShaderModeChanged() {
        stopIrisViewportPass("settings-changed");
        resetShaderProbeLogState();
        rebuildCurrentRenderer(false);
        logShaderProbe("settings-changed");
    }

    public static void onViewportPerformanceSettingsChanged() {
        if (sectionedRenderer != null) {
            sectionedRenderer.needCompileCache();
        }
        if (currentScene != null && currentScene.getRenderer() instanceof CachedIrisWorldSceneRenderer cachedRenderer) {
            cachedRenderer.markDirty();
        }
    }

    public static void loadShaderProbeScene() {
        if (currentScene == null) {
            LOG.warn("Cannot load shader probe scene before viewport exists");
            return;
        }

        saveCameraState();

        var probeModel = new BuildingModel();
        probeModel.addRegion("shader_probe", 0, 0, 0, 5, 3, 3);
        probeModel.getMetadata().setName("shader_probe");
        probeModel.getMetadata().setSize(5, 3, 3);
        probeModel.setBlockAt(1, 1, 1, Blocks.STONE.defaultBlockState());
        probeModel.setBlockAt(2, 1, 1, Blocks.GLASS.defaultBlockState());
        probeModel.setBlockAt(3, 1, 1, Blocks.GLOWSTONE.defaultBlockState());

        loadFromModel(probeModel, true);
        shaderProbeSceneActive = true;
        savedYaw = -45;
        savedPitch = 20;
        savedZoom = 5;
        savedCenter = new Vector3f(2, 1, 1);
        currentScene.setCameraYawAndPitch(savedYaw, savedPitch);
        currentScene.setZoom(savedZoom);
        currentScene.setCenter(savedCenter);

        EditorUI.getSelection().clear();
        resetShaderProbeLogState();
        logShaderProbe("shader-probe-scene-loaded");
    }

    public static void loadFromModel(BuildingModel model) {
        loadFromModel(model, true);
    }

    public static void loadFromModelProgressive(BuildingModel model) {
        loadFromModelProgressive(model, true);
    }

    public static boolean shouldLoadModelProgressively(BuildingModel model) {
        if (model == null) return false;
        long volume = 0L;
        for (var region : model.getRegions()) {
            volume += (long) region.getSizeX() * region.getSizeY() * region.getSizeZ();
            if (volume > SYNC_MODEL_LOAD_VOLUME_LIMIT) return true;
        }
        return false;
    }

    public static boolean shouldLoadComputedProgressively(ComputedProjection computed) {
        return computed != null && computed.blockCount() > SYNC_COMPUTED_LOAD_BLOCK_LIMIT;
    }

    public static void loadFromModelProgressive(BuildingModel model, boolean autoCamera) {
        if (currentScene == null) {
            LOG.error("loadFromModelProgressive: currentScene is null");
            return;
        }

        cancelProgressiveLoad();
        releaseCachedSectionedRenderer("progressive-load-model");
        currentWorld = newViewportWorld();
        progressiveCoreAttached = false;
        createSceneRenderer();
        currentScene.useCacheBuffer(true);
        currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));

        if (autoCamera) {
            savedYaw = -135;
            savedPitch = 25;
        }

        hasLoadedModel = false;
        shaderProbeSceneActive = false;
        progressiveLoad = new ProgressiveModelLoad(model, autoCamera);
        progressiveStatusCooldown = 0;
        LOG.info("Progressive model load started: {} regions, volume={}", model.getRegions().size(), progressiveLoad.totalVolume);
    }

    public static void loadFromComputedProgressive(ComputedProjection computed) {
        loadFromComputedProgressive(computed, true);
    }

    public static void loadFromComputedProgressive(ComputedProjection computed, boolean autoCamera) {
        if (currentScene == null) {
            LOG.error("loadFromComputedProgressive: currentScene is null");
            return;
        }
        if (computed == null) {
            LOG.error("loadFromComputedProgressive: computed projection is null");
            return;
        }

        cancelProgressiveLoad();
        releaseCachedSectionedRenderer("progressive-load-computed");
        currentWorld = newViewportWorld();
        progressiveCoreAttached = false;
        createSceneRenderer();
        currentScene.useCacheBuffer(true);
        currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));

        if (autoCamera) {
            var fit = computed.getCameraFit();
            savedYaw = fit.getYaw();
            savedPitch = fit.getPitch();
            savedZoom = fit.getZoom();
            savedCenter = new Vector3f(fit.getCenterX(), fit.getCenterY(), fit.getCenterZ());
            currentScene.setCameraYawAndPitch(savedYaw, savedPitch);
            currentScene.setZoom(savedZoom);
            currentScene.setCenter(savedCenter);
        }

        hasLoadedModel = false;
        shaderProbeSceneActive = false;
        progressiveComputedLoad = new ProgressiveComputedLoad(computed, autoCamera);
        progressiveStatusCooldown = 0;
        LOG.info("Computed progressive load started: {} blocks, {} batches, volume={}",
                computed.blockCount(), computed.getViewportBatches().size(), computed.getTotalVolume());
    }

    public static void loadFromModel(BuildingModel model, boolean autoCamera) {
        if (currentScene == null) {
            LOG.error("loadFromModel: currentScene is null");
            return;
        }

        cancelProgressiveLoad();
        releaseCachedSectionedRenderer("load-model");
        currentWorld = newViewportWorld();

        int totalBlocksAdded = 0;
        for (var region : model.getRegions()) {
            int count = loadRegion(region);
            totalBlocksAdded += count;
            LOG.info("Loaded region '{}' : {} blocks", region.getName(), count);
        }

        LOG.info("Total blocks: {}", totalBlocksAdded);

        createSceneRenderer();
        currentScene.useCacheBuffer(totalBlocksAdded > FBO_THRESHOLD);

        refreshRenderedCore(autoCamera);
        currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));

        if (autoCamera) {
            savedYaw = -135;
            savedPitch = 25;
        }

        hasLoadedModel = true;
        shaderProbeSceneActive = false;
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

    public static void forceRestoreIrisState(String stage) {
        stopIrisViewportPass(stage);
    }

    public static void releaseViewportSession(String stage) {
        stopIrisViewportPass(stage);
        detachCachedSectionedRendererFromScene(stage);
        // Keep the sectioned renderer and dummy world alive across editor UI
        // close/open cycles. Scene is only the UI owner; the compiled section
        // VBOs are the expensive model cache.
        currentScene = null;
        selectionRectOverlay = null;
        shaderProbeSceneActive = false;
        irisFboWidth = -1;
        irisFboHeight = -1;
        resetShaderProbeLogState();
        IrisCompat.releaseOffscreenViewportResources();
    }

    public static void tickCamera() {
        if (currentScene == null) return;
        updateViewportInteractionState();
        tickProgressiveModelLoad();
        tickIrisViewportFailsafe();
        ensureIrisViewportFboSize();
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

        if (EBEKeyBindings.FLY_FORWARD.isKeyDown()) {
            center.add(-fx * speed, -fy * speed, -fz * speed);
            moved = true;
        }
        if (EBEKeyBindings.FLY_BACK.isKeyDown()) {
            center.add(fx * speed, fy * speed, fz * speed);
            moved = true;
        }
        if (EBEKeyBindings.FLY_LEFT.isKeyDown()) {
            center.add(-rx * speed, -ry * speed, -rz * speed);
            moved = true;
        }
        if (EBEKeyBindings.FLY_RIGHT.isKeyDown()) {
            center.add(rx * speed, ry * speed, rz * speed);
            moved = true;
        }
        if (EBEKeyBindings.FLY_UP.isKeyDown()) {
            center.add(0, speed, 0);
            moved = true;
        }
        if (EBEKeyBindings.FLY_DOWN.isKeyDown()) {
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
        var displayFilter = EditorUI.getState().getDisplayFilter();
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

                        if (!model.isLayerVisibleAt(region, wx, wy, wz)) continue;
                        if (!displayFilter.shouldDisplay(wx, wy, wz, obj)) continue;

                        addBlockToViewportWorld(new BlockPos(wx, wy, wz), blockState);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static TrackedDummyWorld newViewportWorld() {
        return new FastTrackedDummyWorld();
    }

    private static void addBlockToViewportWorld(BlockPos pos, BlockState state) {
        if (currentWorld == null || state == null || state.isAir()) {
            return;
        }
        var info = new BlockInfo(state);
        if (currentWorld instanceof FastTrackedDummyWorld fastWorld) {
            fastWorld.addBlockFast(pos, info);
        } else {
            currentWorld.addBlock(pos, info);
        }
    }

    public static boolean isProgressiveLoadActive() {
        return progressiveLoad != null || progressiveComputedLoad != null;
    }

    private static void cancelProgressiveLoad() {
        progressiveLoad = null;
        progressiveComputedLoad = null;
        progressiveStatusCooldown = 0;
        progressiveCoreAttached = false;
    }

    private static void tickProgressiveModelLoad() {
        if (progressiveComputedLoad != null) {
            tickProgressiveComputedLoad();
            return;
        }

        var load = progressiveLoad;
        if (load == null || currentWorld == null || currentScene == null) return;

        long deadline = System.nanoTime() + progressiveLoadBudgetNanos();
        int blocksThisTick = 0;
        progressiveLoadedScratch.clear();
        List<BlockPos> loadedBlocks = progressiveLoadedScratch;
        var core = getSceneCore();
        var regions = load.model.getRegions();
        var displayFilter = EditorUI.getState().getDisplayFilter();

        while (load.regionIndex < regions.size()
                && blocksThisTick < progressiveLoadBlockLimit()
                && System.nanoTime() < deadline) {
            var region = regions.get(load.regionIndex);
            if (load.y >= region.getSizeY()) {
                LOG.info("Progressive region loaded '{}' : visited={} added={}", region.getName(), load.visited, load.added);
                load.regionIndex++;
                load.x = 0;
                load.y = 0;
                load.z = 0;
                continue;
            }

            var obj = region.getBlocks().get(load.x, load.y, load.z);
            var blockState = resolveBlockState(obj);
            int wx = load.x + region.getOffsetX();
            int wy = load.y + region.getOffsetY();
            int wz = load.z + region.getOffsetZ();
            load.visited++;

            if (blockState != null && !blockState.isAir()
                    && load.model.isLayerVisibleAt(region, wx, wy, wz)
                    && displayFilter.shouldDisplay(wx, wy, wz, obj)) {
                var pos = new BlockPos(wx, wy, wz);
                addBlockToViewportWorld(pos, blockState);
                if (core != null) core.add(pos.immutable());
                loadedBlocks.add(pos.immutable());
                blocksThisTick++;
                load.added++;
            }

            load.x++;
            if (load.x >= region.getSizeX()) {
                load.x = 0;
                load.z++;
                if (load.z >= region.getSizeZ()) {
                    load.z = 0;
                    load.y++;
                }
            }
        }

        if (!loadedBlocks.isEmpty()) {
            scheduleProgressiveLoadCompile(loadedBlocks);
        }

        if (progressiveStatusCooldown-- <= 0) {
            progressiveStatusCooldown = 5;
            int percent = Math.min(99, (int) ((load.visited * 100L) / load.totalVolume));
            EditorUI.setStatus(net.minecraft.network.chat.Component.translatable(
                    "ebe.editor.loading.progress", percent, load.added));
        }

        if (load.regionIndex >= regions.size()) {
            progressiveLoad = null;
            currentScene.useCacheBuffer(load.added > FBO_THRESHOLD);
            if (sectionedRenderer != null) {
                sectionedRenderer.finishProgressiveLoad();
                sectionedRenderer.rebuildTileEntities();
            }
            if (core == null) {
                refreshRenderedCore(load.autoCamera);
            } else if (sectionedRenderer != null && heatmapHook.isActive()) {
                sectionedRenderer.needCompileCache();
            }
            currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));
            hasLoadedModel = true;
            shaderProbeSceneActive = false;
            if (load.added <= AUTO_MATERIAL_REFRESH_BLOCK_LIMIT) {
                EditorUI.refreshMaterialList();
            } else {
                EditorUI.markMaterialListStale();
            }
            EditorUI.updateStatusBar();
            LOG.info("Progressive model load finished: {} blocks, autoCamera={}", load.added, load.autoCamera);
        }
    }

    private static void tickProgressiveComputedLoad() {
        var load = progressiveComputedLoad;
        if (load == null || currentWorld == null || currentScene == null) return;

        long deadline = System.nanoTime() + progressiveLoadBudgetNanos();
        int blocksThisTick = 0;
        progressiveLoadedScratch.clear();
        List<BlockPos> loadedBlocks = progressiveLoadedScratch;
        var core = getSceneCore();
        var displayFilter = EditorUI.getState().getDisplayFilter();
        var batches = load.computed.getViewportBatches();

        while (load.batchIndex < batches.size()
                && blocksThisTick < progressiveLoadBlockLimit()
                && System.nanoTime() < deadline) {
            var batch = batches.get(load.batchIndex);
            var entries = batch.getEntries();
            if (load.entryIndex >= entries.size()) {
                load.batchIndex++;
                load.entryIndex = 0;
                continue;
            }

            var entry = entries.get(load.entryIndex++);
            var pos = entry.getPos();
            load.visited++;
            if (displayFilter.shouldDisplay(pos.getX(), pos.getY(), pos.getZ(), entry.getSource())) {
                addBlockToViewportWorld(pos, entry.getState());
                if (core != null) core.add(pos.immutable());
                loadedBlocks.add(pos.immutable());
                blocksThisTick++;
                load.added++;
            }
        }

        if (!loadedBlocks.isEmpty()) {
            scheduleProgressiveLoadCompile(loadedBlocks);
        }

        if (progressiveStatusCooldown-- <= 0) {
            progressiveStatusCooldown = 5;
            int total = Math.max(1, load.computed.blockCount());
            int percent = Math.min(99, (int) ((load.visited * 100L) / total));
            EditorUI.setStatus(net.minecraft.network.chat.Component.translatable(
                    "ebe.editor.loading.progress", percent, load.added));
        }

        if (load.batchIndex >= batches.size()) {
            progressiveComputedLoad = null;
            currentScene.useCacheBuffer(load.added > FBO_THRESHOLD);
            if (sectionedRenderer != null) {
                sectionedRenderer.finishProgressiveLoad();
                sectionedRenderer.rebuildTileEntities();
            }
            if (core == null) {
                refreshRenderedCore(load.autoCamera);
            } else if (sectionedRenderer != null && heatmapHook.isActive()) {
                sectionedRenderer.needCompileCache();
            }
            currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));
            hasLoadedModel = true;
            shaderProbeSceneActive = false;
            if (load.added <= AUTO_MATERIAL_REFRESH_BLOCK_LIMIT) {
                EditorUI.refreshMaterialList();
            } else {
                EditorUI.markMaterialListStale();
            }
            EditorUI.updateStatusBar();
            LOG.info("Computed progressive load finished: {} blocks, autoCamera={}", load.added, load.autoCamera);
        }
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
                    if (shaderProbeSceneActive) return;
                    var material = state.getActiveBlockState();
                    placeBlock(pos.relative(face), material != null ? material : Blocks.STONE.defaultBlockState(), history);
                }
                case DELETE -> {
                    if (shaderProbeSceneActive) return;
                    deleteBlock(pos, history);
                    selection.remove(pos.getX(), pos.getY(), pos.getZ());
                }
                case REPLACE -> {
                    if (shaderProbeSceneActive) return;
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

    public static void resetCamera() {
        if (currentScene == null) return;
        currentScene.setCenter(new Vector3f(0.5f, 0.5f, 0.5f));
        currentScene.setCameraYawAndPitch(-135, 25);
        currentScene.setZoom(8);
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

        if (currentScene != null) incrementalUpdateCore(pos, true);

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
            scheduleIncrementalCompile(pos, add);
        } else {
            refreshRenderedCore();
        }
    }

    private static void scheduleIncrementalCompile(BlockPos pos, boolean present) {
        if (currentScene == null) return;
        if (sectionedRenderer != null) {
            sectionedRenderer.applyBlockChange(pos, present);
        } else {
            currentScene.needCompileCache();
        }
    }

    private static void scheduleIncrementalCompile(Map<BlockPos, Boolean> changedBlocks) {
        if (currentScene == null || changedBlocks.isEmpty()) return;
        if (sectionedRenderer != null) {
            sectionedRenderer.applyBlockChanges(changedBlocks);
        } else {
            currentScene.needCompileCache();
        }
    }

    private static void scheduleProgressiveLoadCompile(List<BlockPos> loadedBlocks) {
        if (currentScene == null || loadedBlocks.isEmpty()) return;
        if (sectionedRenderer != null) {
            var core = getSceneCore();
            if (core != null && !progressiveCoreAttached) {
                sectionedRenderer.attachRenderedCore(core, createCombinedHook());
                progressiveCoreAttached = true;
            }
            sectionedRenderer.applyLoadedBlocks(loadedBlocks);
        } else {
            currentScene.needCompileCache();
        }
    }

    private static long progressiveLoadBudgetNanos() {
        boolean moving = viewportInteractionFrames > 0 && EBEClientConfig.viewportDegradeWhileMoving.get();
        double budgetMs = moving ? EBEClientConfig.viewportMovingLoadBudgetMs.get()
                : EBEClientConfig.viewportLoadBudgetMs.get();
        String mode = EBEClientConfig.viewportPerformanceMode.get();
        if ("quality".equals(mode) && moving) {
            budgetMs = EBEClientConfig.viewportLoadBudgetMs.get();
        } else if ("performance".equals(mode) && moving) {
            budgetMs = Math.min(budgetMs, 0.5D);
        }
        return Math.max(0L, (long) (budgetMs * 1_000_000L));
    }

    private static int progressiveLoadBlockLimit() {
        int configured = Math.max(128, EBEClientConfig.viewportLoadBlocksPerFrame.get());
        if (viewportInteractionFrames > 0 && EBEClientConfig.viewportDegradeWhileMoving.get()) {
            return Math.max(128, configured / 4);
        }
        return Math.min(PROGRESSIVE_LOAD_BLOCKS_PER_TICK, configured);
    }

    private static void updateViewportInteractionState() {
        if (currentScene == null) return;
        float yaw = currentScene.getRotationYaw();
        float pitch = currentScene.getRotationPitch();
        float zoom = currentScene.getZoom();
        Vector3f center = new Vector3f(currentScene.getCenter());
        boolean changed = Float.isNaN(lastViewportYaw)
                || Math.abs(yaw - lastViewportYaw) > 0.01F
                || Math.abs(pitch - lastViewportPitch) > 0.01F
                || Math.abs(zoom - lastViewportZoom) > 0.01F
                || lastViewportCenter == null
                || center.distanceSquared(lastViewportCenter) > 0.0001F;
        lastViewportYaw = yaw;
        lastViewportPitch = pitch;
        lastViewportZoom = zoom;
        lastViewportCenter = center;
        if (changed) {
            viewportInteractionFrames = 10;
        } else if (viewportInteractionFrames > 0) {
            viewportInteractionFrames--;
        }
    }

    private static void addPendingDelta(BlockPos pos, BlockState state) {
        pendingDeltas.add(new PendingBlockDelta(pos.immutable(), state));
        pendingDeltaOverlayFrames = 0;
    }

    private static void renderPendingDeltasOverlay() {
        if (pendingDeltas.isEmpty()) return;
        var poseStack = new PoseStack();
        for (var delta : pendingDeltas) {
            if (delta.state.isAir()) {
                RenderUtils.renderBlockOverLay(poseStack, delta.pos, 0.8f, 0.2f, 0.2f, 1.005f);
            } else {
                RenderUtils.renderBlockOverLay(poseStack, delta.pos, 0.2f, 0.8f, 0.2f, 1.005f);
            }
        }
    }

    public static void applyBlockDeltas(Object[][] snapshots) {
        if (currentWorld == null || currentScene == null) return;
        var core = getSceneCore();
        boolean smallEdit = snapshots.length < DELTA_OVERLAY_THRESHOLD;
        Map<BlockPos, Boolean> changedBlocks = new LinkedHashMap<>();

        for (var snapshot : snapshots) {
            int x = (int) snapshot[0];
            int y = (int) snapshot[1];
            int z = (int) snapshot[2];
            var newState = snapshot[4];
            var pos = new BlockPos(x, y, z);

            currentWorld.removeBlock(pos);

            BlockState resolvedBs = null;
            if (newState instanceof BlockState bs && !bs.isAir()) {
                resolvedBs = bs;
            } else if (newState instanceof String s && !s.isEmpty() && !s.equals("minecraft:air")) {
                resolvedBs = resolveBlockState(newState);
            }

            if (resolvedBs != null && !resolvedBs.isAir()) {
                currentWorld.addBlock(pos, new BlockInfo(resolvedBs));
                if (core != null) core.add(pos.immutable());
                changedBlocks.put(pos.immutable(), true);
                if (smallEdit) {
                    addPendingDelta(pos, resolvedBs);
                }
            } else {
                if (core != null) core.remove(pos);
                changedBlocks.put(pos.immutable(), false);
                if (smallEdit) {
                    addPendingDelta(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }

        if (sectionedRenderer != null) {
            scheduleIncrementalCompile(changedBlocks);
        } else {
            currentScene.needCompileCache();
            pendingDeltas.clear();
        }
    }

    public static void applyBlockDeltasFromModel(Object[][] snapshots) {
        if (currentWorld == null || currentScene == null) return;
        var core = getSceneCore();
        boolean smallEdit = snapshots.length < DELTA_OVERLAY_THRESHOLD;
        Map<BlockPos, Boolean> changedBlocks = new LinkedHashMap<>();

        for (var snapshot : snapshots) {
            int x = (int) snapshot[0];
            int y = (int) snapshot[1];
            int z = (int) snapshot[2];
            var pos = new BlockPos(x, y, z);
            var newState = snapshot[4];

            currentWorld.removeBlock(pos);

            BlockState resolvedBs = null;
            if (newState instanceof BlockState bs) {
                resolvedBs = bs;
            } else {
                resolvedBs = resolveBlockState(newState);
            }

            if (resolvedBs != null && !resolvedBs.isAir()) {
                currentWorld.addBlock(pos, new BlockInfo(resolvedBs));
                if (core != null) core.add(pos.immutable());
                changedBlocks.put(pos.immutable(), true);
                if (smallEdit) {
                    addPendingDelta(pos, resolvedBs);
                }
            } else {
                if (core != null) core.remove(pos);
                changedBlocks.put(pos.immutable(), false);
                if (smallEdit) {
                    addPendingDelta(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }

        if (sectionedRenderer != null) {
            scheduleIncrementalCompile(changedBlocks);
        } else if (smallEdit) {
            currentScene.needCompileCache();
        } else {
            if (core != null) {
                core.clear();
                currentWorld.getFilledBlocks().forEach(packed -> core.add(BlockPos.of(packed)));
            }
            currentScene.needCompileCache();
            pendingDeltas.clear();
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
        shaderProbeSceneActive = false;
        savedYaw = -135;
        savedPitch = 25;
        savedZoom = 8;
        savedCenter = new Vector3f(3, 2, 3);
        releaseCachedSectionedRenderer("clear-model");
        if (currentScene != null && currentWorld != null) {
            currentWorld = newViewportWorld();
            createSceneRenderer();
            refreshRenderedCore(true);
        }
    }

    public static void refreshFromModel(BuildingModel model) {
        if (currentScene == null) return;
        shaderProbeSceneActive = false;
        saveCameraState();
        releaseCachedSectionedRenderer("refresh-model");
        currentWorld = newViewportWorld();
        var displayFilter = EditorUI.getState().getDisplayFilter();
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
                            if (!model.isLayerVisibleAt(region, wx, wy, wz)) continue;
                            if (!displayFilter.shouldDisplay(wx, wy, wz, obj)) continue;
                            addBlockToViewportWorld(new BlockPos(wx, wy, wz), blockState);
                            totalBlocks++;
                        }
                    }
                }
            }
        }
        createSceneRenderer();
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
        ISceneBlockRenderHook hook = createCombinedHook();
        currentScene.setRenderedCore(positions, hook, autoCamera);
        if (sectionedRenderer != null && heatmapHook.isActive()) {
            sectionedRenderer.needCompileCache();
        }
    }

    private static ISceneBlockRenderHook createCombinedHook() {
        if (heatmapHook.isActive()) {
            return heatmapHook;
        }
        return createIrisRenderHookIfNeeded();
    }

    public static void setHeatmapMode(HeatmapMode mode) {
        heatmapHook.setMode(mode);
        if (currentScene != null) {
            refreshRenderedCore(false);
        }
    }

    public static HeatmapMode getHeatmapMode() {
        return heatmapHook.getMode();
    }

    private static ISceneBlockRenderHook createIrisRenderHookIfNeeded() {
        if (!shouldRunExperimentalIrisViewportPass()) {
            return null;
        }
        return new ISceneBlockRenderHook() {
            @Override
            public void apply(RenderType layer) {
                if (layer == RenderType.translucent()) {
                    IrisCompat.setViewportRenderPhase("TERRAIN_TRANSLUCENT");
                } else if (layer == RenderType.cutoutMipped()) {
                    IrisCompat.setViewportRenderPhase("TERRAIN_CUTOUT_MIPPED");
                } else if (layer == RenderType.cutout()) {
                    IrisCompat.setViewportRenderPhase("TERRAIN_CUTOUT");
                } else if (layer == RenderType.tripwire()) {
                    IrisCompat.setViewportRenderPhase("TRIPWIRE");
                } else {
                    IrisCompat.setViewportRenderPhase("TERRAIN_SOLID");
                }
            }
        };
    }

    private static boolean shouldProbeViewportShaders() {
        return IrisCompat.shouldAttemptViewportShaders(getViewportShaderMode());
    }

    private static boolean shouldRunExperimentalIrisViewportPass() {
        return shouldUseIrisOffscreenRenderer();
    }

    private static boolean shouldRunFullIrisViewportComposite() {
        return true;
    }

    private static String getViewportShaderMode() {
        var mode = EBEClientConfig.viewportShaderMode.get();
        if (!"off".equals(mode) && !"auto".equals(mode) && !"iris".equals(mode)) {
            LOG.warn("Unknown viewport shader mode '{}', falling back to off", mode);
            EBEClientConfig.viewportShaderMode.set("off");
            EBEClientConfig.SPEC.save();
            return "off";
        }
        return mode;
    }

    private static void rebuildCurrentRenderer(boolean autoCamera) {
        if (currentScene == null || currentWorld == null) {
            return;
        }
        saveCameraState();
        createSceneRenderer();
        refreshRenderedCore(autoCamera);
        currentScene.setCameraYawAndPitch(savedYaw, savedPitch);
        currentScene.setZoom(savedZoom);
        currentScene.setCenter(savedCenter);
        currentScene.setOnSelected((pos, face) -> handleBlockClick(pos, face));
    }

    private static void createSceneRenderer() {
        if (currentScene == null || currentWorld == null) {
            return;
        }

        boolean useIrisFbo = shouldUseIrisOffscreenRenderer();
        Size fboSize = useIrisFbo ? getIrisViewportFboSize() : null;
        boolean createIrisFbo = useIrisFbo && fboSize != null;
        if (createIrisFbo) {
            releaseCachedSectionedRenderer("switch-to-iris-fbo");
        }
        currentScene.createScene(currentWorld, createIrisFbo, fboSize);
        if (createIrisFbo) {
            sectionedRenderer = null;
            installCachedIrisRenderer(fboSize);
        } else {
            installSectionedRenderer();
        }
        if (createIrisFbo) {
            irisFboWidth = fboSize.width;
            irisFboHeight = fboSize.height;
            LOG.info("Created Iris offscreen viewport renderer: {}x{}", irisFboWidth, irisFboHeight);
        } else {
            irisFboWidth = -1;
            irisFboHeight = -1;
        }
        resetPendingIrisFboResize();
        installViewportCallbacks();
    }

    private static boolean tryAttachCachedSectionedRenderer() {
        if (currentScene == null || currentWorld == null || sectionedRenderer == null) {
            return false;
        }
        if (!hasLoadedModel || shouldUseIrisOffscreenRenderer() || !sectionedRenderer.isRenderingWorld(currentWorld)) {
            return false;
        }
        try {
            initSceneReflection();
            if (sceneRendererField == null) {
                return false;
            }

            currentScene.createScene(currentWorld, false, null);
            Object temporaryRenderer = sceneRendererField.get(currentScene);
            Set<BlockPos> core = getSceneCore();
            if (core == null) {
                return false;
            }
            core.clear();
            currentWorld.getFilledBlocks().forEach(packed -> core.add(BlockPos.of(packed)));

            configureSectionedRenderer(sectionedRenderer);
            sectionedRenderer.attachRenderedCore(core, createCombinedHook());
            sceneRendererField.set(currentScene, sectionedRenderer);
            if (temporaryRenderer instanceof WorldSceneRenderer worldSceneRenderer && temporaryRenderer != sectionedRenderer) {
                worldSceneRenderer.releaseResource();
            }
            currentScene.useCacheBuffer(true);
            installViewportCallbacks();
            LOG.info("Reattached cached SectionedWorldSceneRenderer: {} blocks", core.size());
            return true;
        } catch (Exception e) {
            LOG.warn("Failed to attach cached SectionedWorldSceneRenderer", e);
            return false;
        }
    }

    private static boolean isSectionedRendererAttachedToCurrentScene() {
        if (currentScene == null || sectionedRenderer == null) {
            return false;
        }
        try {
            initSceneReflection();
            return sceneRendererField != null && sceneRendererField.get(currentScene) == sectionedRenderer;
        } catch (Exception e) {
            return false;
        }
    }

    private static void configureSectionedRenderer(SectionedWorldSceneRenderer renderer) {
        renderer.useCacheBuffer(true);
        renderer.useOrtho(currentScene.isUseOrtho());
        renderer.setOnLookingAt(ray -> {});
        renderer.setAfterWorldRender(currentScene::renderBlockOverLay);
        renderer.setCameraLookAt(
                currentScene.getCenter(),
                currentScene.camZoom(),
                Math.toRadians(currentScene.getRotationYaw()),
                Math.toRadians(currentScene.getRotationPitch())
        );
        if (currentWorld.getParticleManager() != null) {
            renderer.setParticleManager(currentWorld.getParticleManager());
        }
    }

    private static void detachCachedSectionedRendererFromScene(String stage) {
        if (currentScene == null || sectionedRenderer == null) {
            return;
        }
        try {
            initSceneReflection();
            if (sceneRendererField != null && sceneRendererField.get(currentScene) == sectionedRenderer) {
                sceneRendererField.set(currentScene, null);
                LOG.info("Detached cached SectionedWorldSceneRenderer at {}", stage);
            }
        } catch (Exception e) {
            LOG.warn("Failed to detach cached SectionedWorldSceneRenderer at {}", stage, e);
        }
    }

    private static void releaseCachedSectionedRenderer(String stage) {
        var renderer = sectionedRenderer;
        if (renderer == null) {
            return;
        }
        detachCachedSectionedRendererFromScene(stage);
        sectionedRenderer = null;
        progressiveCoreAttached = false;
        renderer.releaseResource();
        LOG.info("Released cached SectionedWorldSceneRenderer at {}", stage);
    }

    private static void installSectionedRenderer() {
        if (currentScene == null || currentWorld == null) {
            return;
        }
        try {
            initSceneReflection();
            if (sceneRendererField == null) {
                return;
            }

            Object oldRenderer = sceneRendererField.get(currentScene);
            var renderer = new SectionedWorldSceneRenderer(currentWorld);
            configureSectionedRenderer(renderer);

            sceneRendererField.set(currentScene, renderer);
            sectionedRenderer = renderer;
            progressiveCoreAttached = false;
            if (oldRenderer instanceof WorldSceneRenderer worldSceneRenderer) {
                worldSceneRenderer.releaseResource();
            }
            LOG.info("Injected SectionedWorldSceneRenderer");
        } catch (Exception e) {
            sectionedRenderer = null;
            LOG.warn("Failed to install SectionedWorldSceneRenderer", e);
        }
    }

    private static void installCachedIrisRenderer(Size fboSize) {
        if (currentScene == null || currentWorld == null || fboSize == null) {
            return;
        }
        try {
            initSceneReflection();
            if (sceneRendererField == null) {
                return;
            }
            var renderer = new CachedIrisWorldSceneRenderer(currentWorld, fboSize.width, fboSize.height);
            renderer.useCacheBuffer(currentScene.isUseCache());
            renderer.useOrtho(currentScene.isUseOrtho());
            renderer.setAfterWorldRender(currentScene::renderBlockOverLay);
            renderer.setCameraLookAt(
                    currentScene.getCenter(),
                    currentScene.camZoom(),
                    Math.toRadians(currentScene.getRotationYaw()),
                    Math.toRadians(currentScene.getRotationPitch())
            );
            sceneRendererField.set(currentScene, renderer);
        } catch (Exception e) {
            LOG.warn("Failed to install cached Iris viewport renderer", e);
        }
    }

    private static boolean shouldUseIrisOffscreenRenderer() {
        if (!"iris".equals(getViewportShaderMode())) {
            return false;
        }
        if (irisOffscreenDisabledForSession) {
            return false;
        }
        var probe = IrisCompat.probe("offscreen-mode-check");
        return probe.irisLoaded() && probe.shaderPackInUse();
    }

    private static Size getIrisViewportFboSize() {
        int width = IRIS_VIEWPORT_FBO_DEFAULT_SIZE;
        int height = IRIS_VIEWPORT_FBO_DEFAULT_SIZE;
        if (currentScene != null) {
            int sceneWidth = Math.round(currentScene.getSizeWidth());
            int sceneHeight = Math.round(currentScene.getSizeHeight());
            if (sceneWidth >= IRIS_VIEWPORT_FBO_MIN_SIZE && sceneHeight >= IRIS_VIEWPORT_FBO_MIN_SIZE) {
                var window = Minecraft.getInstance().getWindow();
                width = Math.round(sceneWidth * (window.getWidth() / (float) window.getGuiScaledWidth()));
                height = Math.round(sceneHeight * (window.getHeight() / (float) window.getGuiScaledHeight()));
            }
        }
        return Size.of(Math.max(IRIS_VIEWPORT_FBO_MIN_SIZE, width), Math.max(IRIS_VIEWPORT_FBO_MIN_SIZE, height));
    }

    private static void ensureIrisViewportFboSize() {
        if (currentScene == null || currentWorld == null) {
            return;
        }

        boolean shouldUseIrisFbo = shouldUseIrisOffscreenRenderer();
        WorldSceneRenderer renderer = currentScene.getRenderer();
        if (!shouldUseIrisFbo) {
            if (renderer instanceof FBOWorldSceneRenderer) {
                rebuildCurrentRenderer(false);
            }
            return;
        }

        if (!(renderer instanceof FBOWorldSceneRenderer fboRenderer)) {
            rebuildCurrentRenderer(false);
            return;
        }

        Size size = getIrisViewportFboSize();
        if (size.width != irisFboWidth || size.height != irisFboHeight) {
            if (size.width != pendingIrisFboWidth || size.height != pendingIrisFboHeight) {
                pendingIrisFboWidth = size.width;
                pendingIrisFboHeight = size.height;
                pendingIrisFboStableFrames = 1;
                return;
            }
            pendingIrisFboStableFrames++;
            if (pendingIrisFboStableFrames < IRIS_VIEWPORT_RESIZE_STABLE_FRAMES) {
                return;
            }
            fboRenderer.setFBOSize(size.width, size.height);
            irisFboWidth = size.width;
            irisFboHeight = size.height;
            resetPendingIrisFboResize();
            LOG.info("Resized Iris offscreen viewport renderer: {}x{}", irisFboWidth, irisFboHeight);
        } else {
            resetPendingIrisFboResize();
        }
    }

    private static RenderTarget getActiveViewportRenderTarget() {
        if (currentScene == null) {
            return null;
        }
        WorldSceneRenderer renderer = currentScene.getRenderer();
        if (renderer instanceof FBOWorldSceneRenderer fboRenderer) {
            return fboRenderer.getFbo();
        }
        return null;
    }

    private static void resetShaderProbeLogState() {
        shaderProbeLoggedForViewport = false;
        irisBeginProbeLogged = false;
        irisFinalizeProbeLogged = false;
        irisOffscreenBeginFailures = 0;
        irisOffscreenDisabledForSession = false;
        resetPendingIrisFboResize();
    }

    private static void resetPendingIrisFboResize() {
        pendingIrisFboWidth = -1;
        pendingIrisFboHeight = -1;
        pendingIrisFboStableFrames = 0;
    }

    private static void tickIrisViewportFailsafe() {
        if (!irisViewportPassActive) {
            return;
        }
        irisViewportPassFrame++;
        if (irisViewportPassFrame > 1) {
            stopIrisViewportPass("tick-failsafe");
        }
    }

    private static void stopIrisViewportPass(String stage) {
        irisViewportPassActive = false;
        irisViewportPassFrame = 0;
        IrisCompat.forceRestoreViewportShaderState(stage);
    }

    private static void logShaderProbe(String stage) {
        var mode = getViewportShaderMode();
        var probe = IrisCompat.probe(stage);
        if ("off".equals(mode)) {
            LOG.info("Iris viewport probe [{}]: disabled by EBE setting", stage);
            return;
        }

        LOG.info(
                "Iris viewport probe [{}]: mode={}, irisLoaded={}, shadersEnabled={}, shaderPackInUse={}, pipelinePresent={}, pipeline={}, shaderOverrideActive={}, phase={}, compositeCandidate={}, error={}",
                stage,
                mode,
                probe.irisLoaded(),
                probe.shadersEnabled(),
                probe.shaderPackInUse(),
                probe.pipelinePresent(),
                probe.pipelineClassName(),
                probe.shaderOverrideActive(),
                probe.phaseName(),
                probe.shaderCompositeCandidate(),
                probe.error()
        );

        if ("iris".equals(mode) && !probe.irisLoaded()) {
            LOG.warn("Iris viewport shader mode is forced, but Iris is not loaded");
        } else if ("after-world-render".equals(stage) && probe.irisLoaded() && probe.shaderPackInUse() && !probe.shaderOverrideActive()) {
            LOG.warn("Iris shader pack is active, but LDLib2 viewport rendering has not entered Iris shader override at stage '{}'", stage);
        }
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
            long window = Minecraft.getInstance().getWindow().getWindow();
            boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            boolean ctrl = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (e.button == 1 && shift) {
                dragSelecting = true;
                penetrateSelect = true;
                dragStartX = (int) e.x;
                dragStartY = (int) e.y;
                dragCurrentX = dragStartX;
                dragCurrentY = dragStartY;
                updateSelectionRect();
                selectionRectOverlay.setDisplay(true);
                return;
            }

            if (e.button == 1 && !shift) {
                var tool = EditorUI.getState().getActiveTool();
                if (tool == EditorTool.SELECT) {
                    if (!sceneReflectionInit) initSceneReflection();
                    try {
                        var renderer = sceneRendererField.get(currentScene);
                        if (renderer == null) return;
                        Object traceResult = rendererTraceField != null ? rendererTraceField.get(renderer) : null;
                        if (traceResult instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS) {
                            var pos = bhr.getBlockPos();
                            var selection = EditorUI.getSelection();
                            selection.remove(pos.getX(), pos.getY(), pos.getZ());
                            EditorUI.getState().setSelectedCount(selection.size());
                            EditorUI.updateStatusBar();
                        }
                    } catch (Exception ex) {
                        // ignore
                    }
                }
                return;
            }

            if (e.button != 0) return;

            if (ctrl && !shift) {
                dragSelecting = false;
                return;
            }
            if (!shift) return;

            dragSelecting = true;
            penetrateSelect = false;
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

            selectBlocksInScreenRect(minX, minY, maxX, maxY, penetrateSelect);
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

    private static void selectBlocksInScreenRect(int minX, int minY, int maxX, int maxY, boolean penetrate) {
        if (currentWorld == null || currentScene == null) return;

        var selection = EditorUI.getSelection();
        selection.clear();
        var selectedPositions = new HashSet<Long>();

        int step = 4;
        for (int sy = minY; sy <= maxY; sy += step) {
            for (int sx = minX; sx <= maxX; sx += step) {
                if (penetrate) {
                    var positions = rayTraceAllBlocks(sx, sy);
                    for (var pos : positions) {
                        selectedPositions.add(com.l1ght.ebe.editor.selection.SelectionManager.packPos(pos.getX(), pos.getY(), pos.getZ()));
                    }
                } else {
                    var hitResult = rayTraceBlock(sx, sy);
                    if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
                        var pos = hitResult.getBlockPos();
                        selectedPositions.add(com.l1ght.ebe.editor.selection.SelectionManager.packPos(pos.getX(), pos.getY(), pos.getZ()));
                    }
                }
            }
        }

        for (long packed : selectedPositions) {
            int x = com.l1ght.ebe.editor.selection.SelectionManager.unpackX(packed);
            int y = com.l1ght.ebe.editor.selection.SelectionManager.unpackY(packed);
            int z = com.l1ght.ebe.editor.selection.SelectionManager.unpackZ(packed);
            selection.add(x, y, z);
        }

        EditorUI.getState().setSelectedCount(selection.size());
        EditorUI.updateStatusBar();
    }

    private static BlockHitResult rayTraceBlock(int screenX, int screenY) {
        if (currentScene == null || currentWorld == null) return null;
        int sceneX = (int) currentScene.getPositionX();
        int sceneY = (int) currentScene.getPositionY();
        int sceneW = (int) currentScene.getSizeWidth();
        int sceneH = (int) currentScene.getSizeHeight();
        int localX = screenX - sceneX;
        int localY = screenY - sceneY;
        if (localX < 0 || localX >= sceneW || localY < 0 || localY >= sceneH) return null;

        float fovRad = (float) Math.toRadians(60f);
        float aspect = sceneW / (float) sceneH;
        float yawRad = (float) Math.toRadians(currentScene.getRotationYaw());
        float pitchRad = (float) Math.toRadians(currentScene.getRotationPitch());
        float zoom = currentScene.getZoom();
        var center = currentScene.getCenter();

        float cp = (float) Math.cos(pitchRad);
        float fx = cp * (float) Math.cos(yawRad);
        float fy = (float) Math.sin(pitchRad);
        float fz = cp * (float) Math.sin(yawRad);

        var camPos = new Vec3(
                center.x + fx * zoom,
                center.y + fy * zoom,
                center.z + fz * zoom);

        float ndcX = (2.0f * localX / sceneW) - 1.0f;
        float ndcY = 1.0f - (2.0f * localY / sceneH);
        float tanHalfFov = (float) Math.tan(fovRad / 2);

        float rightX = (float) Math.sin(yawRad);
        float rightZ = -(float) Math.cos(yawRad);
        float upX = -(float) Math.sin(pitchRad) * (float) Math.cos(yawRad);
        float upY = (float) Math.cos(pitchRad);
        float upZ = -(float) Math.sin(pitchRad) * (float) Math.sin(yawRad);

        var rayDir = new Vec3(
                -fx + rightX * ndcX * tanHalfFov * aspect + upX * ndcY * tanHalfFov,
                -fy + upY * ndcY * tanHalfFov,
                -fz + rightZ * ndcX * tanHalfFov * aspect + upZ * ndcY * tanHalfFov
        ).normalize();

        var endPos = camPos.add(rayDir.scale(1000));

        try {
            return currentWorld.clip(new ClipContext(
                    camPos, endPos,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    Minecraft.getInstance().player));
        } catch (Exception e) {
            return null;
        }
    }

    private static List<BlockPos> rayTraceAllBlocks(int screenX, int screenY) {
        var result = new ArrayList<BlockPos>();
        if (currentScene == null) return result;
        int sceneX = (int) currentScene.getPositionX();
        int sceneY = (int) currentScene.getPositionY();
        int sceneW = (int) currentScene.getSizeWidth();
        int sceneH = (int) currentScene.getSizeHeight();
        int localX = screenX - sceneX;
        int localY = screenY - sceneY;
        if (localX < 0 || localX >= sceneW || localY < 0 || localY >= sceneH) return result;

        float fovRad = (float) Math.toRadians(60f);
        float aspect = sceneW / (float) sceneH;
        float yawRad = (float) Math.toRadians(currentScene.getRotationYaw());
        float pitchRad = (float) Math.toRadians(currentScene.getRotationPitch());
        float zoom = currentScene.getZoom();
        var center = currentScene.getCenter();

        float cp = (float) Math.cos(pitchRad);
        float fx = cp * (float) Math.cos(yawRad);
        float fy = (float) Math.sin(pitchRad);
        float fz = cp * (float) Math.sin(yawRad);

        var camPos = new Vec3(
                center.x + fx * zoom,
                center.y + fy * zoom,
                center.z + fz * zoom);

        float ndcX = (2.0f * localX / sceneW) - 1.0f;
        float ndcY = 1.0f - (2.0f * localY / sceneH);
        float tanHalfFov = (float) Math.tan(fovRad / 2);

        float rightX = (float) Math.sin(yawRad);
        float rightZ = -(float) Math.cos(yawRad);
        float upX = -(float) Math.sin(pitchRad) * (float) Math.cos(yawRad);
        float upY = (float) Math.cos(pitchRad);
        float upZ = -(float) Math.sin(pitchRad) * (float) Math.sin(yawRad);

        var rayDir = new Vec3(
                -fx + rightX * ndcX * tanHalfFov * aspect + upX * ndcY * tanHalfFov,
                -fy + upY * ndcY * tanHalfFov,
                -fz + rightZ * ndcX * tanHalfFov * aspect + upZ * ndcY * tanHalfFov
        ).normalize();

        var model = EditorUI.getSession().getModel();
        double t = 0;
        double maxDist = 1000;
        double stepSize = 0.5;
        var visited = new HashSet<Long>();

        while (t < maxDist) {
            var point = camPos.add(rayDir.scale(t));
            int bx = (int) Math.floor(point.x);
            int by = (int) Math.floor(point.y);
            int bz = (int) Math.floor(point.z);
            long packed = com.l1ght.ebe.editor.selection.SelectionManager.packPos(bx, by, bz);
            if (!visited.contains(packed)) {
                visited.add(packed);
                var block = model.getBlockAt(bx, by, bz);
                if (block != null) {
                    boolean isAir = (block instanceof BlockState bs && bs.isAir())
                            || (block instanceof String s && (s.isEmpty() || s.equals("minecraft:air")));
                    if (!isAir) {
                        result.add(new BlockPos(bx, by, bz));
                    }
                }
            }
            t += stepSize;
        }

        return result;
    }
}
