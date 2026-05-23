package com.l1ght.ebe.client.projection;

import com.l1ght.ebe.client.keybind.EBEKeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class ProjectionController {

    private static boolean controlMode = false;
    private static final int STEP_NORMAL = 1;
    private static final int STEP_FAST = 10;
    private static int moveCooldown = 0;
    private static final int COOLDOWN_TICKS = 4;
    private static final int COOLDOWN_FAST_TICKS = 2;

    public static boolean isControlMode() { return controlMode; }

    public static void toggleControlMode() {
        controlMode = !controlMode;
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable(controlMode ? "ebe.remote.mode_on" : "ebe.remote.mode_off"),
                    true
            );
        }
    }

    public static void exitControlMode() {
        controlMode = false;
    }

    public static void tick() {
        if (!controlMode) return;
        if (!ProjectionManager.hasProjection()) return;
        if (moveCooldown > 0) { moveCooldown--; return; }

        var mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                     || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        int step = shift ? STEP_FAST : STEP_NORMAL;

        boolean moved = false;

        if (EBEKeyBindings.REMOTE_FORWARD.isKeyDown()) { ProjectionManager.moveOrigin(0, 0, -step); moved = true; }
        else if (EBEKeyBindings.REMOTE_BACK.isKeyDown()) { ProjectionManager.moveOrigin(0, 0, step); moved = true; }
        else if (EBEKeyBindings.REMOTE_LEFT.isKeyDown()) { ProjectionManager.moveOrigin(-step, 0, 0); moved = true; }
        else if (EBEKeyBindings.REMOTE_RIGHT.isKeyDown()) { ProjectionManager.moveOrigin(step, 0, 0); moved = true; }

        if (moved) {
            moveCooldown = shift ? COOLDOWN_FAST_TICKS : COOLDOWN_TICKS;
        }
    }

    public static boolean handleMouseScroll(double delta) {
        if (!controlMode) return false;
        if (!ProjectionManager.hasProjection()) return false;

        var mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                     || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        int step = shift ? STEP_FAST : STEP_NORMAL;

        if (delta > 0) { ProjectionManager.moveOrigin(0, step, 0); return true; }
        if (delta < 0) { ProjectionManager.moveOrigin(0, -step, 0); return true; }
        return false;
    }

    public static boolean handleMouseClick(int button, int modifiers) {
        if (!controlMode) return false;

        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (shift) {
            toggleControlMode();
            return true;
        }

        if (!ProjectionManager.hasProjection()) return false;

        if (EBEKeyBindings.REMOTE_ROTATE_CCW.matchesMouse(button, 0)) {
            ProjectionManager.rotateCounterClockwise90();
            return true;
        }
        if (EBEKeyBindings.REMOTE_ROTATE_CW.matchesMouse(button, 0)) {
            ProjectionManager.rotateClockwise90();
            return true;
        }
        return false;
    }
}
