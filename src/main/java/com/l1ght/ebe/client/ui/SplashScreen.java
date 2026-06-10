package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.client.renderer.EBESplashShaders;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class SplashScreen extends Screen {
    private static final ResourceLocation LOGO =
            ResourceLocation.fromNamespaceAndPath(EBEMod.MOD_ID, "textures/gui/ebe_title.png");
    private static final int LOGO_W = 1022;
    private static final int LOGO_H = 173;

    private static final float T_SCAN_END = 2.0f;
    private static final float T_FADE_END = 3.5f;
    private static final float T_TEAR_END = 5.0f;
    private static final float T_LIGHT_END = 7.0f;
    private static final float T_DISSOLVE_END = 9.0f;
    private static final float TOTAL = T_DISSOLVE_END;

    private final Runnable onFinished;
    private long startMillis = -1L;
    private boolean finished = false;

    public SplashScreen(Runnable onFinished) {
        super(Component.translatable("ebe.splash.title"));
        this.onFinished = onFinished;
    }

    @Override
    protected void init() {
        super.init();
        if (startMillis < 0L) {
            startMillis = net.minecraft.Util.getMillis();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private void finish() {
        if (finished) return;
        finished = true;
        if (onFinished != null) onFinished.run();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        finish();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        finish();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float t = (net.minecraft.Util.getMillis() - startMillis) / 1000.0f;
        if (t >= TOTAL || !EBESplashShaders.ready()) {
            finish();
            return;
        }

        int sw = this.width;
        int sh = this.height;

        float introFade = clamp01(t / 0.5f);

        float scanProgress = clamp01(t / T_SCAN_END);
        float fadeIn = clamp01((t - T_SCAN_END) / (T_FADE_END - T_SCAN_END));
        float mosaic = 1.0f - fadeIn;
        float tear = 0.0f;
        if (t >= T_FADE_END && t < T_TEAR_END) {
            float p = (t - T_FADE_END) / (T_TEAR_END - T_FADE_END);
            tear = (float) Math.sin(p * Math.PI);
        }
        float lightScan = -1.0f;
        if (t >= T_TEAR_END && t < T_LIGHT_END) {
            lightScan = (t - T_TEAR_END) / (T_LIGHT_END - T_TEAR_END);
        }
        float dissolve = 0.0f;
        if (t >= T_LIGHT_END) {
            dissolve = clamp01((t - T_LIGHT_END) / (T_DISSOLVE_END - T_LIGHT_END));
        }

        float logoFade = (t >= T_FADE_END) ? 1.0f : fadeIn;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        renderBackground(graphics, t, introFade, scanProgress, tear, dissolve, sw, sh);

        if (logoFade > 0.001f) {
            renderLogo(graphics, t, logoFade, mosaic, tear, lightScan, dissolve, sw, sh);
        }

        RenderSystem.depthMask(true);

        if (dissolve <= 0.0f) {
            int hintAlpha = (int) (Mth.clamp(scanProgress, 0f, 1f) * 140) << 24;
            if (hintAlpha != 0) {
                var hint = Component.translatable("ebe.splash.skip");
                graphics.drawCenteredString(this.font, hint, sw / 2,
                        sh - 24, 0x00FFFFFF | hintAlpha);
            }
        }
    }

    private void renderBackground(GuiGraphics graphics, float t, float introFade, float scan, float tear,
                                  float dissolve, int sw, int sh) {
        ShaderInstance shader = EBESplashShaders.bgShader();
        if (shader == null) return;
        setF(shader, "STime", t);
        setF(shader, "IntroFade", introFade);
        setF(shader, "ScanProgress", scan);
        setF(shader, "TearProgress", tear);
        setF(shader, "DissolveProgress", dissolve);
        var u = shader.getUniform("ScreenSize");
        if (u != null) u.set((float) sw, (float) sh);

        RenderSystem.setShader(() -> shader);
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(pose, 0, sh, 0).setUv(0, 0).setColor(0xFFFFFFFF);
        bb.addVertex(pose, sw, sh, 0).setUv(1, 0).setColor(0xFFFFFFFF);
        bb.addVertex(pose, sw, 0, 0).setUv(1, 1).setColor(0xFFFFFFFF);
        bb.addVertex(pose, 0, 0, 0).setUv(0, 1).setColor(0xFFFFFFFF);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private void renderLogo(GuiGraphics graphics, float t, float fade, float mosaic, float tear,
                            float lightScan, float dissolve, int sw, int sh) {
        ShaderInstance shader = EBESplashShaders.logoShader();
        if (shader == null) return;

        float targetW = Math.min(sw * 0.6f, LOGO_W);
        float scale = targetW / LOGO_W;
        float w = LOGO_W * scale;
        float h = LOGO_H * scale;
        float x = (sw - w) / 2f;
        float y = (sh - h) / 2f;

        setF(shader, "STime", t);
        setF(shader, "FadeIn", fade);
        setF(shader, "Mosaic", mosaic);
        setF(shader, "TearProgress", tear);
        setF(shader, "LightScan", lightScan);
        setF(shader, "DissolveProgress", dissolve);
        var u = shader.getUniform("LogoSize");
        if (u != null) u.set((float) LOGO_W, (float) LOGO_H);

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, LOGO);

        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        bb.addVertex(pose, x, y + h, 0).setUv(0, 1).setColor(0xFFFFFFFF);
        bb.addVertex(pose, x + w, y + h, 0).setUv(1, 1).setColor(0xFFFFFFFF);
        bb.addVertex(pose, x + w, y, 0).setUv(1, 0).setColor(0xFFFFFFFF);
        bb.addVertex(pose, x, y, 0).setUv(0, 0).setColor(0xFFFFFFFF);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private static void setF(ShaderInstance shader, String name, float value) {
        var u = shader.getUniform(name);
        if (u != null) u.set(value);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
