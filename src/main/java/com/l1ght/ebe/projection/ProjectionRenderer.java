package com.l1ght.ebe.projection;

import com.l1ght.ebe.config.EBEClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ProjectionRenderer {

    public static void renderProjection(PoseStack poseStack, Matrix4f projectionMatrix) {
        if (!ProjectionManager.isProjectionVisible()) return;

        var projection = ProjectionManager.getProjection();
        if (projection == null) return;

        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        float opacity = ProjectionManager.getOpacity();
        List<ProjectionData.ProjectionBlock> blocks = projection.getBlocks();
        if (blocks.isEmpty()) return;

        var camera = mc.gameRenderer.getMainCamera();
        BlockPos camPos = camera.getBlockPosition();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;
        int renderDistance = EBEClientConfig.projectionRenderDistance.get();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, opacity);

        var bufferSource = mc.renderBuffers().bufferSource();
        var brd = mc.getBlockRenderer();
        var randomSource = RandomSource.createNewThreadLocalInstance();

        for (var pb : blocks) {
            BlockPos pos = pb.pos();
            if (pos.distManhattan(camPos) > renderDistance) continue;

            BlockState state = pb.state();
            if (state.getRenderShape() == RenderShape.INVISIBLE) continue;

            double dx = pos.getX() - camX;
            double dy = pos.getY() - camY;
            double dz = pos.getZ() - camZ;

            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);

            try {
                var model = brd.getBlockModel(state);
                var modelData = level.getModelData(pos);
                modelData = model.getModelData(level, pos, state, modelData);
                randomSource.setSeed(state.getSeed(pos));

                for (var layer : model.getRenderTypes(state, randomSource, modelData)) {
                    var consumer = bufferSource.getBuffer(layer);
                    brd.renderBatched(state, pos, level, poseStack, consumer, false, randomSource, modelData, layer);
                }
            } catch (Exception ignored) {}

            poseStack.popPose();
        }

        bufferSource.endBatch();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
