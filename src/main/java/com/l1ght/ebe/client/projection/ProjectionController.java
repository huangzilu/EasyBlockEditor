package com.l1ght.ebe.client.projection;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.client.keybind.EBEKeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * Litematica-style projection remote. There is no explicit "control mode" to toggle: the remote is
 * live whenever the player is holding the remote item. All operations are gated behind a modifier
 * key (Alt by default) so normal play isn't disturbed, and movement follows the player's facing
 * (snapped to the nearest cardinal axis) instead of fixed world axes.
 */
@OnlyIn(Dist.CLIENT)
public class ProjectionController {

    private static final int STEP_NORMAL = 1;
    private static final int STEP_FAST = 8;
    private static int moveCooldown = 0;
    private static final int COOLDOWN_TICKS = 4;
    private static final int COOLDOWN_FAST_TICKS = 2;

    // Whether the player is currently holding the remote AND the activation modifier is held.
    // Kept as the old name so existing callers (ClientEventHandler, ProjectionManager) still work.
    public static boolean isControlMode() {
        return isHoldingRemote() && isActivationModifierDown();
    }

    /** Holding the remote in either hand is enough to make the remote live. */
    public static boolean isHoldingRemote() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        return player.getMainHandItem().getItem() == EBEMod.REMOTE.get()
                || player.getOffhandItem().getItem() == EBEMod.REMOTE.get();
    }

    private static boolean isActivationModifierDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static boolean isFastModifierDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    // No-ops kept for backward compatibility with the old toggle-based call sites.
    public static void toggleControlMode() { }
    public static void exitControlMode() { }

    public static void tick() {
        if (!isControlMode()) return;
        if (!ProjectionManager.hasProjection()) return;
        if (moveCooldown > 0) { moveCooldown--; return; }

        boolean fast = isFastModifierDown();
        int step = fast ? STEP_FAST : STEP_NORMAL;

        // Movement is relative to where the player is looking, snapped to a cardinal direction.
        Direction forward = playerFacingHorizontal();
        Direction right = forward.getClockWise();

        boolean moved = false;
        if (EBEKeyBindings.REMOTE_FORWARD.isKeyDown()) {
            moveBy(forward, step); moved = true;
        } else if (EBEKeyBindings.REMOTE_BACK.isKeyDown()) {
            moveBy(forward.getOpposite(), step); moved = true;
        } else if (EBEKeyBindings.REMOTE_LEFT.isKeyDown()) {
            moveBy(right.getOpposite(), step); moved = true;
        } else if (EBEKeyBindings.REMOTE_RIGHT.isKeyDown()) {
            moveBy(right, step); moved = true;
        }

        if (moved) {
            moveCooldown = fast ? COOLDOWN_FAST_TICKS : COOLDOWN_TICKS;
        }
    }

    public static boolean handleMouseScroll(double delta) {
        if (!isControlMode()) return false;
        if (!ProjectionManager.hasProjection()) return false;

        int step = isFastModifierDown() ? STEP_FAST : STEP_NORMAL;

        if (isShiftDown()) {
            // Alt+Shift+wheel: move vertically.
            if (delta > 0) { ProjectionManager.moveOrigin(0, step, 0); return true; }
            if (delta < 0) { ProjectionManager.moveOrigin(0, -step, 0); return true; }
            return false;
        }

        // Alt+wheel: move along the player's facing (forward on scroll up).
        Direction forward = playerFacingHorizontal();
        if (delta > 0) { moveBy(forward, step); return true; }
        if (delta < 0) { moveBy(forward.getOpposite(), step); return true; }
        return false;
    }

    public static boolean handleMouseClick(int button, int modifiers) {
        // No mode toggle anymore; mouse clicks are handled by key bindings only (left as a hook
        // for configurable rotate-on-click bindings).
        if (!isControlMode()) return false;
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

    /** Handles Alt+key actions that aren't movement: rotate (R) and mirror (M). */
    public static boolean handleKeyPress(int key) {
        if (!isControlMode()) return false;
        if (!ProjectionManager.hasProjection()) return false;

        boolean shift = isShiftDown();
        if (key == GLFW.GLFW_KEY_R) {
            if (shift) ProjectionManager.rotateCounterClockwise90();
            else ProjectionManager.rotateClockwise90();
            return true;
        }
        if (key == GLFW.GLFW_KEY_M) {
            if (shift) ProjectionManager.toggleMirrorFrontBack();
            else ProjectionManager.toggleMirrorLeftRight();
            return true;
        }
        return false;
    }

    private static void moveBy(Direction dir, int step) {
        ProjectionManager.moveOrigin(dir.getStepX() * step, dir.getStepY() * step, dir.getStepZ() * step);
    }

    private static Direction playerFacingHorizontal() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return Direction.NORTH;
        return player.getDirection();
    }
}
