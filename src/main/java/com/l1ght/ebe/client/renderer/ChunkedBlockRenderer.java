package com.l1ght.ebe.client.renderer;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class ChunkedBlockRenderer {

    private static final int SECTION_SIZE = 16;
    private static final Logger LOG = LoggerFactory.getLogger("EBE/ChunkedRenderer");
    private static final List<RenderType> LAYERS = RenderType.chunkBufferLayers();

    private TrackedDummyWorld world;
    private final Map<SectionPos, SectionData> sections = new HashMap<>();
    private final Set<BlockPos> allPositions = new HashSet<>();

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
        public boolean compiled = false;

        public SectionData() {
            vertexBuffers = new VertexBuffer[LAYERS.size()];
            hasData = new boolean[LAYERS.size()];
            for (int i = 0; i < LAYERS.size(); i++) {
                vertexBuffers[i] = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
        }

        public void close() {
            for (var vb : vertexBuffers) {
                vb.close();
            }
        }
    }

    public void setWorld(TrackedDummyWorld world) {
        this.world = world;
        clearAll();
    }

    public void clearAll() {
        sections.values().forEach(SectionData::close);
        sections.clear();
        allPositions.clear();
    }

    public void addPosition(BlockPos pos) {
        allPositions.add(pos.immutable());
    }

    public void removePosition(BlockPos pos) {
        allPositions.remove(pos);
    }

    public void markDirty(BlockPos pos) {
        var sp = SectionPos.fromBlock(pos);
        var data = sections.get(sp);
        if (data != null) {
            data.compiled = false;
        }
    }

    public void compileSection(SectionPos sp) {
        var data = sections.computeIfAbsent(sp, k -> new SectionData());

        var mc = Minecraft.getInstance();
        var brd = mc.getBlockRenderer();
        var randomSource = RandomSource.createNewThreadLocalInstance();

        int baseX = sp.x * SECTION_SIZE;
        int baseY = sp.y * SECTION_SIZE;
        int baseZ = sp.z * SECTION_SIZE;

        List<BlockPos> sectionPositions = new ArrayList<>();
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                    if (!allPositions.contains(pos)) continue;
                    var state = world.getBlockState(pos);
                    if (state == null || state.isAir()) continue;
                    if (state.getRenderShape() == RenderShape.MODEL) {
                        sectionPositions.add(pos);
                    }
                }
            }
        }

        if (sectionPositions.isEmpty()) {
            for (int i = 0; i < LAYERS.size(); i++) {
                data.hasData[i] = false;
            }
            data.compiled = true;
            return;
        }

        for (int i = 0; i < LAYERS.size(); i++) {
            RenderType layer = LAYERS.get(i);
            try {
                var bufferBuilder = new BufferBuilder(
                        new ByteBufferBuilder(layer.bufferSize()),
                        com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.BLOCK
                );
                PoseStack poseStack = new PoseStack();

                for (var pos : sectionPositions) {
                    var state = world.getBlockState(pos);
                    if (state == null || state.isAir()) continue;
                    try {
                        poseStack.pushPose();
                        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                        brd.renderBatched(state, pos, world, poseStack, bufferBuilder, false, randomSource, ModelData.EMPTY, layer);
                        poseStack.popPose();
                    } catch (Exception e) {
                        // skip problematic blocks
                    }
                }

                MeshData meshData = bufferBuilder.build();
                if (meshData != null) {
                    data.hasData[i] = true;
                    data.vertexBuffers[i].bind();
                    data.vertexBuffers[i].upload(meshData);
                    VertexBuffer.unbind();
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

    public void compileAll() {
        Map<SectionPos, Set<BlockPos>> sectionBlocks = new HashMap<>();
        for (var pos : allPositions) {
            sectionBlocks.computeIfAbsent(SectionPos.fromBlock(pos), k -> new HashSet<>()).add(pos);
        }
        for (var sp : sectionBlocks.keySet()) {
            compileSection(sp);
        }
    }

    public void compileDirty() {
        for (var entry : sections.entrySet()) {
            if (!entry.getValue().compiled) {
                compileSection(entry.getKey());
            }
        }
        sections.entrySet().removeIf(e -> {
            if (!e.getValue().compiled) return false;
            boolean hasAnyData = false;
            for (boolean b : e.getValue().hasData) hasAnyData |= b;
            if (!hasAnyData && !sectionHasBlocks(e.getKey())) {
                e.getValue().close();
                return true;
            }
            return false;
        });
    }

    private boolean sectionHasBlocks(SectionPos sp) {
        int baseX = sp.x * SECTION_SIZE;
        int baseY = sp.y * SECTION_SIZE;
        int baseZ = sp.z * SECTION_SIZE;
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    if (allPositions.contains(new BlockPos(baseX + x, baseY + y, baseZ + z))) return true;
                }
            }
        }
        return false;
    }

    public void render() {
        compileDirty();

        for (int i = 0; i < LAYERS.size(); i++) {
            RenderType layer = LAYERS.get(i);
            layer.setupRenderState();

            ShaderInstance shader = RenderSystem.getShader();
            setupShaderUniforms(shader);
            RenderSystem.setupShaderLights(shader);
            shader.apply();

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            for (var entry : sections.entrySet()) {
                var data = entry.getValue();
                if (!data.compiled || !data.hasData[i]) continue;

                var vb = data.vertexBuffers[i];
                if (vb.isInvalid()) continue;

                vb.bind();
                vb.draw();
            }

            shader.clear();
            VertexBuffer.unbind();
            layer.clearRenderState();
        }
    }

    private void setupShaderUniforms(ShaderInstance shader) {
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
        for (int j = 0; j < 12; ++j) {
            int tex = RenderSystem.getShaderTexture(j);
            shader.setSampler("Sampler" + j, tex);
        }
    }

    public Vector3f calculateCenter() {
        if (allPositions.isEmpty()) return new Vector3f(0, 0, 0);
        float cx = 0, cy = 0, cz = 0;
        for (var pos : allPositions) {
            cx += pos.getX();
            cy += pos.getY();
            cz += pos.getZ();
        }
        int n = allPositions.size();
        return new Vector3f(cx / n + 0.5f, cy / n + 0.5f, cz / n + 0.5f);
    }

    public float calculateZoom() {
        if (allPositions.isEmpty()) return 8;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var pos : allPositions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        float dx = maxX - minX + 1;
        float dy = maxY - minY + 1;
        float dz = maxZ - minZ + 1;
        return Math.max(Math.max(dx, dy), dz) * 0.8f;
    }

    public boolean isEmpty() {
        return allPositions.isEmpty();
    }

    public Set<BlockPos> getAllPositions() {
        return allPositions;
    }
}
