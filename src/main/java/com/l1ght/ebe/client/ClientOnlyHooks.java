package com.l1ght.ebe.client;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.client.keybind.EBEKeyBindings;
import com.l1ght.ebe.client.ui.AdminScreen;
import com.l1ght.ebe.client.ui.AdminUI;
import com.l1ght.ebe.client.ui.EditorScreen;
import com.l1ght.ebe.client.ui.EditorUI;
import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.io.FileManager;
import com.l1ght.ebe.client.projection.ProjectionController;
import com.l1ght.ebe.client.projection.ProjectionManager;
import com.l1ght.ebe.client.projection.PrinterController;
import com.l1ght.ebe.network.WorkgroupPrintReservationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModContainer;

import java.util.List;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
public final class ClientOnlyHooks {
    private ClientOnlyHooks() {}
    private static String workgroupsJson = "[]";
    private static String adminJson = "{}";
    private static boolean workgroupPrintActive = false;
    private static String workgroupPrintJson = "{}";
    private static String workgroupPrintSessionId = "";
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\"sessionId\"\\s*:\\s*\"([^\"]+)\"");

    public static void registerClientConfig(ModContainer container) {
        EBEClientConfig.register(container);
    }

    public static void loadKeybindings() {
        String raw = EBEClientConfig.KEYBINDINGS.get();
        if (raw == null || raw.isEmpty()) return;
        var map = EBEClientConfig.deserializeKeybindings(raw);
        for (var binding : EBEKeyBindings.getAll()) {
            String serial = map.get(binding.getId());
            if (serial != null) {
                binding.deserialize(serial);
            }
        }
    }

    public static void saveAllKeybindings() {
        var map = new java.util.LinkedHashMap<String, String>();
        for (var binding : EBEKeyBindings.getAll()) {
            map.put(binding.getId(), binding.serialize());
        }
        EBEClientConfig.KEYBINDINGS.set(EBEClientConfig.serializeKeybindings(map));
        EBEClientConfig.SPEC.save();
    }

    public static void openEditorScreen() {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new EditorScreen()));
    }

    public static void openAdminScreen() {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new AdminScreen()));
    }

    public static void ensureSchematicDir() {
        try {
            var dir = EBEClientConfig.schematicDir.get();
            new FileManager(dir).ensureDir();
            EBEMod.LOGGER.info("EasyBlockEditor schematic directory ready: {}", dir);
        } catch (Exception e) {
            EBEMod.LOGGER.error("Failed to create schematic directory", e);
        }
    }

    public static void toggleProjectionRemoteMode() {
        ProjectionController.toggleControlMode();
    }

    public static void appendRemoteTooltip(ItemStack stack, TooltipFlag flag, List<Component> tooltip) {
        String fwd = EBEKeyBindings.REMOTE_FORWARD.getDisplayName();
        String back = EBEKeyBindings.REMOTE_BACK.getDisplayName();
        String left = EBEKeyBindings.REMOTE_LEFT.getDisplayName();
        String right = EBEKeyBindings.REMOTE_RIGHT.getDisplayName();
        String ccw = EBEKeyBindings.REMOTE_ROTATE_CCW.getDisplayName();
        String cw = EBEKeyBindings.REMOTE_ROTATE_CW.getDisplayName();

        tooltip.add(Component.translatable("ebe.item.remote.desc.toggle").withStyle(s -> s.withColor(0xAAAAAA)));
        tooltip.add(Component.translatable("ebe.item.remote.desc.move", fwd, back, left, right).withStyle(s -> s.withColor(0xAAAAAA)));
        tooltip.add(Component.translatable("ebe.item.remote.desc.vertical").withStyle(s -> s.withColor(0xAAAAAA)));
        tooltip.add(Component.translatable("ebe.item.remote.desc.rotate", ccw, cw).withStyle(s -> s.withColor(0xAAAAAA)));
        tooltip.add(Component.translatable("ebe.item.remote.desc.fast").withStyle(s -> s.withColor(0x888888)));
    }

    public static void setProjectionProgress(int placed, int total) {
        ProjectionManager.setProgress(placed, total);
    }

    public static void updateWorkgroups(String json) {
        workgroupsJson = json == null ? "[]" : json;
        workgroupPrintActive = workgroupsJson.contains("\"printSession\":{");
        if (workgroupPrintActive) {
            workgroupPrintSessionId = extractSessionId(workgroupsJson);
        } else {
            workgroupPrintSessionId = "";
        }
        EditorUI.refreshWorkgroupPanel();
    }

    public static String getWorkgroupsJson() {
        return workgroupsJson;
    }

    public static boolean isInWorkgroup() {
        return workgroupsJson.contains("\"group\":{");
    }

    public static boolean hasWorkgroupPrintSession() {
        return workgroupPrintActive;
    }

    public static String getWorkgroupPrintSessionId() {
        return workgroupPrintSessionId;
    }

    public static void acceptWorkgroupPrintReservations(List<WorkgroupPrintReservationPayload.Entry> reservations) {
        PrinterController.acceptWorkgroupReservations(reservations);
    }

    public static void updateWorkgroupPrintState(boolean active, int placed, int total, String snapshotJson) {
        boolean wasActive = workgroupPrintActive;
        workgroupPrintActive = active;
        workgroupPrintJson = snapshotJson == null ? "{}" : snapshotJson;
        workgroupPrintSessionId = active ? extractSessionId(workgroupPrintJson) : "";
        if (active) {
            ProjectionManager.setProgress(placed, total);
        } else if (wasActive) {
            PrinterController.handleWorkgroupPrintSessionEnded();
        }
        EditorUI.refreshWorkgroupPanel();
    }

    public static String getWorkgroupPrintJson() {
        return workgroupPrintJson;
    }

    private static String extractSessionId(String json) {
        if (json == null || json.isEmpty()) return "";
        var matcher = SESSION_ID_PATTERN.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static void updateAdminData(String json) {
        adminJson = json == null ? "{}" : json;
        AdminUI.updateSnapshot(adminJson);
    }

    public static String getAdminJson() {
        return adminJson;
    }
}
