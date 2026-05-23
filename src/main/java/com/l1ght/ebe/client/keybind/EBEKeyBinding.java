package com.l1ght.ebe.client.keybind;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class EBEKeyBinding {

    private final String id;
    private final String category;
    private int keyCode;
    private int modifiers;
    private boolean isMouseButton;
    private final int defaultKeyCode;
    private final int defaultModifiers;
    private final boolean defaultIsMouseButton;

    public EBEKeyBinding(String id, String category, int keyCode, int modifiers, boolean isMouseButton) {
        this.id = id;
        this.category = category;
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        this.isMouseButton = isMouseButton;
        this.defaultKeyCode = keyCode;
        this.defaultModifiers = modifiers;
        this.defaultIsMouseButton = isMouseButton;
    }

    public String getId() { return id; }
    public String getCategory() { return category; }
    public int getKeyCode() { return keyCode; }
    public int getModifiers() { return modifiers; }
    public boolean isMouseButton() { return isMouseButton; }

    public boolean matchesKey(int keyCode, int modifiers) {
        if (isMouseButton) return false;
        return this.keyCode == keyCode && modBits(this.modifiers) == modBits(modifiers);
    }

    public boolean matchesMouse(int button, int modifiers) {
        if (!isMouseButton) return false;
        return this.keyCode == button && modBits(this.modifiers) == modBits(modifiers);
    }

    public boolean hasModifier(int mod) {
        return (modifiers & mod) != 0;
    }

    public boolean isKeyDown() {
        if (isMouseButton) return false;
        var mc = net.minecraft.client.Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
    }

    public void setBinding(int keyCode, int modifiers, boolean isMouseButton) {
        this.keyCode = keyCode;
        this.modifiers = modifiers;
        this.isMouseButton = isMouseButton;
    }

    public void resetToDefault() {
        this.keyCode = defaultKeyCode;
        this.modifiers = defaultModifiers;
        this.isMouseButton = defaultIsMouseButton;
    }

    public boolean isDefault() {
        return keyCode == defaultKeyCode && modBits(modifiers) == modBits(defaultModifiers) && isMouseButton == defaultIsMouseButton;
    }

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("Ctrl+");
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) sb.append("Alt+");
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) sb.append("Shift+");
        sb.append(keyDisplayName(keyCode, isMouseButton));
        return sb.toString();
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) sb.append("CTRL+");
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) sb.append("ALT+");
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) sb.append("SHIFT+");
        sb.append(keySerialName(keyCode, isMouseButton));
        return sb.toString();
    }

    public void deserialize(String s) {
        if (s == null || s.isEmpty()) return;
        String[] parts = s.toUpperCase().split("\\+");
        int newModifiers = 0;
        int newKeyCode = defaultKeyCode;
        boolean newIsMouse = defaultIsMouseButton;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (i < parts.length - 1) {
                switch (part) {
                    case "CTRL" -> newModifiers |= GLFW.GLFW_MOD_CONTROL;
                    case "ALT" -> newModifiers |= GLFW.GLFW_MOD_ALT;
                    case "SHIFT" -> newModifiers |= GLFW.GLFW_MOD_SHIFT;
                }
            } else {
                var parsed = parseKeyPart(part);
                newKeyCode = parsed.keyCode();
                newIsMouse = parsed.isMouse();
            }
        }
        this.keyCode = newKeyCode;
        this.modifiers = newModifiers;
        this.isMouseButton = newIsMouse;
    }

    private static int modBits(int mods) {
        return mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT);
    }

    private static String keyDisplayName(int keyCode, boolean isMouse) {
        if (isMouse) {
            return switch (keyCode) {
                case 0 -> "LMB";
                case 1 -> "RMB";
                case 2 -> "MMB";
                default -> "MB" + keyCode;
            };
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_PAGE_UP -> "PgUp";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PgDn";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CapsLk";
            case GLFW.GLFW_KEY_NUM_LOCK -> "NumLk";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "ScrLk";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "PrtSc";
            case GLFW.GLFW_KEY_PAUSE -> "Pause";
            default -> {
                if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
                }
                if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
                    yield "Num" + (keyCode - GLFW.GLFW_KEY_KP_0);
                }
                String name = GLFW.glfwGetKeyName(keyCode, 0);
                yield name != null ? name.toUpperCase() : "Key" + keyCode;
            }
        };
    }

    private static String keySerialName(int keyCode, boolean isMouse) {
        if (isMouse) {
            return switch (keyCode) {
                case 0 -> "LMB";
                case 1 -> "RMB";
                case 2 -> "MMB";
                default -> "MB" + keyCode;
            };
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_ESCAPE -> "ESCAPE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LEFT_SHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RIGHT_SHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LEFT_CONTROL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RIGHT_CONTROL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LEFT_ALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RIGHT_ALT";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_INSERT -> "INSERT";
            case GLFW.GLFW_KEY_DELETE -> "DELETE";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PAGE_UP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE_DOWN";
            default -> {
                if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
                }
                String name = GLFW.glfwGetKeyName(keyCode, 0);
                yield name != null ? name.toUpperCase() : "KEY_" + keyCode;
            }
        };
    }

    private static KeyParsed parseKeyPart(String part) {
        return switch (part) {
            case "LMB" -> new KeyParsed(0, true);
            case "RMB" -> new KeyParsed(1, true);
            case "MMB" -> new KeyParsed(2, true);
            case "MB3" -> new KeyParsed(3, true);
            case "MB4" -> new KeyParsed(4, true);
            case "SPACE" -> new KeyParsed(GLFW.GLFW_KEY_SPACE, false);
            case "ENTER" -> new KeyParsed(GLFW.GLFW_KEY_ENTER, false);
            case "ESCAPE" -> new KeyParsed(GLFW.GLFW_KEY_ESCAPE, false);
            case "TAB" -> new KeyParsed(GLFW.GLFW_KEY_TAB, false);
            case "BACKSPACE" -> new KeyParsed(GLFW.GLFW_KEY_BACKSPACE, false);
            case "LEFT_SHIFT" -> new KeyParsed(GLFW.GLFW_KEY_LEFT_SHIFT, false);
            case "RIGHT_SHIFT" -> new KeyParsed(GLFW.GLFW_KEY_RIGHT_SHIFT, false);
            case "LEFT_CONTROL" -> new KeyParsed(GLFW.GLFW_KEY_LEFT_CONTROL, false);
            case "RIGHT_CONTROL" -> new KeyParsed(GLFW.GLFW_KEY_RIGHT_CONTROL, false);
            case "LEFT_ALT" -> new KeyParsed(GLFW.GLFW_KEY_LEFT_ALT, false);
            case "RIGHT_ALT" -> new KeyParsed(GLFW.GLFW_KEY_RIGHT_ALT, false);
            case "UP" -> new KeyParsed(GLFW.GLFW_KEY_UP, false);
            case "DOWN" -> new KeyParsed(GLFW.GLFW_KEY_DOWN, false);
            case "LEFT" -> new KeyParsed(GLFW.GLFW_KEY_LEFT, false);
            case "RIGHT" -> new KeyParsed(GLFW.GLFW_KEY_RIGHT, false);
            case "INSERT" -> new KeyParsed(GLFW.GLFW_KEY_INSERT, false);
            case "DELETE" -> new KeyParsed(GLFW.GLFW_KEY_DELETE, false);
            case "HOME" -> new KeyParsed(GLFW.GLFW_KEY_HOME, false);
            case "END" -> new KeyParsed(GLFW.GLFW_KEY_END, false);
            case "PAGE_UP" -> new KeyParsed(GLFW.GLFW_KEY_PAGE_UP, false);
            case "PAGE_DOWN" -> new KeyParsed(GLFW.GLFW_KEY_PAGE_DOWN, false);
            default -> {
                if (part.startsWith("F") && part.length() <= 3) {
                    try {
                        int f = Integer.parseInt(part.substring(1));
                        if (f >= 1 && f <= 25) yield new KeyParsed(GLFW.GLFW_KEY_F1 + f - 1, false);
                    } catch (NumberFormatException ignored) {}
                }
                if (part.startsWith("MB")) {
                    try {
                        int b = Integer.parseInt(part.substring(2));
                        yield new KeyParsed(b, true);
                    } catch (NumberFormatException ignored) {}
                }
                if (part.startsWith("KEY_")) {
                    try {
                        yield new KeyParsed(Integer.parseInt(part.substring(4)), false);
                    } catch (NumberFormatException ignored) {}
                }
                if (part.length() == 1) {
                    int code = GLFW.glfwGetKeyScancode(part.toLowerCase().charAt(0));
                    if (code > 0) yield new KeyParsed(code, false);
                    int ch = part.charAt(0);
                    if (ch >= 'A' && ch <= 'Z') yield new KeyParsed(GLFW.GLFW_KEY_A + (ch - 'A'), false);
                    if (ch >= '0' && ch <= '9') yield new KeyParsed(GLFW.GLFW_KEY_0 + (ch - '0'), false);
                }
                yield new KeyParsed(GLFW.GLFW_KEY_UNKNOWN, false);
            }
        };
    }

    private record KeyParsed(int keyCode, boolean isMouse) {}
}
