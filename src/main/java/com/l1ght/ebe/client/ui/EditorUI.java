package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.EBEKeyMappings;
import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.editor.selection.DisplayFilter;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import org.lwjgl.glfw.GLFW;

import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private static UIElement leftDivider;
    private static UIElement rightDivider;
    private static Button leftCollapseBtn;
    private static Button rightCollapseBtn;
    private static int activeDivider = 0;
    private static int dividerDragStartX = 0;
    private static float leftPanelWidthPercent = 15f;
    private static float rightPanelWidthPercent = 18f;
    private static UIElement blockIndicatorPanel;
    private static boolean leftPanelVisible = true;
    private static boolean rightPanelVisible = true;
    private static boolean blockIndicatorVisible = true;

    private static UIElement keybindHintsPanel;
    private static boolean keybindHintsVisible = true;
    private static UIElement replacePanel;
    private static boolean replacePanelVisible = false;
    private static UIElement fillPanel;
    private static boolean fillPanelVisible = false;

    private static UIElement toolbarPanel;
    private static boolean toolbarExpanded = true;
    private static boolean toolbarVisible = true;

    private static UIElement materialListContainer;
    private static UIElement propertiesContainer;
    private static int displayFilterMode = 0;
    private static UIElement displayFilterContentContainer;

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

        rootElement.addEventListener(UIEvents.MOUSE_MOVE, e -> {
            if (activeDivider == 0) return;
            int dx = (int) e.x - dividerDragStartX;
            dividerDragStartX = (int) e.x;

            float parentWidth = contentArea.getSizeWidth();
            if (parentWidth <= 0) return;
            float deltaPercent = (dx / parentWidth) * 100f;

            if (activeDivider == 1) {
                leftPanelWidthPercent = Math.max(8f, Math.min(35f, leftPanelWidthPercent + deltaPercent));
                leftPanel.layout(l -> l.widthPercent(leftPanelWidthPercent));
            } else if (activeDivider == 2) {
                rightPanelWidthPercent = Math.max(8f, Math.min(35f, rightPanelWidthPercent - deltaPercent));
                rightPanel.layout(l -> l.widthPercent(rightPanelWidthPercent));
            }
        }, true);

        rootElement.addEventListener(UIEvents.MOUSE_UP, e -> {
            if (activeDivider != 0) {
                leftDivider.style(s -> s.background(Sprites.RECT_DARK));
                rightDivider.style(s -> s.background(Sprites.RECT_DARK));
                activeDivider = 0;
            }
        }, true);

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

    public static boolean isTextFieldFocused() {
        if (rootElement == null) return false;
        var focused = rootElement.getModularUI().getFocusedElement();
        return focused instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
    }
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
        leftDivider = buildDivider(true);
        var viewport = ViewportFactory.create3DViewport();
        rightDivider = buildDivider(false);
        rightPanel = buildRightPanel();
        rightCollapseBtn = buildCollapseButton(false);

        content.addChild(leftCollapseBtn);
        content.addChild(leftPanel);
        content.addChild(leftDivider);
        content.addChild(viewport);
        content.addChild(rightDivider);
        content.addChild(rightPanel);
        content.addChild(rightCollapseBtn);

        toolbarPanel = buildToolbar();
        viewport.addChild(toolbarPanel);

        var blockIndicator = buildBlockIndicator();
        viewport.addChild(blockIndicator);
        blockIndicatorPanel = blockIndicator;

        keybindHintsPanel = buildKeybindHintsPanel();
        viewport.addChild(keybindHintsPanel);

        replacePanel = buildReplacePanel();
        replacePanel.setDisplay(false);
        viewport.addChild(replacePanel);

        fillPanel = buildFillPanel();
        fillPanel.setDisplay(false);
        viewport.addChild(fillPanel);

        if (!leftPanelVisible) {
            leftPanel.setDisplay(false);
            leftDivider.setDisplay(false);
            leftCollapseBtn.setText(Component.literal("▶"));
        }
        if (!rightPanelVisible) {
            rightPanel.setDisplay(false);
            rightDivider.setDisplay(false);
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
        if (replacePanel != null) {
            replacePanelVisible = (tool == EditorTool.REPLACE);
            replacePanel.setDisplay(replacePanelVisible);
        }
        if (fillPanel != null) {
            fillPanelVisible = (tool == EditorTool.FILL);
            fillPanel.setDisplay(fillPanelVisible);
        }
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
        panel.layout(l -> l.widthPercent(leftPanelWidthPercent).minWidth(80).maxWidth(500).heightPercent(100).flexDirection(FlexDirection.COLUMN));
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
        panel.layout(l -> l.widthPercent(rightPanelWidthPercent).minWidth(80).maxWidth(500).heightPercent(100).flexDirection(FlexDirection.COLUMN));
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

        var displayTab = new Tab();
        displayTab.setText(Component.translatable("ebe.editor.panel.display"));
        tabView.addTab(displayTab, buildDisplayFilterTab());

        panel.addChild(tabView);
        return panel;
    }

    private static UIElement createFilesContent() {
        var treeList = createFileTree();
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.addScrollViewChild(treeList);
        return scroller;
    }

    private static UIElement createLayersContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).gapAll(2));
        container.setId("layersContent");

        var header = new UIElement();
        header.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));

        var addBtn = new Button();
        addBtn.setText(Component.literal("+"));
        addBtn.layout(l -> l.width(20).height(20));
        addBtn.setOnClick(e -> {
            if (session != null) {
                session.getModel().addLayer("Layer " + session.getModel().getLayers().size(), true, false);
                refreshLayersList();
            }
        });
        header.addChild(addBtn);

        var title = new Label();
        title.setText(Component.translatable("ebe.editor.panel.layers"));
        title.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9));
        header.addChild(title);
        container.addChild(header);

        layersListContainer = new UIElement();
        layersListContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        layersListContainer.setId("layersList");

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.addScrollViewChild(layersListContainer);
        container.addChild(scroller);

        refreshLayersList();
        return container;
    }

    private static UIElement layersListContainer;
    private static String renamingLayerId = null;

    public static void refreshLayersList() {
        if (layersListContainer == null || session == null) return;
        layersListContainer.clearAllChildren();
        var layers = session.getModel().getLayers();
        for (var layer : layers) {
            var row = new UIElement();
            row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                    .alignItems(AlignItems.CENTER).gapAll(4).paddingHorizontal(2));

            var visBtn = new UIElement();
            visBtn.layout(l -> l.width(18).height(18));
            var visIcon = new UIElement();
            visIcon.layout(l -> l.widthPercent(100).heightPercent(100));
            visIcon.style(s -> s.backgroundTexture(layer.isVisible() ? EditorIcons.VISIBLE : EditorIcons.HIDDEN));
            visBtn.addChild(visIcon);
            registerTooltip(visBtn, Component.translatable("ebe.layer.visible"));
            visBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                if (e.button != 0) return;
                layer.setVisible(!layer.isVisible());
                refreshLayersList();
                ViewportFactory.refreshFromModel(session.getModel());
            });
            row.addChild(visBtn);

            var lockBtn = new UIElement();
            lockBtn.layout(l -> l.width(18).height(18));
            var lockIcon = new UIElement();
            lockIcon.layout(l -> l.widthPercent(100).heightPercent(100));
            lockIcon.style(s -> s.backgroundTexture(layer.isLocked() ? EditorIcons.LOCKED : EditorIcons.UNLOCKED));
            lockBtn.addChild(lockIcon);
            registerTooltip(lockBtn, Component.translatable("ebe.layer.locked"));
            lockBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                if (e.button != 0) return;
                layer.setLocked(!layer.isLocked());
                refreshLayersList();
            });
            row.addChild(lockBtn);

            if (renamingLayerId != null && renamingLayerId.equals(layer.getId())) {
                var tf = new com.lowdragmc.lowdraglib2.gui.ui.elements.TextField();
                tf.setText(layer.getName());
                tf.layout(l -> l.flex(1).height(14));
                tf.setTextResponder(newName -> {
                    if (newName != null && !newName.isEmpty()) {
                        layer.setName(newName);
                    }
                });
                tf.addEventListener(UIEvents.KEY_DOWN, e2 -> {
                    if (e2.keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || e2.keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                        renamingLayerId = null;
                        refreshLayersList();
                    }
                });
                tf.addEventListener(UIEvents.BLUR, e2 -> {
                    renamingLayerId = null;
                    refreshLayersList();
                });
                row.addChild(tf);
            } else {
                var nameLbl = new Label();
                nameLbl.setText(Component.literal(layer.getName()));
                nameLbl.textStyle(ts -> ts.textColor(layer.isVisible() ? 0xFFFFFFFF : 0xFF808080).fontSize(9));
                nameLbl.layout(l -> l.flex(1));
                long[] lastClickTime = {0};
                nameLbl.addEventListener(UIEvents.MOUSE_DOWN, e2 -> {
                    if (e2.button != 0) return;
                    long now = System.currentTimeMillis();
                    if (now - lastClickTime[0] < 400) {
                        renamingLayerId = layer.getId();
                        refreshLayersList();
                    }
                    lastClickTime[0] = now;
                });
                row.addChild(nameLbl);
            }

            var delBtn = new UIElement();
            delBtn.layout(l -> l.width(14).height(14));
            var delIcon = new UIElement();
            delIcon.layout(l -> l.widthPercent(100).heightPercent(100));
            delIcon.style(s -> s.backgroundTexture(EditorIcons.CLOSE));
            delBtn.addChild(delIcon);
            registerTooltip(delBtn, Component.translatable("ebe.editor.deselect"));
            delBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                if (e.button != 0) return;
                if (session.getModel().getLayers().size() <= 1) return;
                session.getModel().removeLayer(layer.getId());
                refreshLayersList();
                ViewportFactory.refreshFromModel(session.getModel());
            });
            row.addChild(delBtn);

            var moveToBtn = new UIElement();
            moveToBtn.layout(l -> l.width(14).height(14));
            var moveToIcon = new UIElement();
            moveToIcon.layout(l -> l.widthPercent(100).heightPercent(100));
            moveToIcon.style(s -> s.backgroundTexture(EditorIcons.SELECT));
            moveToBtn.addChild(moveToIcon);
            registerTooltip(moveToBtn, Component.translatable("ebe.layer.move_selection_to"));
            moveToBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                if (e.button != 0) return;
                if (session == null || selection.isEmpty()) return;
                for (var region : session.getModel().getRegions()) {
                    for (int y = 0; y < region.getSizeY(); y++) {
                        for (int z = 0; z < region.getSizeZ(); z++) {
                            for (int x = 0; x < region.getSizeX(); x++) {
                                int wx = x + region.getOffsetX();
                                int wy = y + region.getOffsetY();
                                int wz = z + region.getOffsetZ();
                                if (selection.contains(wx, wy, wz)) {
                                    region.setLayerId(layer.getId());
                                    break;
                                }
                            }
                        }
                    }
                }
                ViewportFactory.refreshFromModel(session.getModel());
            });
            row.addChild(moveToBtn);

            layersListContainer.addChild(row);
        }

        var actionRow = new UIElement();
        actionRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .gapAll(2).paddingHorizontal(2).marginTop(4));

        var createLayerBtn = new Button();
        createLayerBtn.setText(Component.translatable("ebe.layer.create_from_selection"));
        createLayerBtn.layout(l -> l.flex(1).height(18));
        createLayerBtn.setOnClick(e -> {
            if (session == null || selection.isEmpty()) return;
            var newLayer = session.getModel().addLayer("Layer " + session.getModel().getLayers().size(), true, false);
            for (var region : session.getModel().getRegions()) {
                for (int y = 0; y < region.getSizeY(); y++) {
                    for (int z = 0; z < region.getSizeZ(); z++) {
                        for (int x = 0; x < region.getSizeX(); x++) {
                            int wx = x + region.getOffsetX();
                            int wy = y + region.getOffsetY();
                            int wz = z + region.getOffsetZ();
                            if (selection.contains(wx, wy, wz)) {
                                if (newLayer.getId().equals(region.getLayerId())) continue;
                                region.setLayerId(newLayer.getId());
                                break;
                            }
                        }
                    }
                }
            }
            refreshLayersList();
            ViewportFactory.refreshFromModel(session.getModel());
        });
        actionRow.addChild(createLayerBtn);

        layersListContainer.addChild(actionRow);
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
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
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
        undoBtn.style(s -> s.backgroundTexture(EditorIcons.UNDO));
        undoBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) undo(); });
        header.addChild(undoBtn);

        var redoBtn = new UIElement();
        redoBtn.layout(l -> l.width(20).height(20));
        redoBtn.style(s -> s.backgroundTexture(EditorIcons.REDO));
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
        scroller.layout(l -> l.widthPercent(100).flex(1));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.addScrollViewChild(historyListContainer);
        container.addChild(scroller);

        refreshHistoryList();
        return container;
    }

    private static UIElement buildDisplayFilterTab() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4));
        container.setId("displayFilterContent");

        var currentModeLabel = new Label();
        currentModeLabel.setId("displayCurrentMode");
        currentModeLabel.setText(Component.translatable("ebe.display.filter.mode.all"));
        currentModeLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(9).textShadow(false));
        container.addChild(currentModeLabel);

        var modeLabel = new Label();
        modeLabel.setText(Component.translatable("ebe.display.filter.type"));
        modeLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9).textShadow(false));
        container.addChild(modeLabel);

        var modeRow = new UIElement();
        modeRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(3));
        modeRow.setId("displayFilterModeRow");

        String[] modeKeys = {
                "ebe.display.filter.mode.all",
                "ebe.display.filter.mode.block_type",
                "ebe.display.filter.mode.y_layer",
                "ebe.display.filter.mode.row_column",
                "ebe.display.filter.mode.selection"
        };
        for (int i = 0; i < modeKeys.length; i++) {
            final int idx = i;
            var btn = new Button();
            btn.setText(Component.translatable(modeKeys[i]));
            btn.layout(l -> l.height(18).paddingHorizontal(4));
            btn.setId("displayModeBtn_" + i);
            btn.style(s -> s.background(idx == displayFilterMode ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            btn.setOnClick(e -> {
                displayFilterMode = idx;
                updateDisplayFilterModeHighlight();
                updateDisplayFilterContent();
            });
            modeRow.addChild(btn);
        }
        container.addChild(modeRow);

        displayFilterContentContainer = new UIElement();
        displayFilterContentContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4).paddingTop(4));
        displayFilterContentContainer.setId("displayFilterParams");
        container.addChild(displayFilterContentContainer);

        var nbtRow = new UIElement();
        nbtRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(6).paddingTop(4));

        var nbtBtn = new Button();
        nbtBtn.setText(Component.translatable("ebe.display.filter.nbt_toggle"));
        nbtBtn.layout(l -> l.height(18).paddingHorizontal(6));
        nbtBtn.setId("displayNbtToggle");
        nbtBtn.style(s -> s.background(Sprites.RECT_RD));
        nbtBtn.setOnClick(e -> {
            var filter = state.getDisplayFilter();
            filter.setNbtSensitive(!filter.isNbtSensitive());
            nbtBtn.style(s -> s.background(filter.isNbtSensitive() ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
        });
        nbtRow.addChild(nbtBtn);
        container.addChild(nbtRow);

        var resetBtn = new Button();
        resetBtn.setText(Component.translatable("ebe.display.filter.reset"));
        resetBtn.layout(l -> l.widthPercent(100).height(22));
        resetBtn.style(s -> s.background(Sprites.RECT_RD));
        resetBtn.setOnClick(e -> {
            displayFilterMode = 0;
            state.getDisplayFilter().reset();
            updateDisplayFilterModeHighlight();
            updateDisplayFilterContent();
            updateCurrentModeLabel();
            var nbtToggle = UIUtils.findById(rightPanel, "displayNbtToggle");
            if (nbtToggle instanceof Button b) {
                b.style(s -> s.background(Sprites.RECT_RD));
            }
            applyDisplayFilter();
        });
        container.addChild(resetBtn);

        updateDisplayFilterContent();
        return container;
    }

    private static void updateCurrentModeLabel() {
        String[] modeKeys = {
                "ebe.display.filter.mode.all",
                "ebe.display.filter.mode.block_type",
                "ebe.display.filter.mode.y_layer",
                "ebe.display.filter.mode.row_column",
                "ebe.display.filter.mode.selection"
        };
        var label = UIUtils.findById(rightPanel, "displayCurrentMode");
        if (label instanceof Label l && displayFilterMode < modeKeys.length) {
            l.setText(Component.translatable(modeKeys[displayFilterMode]));
        }
    }

    private static void updateDisplayFilterModeHighlight() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            var btn = UIUtils.findById(rightPanel, "displayModeBtn_" + i);
            if (btn instanceof Button b) {
                b.style(s -> s.background(idx == displayFilterMode ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            }
        }
    }

    private static void updateDisplayFilterContent() {
        if (displayFilterContentContainer == null) return;
        displayFilterContentContainer.clearAllChildren();

        if (displayFilterMode == 0) {
            var applyBtn = new Button();
            applyBtn.setText(Component.translatable("ebe.display.filter.apply"));
            applyBtn.layout(l -> l.widthPercent(100).height(22));
            applyBtn.style(s -> s.background(Sprites.RECT_RD));
            applyBtn.setOnClick(e -> {
                applyDisplayFilter();
                updateCurrentModeLabel();
            });
            displayFilterContentContainer.addChild(applyBtn);
        } else if (displayFilterMode == 1) {
            var useSelectedBtn = new Button();
            useSelectedBtn.setText(Component.translatable("ebe.display.filter.use_selected_type"));
            useSelectedBtn.layout(l -> l.widthPercent(100).height(22));
            useSelectedBtn.style(s -> s.background(Sprites.RECT_RD));
            useSelectedBtn.setOnClick(e -> {
                var active = state.getActiveBlockState();
                if (active != null) {
                    var filter = state.getDisplayFilter();
                    filter.clearVisibleBlockTypes();
                    filter.addVisibleBlockType(active.getBlock());
                    applyDisplayFilter();
                    updateCurrentModeLabel();
                }
            });
            displayFilterContentContainer.addChild(useSelectedBtn);
        } else if (displayFilterMode == 2) {
            var minYRow = new UIElement();
            minYRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4).alignItems(AlignItems.CENTER));
            var minLabel = new Label();
            minLabel.setText(Component.translatable("ebe.display.filter.min_y"));
            minLabel.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(9).textShadow(false));
            minLabel.layout(l -> l.width(40));
            minYRow.addChild(minLabel);
            var minYField = new TextField();
            minYField.layout(l -> l.flex(1).height(18));
            minYField.setId("filterMinY");
            minYField.setText("0");
            minYRow.addChild(minYField);
            displayFilterContentContainer.addChild(minYRow);

            var maxYRow = new UIElement();
            maxYRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4).alignItems(AlignItems.CENTER));
            var maxLabel = new Label();
            maxLabel.setText(Component.translatable("ebe.display.filter.max_y"));
            maxLabel.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(9).textShadow(false));
            maxLabel.layout(l -> l.width(40));
            maxYRow.addChild(maxLabel);
            var maxYField = new TextField();
            maxYField.layout(l -> l.flex(1).height(18));
            maxYField.setId("filterMaxY");
            maxYField.setText("255");
            maxYRow.addChild(maxYField);
            displayFilterContentContainer.addChild(maxYRow);

            var applyBtn = new Button();
            applyBtn.setText(Component.translatable("ebe.display.filter.apply"));
            applyBtn.layout(l -> l.widthPercent(100).height(22));
            applyBtn.style(s -> s.background(Sprites.RECT_RD));
            applyBtn.setOnClick(e -> {
                applyDisplayFilter();
                updateCurrentModeLabel();
            });
            displayFilterContentContainer.addChild(applyBtn);
        } else if (displayFilterMode == 3) {
            var minXRow = new UIElement();
            minXRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4).alignItems(AlignItems.CENTER));
            var minXLabel = new Label();
            minXLabel.setText(Component.translatable("ebe.display.filter.min_x"));
            minXLabel.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(9).textShadow(false));
            minXLabel.layout(l -> l.width(40));
            minXRow.addChild(minXLabel);
            var minXField = new TextField();
            minXField.layout(l -> l.flex(1).height(18));
            minXField.setId("filterMinX");
            minXField.setText("0");
            minXRow.addChild(minXField);
            displayFilterContentContainer.addChild(minXRow);

            var maxXRow = new UIElement();
            maxXRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4).alignItems(AlignItems.CENTER));
            var maxXLabel = new Label();
            maxXLabel.setText(Component.translatable("ebe.display.filter.max_x"));
            maxXLabel.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(9).textShadow(false));
            maxXLabel.layout(l -> l.width(40));
            maxXRow.addChild(maxXLabel);
            var maxXField = new TextField();
            maxXField.layout(l -> l.flex(1).height(18));
            maxXField.setId("filterMaxX");
            maxXField.setText("0");
            maxXRow.addChild(maxXField);
            displayFilterContentContainer.addChild(maxXRow);

            var minZRow = new UIElement();
            minZRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4).alignItems(AlignItems.CENTER));
            var minZLabel = new Label();
            minZLabel.setText(Component.translatable("ebe.display.filter.min_z"));
            minZLabel.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(9).textShadow(false));
            minZLabel.layout(l -> l.width(40));
            minZRow.addChild(minZLabel);
            var minZField = new TextField();
            minZField.layout(l -> l.flex(1).height(18));
            minZField.setId("filterMinZ");
            minZField.setText("0");
            minZRow.addChild(minZField);
            displayFilterContentContainer.addChild(minZRow);

            var maxZRow = new UIElement();
            maxZRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4).alignItems(AlignItems.CENTER));
            var maxZLabel = new Label();
            maxZLabel.setText(Component.translatable("ebe.display.filter.max_z"));
            maxZLabel.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(9).textShadow(false));
            maxZLabel.layout(l -> l.width(40));
            maxZRow.addChild(maxZLabel);
            var maxZField = new TextField();
            maxZField.layout(l -> l.flex(1).height(18));
            maxZField.setId("filterMaxZ");
            maxZField.setText("0");
            maxZRow.addChild(maxZField);
            displayFilterContentContainer.addChild(maxZRow);

            var applyBtn = new Button();
            applyBtn.setText(Component.translatable("ebe.display.filter.apply"));
            applyBtn.layout(l -> l.widthPercent(100).height(22));
            applyBtn.style(s -> s.background(Sprites.RECT_RD));
            applyBtn.setOnClick(e -> {
                applyDisplayFilter();
                updateCurrentModeLabel();
            });
            displayFilterContentContainer.addChild(applyBtn);
        } else if (displayFilterMode == 4) {
            var applyBtn = new Button();
            applyBtn.setText(Component.translatable("ebe.display.filter.apply"));
            applyBtn.layout(l -> l.widthPercent(100).height(22));
            applyBtn.style(s -> s.background(Sprites.RECT_RD));
            applyBtn.setOnClick(e -> {
                applyDisplayFilter();
                updateCurrentModeLabel();
            });
            displayFilterContentContainer.addChild(applyBtn);
        }
    }

    private static void applyDisplayFilter() {
        var filter = state.getDisplayFilter();
        filter.setMode(switch (displayFilterMode) {
            case 0 -> DisplayFilter.FilterMode.ALL;
            case 1 -> DisplayFilter.FilterMode.BY_BLOCK_TYPE;
            case 2 -> DisplayFilter.FilterMode.BY_Y_LAYER;
            case 3 -> DisplayFilter.FilterMode.BY_ROW_COLUMN;
            case 4 -> DisplayFilter.FilterMode.BY_SELECTION;
            default -> DisplayFilter.FilterMode.ALL;
        });
        if (displayFilterMode == 2) {
            int minY = getIntField(rightPanel, "filterMinY", 0);
            int maxY = getIntField(rightPanel, "filterMaxY", 255);
            filter.setYRange(minY, maxY);
        } else if (displayFilterMode == 3) {
            int minX = getIntField(rightPanel, "filterMinX", 0);
            int maxX = getIntField(rightPanel, "filterMaxX", 0);
            int minZ = getIntField(rightPanel, "filterMinZ", 0);
            int maxZ = getIntField(rightPanel, "filterMaxZ", 0);
            filter.setXZRange(minX, maxX, minZ, maxZ);
        } else if (displayFilterMode == 4) {
            filter.setSelectedPositions(selection.getAllPacked());
        }
        ViewportFactory.refreshFromModel(session.getModel());
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

        var nbt = session.getModel().getBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ());
        if (nbt == null && bs.hasBlockEntity()) {
            nbt = createDefaultBlockEntityNbt(bs, state.getCursorX(), state.getCursorY(), state.getCursorZ());
            if (nbt != null) {
                session.getModel().setBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ(), nbt);
            }
        }
        if (nbt != null) {
            var nbtLabel = new Label();
            nbtLabel.setText(Component.literal("NBT:"));
            nbtLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9));
            nbtLabel.layout(l -> l.marginTop(4));
            propertiesContainer.addChild(nbtLabel);

            addNbtTree(propertiesContainer, nbt, 0);
        } else if (bs.hasBlockEntity()) {
            var nbtLabel = new Label();
            nbtLabel.setText(Component.literal("NBT: (empty)"));
            nbtLabel.textStyle(ts -> ts.textColor(0xFF808080).fontSize(9));
            nbtLabel.layout(l -> l.marginTop(4));
            propertiesContainer.addChild(nbtLabel);

            var addNbtBtn = new Button();
            addNbtBtn.setText(Component.translatable("ebe.editor.nbt.create"));
            addNbtBtn.layout(l -> l.widthPercent(100).height(18));
            addNbtBtn.setOnClick(e -> {
                var tag = new net.minecraft.nbt.CompoundTag();
                session.getModel().setBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ(), tag);
                refreshPropertiesPanel();
            });
            propertiesContainer.addChild(addNbtBtn);
        }
    }

    private static void addNbtTree(UIElement parent, net.minecraft.nbt.CompoundTag tag, int depth) {
        int indent = depth * 8;
        for (var key : tag.getAllKeys()) {
            var value = tag.get(key);
            var row = new UIElement();
            row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                    .alignItems(AlignItems.CENTER).gapAll(2).paddingHorizontal(4).paddingVertical(1)
                    .marginLeft(indent));
            row.style(s -> s.background(Sprites.RECT_DARK));

            var keyLbl = new Label();
            keyLbl.setText(Component.literal(key + ":"));
            keyLbl.textStyle(ts -> ts.textColor(0xFF88CCFF).fontSize(8).textShadow(false));
            keyLbl.layout(l -> l.minWidth(30));
            row.addChild(keyLbl);

            if (value instanceof net.minecraft.nbt.CompoundTag compound) {
                var expandBtn = new Button();
                expandBtn.setText(Component.literal("{...}"));
                expandBtn.layout(l -> l.height(14));
                expandBtn.textStyle(ts -> ts.fontSize(8));
                var childContainer = new UIElement();
                childContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN));
                childContainer.setDisplay(false);
                expandBtn.setOnClick(e -> {
                    childContainer.setDisplay(!childContainer.isDisplayed());
                });
                row.addChild(expandBtn);
                parent.addChild(row);
                addNbtTree(childContainer, compound, depth + 1);
                parent.addChild(childContainer);
            } else if (value instanceof net.minecraft.nbt.ListTag list) {
                var listLbl = new Label();
                listLbl.setText(Component.literal("[" + list.size() + " items]"));
                listLbl.textStyle(ts -> ts.textColor(0xFFCC88FF).fontSize(8).textShadow(false));
                row.addChild(listLbl);
                parent.addChild(row);

                for (int i = 0; i < list.size(); i++) {
                    var item = list.get(i);
                    if (item instanceof net.minecraft.nbt.CompoundTag compoundItem) {
                        var itemRow = new UIElement();
                        itemRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                                .alignItems(AlignItems.CENTER).gapAll(2).paddingHorizontal(4)
                                .marginLeft((depth + 1) * 8));
                        var idxLbl = new Label();
                        idxLbl.setText(Component.literal("[" + i + "]:"));
                        idxLbl.textStyle(ts -> ts.textColor(0xFF88CCFF).fontSize(8).textShadow(false));
                        itemRow.addChild(idxLbl);
                        var braceLbl = new Label();
                        braceLbl.setText(Component.literal("{...}"));
                        braceLbl.textStyle(ts -> ts.textColor(0xFFCC88FF).fontSize(8).textShadow(false));
                        itemRow.addChild(braceLbl);
                        parent.addChild(itemRow);
                        addNbtTree(parent, compoundItem, depth + 2);
                    } else {
                        addNbtValueRow(parent, "[" + i + "]", item, (depth + 1) * 8);
                    }
                }
            } else {
                var valLbl = new Label();
                String valStr = nbtValueToString(value);
                valLbl.setText(Component.literal(valStr));
                valLbl.textStyle(ts -> ts.textColor(nbtValueColor(value)).fontSize(8).textShadow(false));
                valLbl.layout(l -> l.flex(1));
                row.addChild(valLbl);

                var editBtn = new UIElement();
                editBtn.layout(l -> l.width(14).height(14));
                var editIcon = new UIElement();
                editIcon.layout(l -> l.widthPercent(100).heightPercent(100));
                editIcon.style(s -> s.backgroundTexture(EditorIcons.PENCIL));
                editBtn.addChild(editIcon);
                registerTooltip(editBtn, Component.translatable("ebe.nbt.edit"));
                editBtn.addEventListener(UIEvents.MOUSE_DOWN, e2 -> {
                    if (e2.button != 0) return;
                    var valLbl2 = new TextField();
                    valLbl2.setText(nbtValueToString(value).replace("\"", ""));
                    valLbl2.layout(l -> l.flex(1).height(14));
                    valLbl2.addEventListener(UIEvents.KEY_DOWN, e3 -> {
                        if (e3.keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                            var text = valLbl2.getText();
                            net.minecraft.nbt.Tag newTag = parseNbtValue(text, value);
                            if (newTag != null) {
                                tag.put(key, newTag);
                                session.getModel().setBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ(), tag);
                            }
                            refreshPropertiesPanel();
                        } else if (e3.keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                            refreshPropertiesPanel();
                        }
                    });
                    var parentRow = editBtn.getParent();
                    if (parentRow != null) {
                        var children = new ArrayList<>(parentRow.getChildren());
                        for (int ci = 0; ci < children.size(); ci++) {
                            if (children.get(ci) == valLbl) {
                                parentRow.removeChild(valLbl);
                                parentRow.addChildAt(valLbl2, ci);
                                valLbl2.focus();
                                break;
                            }
                        }
                    }
                });
                row.addChild(editBtn);

                var delBtn = new Button();
                delBtn.setText(Component.literal("✕"));
                delBtn.layout(l -> l.width(14).height(14));
                delBtn.setOnClick(e -> {
                    tag.remove(key);
                    session.getModel().setBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ(), tag);
                    refreshPropertiesPanel();
                });
                row.addChild(delBtn);

                parent.addChild(row);
            }
        }

        var addFieldBtn = new Button();
        addFieldBtn.setText(Component.literal("+"));
        addFieldBtn.layout(l -> l.width(16).height(16).marginLeft(indent));
        addFieldBtn.setOnClick(e -> {
            addNbtField(tag);
        });
        parent.addChild(addFieldBtn);
    }

    private static void addNbtValueRow(UIElement parent, String key, net.minecraft.nbt.Tag value, int indent) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(2).paddingHorizontal(4).marginLeft(indent));
        var keyLbl = new Label();
        keyLbl.setText(Component.literal(key + ":"));
        keyLbl.textStyle(ts -> ts.textColor(0xFF88CCFF).fontSize(8).textShadow(false));
        row.addChild(keyLbl);
        var valLbl = new Label();
        valLbl.setText(Component.literal(nbtValueToString(value)));
        valLbl.textStyle(ts -> ts.textColor(nbtValueColor(value)).fontSize(8).textShadow(false));
        row.addChild(valLbl);
        parent.addChild(row);
    }

    private static String nbtValueToString(net.minecraft.nbt.Tag tag) {
        if (tag instanceof net.minecraft.nbt.StringTag s) return "\"" + s.getAsString() + "\"";
        if (tag instanceof net.minecraft.nbt.IntTag i) return String.valueOf(i.getAsInt());
        if (tag instanceof net.minecraft.nbt.LongTag l) return l.getAsLong() + "L";
        if (tag instanceof net.minecraft.nbt.FloatTag f) return f.getAsFloat() + "f";
        if (tag instanceof net.minecraft.nbt.DoubleTag d) return d.getAsDouble() + "d";
        if (tag instanceof net.minecraft.nbt.ByteTag b) return b.getAsByte() + "b";
        if (tag instanceof net.minecraft.nbt.ShortTag s) return s.getAsShort() + "s";
        if (tag instanceof net.minecraft.nbt.ByteArrayTag ba) return "byte[" + ba.size() + "]";
        if (tag instanceof net.minecraft.nbt.IntArrayTag ia) return "int[" + ia.size() + "]";
        if (tag instanceof net.minecraft.nbt.LongArrayTag la) return "long[" + la.size() + "]";
        return tag.toString();
    }

    private static int nbtValueColor(net.minecraft.nbt.Tag tag) {
        if (tag instanceof net.minecraft.nbt.StringTag) return 0xFF88FF88;
        if (tag instanceof net.minecraft.nbt.NumericTag) return 0xFFFFAA44;
        return 0xFFCCCCCC;
    }

    private static void editNbtValue(net.minecraft.nbt.CompoundTag parent, String key, net.minecraft.nbt.Tag oldValue) {
        var input = new com.lowdragmc.lowdraglib2.gui.ui.elements.TextField();
        input.setText(nbtValueToString(oldValue).replace("\"", ""));
        input.layout(l -> l.widthPercent(100).height(18));
        input.setTextResponder(text -> {
            net.minecraft.nbt.Tag newTag = parseNbtValue(text, oldValue);
            if (newTag != null) {
                parent.put(key, newTag);
                session.getModel().setBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ(), parent);
                refreshPropertiesPanel();
            }
        });
    }

    private static net.minecraft.nbt.Tag parseNbtValue(String text, net.minecraft.nbt.Tag oldTag) {
        try {
            if (oldTag instanceof net.minecraft.nbt.StringTag) return net.minecraft.nbt.StringTag.valueOf(text);
            if (oldTag instanceof net.minecraft.nbt.IntTag) return net.minecraft.nbt.IntTag.valueOf(Integer.parseInt(text));
            if (oldTag instanceof net.minecraft.nbt.LongTag) return net.minecraft.nbt.LongTag.valueOf(Long.parseLong(text.endsWith("L") ? text.substring(0, text.length() - 1) : text));
            if (oldTag instanceof net.minecraft.nbt.FloatTag) return net.minecraft.nbt.FloatTag.valueOf(Float.parseFloat(text.endsWith("f") ? text.substring(0, text.length() - 1) : text));
            if (oldTag instanceof net.minecraft.nbt.DoubleTag) return net.minecraft.nbt.DoubleTag.valueOf(Double.parseDouble(text.endsWith("d") ? text.substring(0, text.length() - 1) : text));
            if (oldTag instanceof net.minecraft.nbt.ByteTag) return net.minecraft.nbt.ByteTag.valueOf(Byte.parseByte(text.endsWith("b") ? text.substring(0, text.length() - 1) : text));
            if (oldTag instanceof net.minecraft.nbt.ShortTag) return net.minecraft.nbt.ShortTag.valueOf(Short.parseShort(text.endsWith("s") ? text.substring(0, text.length() - 1) : text));
            return net.minecraft.nbt.StringTag.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void addNbtField(net.minecraft.nbt.CompoundTag parent) {
        String baseName = "new_field";
        String name = baseName;
        int suffix = 1;
        while (parent.contains(name)) {
            name = baseName + "_" + suffix++;
        }
        parent.putString(name, "");
        session.getModel().setBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ(), parent);
        refreshPropertiesPanel();
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

    private static net.minecraft.nbt.CompoundTag createDefaultBlockEntityNbt(BlockState bs, int x, int y, int z) {
        var block = bs.getBlock();
        if (!(block instanceof net.minecraft.world.level.block.EntityBlock eb)) return null;
        var be = eb.newBlockEntity(new net.minecraft.core.BlockPos(x, y, z), bs);
        if (be == null) return null;
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        try {
            return be.saveWithId(level.registryAccess());
        } catch (Exception e) {
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("id", net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            return tag;
        }
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

    private static UIElement buildDivider(boolean isLeft) {
        var divider = new UIElement();
        divider.layout(l -> l.width(4).heightPercent(100));
        divider.style(s -> s.background(Sprites.RECT_DARK));
        divider.setId(isLeft ? "leftDivider" : "rightDivider");

        divider.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                activeDivider = isLeft ? 1 : 2;
                dividerDragStartX = (int) e.x;
                e.stopPropagation();
            }
        });

        divider.addEventListener(UIEvents.MOUSE_ENTER, e -> {
            divider.style(s -> s.background(Sprites.RECT_RD_DARK));
        });
        divider.addEventListener(UIEvents.MOUSE_LEAVE, e -> {
            if (activeDivider == 0 || (isLeft && activeDivider != 1) || (!isLeft && activeDivider != 2)) {
                divider.style(s -> s.background(Sprites.RECT_DARK));
            }
        });

        return divider;
    }

    private static void toggleLeftPanel() {
        leftPanelVisible = !leftPanelVisible;
        leftPanel.setDisplay(leftPanelVisible);
        leftDivider.setDisplay(leftPanelVisible);
        leftCollapseBtn.setText(Component.literal(leftPanelVisible ? "◀" : "▶"));
    }

    private static void toggleRightPanel() {
        rightPanelVisible = !rightPanelVisible;
        rightPanel.setDisplay(rightPanelVisible);
        rightDivider.setDisplay(rightPanelVisible);
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
                .minWidth(180).maxWidth(260)
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

    private static UIElement buildReplacePanel() {
        var panel = new UIElement();
        panel.setId("replacePanel");
        panel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(40).top(40)
                .minWidth(240).maxWidth(320)
                .flexDirection(FlexDirection.COLUMN).gapAll(4)
                .paddingAll(6));
        panel.style(s -> s.background(Sprites.BORDER).zIndex(150));

        var titleRow = new UIElement();
        titleRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));
        var title = new Label();
        title.setText(Component.translatable("ebe.editor.replace.title"));
        title.textStyle(ts -> ts.textColor(0xFFFFD700).textShadow(false).fontSize(10));
        title.layout(l -> l.flex(1));
        titleRow.addChild(title);
        var closeBtn = new UIElement();
        closeBtn.layout(l -> l.width(16).height(16));
        var closeIcon = new UIElement();
        closeIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        closeIcon.style(s -> s.backgroundTexture(EditorIcons.CLOSE));
        closeBtn.addChild(closeIcon);
        closeBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                replacePanelVisible = false;
                panel.setDisplay(false);
            }
        });
        titleRow.addChild(closeBtn);
        panel.addChild(titleRow);

        var tabView = new TabView();
        tabView.layout(l -> l.flexDirection(FlexDirection.COLUMN).widthPercent(100).flex(1));
        tabView.setId("replaceTabView");
        {
            var h = tabView.tabHeaderContainer;
            var c = tabView.tabContentContainer;
            tabView.removeChild(h);
            tabView.removeChild(c);
            tabView.addChild(h);
            tabView.addChild(c);
        }

        var singleTab = new Tab();
        singleTab.setText(Component.translatable("ebe.editor.replace.single"));
        tabView.addTab(singleTab, buildReplaceSingleTab());

        var batchTab = new Tab();
        batchTab.setText(Component.translatable("ebe.editor.replace.batch"));
        tabView.addTab(batchTab, buildReplaceBatchTab());

        panel.addChild(tabView);
        return panel;
    }

    private static UIElement buildReplaceSingleTab() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.replace.single.hint"));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        content.addChild(hint);

        var targetSection = new UIElement();
        targetSection.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        var targetTitle = new Label();
        targetTitle.setText(Component.translatable("ebe.replace.target_block"));
        targetTitle.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
        targetSection.addChild(targetTitle);
        var targetRow = new UIElement();
        targetRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));
        var targetIcon = new UIElement();
        targetIcon.layout(l -> l.width(24).height(24));
        targetIcon.setId("replaceSingleTargetIcon");
        targetIcon.style(s -> s.background(Sprites.RECT_DARK));
        targetRow.addChild(targetIcon);
        var targetName = new Label();
        targetName.setId("replaceSingleTargetName");
        targetName.setText(Component.literal("-"));
        targetName.textStyle(ts -> ts.textColor(0xFFFFFFFF).textShadow(false).fontSize(9));
        targetName.layout(l -> l.flex(1));
        targetRow.addChild(targetName);
        var pickBtn = new UIElement();
        pickBtn.layout(l -> l.width(20).height(20));
        var pickIcon = new UIElement();
        pickIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        pickIcon.style(s -> s.backgroundTexture(EditorIcons.SEARCH));
        pickBtn.addChild(pickIcon);
        registerTooltip(pickBtn, Component.translatable("ebe.replace.browse_block"));
        pickBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                BlockPaletteUI.openWithCallback(rootElement, bs -> {
                    replaceTargetState = bs;
                    updateReplaceSingleDisplay();
                });
            }
        });
        targetRow.addChild(pickBtn);
        var grabBtn = new UIElement();
        grabBtn.layout(l -> l.width(20).height(20));
        var grabIcon = new UIElement();
        grabIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        grabIcon.style(s -> s.backgroundTexture(EditorIcons.PENCIL));
        grabBtn.addChild(grabIcon);
        registerTooltip(grabBtn, Component.translatable("ebe.replace.use_grabbed"));
        grabBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                var active = state.getActiveBlockState();
                if (active != null) {
                    replaceTargetState = active;
                    updateReplaceSingleDisplay();
                }
            }
        });
        targetRow.addChild(grabBtn);
        targetSection.addChild(targetRow);
        content.addChild(targetSection);

        return content;
    }

    private static UIElement buildReplaceBatchTab() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var modeTabView = new TabView();
        modeTabView.layout(l -> l.flexDirection(FlexDirection.COLUMN).widthPercent(100).flex(1));
        modeTabView.setId("replaceModeTabView");
        modeTabView.tabScroller(s -> s.scrollerStyle(style -> style.horizontalScrollDisplay(ScrollDisplay.ALWAYS)));
        {
            var h = modeTabView.tabHeaderContainer;
            var c = modeTabView.tabContentContainer;
            modeTabView.removeChild(h);
            modeTabView.removeChild(c);
            modeTabView.addChild(h);
            modeTabView.addChild(c);
        }

        var sameTypeTab = new Tab();
        sameTypeTab.setText(Component.translatable("ebe.replace.mode.same_type"));
        modeTabView.addTab(sameTypeTab, buildSameTypeContent());

        var byCondTab = new Tab();
        byCondTab.setText(Component.translatable("ebe.replace.mode.by_condition"));
        modeTabView.addTab(byCondTab, buildByConditionContent());

        var customTab = new Tab();
        customTab.setText(Component.translatable("ebe.replace.mode.custom"));
        modeTabView.addTab(customTab, buildCustomReplaceContent());

        content.addChild(modeTabView);
        return content;
    }

    private static UIElement buildSameTypeContent() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.replace.same_type.hint"));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        content.addChild(hint);

        var sourceSection = buildSourceBlockSection();
        content.addChild(sourceSection);

        var targetSection = buildTargetBlockSection("replaceBatchTargetIcon", "replaceBatchTargetName");
        content.addChild(targetSection);

        var execBtn = new Button();
        execBtn.setText(Component.translatable("ebe.editor.replace.execute"));
        execBtn.layout(l -> l.widthPercent(100).height(22));
        execBtn.setOnClick(e -> executeSameTypeReplace());
        content.addChild(execBtn);

        return content;
    }

    private static UIElement buildByConditionContent() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.replace.condition.hint"));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        content.addChild(hint);

        var sourceSection = buildSourceBlockSection();
        content.addChild(sourceSection);

        var targetSection = buildTargetBlockSection("replaceCondTargetIcon", "replaceCondTargetName");
        content.addChild(targetSection);

        var condTypeRow = new UIElement();
        condTypeRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));
        String[] condTypes = {"property", "color", "material", "tag", "nbt", "custom"};
        for (String type : condTypes) {
            var btn = new Button();
            btn.setText(Component.translatable("ebe.replace.condition.type." + type));
            btn.setId("condTypeBtn_" + type);
            btn.layout(l -> l.flex(1).height(18));
            btn.setOnClick(e -> {
                conditionType = type;
                updateConditionTypeHighlight();
                updateConditionContent();
            });
            condTypeRow.addChild(btn);
        }
        content.addChild(condTypeRow);

        conditionContentContainer = new UIElement();
        conditionContentContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));
        conditionContentContainer.setId("conditionContentContainer");
        content.addChild(conditionContentContainer);

        var execBtn = new Button();
        execBtn.setText(Component.translatable("ebe.editor.replace.execute"));
        execBtn.layout(l -> l.widthPercent(100).height(22));
        execBtn.setOnClick(e -> executeByConditionReplace());
        content.addChild(execBtn);

        updateConditionContent();
        updateConditionTypeHighlight();
        return content;
    }

    private static void updateConditionTypeHighlight() {
        String[] condTypes = {"property", "color", "material", "tag", "nbt", "custom"};
        for (String type : condTypes) {
            var btn = UIUtils.findById(replacePanel, "condTypeBtn_" + type);
            if (btn instanceof Button b) {
                b.style(s -> s.background(type.equals(conditionType) ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            }
        }
    }

    private static void updateConditionContent() {
        if (conditionContentContainer == null) return;
        conditionContentContainer.clearAllChildren();

        switch (conditionType) {
            case "property" -> {
                var condLabel = new Label();
                condLabel.setText(Component.translatable("ebe.replace.condition.property"));
                condLabel.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
                conditionContentContainer.addChild(condLabel);

                var propRow = new UIElement();
                propRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .alignItems(AlignItems.CENTER).gapAll(4));
                var propNameField = new TextField();
                propNameField.setId("replaceCondPropName");
                propNameField.setText("waterlogged");
                propNameField.layout(l -> l.flex(1).height(18));
                propNameField.textFieldStyle(s -> s.placeholder(Component.translatable("ebe.replace.condition.prop_name")));
                propRow.addChild(propNameField);
                conditionContentContainer.addChild(propRow);

                var valRow = new UIElement();
                valRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .alignItems(AlignItems.CENTER).gapAll(4));
                var valLabel = new Label();
                valLabel.setText(Component.literal("→"));
                valLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9));
                valLabel.layout(l -> l.width(12));
                valRow.addChild(valLabel);
                var propValField = new TextField();
                propValField.setId("replaceCondPropValue");
                propValField.setText("true");
                propValField.layout(l -> l.flex(1).height(18));
                propValField.textFieldStyle(s -> s.placeholder(Component.translatable("ebe.replace.condition.prop_value")));
                valRow.addChild(propValField);
                conditionContentContainer.addChild(valRow);
            }
            case "color" -> {
                var label = new Label();
                label.setText(Component.translatable("ebe.replace.condition.color"));
                label.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
                conditionContentContainer.addChild(label);

                var field = new TextField();
                field.setId("replaceCondColorId");
                field.setText("0");
                field.layout(l -> l.widthPercent(100).height(18));
                conditionContentContainer.addChild(field);
            }
            case "material" -> {
                var label = new Label();
                label.setText(Component.translatable("ebe.replace.condition.material_type"));
                label.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
                conditionContentContainer.addChild(label);

                var row = new UIElement();
                row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(2));
                String[] materials = {"wood", "stone", "metal", "glass", "wool", "concrete", "terracotta"};
                for (String mat : materials) {
                    var btn = new Button();
                    btn.setText(Component.translatable("ebe.replace.condition.material." + mat));
                    btn.setId("condMaterialBtn_" + mat);
                    btn.layout(l -> l.height(18).paddingHorizontal(4));
                    btn.setOnClick(e -> {
                        conditionMaterialType = mat;
                        updateMaterialTypeHighlight();
                    });
                    row.addChild(btn);
                }
                conditionContentContainer.addChild(row);
                updateMaterialTypeHighlight();
            }
            case "tag" -> {
                var label = new Label();
                label.setText(Component.translatable("ebe.replace.condition.tag_name"));
                label.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
                conditionContentContainer.addChild(label);

                var field = new TextField();
                field.setId("replaceCondTagName");
                field.setText("minecraft:planks");
                field.layout(l -> l.widthPercent(100).height(18));
                conditionContentContainer.addChild(field);
            }
            case "nbt" -> {
                var fieldLabel = new Label();
                fieldLabel.setText(Component.translatable("ebe.replace.condition.nbt_field"));
                fieldLabel.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
                conditionContentContainer.addChild(fieldLabel);

                var nbtFieldInput = new TextField();
                nbtFieldInput.setId("replaceCondNbtField");
                nbtFieldInput.setText("");
                nbtFieldInput.layout(l -> l.widthPercent(100).height(18));
                conditionContentContainer.addChild(nbtFieldInput);

                var valLabel = new Label();
                valLabel.setText(Component.translatable("ebe.replace.condition.nbt_value"));
                valLabel.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
                conditionContentContainer.addChild(valLabel);

                var nbtValInput = new TextField();
                nbtValInput.setId("replaceCondNbtValue");
                nbtValInput.setText("");
                nbtValInput.layout(l -> l.widthPercent(100).height(18));
                conditionContentContainer.addChild(nbtValInput);
            }
            case "custom" -> {
                var label = new Label();
                label.setText(Component.translatable("ebe.replace.condition.custom_rule"));
                label.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
                conditionContentContainer.addChild(label);

                var field = new TextField();
                field.setId("replaceCondCustomRule");
                field.setText("");
                field.layout(l -> l.widthPercent(100).height(18));
                conditionContentContainer.addChild(field);
            }
        }
    }

    private static void updateMaterialTypeHighlight() {
        String[] materials = {"wood", "stone", "metal", "glass", "wool", "concrete", "terracotta"};
        for (String mat : materials) {
            var btn = UIUtils.findById(replacePanel, "condMaterialBtn_" + mat);
            if (btn instanceof Button b) {
                b.style(s -> s.background(mat.equals(conditionMaterialType) ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            }
        }
    }

    private static UIElement buildCustomReplaceContent() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.replace.custom.hint"));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        content.addChild(hint);

        var targetSection = buildTargetBlockSection("replaceCustomTargetIcon", "replaceCustomTargetName");
        content.addChild(targetSection);

        var execBtn = new Button();
        execBtn.setText(Component.translatable("ebe.editor.replace.execute"));
        execBtn.layout(l -> l.widthPercent(100).height(22));
        execBtn.setOnClick(e -> executeCustomReplace());
        content.addChild(execBtn);

        return content;
    }

    private static UIElement buildSourceBlockSection() {
        var section = new UIElement();
        section.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        var title = new Label();
        title.setText(Component.translatable("ebe.replace.source_block"));
        title.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
        section.addChild(title);
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));
        var icon = new UIElement();
        icon.layout(l -> l.width(24).height(24));
        icon.setId("replaceBatchSourceIcon");
        icon.style(s -> s.background(Sprites.RECT_DARK));
        row.addChild(icon);
        var name = new Label();
        name.setId("replaceBatchSourceName");
        name.setText(Component.literal("-"));
        name.textStyle(ts -> ts.textColor(0xFFFFFFFF).textShadow(false).fontSize(9));
        name.layout(l -> l.flex(1));
        row.addChild(name);
        var pickBtn = new UIElement();
        pickBtn.layout(l -> l.width(20).height(20));
        var pickIcon = new UIElement();
        pickIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        pickIcon.style(s -> s.backgroundTexture(EditorIcons.SEARCH));
        pickBtn.addChild(pickIcon);
        registerTooltip(pickBtn, Component.translatable("ebe.replace.pick_from_viewport"));
        pickBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) replacePickingSource = true;
        });
        row.addChild(pickBtn);
        var useSelBtn = new UIElement();
        useSelBtn.layout(l -> l.width(20).height(20));
        var useSelIcon = new UIElement();
        useSelIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        useSelIcon.style(s -> s.backgroundTexture(EditorIcons.SELECT));
        useSelBtn.addChild(useSelIcon);
        registerTooltip(useSelBtn, Component.translatable("ebe.replace.use_selected"));
        useSelBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                var sel = getSelection();
                if (sel.size() == 1) {
                    var packed = sel.getAllPacked().iterator().next();
                    int bx = com.l1ght.ebe.editor.selection.SelectionManager.unpackX(packed);
                    int by = com.l1ght.ebe.editor.selection.SelectionManager.unpackY(packed);
                    int bz = com.l1ght.ebe.editor.selection.SelectionManager.unpackZ(packed);
                    var bsObj = session.getModel().getBlockAt(bx, by, bz);
                    var bsState = ViewportFactory.resolveBlockStatePublic(bsObj);
                    replaceSourceBlock = bsState.getBlock();
                    updateReplaceBatchSourceDisplay();
                }
            }
        });
        row.addChild(useSelBtn);
        section.addChild(row);
        return section;
    }

    private static UIElement buildTargetBlockSection(String iconId, String nameId) {
        var section = new UIElement();
        section.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        var title = new Label();
        title.setText(Component.translatable("ebe.replace.target_block"));
        title.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
        section.addChild(title);
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));
        var icon = new UIElement();
        icon.layout(l -> l.width(24).height(24));
        icon.setId(iconId);
        icon.style(s -> s.background(Sprites.RECT_DARK));
        row.addChild(icon);
        var name = new Label();
        name.setId(nameId);
        name.setText(Component.literal("-"));
        name.textStyle(ts -> ts.textColor(0xFFFFFFFF).textShadow(false).fontSize(9));
        name.layout(l -> l.flex(1));
        row.addChild(name);
        var browseBtn = new UIElement();
        browseBtn.layout(l -> l.width(20).height(20));
        var browseIcon = new UIElement();
        browseIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        browseIcon.style(s -> s.backgroundTexture(EditorIcons.SEARCH));
        browseBtn.addChild(browseIcon);
        registerTooltip(browseBtn, Component.translatable("ebe.replace.browse_block"));
        browseBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                BlockPaletteUI.openWithCallback(rootElement, bs -> {
                    replaceTargetState = bs;
                    updateTargetBlockDisplay(iconId, nameId);
                });
            }
        });
        row.addChild(browseBtn);
        var grabBtn = new UIElement();
        grabBtn.layout(l -> l.width(20).height(20));
        var grabIcon = new UIElement();
        grabIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        grabIcon.style(s -> s.backgroundTexture(EditorIcons.PENCIL));
        grabBtn.addChild(grabIcon);
        registerTooltip(grabBtn, Component.translatable("ebe.replace.use_grabbed"));
        grabBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                var active = state.getActiveBlockState();
                if (active != null) {
                    replaceTargetState = active;
                    updateTargetBlockDisplay(iconId, nameId);
                }
            }
        });
        row.addChild(grabBtn);
        section.addChild(row);
        return section;
    }

    private static void updateTargetBlockDisplay(String iconId, String nameId) {
        var nameEl = UIUtils.findById(replacePanel, nameId);
        if (nameEl instanceof Label l) {
            l.setText(replaceTargetState != null ? replaceTargetState.getBlock().getName() : Component.literal("-"));
        }
        var iconEl = UIUtils.findById(replacePanel, iconId);
        if (iconEl != null && replaceTargetState != null) {
            iconEl.style(s -> s.backgroundTexture(new ItemStackTexture(replaceTargetState.getBlock().asItem())));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyPropertyFromString(BlockState state, net.minecraft.world.level.block.state.properties.Property<T> prop, String value) {
        for (var v : prop.getPossibleValues()) {
            if (v.toString().equals(value)) {
                return state.setValue(prop, v);
            }
        }
        return state;
    }

    private static int replaceBatchSubMode = 0;
    private static net.minecraft.world.level.block.Block replaceSourceBlock = null;
    private static BlockState replaceTargetState = null;
    private static boolean replacePickingSource = false;

    private static final List<RandomFillEntry> randomFillEntries = new ArrayList<>();
    private static UIElement randomFillListContainer;
    private static String conditionType = "property";
    private static UIElement conditionContentContainer;
    private static String conditionMaterialType = "wood";

    private static class RandomFillEntry {
        BlockState blockState;
        int weight;
        RandomFillEntry(BlockState blockState, int weight) {
            this.blockState = blockState;
            this.weight = weight;
        }
    }

    private static UIElement buildFillPanel() {
        var panel = new UIElement();
        panel.setId("fillPanel");
        panel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(40).top(40)
                .minWidth(220).maxWidth(300)
                .flexDirection(FlexDirection.COLUMN).gapAll(4)
                .paddingAll(6));
        panel.style(s -> s.background(Sprites.BORDER).zIndex(150));

        var titleRow = new UIElement();
        titleRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));
        var title = new Label();
        title.setText(Component.translatable("ebe.editor.fill.title"));
        title.textStyle(ts -> ts.textColor(0xFFFFD700).textShadow(false).fontSize(10));
        title.layout(l -> l.flex(1));
        titleRow.addChild(title);
        var closeBtn = new UIElement();
        closeBtn.layout(l -> l.width(16).height(16));
        var closeIcon = new UIElement();
        closeIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        closeIcon.style(s -> s.backgroundTexture(EditorIcons.CLOSE));
        closeBtn.addChild(closeIcon);
        closeBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                fillPanelVisible = false;
                panel.setDisplay(false);
            }
        });
        titleRow.addChild(closeBtn);
        panel.addChild(titleRow);

        var tabView = new TabView();
        tabView.layout(l -> l.flexDirection(FlexDirection.COLUMN).widthPercent(100).flex(1));
        tabView.setId("fillTabView");
        {
            var h = tabView.tabHeaderContainer;
            var c = tabView.tabContentContainer;
            tabView.removeChild(h);
            tabView.removeChild(c);
            tabView.addChild(h);
            tabView.addChild(c);
        }

        var fillTab = new Tab();
        fillTab.setText(Component.translatable("ebe.fill.tab.fill"));
        tabView.addTab(fillTab, buildFillFillTab());

        var translateTab = new Tab();
        translateTab.setText(Component.translatable("ebe.fill.tab.translate"));
        tabView.addTab(translateTab, buildFillTranslateTab());

        var rotateTab = new Tab();
        rotateTab.setText(Component.translatable("ebe.fill.tab.rotate"));
        tabView.addTab(rotateTab, buildFillRotateTab());

        var randomFillTab = new Tab();
        randomFillTab.setText(Component.translatable("ebe.fill.tab.random"));
        tabView.addTab(randomFillTab, buildRandomFillTab());

        var mirrorTab = new Tab();
        mirrorTab.setText(Component.translatable("ebe.editor.mirror"));
        tabView.addTab(mirrorTab, buildFillMirrorTab());

        panel.addChild(tabView);
        return panel;
    }

    private static UIElement buildFillFillTab() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.fill.hint"));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        content.addChild(hint);

        var blockSection = new UIElement();
        blockSection.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        var blockTitle = new Label();
        blockTitle.setText(Component.translatable("ebe.fill.target_block"));
        blockTitle.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
        blockSection.addChild(blockTitle);
        var blockRow = new UIElement();
        blockRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));
        var blockIcon = new UIElement();
        blockIcon.layout(l -> l.width(24).height(24));
        blockIcon.setId("fillBlockIcon");
        blockIcon.style(s -> s.background(Sprites.RECT_DARK));
        blockRow.addChild(blockIcon);
        var blockLabel = new Label();
        blockLabel.setId("fillBlockLabel");
        blockLabel.setText(Component.literal("-"));
        blockLabel.textStyle(ts -> ts.textColor(0xFFFFFFFF).textShadow(false).fontSize(9));
        blockLabel.layout(l -> l.flex(1));
        blockRow.addChild(blockLabel);
        var browseBtn = new UIElement();
        browseBtn.layout(l -> l.width(20).height(20));
        var browseIcon = new UIElement();
        browseIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        browseIcon.style(s -> s.backgroundTexture(EditorIcons.SEARCH));
        browseBtn.addChild(browseIcon);
        registerTooltip(browseBtn, Component.translatable("ebe.replace.browse_block"));
        browseBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                BlockPaletteUI.openWithCallback(rootElement, bs -> {
                    fillBlockState = bs;
                    updateFillBlockDisplay();
                });
            }
        });
        blockRow.addChild(browseBtn);
        var grabBtn = new UIElement();
        grabBtn.layout(l -> l.width(20).height(20));
        var grabIcon = new UIElement();
        grabIcon.layout(l -> l.widthPercent(100).heightPercent(100));
        grabIcon.style(s -> s.backgroundTexture(EditorIcons.PENCIL));
        grabBtn.addChild(grabIcon);
        registerTooltip(grabBtn, Component.translatable("ebe.replace.use_grabbed"));
        grabBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                var active = state.getActiveBlockState();
                if (active != null) {
                    fillBlockState = active;
                    updateFillBlockDisplay();
                }
            }
        });
        blockRow.addChild(grabBtn);
        blockSection.addChild(blockRow);
        content.addChild(blockSection);

        var fillBtn = new Button();
        fillBtn.setText(Component.translatable("ebe.editor.fill.execute_fill"));
        fillBtn.layout(l -> l.widthPercent(100).height(22));
        fillBtn.setOnClick(e -> executeFill());
        content.addChild(fillBtn);

        return content;
    }

    private static UIElement buildFillTranslateTab() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.translate.hint"));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        content.addChild(hint);

        var dxRow = new UIElement();
        dxRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));
        var dxLabel = new Label();
        dxLabel.setText(Component.literal("X:"));
        dxLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9));
        dxLabel.layout(l -> l.width(16));
        dxRow.addChild(dxLabel);
        var dxField = new TextField();
        dxField.setText("0");
        dxField.setId("fillDxField");
        dxField.layout(l -> l.flex(1).height(18));
        dxRow.addChild(dxField);
        content.addChild(dxRow);

        var dyRow = new UIElement();
        dyRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));
        var dyLabel = new Label();
        dyLabel.setText(Component.literal("Y:"));
        dyLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9));
        dyLabel.layout(l -> l.width(16));
        dyRow.addChild(dyLabel);
        var dyField = new TextField();
        dyField.setText("0");
        dyField.setId("fillDyField");
        dyField.layout(l -> l.flex(1).height(18));
        dyRow.addChild(dyField);
        content.addChild(dyRow);

        var dzRow = new UIElement();
        dzRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));
        var dzLabel = new Label();
        dzLabel.setText(Component.literal("Z:"));
        dzLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9));
        dzLabel.layout(l -> l.width(16));
        dzRow.addChild(dzLabel);
        var dzField = new TextField();
        dzField.setText("0");
        dzField.setId("fillDzField");
        dzField.layout(l -> l.flex(1).height(18));
        dzRow.addChild(dzField);
        content.addChild(dzRow);

        var translateBtn = new Button();
        translateBtn.setText(Component.translatable("ebe.editor.fill.execute_translate"));
        translateBtn.layout(l -> l.widthPercent(100).height(22));
        translateBtn.setOnClick(e -> executeTranslate());
        content.addChild(translateBtn);

        return content;
    }

    private static UIElement buildFillRotateTab() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.rotate.hint"));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        content.addChild(hint);

        var axisLabel = new Label();
        axisLabel.setText(Component.translatable("ebe.rotate.axis"));
        axisLabel.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
        content.addChild(axisLabel);

        var axisRow = new UIElement();
        axisRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));
        String[] axes = {"X", "Y", "Z"};
        String[] axisIds = {"rotateAxisX", "rotateAxisY", "rotateAxisZ"};
        for (int i = 0; i < axes.length; i++) {
            final int ai = i;
            var btn = new Button();
            btn.setText(Component.literal(axes[i]));
            btn.setId(axisIds[i]);
            btn.layout(l -> l.flex(1).height(18));
            btn.setOnClick(e -> {
                rotateAxis = ai;
                updateRotateAxisHighlight();
            });
            axisRow.addChild(btn);
        }
        content.addChild(axisRow);

        var angleLabel = new Label();
        angleLabel.setText(Component.translatable("ebe.rotate.angle"));
        angleLabel.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
        content.addChild(angleLabel);

        var angleRow = new UIElement();
        angleRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));
        int[] angles = {90, 180, 270};
        for (int angle : angles) {
            var btn = new Button();
            btn.setText(Component.literal(angle + "°"));
            btn.layout(l -> l.flex(1).height(18));
            final int a = angle;
            btn.setOnClick(e -> rotateAngle = a);
            angleRow.addChild(btn);
        }
        content.addChild(angleRow);

        var rotateBtn = new Button();
        rotateBtn.setText(Component.translatable("ebe.editor.fill.execute_rotate"));
        rotateBtn.layout(l -> l.widthPercent(100).height(22));
        rotateBtn.setOnClick(e -> executeRotate());
        content.addChild(rotateBtn);

        return content;
    }

    private static int rotateAxis = 1;
    private static int rotateAngle = 90;

    private static void updateRotateAxisHighlight() {
        String[] axisIds = {"rotateAxisX", "rotateAxisY", "rotateAxisZ"};
        for (int i = 0; i < axisIds.length; i++) {
            final int idx = i;
            var btn = UIUtils.findById(fillPanel, axisIds[i]);
            if (btn instanceof Button b) {
                b.style(s -> s.background(idx == rotateAxis ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            }
        }
    }

    private static void executeRotate() {
        if (session == null) return;
        var selection = getSelection();
        if (selection.isEmpty()) return;
        var model = session.getModel();
        var history = getHistory();
        var snapshots = new ArrayList<Object[]>();

        double radians = Math.toRadians(rotateAngle);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        var positions = selection.getPositions();
        int cx = 0, cy = 0, cz = 0;
        for (var p : positions) { cx += (int) p[0]; cy += (int) p[1]; cz += (int) p[2]; }
        cx /= positions.size(); cy /= positions.size(); cz /= positions.size();

        var newPositions = new LinkedHashMap<long[], Object>();
        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            var oldState = model.getBlockAt(ox, oy, oz);
            var bs = ViewportFactory.resolveBlockStatePublic(oldState);

            int dx = ox - cx, dy = oy - cy, dz = oz - cz;
            int nx, ny, nz;
            if (rotateAxis == 0) {
                nx = ox; ny = (int) Math.round(dy * cos - dz * sin) + cy; nz = (int) Math.round(dy * sin + dz * cos) + cz;
            } else if (rotateAxis == 1) {
                nx = (int) Math.round(dx * cos + dz * sin) + cx; ny = oy; nz = (int) Math.round(-dx * sin + dz * cos) + cz;
            } else {
                nx = (int) Math.round(dx * cos - dy * sin) + cx; ny = (int) Math.round(dx * sin + dy * cos) + cy; nz = oz;
            }

            var rotation = switch (rotateAxis) {
                case 0 -> switch (rotateAngle) {
                    case 90 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
                    case 180 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
                    case 270 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
                    default -> net.minecraft.world.level.block.Rotation.NONE;
                };
                default -> switch (rotateAngle) {
                    case 90 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
                    case 180 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
                    case 270 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
                    default -> net.minecraft.world.level.block.Rotation.NONE;
                };
            };
            var rotatedBs = bs.rotate(rotation);
            newPositions.put(new long[]{nx, ny, nz}, rotatedBs);
        }

        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            model.setBlockAt(ox, oy, oz, "minecraft:air");
        }
        for (var entry : newPositions.entrySet()) {
            var pos = entry.getKey();
            model.setBlockAt((int) pos[0], (int) pos[1], (int) pos[2], entry.getValue());
        }

        selection.clear();
        for (var pos : newPositions.keySet()) {
            selection.add((int) pos[0], (int) pos[1], (int) pos[2]);
        }

        ViewportFactory.refreshFromModel(model);
    }

    private static UIElement buildFillMirrorTab() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.mirror.hint"));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        content.addChild(hint);

        var axisLabel = new Label();
        axisLabel.setText(Component.translatable("ebe.mirror.axis"));
        axisLabel.textStyle(ts -> ts.textColor(0xFFCCCC00).textShadow(false).fontSize(9));
        content.addChild(axisLabel);

        var axisRow = new UIElement();
        axisRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));
        String[] axes = {"X", "Y", "Z"};
        String[] axisIds = {"mirrorAxisX", "mirrorAxisY", "mirrorAxisZ"};
        for (int i = 0; i < axes.length; i++) {
            final int ai = i;
            var btn = new Button();
            btn.setText(Component.literal(axes[i]));
            btn.setId(axisIds[i]);
            btn.layout(l -> l.flex(1).height(18));
            btn.setOnClick(e -> {
                mirrorAxis = ai;
                updateMirrorAxisHighlight();
            });
            axisRow.addChild(btn);
        }
        content.addChild(axisRow);

        var mirrorBtn = new Button();
        mirrorBtn.setText(Component.translatable("ebe.editor.fill.execute_mirror"));
        mirrorBtn.layout(l -> l.widthPercent(100).height(22));
        mirrorBtn.setOnClick(e -> executeMirror());
        content.addChild(mirrorBtn);

        return content;
    }

    private static int mirrorAxis = 1;

    private static void updateMirrorAxisHighlight() {
        String[] axisIds = {"mirrorAxisX", "mirrorAxisY", "mirrorAxisZ"};
        for (int i = 0; i < axisIds.length; i++) {
            final int idx = i;
            var btn = UIUtils.findById(fillPanel, axisIds[i]);
            if (btn instanceof Button b) {
                b.style(s -> s.background(idx == mirrorAxis ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            }
        }
    }

    private static void executeMirror() {
        if (session == null) return;
        var selection = getSelection();
        if (selection.isEmpty()) return;
        clipboard.mirror(session.getModel(), selection, mirrorAxis, history);
        ViewportFactory.refreshFromModel(session.getModel());
        refreshMaterialList();
        refreshHistoryList();
    }

    private static BlockState fillBlockState = null;

    private static void updateFillBlockDisplay() {
        var label = UIUtils.findById(fillPanel, "fillBlockLabel");
        if (label instanceof Label l && fillBlockState != null) {
            l.setText(fillBlockState.getBlock().getName());
        }
        var icon = UIUtils.findById(fillPanel, "fillBlockIcon");
        if (icon != null && fillBlockState != null) {
            icon.style(s -> s.backgroundTexture(new ItemStackTexture(fillBlockState.getBlock().asItem())));
        }
    }

    private static UIElement buildRandomFillTab() {
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(6).paddingAll(4));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.fill.random.hint"));
        hint.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        hint.layout(l -> l.widthPercent(100));
        content.addChild(hint);

        randomFillListContainer = new UIElement();
        randomFillListContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        randomFillListContainer.setId("randomFillList");

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).maxHeight(120));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.addScrollViewChild(randomFillListContainer);
        content.addChild(scroller);

        var addBtn = new Button();
        addBtn.setText(Component.translatable("ebe.fill.random.add_block"));
        addBtn.layout(l -> l.widthPercent(100).height(22));
        addBtn.setOnClick(ev -> {
            BlockPaletteUI.openWithCallback(rootElement, bs -> {
                for (var entry : randomFillEntries) {
                    if (entry.blockState == bs) { entry.weight++; refreshRandomFillList(); return; }
                }
                randomFillEntries.add(new RandomFillEntry(bs, 1));
                refreshRandomFillList();
            });
        });
        content.addChild(addBtn);

        var execBtn = new Button();
        execBtn.setText(Component.translatable("ebe.fill.random.execute"));
        execBtn.layout(l -> l.widthPercent(100).height(22));
        execBtn.setOnClick(e -> executeRandomFill());
        content.addChild(execBtn);

        refreshRandomFillList();
        return content;
    }

    private static void refreshRandomFillList() {
        if (randomFillListContainer == null) return;
        randomFillListContainer.clearAllChildren();

        if (randomFillEntries.isEmpty()) {
            var empty = new Label();
            empty.setText(Component.translatable("ebe.fill.random.no_entries"));
            empty.textStyle(ts -> ts.textColor(0xFF707070).textShadow(false).fontSize(9));
            randomFillListContainer.addChild(empty);
            return;
        }

        for (int i = 0; i < randomFillEntries.size(); i++) {
            final int idx = i;
            var entry = randomFillEntries.get(i);
            var row = new UIElement();
            row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                    .alignItems(AlignItems.CENTER).gapAll(4));

            var icon = new UIElement();
            icon.layout(l -> l.width(18).height(18));
            icon.style(s -> s.backgroundTexture(new ItemStackTexture(entry.blockState.getBlock().asItem())));
            row.addChild(icon);

            var name = new Label();
            name.setText(entry.blockState.getBlock().getName());
            name.textStyle(ts -> ts.textColor(0xFFFFFFFF).textShadow(false).fontSize(9));
            name.layout(l -> l.flex(1));
            row.addChild(name);

            var weightLabel = new Label();
            weightLabel.setText(Component.translatable("ebe.fill.random.weight"));
            weightLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(8));
            row.addChild(weightLabel);

            var weightField = new TextField();
            weightField.setText(String.valueOf(entry.weight));
            weightField.layout(l -> l.width(36).height(16));
            weightField.setTextResponder(text -> {
                try {
                    randomFillEntries.get(idx).weight = Integer.parseInt(text);
                } catch (NumberFormatException ignored) {}
            });
            row.addChild(weightField);

            var delBtn = new Button();
            delBtn.setText(Component.literal("✕"));
            delBtn.layout(l -> l.width(16).height(16));
            delBtn.setOnClick(e -> {
                randomFillEntries.remove(idx);
                refreshRandomFillList();
            });
            row.addChild(delBtn);

            randomFillListContainer.addChild(row);
        }
    }

    private static void executeRandomFill() {
        if (session == null || randomFillEntries.isEmpty()) return;
        var selection = getSelection();
        if (selection.isEmpty()) return;
        var ratios = new LinkedHashMap<Object, Double>();
        for (var entry : randomFillEntries) {
            ratios.put(entry.blockState, (double) entry.weight);
        }
        clipboard.fillRandom(session.getModel(), selection, ratios, history);
        ViewportFactory.refreshFromModel(session.getModel());
        refreshMaterialList();
        refreshHistoryList();
    }

    private static void executeFill() {
        if (session == null || fillBlockState == null) return;
        var selection = getSelection();
        if (selection.isEmpty()) return;
        clipboard.fill(session.getModel(), selection, fillBlockState, history);
        ViewportFactory.refreshFromModel(session.getModel());
        refreshMaterialList();
        refreshHistoryList();
    }

    private static void executeTranslate() {
        if (session == null) return;
        var selection = getSelection();
        if (selection.isEmpty()) return;
        int dx = getIntField(fillPanel, "fillDxField", 0);
        int dy = getIntField(fillPanel, "fillDyField", 0);
        int dz = getIntField(fillPanel, "fillDzField", 0);
        if (dx == 0 && dy == 0 && dz == 0) return;
        clipboard.translateSelection(session.getModel(), selection, dx, dy, dz, history);
        ViewportFactory.refreshFromModel(session.getModel());
        refreshMaterialList();
        refreshHistoryList();
    }

    private static int getIntField(UIElement parent, String id, int defaultVal) {
        var elem = UIUtils.findById(parent, id);
        if (elem instanceof com.lowdragmc.lowdraglib2.gui.ui.elements.TextField tf) {
            try { return Integer.parseInt(tf.getText()); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private static void updateBatchModeHighlight() {
        String[] modeIds = {"replaceModeSameType", "replaceModeByCondition", "replaceModeCustom"};
        for (int i = 0; i < modeIds.length; i++) {
            final int idx = i;
            var btn = UIUtils.findById(replacePanel, modeIds[i]);
            if (btn instanceof Button b) {
                b.style(s -> s.background(idx == replaceBatchSubMode ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            }
        }
    }

    private static void updateBatchContentVisibility() {
    }

    private static void updateReplaceSingleDisplay() {
        var name = UIUtils.findById(replacePanel, "replaceSingleTargetName");
        if (name instanceof Label l) {
            if (replaceTargetState != null) {
                l.setText(replaceTargetState.getBlock().getName());
            } else {
                l.setText(Component.literal("-"));
            }
        }
        var icon = UIUtils.findById(replacePanel, "replaceSingleTargetIcon");
        if (icon != null && replaceTargetState != null) {
            icon.style(s -> s.backgroundTexture(new ItemStackTexture(replaceTargetState.getBlock().asItem())));
        }
    }

    private static void updateReplaceBatchSourceDisplay() {
        var name = UIUtils.findById(replacePanel, "replaceBatchSourceName");
        if (name instanceof Label l) {
            if (replaceSourceBlock != null) {
                l.setText(replaceSourceBlock.getName());
            } else {
                l.setText(Component.literal("-"));
            }
        }
        var icon = UIUtils.findById(replacePanel, "replaceBatchSourceIcon");
        if (icon != null && replaceSourceBlock != null) {
            icon.style(s -> s.backgroundTexture(new ItemStackTexture(replaceSourceBlock.asItem())));
        }
    }

    private static void updateReplaceBatchTargetDisplay() {
        var name = UIUtils.findById(replacePanel, "replaceBatchTargetName");
        if (name instanceof Label l) {
            if (replaceTargetState != null) {
                l.setText(replaceTargetState.getBlock().getName());
            } else {
                l.setText(Component.literal("-"));
            }
        }
        var icon = UIUtils.findById(replacePanel, "replaceBatchTargetIcon");
        if (icon != null && replaceTargetState != null) {
            icon.style(s -> s.backgroundTexture(new ItemStackTexture(replaceTargetState.getBlock().asItem())));
        }
    }

    private static void executeSameTypeReplace() {
        if (session == null || replaceSourceBlock == null || replaceTargetState == null) return;
        var model = session.getModel();
        var history = getHistory();
        var snapshots = new ArrayList<Object[]>();
        for (var region : model.getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = region.getBlocks().get(x, y, z);
                        var bs = ViewportFactory.resolveBlockStatePublic(obj);
                        if (!bs.isAir() && bs.getBlock() == replaceSourceBlock) {
                            int wx = x + region.getOffsetX();
                            int wy = y + region.getOffsetY();
                            int wz = z + region.getOffsetZ();
                            var old = model.getBlockAt(wx, wy, wz);
                            model.setBlockAt(wx, wy, wz, replaceTargetState);
                            snapshots.add(new Object[]{wx, wy, wz, old, replaceTargetState});
                        }
                    }
                }
            }
        }
        if (!snapshots.isEmpty()) {
            history.push(new com.l1ght.ebe.editor.history.HistoryEntry(
                    history.nextId(), com.l1ght.ebe.editor.history.HistoryActionType.REPLACE,
                    snapshots.toArray(new Object[0][]),
                    (int) snapshots.get(0)[0], (int) snapshots.get(0)[1], (int) snapshots.get(0)[2],
                    replaceSourceBlock.getDescriptionId(), snapshots.size()));
        }
        ViewportFactory.refreshFromModel(model);
    }

    private static void executeByConditionReplace() {
        if (session == null || replaceTargetState == null) return;
        if (conditionType.equals("property") && replaceSourceBlock == null) return;
        var model = session.getModel();
        var history = getHistory();

        String propName = "", propVal = "";
        if (conditionType.equals("property")) {
            var propNameEl = UIUtils.findById(replacePanel, "replaceCondPropName");
            var propValEl = UIUtils.findById(replacePanel, "replaceCondPropValue");
            propName = propNameEl instanceof TextField tf ? tf.getText() : "";
            propVal = propValEl instanceof TextField tf ? tf.getText() : "";
        }

        var snapshots = new ArrayList<Object[]>();
        for (var region : model.getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = region.getBlocks().get(x, y, z);
                        var bs = ViewportFactory.resolveBlockStatePublic(obj);
                        if (bs.isAir()) continue;

                        int wx = x + region.getOffsetX();
                        int wy = y + region.getOffsetY();
                        int wz = z + region.getOffsetZ();

                        boolean matches = switch (conditionType) {
                            case "property" -> matchesPropertyCondition(bs);
                            case "color" -> matchesColorCondition(bs);
                            case "material" -> matchesMaterialCondition(bs);
                            case "tag" -> matchesTagCondition(bs);
                            case "nbt" -> matchesNbtCondition(wx, wy, wz);
                            case "custom" -> matchesCustomCondition(bs);
                            default -> false;
                        };

                        if (!matches) continue;

                        var old = model.getBlockAt(wx, wy, wz);
                        var newState = replaceTargetState;
                        if (conditionType.equals("property") && !propName.isEmpty()) {
                            for (var p : replaceTargetState.getProperties()) {
                                if (p.getName().equals(propName)) {
                                    try {
                                        newState = applyPropertyFromString(newState, p, propVal);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                        model.setBlockAt(wx, wy, wz, newState);
                        snapshots.add(new Object[]{wx, wy, wz, old, newState});
                    }
                }
            }
        }
        if (!snapshots.isEmpty()) {
            history.push(new com.l1ght.ebe.editor.history.HistoryEntry(
                    history.nextId(), com.l1ght.ebe.editor.history.HistoryActionType.REPLACE,
                    snapshots.toArray(new Object[0][]),
                    (int) snapshots.get(0)[0], (int) snapshots.get(0)[1], (int) snapshots.get(0)[2],
                    replaceTargetState.getBlock().getDescriptionId(), snapshots.size()));
        }
        ViewportFactory.refreshFromModel(model);
    }

    private static boolean matchesPropertyCondition(BlockState bs) {
        if (replaceSourceBlock != null && bs.getBlock() != replaceSourceBlock) return false;
        var propNameEl = UIUtils.findById(replacePanel, "replaceCondPropName");
        var propValEl = UIUtils.findById(replacePanel, "replaceCondPropValue");
        String propName = propNameEl instanceof TextField tf ? tf.getText() : "";
        String propVal = propValEl instanceof TextField tf ? tf.getText() : "";
        if (propName.isEmpty()) return false;
        net.minecraft.world.level.block.state.properties.Property<?> matchProp = null;
        for (var p : bs.getProperties()) {
            if (p.getName().equals(propName)) { matchProp = p; break; }
        }
        if (matchProp == null) return false;
        var currentVal = bs.getValue(matchProp);
        return currentVal.toString().equals(propVal);
    }

    private static boolean matchesColorCondition(BlockState bs) {
        var colorIdEl = UIUtils.findById(replacePanel, "replaceCondColorId");
        String colorIdStr = colorIdEl instanceof TextField tf ? tf.getText() : "";
        if (colorIdStr.isEmpty()) return false;
        try {
            int targetColorId = Integer.parseInt(colorIdStr);
            var level = Minecraft.getInstance().level;
            if (level == null) return false;
            MapColor mapColor = bs.getMapColor(level, BlockPos.ZERO);
            return mapColor.id == targetColorId;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean matchesMaterialCondition(BlockState bs) {
        var block = bs.getBlock();
        var blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        return switch (conditionMaterialType) {
            case "wood" -> bs.is(BlockTags.PLANKS) || bs.is(BlockTags.LOGS);
            case "stone" -> bs.is(BlockTags.STONE_BRICKS) || blockId.contains("stone") || blockId.contains("cobble");
            case "metal" -> bs.is(BlockTags.BEACON_BASE_BLOCKS);
            case "glass" -> bs.is(BlockTags.IMPERMEABLE) || blockId.contains("glass");
            case "wool" -> bs.is(BlockTags.WOOL);
            case "concrete" -> blockId.contains("concrete") && !blockId.contains("concrete_powder");
            case "terracotta" -> blockId.contains("terracotta");
            default -> false;
        };
    }

    private static boolean matchesTagCondition(BlockState bs) {
        var tagNameEl = UIUtils.findById(replacePanel, "replaceCondTagName");
        String tagName = tagNameEl instanceof TextField tf ? tf.getText() : "";
        if (tagName.isEmpty()) return false;
        try {
            var tagKey = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse(tagName));
            return bs.is(tagKey);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean matchesNbtCondition(int wx, int wy, int wz) {
        var nbtFieldEl = UIUtils.findById(replacePanel, "replaceCondNbtField");
        var nbtValEl = UIUtils.findById(replacePanel, "replaceCondNbtValue");
        String nbtField = nbtFieldEl instanceof TextField tf ? tf.getText() : "";
        String nbtVal = nbtValEl instanceof TextField tf ? tf.getText() : "";
        if (nbtField.isEmpty()) return false;
        var nbt = session.getModel().getBlockEntityNbt(wx, wy, wz);
        if (nbt == null) return false;
        if (!nbt.contains(nbtField)) return false;
        if (nbtVal.isEmpty()) return true;
        var tag = nbt.get(nbtField);
        String tagStr = nbtValueToString(tag).replace("\"", "");
        return tagStr.equals(nbtVal) || tagStr.replaceAll("[LfFdDsBb]$", "").equals(nbtVal);
    }

    private static boolean matchesCustomCondition(BlockState bs) {
        var ruleEl = UIUtils.findById(replacePanel, "replaceCondCustomRule");
        String rule = ruleEl instanceof TextField tf ? tf.getText() : "";
        if (rule.isEmpty()) return false;
        var conditions = rule.split(",");
        for (String cond : conditions) {
            var parts = cond.trim().split("=");
            if (parts.length != 2) return false;
            String propName = parts[0].trim();
            String propVal = parts[1].trim();
            boolean found = false;
            for (var p : bs.getProperties()) {
                if (p.getName().equals(propName) && bs.getValue(p).toString().equals(propVal)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static void executeCustomReplace() {
        if (session == null || replaceTargetState == null) return;
        var model = session.getModel();
        var selection = getSelection();
        var history = getHistory();
        if (selection.isEmpty()) return;
        var snapshots = new ArrayList<Object[]>();
        for (var packed : selection.getAllPacked()) {
            int x = com.l1ght.ebe.editor.selection.SelectionManager.unpackX(packed);
            int y = com.l1ght.ebe.editor.selection.SelectionManager.unpackY(packed);
            int z = com.l1ght.ebe.editor.selection.SelectionManager.unpackZ(packed);
            var old = model.getBlockAt(x, y, z);
            model.setBlockAt(x, y, z, replaceTargetState);
            snapshots.add(new Object[]{x, y, z, old, replaceTargetState});
        }
        if (!snapshots.isEmpty()) {
            history.push(new com.l1ght.ebe.editor.history.HistoryEntry(
                    history.nextId(), com.l1ght.ebe.editor.history.HistoryActionType.REPLACE,
                    snapshots.toArray(new Object[0][]),
                    (int) snapshots.get(0)[0], (int) snapshots.get(0)[1], (int) snapshots.get(0)[2],
                    replaceTargetState.getBlock().getDescriptionId(), snapshots.size()));
        }
        ViewportFactory.refreshFromModel(model);
    }

    private static void refreshAfterEdit() {
        var model = session.getModel();
        ViewportFactory.refreshFromModel(model);
        refreshMaterialList();
        refreshHistoryList();
    }

    public static void refreshKeybindHints() {
        if (keybindHintsPanel == null) return;
        var hintsLabel = UIUtils.findById(keybindHintsPanel, "keybindHintsText");
        if (!(hintsLabel instanceof Label l)) return;

        var tool = state.getActiveTool();
        String text = switch (tool) {
            case SELECT -> "■ " +
                    Component.translatable("ebe.hints.select.click").getString() + "\n" +
                    "■ Ctrl+" + Component.translatable("ebe.hints.select.multi").getString() + "\n" +
                    "■ Shift+" + Component.translatable("ebe.hints.select.box").getString() + "\n" +
                    "■ Ctrl+Shift+" + Component.translatable("ebe.hints.select.same_type").getString() + "\n" +
                    "■ " + Component.translatable("ebe.hints.common.undo").getString() + ": Ctrl+Z/Y\n" +
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
        if (isTextFieldFocused()) return false;
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
