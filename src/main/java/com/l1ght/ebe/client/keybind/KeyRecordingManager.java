package com.l1ght.ebe.client.keybind;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

@OnlyIn(Dist.CLIENT)
public class KeyRecordingManager {

    private static EBEKeyBinding target;
    private static boolean recording;
    private static final IntSet heldKeys = new IntOpenHashSet();
    private static final IntSet heldMouseButtons = new IntOpenHashSet();
    private static int capturedModifiers;
    private static int capturedKeyCode = -1;
    private static boolean capturedIsMouse;
    private static Runnable onComplete;

    public static boolean isRecording() { return recording; }

    public static void startRecording(EBEKeyBinding binding, Runnable onComplete) {
        target = binding;
        recording = true;
        heldKeys.clear();
        heldMouseButtons.clear();
        capturedModifiers = 0;
        capturedKeyCode = -1;
        capturedIsMouse = false;
        KeyRecordingManager.onComplete = onComplete;
    }

    public static void onKeyPress(int keyCode, int scanCode, int modifiers) {
        if (!recording) return;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelRecording();
            return;
        }
        heldKeys.add(keyCode);
        if (!isModifierKey(keyCode)) {
            capturedKeyCode = keyCode;
            capturedModifiers = modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT);
            capturedIsMouse = false;
        } else {
            if (capturedKeyCode < 0) {
                capturedModifiers |= modFromKey(keyCode);
            }
        }
    }

    public static void onKeyRelease(int keyCode) {
        if (!recording) return;
        heldKeys.remove(keyCode);
        checkComplete();
    }

    public static void onMousePress(int button, int modifiers) {
        if (!recording) return;
        heldMouseButtons.add(button);
        capturedKeyCode = button;
        capturedModifiers = modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT);
        capturedIsMouse = true;
    }

    public static void onMouseRelease(int button) {
        if (!recording) return;
        heldMouseButtons.remove(button);
        checkComplete();
    }

    private static void checkComplete() {
        if (heldKeys.isEmpty() && heldMouseButtons.isEmpty() && capturedKeyCode >= 0) {
            target.setBinding(capturedKeyCode, capturedModifiers, capturedIsMouse);
            saveBinding(target);
            recording = false;
            if (onComplete != null) {
                var cb = onComplete;
                onComplete = null;
                cb.run();
            }
        }
    }

    public static void cancelRecording() {
        recording = false;
        onComplete = null;
    }

    private static boolean isModifierKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
            || keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
            || keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT;
    }

    private static int modFromKey(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) return GLFW.GLFW_MOD_CONTROL;
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) return GLFW.GLFW_MOD_SHIFT;
        if (keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT) return GLFW.GLFW_MOD_ALT;
        return 0;
    }

    private static void saveBinding(EBEKeyBinding binding) {
        var config = com.l1ght.ebe.config.EBEClientConfig.KEYBINDINGS.get();
        var map = com.l1ght.ebe.config.EBEClientConfig.deserializeKeybindings(config);
        map.put(binding.getId(), binding.serialize());
        com.l1ght.ebe.config.EBEClientConfig.KEYBINDINGS.set(com.l1ght.ebe.config.EBEClientConfig.serializeKeybindings(map));
        com.l1ght.ebe.config.EBEClientConfig.SPEC.save();
    }
}
