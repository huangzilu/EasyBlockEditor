package com.l1ght.ebe.client.renderer;

import com.l1ght.ebe.config.EBEClientConfig;
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
 * It redraws the scene when marked dirty and throttles mouse-only redraws, then
 * reuses the previous FBO texture for static frames.
 */
public class CachedIrisWorldSceneRenderer extends FBOWorldSceneRenderer {
    private static final long BALANCED_MOUSE_REDRAW_INTERVAL_NANOS = 50_000_000L;
    private static final long PERFORMANCE_MOUSE_REDRAW_INTERVAL_NANOS = 100_000_000L;
    private boolean dirty = true;
    private float lastMouseX = Float.NaN;
    private float lastMouseY = Float.NaN;
    private long lastMouseRedrawNanos;
    private int requestedWidth;
    private int requestedHeight;
    private int appliedWidth;
    private int appliedHeight;
    private int cameraMovingFrames;

    public CachedIrisWorldSceneRenderer(Level world, int resolutionWidth, int resolutionHeight) {
        super(world, resolutionWidth, resolutionHeight);
        this.requestedWidth = resolutionWidth;
        this.requestedHeight = resolutionHeight;
        this.appliedWidth = resolutionWidth;
        this.appliedHeight = resolutionHeight;
    }

    public void markDirty() {
        dirty = true;
    }

    @Override
    public void setFBOSize(int resolutionWidth, int resolutionHeight) {
        requestedWidth = resolutionWidth;
        requestedHeight = resolutionHeight;
        appliedWidth = resolutionWidth;
        appliedHeight = resolutionHeight;
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
        cameraMovingFrames = 8;
        markDirty();
    }

    @Override
    public void setCameraLookAt(Vector3f lookAt, double radius, double yaw, double pitch) {
        super.setCameraLookAt(lookAt, radius, yaw, pitch);
        cameraMovingFrames = 8;
        markDirty();
    }

    @Override
    public void setCameraOrtho(float x, float y, float z) {
        super.setCameraOrtho(x, y, z);
        cameraMovingFrames = 8;
        markDirty();
    }

    @Override
    public void setCameraOrtho(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
        super.setCameraOrtho(minX, maxX, minY, maxY, minZ, maxZ);
        cameraMovingFrames = 8;
        markDirty();
    }

    @Override
    public void render(@Nonnull PoseStack poseStack, float x, float y, float width, float height, float mouseX, float mouseY) {
        boolean cameraMoving = cameraMovingFrames > 0;
        if (cameraMovingFrames > 0) {
            cameraMovingFrames--;
        }
        applyDynamicResolution(cameraMoving);

        boolean mouseChanged = mouseX != lastMouseX || mouseY != lastMouseY;
        boolean mouseRedrawDue = mouseChanged && shouldRedrawForMouseMove();
        boolean invalidFbo = getFbo() == null || getFbo().frameBufferId < 0;
        if (dirty || mouseRedrawDue || invalidFbo) {
            drawScene(x, y, width, height, mouseX, mouseY);
            dirty = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            if (mouseChanged) {
                lastMouseRedrawNanos = System.nanoTime();
            }
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

    private void applyDynamicResolution(boolean cameraMoving) {
        int targetWidth = requestedWidth;
        int targetHeight = requestedHeight;
        if (cameraMoving && shouldUseDynamicFboScale()) {
            double scale = Math.max(0.25D, Math.min(1.0D, EBEClientConfig.viewportMovingFboScale.get()));
            targetWidth = Math.max(64, (int) Math.round(requestedWidth * scale));
            targetHeight = Math.max(64, (int) Math.round(requestedHeight * scale));
        }
        if (targetWidth != appliedWidth || targetHeight != appliedHeight) {
            appliedWidth = targetWidth;
            appliedHeight = targetHeight;
            super.setFBOSize(targetWidth, targetHeight);
            markDirty();
        }
    }

    private boolean shouldRedrawForMouseMove() {
        String mode = EBEClientConfig.viewportPerformanceMode.get();
        if ("quality".equals(mode)) {
            return true;
        }
        long interval = "performance".equals(mode)
                ? PERFORMANCE_MOUSE_REDRAW_INTERVAL_NANOS
                : BALANCED_MOUSE_REDRAW_INTERVAL_NANOS;
        return System.nanoTime() - lastMouseRedrawNanos >= interval;
    }

    private boolean shouldUseDynamicFboScale() {
        String mode = EBEClientConfig.viewportPerformanceMode.get();
        if ("quality".equals(mode)) {
            return false;
        }
        return "performance".equals(mode) || EBEClientConfig.viewportDynamicFboScale.get();
    }
}
