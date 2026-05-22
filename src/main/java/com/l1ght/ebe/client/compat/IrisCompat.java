package com.l1ght.ebe.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Optional Iris bridge. Keep every Iris touch behind reflection so EBE can run
 * unchanged without Iris/Sodium installed.
 */
public final class IrisCompat {
    private static final Logger LOG = LoggerFactory.getLogger("EBE/IrisCompat");

    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String IRIS_INTERNAL_CLASS = "net.irisshaders.iris.Iris";
    private static final String SHADER_PIPELINE_CLASS = "net.irisshaders.iris.pipeline.ShaderRenderingPipeline";
    private static final String IMMEDIATE_STATE_CLASS = "net.irisshaders.iris.vertices.ImmediateState";
    private static final boolean ENABLE_DEPTH_ALPHA_MASK = false;

    private static ImmediateStateSnapshot immediateStateSnapshot;
    private static MainRenderTargetSnapshot mainRenderTargetSnapshot;
    private static RenderTarget viewportDepthMaskTarget;

    private IrisCompat() {
    }

    public static ProbeResult probe(String stage) {
        boolean irisLoaded = classExists(IRIS_API_CLASS);
        if (!irisLoaded) {
            return new ProbeResult(stage, false, false, false, false, null, false, null, null);
        }

        boolean shaderPackInUse = false;
        boolean shadersEnabled = false;
        Object pipeline = null;
        String pipelineClassName = null;
        boolean shaderOverrideActive = false;
        String phaseName = null;
        String error = null;

        try {
            Object api = invokeStatic(IRIS_API_CLASS, "getInstance");
            shaderPackInUse = invokeBoolean(api, "isShaderPackInUse");

            Object config = invoke(api, "getConfig");
            shadersEnabled = config != null && invokeBoolean(config, "areShadersEnabled");

            pipeline = getPipelineNullable();
            if (pipeline != null) {
                pipelineClassName = pipeline.getClass().getName();
                phaseName = invokeToString(pipeline, "getPhase");
                shaderOverrideActive = isShaderRenderingPipeline(pipeline) && invokeBoolean(pipeline, "shouldOverrideShaders");
            }
        } catch (Throwable t) {
            error = t.getClass().getSimpleName() + ": " + t.getMessage();
            LOG.debug("Iris probe failed at stage '{}'", stage, t);
        }

        return new ProbeResult(
                stage,
                true,
                shaderPackInUse,
                shadersEnabled,
                pipeline != null,
                pipelineClassName,
                shaderOverrideActive,
                phaseName,
                error
        );
    }

    public static boolean shouldAttemptViewportShaders(String mode) {
        if ("off".equals(mode)) {
            return false;
        }
        ProbeResult probe = probe("mode-check");
        if ("iris".equals(mode)) {
            return probe.irisLoaded();
        }
        return probe.irisLoaded() && probe.shaderPackInUse();
    }

    /**
     * Starts an Iris pass against a viewport-local render target.
     *
     * Iris 1.21.1 hardcodes several pipeline operations to
     * Minecraft.getInstance().getMainRenderTarget(). For the editor viewport we
     * temporarily redirect that field to LDLib2's FBO so clear/resize/composite
     * work in viewport coordinates instead of leaking into the real screen.
     */
    public static boolean beginOffscreenViewportShaderPass(String stage, RenderTarget viewportTarget) {
        if (viewportTarget == null) {
            return false;
        }

        try {
            forceRestoreViewportShaderState("pre-offscreen-begin");

            ProbeResult probe = probe(stage + ":pre-begin");
            if (!probe.irisLoaded() || !probe.shaderPackInUse()) {
                return false;
            }

            Object pipeline = getPipelineNullable();
            if (pipeline == null || !isShaderRenderingPipeline(pipeline)) {
                return false;
            }

            swapMinecraftMainRenderTarget(viewportTarget);
            invoke(pipeline, "beginLevelRendering");
            beginImmediateLevelState();
            setWorldRenderingPhase(pipeline, "TERRAIN_SOLID");
            return true;
        } catch (Throwable t) {
            restoreImmediateLevelState();
            restoreMinecraftMainRenderTarget();
            LOG.warn("Failed to begin Iris offscreen viewport pass at stage '{}'", stage, t);
            return false;
        }
    }

    public static void forceRestoreViewportShaderState(String stage) {
        restoreImmediateLevelState();
        try {
            Object pipeline = getPipelineNullable();
            if (pipeline == null || !isShaderRenderingPipeline(pipeline)) {
                return;
            }

            setWorldRenderingPhase(pipeline, "NONE");
            invokeIfPresent(pipeline, "removePhaseIfNeeded");
            setBooleanField(pipeline, "isRenderingWorld", false);
            setBooleanField(pipeline, "isMainBound", false);
        } catch (Throwable t) {
            LOG.debug("Failed to force-restore Iris viewport shader state at stage '{}'", stage, t);
        } finally {
            restoreMinecraftMainRenderTarget();
        }
    }

    public static void setViewportRenderPhase(String phaseName) {
        try {
            Object pipeline = getPipelineNullable();
            if (pipeline == null || !isShaderRenderingPipeline(pipeline)) {
                return;
            }
            setWorldRenderingPhase(pipeline, phaseName);
        } catch (Throwable t) {
            LOG.debug("Failed to set Iris viewport phase '{}'", phaseName, t);
        }
    }

    public static void endOffscreenViewportShaderPass(String stage, boolean runComposite) {
        try {
            Object pipeline = getPipelineNullable();
            if (pipeline == null || !isShaderRenderingPipeline(pipeline)) {
                return;
            }

            if (runComposite) {
                if (ENABLE_DEPTH_ALPHA_MASK) {
                    captureViewportDepthMask();
                }
                setWorldRenderingPhase(pipeline, "TERRAIN_TRANSLUCENT");
                invoke(pipeline, "beginTranslucents");
                setWorldRenderingPhase(pipeline, "NONE");
                invoke(pipeline, "finalizeLevelRendering");
                if (ENABLE_DEPTH_ALPHA_MASK) {
                    applyViewportDepthAlphaMask();
                } else {
                    forceViewportTargetOpaqueAlpha();
                }
            } else {
                setWorldRenderingPhase(pipeline, "NONE");
                invokeIfPresent(pipeline, "removePhaseIfNeeded");
                setBooleanField(pipeline, "isRenderingWorld", false);
                setBooleanField(pipeline, "isMainBound", false);
            }
        } catch (Throwable t) {
            LOG.warn("Failed to end Iris offscreen viewport pass at stage '{}'", stage, t);
        } finally {
            restoreImmediateLevelState();
            restoreMinecraftMainRenderTarget();
        }
    }

    public static void releaseOffscreenViewportResources() {
        if (viewportDepthMaskTarget != null) {
            try {
                viewportDepthMaskTarget.destroyBuffers();
            } catch (Throwable t) {
                LOG.debug("Failed to release Iris viewport depth mask target", t);
            } finally {
                viewportDepthMaskTarget = null;
            }
        }
    }

    private static Object getPipelineNullable() throws ReflectiveOperationException {
        Object manager = invokeStatic(IRIS_INTERNAL_CLASS, "getPipelineManager");
        return manager == null ? null : invoke(manager, "getPipelineNullable");
    }

    private static boolean isShaderRenderingPipeline(Object pipeline) {
        try {
            Class<?> shaderPipeline = Class.forName(SHADER_PIPELINE_CLASS);
            return shaderPipeline.isInstance(pipeline);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeStatic(String className, String methodName) throws ReflectiveOperationException {
        Method method = Class.forName(className).getMethod(methodName);
        return method.invoke(null);
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static void invokeIfPresent(Object target, String methodName) {
        try {
            invoke(target, methodName);
        } catch (Throwable ignored) {
        }
    }

    private static void setBooleanField(Object target, String fieldName, boolean value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void beginImmediateLevelState() {
        try {
            Class<?> immediateState = Class.forName(IMMEDIATE_STATE_CLASS);
            boolean previousIsRenderingLevel = getStaticBoolean(immediateState, "isRenderingLevel");
            boolean previousRenderWithExtendedVertexFormat = getStaticBoolean(immediateState, "renderWithExtendedVertexFormat");

            var skipExtensionField = immediateState.getDeclaredField("skipExtension");
            skipExtensionField.setAccessible(true);
            var skipExtension = (ThreadLocal<Boolean>) skipExtensionField.get(null);
            boolean previousSkipExtension = Boolean.TRUE.equals(skipExtension.get());

            immediateStateSnapshot = new ImmediateStateSnapshot(
                    previousIsRenderingLevel,
                    previousRenderWithExtendedVertexFormat,
                    previousSkipExtension
            );

            setStaticBoolean(immediateState, "isRenderingLevel", true);
            setStaticBoolean(immediateState, "renderWithExtendedVertexFormat", true);
            skipExtension.set(false);
        } catch (Throwable t) {
            LOG.debug("Failed to begin Iris immediate level state", t);
        }
    }

    @SuppressWarnings("unchecked")
    private static void restoreImmediateLevelState() {
        if (immediateStateSnapshot == null) {
            return;
        }
        try {
            Class<?> immediateState = Class.forName(IMMEDIATE_STATE_CLASS);
            setStaticBoolean(immediateState, "isRenderingLevel", immediateStateSnapshot.isRenderingLevel());
            setStaticBoolean(immediateState, "renderWithExtendedVertexFormat", immediateStateSnapshot.renderWithExtendedVertexFormat());

            var skipExtensionField = immediateState.getDeclaredField("skipExtension");
            skipExtensionField.setAccessible(true);
            var skipExtension = (ThreadLocal<Boolean>) skipExtensionField.get(null);
            skipExtension.set(immediateStateSnapshot.skipExtension());
        } catch (Throwable t) {
            LOG.debug("Failed to restore Iris immediate level state", t);
        } finally {
            immediateStateSnapshot = null;
        }
    }

    private static boolean getStaticBoolean(Class<?> owner, String fieldName) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    private static void setStaticBoolean(Class<?> owner, String fieldName, boolean value) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setWorldRenderingPhase(Object pipeline, String phaseName) throws ReflectiveOperationException {
        Class phaseClass = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase");
        Object phase = Enum.valueOf(phaseClass, phaseName);
        Method method = pipeline.getClass().getMethod("setPhase", phaseClass);
        method.invoke(pipeline, phase);
    }

    private static boolean invokeBoolean(Object target, String methodName) throws ReflectiveOperationException {
        Object value = invoke(target, methodName);
        return value instanceof Boolean b && b;
    }

    private static String invokeToString(Object target, String methodName) {
        try {
            Object value = invoke(target, methodName);
            return value == null ? null : value.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void swapMinecraftMainRenderTarget(RenderTarget replacement) throws ReflectiveOperationException {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget current = minecraft.getMainRenderTarget();
        if (current == replacement) {
            return;
        }

        if (mainRenderTargetSnapshot != null) {
            restoreMinecraftMainRenderTarget();
        }

        Field field = findMinecraftMainRenderTargetField(minecraft, current);
        field.setAccessible(true);
        mainRenderTargetSnapshot = new MainRenderTargetSnapshot(field, current, replacement);
        field.set(minecraft, replacement);
    }

    private static Field findMinecraftMainRenderTargetField(Minecraft minecraft, RenderTarget current) throws ReflectiveOperationException {
        Class<?> owner = minecraft.getClass();
        for (Field field : owner.getDeclaredFields()) {
            if (!RenderTarget.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(minecraft);
            if (value == current) {
                return field;
            }
        }
        throw new NoSuchFieldException("Minecraft main RenderTarget field");
    }

    private static void restoreMinecraftMainRenderTarget() {
        if (mainRenderTargetSnapshot == null) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            mainRenderTargetSnapshot.field().setAccessible(true);
            mainRenderTargetSnapshot.field().set(minecraft, mainRenderTargetSnapshot.originalTarget());
            mainRenderTargetSnapshot.originalTarget().bindWrite(true);
        } catch (Throwable t) {
            LOG.warn("Failed to restore Minecraft main render target after Iris viewport pass", t);
        } finally {
            resetCommonRenderState();
            mainRenderTargetSnapshot = null;
        }
    }

    private static void captureViewportDepthMask() {
        if (mainRenderTargetSnapshot == null || mainRenderTargetSnapshot.replacementTarget() == null) {
            return;
        }
        try {
            RenderTarget viewportTarget = mainRenderTargetSnapshot.replacementTarget();
            ensureViewportDepthMaskTarget(viewportTarget.width, viewportTarget.height);
            viewportDepthMaskTarget.clear(Minecraft.ON_OSX);
            viewportDepthMaskTarget.copyDepthFrom(viewportTarget);
        } catch (Throwable t) {
            LOG.debug("Failed to capture Iris viewport depth mask", t);
        }
    }

    private static void ensureViewportDepthMaskTarget(int width, int height) {
        if (viewportDepthMaskTarget != null && viewportDepthMaskTarget.width == width && viewportDepthMaskTarget.height == height) {
            return;
        }
        releaseOffscreenViewportResources();
        viewportDepthMaskTarget = new MainTarget(width, height);
        viewportDepthMaskTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static void forceViewportTargetOpaqueAlpha() {
        if (mainRenderTargetSnapshot == null || mainRenderTargetSnapshot.replacementTarget() == null) {
            return;
        }

        try {
            RenderTarget viewportTarget = mainRenderTargetSnapshot.replacementTarget();
            viewportTarget.bindWrite(false);
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        } catch (Throwable t) {
            LOG.debug("Failed to force opaque alpha on Iris viewport target", t);
        } finally {
            resetCommonRenderState();
            RenderSystem.colorMask(true, true, true, true);
        }
    }

    private static void resetCommonRenderState() {
        try {
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
        } catch (Throwable ignored) {
        }
    }

    private static void applyViewportDepthAlphaMask() {
        if (mainRenderTargetSnapshot == null || mainRenderTargetSnapshot.replacementTarget() == null) {
            return;
        }

        try {
            RenderTarget viewportTarget = mainRenderTargetSnapshot.replacementTarget();
            if (viewportDepthMaskTarget != null
                    && viewportDepthMaskTarget.width == viewportTarget.width
                    && viewportDepthMaskTarget.height == viewportTarget.height) {
                viewportTarget.copyDepthFrom(viewportDepthMaskTarget);
            } else {
                forceViewportTargetOpaqueAlpha();
                return;
            }

            viewportTarget.bindWrite(false);
            Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            Matrix4fStack modelView = RenderSystem.getModelViewStack();

            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);

            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(0.0F, viewportTarget.width, viewportTarget.height, 0.0F, -1.0F, 1.0F),
                    VertexSorting.ORTHOGRAPHIC_Z
            );
            modelView.pushMatrix();
            modelView.identity();
            RenderSystem.applyModelViewMatrix();

            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            GL11.glDepthFunc(GL11.GL_GREATER);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            buffer.addVertex(0.0F, viewportTarget.height, -1.0F).setColor(255, 255, 255, 255);
            buffer.addVertex(viewportTarget.width, viewportTarget.height, -1.0F).setColor(255, 255, 255, 255);
            buffer.addVertex(viewportTarget.width, 0.0F, -1.0F).setColor(255, 255, 255, 255);
            buffer.addVertex(0.0F, 0.0F, -1.0F).setColor(255, 255, 255, 255);
            BufferUploader.drawWithShader(buffer.buildOrThrow());

            GL11.glDepthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.ORTHOGRAPHIC_Z);

            RenderSystem.colorMask(true, true, true, true);
            if (!hasAnyVisibleAlpha(viewportTarget)) {
                forceViewportTargetOpaqueAlpha();
            }
        } catch (Throwable t) {
            LOG.debug("Failed to apply Iris viewport depth alpha mask", t);
            forceViewportTargetOpaqueAlpha();
        } finally {
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
        }
    }

    private static boolean hasAnyVisibleAlpha(RenderTarget target) {
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        int[][] samples = {
                {target.width / 2, target.height / 2},
                {target.width / 3, target.height / 2},
                {target.width * 2 / 3, target.height / 2},
                {target.width / 2, target.height / 3},
                {target.width / 2, target.height * 2 / 3}
        };
        for (int[] sample : samples) {
            pixel.clear();
            GL11.glReadPixels(sample[0], sample[1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            int alpha = pixel.get(3) & 0xFF;
            if (alpha > 0) {
                return true;
            }
        }
        return false;
    }

    public record ProbeResult(
            String stage,
            boolean irisLoaded,
            boolean shaderPackInUse,
            boolean shadersEnabled,
            boolean pipelinePresent,
            String pipelineClassName,
            boolean shaderOverrideActive,
            String phaseName,
            String error
    ) {
        public boolean shaderCompositeCandidate() {
            return irisLoaded && shaderPackInUse && pipelinePresent;
        }
    }

    private record ImmediateStateSnapshot(
            boolean isRenderingLevel,
            boolean renderWithExtendedVertexFormat,
            boolean skipExtension
    ) {
    }

    private record MainRenderTargetSnapshot(
            Field field,
            RenderTarget originalTarget,
            RenderTarget replacementTarget
    ) {
    }
}
