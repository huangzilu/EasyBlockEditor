package com.l1ght.ebe.client.projection.mega;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.client.renderer.EBEViewportShaders;
import com.l1ght.ebe.projection.ProjectionData;
import com.l1ght.ebe.projection.mega.ProjectionLodPyramid;
import com.l1ght.ebe.projection.mega.ProjectionLodPyramidBuilder;
import com.l1ght.ebe.projection.mega.ProjectionShellMesh;
import com.l1ght.ebe.projection.mega.ProjectionShellMeshBuilder;
import com.l1ght.ebe.projection.mega.ProjectionSparseStore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class MegaProjectionRenderer {
    private static final int MEGA_BLOCK_THRESHOLD = 750_000;
    private static final int MEGA_SECTION_THRESHOLD = 8_192;
    private static final int WORLD_SHELL_SAMPLE_CAP = 160_000;
    private static ProjectionData cachedProjection;
    private static int cachedMeshVersion = -1;
    private static ProjectionSparseStore cachedStore = ProjectionSparseStore.empty();
    private static ProjectionLodPyramid cachedPyramid = ProjectionLodPyramid.empty();
    private static ProjectionShellMesh cachedShellMesh = ProjectionShellMesh.empty();
    private static VertexBuffer cachedShellVbo;
    private static boolean cachedShellVboDirty = true;
    private static boolean cachedShellVboHasData;

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
        if (cachedShellMesh == null || cachedShellMesh.isEmpty()) {
            return false;
        }

        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        var origin = projection.getOrigin();
        int renderDistance = EBEClientConfig.projectionRenderDistance.get();
        double maxDistanceSq = renderDistance <= 0 ? Double.POSITIVE_INFINITY : (double) renderDistance * renderDistance;
        float opacity = EBEClientConfig.projectionOpacity.get().floatValue();

        poseStack.pushPose();
        poseStack.translate(origin.getX() - camPos.x, origin.getY() - camPos.y, origin.getZ() - camPos.z);

        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.mul(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        if (renderDistance <= 0) {
            useProjectionLodShader(32.0F, 0.28F, 520.0F, opacity);
        } else {
            useProjectionLodShader(32.0F, 0.28F, 520.0F, 1.0F);
        }

        if (renderDistance <= 0 && renderCachedShellVbo()) {
            // Full cached shell draw: trades a little GPU overdraw for much lower CPU cost on mega projections.
        } else {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int drawn = 0;
            for (var face : cachedShellMesh.faces()) {
                AABB bounds = faceBounds(face, origin);
                if (bounds.distanceToSqr(camPos) > maxDistanceSq) {
                    continue;
                }
                if (frustum != null && !frustum.isVisible(bounds)) {
                    continue;
                }
                addFace(buffer, face, colorForState(face.stateId()), alphaFor(face, opacity));
                drawn++;
            }

            if (drawn > 0) {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            }
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
        cachedMeshVersion = -1;
        cachedStore = ProjectionSparseStore.empty();
        cachedPyramid = ProjectionLodPyramid.empty();
        cachedShellMesh = ProjectionShellMesh.empty();
        closeCachedShellVbo();
    }

    private static void ensureCache(ProjectionData projection) {
        if (cachedProjection == projection && cachedMeshVersion == projection.getMeshVersion()) {
            return;
        }
        cachedProjection = projection;
        cachedMeshVersion = projection.getMeshVersion();
        cachedStore = buildSampledStore(projection);
        cachedPyramid = ProjectionLodPyramidBuilder.build(cachedStore);
        cachedShellMesh = ProjectionShellMeshBuilder.build(cachedPyramid.coarsestLevel());
        closeCachedShellVbo();
    }

    private static void useProjectionLodShader(float gridScale, float gridStrength,
                                               float depthFadeDistance, float alphaMultiplier) {
        if (EBEViewportShaders.hasProjectionLodShader()) {
            RenderSystem.setShader(EBEViewportShaders::projectionLodShader);
            EBEViewportShaders.configureProjectionLod(gridScale, gridStrength, depthFadeDistance, alphaMultiplier);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
        }
    }

    private static boolean renderCachedShellVbo() {
        ensureCachedShellVbo();
        if (!cachedShellVboHasData || cachedShellVbo == null || cachedShellVbo.isInvalid() || cachedShellVbo.getFormat() == null) {
            return false;
        }
        setupVBODrawState();
        cachedShellVbo.bind();
        cachedShellVbo.draw();
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) {
            shader.clear();
        }
        VertexBuffer.unbind();
        return true;
    }

    private static void ensureCachedShellVbo() {
        if (!cachedShellVboDirty) {
            return;
        }
        closeCachedShellVbo();
        cachedShellVboDirty = false;
        cachedShellVboHasData = false;
        if (cachedShellMesh == null || cachedShellMesh.isEmpty()) {
            return;
        }
        try {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            int faces = 0;
            for (var face : cachedShellMesh.faces()) {
                addFace(buffer, face, colorForState(face.stateId()), alphaFor(face, 1.0F));
                faces++;
            }
            if (faces <= 0) {
                return;
            }
            MeshData meshData = buffer.build();
            if (meshData == null) {
                return;
            }
            cachedShellVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            cachedShellVbo.bind();
            cachedShellVbo.upload(meshData);
            VertexBuffer.unbind();
            cachedShellVboHasData = true;
        } catch (Exception ignored) {
            closeCachedShellVbo();
        }
    }

    private static void closeCachedShellVbo() {
        if (cachedShellVbo != null) {
            cachedShellVbo.close();
            cachedShellVbo = null;
        }
        cachedShellVboHasData = false;
        cachedShellVboDirty = true;
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

        RenderSystem.setupShaderLights(shader);
        shader.apply();
    }

    private static ProjectionSparseStore buildSampledStore(ProjectionData projection) {
        if (projection == null || projection.getBlocks().isEmpty()) {
            return ProjectionSparseStore.empty();
        }
        int total = projection.getBlockCount();
        int stride = Math.max(1, (total + WORLD_SHELL_SAMPLE_CAP - 1) / WORLD_SHELL_SAMPLE_CAP);
        var builder = ProjectionSparseStore.builder(Math.min(WORLD_SHELL_SAMPLE_CAP, total / stride + 1),
                projection.getRenderVersion(), projection.getMeshVersion());
        int visible = 0;
        for (var block : projection.getSparseIndex().blocks()) {
            if (visible++ % stride == 0) {
                builder.add(block.pos().subtract(projection.getOrigin()), block.state(), null);
                if (builder.size() >= WORLD_SHELL_SAMPLE_CAP) {
                    break;
                }
            }
        }
        return builder.build();
    }

    private static int colorForState(int stateId) {
        int hash = Integer.rotateLeft(stateId * 0x45D9F3B, 7);
        int r = 80 + Math.floorMod(hash, 130);
        int g = 90 + Math.floorMod(hash >> 8, 120);
        int b = 105 + Math.floorMod(hash >> 16, 110);
        return (r << 16) | (g << 8) | b;
    }

    private static int alphaFor(ProjectionShellMesh.Face face, float opacity) {
        float densityBoost = 0.48F + Math.min(0.40F, face.density() * 2.0F);
        return Math.max(42, Math.min(225, Math.round(255.0F * opacity * densityBoost)));
    }

    private static AABB faceBounds(ProjectionShellMesh.Face face, net.minecraft.core.BlockPos origin) {
        double minX = Math.min(face.minX(), face.maxX()) + origin.getX();
        double minY = Math.min(face.minY(), face.maxY()) + origin.getY();
        double minZ = Math.min(face.minZ(), face.maxZ()) + origin.getZ();
        double maxX = Math.max(face.minX(), face.maxX()) + origin.getX();
        double maxY = Math.max(face.minY(), face.maxY()) + origin.getY();
        double maxZ = Math.max(face.minZ(), face.maxZ()) + origin.getZ();
        double pad = 0.05D;
        return new AABB(minX - pad, minY - pad, minZ - pad, maxX + pad, maxY + pad, maxZ + pad);
    }

    private static void addFace(BufferBuilder buffer, ProjectionShellMesh.Face face, int rgb, int alpha) {
        float x0 = face.minX();
        float y0 = face.minY();
        float z0 = face.minZ();
        float x1 = face.maxX();
        float y1 = face.maxY();
        float z1 = face.maxZ();
        float shade = switch (face.direction()) {
            case UP -> 1.18F;
            case DOWN -> 0.62F;
            case NORTH, SOUTH -> 0.86F;
            case EAST, WEST -> 1.0F;
        };
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.min(255, Math.max(0, Math.round(r * shade)));
        g = Math.min(255, Math.max(0, Math.round(g * shade)));
        b = Math.min(255, Math.max(0, Math.round(b * shade)));

        switch (face.direction()) {
            case NORTH -> addQuad(buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, alpha);
            case SOUTH -> addQuad(buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, r, g, b, alpha);
            case WEST -> addQuad(buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, r, g, b, alpha);
            case EAST -> addQuad(buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, alpha);
            case UP -> addQuad(buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, alpha);
            case DOWN -> addQuad(buffer, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, r, g, b, Math.max(18, alpha / 2));
        }
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
