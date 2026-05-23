package com.l1ght.ebe.client.projection;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.projection.ProjectionData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class ProjectionRenderer {
    private static final int SECTION_SIZE = 16;
    private static final int SECTION_MESH_BUILDS_PER_FRAME = 3;
    private static final int MAX_IMMEDIATE_FALLBACK_BLOCKS = 4096;
    private static final float PROJECTION_FOG_START = 1_000_000.0F;
    private static final float PROJECTION_FOG_END = 1_000_001.0F;
    private static final Map<RenderType, RenderType> TRANSLUCENT_ENTITY_TYPES = new HashMap<>();
    private static Method renderTypeStateMethod;
    private static Method textureCutoutTextureMethod;
    private static ProjectionData cachedProjection;
    private static int cachedProjectionVersion = -1;
    private static ProjectionRenderCache cachedRenderCache;
    private static ProjectionMeshCache cachedMeshCache;

    public static void renderProjection(PoseStack poseStack, Matrix4f projectionMatrix) {
        renderProjection(poseStack, projectionMatrix, null);
    }

    public static void renderProjection(PoseStack poseStack, Matrix4f projectionMatrix, Frustum frustum) {
        ProjectionManager.loadPersistentStateIfNeeded();
        if (!ProjectionManager.isProjectionVisible()) return;

        var projection = ProjectionManager.getProjection();
        if (projection == null) return;

        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        float opacity = ProjectionManager.getOpacity();
        List<ProjectionData.ProjectionBlock> blocks = projection.getBlocks();
        if (blocks.isEmpty()) return;
        ProjectionRenderCache renderCache = getRenderCache(projection);

        var camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        int renderDistance = EBEClientConfig.projectionRenderDistance.get();
        double maxDistanceSq = renderDistance <= 0 ? Double.POSITIVE_INFINITY : (double) renderDistance * (double) renderDistance;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-0.3f, -0.6f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, opacity);

        var bufferSource = mc.renderBuffers().bufferSource();
        var brd = mc.getBlockRenderer();
        var randomSource = RandomSource.createNewThreadLocalInstance();
        BlockAndTintGetter projectionView = new ProjectionBlockView(level, renderCache.states(), projection.getOrigin());
        ImmediateAlphaBufferSource blockEntityBuffers = new ImmediateAlphaBufferSource(opacity);
        ProjectionMeshCache meshCache = getMeshCache(projection, renderCache);

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        List<SectionBucket> visibleSections = new java.util.ArrayList<>();
        for (var section : renderCache.sections()) {
            if (section.bounds().distanceToSqr(camPos) > maxDistanceSq) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(section.bounds())) {
                continue;
            }
            visibleSections.add(section);
        }
        visibleSections.sort(java.util.Comparator.comparingDouble(section -> section.bounds().distanceToSqr(camPos)));

        buildVisibleSectionMeshes(visibleSections, meshCache, projectionView, brd, randomSource, opacity);
        drawCompiledSectionMeshes(visibleSections, meshCache, poseStack, projection.getOrigin());

        RenderType projectionLayer = RenderType.translucent();
        VertexConsumer projectionConsumer = new ProjectionVertexConsumer(bufferSource.getBuffer(projectionLayer), opacity);
        int immediateFallbackBlocks = 0;

        for (var section : visibleSections) {
            SectionMesh sectionMesh = meshCache.meshes().get(section.key());
            boolean renderImmediateBlocks = sectionMesh == null || !sectionMesh.compiled() || sectionMesh.failed();
            boolean waitingForCompile = sectionMesh == null || !sectionMesh.compiled();
            if (waitingForCompile && immediateFallbackBlocks >= MAX_IMMEDIATE_FALLBACK_BLOCKS) {
                continue;
            }
            for (var pb : section.blocks()) {
                BlockPos pos = pb.pos();
                BlockState state = pb.state();
                if (state.getRenderShape() == RenderShape.INVISIBLE) continue;

                try {
                    if (state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                        renderBlockEntityProjection(state, pos, poseStack, blockEntityBuffers);
                        continue;
                    }
                    if (!renderImmediateBlocks) {
                        continue;
                    }
                    if (waitingForCompile && immediateFallbackBlocks >= MAX_IMMEDIATE_FALLBACK_BLOCKS) {
                        break;
                    }
                    if (waitingForCompile) {
                        immediateFallbackBlocks++;
                    }

                    var model = brd.getBlockModel(state);
                    var modelData = projectionView.getModelData(pos);
                    modelData = model.getModelData(projectionView, pos, state, modelData);
                    randomSource.setSeed(state.getSeed(pos));

                    for (var modelLayer : model.getRenderTypes(state, randomSource, modelData)) {
                        randomSource.setSeed(state.getSeed(pos));
                        poseStack.pushPose();
                        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                        brd.renderBatched(state, pos, projectionView, poseStack, projectionConsumer, true, randomSource, modelData, modelLayer);
                        poseStack.popPose();
                    }
                } catch (Exception ignored) {}
            }
        }

        poseStack.popPose();

        bufferSource.endBatch(projectionLayer);
        blockEntityBuffers.endBatch();
        blockEntityBuffers.close();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.polygonOffset(0f, 0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static ProjectionRenderCache getRenderCache(ProjectionData projection) {
        int version = projection.getRenderVersion();
        if (cachedProjection == projection && cachedProjectionVersion == version && cachedRenderCache != null) {
            return cachedRenderCache;
        }

        Map<BlockPos, BlockState> states = new HashMap<>(projection.getBlocks().size());
        Map<SectionKey, SectionBucketBuilder> sectionBuilders = new HashMap<>();
        BlockPos origin = projection.getOrigin();
        for (var block : projection.getBlocks()) {
            BlockPos pos = block.pos();
            states.put(pos, block.state());
            SectionKey key = SectionKey.from(pos, origin);
            sectionBuilders.computeIfAbsent(key, SectionBucketBuilder::new).blocks().add(block);
        }

        List<SectionBucket> sections = new java.util.ArrayList<>(sectionBuilders.size());
        for (var builder : sectionBuilders.values()) {
            sections.add(builder.build(origin));
        }

        cachedProjection = projection;
        cachedProjectionVersion = version;
        cachedRenderCache = new ProjectionRenderCache(states, sections);
        return cachedRenderCache;
    }

    private static ProjectionMeshCache getMeshCache(ProjectionData projection, ProjectionRenderCache renderCache) {
        int version = projection.getMeshVersion();
        if (cachedMeshCache != null
                && cachedMeshCache.projection() == projection
                && cachedMeshCache.version() == version) {
            return cachedMeshCache;
        }

        clearMeshCache();
        Map<SectionKey, SectionMesh> meshes = new HashMap<>(renderCache.sections().size());
        for (var section : renderCache.sections()) {
            meshes.put(section.key(), new SectionMesh());
        }
        cachedMeshCache = new ProjectionMeshCache(projection, version, meshes);
        return cachedMeshCache;
    }

    private static void clearMeshCache() {
        if (cachedMeshCache == null) {
            return;
        }
        for (var mesh : cachedMeshCache.meshes().values()) {
            mesh.close();
        }
        cachedMeshCache = null;
    }

    private static void buildVisibleSectionMeshes(
            List<SectionBucket> visibleSections,
            ProjectionMeshCache meshCache,
            BlockAndTintGetter projectionView,
            BlockRenderDispatcher brd,
            RandomSource randomSource,
            float opacity
    ) {
        int builds = 0;
        for (var section : visibleSections) {
            if (builds >= SECTION_MESH_BUILDS_PER_FRAME) {
                return;
            }
            SectionMesh mesh = meshCache.meshes().get(section.key());
            if (mesh == null || mesh.compiled()) {
                continue;
            }
            compileSectionMesh(section, mesh, projectionView, brd, randomSource, opacity);
            builds++;
        }
    }

    private static void compileSectionMesh(
            SectionBucket section,
            SectionMesh mesh,
            BlockAndTintGetter projectionView,
            BlockRenderDispatcher brd,
            RandomSource randomSource,
            float opacity
    ) {
        RenderType projectionLayer = RenderType.translucent();
        try {
            var bufferBuilder = new BufferBuilder(
                    new ByteBufferBuilder(projectionLayer.bufferSize()),
                    VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.BLOCK
            );
            PoseStack buildPose = new PoseStack();
            var wrapper = new ProjectionVertexConsumer(bufferBuilder, opacity);

            for (var pb : section.blocks()) {
                BlockPos pos = pb.pos();
                BlockState state = pb.state();
                if (state.getRenderShape() == RenderShape.INVISIBLE || state.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                    continue;
                }

                var model = brd.getBlockModel(state);
                var modelData = projectionView.getModelData(pos);
                modelData = model.getModelData(projectionView, pos, state, modelData);
                randomSource.setSeed(state.getSeed(pos));

                for (var modelLayer : model.getRenderTypes(state, randomSource, modelData)) {
                    randomSource.setSeed(state.getSeed(pos));
                    buildPose.pushPose();
                    BlockPos origin = projectionView instanceof ProjectionBlockView view ? view.origin() : BlockPos.ZERO;
                    buildPose.translate(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ());
                    brd.renderBatched(state, pos, projectionView, buildPose, wrapper, true, randomSource, modelData, modelLayer);
                    buildPose.popPose();
                    wrapper.clearOffset();
                    wrapper.clearColor();
                }
            }

            MeshData meshData = bufferBuilder.build();
            if (meshData != null) {
                mesh.vertexBuffer().bind();
                mesh.vertexBuffer().upload(meshData);
                VertexBuffer.unbind();
                mesh.setHasData(true);
            } else {
                mesh.setHasData(false);
            }
            mesh.setCompiled(true);
        } catch (Exception e) {
            mesh.setFailed(true);
            mesh.setCompiled(true);
        }
    }

    private static void drawCompiledSectionMeshes(
            List<SectionBucket> visibleSections,
            ProjectionMeshCache meshCache,
            PoseStack poseStack,
            BlockPos origin
    ) {
        boolean anyDrawn = false;
        RenderType projectionLayer = RenderType.translucent();

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        modelViewStack.translate(origin.getX(), origin.getY(), origin.getZ());
        RenderSystem.applyModelViewMatrix();

        try {
            projectionLayer.setupRenderState();
            for (var section : visibleSections) {
                SectionMesh mesh = meshCache.meshes().get(section.key());
                if (mesh == null || !mesh.compiled() || !mesh.hasData() || mesh.failed()
                        || mesh.vertexBuffer().isInvalid() || mesh.vertexBuffer().getFormat() == null) {
                    continue;
                }

                if (!anyDrawn) {
                    setupVBODrawState();
                    anyDrawn = true;
                }

                mesh.vertexBuffer().bind();
                mesh.vertexBuffer().draw();
            }
        } finally {
            if (anyDrawn) {
                ShaderInstance shader = RenderSystem.getShader();
                if (shader != null) {
                    shader.clear();
                }
                VertexBuffer.unbind();
            }
            projectionLayer.clearRenderState();
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void setupVBODrawState() {
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
            shader.FOG_START.set(PROJECTION_FOG_START);
        }
        if (shader.FOG_END != null) {
            shader.FOG_END.set(PROJECTION_FOG_END);
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

    private static void renderBlockEntityProjection(
            BlockState state,
            BlockPos pos,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }

        BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
        if (blockEntity == null) {
            return;
        }

        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        if (renderer == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        renderer.render(blockEntity, 0.0F, poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static class ProjectionBlockView implements BlockAndTintGetter {
        private final BlockAndTintGetter delegate;
        private final Map<BlockPos, BlockState> projectionStates;
        private final BlockPos origin;

        private ProjectionBlockView(BlockAndTintGetter delegate, Map<BlockPos, BlockState> projectionStates, BlockPos origin) {
            this.delegate = delegate;
            this.projectionStates = projectionStates;
            this.origin = origin;
        }

        private BlockPos origin() {
            return origin;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return delegate.getBlockTint(pos, colorResolver);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return projectionStates.getOrDefault(pos, delegate.getBlockState(pos));
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            BlockState state = projectionStates.get(pos);
            return state == null ? delegate.getFluidState(pos) : state.getFluidState();
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }
    }

    private static class ImmediateAlphaBufferSource implements MultiBufferSource, AutoCloseable {
        private final ByteBufferBuilder builder = new ByteBufferBuilder(786432);
        private final MultiBufferSource.BufferSource delegate = MultiBufferSource.immediate(builder);
        private final float alpha;
        private final Set<RenderType> usedTypes = new HashSet<>();

        private ImmediateAlphaBufferSource(float alpha) {
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            RenderType translucentType = remapEntityRenderType(renderType);
            usedTypes.add(translucentType);
            return new AlphaVertexConsumer(delegate.getBuffer(translucentType), alpha);
        }

        private void endBatch() {
            for (RenderType renderType : usedTypes) {
                delegate.endBatch(renderType);
            }
            delegate.endBatch();
            usedTypes.clear();
        }

        @Override
        public void close() {
            builder.close();
        }
    }

    private static class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        private AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.min(alpha, this.alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16 & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    private static class ProjectionVertexConsumer extends AlphaVertexConsumer {
        private float offsetX;
        private float offsetY;
        private float offsetZ;

        private ProjectionVertexConsumer(VertexConsumer delegate, float alpha) {
            super(delegate, alpha);
        }

        public void addOffset(float offsetX, float offsetY, float offsetZ) {
            this.offsetX += offsetX;
            this.offsetY += offsetY;
            this.offsetZ += offsetZ;
        }

        public void clearOffset() {
            this.offsetX = 0;
            this.offsetY = 0;
            this.offsetZ = 0;
        }

        public void clearColor() {
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return super.addVertex(x + offsetX, y + offsetY, z + offsetZ);
        }
    }

    private static RenderType remapEntityRenderType(RenderType renderType) {
        return TRANSLUCENT_ENTITY_TYPES.computeIfAbsent(renderType, type -> {
            var texture = extractTexture(type);
            return texture == null ? type : RenderType.entityTranslucent(texture, false);
        });
    }

    private static net.minecraft.resources.ResourceLocation extractTexture(RenderType renderType) {
        try {
            if (renderTypeStateMethod == null) {
                renderTypeStateMethod = renderType.getClass().getDeclaredMethod("state");
                renderTypeStateMethod.setAccessible(true);
            }

            Object state = renderTypeStateMethod.invoke(renderType);
            var textureField = state.getClass().getDeclaredField("textureState");
            textureField.setAccessible(true);
            Object textureState = textureField.get(state);

            if (textureCutoutTextureMethod == null) {
                textureCutoutTextureMethod = textureState.getClass().getSuperclass().getDeclaredMethod("cutoutTexture");
                textureCutoutTextureMethod.setAccessible(true);
            }

            Object value = textureCutoutTextureMethod.invoke(textureState);
            if (value instanceof java.util.Optional<?> optional && optional.isPresent()
                    && optional.get() instanceof net.minecraft.resources.ResourceLocation location) {
                return location;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private record ProjectionRenderCache(Map<BlockPos, BlockState> states, List<SectionBucket> sections) {
    }

    private record ProjectionMeshCache(ProjectionData projection, int version, Map<SectionKey, SectionMesh> meshes) {
    }

    private static class SectionMesh implements AutoCloseable {
        private final VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        private boolean compiled;
        private boolean hasData;
        private boolean failed;

        private VertexBuffer vertexBuffer() {
            return vertexBuffer;
        }

        private boolean compiled() {
            return compiled;
        }

        private void setCompiled(boolean compiled) {
            this.compiled = compiled;
        }

        private boolean hasData() {
            return hasData;
        }

        private void setHasData(boolean hasData) {
            this.hasData = hasData;
        }

        private boolean failed() {
            return failed;
        }

        private void setFailed(boolean failed) {
            this.failed = failed;
        }

        @Override
        public void close() {
            vertexBuffer.close();
        }
    }

    private record SectionKey(int x, int y, int z) {
        private static SectionKey from(BlockPos pos, BlockPos origin) {
            return new SectionKey(
                    Math.floorDiv(pos.getX() - origin.getX(), SECTION_SIZE),
                    Math.floorDiv(pos.getY() - origin.getY(), SECTION_SIZE),
                    Math.floorDiv(pos.getZ() - origin.getZ(), SECTION_SIZE)
            );
        }
    }

    private record SectionBucket(SectionKey key, List<ProjectionData.ProjectionBlock> blocks, AABB bounds) {
    }

    private record SectionBucketBuilder(SectionKey key, List<ProjectionData.ProjectionBlock> blocks) {
        private SectionBucketBuilder(SectionKey key) {
            this(key, new java.util.ArrayList<>());
        }

        private SectionBucket build(BlockPos origin) {
            double minX = origin.getX() + key.x() * SECTION_SIZE;
            double minY = origin.getY() + key.y() * SECTION_SIZE;
            double minZ = origin.getZ() + key.z() * SECTION_SIZE;
            return new SectionBucket(key, blocks, new AABB(minX, minY, minZ, minX + SECTION_SIZE, minY + SECTION_SIZE, minZ + SECTION_SIZE));
        }
    }
}
