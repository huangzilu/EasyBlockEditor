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

/**
 * Registers the custom shaders used by the first-launch splash animation:
 * a full-screen background effect (scanlines / glowing border / CRT tear /
 * dust dissolve) and a logo effect (mosaic resolve / light scan / dissolve).
 */
@EventBusSubscriber(modid = EBEMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EBESplashShaders {
    private static ShaderInstance bgShader;
    private static ShaderInstance logoShader;

    private EBESplashShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "splash_bg"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> bgShader = shader
            );
        } catch (IOException e) {
            EBEMod.LOGGER.warn("Failed to register EBE splash background shader", e);
            bgShader = null;
        }
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "splash_logo"),
                            DefaultVertexFormat.POSITION_TEX_COLOR
                    ),
                    shader -> logoShader = shader
            );
        } catch (IOException e) {
            EBEMod.LOGGER.warn("Failed to register EBE splash logo shader", e);
            logoShader = null;
        }
    }

    public static ShaderInstance bgShader() {
        return bgShader;
    }

    public static ShaderInstance logoShader() {
        return logoShader;
    }

    public static boolean ready() {
        return bgShader != null && logoShader != null;
    }
}
