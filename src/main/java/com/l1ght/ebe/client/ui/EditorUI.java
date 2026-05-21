package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.client.keybind.EBEKeyMappings;
import com.l1ght.ebe.config.EBEClientConfig;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EditorUI {

    private static final EditorState state = new EditorState();
    private static final EditorSession session = new EditorSession();
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

    private static UIElement toolbarPanel;
    private static boolean toolbarExpanded = true;

    private static UIElement materialListContainer;
    private static ScrollerView materialListScroller;

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

            if (key.equals("ebe.editor.panel.files") || key.equals("ebe.editor.panel.layers")) {
                isChecked = leftPanelVisible;
            } else if (key.equals("ebe.editor.panel.properties") || key.equals("ebe.editor.panel.materials") || key.equals("ebe.editor.panel.history")) {
                isChecked = rightPanelVisible;
            } else if (key.equals("ebe.editor.block_indicator")) {
                isChecked = blockIndicatorVisible;
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
            try { session.load(file); ViewportFactory.loadFromModel(session.getModel()); } catch (Exception e) { e.printStackTrace(); }
        }));
        root.child("ebe.editor.save", () -> { try { session.save(); } catch (Exception e) { e.printStackTrace(); } });
        root.child("ebe.editor.save_as", () -> EditorDialogs.saveAsDialog(rootElement, session.getCurrentName(), name -> {
            try { session.saveAs(name); } catch (Exception e) { e.printStackTrace(); }
        }));
        root.child("ebe.editor.import", () -> ImportDialog.showImport(rootElement, file -> {
            try { session.load(file); ViewportFactory.loadFromModel(session.getModel()); } catch (Exception e) { e.printStackTrace(); }
        }));
        root.child("ebe.editor.export", () -> EditorDialogs.saveAsDialog(rootElement, session.getCurrentName(), name -> {
            try { session.saveAs(name); } catch (Exception e) { e.printStackTrace(); }
        }));
        return root;
    }

    private static MenuTreeNode buildEditMenu() {
        var root = new MenuTreeNode("edit");
        root.child("ebe.editor.undo");
        root.child("ebe.editor.redo");
        root.child("ebe.editor.copy");
        root.child("ebe.editor.paste");
        root.child("ebe.editor.cut");
        root.child("ebe.editor.select_all");
        root.child("ebe.editor.deselect");
        return root;
    }

    private static MenuTreeNode buildViewMenu() {
        var root = new MenuTreeNode("view");
        root.child("ebe.editor.panel.files", () -> toggleLeftPanel());
        root.child("ebe.editor.panel.layers", () -> toggleLeftPanel());
        root.child("ebe.editor.panel.properties", () -> toggleRightPanel());
        root.child("ebe.editor.panel.materials", () -> toggleRightPanel());
        root.child("ebe.editor.panel.history", () -> toggleRightPanel());
        root.child("ebe.editor.block_palette", () -> BlockPaletteUI.togglePalette(rootElement));
        root.child("ebe.editor.block_indicator", () -> toggleBlockIndicator());
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

    private static UIElement buildToolbarButton(EditorTool tool) {
        var btn = new UIElement();
        btn.layout(l -> l.width(26).height(26).paddingAll(2));
        btn.style(s -> s.background(Sprites.RECT_RD));
        registerTooltip(btn, Component.translatable(tool.getTranslationKey()));

        var iconStack = getToolIcon(tool);
        if (iconStack != null) {
            btn.style(s -> s.backgroundTexture(new ItemStackTexture(iconStack)));
        }

        btn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) selectTool(tool);
        });
        return btn;
    }

    private static ItemStack getToolIcon(EditorTool tool) {
        return switch (tool) {
            case SELECT -> new ItemStack(Items.STICK);
            case PLACE -> new ItemStack(Items.STONE);
            case DELETE -> new ItemStack(Items.BARRIER);
            case REPLACE -> new ItemStack(Items.PAPER);
            case GRAB -> new ItemStack(Items.ENDER_EYE);
            case MEASURE -> new ItemStack(Items.COMPASS);
            case FILL -> new ItemStack(Items.WATER_BUCKET);
        };
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

    private static void selectTool(EditorTool tool) {
        state.setActiveTool(tool);
        highlightCurrentTool();
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
        tabView.addTab(propsTab, createPlaceholderPanel());

        var matsTab = new Tab();
        matsTab.setText(Component.translatable("ebe.editor.panel.materials"));
        tabView.addTab(matsTab, createPlaceholderPanel());

        var histTab = new Tab();
        histTab.setText(Component.translatable("ebe.editor.panel.history"));
        tabView.addTab(histTab, createPlaceholderPanel());

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

    // ========== Bottom Bar (Material List) ==========

    private static UIElement buildBottomBar() {
        var bar = new UIElement();
        bar.layout(l -> l.widthPercent(100).height(28).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).paddingHorizontal(4).gapAll(2));
        bar.style(s -> s.background(Sprites.BORDER));
        bar.setId("bottomBar");

        var title = new Label();
        title.setText(Component.translatable("ebe.editor.material_list"));
        title.textStyle(ts -> ts.textColor(0xFFCCCCCC).textShadow(false).fontSize(10));
        bar.addChild(title);

        var sep = new UIElement();
        sep.layout(l -> l.width(1).heightPercent(80));
        sep.style(s -> s.background(Sprites.RECT_DARK));
        bar.addChild(sep);

        var materialsScroller = new ScrollerView();
        materialsScroller.layout(l -> l.flex(1).heightPercent(100));
        materialListScroller = materialsScroller;
        materialListContainer = new UIElement();
        materialListContainer.layout(l -> l.flexDirection(FlexDirection.ROW).gapAll(4).alignItems(AlignItems.CENTER).paddingHorizontal(4));
        materialsScroller.addScrollViewChild(materialListContainer);
        bar.addChild(materialsScroller);

        updateMaterialList();

        var spacer = new UIElement();
        spacer.layout(l -> l.width(8));
        bar.addChild(spacer);

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
                                var loc = net.minecraft.resources.ResourceLocation.parse(
                                        s.contains("[") ? s.substring(0, s.indexOf('[')) : s);
                                BuiltInRegistries.BLOCK.getOptional(loc)
                                        .ifPresent(block -> counts.merge(block, 1, Integer::sum));
                            }
                        }
                    }
                }
            }

            for (var entry : counts.entrySet()) {
                var row = new UIElement();
                row.layout(l -> l.flexDirection(FlexDirection.ROW).gapAll(2).alignItems(AlignItems.CENTER));

                var icon = new UIElement();
                icon.layout(l -> l.width(14).height(14));
                icon.style(s -> s.backgroundTexture(new ItemStackTexture(entry.getKey().asItem())));
                row.addChild(icon);

                var countLabel = new Label();
                countLabel.setText(Component.literal("×" + entry.getValue()));
                countLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(9));
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
