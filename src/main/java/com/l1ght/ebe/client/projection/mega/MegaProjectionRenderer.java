package com.l1ght.ebe.client.projection.mega;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.projection.ProjectionData;
import com.l1ght.ebe.projection.mega.ProjectionLodPyramid;
import com.l1ght.ebe.projection.mega.ProjectionLodPyramidBuilder;
import com.l1ght.ebe.projection.mega.ProjectionSparseStore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class MegaProjectionRenderer {
    private static final int MEGA_BLOCK_THRESHOLD = 50_000;
    private static final int MEGA_SECTION_THRESHOLD = 384;
    private static ProjectionData cachedProjection;
    private static int cachedRenderVersion = -1;
    private static ProjectionSparseStore cachedStore = ProjectionSparseStore.empty();
    private static ProjectionLodPyramid cachedPyramid = ProjectionLodPyramid.empty();

    private MegaProjectionRenderer() {
    }

    public static boolean shouldUse(ProjectionData projection) {
        if (projection == null) {
            return false;
        }
        if (EBEClientConfig.viewportSynchronousLoadBelowMb.get() <= 0.0D) {
            return false;
        }
        if (projection.getBlockCount() >= MEGA_BLOCK_THRESHOLD) {
            return true;
        }
        return projection.getSparseIndex().sectionSummaries().size() >= MEGA_SECTION_THRESHOLD;
    }

    public static boolean renderProjection(PoseStack poseStack, Matrix4f projectionMatrix, Frustum frustum, ProjectionData projection) {
        if (projection == null || projection.getBlocks().isEmpty()) {
            return false;
        }
        ensureCache(projection);
        var level = cachedPyramid.coarsestLevel();
        if (level == null || level.isEmpty()) {
            return false;
        }

        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        int renderDistance = EBEClientConfig.projectionRenderDistance.get();
        double maxDistanceSq = renderDistance <= 0 ? Double.POSITIVE_INFINITY : (double) renderDistance * renderDistance;
        float opacity = EBEClientConfig.projectionOpacity.get().floatValue();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int drawn = 0;
        for (var box : level.boxes()) {
            AABB bounds = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
            if (bounds.distanceToSqr(camPos) > maxDistanceSq) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(bounds)) {
                continue;
            }
            addBox(buffer, box, colorForState(box.stateId()), alphaFor(box, opacity));
            drawn++;
        }

        if (drawn > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        poseStack.popPose();
        return true;
    }

    public static void clear() {
        cachedProjection = null;
        cachedRenderVersion = -1;
        cachedStore = ProjectionSparseStore.empty();
        cachedPyramid = ProjectionLodPyramid.empty();
    }

    private static void ensureCache(ProjectionData projection) {
        if (cachedProjection == projection && cachedRenderVersion == projection.getRenderVersion()) {
            return;
        }
        cachedProjection = projection;
        cachedRenderVersion = projection.getRenderVersion();
        cachedStore = ProjectionSparseStore.fromProjection(projection);
        cachedPyramid = ProjectionLodPyramidBuilder.build(cachedStore);
    }

    private static int colorForState(int stateId) {
        int hash = Integer.rotateLeft(stateId * 0x45D9F3B, 7);
        int r = 80 + Math.floorMod(hash, 130);
        int g = 90 + Math.floorMod(hash >> 8, 120);
        int b = 105 + Math.floorMod(hash >> 16, 110);
        return (r << 16) | (g << 8) | b;
    }

    private static int alphaFor(ProjectionLodPyramid.LodBox box, float opacity) {
        float densityBoost = 0.38F + Math.min(0.42F, box.density() * 2.0F);
        return Math.max(24, Math.min(210, Math.round(255.0F * opacity * densityBoost)));
    }

    private static void addBox(BufferBuilder buffer, ProjectionLodPyramid.LodBox box, int rgb, int alpha) {
        float x0 = box.minX();
        float y0 = box.minY();
        float z0 = box.minZ();
        float x1 = box.maxX();
        float y1 = box.maxY();
        float z1 = box.maxZ();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        addQuad(buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, alpha);
        addQuad(buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, r, g, b, alpha);
        addQuad(buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, r, g, b, alpha);
        addQuad(buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
        addQuad(buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, alpha);
        addQuad(buffer, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r, g, b, Math.max(12, alpha / 2));
    }

    private static void addQuad(BufferBuilder buffer,
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
}
