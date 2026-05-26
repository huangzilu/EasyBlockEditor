package com.l1ght.ebe.client.renderer;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.projection.ProjectionEntityTransforms;
import com.l1ght.ebe.projection.mega.ProjectionLodPyramid;
import com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.ISceneEntityRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.ImmediateWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.minecraft.world.level.block.RenderShape.INVISIBLE;

@OnlyIn(Dist.CLIENT)
public class SectionedWorldSceneRenderer extends ImmediateWorldSceneRenderer {

    private static final Logger LOG = LoggerFactory.getLogger("EBE/SectionedRenderer");
    private static final int SECTION_SIZE = 16;
    private static final int DEFAULT_SECTION_COMPILE_BATCH_SIZE = 6;
    private static final int MAX_IMMEDIATE_FALLBACK_BLOCKS = 4096;
    private static final int BALANCED_MOVING_SECTION_LIMIT = 384;
    private static final int PERFORMANCE_MOVING_SECTION_LIMIT = 192;
    private static final double CAMERA_EPSILON_SQR = 0.0001D;
    private static final List<RenderType> LAYERS = RenderType.chunkBufferLayers();
    private static final Map<RenderType, Integer> LAYER_INDICES = createLayerIndices();

    private static Field f_beforeWorldRender;
    private static Field f_afterWorldRender;
    private static Field f_beforeBatchEnd;
    private static Field f_sceneEntityRenderHook;
    private static Field f_endBatchLast;

    static {
        try {
            f_beforeWorldRender = WorldSceneRenderer.class.getDeclaredField("beforeWorldRender");
            f_beforeWorldRender.setAccessible(true);
            f_afterWorldRender = WorldSceneRenderer.class.getDeclaredField("afterWorldRender");
            f_afterWorldRender.setAccessible(true);
            f_beforeBatchEnd = WorldSceneRenderer.class.getDeclaredField("beforeBatchEnd");
            f_beforeBatchEnd.setAccessible(true);
            f_sceneEntityRenderHook = WorldSceneRenderer.class.getDeclaredField("sceneEntityRenderHook");
            f_sceneEntityRenderHook.setAccessible(true);
            f_endBatchLast = WorldSceneRenderer.class.getDeclaredField("endBatchLast");
            f_endBatchLast.setAccessible(true);
        } catch (Exception e) {
            LOG.error("Failed to init reflection fields", e);
        }
    }

    private static Map<RenderType, Integer> createLayerIndices() {
        var indices = new IdentityHashMap<RenderType, Integer>();
        for (int i = 0; i < LAYERS.size(); i++) {
            indices.put(LAYERS.get(i), i);
        }
        return indices;
    }

    private final Map<SectionPos, SectionData> sections = new LinkedHashMap<>();
    private final Map<SectionPos, LongArrayList> sectionBlocks = new LinkedHashMap<>();
    private final Set<SectionPos> dirtySections = new LinkedHashSet<>();
    private final Set<BlockPos> tileEntities = new LinkedHashSet<>();
    private ProjectionLodPyramid viewportLodPyramid = ProjectionLodPyramid.empty();
    private List<BlockState> viewportLodPalette = List.of();
    private HeatmapMode fastHeatmapMode = HeatmapMode.OFF;

    private volatile Thread compileThread;
    private final AtomicBoolean compiling = new AtomicBoolean(false);
    private volatile boolean needsFullRebuild = true;
    private Vector3f lastEyePos;
    private Vector3f lastLookAt;
    private int cameraMovingFrames;

    public record SectionPos(int x, int y, int z) {
        public static SectionPos fromBlock(BlockPos pos) {
            return new SectionPos(
                    Math.floorDiv(pos.getX(), SECTION_SIZE),
                    Math.floorDiv(pos.getY(), SECTION_SIZE),
                    Math.floorDiv(pos.getZ(), SECTION_SIZE)
            );
        }
    }

    public static class SectionData {
        public final VertexBuffer[] vertexBuffers;
        public final boolean[] hasData;
        public volatile boolean compiled = false;

        public SectionData() {
            vertexBuffers = new VertexBuffer[LAYERS.size()];
            hasData = new boolean[LAYERS.size()];
        }

        public void close() {
            for (var vb : vertexBuffers) {
                if (vb != null) vb.close();
            }
        }

        public VertexBuffer vertexBuffer(int layerIndex) {
            VertexBuffer vb = vertexBuffers[layerIndex];
            if (vb == null) {
                vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vertexBuffers[layerIndex] = vb;
            }
            return vb;
        }
    }

    public SectionedWorldSceneRenderer(Level world) {
        super(world);
        this.useCache = true;
    }

    @Override
    public WorldSceneRenderer useCacheBuffer(boolean useCache) {
        this.useCache = true;
        return this;
    }

    @Override
    public WorldSceneRenderer needCompileCache() {
        if (compileThread != null) {
            compileThread.interrupt();
            compileThread = null;
        }
        compiling.set(false);
        needsFullRebuild = true;
        return this;
    }

    public void markSectionDirty(BlockPos pos) {
        var sp = SectionPos.fromBlock(pos);
        markSectionDirty(sp);
    }

    private void markSectionDirty(SectionPos sp) {
        dirtySections.add(sp);
        var data = sections.get(sp);
        if (data != null) {
            data.compiled = false;
        }
    }

    public void applyBlockChange(BlockPos pos, boolean present) {
        updateSectionBlock(pos, present);
    }

    public void applyBlockChanges(Map<BlockPos, Boolean> changedBlocks) {
        if (changedBlocks == null || changedBlocks.isEmpty()) {
            return;
        }
        for (var entry : changedBlocks.entrySet()) {
            updateSectionBlock(entry.getKey(), entry.getValue());
        }
    }

    public void applyLoadedBlocks(Collection<BlockPos> loadedBlocks) {
        if (loadedBlocks == null || loadedBlocks.isEmpty()) {
            return;
        }
        Set<SectionPos> touchedSections = new HashSet<>();
        for (var pos : loadedBlocks) {
            var immutable = pos.immutable();
            var sp = SectionPos.fromBlock(immutable);
            addBlockToSection(sp, immutable);
            sections.computeIfAbsent(sp, ignored -> new SectionData());
            touchedSections.add(sp);
        }
        for (var sp : touchedSections) {
            markSectionDirty(sp);
        }
    }

    public void finishProgressiveLoad() {
        dirtySections.addAll(sectionBlocks.keySet());
        for (var sp : sectionBlocks.keySet()) {
            var data = sections.get(sp);
            if (data != null) {
                data.compiled = false;
            }
        }
    }

    public void rebuildTileEntities() {
        rebuildAllTileEntities();
    }

    public void setViewportLod(ProjectionLodPyramid pyramid, List<BlockState> palette) {
        this.viewportLodPyramid = pyramid == null ? ProjectionLodPyramid.empty() : pyramid;
        this.viewportLodPalette = palette == null ? List.of() : List.copyOf(palette);
    }

    public void clearViewportLod() {
        this.viewportLodPyramid = ProjectionLodPyramid.empty();
        this.viewportLodPalette = List.of();
    }

    public void setFastHeatmapMode(HeatmapMode mode) {
        this.fastHeatmapMode = mode == null ? HeatmapMode.OFF : mode;
    }

    public int sectionCount() {
        return sectionBlocks.size();
    }

    public boolean isRenderingWorld(Level targetWorld) {
        return this.world == targetWorld;
    }

    public void attachRenderedCore(Collection<BlockPos> blocks, ISceneBlockRenderHook renderHook) {
        renderedBlocksMap.clear();
        if (blocks != null) {
            renderedBlocksMap.put(blocks, renderHook);
        }
    }

    public void updateRenderHook(ISceneBlockRenderHook renderHook, boolean recompile) {
        if (renderedBlocksMap.isEmpty()) {
            return;
        }
        var blocks = new ArrayList<>(renderedBlocksMap.keySet());
        renderedBlocksMap.clear();
        for (var blockSet : blocks) {
            renderedBlocksMap.put(blockSet, renderHook);
        }
        if (recompile) {
            dirtySections.addAll(sectionBlocks.keySet());
            for (var data : sections.values()) {
                data.compiled = false;
            }
        }
    }

    private void updateSectionBlock(BlockPos pos, boolean added) {
        var sp = SectionPos.fromBlock(pos);
        if (added) {
            addBlockToSection(sp, pos.immutable());
            sections.computeIfAbsent(sp, k -> new SectionData());
        } else {
            var blocks = sectionBlocks.get(sp);
            if (blocks != null) {
                blocks.rem(pos.asLong());
                if (blocks.isEmpty()) {
                    sectionBlocks.remove(sp);
                    var data = sections.remove(sp);
                    if (data != null) data.close();
                }
            }
        }
        updateTileEntityAt(pos);
        markAffectedSectionsDirty(sp);
    }

    private void markAffectedSectionsDirty(SectionPos center) {
        markSectionDirty(center);
        markSectionDirty(new SectionPos(center.x() + 1, center.y(), center.z()));
        markSectionDirty(new SectionPos(center.x() - 1, center.y(), center.z()));
        markSectionDirty(new SectionPos(center.x(), center.y() + 1, center.z()));
        markSectionDirty(new SectionPos(center.x(), center.y() - 1, center.z()));
        markSectionDirty(new SectionPos(center.x(), center.y(), center.z() + 1));
        markSectionDirty(new SectionPos(center.x(), center.y(), center.z() - 1));
    }

    private void updateTileEntityAt(BlockPos pos) {
        tileEntities.remove(pos);
        var mc = Minecraft.getInstance();
        BlockEntity tile = world.getBlockEntity(pos);
        if (tile != null && mc.getBlockEntityRenderDispatcher().getRenderer(tile) != null) {
            tileEntities.add(pos.immutable());
        }
    }

    @Override
    public WorldSceneRenderer deleteCacheBuffer() {
        if (compileThread != null) {
            compileThread.interrupt();
            compileThread = null;
        }
        compiling.set(false);
        for (var data : sections.values()) {
            data.close();
        }
        sections.clear();
        sectionBlocks.clear();
        dirtySections.clear();
        tileEntities.clear();
        needsFullRebuild = true;
        super.deleteCacheBuffer();
        return this;
    }

    @Override
    public WorldSceneRenderer addRenderedBlocks(Collection<BlockPos> blocks, ISceneBlockRenderHook renderHook) {
        super.addRenderedBlocks(blocks, renderHook);
        if (blocks != null && !blocks.isEmpty()) {
            for (var pos : blocks) {
                var sp = SectionPos.fromBlock(pos);
                addBlockToSection(sp, pos.immutable());
                sections.computeIfAbsent(sp, k -> new SectionData());
                markSectionDirty(sp);
            }
            rebuildAllTileEntities();
        }
        return this;
    }

    @Override
    public WorldSceneRenderer removeRenderedBlocks(Collection<BlockPos> blocks) {
        if (blocks != null && !blocks.isEmpty()) {
            for (var pos : blocks) {
                var sp = SectionPos.fromBlock(pos);
                var sectionList = sectionBlocks.get(sp);
                if (sectionList != null) {
                    sectionList.rem(pos.asLong());
                    if (sectionList.isEmpty()) {
                        sectionBlocks.remove(sp);
                        var data = sections.remove(sp);
                        if (data != null) data.close();
                    } else {
                        markSectionDirty(sp);
                    }
                }
                tileEntities.remove(pos);
            }
        }
        return super.removeRenderedBlocks(blocks);
    }

    @Override
    public WorldSceneRenderer removeAllRenderedBlocks() {
        sectionBlocks.clear();
        dirtySections.clear();
        tileEntities.clear();
        for (var data : sections.values()) {
            data.close();
        }
        sections.clear();
        return super.removeAllRenderedBlocks();
    }

    @Override
    public boolean isCompiling() {
        return compiling.get();
    }

    private void rebuildSectionBlocks() {
        sectionBlocks.clear();
        for (var entry : renderedBlocksMap.entrySet()) {
            for (var pos : entry.getKey()) {
                var sp = SectionPos.fromBlock(pos);
                addBlockToSection(sp, pos.immutable());
            }
        }

        Set<SectionPos> activeSections = new HashSet<>(sectionBlocks.keySet());
        sections.entrySet().removeIf(e -> {
            if (!activeSections.contains(e.getKey())) {
                e.getValue().close();
                return true;
            }
            return false;
        });
        for (var sp : activeSections) {
            sections.computeIfAbsent(sp, k -> new SectionData());
        }

        dirtySections.clear();
        dirtySections.addAll(activeSections);
        for (var data : sections.values()) {
            data.compiled = false;
        }
        rebuildAllTileEntities();
        needsFullRebuild = false;
    }

    private LongArrayList newSectionBlockList() {
        return new LongArrayList();
    }

    private void addBlockToSection(SectionPos sp, BlockPos pos) {
        var blocks = sectionBlocks.computeIfAbsent(sp, ignored -> newSectionBlockList());
        long packed = pos.asLong();
        if (blocks.isEmpty() || !blocks.contains(packed)) {
            blocks.add(packed);
        }
    }

    private void compileDirtySections(Vector3f eyePos, Vector3f focusPos, boolean cameraMoving) {
        if (needsFullRebuild) {
            rebuildSectionBlocks();
        }

        if (dirtySections.isEmpty() || compiling.get()) return;

        long budgetNanos = compileBudgetNanos(cameraMoving);
        if (budgetNanos <= 0L) {
            return;
        }
        long deadline = System.nanoTime() + budgetNanos;

        List<SectionPos> toCompile = selectDirtySectionsForCompile(eyePos, focusPos, deadline, cameraMoving);
        if (toCompile.isEmpty()) return;

        dirtySections.removeAll(toCompile);

        compiling.set(true);
        try {
            ModelBlockRenderer.enableCaching();
            var mc = Minecraft.getInstance();
            var brd = mc.getBlockRenderer();
            var randomSource = RandomSource.createNewThreadLocalInstance();

            for (var sp : toCompile) {
                if (System.nanoTime() > deadline) {
                    dirtySections.add(sp);
                    continue;
                }
                compileSection(sp, brd, randomSource);
            }

            rebuildTileEntitiesForSections(toCompile);
        } catch (Exception e) {
            LOG.warn("Section compilation error", e);
        } finally {
            ModelBlockRenderer.clearCache();
            compiling.set(false);
            compileThread = null;
        }
    }

    private void compileSection(SectionPos sp, BlockRenderDispatcher brd, RandomSource randomSource) {
        var data = sections.get(sp);
        if (data == null) return;

        var blocks = sectionBlocks.get(sp);
        if (blocks == null || blocks.isEmpty()) {
            for (int i = 0; i < LAYERS.size(); i++) {
                data.hasData[i] = false;
            }
            data.compiled = true;
            return;
        }

        BufferBuilder[] builders = new BufferBuilder[LAYERS.size()];
        ByteBufferBuilder[] byteBuilders = new ByteBufferBuilder[LAYERS.size()];
        VertexConsumerWrapper[] wrappers = new VertexConsumerWrapper[LAYERS.size()];

        boolean compileFailed = false;
        try {
            PoseStack poseStack = new PoseStack();
            ISceneBlockRenderHook renderHook = currentRenderHook();
            for (long packed : blocks) {
                if (Thread.interrupted()) return;
                var pos = BlockPos.of(packed);
                var state = world.getBlockState(pos);
                if (state == null || state.isAir()) continue;

                var fluidState = state.getFluidState();
                var block = state.getBlock();
                if (block == Blocks.AIR) continue;

                if (state.getRenderShape() != INVISIBLE) {
                    var model = brd.getBlockModel(state);
                    var modelData = world.getModelData(pos);
                    modelData = model.getModelData(world, pos, state, modelData);
                    long seed = state.getSeed(pos);
                    randomSource.setSeed(seed);
                    var renderTypes = model.getRenderTypes(state, randomSource, modelData);

                    for (int i = 0; i < LAYERS.size(); i++) {
                        RenderType layer = LAYERS.get(i);
                        if (!renderTypes.contains(layer)) {
                            continue;
                        }
                        var wrapper = wrapperForLayer(i, builders, byteBuilders, wrappers);
                        randomSource.setSeed(seed);
                        poseStack.pushPose();
                        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                        if (renderHook != null) {
                            renderHook.applyVertexConsumerWrapper(world, pos, state, wrapper, layer, 0);
                        }
                        brd.renderBatched(state, pos, world, poseStack, wrapper, false, randomSource, modelData, layer);
                        poseStack.popPose();
                        wrapper.clearOffset();
                        wrapper.clearColor();
                    }
                }

                if (!fluidState.isEmpty()) {
                    RenderType fluidLayer = ItemBlockRenderTypes.getRenderLayer(fluidState);
                    Integer layerIndex = LAYER_INDICES.get(fluidLayer);
                    if (layerIndex != null) {
                        var wrapper = wrapperForLayer(layerIndex, builders, byteBuilders, wrappers);
                        wrapper.addOffset(pos.getX() - (pos.getX() & 15), pos.getY() - (pos.getY() & 15), pos.getZ() - (pos.getZ() & 15));
                        if (renderHook != null) {
                            renderHook.applyVertexConsumerWrapper(world, pos, state, wrapper, fluidLayer, 0);
                        }
                        brd.renderLiquid(pos, world, wrapper, state, fluidState);
                        wrapper.clearOffset();
                        wrapper.clearColor();
                    }
                }
            }
        } catch (Exception e) {
            compileFailed = true;
            LOG.warn("Failed to compile section {}", sp, e);
            for (int i = 0; i < LAYERS.size(); i++) {
                data.hasData[i] = false;
            }
        }

        for (int i = 0; i < LAYERS.size(); i++) {
            try {
                if (builders[i] == null) {
                    data.hasData[i] = false;
                    continue;
                }
                MeshData meshData = builders[i].build();
                if (compileFailed) {
                    data.hasData[i] = false;
                } else if (meshData != null) {
                    data.hasData[i] = true;
                    var vertexBuffer = data.vertexBuffer(i);
                    if (!vertexBuffer.isInvalid()) {
                        vertexBuffer.bind();
                        vertexBuffer.upload(meshData);
                        VertexBuffer.unbind();
                    }
                } else {
                    data.hasData[i] = false;
                }
            } catch (Exception e) {
                LOG.warn("Failed to upload section {} layer {}", sp, i, e);
                data.hasData[i] = false;
            }
        }

        data.compiled = true;
    }

    private VertexConsumerWrapper wrapperForLayer(int layerIndex, BufferBuilder[] builders,
                                                  ByteBufferBuilder[] byteBuilders,
                                                  VertexConsumerWrapper[] wrappers) {
        var wrapper = wrappers[layerIndex];
        if (wrapper != null) return wrapper;

        RenderType layer = LAYERS.get(layerIndex);
        byteBuilders[layerIndex] = new ByteBufferBuilder(layer.bufferSize());
        builders[layerIndex] = new BufferBuilder(
                byteBuilders[layerIndex],
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK
        );
        wrapper = new VertexConsumerWrapper(builders[layerIndex]);
        wrappers[layerIndex] = wrapper;
        return wrapper;
    }

    private ISceneBlockRenderHook currentRenderHook() {
        if (renderedBlocksMap.isEmpty()) {
            return null;
        }
        return renderedBlocksMap.values().iterator().next();
    }

    private List<SectionPos> selectDirtySectionsForCompile(Vector3f eyePos, Vector3f focusPos, long deadline, boolean cameraMoving) {
        int max = Math.min(sectionCompileBatchSize(cameraMoving), dirtySections.size());
        var selected = new ArrayList<SectionPos>(max);
        for (var sp : dirtySections) {
            if (System.nanoTime() > deadline) {
                break;
            }
            int insertAt = 0;
            while (insertAt < selected.size()
                    && compareCompilePriority(selected.get(insertAt), sp, eyePos, focusPos) <= 0) {
                insertAt++;
            }
            if (insertAt < max) {
                selected.add(insertAt, sp);
                if (selected.size() > max) {
                    selected.remove(selected.size() - 1);
                }
            }
        }
        return selected;
    }

    private int sectionCompileBatchSize(boolean cameraMoving) {
        String mode = performanceMode();
        if (cameraMoving) {
            if ("quality".equals(mode)) return Math.max(2, DEFAULT_SECTION_COMPILE_BATCH_SIZE / 2);
            if ("performance".equals(mode)) return 1;
            return 2;
        }
        if ("quality".equals(mode)) return 10;
        if ("performance".equals(mode)) return 4;
        return DEFAULT_SECTION_COMPILE_BATCH_SIZE;
    }

    private int compareCompilePriority(SectionPos a, SectionPos b, Vector3f eyePos, Vector3f focusPos) {
        boolean av = isSectionVisible(a, focusPos);
        boolean bv = isSectionVisible(b, focusPos);
        if (av != bv) return av ? -1 : 1;
        return Double.compare(distanceToSectionSqr(a, eyePos), distanceToSectionSqr(b, eyePos));
    }

    private void rebuildAllTileEntities() {
        tileEntities.clear();
        rebuildTileEntitiesForSections(sectionBlocks.keySet());
    }

    private void rebuildTileEntitiesForSections(Collection<SectionPos> targetSections) {
        var mc = Minecraft.getInstance();
        for (var sp : targetSections) {
            tileEntities.removeIf(pos -> SectionPos.fromBlock(pos).equals(sp));
            var blocks = sectionBlocks.get(sp);
            if (blocks == null) {
                continue;
            }
            for (long packed : blocks) {
                if (Thread.interrupted()) return;
                var pos = BlockPos.of(packed);
                BlockEntity tile = world.getBlockEntity(pos);
                if (tile != null && mc.getBlockEntityRenderDispatcher().getRenderer(tile) != null) {
                    tileEntities.add(pos.immutable());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void drawWorld() {
        Consumer<WorldSceneRenderer> beforeWR = null;
        Consumer<WorldSceneRenderer> afterWR = null;
        BiConsumer<MultiBufferSource, Float> beforeBE = null;
        ISceneEntityRenderHook entityHook = null;
        boolean endBatchLastVal = false;

        try {
            if (f_beforeWorldRender != null) beforeWR = (Consumer<WorldSceneRenderer>) f_beforeWorldRender.get(this);
            if (f_afterWorldRender != null) afterWR = (Consumer<WorldSceneRenderer>) f_afterWorldRender.get(this);
            if (f_beforeBatchEnd != null) beforeBE = (BiConsumer<MultiBufferSource, Float>) f_beforeBatchEnd.get(this);
            if (f_sceneEntityRenderHook != null) entityHook = (ISceneEntityRenderHook) f_sceneEntityRenderHook.get(this);
            if (f_endBatchLast != null) endBatchLastVal = f_endBatchLast.getBoolean(this);
        } catch (Exception e) {
            LOG.warn("Failed to read renderer fields via reflection", e);
        }

        if (beforeWR != null) {
            beforeWR.accept(this);
        }

        Minecraft mc = Minecraft.getInstance();
        float particleTicks = mc.getTimer().getGameTimeDeltaPartialTick(false);
        var buffers = mc.renderBuffers().bufferSource();

        var eyePos = safeEyePos();
        var focusPos = safeFocusPos();
        boolean cameraMoving = updateCameraMoving(eyePos);
        boolean sortVisibleSections = !cameraMoving || "quality".equals(performanceMode());
        List<SectionPos> visibleSections = collectVisibleSections(focusPos, eyePos, sortVisibleSections);
        visibleSections = limitVisibleSectionsWhileMoving(visibleSections, cameraMoving, eyePos);
        Set<BlockPos> visibleTileEntities = collectVisibleTileEntities(visibleSections);

        compileDirtySections(eyePos, focusPos, cameraMoving);
        renderViewportLod(eyePos, cameraMoving);
        renderSectionPlaceholders(visibleSections, cameraMoving);

        int fallbackLimit = fallbackBlockLimit(cameraMoving);
        List<BlockPos> uncompiledBlocks = collectUncompiledBlocks(fallbackLimit, visibleSections);

        var bsr = mc.getBlockRenderer();
        var randomSource = RandomSource.createNewThreadLocalInstance();
        PoseStack poseStack = new PoseStack();

        for (int i = 0; i < LAYERS.size(); i++) {
            RenderType layer = LAYERS.get(i);

            if (layer == RenderType.translucent()) {
                setDefaultRenderLayerState(null);
                renderTESRInternal(visibleTileEntities, poseStack, buffers, particleTicks);

                if (!endBatchLastVal) {
                    buffers.endBatch();
                }

                if (particleManager != null) {
                    poseStack.pushPose();
                    poseStack.setIdentity();
                    poseStack.translate(cameraEntity.getX(), cameraEntity.getY(), cameraEntity.getZ());
                    particleManager.render(poseStack, camera, particleTicks, type -> !type.isTranslucent());
                    poseStack.popPose();
                }
            }

            setDefaultRenderLayerState(layer);
            layer.setupRenderState();

            drawCompiledSectionVBOs(i, visibleSections);

            if (!uncompiledBlocks.isEmpty()) {
                renderUncompiledBlocksForLayer(bsr, randomSource, poseStack, buffers, layer, uncompiledBlocks);
            }

            if (!endBatchLastVal) {
                buffers.endBatch();
            }
            layer.clearRenderState();
        }

        renderHeatmapSectionOverlay(visibleSections, cameraMoving);

        if (world instanceof TrackedDummyWorld level) {
            renderEntitiesInternal(level, poseStack, buffers, entityHook, particleTicks);
        }

        if (beforeBE != null) {
            beforeBE.accept(buffers, particleTicks);
        }

        buffers.endBatch();

        if (particleManager != null) {
            poseStack.pushPose();
            poseStack.setIdentity();
            poseStack.translate(cameraEntity.getX(), cameraEntity.getY(), cameraEntity.getZ());
            particleManager.render(poseStack, camera, particleTicks, ParticleRenderType::isTranslucent);
            poseStack.popPose();
        }

        if (afterWR != null) {
            afterWR.accept(this);
        }
    }

    private void renderViewportLod(Vector3f eyePos, boolean cameraMoving) {
        if (viewportLodPyramid == null || viewportLodPyramid.isEmpty()) return;
        ProjectionLodPyramid.LodLevel level = chooseViewportLodLevel(cameraMoving);
        if (level == null || level.isEmpty()) return;

        int maxBoxes = lodBoxBudget(cameraMoving);
        var boxes = level.boxes();
        int stride = Math.max(1, boxes.size() / Math.max(1, maxBoxes));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int drawn = 0;
        int renderDistance = viewportRenderDistance();
        for (int i = 0; i < boxes.size() && drawn < maxBoxes; i += stride) {
            var box = boxes.get(i);
            if (renderDistance > 0 && boxDistanceSqr(box, eyePos) > (double) renderDistance * renderDistance) {
                continue;
            }
            if (isLodBoxCoveredByCompiledSections(box)) {
                continue;
            }
            addLodBox(buffer, box);
            drawn++;
        }

        if (drawn > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private ProjectionLodPyramid.LodLevel chooseViewportLodLevel(boolean cameraMoving) {
        var levels = viewportLodPyramid.levels();
        if (levels == null || levels.isEmpty()) return null;
        if (cameraMoving || levels.size() == 1) return levels.get(0);
        return levels.get(Math.min(1, levels.size() - 1));
    }

    private int lodBoxBudget(boolean cameraMoving) {
        String mode = performanceMode();
        if (cameraMoving) {
            if ("performance".equals(mode)) return 1024;
            if ("quality".equals(mode)) return 4096;
            return 2048;
        }
        if ("performance".equals(mode)) return 4096;
        if ("quality".equals(mode)) return 12288;
        return 8192;
    }

    private boolean isLodBoxCoveredByCompiledSections(ProjectionLodPyramid.LodBox box) {
        int minSectionX = Math.floorDiv(box.minX(), SECTION_SIZE);
        int minSectionY = Math.floorDiv(box.minY(), SECTION_SIZE);
        int minSectionZ = Math.floorDiv(box.minZ(), SECTION_SIZE);
        int maxSectionX = Math.floorDiv(Math.max(box.minX(), box.maxX() - 1), SECTION_SIZE);
        int maxSectionY = Math.floorDiv(Math.max(box.minY(), box.maxY() - 1), SECTION_SIZE);
        int maxSectionZ = Math.floorDiv(Math.max(box.minZ(), box.maxZ() - 1), SECTION_SIZE);

        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
            for (int sz = minSectionZ; sz <= maxSectionZ; sz++) {
                for (int sx = minSectionX; sx <= maxSectionX; sx++) {
                    var data = sections.get(new SectionPos(sx, sy, sz));
                    if (data == null || !data.compiled) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private double boxDistanceSqr(ProjectionLodPyramid.LodBox box, Vector3f eyePos) {
        double cx = (box.minX() + box.maxX()) * 0.5D;
        double cy = (box.minY() + box.maxY()) * 0.5D;
        double cz = (box.minZ() + box.maxZ()) * 0.5D;
        double dx = cx - eyePos.x();
        double dy = cy - eyePos.y();
        double dz = cz - eyePos.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private void addLodBox(BufferBuilder buffer, ProjectionLodPyramid.LodBox box) {
        float inset = 0.025F;
        float x0 = box.minX() + inset;
        float y0 = box.minY() + inset;
        float z0 = box.minZ() + inset;
        float x1 = box.maxX() - inset;
        float y1 = box.maxY() - inset;
        float z1 = box.maxZ() - inset;
        int[] rgb = lodColor(box.stateId());
        int alpha = Mth.clamp((int) (36 + box.density() * 92), 34, 128);

        addQuad(buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, rgb[0], rgb[1], rgb[2], alpha);
        addQuad(buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, rgb[0], rgb[1], rgb[2], alpha);
        addQuad(buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, rgb[0], rgb[1], rgb[2], alpha);
        addQuad(buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, rgb[0], rgb[1], rgb[2], alpha);
        addQuad(buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, rgb[0], rgb[1], rgb[2], Math.min(150, alpha + 18));
        addQuad(buffer, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, rgb[0], rgb[1], rgb[2], Math.max(18, alpha / 2));
    }

    private int[] lodColor(int stateId) {
        BlockState state = stateId >= 0 && stateId < viewportLodPalette.size() ? viewportLodPalette.get(stateId) : null;
        int hash = state == null ? stateId * 1103515245 : Objects.hash(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                state.getValues());
        int r = 76 + Math.floorMod(hash, 120);
        int g = 92 + Math.floorMod(hash >> 8, 112);
        int b = 104 + Math.floorMod(hash >> 16, 104);
        return new int[]{Math.min(220, r), Math.min(220, g), Math.min(220, b)};
    }

    private void renderHeatmapSectionOverlay(List<SectionPos> visibleSections, boolean cameraMoving) {
        if (fastHeatmapMode == null || fastHeatmapMode == HeatmapMode.OFF || visibleSections.isEmpty()) {
            return;
        }

        int maxOverlays = cameraMoving ? 384 : 1024;
        int drawn = 0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = null;
        for (var sp : visibleSections) {
            if (drawn >= maxOverlays) break;
            var blocks = sectionBlocks.get(sp);
            if (blocks == null || blocks.isEmpty()) continue;
            if (buffer == null) {
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            int[] rgb = heatmapSectionColor(sp, blocks);
            int alpha = cameraMoving ? 52 : 72;
            addSectionBox(buffer, sp, rgb[0], rgb[1], rgb[2], alpha);
            drawn++;
        }

        if (buffer != null && drawn > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private int[] heatmapSectionColor(SectionPos sp, LongArrayList blocks) {
        if (fastHeatmapMode == HeatmapMode.BY_HEIGHT) {
            return heightHeatmapColor(sp.y() * SECTION_SIZE + 8);
        }

        int maxSamples = Math.min(96, blocks.size());
        int stride = Math.max(1, blocks.size() / Math.max(1, maxSamples));
        int r = 0;
        int g = 0;
        int b = 0;
        int samples = 0;
        for (int i = 0; i < blocks.size() && samples < maxSamples; i += stride) {
            BlockState state = world.getBlockState(BlockPos.of(blocks.getLong(i)));
            if (state == null || state.isAir()) continue;
            int[] color = switch (fastHeatmapMode) {
                case BY_TYPE -> typeHeatmapColor(state);
                case BY_RARITY -> rarityHeatmapColor(state);
                case BY_FACING -> facingHeatmapColor(state);
                case BY_HEIGHT -> heightHeatmapColor(sp.y() * SECTION_SIZE + 8);
                default -> new int[]{70, 160, 220};
            };
            r += color[0];
            g += color[1];
            b += color[2];
            samples++;
        }
        if (samples <= 0) {
            return new int[]{70, 160, 220};
        }
        return new int[]{r / samples, g / samples, b / samples};
    }

    private int[] typeHeatmapColor(BlockState state) {
        int hash = Objects.hash(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        return new int[]{
                72 + Math.floorMod(hash, 160),
                72 + Math.floorMod(hash >> 8, 160),
                72 + Math.floorMod(hash >> 16, 160)
        };
    }

    private int[] rarityHeatmapColor(BlockState state) {
        float speed = state.getBlock().defaultBlockState().getDestroySpeed(null, BlockPos.ZERO);
        if (speed < 0 || speed > 15.0F) return new int[]{180, 60, 230};
        if (speed > 5.0F) return new int[]{40, 110, 255};
        if (speed > 1.5F) return new int[]{70, 190, 80};
        if (speed > 0.0F) return new int[]{220, 220, 220};
        return new int[]{255, 210, 40};
    }

    private int[] heightHeatmapColor(int y) {
        float t = Mth.clamp(y / 255.0F, 0.0F, 1.0F);
        float r;
        float g;
        float b;
        if (t < 0.25F) {
            float s = t / 0.25F;
            r = 0.0F; g = s; b = 1.0F;
        } else if (t < 0.5F) {
            float s = (t - 0.25F) / 0.25F;
            r = 0.0F; g = 1.0F; b = 1.0F - s;
        } else if (t < 0.75F) {
            float s = (t - 0.5F) / 0.25F;
            r = s; g = 1.0F; b = 0.0F;
        } else {
            float s = (t - 0.75F) / 0.25F;
            r = 1.0F; g = 1.0F - s; b = 0.0F;
        }
        return new int[]{(int) (r * 255.0F), (int) (g * 255.0F), (int) (b * 255.0F)};
    }

    private int[] facingHeatmapColor(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dirProp && "facing".equals(prop.getName())) {
                return switch (state.getValue(dirProp)) {
                    case NORTH -> new int[]{255, 70, 70};
                    case SOUTH -> new int[]{70, 255, 70};
                    case WEST -> new int[]{70, 70, 255};
                    case EAST -> new int[]{255, 255, 70};
                    case UP -> new int[]{255, 70, 255};
                    case DOWN -> new int[]{70, 255, 255};
                };
            }
        }
        return new int[]{128, 128, 128};
    }

    private List<BlockPos> collectUncompiledBlocks(int limit, List<SectionPos> visibleSections) {
        List<BlockPos> uncompiled = new ArrayList<>();
        if (limit <= 0) {
            return uncompiled;
        }
        for (var sp : visibleSections) {
            var data = sections.get(sp);
            if (data == null || !data.compiled) {
                var blocks = sectionBlocks.get(sp);
                if (blocks == null) continue;
                for (long packed : blocks) {
                    var pos = BlockPos.of(packed);
                    uncompiled.add(pos);
                    if (uncompiled.size() >= limit) {
                        return uncompiled;
                    }
                }
            }
        }
        return uncompiled;
    }

    private Set<BlockPos> collectVisibleTileEntities(List<SectionPos> visibleSections) {
        if (tileEntities.isEmpty() || visibleSections.isEmpty()) {
            return Set.of();
        }
        Set<SectionPos> visibleSet = new HashSet<>(visibleSections);
        Set<BlockPos> visible = new LinkedHashSet<>();
        for (var pos : tileEntities) {
            if (visibleSet.contains(SectionPos.fromBlock(pos))) {
                visible.add(pos);
            }
        }
        return visible;
    }

    private void drawCompiledSectionVBOs(int layerIndex, List<SectionPos> visibleSections) {
        boolean anyDrawn = false;
        for (var sp : visibleSections) {
            var data = sections.get(sp);
            if (data == null) continue;
            if (!data.compiled || !data.hasData[layerIndex]) continue;
            var vb = data.vertexBuffers[layerIndex];
            if (vb == null || vb.isInvalid() || vb.getFormat() == null) continue;

            if (!anyDrawn) {
                setupVBODrawState();
                anyDrawn = true;
            }

            vb.bind();
            vb.draw();
        }
        if (anyDrawn) {
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) shader.clear();
            VertexBuffer.unbind();
        }
    }

    private void renderSectionPlaceholders(List<SectionPos> visibleSections, boolean cameraMoving) {
        if (visibleSections.isEmpty()) return;

        int maxPlaceholders = cameraMoving ? 192 : 512;
        int drawn = 0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = null;
        for (var sp : visibleSections) {
            if (drawn >= maxPlaceholders) break;
            var data = sections.get(sp);
            if (data != null && data.compiled) continue;
            var blocks = sectionBlocks.get(sp);
            if (blocks == null || blocks.isEmpty()) continue;
            if (buffer == null) {
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            }
            addSectionBox(buffer, sp, Math.min(90, 28 + blocks.size() / 12));
            drawn++;
        }

        if (buffer != null && drawn > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private void addSectionBox(BufferBuilder buffer, SectionPos sp, int alpha) {
        float x0 = sp.x() * SECTION_SIZE + 0.035F;
        float y0 = sp.y() * SECTION_SIZE + 0.035F;
        float z0 = sp.z() * SECTION_SIZE + 0.035F;
        float x1 = (sp.x() + 1) * SECTION_SIZE - 0.035F;
        float y1 = (sp.y() + 1) * SECTION_SIZE - 0.035F;
        float z1 = (sp.z() + 1) * SECTION_SIZE - 0.035F;
        int r = 60;
        int g = 172;
        int b = 235;

        addQuad(buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, alpha);
        addQuad(buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, r, g, b, alpha);
        addQuad(buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, r, g, b, alpha);
        addQuad(buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
        addQuad(buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
        addQuad(buffer, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r, g, b, Math.max(18, alpha / 2));
    }

    private void addSectionBox(BufferBuilder buffer, SectionPos sp, int r, int g, int b, int alpha) {
        float x0 = sp.x() * SECTION_SIZE + 0.045F;
        float y0 = sp.y() * SECTION_SIZE + 0.045F;
        float z0 = sp.z() * SECTION_SIZE + 0.045F;
        float x1 = (sp.x() + 1) * SECTION_SIZE - 0.045F;
        float y1 = (sp.y() + 1) * SECTION_SIZE - 0.045F;
        float z1 = (sp.z() + 1) * SECTION_SIZE - 0.045F;

        addQuad(buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, alpha);
        addQuad(buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, r, g, b, alpha);
        addQuad(buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, r, g, b, alpha);
        addQuad(buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
        addQuad(buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, Math.min(130, alpha + 20));
        addQuad(buffer, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r, g, b, Math.max(16, alpha / 2));
    }

    private void addQuad(BufferBuilder buffer,
                         float x0, float y0, float z0,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         int r, int g, int b, int a) {
        buffer.addVertex(x0, y0, z0).setColor(r, g, b, a);
        buffer.addVertex(x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(x3, y3, z3).setColor(r, g, b, a);
    }

    private Vector3f safeEyePos() {
        try {
            return new Vector3f(getEyePos());
        } catch (Exception ignored) {
            return new Vector3f((float) cameraEntity.getX(), (float) cameraEntity.getY(), (float) cameraEntity.getZ());
        }
    }

    private Vector3f safeFocusPos() {
        try {
            return new Vector3f(getLookAt());
        } catch (Exception ignored) {
            return safeEyePos();
        }
    }

    private boolean updateCameraMoving(Vector3f eyePos) {
        Vector3f lookAt;
        try {
            lookAt = new Vector3f(getLookAt());
        } catch (Exception ignored) {
            lookAt = new Vector3f();
        }

        boolean changed = lastEyePos == null || lastLookAt == null
                || eyePos.distanceSquared(lastEyePos) > CAMERA_EPSILON_SQR
                || lookAt.distanceSquared(lastLookAt) > CAMERA_EPSILON_SQR;
        lastEyePos = new Vector3f(eyePos);
        lastLookAt = new Vector3f(lookAt);

        if (changed) {
            cameraMovingFrames = 8;
        } else if (cameraMovingFrames > 0) {
            cameraMovingFrames--;
        }
        return cameraMovingFrames > 0 && degradeWhileMoving();
    }

    private List<SectionPos> collectVisibleSections(Vector3f focusPos, Vector3f eyePos, boolean sort) {
        var visible = new ArrayList<SectionPos>(sections.size());
        for (var sp : sections.keySet()) {
            if (isSectionVisible(sp, focusPos)) {
                visible.add(sp);
            }
        }
        if (visible.isEmpty() && !sections.isEmpty()) {
            visible.addAll(sections.keySet());
        }
        if (sort) {
            visible.sort(Comparator.comparingDouble(sp -> distanceToSectionSqr(sp, eyePos)));
        }
        return visible;
    }

    private List<SectionPos> limitVisibleSectionsWhileMoving(List<SectionPos> visibleSections, boolean cameraMoving, Vector3f eyePos) {
        if (!cameraMoving || visibleSections.isEmpty()) return visibleSections;
        int limit = movingSectionLimit();
        if (limit <= 0 || visibleSections.size() <= limit) return visibleSections;
        var nearest = new ArrayList<>(visibleSections);
        nearest.sort(Comparator.comparingDouble(sp -> distanceToSectionSqr(sp, eyePos)));
        return new ArrayList<>(nearest.subList(0, limit));
    }

    private int movingSectionLimit() {
        String mode = performanceMode();
        if ("quality".equals(mode)) return 0;
        if ("performance".equals(mode)) return PERFORMANCE_MOVING_SECTION_LIMIT;
        return BALANCED_MOVING_SECTION_LIMIT;
    }

    private boolean isSectionVisible(SectionPos sp, Vector3f focusPos) {
        int renderDistance = viewportRenderDistance();
        if (renderDistance > 0 && distanceToSectionSqr(sp, focusPos) > (double) renderDistance * renderDistance) {
            return false;
        }
        return true;
    }

    private double distanceToSectionSqr(SectionPos sp, Vector3f eyePos) {
        double cx = sp.x() * SECTION_SIZE + SECTION_SIZE * 0.5D;
        double cy = sp.y() * SECTION_SIZE + SECTION_SIZE * 0.5D;
        double cz = sp.z() * SECTION_SIZE + SECTION_SIZE * 0.5D;
        double dx = cx - eyePos.x;
        double dy = cy - eyePos.y;
        double dz = cz - eyePos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private long compileBudgetNanos(boolean cameraMoving) {
        double budgetMs = cameraMoving ? EBEClientConfig.viewportMovingCompileBudgetMs.get()
                : EBEClientConfig.viewportCompileBudgetMs.get();
        String mode = performanceMode();
        if ("quality".equals(mode) && cameraMoving) {
            budgetMs = EBEClientConfig.viewportCompileBudgetMs.get();
        } else if ("performance".equals(mode) && cameraMoving) {
            budgetMs = Math.min(budgetMs, 0.1D);
        }
        return Math.max(0L, (long) (budgetMs * 1_000_000L));
    }

    private int fallbackBlockLimit(boolean cameraMoving) {
        if (cameraMoving) {
            if ("quality".equals(performanceMode())) {
                return Math.max(MAX_IMMEDIATE_FALLBACK_BLOCKS, EBEClientConfig.viewportFallbackBlocks.get());
            }
            return Math.max(0, EBEClientConfig.viewportMovingFallbackBlocks.get());
        }
        return Math.max(0, EBEClientConfig.viewportFallbackBlocks.get());
    }

    private boolean degradeWhileMoving() {
        String mode = performanceMode();
        if ("quality".equals(mode)) {
            return false;
        }
        if ("performance".equals(mode)) {
            return true;
        }
        return EBEClientConfig.viewportDegradeWhileMoving.get();
    }

    private int viewportRenderDistance() {
        return Math.max(0, EBEClientConfig.viewportRenderDistance.get());
    }

    private String performanceMode() {
        String mode = EBEClientConfig.viewportPerformanceMode.get();
        if ("quality".equals(mode) || "balanced".equals(mode) || "performance".equals(mode)) {
            return mode;
        }
        return "balanced";
    }

    private void setupVBODrawState() {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) return;

        for (int j = 0; j < 12; ++j) {
            int tex = RenderSystem.getShaderTexture(j);
            shader.setSampler("Sampler" + j, tex);
        }

        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        if (shader.FOG_START != null) {
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        if (shader.FOG_END != null) {
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        if (shader.FOG_COLOR != null) {
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        if (shader.FOG_SHAPE != null) {
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        if (shader.TEXTURE_MATRIX != null) {
            shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }
        if (shader.GAME_TIME != null) {
            shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }

        RenderSystem.setupShaderLights(shader);
        shader.apply();
    }

    private void renderUncompiledBlocksForLayer(BlockRenderDispatcher bsr, RandomSource randomSource,
                                                 PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                                 RenderType layer, List<BlockPos> uncompiledBlocks) {
        layer.setupRenderState();
        var buffer = buffers.getBuffer(layer);
        var wrapper = new VertexConsumerWrapper(buffer);
        ISceneBlockRenderHook fallbackHook = renderedBlocksMap.isEmpty() ? null : renderedBlocksMap.values().iterator().next();

        for (var pos : uncompiledBlocks) {
            var state = world.getBlockState(pos);
            if (state == null) continue;
            var fluidState = state.getFluidState();
            var block = state.getBlock();

            if (fallbackHook != null) {
                fallbackHook.applyVertexConsumerWrapper(world, pos, state, wrapper, layer, 0);
            }

            if (block == Blocks.AIR) continue;
            if (state.getRenderShape() != INVISIBLE) {
                var model = bsr.getBlockModel(state);
                var modelData = world.getModelData(pos);
                modelData = model.getModelData(world, pos, state, modelData);
                long seed = state.getSeed(pos);
                randomSource.setSeed(seed);
                if (model.getRenderTypes(state, randomSource, modelData).contains(layer)) {
                    randomSource.setSeed(seed);
                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                    bsr.renderBatched(state, pos, world, poseStack, wrapper, false, randomSource, modelData, layer);
                    poseStack.popPose();
                }
            }
            if (!fluidState.isEmpty() && ItemBlockRenderTypes.getRenderLayer(fluidState) == layer) {
                wrapper.addOffset(pos.getX() - (pos.getX() & 15), pos.getY() - (pos.getY() & 15), pos.getZ() - (pos.getZ() & 15));
                bsr.renderLiquid(pos, world, wrapper, state, fluidState);
            }
            wrapper.clearOffset();
            wrapper.clearColor();
        }
    }

    private void renderTESRInternal(Set<BlockPos> poses, PoseStack poseStack, MultiBufferSource.BufferSource buffers, float partialTicks) {
        for (var pos : poses) {
            var tile = world.getBlockEntity(pos);
            if (tile != null) {
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                var beRenderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(tile);
                if (beRenderer != null) {
                    if (tile.hasLevel() && tile.getType().isValid(tile.getBlockState())) {
                        beRenderer.render(tile, partialTicks, poseStack, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY);
                    }
                }
                poseStack.popPose();
            }
        }
    }

    private void renderEntitiesInternal(TrackedDummyWorld level, PoseStack poseStack, MultiBufferSource buffer,
                                         ISceneEntityRenderHook hook, float partialTicks) {
        for (var entity : level.getAllRenderedEntities()) {
            poseStack.pushPose();
            try {
                ProjectionEntityTransforms.stabilizeRenderableEntity(entity);
                float stablePartialTicks = 0.0F;
                double d0 = Mth.lerp(stablePartialTicks, entity.xOld, entity.getX());
                double d1 = Mth.lerp(stablePartialTicks, entity.yOld, entity.getY());
                double d2 = Mth.lerp(stablePartialTicks, entity.zOld, entity.getZ());
                float f = Mth.lerp(stablePartialTicks, entity.yRotO, entity.getYRot());
                var renderManager = Minecraft.getInstance().getEntityRenderDispatcher();
                int light = renderManager.getRenderer(entity).getPackedLightCoords(entity, stablePartialTicks);
                if (hook != null) {
                    hook.applyEntity(world, entity, poseStack, stablePartialTicks);
                }
                renderManager.render(entity, d0, d1, d2, f, stablePartialTicks, poseStack, buffer, light);
            } catch (Exception ex) {
                LOG.debug("Skipping decorative entity render after failure", ex);
            }
            poseStack.popPose();
        }
    }
}
