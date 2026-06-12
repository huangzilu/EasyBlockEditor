package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.EBEKeyBindings;
import com.l1ght.ebe.editor.selection.BoxSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class BoxSelectionController {

    private static final int STEP_NORMAL = 1;
    private static final int STEP_FAST = 8;
    private static final int COOLDOWN_TICKS = 4;
    private static final int COOLDOWN_FAST_TICKS = 2;
    private static int moveCooldown = 0;

    public static boolean isActive() {
        return EditorUI.getBoxSelection().isActive()
                && !ViewportFlightController.isFlying()
                && isAltDown();
    }

    private static boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static boolean isFastDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    public static void tick() {
        if (!isActive()) return;
        if (moveCooldown > 0) { moveCooldown--; return; }

        boolean fast = isFastDown();
        int step = fast ? STEP_FAST : STEP_NORMAL;
        boolean shift = isShiftDown();

        Direction forward = ViewportFactory.getViewportFacing();
        Direction right = forward.getClockWise();

        boolean acted = false;
        if (EBEKeyBindings.FLY_FORWARD.isKeyDown() || EBEKeyBindings.REMOTE_FORWARD.isKeyDown()) {
            acted = apply(forward, step, shift);
        } else if (EBEKeyBindings.FLY_BACK.isKeyDown() || EBEKeyBindings.REMOTE_BACK.isKeyDown()) {
            acted = apply(forward.getOpposite(), step, shift);
        } else if (EBEKeyBindings.FLY_LEFT.isKeyDown() || EBEKeyBindings.REMOTE_LEFT.isKeyDown()) {
            acted = apply(right.getOpposite(), step, shift);
        } else if (EBEKeyBindings.FLY_RIGHT.isKeyDown() || EBEKeyBindings.REMOTE_RIGHT.isKeyDown()) {
            acted = apply(right, step, shift);
        }

        if (acted) {
            moveCooldown = fast ? COOLDOWN_FAST_TICKS : COOLDOWN_TICKS;
            EditorUI.onBoxSelectionChanged();
        }
    }

    public static boolean handleScroll(double delta) {
        if (!isActive()) return false;
        int step = isFastDown() ? STEP_FAST : STEP_NORMAL;
        boolean shift = isShiftDown();
        BoxSelection box = EditorUI.getBoxSelection();

        if (shift) {
            box.stretchFace(0, 1, 0, delta > 0 ? step : -step);
            EditorUI.onBoxSelectionChanged();
            return true;
        }

        Direction forward = ViewportFactory.getViewportFacing();
        if (delta > 0) box.move(forward.getStepX() * step, 0, forward.getStepZ() * step);
        else box.move(forward.getOpposite().getStepX() * step, 0, forward.getOpposite().getStepZ() * step);
        EditorUI.onBoxSelectionChanged();
        return true;
    }

    private static boolean apply(Direction dir, int step, boolean stretch) {
        BoxSelection box = EditorUI.getBoxSelection();
        if (stretch) {
            box.stretchFace(dir.getStepX(), dir.getStepY(), dir.getStepZ(), step);
        } else {
            box.move(dir.getStepX() * step, dir.getStepY() * step, dir.getStepZ() * step);
        }
        return true;
    }
}
