package com.l1ght.ebe.client.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class EBEKeyMappings {

    private static final String CATEGORY = "ebe.key.category";

    public static final KeyMapping TOOL_SELECT = new KeyMapping(
            "ebe.key.tool_select", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping TOOL_PLACE = new KeyMapping(
            "ebe.key.tool_place", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, CATEGORY);
    public static final KeyMapping TOOL_DELETE = new KeyMapping(
            "ebe.key.tool_delete", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, CATEGORY);
    public static final KeyMapping TOOL_REPLACE = new KeyMapping(
            "ebe.key.tool_replace", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping TOOL_GRAB = new KeyMapping(
            "ebe.key.tool_grab", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, CATEGORY);
    public static final KeyMapping TOOL_MEASURE = new KeyMapping(
            "ebe.key.tool_measure", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping TOOL_FILL = new KeyMapping(
            "ebe.key.tool_fill", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);

    public static final KeyMapping FREE_FLIGHT_FORWARD = new KeyMapping(
            "ebe.key.fly_forward", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_W, CATEGORY);
    public static final KeyMapping FREE_FLIGHT_BACK = new KeyMapping(
            "ebe.key.fly_back", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_S, CATEGORY);
    public static final KeyMapping FREE_FLIGHT_LEFT = new KeyMapping(
            "ebe.key.fly_left", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_A, CATEGORY);
    public static final KeyMapping FREE_FLIGHT_RIGHT = new KeyMapping(
            "ebe.key.fly_right", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_D, CATEGORY);
    public static final KeyMapping FREE_FLIGHT_UP = new KeyMapping(
            "ebe.key.fly_up", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
    public static final KeyMapping FREE_FLIGHT_DOWN = new KeyMapping(
            "ebe.key.fly_down", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, CATEGORY);
}
