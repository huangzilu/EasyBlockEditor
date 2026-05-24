package com.l1ght.ebe.client.renderer;

import com.l1ght.ebe.config.EBEClientConfig;
import com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.ISceneEntityRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.ImmediateWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
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
    private static final int SECTION_COMPILE_BATCH_SIZE = 3;
    private static final int MAX_IMMEDIATE_FALLBACK_BLOCKS = 4096;
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
    private final Map<SectionPos, Set<BlockPos>> sectionBlocks = new LinkedHashMap<>();
    private final Set<SectionPos> dirtySections = new LinkedHashSet<>();
    private final Set<BlockPos> tileEntities = new LinkedHashSet<>();

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
            for (int i = 0; i < LAYERS.size(); i++) {
                vertexBuffers[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
        }

        public void close() {
            for (var vb : vertexBuffers) {
                if (vb != null) vb.close();
            }
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
            sectionBlocks.computeIfAbsent(sp, ignored -> newSectionBlockSet()).add(immutable);
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
            sectionBlocks.computeIfAbsent(sp, k -> newSectionBlockSet()).add(pos.immutable());
            sections.computeIfAbsent(sp, k -> new SectionData());
        } else {
            var blocks = sectionBlocks.get(sp);
            if (blocks != null) {
                blocks.remove(pos);
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
                sectionBlocks.computeIfAbsent(sp, k -> newSectionBlockSet()).add(pos.immutable());
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
                var sectionSet = sectionBlocks.get(sp);
                if (sectionSet != null) {
                    sectionSet.remove(pos);
                    if (sectionSet.isEmpty()) {
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
                sectionBlocks.computeIfAbsent(sp, k -> newSectionBlockSet()).add(pos.immutable());
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

    private Set<BlockPos> newSectionBlockSet() {
        return new LinkedHashSet<>();
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

        List<SectionPos> toCompile = selectDirtySectionsForCompile(eyePos, focusPos, deadline);
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
        VertexConsumerWrapper[] wrappers = new VertexConsumerWrapper[LAYERS.size()];
        for (int i = 0; i < LAYERS.size(); i++) {
            RenderType layer = LAYERS.get(i);
            builders[i] = new BufferBuilder(
                    new ByteBufferBuilder(layer.bufferSize()),
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.BLOCK
            );
            wrappers[i] = new VertexConsumerWrapper(builders[i]);
        }

        boolean compileFailed = false;
        try {
            PoseStack poseStack = new PoseStack();
            ISceneBlockRenderHook renderHook = currentRenderHook();
            for (var pos : blocks) {
                if (Thread.interrupted()) return;
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
                        randomSource.setSeed(seed);
                        poseStack.pushPose();
                        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                        if (renderHook != null) {
                            renderHook.applyVertexConsumerWrapper(world, pos, state, wrappers[i], layer, 0);
                        }
                        brd.renderBatched(state, pos, world, poseStack, wrappers[i], false, randomSource, modelData, layer);
                        poseStack.popPose();
                        wrappers[i].clearOffset();
                        wrappers[i].clearColor();
                    }
                }

                if (!fluidState.isEmpty()) {
                    RenderType fluidLayer = ItemBlockRenderTypes.getRenderLayer(fluidState);
                    Integer layerIndex = LAYER_INDICES.get(fluidLayer);
                    if (layerIndex != null) {
                        var wrapper = wrappers[layerIndex];
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
                MeshData meshData = builders[i].build();
                if (compileFailed) {
                    data.hasData[i] = false;
                } else if (meshData != null) {
                    data.hasData[i] = true;
                    var vertexBuffer = data.vertexBuffers[i];
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

    private ISceneBlockRenderHook currentRenderHook() {
        if (renderedBlocksMap.isEmpty()) {
            return null;
        }
        return renderedBlocksMap.values().iterator().next();
    }

    private List<SectionPos> selectDirtySectionsForCompile(Vector3f eyePos, Vector3f focusPos, long deadline) {
        int max = Math.min(SECTION_COMPILE_BATCH_SIZE, dirtySections.size());
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
            for (var pos : blocks) {
                if (Thread.interrupted()) return;
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

        compileDirtySections(eyePos, focusPos, cameraMoving);

        int fallbackLimit = fallbackBlockLimit(cameraMoving);
        List<BlockPos> uncompiledBlocks = collectUncompiledBlocks(fallbackLimit, visibleSections);

        var bsr = mc.getBlockRenderer();
        var randomSource = RandomSource.createNewThreadLocalInstance();
        PoseStack poseStack = new PoseStack();

        for (int i = 0; i < LAYERS.size(); i++) {
            RenderType layer = LAYERS.get(i);

            if (layer == RenderType.translucent()) {
                setDefaultRenderLayerState(null);
                renderTESRInternal(tileEntities, poseStack, buffers, particleTicks);

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
                for (var pos : blocks) {
                    uncompiled.add(pos);
                    if (uncompiled.size() >= limit) {
                        return uncompiled;
                    }
                }
            }
        }
        return uncompiled;
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
            if (entity.tickCount == 0) {
                entity.xOld = entity.getX();
                entity.yOld = entity.getY();
                entity.zOld = entity.getZ();
            }
            double d0 = Mth.lerp(partialTicks, entity.xOld, entity.getX());
            double d1 = Mth.lerp(partialTicks, entity.yOld, entity.getY());
            double d2 = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
            float f = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
            var renderManager = Minecraft.getInstance().getEntityRenderDispatcher();
            int light = renderManager.getRenderer(entity).getPackedLightCoords(entity, partialTicks);
            if (hook != null) {
                hook.applyEntity(world, entity, poseStack, partialTicks);
            }
            renderManager.render(entity, d0, d1, d2, f, partialTicks, poseStack, buffer, light);
            poseStack.popPose();
        }
    }
}
