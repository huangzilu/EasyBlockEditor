package com.l1ght.ebe.client.renderer;

import com.l1ght.ebe.EBEMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

@EventBusSubscriber(modid = EBEMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EBEViewportShaders {
    private static ShaderInstance projectionLodShader;

    private EBEViewportShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "projection_lod"),
                            DefaultVertexFormat.POSITION_COLOR
                    ),
                    shader -> projectionLodShader = shader
            );
        } catch (IOException e) {
            EBEMod.LOGGER.warn("Failed to register EBE projection LOD shader; falling back to vanilla position_color", e);
            projectionLodShader = null;
        }
    }

    public static ShaderInstance projectionLodShader() {
        return projectionLodShader;
    }

    public static boolean hasProjectionLodShader() {
        return projectionLodShader != null;
    }

    public static void configureProjectionLod(float gridScale, float gridStrength,
                                              float depthFadeDistance, float alphaMultiplier) {
        if (projectionLodShader == null) {
            return;
        }
        projectionLodShader.safeGetUniform("LodGridScale").set(gridScale);
        projectionLodShader.safeGetUniform("LodGridStrength").set(gridStrength);
        projectionLodShader.safeGetUniform("LodDepthFadeDistance").set(depthFadeDistance);
        projectionLodShader.safeGetUniform("LodAlphaMultiplier").set(alphaMultiplier);
    }
}
