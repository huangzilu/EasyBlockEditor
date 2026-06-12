package com.l1ght.ebe.client.ui;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class ViewportFlightController {

    private static final double SENSITIVITY = 0.15;

    private static boolean flying = false;
    private static double lastCursorX;
    private static double lastCursorY;
    private static boolean haveLastCursor = false;

    public static boolean isFlying() {
        return flying;
    }

    public static void toggle() {
        if (flying) disable();
        else enable();
    }

    public static void enable() {
        if (flying) return;
        if (ViewportFactory.getScene() == null) return;
        flying = true;
        haveLastCursor = false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        ViewportFactory.setSceneDraggableExternal(false);
    }

    public static void disable() {
        if (!flying) return;
        flying = false;
        haveLastCursor = false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        ViewportFactory.setSceneDraggableExternal(true);
    }

    public static void tickMouseLook() {
        if (!flying) return;
        var scene = ViewportFactory.getScene();
        if (scene == null) return;

        long window = Minecraft.getInstance().getWindow().getWindow();
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(window, cx, cy);

        if (!haveLastCursor) {
            lastCursorX = cx[0];
            lastCursorY = cy[0];
            haveLastCursor = true;
            return;
        }

        double dx = cx[0] - lastCursorX;
        double dy = cy[0] - lastCursorY;
        lastCursorX = cx[0];
        lastCursorY = cy[0];
        if (dx == 0 && dy == 0) return;

        float yaw = scene.getRotationYaw() + (float) (dx * SENSITIVITY);
        float pitch = scene.getRotationPitch() - (float) (dy * SENSITIVITY);
        pitch = Math.max(-89.9f, Math.min(89.9f, pitch));
        scene.setCameraYawAndPitch(yaw, pitch);
    }
}
