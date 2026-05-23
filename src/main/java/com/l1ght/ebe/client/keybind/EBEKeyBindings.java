package com.l1ght.ebe.client.keybind;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class EBEKeyBindings {

    private static final List<EBEKeyBinding> ALL = new ArrayList<>();
    private static final Map<String, List<EBEKeyBinding>> BY_CATEGORY = new LinkedHashMap<>();

    public static final String CAT_EDIT = "ebe.key.category.edit";
    public static final String CAT_TOOLS = "ebe.key.category.tools";
    public static final String CAT_FLIGHT = "ebe.key.category.flight";
    public static final String CAT_MOUSE = "ebe.key.category.mouse";
    public static final String CAT_REMOTE = "ebe.key.category.remote";

    public static final EBEKeyBinding UNDO = register("ebe.key.undo", CAT_EDIT, GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL, false);
    public static final EBEKeyBinding REDO = register("ebe.key.redo", CAT_EDIT, GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL, false);
    public static final EBEKeyBinding COPY = register("ebe.key.copy", CAT_EDIT, GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL, false);
    public static final EBEKeyBinding PASTE = register("ebe.key.paste", CAT_EDIT, GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL, false);
    public static final EBEKeyBinding CUT = register("ebe.key.cut", CAT_EDIT, GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL, false);
    public static final EBEKeyBinding SELECT_ALL = register("ebe.key.select_all", CAT_EDIT, GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL, false);
    public static final EBEKeyBinding DESELECT = register("ebe.key.deselect", CAT_EDIT, GLFW.GLFW_KEY_D, GLFW.GLFW_MOD_CONTROL, false);

    public static final EBEKeyBinding TOOL_SELECT = register("ebe.key.tool_select", CAT_TOOLS, GLFW.GLFW_KEY_Z, 0, false);
    public static final EBEKeyBinding TOOL_PLACE = register("ebe.key.tool_place", CAT_TOOLS, GLFW.GLFW_KEY_Q, 0, false);
    public static final EBEKeyBinding TOOL_DELETE = register("ebe.key.tool_delete", CAT_TOOLS, GLFW.GLFW_KEY_E, 0, false);
    public static final EBEKeyBinding TOOL_REPLACE = register("ebe.key.tool_replace", CAT_TOOLS, GLFW.GLFW_KEY_R, 0, false);
    public static final EBEKeyBinding TOOL_GRAB = register("ebe.key.tool_grab", CAT_TOOLS, GLFW.GLFW_KEY_T, 0, false);
    public static final EBEKeyBinding TOOL_MEASURE = register("ebe.key.tool_measure", CAT_TOOLS, GLFW.GLFW_KEY_H, 0, false);
    public static final EBEKeyBinding TOOL_FILL = register("ebe.key.tool_fill", CAT_TOOLS, GLFW.GLFW_KEY_F, 0, false);

    public static final EBEKeyBinding FLY_FORWARD = register("ebe.key.fly_forward", CAT_FLIGHT, GLFW.GLFW_KEY_W, 0, false);
    public static final EBEKeyBinding FLY_BACK = register("ebe.key.fly_back", CAT_FLIGHT, GLFW.GLFW_KEY_S, 0, false);
    public static final EBEKeyBinding FLY_LEFT = register("ebe.key.fly_left", CAT_FLIGHT, GLFW.GLFW_KEY_A, 0, false);
    public static final EBEKeyBinding FLY_RIGHT = register("ebe.key.fly_right", CAT_FLIGHT, GLFW.GLFW_KEY_D, 0, false);
    public static final EBEKeyBinding FLY_UP = register("ebe.key.fly_up", CAT_FLIGHT, GLFW.GLFW_KEY_SPACE, 0, false);
    public static final EBEKeyBinding FLY_DOWN = register("ebe.key.fly_down", CAT_FLIGHT, GLFW.GLFW_KEY_LEFT_ALT, 0, false);

    public static final EBEKeyBinding SELECT_MULTI = register("ebe.key.select_multi", CAT_MOUSE, 0, GLFW.GLFW_MOD_CONTROL, true);
    public static final EBEKeyBinding BOX_SELECT_SURFACE = register("ebe.key.box_surface", CAT_MOUSE, 0, GLFW.GLFW_MOD_SHIFT, true);
    public static final EBEKeyBinding BOX_SELECT_PENETRATE = register("ebe.key.box_penetrate", CAT_MOUSE, 1, GLFW.GLFW_MOD_SHIFT, true);
    public static final EBEKeyBinding SELECT_SAME_TYPE = register("ebe.key.same_type", CAT_MOUSE, 0, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT, true);
    public static final EBEKeyBinding DESELECT_BLOCK = register("ebe.key.deselect_block", CAT_MOUSE, 1, 0, true);
    public static final EBEKeyBinding GRAB_VIEWPORT = register("ebe.key.grab_viewport", CAT_MOUSE, 2, 0, true);
    public static final EBEKeyBinding FILL_EXECUTE = register("ebe.key.fill_execute", CAT_MOUSE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_CONTROL, false);

    public static final EBEKeyBinding REMOTE_FORWARD = register("ebe.key.remote_forward", CAT_REMOTE, GLFW.GLFW_KEY_UP, 0, false);
    public static final EBEKeyBinding REMOTE_BACK = register("ebe.key.remote_back", CAT_REMOTE, GLFW.GLFW_KEY_DOWN, 0, false);
    public static final EBEKeyBinding REMOTE_LEFT = register("ebe.key.remote_left", CAT_REMOTE, GLFW.GLFW_KEY_LEFT, 0, false);
    public static final EBEKeyBinding REMOTE_RIGHT = register("ebe.key.remote_right", CAT_REMOTE, GLFW.GLFW_KEY_RIGHT, 0, false);
    public static final EBEKeyBinding REMOTE_ROTATE_CCW = register("ebe.key.remote_rotate_ccw", CAT_REMOTE, 0, 0, true);
    public static final EBEKeyBinding REMOTE_ROTATE_CW = register("ebe.key.remote_rotate_cw", CAT_REMOTE, 1, 0, true);

    private static EBEKeyBinding register(String id, String category, int keyCode, int modifiers, boolean isMouseButton) {
        var binding = new EBEKeyBinding(id, category, keyCode, modifiers, isMouseButton);
        ALL.add(binding);
        BY_CATEGORY.computeIfAbsent(category, k -> new ArrayList<>()).add(binding);
        return binding;
    }

    public static List<EBEKeyBinding> getAll() { return ALL; }
    public static Map<String, List<EBEKeyBinding>> getByCategory() { return BY_CATEGORY; }

    public static EBEKeyBinding getById(String id) {
        for (var b : ALL) {
            if (b.getId().equals(id)) return b;
        }
        return null;
    }
}
