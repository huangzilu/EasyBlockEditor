package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.EBEKeyMappings;
import com.l1ght.ebe.config.EBEClientConfig;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Property;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EditorUI {

    private static final Map<String, String> EDIT_SHORTCUTS = Map.of(
            "ebe.editor.undo", "Ctrl+Z",
            "ebe.editor.redo", "Ctrl+Y",
            "ebe.editor.copy", "Ctrl+C",
            "ebe.editor.paste", "Ctrl+V",
            "ebe.editor.cut", "Ctrl+X",
            "ebe.editor.select_all", "Ctrl+A",
            "ebe.editor.deselect", "Ctrl+D"
    );

    private static final EditorState state = new EditorState();
    private static final EditorSession session = new EditorSession();
    private static final com.l1ght.ebe.editor.selection.SelectionManager selection = new com.l1ght.ebe.editor.selection.SelectionManager();
    private static final com.l1ght.ebe.editor.ClipboardManager clipboard = new com.l1ght.ebe.editor.ClipboardManager();
    private static final com.l1ght.ebe.editor.history.HistoryManager history =
            new com.l1ght.ebe.editor.history.HistoryManager(
                    EBEClientConfig.historyMaxEntries.get() == 0
                            ? Integer.MAX_VALUE
                            : EBEClientConfig.historyMaxEntries.get());
    private static UIElement historyListContainer;
    private static final Map<EditorTool, UIElement> toolbarButtons = new EnumMap<>(EditorTool.class);
    private static UIElement rootElement;
    private static UIElement contentArea;

    private static UIElement leftPanel;
    private static UIElement rightPanel;
    private static Button leftCollapseBtn;
    private static Button rightCollapseBtn;
    private static UIElement blockIndicatorPanel;
    private static boolean leftPanelVisible = true;
    private static boolean rightPanelVisible = true;
    private static boolean blockIndicatorVisible = true;

    private static UIElement keybindHintsPanel;
    private static boolean keybindHintsVisible = true;

    private static UIElement toolbarPanel;
    private static boolean toolbarExpanded = true;
    private static boolean toolbarVisible = true;

    private static UIElement materialListContainer;
    private static UIElement propertiesContainer;

    private static UIElement openDropdown;
    private static UIElement openMenuAnchor;

    public static ModularUI createModularUI() {
        rootElement = new UIElement();
        rootElement.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN));
        rootElement.setId("root");

        rootElement.addChild(buildMenuBar());
        contentArea = buildContentArea();
        rootElement.addChild(contentArea);
        rootElement.addChild(buildBottomBar());

        BlockPaletteUI.restorePalette(rootElement);

        var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(getThemeStylesheet());
        var ui = UI.of(rootElement, List.of(stylesheet), screenSize -> screenSize);
        return ModularUI.of(ui);
    }

    private static net.minecraft.resources.ResourceLocation getThemeStylesheet() {
        var theme = EBEClientConfig.theme.get();
        return switch (theme) {
            case "mc" -> StylesheetManager.MC;
            case "modern" -> StylesheetManager.MODERN;
            default -> StylesheetManager.GDP;
        };
    }

    public static EditorState getState() { return state; }
    public static EditorSession getSession() { return session; }
    public static com.l1ght.ebe.editor.selection.SelectionManager getSelection() { return selection; }
    public static com.l1ght.ebe.editor.ClipboardManager getClipboard() { return clipboard; }
    public static com.l1ght.ebe.editor.history.HistoryManager getHistory() { return history; }

    // ========== Menu Bar ==========

    private static UIElement buildMenuBar() {
        var bar = new UIElement();
        bar.layout(l -> l.widthPercent(100).height(22).flexDirection(FlexDirection.ROW)
                .paddingHorizontal(4).alignItems(AlignItems.CENTER).gapAll(2));
        bar.style(s -> s.background(Sprites.BORDER));
        bar.setId("menuBar");

        bar.addChildren(
                menuButton("ebe.editor.menu.file", buildFileMenu()),
                menuButton("ebe.editor.menu.edit", buildEditMenu()),
                menuButton("ebe.editor.menu.view", buildViewMenu()),
                menuButton("ebe.editor.menu.tool", buildToolMenu()),
                menuButton("ebe.editor.menu.help", buildHelpMenu())
        );
        return bar;
    }

    private static Button menuButton(String translationKey, MenuTreeNode menuTree) {
        var btn = new Button();
        btn.setText(Component.translatable(translationKey));
        btn.layout(l -> l.height(18).paddingHorizontal(8));
        btn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) toggleMenu(btn, menuTree);
        });
        return btn;
    }

    private static void closeMenu() {
        if (openDropdown != null) { openDropdown.removeSelf(); openDropdown = null; }
        if (openMenuAnchor != null) { openMenuAnchor.style(s -> s.background(Sprites.RECT_RD)); openMenuAnchor = null; }
    }

    private static void toggleMenu(UIElement anchor, MenuTreeNode tree) {
        if (openMenuAnchor == anchor) { closeMenu(); return; }
        closeMenu();

        var dropdown = new UIElement();
        dropdown.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(anchor.getPositionX())
                .top(anchor.getPositionY() + anchor.getSizeHeight())
                .flexDirection(FlexDirection.COLUMN).paddingVertical(2).gapAll(1));
        dropdown.style(s -> s.background(Sprites.BORDER).zIndex(1000));
        dropdown.setId("__dropdown_menu__");

        for (var child : tree.getChildren()) {
            var item = new Button();
            var key = child.getKey();
            var text = Component.translatable(key);
            boolean isChecked = false;

            var shortcut = EDIT_SHORTCUTS.get(key);
            if (shortcut != null) {
                text = text.copy().append(Component.literal("  " + shortcut).withStyle(ChatFormatting.DARK_GRAY));
            }

            if (key.equals("ebe.editor.panel.files") || key.equals("ebe.editor.panel.layers")) {
                isChecked = leftPanelVisible;
            } else if (key.equals("ebe.editor.panel.properties") || key.equals("ebe.editor.panel.materials") || key.equals("ebe.editor.panel.history")) {
                isChecked = rightPanelVisible;
            } else if (key.equals("ebe.editor.block_indicator")) {
                isChecked = blockIndicatorVisible;
            } else if (key.equals("ebe.editor.panel.tools")) {
                isChecked = toolbarVisible;
            } else if (key.equals("ebe.editor.keybind_hints")) {
                isChecked = keybindHintsVisible;
            }

            if (isChecked) {
                var check = Component.literal("✓ ").withStyle(ChatFormatting.GREEN);
                text = check.append(text);
                item.style(s -> s.background(Sprites.RECT_RD_DARK));
            } else {
                text = Component.literal("  ").append(text);
            }

            item.setText(text);
            item.layout(l -> l.widthPercent(100).height(18).paddingHorizontal(8));
            var action = child.getContent();
            if (action != null) {
                item.setOnClick(e -> { action.run(); closeMenu(); });
            }
            dropdown.addChild(item);
        }

        rootElement.addChild(dropdown);
        openMenuAnchor = anchor;
        anchor.style(s -> s.background(Sprites.RECT_RD_DARK));
        openDropdown = dropdown;
    }

    // ========== Menu Trees ==========

    private static MenuTreeNode buildFileMenu() {
        var root = new MenuTreeNode("file");
        root.child("ebe.editor.new_project", () -> EditorDialogs.newProjectDialog(rootElement, name -> {
            session.newProject(name);
            ViewportFactory.clearModel();
        }));
        root.child("ebe.editor.open", () -> ImportDialog.showOpen(rootElement, file -> {
            try { session.load(file); ViewportFactory.loadFromModel(session.getModel()); refreshMaterialList(); updateStatusBar(); } catch (Exception e) { e.printStackTrace(); }
        }));
        root.child("ebe.editor.save", () -> { try { session.save(); } catch (Exception e) { e.printStackTrace(); } });
        root.child("ebe.editor.save_as", () -> EditorDialogs.saveAsDialog(rootElement, session.getCurrentName(), name -> {
            try { session.saveAs(name); } catch (Exception e) { e.printStackTrace(); }
        }));
        root.child("ebe.editor.import", () -> ImportDialog.showImport(rootElement, file -> {
            try { session.load(file); ViewportFactory.loadFromModel(session.getModel()); refreshMaterialList(); updateStatusBar(); } catch (Exception e) { e.printStackTrace(); }
        }));
        root.child("ebe.editor.export", () -> EditorDialogs.saveAsDialog(rootElement, session.getCurrentName(), name -> {
            try { session.saveAs(name); } catch (Exception e) { e.printStackTrace(); }
        }));
        return root;
    }

    private static MenuTreeNode buildEditMenu() {
        var root = new MenuTreeNode("edit");
        root.child("ebe.editor.undo", () -> undo());
        root.child("ebe.editor.redo", () -> redo());
        root.child("ebe.editor.copy", () -> clipboard.copy(session.getModel(), selection));
        root.child("ebe.editor.paste", () -> {
            clipboard.paste(session.getModel(), new net.minecraft.core.BlockPos(
                    state.getCursorX(), state.getCursorY(), state.getCursorZ()), history);
            ViewportFactory.refreshFromModel(session.getModel());
            refreshMaterialList();
            refreshHistoryList();
        });
        root.child("ebe.editor.cut", () -> {
            clipboard.cut(session.getModel(), selection, history);
            ViewportFactory.refreshFromModel(session.getModel());
            refreshMaterialList();
            refreshHistoryList();
            updateSelectionCount();
        });
        root.child("ebe.editor.select_all", () -> selectAll());
        root.child("ebe.editor.deselect", () -> { selection.clear(); updateSelectionCount(); });
        return root;
    }

    private static MenuTreeNode buildViewMenu() {
        var root = new MenuTreeNode("view");
        root.child("ebe.editor.panel.files", () -> toggleLeftPanel());
        root.child("ebe.editor.panel.layers", () -> toggleLeftPanel());
        root.child("ebe.editor.panel.properties", () -> toggleRightPanel());
        root.child("ebe.editor.panel.materials", () -> toggleRightPanel());
        root.child("ebe.editor.panel.history", () -> toggleRightPanel());
        root.child("ebe.editor.panel.tools", () -> toggleToolbarPanel());
        root.child("ebe.editor.block_palette", () -> BlockPaletteUI.togglePalette(rootElement));
        root.child("ebe.editor.block_indicator", () -> toggleBlockIndicator());
        root.child("ebe.editor.keybind_hints", () -> toggleKeybindHints());
        root.child("ebe.editor.settings", () -> SettingsUI.showSettings(rootElement));
        return root;
    }

    private static MenuTreeNode buildToolMenu() {
        var root = new MenuTreeNode("tool");
        for (var t : EditorTool.values()) root.child(t.getTranslationKey(), () -> selectTool(t));
        return root;
    }

    private static MenuTreeNode buildHelpMenu() {
        var root = new MenuTreeNode("help");
        root.child("ebe.name");
        return root;
    }

    // ========== Content Area ==========

    private static UIElement buildContentArea() {
        var content = new UIElement();
        content.layout(l -> l.flexDirection(FlexDirection.ROW).flex(1).widthPercent(100)
                .alignItems(AlignItems.STRETCH));
        content.setId("contentArea");

        leftCollapseBtn = buildCollapseButton(true);
        leftPanel = buildLeftPanel();
        var viewport = ViewportFactory.create3DViewport();
        rightPanel = buildRightPanel();
        rightCollapseBtn = buildCollapseButton(false);

        content.addChild(leftCollapseBtn);
        content.addChild(leftPanel);
        content.addChild(viewport);
        content.addChild(rightPanel);
        content.addChild(rightCollapseBtn);

        toolbarPanel = buildToolbar();
        viewport.addChild(toolbarPanel);

        var blockIndicator = buildBlockIndicator();
        viewport.addChild(blockIndicator);
        blockIndicatorPanel = blockIndicator;

        keybindHintsPanel = buildKeybindHintsPanel();
        viewport.addChild(keybindHintsPanel);

        if (!leftPanelVisible) {
            leftPanel.setDisplay(false);
            leftCollapseBtn.setText(Component.literal("▶"));
        }
        if (!rightPanelVisible) {
            rightPanel.setDisplay(false);
            rightCollapseBtn.setText(Component.literal("◀"));
        }
        if (!blockIndicatorVisible) {
            blockIndicatorPanel.setDisplay(false);
        }

        return content;
    }

    // ========== Toolbar ==========

    private static UIElement buildToolbar() {
        var panel = new UIElement();
        panel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(4).topPercent(50)
                .flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(2)
                .width(30));
        panel.style(s -> s.background(Sprites.BORDER).zIndex(90));
        panel.setId("toolbarPanel");

        var collapseBtn = buildToolbarCollapseBtn();
        panel.addChild(collapseBtn);

        for (var tool : EditorTool.values()) {
            var btn = buildToolbarButton(tool);
            toolbarButtons.put(tool, btn);
            panel.addChild(btn);
        }

        var modeSep = new UIElement();
        modeSep.layout(l -> l.widthPercent(100).height(1));
        modeSep.style(s -> s.background(Sprites.RECT_DARK));
        panel.addChild(modeSep);

        var modeBtn = buildModeToggleButton();
        panel.addChild(modeBtn);

        highlightCurrentTool();
        return panel;
    }

    private static UIElement buildToolbarCollapseBtn() {
        var btn = new UIElement();
        btn.layout(l -> l.width(26).height(10));
        btn.style(s -> s.background(Sprites.RECT_DARK));
        btn.setId("toolbarCollapseBtn");

        var inner = new Label();
        inner.setText(Component.literal(toolbarExpanded ? "◀" : "▶"));
        inner.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(8));
        btn.addChild(inner);

        btn.addEventListener(UIEvents.MOUSE_DOWN, e -> toggleToolbar());
        return btn;
    }

    private static void toggleToolbar() {
        toolbarExpanded = !toolbarExpanded;
        toolbarButtons.values().forEach(b -> b.setDisplay(toolbarExpanded));
        var btn = findById(rootElement, "toolbarCollapseBtn");
        if (btn != null) {
            var inner = btn.getChildren().stream()
                    .filter(c -> c instanceof Label)
                    .map(c -> (Label) c)
                    .findFirst().orElse(null);
            if (inner != null) {
                inner.setText(Component.literal(toolbarExpanded ? "◀" : "▶"));
            }
        }
    }

    private static void toggleToolbarPanel() {
        toolbarVisible = !toolbarVisible;
        if (toolbarPanel != null) {
            toolbarPanel.setDisplay(toolbarVisible);
        }
    }

    private static UIElement buildToolbarButton(EditorTool tool) {
        var btn = new UIElement();
        btn.layout(l -> l.width(26).height(26).alignItems(AlignItems.CENTER).justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER));
        btn.style(s -> s.background(Sprites.RECT_RD));
        btn.setId("toolbar_btn_" + tool.name());
        registerTooltip(btn, Component.translatable(tool.getTranslationKey()));

        var iconPath = switch (tool) {
            case SELECT -> "ebe:textures/gui/tool_select.png";
            case PLACE -> "ebe:textures/gui/tool_place.png";
            case DELETE -> "ebe:textures/gui/tool_delete.png";
            case REPLACE -> "ebe:textures/gui/tool_replace.png";
            case GRAB -> "ebe:textures/gui/tool_grab.png";
            case MEASURE -> "ebe:textures/gui/tool_measure.png";
            case FILL -> "ebe:textures/gui/tool_fill.png";
        };
        var icon = new UIElement();
        icon.layout(l -> l.width(20).height(20).paddingAll(3));
        icon.style(s -> s.backgroundTexture(SpriteTexture.of(iconPath)));
        btn.addChild(icon);

        btn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) selectTool(tool);
        });
        return btn;
    }

    private static void registerTooltip(UIElement el, Component tooltip) {
        el.addEventListener(UIEvents.MOUSE_ENTER, e -> el.style(s -> s.tooltips(tooltip)));
    }

    private static void highlightCurrentTool() {
        var active = state.getActiveTool();
        for (var entry : toolbarButtons.entrySet()) {
            var bg = entry.getKey() == active ? Sprites.RECT_RD_DARK : Sprites.RECT_RD;
            entry.getValue().style(s -> s.background(bg));
        }
    }

    private static UIElement buildModeToggleButton() {
        var btn = new UIElement();
        btn.layout(l -> l.width(26).height(26).alignItems(AlignItems.CENTER).justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER));
        btn.setId("toolbar_mode_btn");
        updateModeButtonStyle(btn);

        var label = new Label();
        label.setId("modeBtnLabel");
        label.setText(Component.literal("👁"));
        label.textStyle(ts -> ts.fontSize(14));
        btn.addChild(label);

        btn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) toggleMode();
        });
        return btn;
    }

    private static void updateModeButtonStyle(UIElement btn) {
        var mode = state.getMode();
        if (mode == EditorMode.VIEW) {
            btn.style(s -> s.background(Sprites.RECT_RD));
        } else {
            btn.style(s -> s.background(Sprites.RECT_RD_DARK));
        }
        var modeLabel = findById(btn, "modeBtnLabel");
        if (modeLabel instanceof Label l) {
            l.setText(mode == EditorMode.VIEW ? Component.literal("👁") : Component.literal("✏"));
        }
        registerTooltip(btn, Component.translatable(mode.getKey()));
    }

    private static void toggleMode() {
        var newMode = state.getMode() == EditorMode.VIEW ? EditorMode.EDIT : EditorMode.VIEW;
        state.setMode(newMode);
        var btn = findById(rootElement, "toolbar_mode_btn");
        if (btn != null) updateModeButtonStyle(btn);
        EditorUI.updateStatusBar();
    }

    private static void selectTool(EditorTool tool) {
        state.setActiveTool(tool);
        highlightCurrentTool();
        refreshKeybindHints();
    }

    private static void undo() {
        var entry = history.undo();
        if (entry == null) return;
        for (var s : entry.getSnapshots()) {
            int x = (int) s[0], y = (int) s[1], z = (int) s[2];
            session.getModel().setBlockAt(x, y, z, s[3]);
        }
        ViewportFactory.refreshFromModel(session.getModel());
        refreshMaterialList();
        refreshHistoryList();
    }

    private static void redo() {
        var entry = history.redo();
        if (entry == null) return;
        for (var s : entry.getSnapshots()) {
            int x = (int) s[0], y = (int) s[1], z = (int) s[2];
            session.getModel().setBlockAt(x, y, z, s[4]);
        }
        ViewportFactory.refreshFromModel(session.getModel());
        refreshMaterialList();
        refreshHistoryList();
    }

    private static void goToHistoryEntry(int displayIdx) {
        int count = history.goToEntryCount(displayIdx);
        if (count <= 0) return;
        var undone = history.popUndoEntries(count);
        var model = session.getModel();
        for (var entry : undone) {
            for (var s : entry.getSnapshots()) {
                int x = (int) s[0], y = (int) s[1], z = (int) s[2];
                model.setBlockAt(x, y, z, s[3]);
            }
        }
        ViewportFactory.refreshFromModel(model);
        refreshMaterialList();
        refreshHistoryList();
    }

    private static void selectAll() {
        selection.clear();
        for (var region : session.getModel().getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var val = region.getBlocks().get(x, y, z);
                        if (val instanceof net.minecraft.world.level.block.state.BlockState bs && bs.isAir()) continue;
                        if (val instanceof String s && s.equals("minecraft:air")) continue;
                        selection.add(x + region.getOffsetX(), y + region.getOffsetY(), z + region.getOffsetZ());
                    }
                }
            }
        }
        updateSelectionCount();
    }

    private static void updateSelectionCount() {
        state.setSelectedCount(selection.size());
        updateStatusBar();
    }

    // ========== Block Indicator ==========

    private static UIElement buildBlockIndicator() {
        var panel = new UIElement();
        panel.setId("blockIndicatorPanel");
        panel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .right(4).bottom(4)
                .minWidth(200).maxWidth(360)
                .flexDirection(FlexDirection.COLUMN).gapAll(4)
                .paddingHorizontal(8).paddingVertical(4));
        panel.style(s -> s.background(Sprites.BORDER).zIndex(100));

        var inspectedRow = buildIndicatorRow(
                "inspectedBlockIcon", "inspectedBlockLabel", "inspectedBlockNbtLabel",
                Component.translatable("ebe.editor.indicator.inspected"),
                0xFFFFD700);
        panel.addChild(inspectedRow);

        var separator = new UIElement();
        separator.layout(l -> l.widthPercent(100).height(1));
        separator.style(s -> s.background(Sprites.RECT_DARK));
        panel.addChild(separator);

        var activeRow = buildIndicatorRow(
                "activeBlockIcon", "activeBlockLabel", "activeBlockNbtLabel",
                Component.translatable("ebe.editor.indicator.active"),
                0xFF70FF70);
        panel.addChild(activeRow);

        return panel;
    }

    private static UIElement buildIndicatorRow(String iconId, String nameId, String nbtId, Component title, int nameColor) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.START).gapAll(6));

        var iconWrap = new UIElement();
        iconWrap.setId(iconId);
        iconWrap.layout(l -> l.width(24).height(24));
        iconWrap.style(s -> s.backgroundTexture(new ItemStackTexture(Items.AIR)));
        row.addChild(iconWrap);

        var infoCol = new UIElement();
        infoCol.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(1).flex(1).minWidth(140));

        var titleLabel = new Label();
        titleLabel.setText(title);
        titleLabel.textStyle(ts -> ts.textColor(0xFFA0A0A0).textShadow(false).fontSize(8));
        infoCol.addChild(titleLabel);

        var nameLabel = new Label();
        nameLabel.setId(nameId);
        nameLabel.setText(Component.translatable("ebe.editor.palette.selected_none"));
        nameLabel.textStyle(ts -> ts.textColor(nameColor).textShadow(false).fontSize(11)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        nameLabel.layout(l -> l.widthPercent(100));
        infoCol.addChild(nameLabel);

        var nbtLabel = new Label();
        nbtLabel.setId(nbtId);
        nbtLabel.setText(Component.literal(""));
        nbtLabel.textStyle(ts -> ts.textColor(0xFFA0A0A0).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        nbtLabel.layout(l -> l.widthPercent(100));
        infoCol.addChild(nbtLabel);

        row.addChild(infoCol);
        return row;
    }

    public static void updateActiveBlockIndicator() {
        var bs = state.getActiveBlockState();
        updateIndicatorRow("activeBlockIcon", "activeBlockLabel", "activeBlockNbtLabel", bs);
    }

    public static void updateBlockInspection() {
        var bs = state.getInspectedBlockState();
        updateIndicatorRow("inspectedBlockIcon", "inspectedBlockLabel", "inspectedBlockNbtLabel", bs);
    }

    private static void updateIndicatorRow(String iconId, String nameId, String nbtId, net.minecraft.world.level.block.state.BlockState bs) {
        var iconWrap = UIUtils.findById(rootElement, iconId);
        var nameLabel = UIUtils.findById(rootElement, nameId);
        var nbtLabel = UIUtils.findById(rootElement, nbtId);

        if (bs != null && !bs.isAir()) {
            if (iconWrap != null) {
                iconWrap.style(s -> s.backgroundTexture(new ItemStackTexture(bs.getBlock().asItem())));
            }
            if (nameLabel instanceof Label l) {
                l.setText(bs.getBlock().getName());
            }
            if (nbtLabel instanceof Label l) {
                var props = bs.getValues();
                if (props.isEmpty()) {
                    l.setText(Component.literal(""));
                } else {
                    var sb = new StringBuilder();
                    for (var entry : props.entrySet()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(entry.getKey().getName()).append("=").append(entry.getValue());
                    }
                    l.setText(Component.literal(sb.toString()));
                }
            }
        } else {
            if (iconWrap != null) {
                iconWrap.style(s -> s.backgroundTexture(new ItemStackTexture(Items.AIR)));
            }
            if (nameLabel instanceof Label l) {
                l.setText(Component.translatable("ebe.editor.palette.selected_none"));
            }
            if (nbtLabel instanceof Label l) {
                l.setText(Component.literal(""));
            }
        }
    }

    // ========== Panels ==========

    private static TabView createTopTabView() {
        var tabView = new TabView();
        tabView.layout(l -> l.flexDirection(FlexDirection.COLUMN).flex(1).widthPercent(100));
        var header = tabView.tabHeaderContainer;
        var tvContent = tabView.tabContentContainer;
        tabView.removeChild(header);
        tabView.removeChild(tvContent);
        tabView.addChild(header);
        tabView.addChild(tvContent);
        return tabView;
    }

    private static UIElement buildLeftPanel() {
        var panel = new UIElement();
        panel.layout(l -> l.widthPercent(15).minWidth(120).maxWidth(300).flexDirection(FlexDirection.COLUMN));
        panel.style(s -> s.background(Sprites.RECT_DARK));
        panel.setId("leftPanel");

        var tabView = createTopTabView();
        var filesTab = new Tab();
        filesTab.setText(Component.translatable("ebe.editor.panel.files"));
        tabView.addTab(filesTab, createFilesContent());

        var layersTab = new Tab();
        layersTab.setText(Component.translatable("ebe.editor.panel.layers"));
        tabView.addTab(layersTab, createLayersContent());

        panel.addChild(tabView);
        return panel;
    }

    private static UIElement buildRightPanel() {
        var panel = new UIElement();
        panel.layout(l -> l.widthPercent(18).minWidth(150).maxWidth(350).flexDirection(FlexDirection.COLUMN));
        panel.style(s -> s.background(Sprites.RECT_DARK));
        panel.setId("rightPanel");

        var tabView = createTopTabView();
        var propsTab = new Tab();
        propsTab.setText(Component.translatable("ebe.editor.panel.properties"));
        tabView.addTab(propsTab, createPropertiesContent());

        var matsTab = new Tab();
        matsTab.setText(Component.translatable("ebe.editor.panel.materials"));
        tabView.addTab(matsTab, createMaterialsContent());

        var histTab = new Tab();
        histTab.setText(Component.translatable("ebe.editor.panel.history"));
        tabView.addTab(histTab, createHistoryContent());

        panel.addChild(tabView);
        return panel;
    }

    private static UIElement createFilesContent() {
        var treeList = createFileTree();
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));
        scroller.addScrollViewChild(treeList);
        return scroller;
    }

    private static UIElement createLayersContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).gapAll(2));
        var placeholder = new Label();
        placeholder.setText(Component.literal("No layers"));
        placeholder.textStyle(ts -> ts.textColor(0xFF808080));
        container.addChild(placeholder);
        return container;
    }

    private static UIElement createMaterialsContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4));
        container.setId("materialsContent");

        materialListContainer = new UIElement();
        materialListContainer.layout(l -> l.flexDirection(FlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP)
                .gapAll(4).alignItems(AlignItems.CENTER));
        materialListContainer.setId("materialsList");

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));
        scroller.addScrollViewChild(materialListContainer);
        container.addChild(scroller);

        updateMaterialList();
        return container;
    }

    private static UIElement createHistoryContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4));
        container.setId("historyContent");

        var header = new UIElement();
        header.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).paddingHorizontal(2).gapAll(4));

        var undoBtn = new UIElement();
        undoBtn.layout(l -> l.width(20).height(20));
        undoBtn.style(s -> s.backgroundTexture(SpriteTexture.of("ebe:textures/gui/undo.png")));
        undoBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) undo(); });
        header.addChild(undoBtn);

        var redoBtn = new UIElement();
        redoBtn.layout(l -> l.width(20).height(20));
        redoBtn.style(s -> s.backgroundTexture(SpriteTexture.of("ebe:textures/gui/redo.png")));
        redoBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) redo(); });
        header.addChild(redoBtn);

        var clearBtn = new Button();
        clearBtn.setText(Component.literal("✕"));
        clearBtn.layout(l -> l.width(20).height(20));
        clearBtn.style(s -> s.background(Sprites.RECT_RD));
        clearBtn.setOnClick(e -> { history.clear(); refreshHistoryList(); });
        header.addChild(clearBtn);

        container.addChild(header);

        historyListContainer = new UIElement();
        historyListContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(1).paddingTop(2));

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));
        scroller.addScrollViewChild(historyListContainer);
        container.addChild(scroller);

        refreshHistoryList();
        return container;
    }

    public static void refreshHistoryList() {
        if (historyListContainer == null) return;
        historyListContainer.clearAllChildren();

        var entries = history.getUndoEntries();
        if (entries.isEmpty()) {
            var empty = new Label();
            empty.setText(Component.literal("No history"));
            empty.textStyle(ts -> ts.textColor(0xFF707070).fontSize(9));
            historyListContainer.addChild(empty);
            return;
        }

        int idx = entries.size();
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            var row = buildHistoryRow(entry, idx--);
            historyListContainer.addChild(row);
        }
    }

    private static UIElement buildHistoryRow(com.l1ght.ebe.editor.history.HistoryEntry entry, int displayIdx) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4).paddingHorizontal(4).paddingVertical(2));
        row.style(s -> s.background(Sprites.RECT_DARK));

        var icon = new UIElement();
        icon.layout(l -> l.width(18).height(18));
        if (entry.getPrimaryBlock() instanceof net.minecraft.world.level.block.state.BlockState bs) {
            icon.style(s -> s.backgroundTexture(new ItemStackTexture(bs.getBlock().asItem())));
        } else {
            icon.style(s -> s.background(Sprites.RECT_RD));
        }
        row.addChild(icon);

        var infoCol = new UIElement();
        infoCol.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(1).flex(1));

        var title = new Label();
        var actionName = Component.translatable(entry.getActionType().getKey());
        var countStr = entry.getAffectedCount() > 1 ? " ×" + entry.getAffectedCount() : "";
        title.setText(Component.literal("#" + displayIdx + " ").append(actionName).append(countStr));
        title.textStyle(ts -> ts.textColor(0xFFDDDDDD).fontSize(10));
        infoCol.addChild(title);

        var pos = new Label();
        pos.setText(Component.literal(entry.getPrimaryX() + ", " + entry.getPrimaryY() + ", " + entry.getPrimaryZ()));
        pos.textStyle(ts -> ts.textColor(0xFF808080).fontSize(9));
        infoCol.addChild(pos);

        row.addChild(infoCol);

        var jumpBtn = new Button();
        jumpBtn.setText(Component.literal("↩"));
        jumpBtn.layout(l -> l.width(16).height(16));
        jumpBtn.style(s -> s.background(Sprites.RECT_RD));
        var displayIdxCapture = displayIdx;
        jumpBtn.setOnClick(e -> goToHistoryEntry(displayIdxCapture));
        row.addChild(jumpBtn);

        return row;
    }

    private static UIElement createPropertiesContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(6));
        container.setId("propertiesContent");
        propertiesContainer = container;
        refreshPropertiesPanel();
        return container;
    }

    public static void refreshPropertiesPanel() {
        if (propertiesContainer == null) return;
        propertiesContainer.clearAllChildren();

        var bs = state.getInspectedBlockState();
        if (bs == null || bs.isAir()) {
            var empty = new Label();
            empty.setText(Component.translatable("ebe.editor.palette.selected_none"));
            empty.textStyle(ts -> ts.textColor(0xFF707070).fontSize(10));
            propertiesContainer.addChild(empty);
            return;
        }

        var block = bs.getBlock();
        var item = block.asItem();
        var itemKey = BuiltInRegistries.ITEM.getKey(item);
        var modId = itemKey.getNamespace();

        var row1 = new UIElement();
        row1.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(6));

        var icon = new UIElement();
        icon.layout(l -> l.width(28).height(28));
        icon.style(s -> s.backgroundTexture(new ItemStackTexture(item)));
        row1.addChild(icon);

        var nameLabel = new Label();
        nameLabel.setText(block.getName());
        nameLabel.textStyle(ts -> ts.textColor(0xFFEEEEEE).fontSize(12).textShadow(false));
        row1.addChild(nameLabel);
        propertiesContainer.addChild(row1);

        addPropField(propertiesContainer, "ID", itemKey.toString(), 0xFFCCCC88);

        var modLabel = new Label();
        modLabel.setText(Component.literal("Mod: " + modId));
        modLabel.textStyle(ts -> ts.textColor(0xFF888888).fontSize(9));
        propertiesContainer.addChild(modLabel);

        var posSection = new UIElement();
        posSection.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4).paddingTop(4));

        var posLabel = new Label();
        posLabel.setText(Component.literal("Position:"));
        posLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9));
        posSection.addChild(posLabel);

        var posRow = new UIElement();
        posRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4));

        posRow.addChild(buildCoordField("X", state.getCursorX()));
        posRow.addChild(buildCoordField("Y", state.getCursorY()));
        posRow.addChild(buildCoordField("Z", state.getCursorZ()));
        posSection.addChild(posRow);
        propertiesContainer.addChild(posSection);

        var propsList = bs.getProperties();
        if (!propsList.isEmpty()) {
            var propsLabel = new Label();
            propsLabel.setText(Component.literal("BlockStates:"));
            propsLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9));
            propertiesContainer.addChild(propsLabel);

            for (Property<?> prop : propsList) {
                var propValRow = new UIElement();
                propValRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .alignItems(AlignItems.CENTER).gapAll(4).paddingHorizontal(4).paddingVertical(1));
                propValRow.style(s -> s.background(Sprites.RECT_DARK));

                var propNameLabel = new Label();
                propNameLabel.setText(Component.literal(prop.getName()));
                propNameLabel.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(9));
                propValRow.addChild(propNameLabel);

                var spacer = new UIElement();
                spacer.layout(l -> l.flex(1));
                propValRow.addChild(spacer);

                @SuppressWarnings({"unchecked", "rawtypes"})
                Comparable<?> currentValue = bs.getValue((Property) prop);
                var valBtn = new Button();
                valBtn.setText(Component.literal(currentValue.toString()));
                valBtn.layout(l -> l.paddingHorizontal(6).height(16));
                valBtn.style(s -> s.background(Sprites.RECT_RD));

                var propRef = prop;
                valBtn.setOnClick(e -> {
                    var newBs = cycleProperty(bs, propRef);
                    state.setInspectedBlockState(newBs);
                    var sessionModel = session.getModel();
                    sessionModel.setBlockAt(state.getCursorX(), state.getCursorY(), state.getCursorZ(), newBs);
                    session.markDirty();
                    ViewportFactory.placeBlock(
                            new net.minecraft.core.BlockPos(state.getCursorX(), state.getCursorY(), state.getCursorZ()),
                            newBs, history);
                    EditorUI.updateBlockInspection();
                    refreshPropertiesPanel();
                    refreshHistoryList();
                    refreshMaterialList();
                });
                propValRow.addChild(valBtn);
                propertiesContainer.addChild(propValRow);
            }
        }
    }

    private static UIElement buildCoordField(String label, int value) {
        var col = new UIElement();
        col.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(1).flex(1));

        var lbl = new Label();
        lbl.setText(Component.literal(label));
        lbl.textStyle(ts -> ts.textColor(0xFF888888).fontSize(8));
        col.addChild(lbl);

        var tf = new TextField();
        tf.setText(String.valueOf(value));
        tf.layout(l -> l.widthPercent(100).height(14));
        col.addChild(tf);

        return col;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static net.minecraft.world.level.block.state.BlockState cycleProperty(
            net.minecraft.world.level.block.state.BlockState bs, Property property) {
        var values = property.getPossibleValues();
        var current = bs.getValue(property);
        Comparable next = null;
        boolean found = false;
        for (Comparable v : (Iterable<Comparable>) values) {
            if (found) { next = v; break; }
            if (v.equals(current)) found = true;
        }
        if (next == null) next = (Comparable) values.iterator().next();
        return bs.setValue(property, next);
    }

    private static void addPropField(UIElement parent, String label, String value, int color) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.START).gapAll(2));

        var lbl = new Label();
        lbl.setText(Component.literal(label + ":"));
        lbl.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9));
        lbl.layout(l -> l.minWidth(18));
        row.addChild(lbl);

        var val = new Label();
        val.setText(Component.literal(value));
        val.textStyle(ts -> ts.textColor(color).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        val.layout(l -> l.flex(1));
        row.addChild(val);

        parent.addChild(row);
    }

    private static UIElement createPlaceholderPanel() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4));
        return container;
    }

    // ========== File Tree ==========

    private static TreeList<FileTreeNode> createFileTree() {
        var root = buildFileSystemTree();
        var treeList = new TreeList<FileTreeNode>(root, true);
        treeList.setNodeUISupplier(TreeList.textTemplate(
                node -> Component.literal(node.getPath().getFileName().toString())
        ));
        treeList.setClickToExpand(true);
        treeList.setDoubleClickToExpand(true);
        treeList.setOnDoubleClickNode(node -> {
            if (!node.isDirectory()) {
                EditorDialogs.confirmDialog(rootElement,
                        Component.translatable("ebe.editor.confirm_load", node.getPath().getFileName().toString()),
                        () -> onFileSelected(node.getPath()));
            }
        });
        return treeList;
    }

    private static FileTreeNode buildFileSystemTree() {
        var dir = Path.of(EBEClientConfig.schematicDir.get());
        var root = FileTreeNode.ofDirectory(dir);
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.sorted(Comparator
                        .comparing((Path p) -> !Files.isDirectory(p))
                        .thenComparing(Path::getFileName))
                        .forEach(p -> root.addChild(Files.isDirectory(p)
                                ? FileTreeNode.ofDirectory(p)
                                : FileTreeNode.ofFile(p)));
            } catch (Exception ignored) {}
        }
        return root;
    }

    private static void onFileSelected(Path file) {
        try {
            session.load(file);
            ViewportFactory.loadFromModel(session.getModel());
            refreshMaterialList();
            updateStatusBar();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Button buildCollapseButton(boolean isLeft) {
        var btn = new Button();
        btn.setText(Component.literal(isLeft ? "◀" : "▶"));
        btn.layout(l -> l.width(14).heightPercent(100));
        btn.style(s -> s.background(Sprites.RECT_DARK));
        btn.setId(isLeft ? "leftCollapseBtn" : "rightCollapseBtn");
        btn.setOnClick(e -> {
            if (isLeft) toggleLeftPanel();
            else toggleRightPanel();
        });
        return btn;
    }

    private static void toggleLeftPanel() {
        leftPanelVisible = !leftPanelVisible;
        leftPanel.setDisplay(leftPanelVisible);
        leftCollapseBtn.setText(Component.literal(leftPanelVisible ? "◀" : "▶"));
    }

    private static void toggleRightPanel() {
        rightPanelVisible = !rightPanelVisible;
        rightPanel.setDisplay(rightPanelVisible);
        rightCollapseBtn.setText(Component.literal(rightPanelVisible ? "▶" : "◀"));
    }

    private static void toggleBlockIndicator() {
        blockIndicatorVisible = !blockIndicatorVisible;
        if (blockIndicatorPanel != null) {
            blockIndicatorPanel.setDisplay(blockIndicatorVisible);
        }
    }

    private static UIElement buildKeybindHintsPanel() {
        var panel = new UIElement();
        panel.setId("keybindHintsPanel");
        panel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .right(4).top(4)
                .maxWidth(260)
                .flexDirection(FlexDirection.COLUMN).gapAll(2)
                .paddingHorizontal(8).paddingVertical(4));
        panel.style(s -> s.background(Sprites.BORDER).zIndex(100));

        var title = new Label();
        title.setText(Component.translatable("ebe.editor.keybind_hints"));
        title.textStyle(ts -> ts.textColor(0xFFFFD700).textShadow(false).fontSize(9));
        panel.addChild(title);

        var sep = new UIElement();
        sep.layout(l -> l.widthPercent(100).height(1));
        sep.style(s -> s.background(Sprites.RECT_DARK));
        panel.addChild(sep);

        var hintsLabel = new Label();
        hintsLabel.setId("keybindHintsText");
        hintsLabel.setText(Component.literal(""));
        hintsLabel.textStyle(ts -> ts.textColor(0xFFCCCCCC).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hintsLabel.layout(l -> l.widthPercent(100));
        panel.addChild(hintsLabel);

        refreshKeybindHints();
        return panel;
    }

    private static void toggleKeybindHints() {
        keybindHintsVisible = !keybindHintsVisible;
        if (keybindHintsPanel != null) {
            keybindHintsPanel.setDisplay(keybindHintsVisible);
        }
    }

    public static void refreshKeybindHints() {
        if (keybindHintsPanel == null) return;
        var hintsLabel = UIUtils.findById(keybindHintsPanel, "keybindHintsText");
        if (!(hintsLabel instanceof Label l)) return;

        var tool = state.getActiveTool();
        String text = switch (tool) {
            case SELECT -> "■ " +
                    Component.translatable("ebe.hints.select.click").getString() + "\n" +
                    "■ Shift+" + Component.translatable("ebe.hints.select.box").getString() + "\n" +
                    "■ " + Component.translatable("ebe.hints.common.undo").getString() + ": Ctrl+Z\n" +
                    "■ " + Component.translatable("ebe.hints.common.clipboard").getString() + ": Ctrl+C/V/X";
            case PLACE -> "■ " +
                    Component.translatable("ebe.hints.place.click").getString() + "\n" +
                    "■ " + Component.translatable("ebe.hints.common.undo").getString() + ": Ctrl+Z";
            case DELETE -> "■ " +
                    Component.translatable("ebe.hints.delete.click").getString() + "\n" +
                    "■ " + Component.translatable("ebe.hints.common.undo").getString() + ": Ctrl+Z";
            case REPLACE -> "■ " +
                    Component.translatable("ebe.hints.replace.click").getString() + "\n" +
                    "■ " + Component.translatable("ebe.hints.common.undo").getString() + ": Ctrl+Z";
            case GRAB -> "■ " +
                    Component.translatable("ebe.hints.grab.click").getString() + "\n" +
                    "■ " + Component.translatable("ebe.hints.grab.middle").getString();
            case MEASURE -> "■ " +
                    Component.translatable("ebe.hints.measure.click").getString();
            case FILL -> "■ " +
                    Component.translatable("ebe.hints.fill.click").getString() + "\n" +
                    "■ " + Component.translatable("ebe.hints.fill.ctrl").getString();
        };
        l.setText(Component.literal(text));
    }

    // ========== Bottom Bar (Material List) ==========

    private static UIElement buildBottomBar() {
        var bar = new UIElement();
        bar.layout(l -> l.widthPercent(100).height(22).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).paddingHorizontal(8));
        bar.style(s -> s.background(Sprites.BORDER));
        bar.setId("bottomBar");

        var status = new Label();
        status.setText(Component.literal(""));
        status.textStyle(ts -> ts.textColor(0xFFE0E0E0).textShadow(false).fontSize(9));
        status.setId("statusBar");
        bar.addChild(status);

        return bar;
    }

    public static void refreshMaterialList() {
        updateMaterialList();
    }

    private static void updateMaterialList() {
        if (materialListContainer == null) return;
        materialListContainer.clearAllChildren();

        var model = session.getModel();
        if (model != null && !model.getRegions().isEmpty()) {
            Map<net.minecraft.world.level.block.Block, Integer> counts = new LinkedHashMap<>();
            for (var region : model.getRegions()) {
                var blocks = region.getBlocks();
                for (int y = 0; y < region.getSizeY(); y++) {
                    for (int z = 0; z < region.getSizeZ(); z++) {
                        for (int x = 0; x < region.getSizeX(); x++) {
                            var obj = blocks.get(x, y, z);
                            if (obj instanceof net.minecraft.world.level.block.state.BlockState bs && !bs.isAir()) {
                                counts.merge(bs.getBlock(), 1, Integer::sum);
                            } else if (obj instanceof String s && !s.isEmpty() && !s.equals("minecraft:air")) {
                                var locStr = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
                                var loc = net.minecraft.resources.ResourceLocation.parse(locStr);
                                BuiltInRegistries.BLOCK.getOptional(loc)
                                        .ifPresent(block -> counts.merge(block, 1, Integer::sum));
                            }
                        }
                    }
                }
            }

            for (var entry : counts.entrySet()) {
                var block = entry.getKey();
                var item = block.asItem();
                var modId = BuiltInRegistries.ITEM.getKey(item).getNamespace();
                var blockName = block.getName().getString();
                var tooltipText = Component.literal(blockName + "\n" + ChatFormatting.GRAY + modId);

                var row = new UIElement();
                row.layout(l -> l.flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.CENTER).gapAll(1));

                var icon = new UIElement();
                icon.layout(l -> l.width(24).height(24));
                icon.style(s -> s.backgroundTexture(new ItemStackTexture(item)));
                icon.addEventListener(UIEvents.MOUSE_ENTER, e -> icon.style(s -> s.tooltips(tooltipText)));
                row.addChild(icon);

                var countLabel = new Label();
                countLabel.setText(Component.literal("×" + entry.getValue()));
                countLabel.textStyle(ts -> ts.textColor(0xFFCCCCCC).textShadow(false).fontSize(8));
                row.addChild(countLabel);

                materialListContainer.addChild(row);
            }
        }
    }

    // ========== Status Bar ==========

    public static void updateStatusBar() {
        if (rootElement == null) return;
        var status = findById(rootElement, "statusBar");
        if (status instanceof Label l) {
            l.setText(Component.literal(state.buildStatusText()));
        }
    }

    // ========== Key Input Handler (called from EditorScreen) ==========

    public static boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl) {
            if (keyCode == GLFW.GLFW_KEY_Z) { undo(); return true; }
            if (keyCode == GLFW.GLFW_KEY_Y) { redo(); return true; }
            if (keyCode == GLFW.GLFW_KEY_C) { clipboard.copy(session.getModel(), selection); return true; }
            if (keyCode == GLFW.GLFW_KEY_V) {
                clipboard.paste(session.getModel(), new net.minecraft.core.BlockPos(
                        state.getCursorX(), state.getCursorY(), state.getCursorZ()), history);
                ViewportFactory.refreshFromModel(session.getModel());
                refreshMaterialList();
                refreshHistoryList();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                clipboard.cut(session.getModel(), selection, history);
                ViewportFactory.refreshFromModel(session.getModel());
                refreshMaterialList();
                refreshHistoryList();
                updateSelectionCount();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_A) { selectAll(); return true; }
            if (keyCode == GLFW.GLFW_KEY_D) { selection.clear(); updateSelectionCount(); return true; }
            return false;
        }

        if (EBEKeyMappings.TOOL_SELECT.matches(keyCode, scanCode)) { selectTool(EditorTool.SELECT); return true; }
        if (EBEKeyMappings.TOOL_PLACE.matches(keyCode, scanCode)) { selectTool(EditorTool.PLACE); return true; }
        if (EBEKeyMappings.TOOL_DELETE.matches(keyCode, scanCode)) { selectTool(EditorTool.DELETE); return true; }
        if (EBEKeyMappings.TOOL_REPLACE.matches(keyCode, scanCode)) { selectTool(EditorTool.REPLACE); return true; }
        if (EBEKeyMappings.TOOL_GRAB.matches(keyCode, scanCode)) { selectTool(EditorTool.GRAB); return true; }
        if (EBEKeyMappings.TOOL_MEASURE.matches(keyCode, scanCode)) { selectTool(EditorTool.MEASURE); return true; }
        if (EBEKeyMappings.TOOL_FILL.matches(keyCode, scanCode)) { selectTool(EditorTool.FILL); return true; }
        return false;
    }

    static UIElement findById(UIElement root, String id) {
        if (id.equals(root.getId())) return root;
        for (var child : root.getChildren()) {
            var found = findById(child, id);
            if (found != null) return found;
        }
        return null;
    }
}
