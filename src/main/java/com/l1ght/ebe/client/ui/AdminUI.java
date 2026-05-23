package com.l1ght.ebe.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.l1ght.ebe.network.AdminActionPayload;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class AdminUI {
    private static UIElement root;
    private static UIElement content;
    private static String snapshot = "{}";
    private static int activeTab = 0;
    private static final String[] FEATURES = {"projection", "printer", "place_all", "collaborate", "file_library", "import", "export"};
    private static final String[] SETTINGS = {"projection_timeout_seconds", "place_chunks_per_tick", "printer_blocks_per_tick", "max_edit_size"};

    public static ModularUI createModularUI() {
        root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN));
        root.style(s -> s.background(Sprites.BORDER));

        root.addChild(buildHeader());
        var body = new UIElement();
        body.layout(l -> l.widthPercent(100).flex(1).flexDirection(FlexDirection.ROW).paddingAll(6).gapAll(6));
        body.addChild(buildSidebar());
        content = new UIElement();
        content.layout(l -> l.flex(1).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));
        body.addChild(content);
        root.addChild(body);

        requestSync();
        renderContent();
        var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.GDP);
        return ModularUI.of(UI.of(root, List.of(stylesheet), screenSize -> screenSize));
    }

    public static void updateSnapshot(String json) {
        snapshot = json == null ? "{}" : json;
        renderContent();
    }

    private static UIElement buildHeader() {
        var header = new UIElement();
        header.layout(l -> l.widthPercent(100).height(30).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).paddingHorizontal(8).gapAll(6));
        header.style(s -> s.background(Sprites.RECT_DARK));

        var title = new Label();
        title.setText(Component.literal("EasyBlockEditor Admin"));
        title.layout(l -> l.flex(1));
        title.textStyle(ts -> ts.textColor(0xFFFFFFFF).fontSize(13).textShadow(false));
        header.addChild(title);

        header.addChild(iconButton(EditorIcons.REFRESH, "Refresh server data", AdminUI::requestSync));
        header.addChild(iconButton(EditorIcons.CLOSE, "Close", () -> Minecraft.getInstance().setScreen(null)));
        return header;
    }

    private static UIElement buildSidebar() {
        var side = new UIElement();
        side.layout(l -> l.width(132).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));
        side.style(s -> s.background(Sprites.RECT_RD_DARK));
        side.addChild(tabButton("Permissions", 0));
        side.addChild(tabButton("Server Settings", 1));
        side.addChild(tabButton("Workgroups", 2));
        var hint = new Label();
        hint.setText(Component.literal("All changes are saved on the server immediately."));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(8).textShadow(false)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        side.addChild(hint);
        return side;
    }

    private static Button tabButton(String label, int tab) {
        var btn = new Button();
        btn.setText(Component.literal(label));
        btn.layout(l -> l.widthPercent(100).height(22));
        btn.style(s -> s.background(activeTab == tab ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
        btn.setOnClick(e -> {
            activeTab = tab;
            if (root != null) {
                root.clearAllChildren();
                root.addChild(buildHeader());
                var body = new UIElement();
                body.layout(l -> l.widthPercent(100).flex(1).flexDirection(FlexDirection.ROW).paddingAll(6).gapAll(6));
                body.addChild(buildSidebar());
                content = new UIElement();
                content.layout(l -> l.flex(1).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));
                body.addChild(content);
                root.addChild(body);
                renderContent();
            }
        });
        return btn;
    }

    private static void renderContent() {
        if (content == null) return;
        content.clearAllChildren();
        switch (activeTab) {
            case 0 -> renderPermissions();
            case 1 -> renderSettings();
            case 2 -> renderWorkgroups();
            default -> renderPermissions();
        }
    }

    private static void renderPermissions() {
        content.addChild(sectionTitle("Global Permissions"));
        JsonObject permissions = obj(rootJson().get("permissions"));
        for (String feature : FEATURES) {
            String value = string(permissions.get(feature), "default");
            content.addChild(decisionRow(feature, value, decision -> send("permission_global", feature, decision, "")));
        }

        content.addChild(sectionTitle("Player Override"));
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        var player = new TextField();
        player.layout(l -> l.width(140).height(18));
        player.textFieldStyle(s -> s.placeholder(Component.literal("Player name")));
        row.addChild(player);
        row.addChild(new Label().setText(Component.literal("Select a feature below, then set Allow / Default / Deny.")));
        content.addChild(row);

        for (String feature : FEATURES) {
            content.addChild(decisionRow(feature, "default", decision -> {
                String playerName = player.getText().trim();
                if (!playerName.isEmpty()) send("permission_player", playerName, feature, decision);
            }));
        }

        JsonObject overrides = obj(rootJson().get("playerOverrides"));
        if (!overrides.entrySet().isEmpty()) {
            content.addChild(sectionTitle("Saved Player Overrides"));
            for (var entry : overrides.entrySet()) {
                var label = new Label();
                label.setText(Component.literal(entry.getKey() + " : " + entry.getValue()));
                label.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(8).textShadow(false));
                content.addChild(label);
            }
        }
    }

    private static void renderSettings() {
        content.addChild(sectionTitle("Server Global Settings"));
        JsonObject settings = obj(rootJson().get("settings"));
        for (String key : SETTINGS) {
            int value = integer(settings.get(key), defaultSetting(key));
            content.addChild(settingRow(key, value));
        }
    }

    private static void renderWorkgroups() {
        content.addChild(sectionTitle("Server Workgroups"));
        JsonArray groups = arr(rootJson().get("workgroups"));
        if (groups.isEmpty()) {
            var empty = new Label();
            empty.setText(Component.literal("No active workgroups."));
            empty.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9).textShadow(false));
            content.addChild(empty);
            return;
        }
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        var list = new UIElement();
        list.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));
        for (JsonElement elem : groups) {
            list.addChild(adminGroupCard(elem.getAsJsonObject()));
        }
        scroller.addScrollViewChild(list);
        content.addChild(scroller);
    }

    private static UIElement adminGroupCard(JsonObject group) {
        String name = string(group.get("name"), "");
        var card = new UIElement();
        card.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3).paddingAll(5));
        card.style(s -> s.background(Sprites.RECT_RD));

        var top = new UIElement();
        top.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        var title = new Label();
        title.setText(Component.literal(name + "  Leader: " + string(group.get("leader"), "-")));
        title.layout(l -> l.flex(1));
        title.textStyle(ts -> ts.textColor(0xFFFFFFFF).fontSize(10).textShadow(false));
        top.addChild(title);
        var disband = new Button();
        disband.setText(Component.literal("Disband"));
        disband.layout(l -> l.width(70).height(18));
        disband.setOnClick(e -> send("group_disband", name, "", ""));
        top.addChild(disband);
        card.addChild(top);

        JsonArray members = arr(group.get("members"));
        for (JsonElement memberElem : members) {
            JsonObject member = memberElem.getAsJsonObject();
            var row = new UIElement();
            row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
            var label = new Label();
            label.setText(Component.literal(string(member.get("name"), "-") + "  " + string(member.get("role"), "member")));
            label.layout(l -> l.flex(1));
            label.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(8).textShadow(false));
            row.addChild(label);
            if (!"leader".equals(string(member.get("role"), ""))) {
                var kick = new Button();
                kick.setText(Component.literal("Kick"));
                kick.layout(l -> l.width(44).height(16));
                kick.setOnClick(e -> send("group_kick", name, string(member.get("name"), ""), ""));
                row.addChild(kick);
            }
            card.addChild(row);
        }
        return card;
    }

    private static UIElement settingRow(String key, int value) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        row.style(s -> s.background(Sprites.RECT_RD));
        var label = new Label();
        label.setText(Component.literal(pretty(key) + " = " + value));
        label.layout(l -> l.flex(1));
        label.textStyle(ts -> ts.textColor(0xFFFFFFFF).fontSize(9).textShadow(false));
        row.addChild(label);
        int step = settingStep(key);
        row.addChild(smallButton("-", () -> send("setting", key, Integer.toString(value - step), "")));
        row.addChild(smallButton("+", () -> send("setting", key, Integer.toString(value + step), "")));
        return row;
    }

    private static UIElement decisionRow(String feature, String value, DecisionAction action) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(3));
        row.style(s -> s.background(Sprites.RECT_RD));
        var label = new Label();
        label.setText(Component.literal(pretty(feature) + "  [" + value + "]"));
        label.layout(l -> l.flex(1));
        label.textStyle(ts -> ts.textColor(0xFFFFFFFF).fontSize(9).textShadow(false));
        row.addChild(label);
        row.addChild(decisionButton("Allow", "allow".equals(value), () -> action.run("allow")));
        row.addChild(decisionButton("Default", "default".equals(value), () -> action.run("default")));
        row.addChild(decisionButton("Deny", "deny".equals(value), () -> action.run("deny")));
        return row;
    }

    private static Button decisionButton(String label, boolean active, Runnable action) {
        var btn = new Button();
        btn.setText(Component.literal(label));
        btn.layout(l -> l.width(58).height(18));
        btn.style(s -> s.background(active ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
        btn.setOnClick(e -> action.run());
        return btn;
    }

    private static Button smallButton(String label, Runnable action) {
        var btn = new Button();
        btn.setText(Component.literal(label));
        btn.layout(l -> l.width(26).height(18));
        btn.setOnClick(e -> action.run());
        return btn;
    }

    private static Label sectionTitle(String text) {
        var label = new Label();
        label.setText(Component.literal(text));
        label.textStyle(ts -> ts.textColor(0xFFFFD166).fontSize(11).textShadow(false));
        return label;
    }

    private static UIElement iconButton(SpriteTexture icon, String tooltip, Runnable action) {
        var btn = new UIElement();
        btn.layout(l -> l.width(24).height(24).alignItems(AlignItems.CENTER).justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER));
        var img = new UIElement();
        img.layout(l -> l.width(18).height(18));
        img.style(s -> s.backgroundTexture(icon));
        btn.addChild(img);
        btn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) action.run();
        });
        btn.addEventListener(UIEvents.MOUSE_ENTER, e -> btn.style(s -> s.tooltips(Component.literal(tooltip))));
        return btn;
    }

    private static void requestSync() {
        send("sync", "", "", "");
    }

    private static void send(String action, String a, String b, String c) {
        PacketDistributor.sendToServer(new AdminActionPayload(action, a, b, c));
    }

    private static JsonObject rootJson() {
        try {
            return JsonParser.parseString(snapshot).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static JsonObject obj(JsonElement elem) {
        return elem != null && elem.isJsonObject() ? elem.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray arr(JsonElement elem) {
        return elem != null && elem.isJsonArray() ? elem.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonElement elem, String fallback) {
        return elem == null || elem.isJsonNull() ? fallback : elem.getAsString();
    }

    private static int integer(JsonElement elem, int fallback) {
        try {
            return elem == null || elem.isJsonNull() ? fallback : elem.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int defaultSetting(String key) {
        return switch (key) {
            case "place_chunks_per_tick" -> 4;
            case "printer_blocks_per_tick" -> 1;
            case "max_edit_size" -> 256;
            default -> 0;
        };
    }

    private static int settingStep(String key) {
        return switch (key) {
            case "projection_timeout_seconds" -> 60;
            case "max_edit_size" -> 16;
            default -> 1;
        };
    }

    private static String pretty(String key) {
        String[] parts = key.split("_");
        var out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    @FunctionalInterface
    private interface DecisionAction {
        void run(String decision);
    }
}
