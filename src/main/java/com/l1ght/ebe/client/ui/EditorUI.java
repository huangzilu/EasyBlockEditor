package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Menu;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;

public class EditorUI {

    private static final EditorState state = new EditorState();

    public static ModularUI createModularUI() {
        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN));
        root.setId("root");

        root.addChild(buildMenuBar());
        root.addChild(buildContentArea());
        root.addChild(buildBottomBar());

        var ui = UI.of(root, Collections.emptyList(), screenSize -> screenSize);
        return ModularUI.of(ui);
    }

    public static EditorState getState() {
        return state;
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
                openMenu(btn, menuTree);
            }
        });
        return btn;
    }

    private static UIElement openDropdown;

    private static void openMenu(UIElement anchor, MenuTreeNode tree) {
        if (openDropdown != null) {
            openDropdown.removeSelf();
            openDropdown = null;
        }

        UIElementProvider<String> uiProvider = key -> {
            var label = new Label();
            label.setText(Component.translatable(key));
            label.textStyle(ts -> ts.textColor(0xFFE0E0E0).textShadow(false));
            return label;
        };

        var menu = new Menu<>(tree, uiProvider);
        menu.setId("__dropdown_menu__");
        menu.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(anchor.getPositionX())
                .top(anchor.getPositionY() + anchor.getSizeHeight()));
        menu.setOnNodeClicked(node -> {
            var action = node.getContent();
            if (action != null) {
                action.run();
            }
        });
        menu.setOnClose(() -> openDropdown = null);

        anchor.addChild(menu);
        openDropdown = menu;
    }

    private static MenuTreeNode buildFileMenu() {
        var root = new MenuTreeNode("file");
        root.child("ebe.editor.new_project");
        root.child("ebe.editor.open");
        root.child("ebe.editor.save");
        root.child("ebe.editor.save_as");
        root.child("ebe.editor.import");
        root.child("ebe.editor.export");
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

    private static UIElement buildLeftPanel() {
        var panel = new UIElement();
        panel.layout(l -> l.width(200).flexDirection(FlexDirection.COLUMN));
        panel.style(s -> s.background(Sprites.RECT_DARK));

        var titleBar = new UIElement();
        titleBar.layout(l -> l.widthPercent(100).height(16).paddingHorizontal(4)
                .alignItems(AlignItems.CENTER));
        var title = new Label();
        title.setText(Component.translatable("ebe.editor.panel.files"));
        title.textStyle(ts -> ts.textColor(0xFFE0E0E0).textShadow(false));
        titleBar.addChild(title);
        panel.addChild(titleBar);

        var treeList = createFileTree();
        var scroller = new ScrollerView();
        scroller.layout(l -> l.flex(1).widthPercent(100));
        scroller.addScrollViewChild(treeList);
        panel.addChild(scroller);

        return panel;
    }

    private static TreeList<FileTreeNode> createFileTree() {
        var root = buildFileSystemTree();

        var treeList = new TreeList<FileTreeNode>(root, true);
        treeList.setNodeUISupplier(TreeList.textTemplate(
                node -> Component.literal(node.getPath().getFileName().toString())
        ));
        treeList.setClickToExpand(true);
        treeList.setDoubleClickToExpand(false);
        treeList.setOnSelectedChanged(selected -> {
            if (!selected.isEmpty()) {
                var node = selected.iterator().next();
                if (!node.isDirectory()) {
                    onFileSelected(node.getPath());
                }
            }
        });
        return treeList;
    }

    private static FileTreeNode buildFileSystemTree() {
        var dir = Path.of("config/ebe/client/schematics");
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
    }

    private static UIElement buildViewport() {
        return ViewportFactory.create3DViewport();
    }

    private static UIElement buildRightPanel() {
        var panel = new UIElement();
        panel.layout(l -> l.width(250).flexDirection(FlexDirection.COLUMN)
                .paddingAll(4).gapAll(4));
        panel.style(s -> s.background(Sprites.RECT_DARK));

        var title = new Label();
        title.setText(Component.translatable("ebe.editor.panel.properties"));
        title.textStyle(ts -> ts.textColor(0xFFE0E0E0).textShadow(false));
        panel.addChild(title);

        return panel;
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
                state.setActiveTool(tool);
            }
        });
        return btn;
    }
}
