package com.l1ght.ebe.client.renderer;

import com.lowdragmc.lowdraglib2.client.scene.FBOWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * FBO renderer optimized for expensive Iris viewport passes.
 * It redraws the scene only when marked dirty or when the mouse changes, then
 * reuses the previous FBO texture for static frames.
 */
public class CachedIrisWorldSceneRenderer extends FBOWorldSceneRenderer {
    private boolean dirty = true;
    private float lastMouseX = Float.NaN;
    private float lastMouseY = Float.NaN;

    public CachedIrisWorldSceneRenderer(Level world, int resolutionWidth, int resolutionHeight) {
        super(world, resolutionWidth, resolutionHeight);
    }

    public void markDirty() {
        dirty = true;
    }

    @Override
    public void setFBOSize(int resolutionWidth, int resolutionHeight) {
        super.setFBOSize(resolutionWidth, resolutionHeight);
        markDirty();
    }

    @Override
    public WorldSceneRenderer useCacheBuffer(boolean useCache) {
        WorldSceneRenderer renderer = super.useCacheBuffer(useCache);
        markDirty();
        return renderer;
    }

    @Override
    public WorldSceneRenderer needCompileCache() {
        WorldSceneRenderer renderer = super.needCompileCache();
        markDirty();
        return renderer;
    }

    @Override
    public WorldSceneRenderer addRenderedBlocks(Collection<BlockPos> blocks, ISceneBlockRenderHook renderHook) {
        WorldSceneRenderer renderer = super.addRenderedBlocks(blocks, renderHook);
        markDirty();
        return renderer;
    }

    @Override
    public WorldSceneRenderer removeRenderedBlocks(Collection<BlockPos> blocks) {
        WorldSceneRenderer renderer = super.removeRenderedBlocks(blocks);
        markDirty();
        return renderer;
    }

    @Override
    public WorldSceneRenderer removeAllRenderedBlocks() {
        WorldSceneRenderer renderer = super.removeAllRenderedBlocks();
        markDirty();
        return renderer;
    }

    @Override
    public void setCameraLookAt(Vector3f eyePos, Vector3f lookAt, Vector3f worldUp) {
        super.setCameraLookAt(eyePos, lookAt, worldUp);
        markDirty();
    }

    @Override
    public void setCameraLookAt(Vector3f lookAt, double radius, double yaw, double pitch) {
        super.setCameraLookAt(lookAt, radius, yaw, pitch);
        markDirty();
    }

    @Override
    public void setCameraOrtho(float x, float y, float z) {
        super.setCameraOrtho(x, y, z);
        markDirty();
    }

    @Override
    public void setCameraOrtho(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        super.setCameraOrtho(minX, maxX, minY, maxY, minZ, maxZ);
        markDirty();
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, float x, float y, float width, float height, float mouseX, float mouseY) {
        boolean mouseChanged = mouseX != lastMouseX || mouseY != lastMouseY;
        boolean invalidFbo = getFbo() == null || getFbo().frameBufferId < 0;
        if (dirty || mouseChanged || invalidFbo) {
            drawScene(x, y, width, height, mouseX, mouseY);
            dirty = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        if (getFbo() == null || getFbo().frameBufferId < 0) {
            return;
        }

        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, getFbo().getColorTextureId());

        var pose = poseStack.last().pose();
        bufferBuilder.addVertex(pose, x + width, y + height, 0).setUv(1, 0);
        bufferBuilder.addVertex(pose, x + width, y, 0).setUv(1, 1);
        bufferBuilder.addVertex(pose, x, y, 0).setUv(0, 1);
        bufferBuilder.addVertex(pose, x, y + height, 0).setUv(0, 0);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
    }
}
