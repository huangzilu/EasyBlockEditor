package com.l1ght.ebe.client.renderer;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static net.minecraft.world.level.block.RenderShape.INVISIBLE;

@OnlyIn(Dist.CLIENT)
public class SectionedWorldSceneRenderer extends ImmediateWorldSceneRenderer {

    private static final Logger LOG = LoggerFactory.getLogger("EBE/SectionedRenderer");
    private static final int SECTION_SIZE = 16;
    private static final List<RenderType> LAYERS = RenderType.chunkBufferLayers();

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

    private final Map<SectionPos, SectionData> sections = new ConcurrentHashMap<>();
    private final Map<SectionPos, Set<BlockPos>> sectionBlocks = new ConcurrentHashMap<>();
    private final Set<SectionPos> dirtySections = new CopyOnWriteArraySet<>();
    private volatile Set<BlockPos> tileEntities = Collections.emptySet();

    private volatile Thread compileThread;
    private final AtomicBoolean compiling = new AtomicBoolean(false);
    private volatile boolean needsFullRebuild = true;

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
        dirtySections.add(sp);
        var data = sections.get(sp);
        if (data != null) {
            data.compiled = false;
        }
    }

    public void updateSectionBlock(BlockPos pos, boolean added) {
        var sp = SectionPos.fromBlock(pos);
        if (added) {
            sectionBlocks.computeIfAbsent(sp, k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
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
        markSectionDirty(pos);
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
        tileEntities = Collections.emptySet();
        needsFullRebuild = true;
        super.deleteCacheBuffer();
        return this;
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
                sectionBlocks.computeIfAbsent(sp, k -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
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
        needsFullRebuild = false;
    }

    private void compileDirtySections() {
        if (needsFullRebuild) {
            rebuildSectionBlocks();
        }

        if (dirtySections.isEmpty() || compiling.get()) return;

        List<SectionPos> toCompile = new ArrayList<>(dirtySections);
        dirtySections.clear();

        compileThread = new Thread(() -> {
            compiling.set(true);
            try {
                ModelBlockRenderer.enableCaching();
                var mc = Minecraft.getInstance();
                var brd = mc.getBlockRenderer();
                var randomSource = RandomSource.createNewThreadLocalInstance();

                for (var sp : toCompile) {
                    if (Thread.interrupted()) break;
                    compileSection(sp, brd, randomSource);
                }

                collectTileEntities();

                ModelBlockRenderer.clearCache();
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) {
                    LOG.warn("Section compilation error", e);
                }
            } finally {
                compiling.set(false);
                compileThread = null;
            }
        }, "EBE-SectionCompiler");
        compileThread.start();
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

        List<BlockPos> blockList = new ArrayList<>(blocks);

        for (int i = 0; i < LAYERS.size(); i++) {
            if (Thread.interrupted()) return;
            RenderType layer = LAYERS.get(i);

            try {
                var bufferBuilder = new BufferBuilder(
                        new ByteBufferBuilder(layer.bufferSize()),
                        VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.BLOCK
                );
                PoseStack poseStack = new PoseStack();

                for (var pos : blockList) {
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
                        randomSource.setSeed(state.getSeed(pos));
                        modelData = model.getModelData(world, pos, state, modelData);

                        if (model.getRenderTypes(state, randomSource, modelData).contains(layer)) {
                            poseStack.pushPose();
                            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                            brd.renderBatched(state, pos, world, poseStack, bufferBuilder, false, randomSource, modelData, layer);
                            poseStack.popPose();
                        }
                    }

                    if (!fluidState.isEmpty() && ItemBlockRenderTypes.getRenderLayer(fluidState) == layer) {
                        var wrapper = new VertexConsumerWrapper(bufferBuilder);
                        wrapper.addOffset(pos.getX() - (pos.getX() & 15), pos.getY() - (pos.getY() & 15), pos.getZ() - (pos.getZ() & 15));
                        brd.renderLiquid(pos, world, wrapper, state, fluidState);
                        wrapper.clearOffset();
                    }
                }

                MeshData meshData = bufferBuilder.build();
                if (meshData != null) {
                    data.hasData[i] = true;
                    var vertexBuffer = data.vertexBuffers[i];
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        if (!vertexBuffer.isInvalid()) {
                            vertexBuffer.bind();
                            vertexBuffer.upload(meshData);
                            VertexBuffer.unbind();
                        }
                    }, runnable -> RenderSystem.recordRenderCall(runnable::run));
                } else {
                    data.hasData[i] = false;
                }
            } catch (Exception e) {
                LOG.warn("Failed to compile section {} layer {}", sp, i, e);
                data.hasData[i] = false;
            }
        }

        data.compiled = true;
    }

    private void collectTileEntities() {
        var mc = Minecraft.getInstance();
        var tes = new HashSet<BlockPos>();
        renderedBlocksMap.forEach((blocks, hook) -> {
            for (var pos : blocks) {
                if (Thread.interrupted()) return;
                BlockEntity tile = world.getBlockEntity(pos);
                if (tile != null && mc.getBlockEntityRenderDispatcher().getRenderer(tile) != null) {
                    tes.add(pos);
                }
            }
        });
        tileEntities = tes;
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

        compileDirtySections();

        List<BlockPos> uncompiledBlocks = collectUncompiledBlocks();

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

            drawCompiledSectionVBOs(i);

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

    private List<BlockPos> collectUncompiledBlocks() {
        List<BlockPos> uncompiled = new ArrayList<>();
        for (var entry : sectionBlocks.entrySet()) {
            var data = sections.get(entry.getKey());
            if (data == null || !data.compiled) {
                uncompiled.addAll(entry.getValue());
            }
        }
        return uncompiled;
    }

    private void drawCompiledSectionVBOs(int layerIndex) {
        boolean anyDrawn = false;
        for (var entry : sections.entrySet()) {
            var data = entry.getValue();
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

        renderedBlocksMap.forEach((renderedBlocks, hook) -> {
            for (var pos : uncompiledBlocks) {
                if (!renderedBlocks.contains(pos)) continue;
                var state = world.getBlockState(pos);
                if (state == null) continue;
                var fluidState = state.getFluidState();
                var block = state.getBlock();

                if (hook != null) {
                    hook.applyVertexConsumerWrapper(world, pos, state, wrapper, layer, 0);
                }

                if (block == Blocks.AIR) continue;
                if (state.getRenderShape() != INVISIBLE) {
                    var model = bsr.getBlockModel(state);
                    var modelData = world.getModelData(pos);
                    modelData = model.getModelData(world, pos, state, modelData);
                    randomSource.setSeed(state.getSeed(pos));
                    modelData = model.getModelData(world, pos, state, modelData);
                    if (model.getRenderTypes(state, randomSource, modelData).contains(layer)) {
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
        });
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
