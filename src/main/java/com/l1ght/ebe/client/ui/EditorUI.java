package com.l1ght.ebe.client.ui;

import com.l1ght.ebe.config.EBEClientConfig;
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
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class EditorUI {

    private static final EditorState state = new EditorState();
    private static final EditorSession session = new EditorSession();
    private static final Map<EditorTool, Button> toolButtons = new EnumMap<>(EditorTool.class);
    private static UIElement rootElement;

    public static ModularUI createModularUI() {
        rootElement = new UIElement();
        rootElement.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN));
        rootElement.setId("root");

        rootElement.addChild(buildMenuBar());
        rootElement.addChild(buildContentArea());
        rootElement.addChild(buildBottomBar());

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

    public static EditorState getState() {
        return state;
    }

    public static EditorSession getSession() {
        return session;
    }

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
            if (e.button == 0) {
                toggleMenu(btn, menuTree);
            }
        });
        return btn;
    }

    private static UIElement openDropdown;
    private static UIElement openMenuAnchor;

    private static void closeMenu() {
        if (openDropdown != null) {
            openDropdown.removeSelf();
            openDropdown = null;
        }
        if (openMenuAnchor != null) {
            openMenuAnchor.style(s -> s.background(Sprites.RECT_RD));
            openMenuAnchor = null;
        }
    }

    private static void toggleMenu(UIElement anchor, MenuTreeNode tree) {
        if (openMenuAnchor == anchor) {
            closeMenu();
            return;
        }

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
            item.setText(Component.translatable(child.getKey()));
            item.layout(l -> l.widthPercent(100).height(18).paddingHorizontal(8));
            var action = child.getContent();
            if (action != null) {
                item.setOnClick(e -> {
                    action.run();
                    closeMenu();
                });
            }
            dropdown.addChild(item);
        }

        rootElement.addChild(dropdown);

        openMenuAnchor = anchor;
        anchor.style(s -> s.background(Sprites.RECT_RD_DARK));

        openDropdown = dropdown;
    }

    private static MenuTreeNode buildFileMenu() {
        var root = new MenuTreeNode("file");
        root.child("ebe.editor.new_project", () -> EditorDialogs.newProjectDialog(rootElement, name -> {
            session.newProject(name);
        }));
        root.child("ebe.editor.open", () -> ImportDialog.show(rootElement, file -> {
            try { session.load(file); ViewportFactory.loadFromModel(session.getModel()); } catch (Exception ignored) {}
        }));
        root.child("ebe.editor.save", () -> {
            try { session.save(); } catch (Exception ignored) {}
        });
        root.child("ebe.editor.save_as", () -> EditorDialogs.saveAsDialog(rootElement, session.getCurrentName(), name -> {
            try { session.saveAs(name); } catch (Exception ignored) {}
        }));
        root.child("ebe.editor.import", () -> ImportDialog.show(rootElement, file -> {
            try { session.load(file); ViewportFactory.loadFromModel(session.getModel()); } catch (Exception ignored) {}
        }));
        root.child("ebe.editor.export", () -> EditorDialogs.saveAsDialog(rootElement, session.getCurrentName(), name -> {
            try { session.saveAs(name); } catch (Exception ignored) {}
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
        root.child("ebe.editor.panel.files");
        root.child("ebe.editor.panel.layers");
        root.child("ebe.editor.panel.properties");
        root.child("ebe.editor.panel.materials");
        root.child("ebe.editor.panel.history");
        root.child("ebe.editor.block_palette", () -> BlockPaletteUI.togglePalette(rootElement));
        root.child("ebe.editor.settings", () -> SettingsUI.showSettings(rootElement));
        return root;
    }

    private static MenuTreeNode buildToolMenu() {
        var root = new MenuTreeNode("tool");
        root.child("ebe.editor.tool.select");
        root.child("ebe.editor.tool.place");
        root.child("ebe.editor.tool.delete");
        root.child("ebe.editor.tool.replace");
        root.child("ebe.editor.tool.grab");
        root.child("ebe.editor.tool.measure");
        root.child("ebe.editor.tool.fill");
        return root;
    }

    private static MenuTreeNode buildHelpMenu() {
        var root = new MenuTreeNode("help");
        root.child("ebe.name");
        return root;
    }

    private static UIElement buildContentArea() {
        var content = new UIElement();
        content.layout(l -> l.flexDirection(FlexDirection.ROW).flex(1).widthPercent(100)
                .alignItems(AlignItems.STRETCH));

        content.addChild(buildLeftPanel());
        content.addChild(buildViewport());
        content.addChild(buildRightPanel());

        return content;
    }

    private static TabView createTopTabView() {
        var tabView = new TabView();
        tabView.layout(l -> l.flexDirection(FlexDirection.COLUMN).flex(1).widthPercent(100));

        var header = tabView.tabHeaderContainer;
        var content = tabView.tabContentContainer;
        tabView.removeChild(header);
        tabView.removeChild(content);
        tabView.addChild(header);
        tabView.addChild(content);

        return tabView;
    }

    private static UIElement buildLeftPanel() {
        var panel = new UIElement();
        panel.layout(l -> l.widthPercent(15).minWidth(120).maxWidth(300).flexDirection(FlexDirection.COLUMN));
        panel.style(s -> s.background(Sprites.RECT_DARK));

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
                        .forEach(p -> {
                            if (Files.isDirectory(p)) {
                                root.addChild(FileTreeNode.ofDirectory(p));
                            } else {
                                root.addChild(FileTreeNode.ofFile(p));
                            }
                        });
            } catch (Exception ignored) {
            }
        }
        return root;
    }

    private static void onFileSelected(Path file) {
        try {
            EditorUI.getSession().load(file);
            ViewportFactory.loadFromModel(EditorUI.getSession().getModel());
        } catch (Exception ignored) {
        }
    }

    private static UIElement buildViewport() {
        return ViewportFactory.create3DViewport();
    }

    private static UIElement buildRightPanel() {
        var panel = new UIElement();
        panel.layout(l -> l.widthPercent(18).minWidth(150).maxWidth(350).flexDirection(FlexDirection.COLUMN));
        panel.style(s -> s.background(Sprites.RECT_DARK));

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

    private static UIElement createPlaceholderPanel() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4));
        return container;
    }

    private static UIElement buildBottomBar() {
        var bar = new UIElement();
        bar.layout(l -> l.widthPercent(100).height(28).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).paddingHorizontal(4).gapAll(2));
        bar.style(s -> s.background(Sprites.BORDER));

        for (var tool : EditorTool.values()) {
            bar.addChild(toolButton(tool));
        }

        var spacer = new UIElement();
        spacer.layout(l -> l.flex(1));
        bar.addChild(spacer);

        var blockPanel = new UIElement();
        blockPanel.setId("activeBlockPanel");
        blockPanel.layout(l -> l.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4)
                .paddingHorizontal(4).paddingVertical(2));
        blockPanel.style(s -> s.background(Sprites.RECT_DARK));

        var blockSceneWrap = new UIElement();
        blockSceneWrap.setId("activeBlockSceneWrap");
        blockSceneWrap.layout(l -> l.width(20).height(20));
        var blockIcon = new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(
                net.minecraft.world.item.Items.AIR);
        blockSceneWrap.style(s -> s.backgroundTexture(blockIcon));
        blockPanel.addChild(blockSceneWrap);

        var blockInfo = new UIElement();
        blockInfo.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(1));

        var blockNameLabel = new Label();
        blockNameLabel.setId("activeBlockLabel");
        blockNameLabel.setText(Component.translatable("ebe.editor.palette.selected_none"));
        blockNameLabel.textStyle(ts -> ts.textColor(0xFFFFD700).textShadow(false).fontSize(10));
        blockInfo.addChild(blockNameLabel);

        var blockNbtLabel = new Label();
        blockNbtLabel.setId("activeBlockNbtLabel");
        blockNbtLabel.setText(Component.literal(""));
        blockNbtLabel.textStyle(ts -> ts.textColor(0xFFA0A0A0).textShadow(false).fontSize(8));
        blockInfo.addChild(blockNbtLabel);

        blockPanel.addChild(blockInfo);
        bar.addChild(blockPanel);

        var spacer2 = new UIElement();
        spacer2.layout(l -> l.width(8));
        bar.addChild(spacer2);

        var status = new Label();
        status.setText(Component.literal(state.buildStatusText()));
        status.textStyle(ts -> ts.textColor(0xFFE0E0E0).textShadow(false));
        status.setId("statusBar");
        bar.addChild(status);

        return bar;
    }

    private static Button toolButton(EditorTool tool) {
        var btn = new Button();
        btn.setText(Component.translatable(tool.getTranslationKey()));
        btn.layout(l -> l.height(20).paddingHorizontal(6));
        btn.addEventListener(UIEvents.CLICK, e -> {
            if (e.button == 0) {
                selectTool(tool);
            }
        });
        toolButtons.put(tool, btn);
        if (tool == state.getActiveTool()) {
            btn.style(s -> s.background(Sprites.RECT_RD_DARK));
        }
        return btn;
    }

    public static void updateActiveBlockIndicator() {
        var bs = state.getActiveBlockState();
        var iconWrap = UIUtils.findById(rootElement, "activeBlockSceneWrap");
        var nameLabel = UIUtils.findById(rootElement, "activeBlockLabel");
        var nbtLabel = UIUtils.findById(rootElement, "activeBlockNbtLabel");

        if (bs != null && !bs.isAir()) {
            if (iconWrap != null) {
                iconWrap.style(s -> s.backgroundTexture(
                        new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(bs.getBlock().asItem())));
            }
            if (nameLabel instanceof Label l) {
                l.setText(Component.translatable("ebe.editor.palette.selected", bs.getBlock().getName()));
            }
            if (nbtLabel instanceof Label l) {
                var props = bs.getValues();
                if (props.isEmpty()) {
                    l.setText(Component.literal(""));
                } else {
                    var sb = new StringBuilder();
                    for (var entry : props.entrySet()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(entry.getKey().getName())
                                .append("=")
                                .append(entry.getValue());
                    }
                    l.setText(Component.literal(sb.toString()));
                }
            }
        } else {
            if (iconWrap != null) {
                iconWrap.style(s -> s.backgroundTexture(
                        new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(
                                net.minecraft.world.item.Items.AIR)));
            }
            if (nameLabel instanceof Label l) {
                l.setText(Component.translatable("ebe.editor.palette.selected_none"));
            }
            if (nbtLabel instanceof Label l) {
                l.setText(Component.literal(""));
            }
        }
    }

    private static void selectTool(EditorTool tool) {
        var prev = toolButtons.get(state.getActiveTool());
        if (prev != null) {
            prev.style(s -> s.background(Sprites.RECT_RD));
        }
        state.setActiveTool(tool);
        var next = toolButtons.get(tool);
        if (next != null) {
            next.style(s -> s.background(Sprites.RECT_RD_DARK));
        }
    }
}
