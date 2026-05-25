package com.l1ght.ebe.clientbridge;

import com.l1ght.ebe.EBEMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;

import java.lang.reflect.Method;
import java.util.List;

public final class EBEClientBridge {
    private static final String CLIENT_HOOKS = "com.l1ght.ebe.client.ClientOnlyHooks";

    private EBEClientBridge() {
    }

    public static void registerClientConfig(ModContainer container) {
        invoke("registerClientConfig", new Class<?>[]{ModContainer.class}, container);
    }

    public static void ensureSchematicDir() {
        invoke("ensureSchematicDir", new Class<?>[0]);
    }

    public static void openEditorScreen() {
        invoke("openEditorScreen", new Class<?>[0]);
    }

    public static void toggleProjectionRemoteMode() {
        invoke("toggleProjectionRemoteMode", new Class<?>[0]);
    }

    public static void appendRemoteTooltip(ItemStack stack, TooltipFlag flag, List<Component> tooltip) {
        invoke("appendRemoteTooltip", new Class<?>[]{ItemStack.class, TooltipFlag.class, List.class}, stack, flag, tooltip);
    }

    private static void invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> hooks = Class.forName(CLIENT_HOOKS);
            Method method = hooks.getMethod(methodName, parameterTypes);
            method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            EBEMod.LOGGER.error("Failed to invoke EBE client hook '{}'", methodName, e);
        }
    }
}
