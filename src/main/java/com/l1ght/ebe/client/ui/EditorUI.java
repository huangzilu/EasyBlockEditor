package com.l1ght.ebe.client.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.l1ght.ebe.client.keybind.EBEKeyBindings;
import com.l1ght.ebe.client.keybind.KeyRecordingManager;
import com.l1ght.ebe.client.renderer.HeatmapMode;
import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.io.FileManager;
import com.l1ght.ebe.editor.selection.DisplayFilter;
import com.l1ght.ebe.network.WorkgroupActionPayload;
import com.l1ght.ebe.client.projection.PrinterController;
import com.l1ght.ebe.projection.PrinterMode;
import com.l1ght.ebe.client.projection.ProjectionManager;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.l1ght.ebe.data.BuildingModel;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class EditorUI {

    private static String getShortcutDisplay(String i18nKey) {
        var binding = EBEKeyBindings.getById(i18nKey);
        return binding != null ? binding.getDisplayName() : "";
    }

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
    private static UIElement fileListContainer;
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

    private static UIElement draggingPanel = null;
    private static float panelDragOffsetX = 0, panelDragOffsetY = 0;

    private static UIElement toolbarPanel;
    private static boolean toolbarExpanded = true;
    private static boolean toolbarVisible = true;
    private static float toolbarOffsetX = 4, toolbarOffsetY = 0;
    private static boolean toolbarDragging = false;
    private static float toolbarDragStartX, toolbarDragStartY, toolbarDragOriginX, toolbarDragOriginY;
    private static long horizontalResizeCursor = 0L;
    private static boolean resizeCursorActive = false;

    private static UIElement materialListContainer;
    private static UIElement propertiesContainer;
    private static int displayFilterMode = 0;
    private static UIElement displayFilterContentContainer;
    private static String selectedHeatmapBtnId = "heatmapBtn_0";
    private static String selectedPrinterModeBtnId = "printerModeBtn_0";
    private static long lastFileTreeSignature = Long.MIN_VALUE;
    private static int fileRefreshCooldown = 0;

    private static UIElement openDropdown;
    private static UIElement openMenuAnchor;
    private static final ExecutorService FILE_LOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "EBE File Loader");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicInteger FILE_LOAD_SEQUENCE = new AtomicInteger();

    public static ModularUI createModularUI() {
        ProjectionManager.loadPersistentStateIfNeeded();
        projectionPanel = null;
        projectionPanelVisible = false;
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
                resetMouseCursor();
            }
            if (e.button == 0 && draggingPanel != null) draggingPanel = null;
            if (e.button == 0) toolbarDragging = false;
        }, true);

        rootElement.addEventListener(UIEvents.MOUSE_MOVE, e -> {
            if (draggingPanel != null) {
                float newX = e.x - panelDragOffsetX;
                float newY = e.y - panelDragOffsetY;
                draggingPanel.layout(l -> l.left(newX).top(newY));
            }
            if (toolbarDragging) {
                float dx = e.x - toolbarDragStartX;
                float dy = e.y - toolbarDragStartY;
                toolbarOffsetX = toolbarDragOriginX + dx;
                toolbarOffsetY = toolbarDragOriginY + dy;
                if (toolbarPanel != null) {
                    toolbarPanel.layout(l -> l.left(toolbarOffsetX).top(toolbarOffsetY));
                }
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
            case "modern" -> StylesheetManager.GDP;
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
                menuButton("ebe.projection.panel", buildProjectionMenu()),
                menuButton("ebe.editor.menu.help", buildHelpMenu())
        );

        var spacer = new UIElement();
        spacer.layout(l -> l.flex(1));
        bar.addChild(spacer);

        var workgroupBtn = new UIElement();
        workgroupBtn.layout(l -> l.width(22).height(22).alignItems(AlignItems.CENTER).justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER));
        var workgroupIcon = new UIElement();
        workgroupIcon.layout(l -> l.width(18).height(18));
        workgroupIcon.style(s -> s.backgroundTexture(EditorIcons.WORKGROUP));
        workgroupBtn.addChild(workgroupIcon);
        workgroupBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) toggleWorkgroupPanel();
        });
        registerTooltip(workgroupBtn, Component.translatable("ebe.workgroup.panel"));
        bar.addChild(workgroupBtn);

        var settingsBtn = new UIElement();
        settingsBtn.layout(l -> l.width(22).height(22).alignItems(AlignItems.CENTER).justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER));
        var settingsIcon = new UIElement();
        settingsIcon.layout(l -> l.width(18).height(18));
        settingsIcon.style(s -> s.backgroundTexture(EditorIcons.SETTINGS));
        settingsBtn.addChild(settingsIcon);
        settingsBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) SettingsUI.showSettings(rootElement);
        });
        registerTooltip(settingsBtn, Component.translatable("ebe.editor.menu.settings"));
        bar.addChild(settingsBtn);

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
            var text = key.equals("ebe.help.about_entry")
                    ? Component.translatable(key, HelpManualUI.version())
                    : Component.translatable(key);
            boolean isChecked = false;

            var shortcut = getShortcutDisplay(key);
            if (!shortcut.isEmpty()) {
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
                var check = Component.literal("*").withStyle(ChatFormatting.GREEN);
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
            beginLoadFile(file);
        }));
        root.child("ebe.editor.save", () -> { try { session.save(); } catch (Exception e) { e.printStackTrace(); } });
        root.child("ebe.editor.save_as", () -> EditorDialogs.saveAsDialog(rootElement, session.getCurrentName(), name -> {
            try { session.saveAs(name); } catch (Exception e) { e.printStackTrace(); }
        }));
        root.child("ebe.editor.import", () -> ImportDialog.showImport(rootElement, file -> {
            beginLoadFile(file);
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
            int beforeUndo = history.undoSize();
            clipboard.paste(session.getModel(), new net.minecraft.core.BlockPos(
                    state.getCursorX(), state.getCursorY(), state.getCursorZ()), history);
            finishClipboardMutation(beforeUndo);
        });
        root.child("ebe.editor.cut", () -> {
            int beforeUndo = history.undoSize();
            clipboard.cut(session.getModel(), selection, history);
            finishClipboardMutation(beforeUndo);
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
        return root;
    }

    private static MenuTreeNode buildToolMenu() {
        var root = new MenuTreeNode("tool");
        for (var t : EditorTool.values()) root.child(t.getTranslationKey(), () -> selectTool(t));
        return root;
    }

    private static MenuTreeNode buildHelpMenu() {
        var root = new MenuTreeNode("help");
        root.child("ebe.help.about_entry", () -> HelpManualUI.showAbout(rootElement));
        root.child("ebe.help.manual", () -> HelpManualUI.show(rootElement));
        return root;
    }

    private static MenuTreeNode buildProjectionMenu() {
        var root = new MenuTreeNode("projection");
        root.child("ebe.projection.panel", () -> toggleProjectionPanel());
        return root;
    }

    private static void beginLoadFile(Path file) {
        int sequence = FILE_LOAD_SEQUENCE.incrementAndGet();
        setStatus(Component.translatable("ebe.editor.loading.reading", file.getFileName().toString()));
        CompletableFuture.supplyAsync(() -> {
            try {
                return EditorSession.readFileWithComputed(file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, FILE_LOAD_EXECUTOR).whenComplete((loaded, error) -> Minecraft.getInstance().execute(() -> {
            if (sequence != FILE_LOAD_SEQUENCE.get()) return;
            if (error != null) {
                setStatus(Component.translatable("ebe.editor.loading.failed", rootCauseMessage(error)));
                error.printStackTrace();
                return;
            }
            session.applyLoaded(loaded);
            setStatus(Component.translatable("ebe.editor.loading.viewport", loaded.file().getFileName().toString()));
            boolean preferSyncViewport = EditorSession.shouldPreferSynchronousViewportLoad(loaded.file());
            if (loaded.computed() != null) {
                if (!preferSyncViewport && ViewportFactory.shouldLoadComputedProgressively(loaded.computed())) {
                    ViewportFactory.loadFromComputedProgressive(loaded.computed());
                } else {
                    ViewportFactory.loadFromModel(session.getModel());
                }
            } else {
                if (!preferSyncViewport && ViewportFactory.shouldLoadModelProgressively(session.getModel())) {
                    ViewportFactory.loadFromModelProgressive(session.getModel());
                } else {
                    ViewportFactory.loadFromModel(session.getModel());
                }
            }
        }));
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    public static void setStatus(Component text) {
        if (rootElement == null) return;
        var status = findById(rootElement, "statusBar");
        if (status instanceof Label label) {
            label.setText(text);
        }
    }

    private static UIElement projectionPanel;
    private static boolean projectionPanelVisible = false;
    private static UIElement workgroupPanel;
    private static boolean workgroupPanelVisible = false;

    private static void toggleWorkgroupPanel() {
        if (workgroupPanelVisible && workgroupPanel != null) {
            workgroupPanel.removeSelf();
            workgroupPanel = null;
            workgroupPanelVisible = false;
            return;
        }
        showWorkgroupPanel();
    }

    private static void showWorkgroupPanel() {
        if (workgroupPanel != null) workgroupPanel.removeSelf();
        sendWorkgroupAction("sync", "", "", "");

        var panel = new Dialog();
        panel.setTitle("ebe.workgroup.panel");
        panel.windowMode(12, 32, 320, 390);
        panel.setAutoClose(false);
        panel.setClickOutsideClose(false);

        var content = new UIElement();
        content.setId("workgroupPanelContent");
        content.layout(l -> l.widthPercent(100).flex(1).flexDirection(FlexDirection.COLUMN).gapAll(4));
        panel.addContent(content);
        panel.addButton(new Button()
                .setText(Component.translatable("ebe.editor.close"))
                .setOnClick(e -> {
                    panel.close();
                    workgroupPanel = null;
                    workgroupPanelVisible = false;
                }));
        panel.show(rootElement);

        workgroupPanel = panel.overlay;
        workgroupPanelVisible = true;
        refreshWorkgroupPanel();
    }

    public static void refreshWorkgroupPanel() {
        if (!workgroupPanelVisible || rootElement == null) return;
        var content = findById(rootElement, "workgroupPanelContent");
        if (content == null) return;
        content.clearAllChildren();
        content.addChild(createWorkgroupPanelContent());
    }

    private static UIElement createWorkgroupPanelContent() {
        var wrapper = new UIElement();
        wrapper.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));

        var top = new UIElement();
        top.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        var title = new Label();
        title.setText(Component.translatable("ebe.workgroup.server_panel"));
        title.layout(l -> l.flex(1));
        title.textStyle(ts -> ts.textColor(0xFFFFD166).fontSize(11).textShadow(false));
        top.addChild(title);
        top.addChild(workgroupSmallButton("ebe.editor.refresh", 58, () -> sendWorkgroupAction("sync", "", "", "")));
        wrapper.addChild(top);

        JsonObject data = workgroupRootJson();
        JsonObject group = obj(data.get("group"));
        if (group.entrySet().isEmpty()) {
            wrapper.addChild(createWorkgroupJoinContent());
        } else {
            wrapper.addChild(createCurrentWorkgroupContent(group));
        }
        return wrapper;
    }

    private static UIElement createWorkgroupJoinContent() {
        var box = new UIElement();
        box.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4).paddingAll(5));
        box.style(s -> s.background(Sprites.RECT_RD));

        var hint = new Label();
        hint.setText(Component.translatable("ebe.workgroup.unique_hint"));
        hint.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(8).textShadow(false)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        box.addChild(hint);

        var nameField = new TextField();
        nameField.layout(l -> l.widthPercent(100).height(20));
        nameField.textFieldStyle(s -> s.placeholder(Component.translatable("ebe.workgroup.name")));
        box.addChild(nameField);

        var passwordField = new TextField();
        passwordField.layout(l -> l.widthPercent(100).height(20));
        passwordField.textFieldStyle(s -> s.placeholder(Component.translatable("ebe.workgroup.password_optional")));
        box.addChild(passwordField);

        var actions = new UIElement();
        actions.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4));
        var create = new Button();
        create.setText(Component.translatable("ebe.workgroup.create"));
        create.layout(l -> l.flex(1).height(20));
        create.setOnClick(e -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) sendWorkgroupAction("create", name, passwordField.getText(), "");
        });
        actions.addChild(create);

        var join = new Button();
        join.setText(Component.translatable("ebe.workgroup.join"));
        join.layout(l -> l.flex(1).height(20));
        join.setOnClick(e -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) sendWorkgroupAction("join", name, passwordField.getText(), "");
        });
        actions.addChild(join);
        box.addChild(actions);
        return box;
    }

    private static UIElement createCurrentWorkgroupContent(JsonObject group) {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));

        var list = new UIElement();
        list.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));
        String groupName = string(group.get("name"), "");
        boolean isLeader = bool(group.get("isLeader"));

        var summary = new UIElement();
        summary.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(5));
        summary.style(s -> s.background(Sprites.RECT_RD));
        var name = new Label();
        name.setText(Component.literal(groupName));
        name.textStyle(ts -> ts.textColor(0xFFFFFFFF).fontSize(12).textShadow(false));
        summary.addChild(name);
        var leader = new Label();
        leader.setText(Component.translatable("ebe.workgroup.leader_value", string(group.get("leader"), "-")));
        leader.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(8).textShadow(false));
        summary.addChild(leader);
        list.addChild(summary);

        var actions = new UIElement();
        actions.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4));
        if (isLeader) {
            var disband = new Button();
            disband.setText(Component.translatable("ebe.workgroup.disband"));
            disband.layout(l -> l.flex(1).height(20));
            disband.setOnClick(e -> sendWorkgroupAction("disband", groupName, "", ""));
            actions.addChild(disband);
        } else {
            var leave = new Button();
            leave.setText(Component.translatable("ebe.workgroup.leave"));
            leave.layout(l -> l.flex(1).height(20));
            leave.setOnClick(e -> sendWorkgroupAction("leave", groupName, "", ""));
            actions.addChild(leave);
        }
        list.addChild(actions);

        list.addChild(workgroupSection("ebe.workgroup.members"));
        JsonArray members = arr(group.get("members"));
        for (JsonElement memberElem : members) {
            list.addChild(workgroupMemberRow(groupName, memberElem.getAsJsonObject(), isLeader));
        }

        list.addChild(workgroupSection("ebe.workgroup.synced_projections"));
        JsonArray projections = arr(group.get("projections"));
        if (projections.size() == 0) {
            var empty = new Label();
            empty.setText(Component.translatable("ebe.workgroup.no_synced_projections"));
            empty.textStyle(ts -> ts.textColor(0xFF888888).fontSize(8).textShadow(false));
            list.addChild(empty);
        } else {
            for (JsonElement projectionElem : projections) {
                JsonObject projection = projectionElem.getAsJsonObject();
                var label = new Label();
                label.setText(Component.translatable("ebe.workgroup.projection_row",
                        string(projection.get("file"), "-"),
                        integer(projection.get("x"), 0),
                        integer(projection.get("y"), 0),
                        integer(projection.get("z"), 0),
                        string(projection.get("owner"), "-")));
                label.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(8).textShadow(false)
                        .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
                list.addChild(label);
            }
        }

        scroller.addScrollViewChild(list);
        return scroller;
    }

    private static UIElement workgroupMemberRow(String groupName, JsonObject member, boolean canManage) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4).paddingAll(3));
        row.style(s -> s.background(Sprites.RECT_RD));
        String memberName = string(member.get("name"), "-");
        String role = string(member.get("role"), "member");
        var label = new Label();
        label.setText(Component.translatable("ebe.workgroup.member_row", memberName,
                Component.translatable("ebe.workgroup.role." + role)));
        label.layout(l -> l.flex(1));
        label.textStyle(ts -> ts.textColor("leader".equals(role) ? 0xFFFFD166 : 0xFFDDDDDD).fontSize(8).textShadow(false));
        row.addChild(label);
        if (canManage && !"leader".equals(role)) {
            row.addChild(workgroupSmallButton("ebe.workgroup.kick", 44, () -> sendWorkgroupAction("kick", groupName, "", memberName)));
        }
        return row;
    }

    private static Label workgroupSection(String key) {
        var label = new Label();
        label.setText(Component.translatable(key));
        label.textStyle(ts -> ts.textColor(0xFFFFD166).fontSize(10).textShadow(false));
        return label;
    }

    private static Button workgroupSmallButton(String key, int width, Runnable action) {
        var btn = new Button();
        btn.setText(Component.translatable(key));
        btn.layout(l -> l.width(width).height(18));
        btn.setOnClick(e -> action.run());
        return btn;
    }

    private static void sendWorkgroupAction(String action, String groupName, String password, String target) {
        PacketDistributor.sendToServer(new WorkgroupActionPayload(action, groupName, password, target));
    }

    private static JsonObject workgroupRootJson() {
        try {
            return JsonParser.parseString(com.l1ght.ebe.client.ClientOnlyHooks.getWorkgroupsJson()).getAsJsonObject();
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

    private static boolean bool(JsonElement elem) {
        return elem != null && !elem.isJsonNull() && elem.getAsBoolean();
    }

    private static void toggleProjectionPanel() {
        if (projectionPanelVisible && projectionPanel != null) {
            projectionPanel.removeSelf();
            projectionPanelVisible = false;
            return;
        }
        showProjectionPanel();
    }

    private static void showProjectionPanel() {
        if (projectionPanel != null) projectionPanel.removeSelf();

        var panel = new Dialog();
        panel.setTitle("ebe.projection.panel");
        panel.windowMode(10, 30, 280, 420);
        panel.setAutoClose(false);
        panel.setClickOutsideClose(false);

        var tabContainer = new UIElement();
        tabContainer.layout(l -> l.widthPercent(100).flex(1).flexDirection(FlexDirection.COLUMN));
        tabContainer.setId("projectionTabContainer");

        var tabBar = new UIElement();
        tabBar.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(1));
        tabBar.setId("projectionTabBar");

        var statusTab = new Button();
        statusTab.setText(Component.translatable("ebe.projection.status"));
        statusTab.layout(l -> l.flex(1).height(16));
        statusTab.textStyle(ts -> ts.fontSize(9).textShadow(false));
        statusTab.setId("projTabStatus");
        statusTab.setOnClick(e -> switchProjectionTab(0));
        tabBar.addChild(statusTab);

        var placeTab = new Button();
        placeTab.setText(Component.translatable("ebe.projection.place_tab"));
        placeTab.layout(l -> l.flex(1).height(16));
        placeTab.textStyle(ts -> ts.fontSize(9).textShadow(false));
        placeTab.setId("projTabPlace");
        placeTab.setOnClick(e -> switchProjectionTab(1));
        tabBar.addChild(placeTab);

        var adjustTab = new Button();
        adjustTab.setText(Component.translatable("ebe.projection.adjust_tab"));
        adjustTab.layout(l -> l.flex(1).height(16));
        adjustTab.textStyle(ts -> ts.fontSize(9).textShadow(false));
        adjustTab.setId("projTabAdjust");
        adjustTab.setOnClick(e -> switchProjectionTab(2));
        tabBar.addChild(adjustTab);

        tabContainer.addChild(tabBar);

        var contentArea = new UIElement();
        contentArea.layout(l -> l.widthPercent(100).flex(1));
        contentArea.setId("projectionTabContent");
        tabContainer.addChild(contentArea);

        panel.addContent(tabContainer);
        panel.addButton(new Button()
                .setOnClick(e -> { panel.close(); projectionPanelVisible = false; })
                .setText("ebe.history.dialog.cancel")
                .addClass("__cancel-button__"));
        panel.show(rootElement);

        projectionPanel = panel.overlay;
        projectionPanelVisible = true;
        switchProjectionTab(0);
    }

    private static int currentProjectionTab = 0;

    private static void switchProjectionTab(int index) {
        currentProjectionTab = index;
        var contentArea = findById(rootElement, "projectionTabContent");
        if (contentArea == null) return;
        contentArea.clearAllChildren();

        for (int i = 0; i < 3; i++) {
            int idx = i;
            var tabBtn = findById(rootElement, switch (idx) {
                case 0 -> "projTabStatus";
                case 1 -> "projTabPlace";
                default -> "projTabAdjust";
            });
            if (tabBtn != null) {
                tabBtn.style(s -> s.background(idx == index ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            }
        }

        switch (index) {
            case 0 -> contentArea.addChild(createProjectionStatusTab());
            case 1 -> contentArea.addChild(createProjectionPlaceTab());
            case 2 -> contentArea.addChild(createProjectionAdjustTab());
        }
    }

    private static UIElement createProjectionStatusTab() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).flexDirection(FlexDirection.COLUMN).gapAll(4));

        var proj = ProjectionManager.getProjection();

        if (proj == null) {
            var emptyLabel = new Label();
            emptyLabel.setText(Component.translatable("ebe.projection.no_projection"));
            emptyLabel.textStyle(ts -> ts.textColor(0xFF888888).fontSize(10).textShadow(false));
            container.addChild(emptyLabel);

            var selectBtn = new Button();
            selectBtn.setText(Component.translatable("ebe.projection.select_projection"));
            selectBtn.layout(l -> l.widthPercent(100).height(18));
            selectBtn.setOnClick(e -> {
                if (session != null && session.getModel() != null) {
                    ProjectionManager.selectProjection(session.getModel(), session.getComputedProjection());
                    switchProjectionTab(0);
                }
            });
            container.addChild(selectBtn);
            return container;
        }

        var statusRow = new UIElement();
        statusRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        var statusLabel = new Label();
        boolean loaded = ProjectionManager.isProjectionLoaded();
        statusLabel.setText(Component.translatable(loaded ? "ebe.projection.loaded" : "ebe.projection.unloaded"));
        statusLabel.textStyle(ts -> ts.textColor(loaded ? 0xFF55FF55 : 0xFFFF5555).fontSize(10).textShadow(false));
        statusRow.addChild(statusLabel);
        container.addChild(statusRow);

        var nameLabel = new Label();
        var projName = session != null ? session.getCurrentName() : "Unknown";
        nameLabel.setText(Component.literal(projName));
        nameLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(10).textShadow(false));
        container.addChild(nameLabel);

        var btnRow = new UIElement();
        btnRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4));

        var selectBtn = new Button();
        selectBtn.setText(Component.translatable("ebe.projection.select_projection"));
        selectBtn.layout(l -> l.flex(1).height(16));
        selectBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
        selectBtn.setOnClick(e -> {
            if (session != null && session.getModel() != null) {
                ProjectionManager.selectProjection(session.getModel(), session.getComputedProjection());
                switchProjectionTab(0);
            }
        });
        btnRow.addChild(selectBtn);

        if (loaded) {
            var unloadBtn = new Button();
            unloadBtn.setText(Component.translatable("ebe.projection.unload_projection"));
            unloadBtn.layout(l -> l.flex(1).height(16));
            unloadBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
            unloadBtn.setOnClick(e -> {
                ProjectionManager.unloadProjection();
                switchProjectionTab(0);
            });
            btnRow.addChild(unloadBtn);
        } else {
            var loadBtn = new Button();
            loadBtn.setText(Component.translatable("ebe.projection.load_projection"));
            loadBtn.layout(l -> l.flex(1).height(16));
            loadBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
            loadBtn.setOnClick(e -> {
                ProjectionManager.loadProjection();
                switchProjectionTab(0);
            });
            btnRow.addChild(loadBtn);
        }

        container.addChild(btnRow);

        var dimLabel = new Label();
        var mc = Minecraft.getInstance();
        var level = mc.level;
        String dimName = level != null ? level.dimension().location().toString() : "N/A";
        dimLabel.setText(Component.translatable("ebe.projection.dimension").append(Component.literal(": " + dimName)));
        dimLabel.textStyle(ts -> ts.fontSize(9).textColor(0xFFAAAAAA).textShadow(false));
        container.addChild(dimLabel);

        var coordLabel = new Label();
        coordLabel.setText(Component.translatable("ebe.projection.coordinates").append(Component.literal(
                ": (" + proj.getMinX() + "," + proj.getMinY() + "," + proj.getMinZ() + ") - (" +
                proj.getMaxX() + "," + proj.getMaxY() + "," + proj.getMaxZ() + ")")));
        coordLabel.textStyle(ts -> ts.fontSize(9).textColor(0xFFAAAAAA).textShadow(false));
        container.addChild(coordLabel);

        ProjectionManager.calculateProgress();
        var progressLabel = new Label();
        progressLabel.setId("projProgressLabel");
        progressLabel.setText(Component.translatable("ebe.projection.blocks_placed",
                ProjectionManager.getProgressPlaced(), ProjectionManager.getProgressTotal()));
        progressLabel.textStyle(ts -> ts.fontSize(9).textColor(0xFFDDDDDD).textShadow(false));
        container.addChild(progressLabel);

        if (loaded && Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative()) {
            var placeAllBtn = new Button();
            placeAllBtn.setText(Component.translatable("ebe.projection.place_all"));
            placeAllBtn.layout(l -> l.widthPercent(100).height(18));
            placeAllBtn.setOnClick(e -> ProjectionManager.placeAll());
            container.addChild(placeAllBtn);
        }

        return container;
    }

    private static UIElement createProjectionPlaceTab() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).flexDirection(FlexDirection.COLUMN).gapAll(4));

        if (ProjectionManager.getProjection() == null) {
            var emptyLabel = new Label();
            emptyLabel.setText(Component.translatable("ebe.projection.no_projection"));
            emptyLabel.textStyle(ts -> ts.textColor(0xFF888888).fontSize(10).textShadow(false));
            container.addChild(emptyLabel);
            return container;
        }

        var placeModeLabel = new Label();
        placeModeLabel.setText(Component.translatable("ebe.projection.place_tab"));
        placeModeLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(10).textShadow(false));
        container.addChild(placeModeLabel);

        var playerBtn = new Button();
        playerBtn.setText(Component.translatable("ebe.projection.place_at_player"));
        playerBtn.layout(l -> l.widthPercent(100).height(16));
        playerBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
        playerBtn.setOnClick(e -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null) {
                ProjectionManager.setProjectionOrigin(mc.player.blockPosition());
                switchProjectionTab(currentProjectionTab);
            }
        });
        container.addChild(playerBtn);

        var customLabel = new Label();
        customLabel.setText(Component.translatable("ebe.projection.place_custom"));
        customLabel.textStyle(ts -> ts.fontSize(9).textShadow(false));
        container.addChild(customLabel);

        var origin = ProjectionManager.getProjectionOrigin();

        var xRow = buildCoordRow("X", origin.getX(), (val) -> {
            ProjectionManager.setProjectionOrigin(new BlockPos(val, ProjectionManager.getProjectionOrigin().getY(), ProjectionManager.getProjectionOrigin().getZ()));
            switchProjectionTab(currentProjectionTab);
        });
        container.addChild(xRow);

        var yRow = buildCoordRow("Y", origin.getY(), (val) -> {
            ProjectionManager.setProjectionOrigin(new BlockPos(ProjectionManager.getProjectionOrigin().getX(), val, ProjectionManager.getProjectionOrigin().getZ()));
            switchProjectionTab(currentProjectionTab);
        });
        container.addChild(yRow);

        var zRow = buildCoordRow("Z", origin.getZ(), (val) -> {
            ProjectionManager.setProjectionOrigin(new BlockPos(ProjectionManager.getProjectionOrigin().getX(), ProjectionManager.getProjectionOrigin().getY(), val));
            switchProjectionTab(currentProjectionTab);
        });
        container.addChild(zRow);

        var centerLabel = new Label();
        centerLabel.setText(Component.translatable("ebe.projection.center_point"));
        centerLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(10).textShadow(false));
        container.addChild(centerLabel);

        var proj = ProjectionManager.getProjection();
        String[] cornerNames = {"ebe.projection.center_corner_tl", "ebe.projection.center_corner_tr", "ebe.projection.center_corner_bl", "ebe.projection.center_corner_br"};
        BlockPos[] corners = proj != null ? new BlockPos[]{
                new BlockPos(proj.getMinX(), proj.getMinY(), proj.getMinZ()),
                new BlockPos(proj.getMaxX(), proj.getMinY(), proj.getMinZ()),
                new BlockPos(proj.getMinX(), proj.getMaxY(), proj.getMaxZ()),
                new BlockPos(proj.getMaxX(), proj.getMaxY(), proj.getMaxZ())
        } : new BlockPos[]{BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO};

        for (int i = 0; i < 4; i++) {
            var cornerBtn = new Button();
            int finalI = i;
            cornerBtn.setText(Component.translatable(cornerNames[i]));
            cornerBtn.layout(l -> l.widthPercent(100).height(16));
            cornerBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
            cornerBtn.setOnClick(e -> {
                if (proj != null) ProjectionManager.setProjectionCenter(corners[finalI]);
                switchProjectionTab(currentProjectionTab);
            });
            container.addChild(cornerBtn);
        }

        var customCenterLabel = new Label();
        customCenterLabel.setText(Component.translatable("ebe.projection.center_custom"));
        customCenterLabel.textStyle(ts -> ts.fontSize(9).textShadow(false));
        container.addChild(customCenterLabel);

        if (proj != null) {
            var cp = proj.getCenterPoint();
            var cxRow = buildCoordRow("CX", cp.getX(), (val) -> {
                ProjectionManager.setProjectionCenter(new BlockPos(val, cp.getY(), cp.getZ()));
                switchProjectionTab(currentProjectionTab);
            });
            container.addChild(cxRow);
            var cyRow = buildCoordRow("CY", cp.getY(), (val) -> {
                ProjectionManager.setProjectionCenter(new BlockPos(cp.getX(), val, cp.getZ()));
                switchProjectionTab(currentProjectionTab);
            });
            container.addChild(cyRow);
            var czRow = buildCoordRow("CZ", cp.getZ(), (val) -> {
                ProjectionManager.setProjectionCenter(new BlockPos(cp.getX(), cp.getY(), val));
                switchProjectionTab(currentProjectionTab);
            });
            container.addChild(czRow);
        }

        var sep = new UIElement();
        sep.layout(l -> l.widthPercent(100).height(4));
        container.addChild(sep);

        var placeBtn = new Button();
        placeBtn.setText(Component.translatable("ebe.projection.place_tab"));
        placeBtn.layout(l -> l.widthPercent(100).height(20));
        placeBtn.textStyle(ts -> ts.fontSize(10).textShadow(false));
        placeBtn.setOnClick(e -> {
            if (session != null && session.getModel() != null) {
                ProjectionManager.selectProjection(session.getModel(), session.getComputedProjection());
                ProjectionManager.loadProjection();
                switchProjectionTab(0);
            }
        });
        container.addChild(placeBtn);

        return container;
    }

    private static UIElement createProjectionAdjustTab() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).flexDirection(FlexDirection.COLUMN).gapAll(4));

        var proj = ProjectionManager.getProjection();
        if (proj == null) {
            var emptyLabel = new Label();
            emptyLabel.setText(Component.translatable("ebe.projection.no_projection"));
            emptyLabel.textStyle(ts -> ts.textColor(0xFF888888).fontSize(10).textShadow(false));
            container.addChild(emptyLabel);
            return container;
        }

        var posLabel = new Label();
        posLabel.setText(Component.translatable("ebe.projection.position"));
        posLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(10).textShadow(false));
        container.addChild(posLabel);

        var shiftHint = new Label();
        shiftHint.setText(Component.translatable("ebe.projection.shift_hint"));
        shiftHint.textStyle(ts -> ts.fontSize(7).textColor(0xFF888888).textShadow(false));
        container.addChild(shiftHint);

        var origin = ProjectionManager.getProjectionOrigin();
        var xRow = buildCoordRow("X", origin.getX(), (val) -> {
            ProjectionManager.setProjectionOrigin(new BlockPos(val, origin.getY(), origin.getZ()));
            switchProjectionTab(2);
        });
        container.addChild(xRow);
        var yRow = buildCoordRow("Y", origin.getY(), (val) -> {
            ProjectionManager.setProjectionOrigin(new BlockPos(origin.getX(), val, origin.getZ()));
            switchProjectionTab(2);
        });
        container.addChild(yRow);
        var zRow = buildCoordRow("Z", origin.getZ(), (val) -> {
            ProjectionManager.setProjectionOrigin(new BlockPos(origin.getX(), origin.getY(), val));
            switchProjectionTab(2);
        });
        container.addChild(zRow);

        var rotLabel = new Label();
        rotLabel.setText(Component.translatable("ebe.projection.rotation"));
        rotLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(10).textShadow(false));
        container.addChild(rotLabel);

        var facingLabel = new Label();
        facingLabel.setId("projFacingLabel");
        facingLabel.setText(Component.translatable("ebe.projection.facing").append(Component.literal(": " + proj.getFacing().getName())));
        facingLabel.textStyle(ts -> ts.fontSize(9).textColor(0xFFAAAAAA).textShadow(false));
        container.addChild(facingLabel);

        var rotBtnRow = new UIElement();
        rotBtnRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));

        var ccwBtn = new Button();
        ccwBtn.setText(Component.translatable("ebe.projection.rotate_ccw"));
        ccwBtn.layout(l -> l.flex(1).height(16));
        ccwBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
        ccwBtn.setOnClick(e -> { ProjectionManager.rotateCounterClockwise90(); switchProjectionTab(2); });
        rotBtnRow.addChild(ccwBtn);

        var cwBtn = new Button();
        cwBtn.setText(Component.translatable("ebe.projection.rotate_cw"));
        cwBtn.layout(l -> l.flex(1).height(16));
        cwBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
        cwBtn.setOnClick(e -> { ProjectionManager.rotateClockwise90(); switchProjectionTab(2); });
        rotBtnRow.addChild(cwBtn);

        var r180Btn = new Button();
        r180Btn.setText(Component.translatable("ebe.projection.rotate_180"));
        r180Btn.layout(l -> l.flex(1).height(16));
        r180Btn.textStyle(ts -> ts.fontSize(8).textShadow(false));
        r180Btn.setOnClick(e -> { ProjectionManager.rotate180(); switchProjectionTab(2); });
        rotBtnRow.addChild(r180Btn);

        container.addChild(rotBtnRow);

        var resetRotBtn = new Button();
        resetRotBtn.setText(Component.translatable("ebe.projection.reset_rotation"));
        resetRotBtn.layout(l -> l.widthPercent(100).height(16));
        resetRotBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
        resetRotBtn.setOnClick(e -> { ProjectionManager.resetTransform(); switchProjectionTab(2); });
        container.addChild(resetRotBtn);

        var mirrorLabel = new Label();
        mirrorLabel.setText(Component.translatable("ebe.projection.mirror"));
        mirrorLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(10).textShadow(false));
        container.addChild(mirrorLabel);

        var mirrorRow = new UIElement();
        mirrorRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));

        var lrBtn = new Button();
        lrBtn.setText(Component.translatable("ebe.projection.mirror_lr"));
        lrBtn.layout(l -> l.flex(1).height(16));
        lrBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
        lrBtn.setOnClick(e -> { ProjectionManager.toggleMirrorLeftRight(); switchProjectionTab(2); });
        mirrorRow.addChild(lrBtn);

        var fbBtn = new Button();
        fbBtn.setText(Component.translatable("ebe.projection.mirror_fb"));
        fbBtn.layout(l -> l.flex(1).height(16));
        fbBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
        fbBtn.setOnClick(e -> { ProjectionManager.toggleMirrorFrontBack(); switchProjectionTab(2); });
        mirrorRow.addChild(fbBtn);

        container.addChild(mirrorRow);

        var centerLabel = new Label();
        centerLabel.setText(Component.translatable("ebe.projection.center"));
        centerLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(10).textShadow(false));
        container.addChild(centerLabel);

        var cp = proj.getCenterPoint();
        var cxRow = buildCoordRow("CX", cp.getX(), (val) -> {
            ProjectionManager.setProjectionCenter(new BlockPos(val, cp.getY(), cp.getZ()));
            switchProjectionTab(2);
        });
        container.addChild(cxRow);
        var cyRow = buildCoordRow("CY", cp.getY(), (val) -> {
            ProjectionManager.setProjectionCenter(new BlockPos(cp.getX(), val, cp.getZ()));
            switchProjectionTab(2);
        });
        container.addChild(cyRow);
        var czRow = buildCoordRow("CZ", cp.getZ(), (val) -> {
            ProjectionManager.setProjectionCenter(new BlockPos(cp.getX(), cp.getY(), val));
            switchProjectionTab(2);
        });
        container.addChild(czRow);

        return container;
    }

    private static UIElement buildCoordRow(String axis, int value, java.util.function.IntConsumer onChange) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(2));

        var axisLabel = new Label();
        axisLabel.setText(Component.literal(axis + ":"));
        axisLabel.textStyle(ts -> ts.fontSize(9).textColor(0xFFAAAAAA).textShadow(false));
        axisLabel.layout(l -> l.width(20).flexShrink(0));
        row.addChild(axisLabel);

        var minusBtn = new Button();
        minusBtn.setText(Component.literal("-"));
        minusBtn.layout(l -> l.width(16).height(16).flexShrink(0));
        minusBtn.textStyle(ts -> ts.fontSize(9).textShadow(false));
        minusBtn.setOnClick(e -> {
            int step = UIElement.isShiftDown() ? 10 : 1;
            onChange.accept(value - step);
        });
        row.addChild(minusBtn);

        var valField = new TextField();
        valField.setText(String.valueOf(value), false);
        valField.layout(l -> l.flex(1).height(16));
        valField.setNumbersOnlyInt(-100000, 100000);
        valField.setTextResponder(newText -> {
            try { onChange.accept(Integer.parseInt(newText)); } catch (NumberFormatException ignored) {}
        });
        row.addChild(valField);

        var plusBtn = new Button();
        plusBtn.setText(Component.literal("+"));
        plusBtn.layout(l -> l.width(16).height(16).flexShrink(0));
        plusBtn.textStyle(ts -> ts.fontSize(9).textShadow(false));
        plusBtn.setOnClick(e -> {
            int step = UIElement.isShiftDown() ? 10 : 1;
            onChange.accept(value + step);
        });
        row.addChild(plusBtn);

        return row;
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

        viewport.addEventListener(UIEvents.LAYOUT_CHANGED, e -> {
            if (toolbarOffsetY == 0 && toolbarPanel != null) {
                float vpHeight = viewport.getSizeHeight();
                float tbHeight = toolbarPanel.getSizeHeight();
                toolbarOffsetY = Math.max(0, (vpHeight - tbHeight) / 2);
                toolbarPanel.layout(l -> l.left(toolbarOffsetX).top(toolbarOffsetY));
            }
        });

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
            leftCollapseBtn.setText(Component.literal(">"));
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
                .left(toolbarOffsetX).top(toolbarOffsetY)
                .flexDirection(FlexDirection.COLUMN).gapAll(2).paddingAll(2)
                .width(30));
        panel.style(s -> s.background(Sprites.BORDER).zIndex(90));
        panel.setId("toolbarPanel");

        var dragHandle = buildToolbarDragHandle();
        panel.addChild(dragHandle);

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
        modeSep.setId("toolbarModeSep");
        panel.addChild(modeSep);

        var modeBtn = buildModeToggleButton();
        panel.addChild(modeBtn);

        var modeSep2 = new UIElement();
        modeSep2.layout(l -> l.widthPercent(100).height(1));
        modeSep2.style(s -> s.background(Sprites.RECT_DARK));
        modeSep2.setId("toolbarResetSep");
        panel.addChild(modeSep2);

        var resetCamBtn = new UIElement();
        resetCamBtn.layout(l -> l.width(26).height(26).alignItems(AlignItems.CENTER).justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER));
        resetCamBtn.style(s -> s.background(Sprites.RECT_RD));
        var resetCamIcon = new UIElement();
        resetCamIcon.layout(l -> l.width(20).height(20));
        resetCamIcon.style(s -> s.backgroundTexture(EditorIcons.HOME));
        resetCamBtn.addChild(resetCamIcon);
        resetCamBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) ViewportFactory.resetCamera();
        });
        registerTooltip(resetCamBtn, Component.translatable("ebe.editor.reset_camera"));
        resetCamBtn.setId("toolbarResetBtn");
        panel.addChild(resetCamBtn);

        highlightCurrentTool();
        return panel;
    }

    private static UIElement buildToolbarDragHandle() {
        var handle = new UIElement();
        handle.layout(l -> l.widthPercent(100).height(6));
        handle.style(s -> s.background(Sprites.RECT_DARK));
        handle.setId("toolbarDragHandle");

        var dots = new Label();
        dots.setText(Component.literal("···"));
        dots.textStyle(ts -> ts.fontSize(7).textColor(0xFF888888).textShadow(false));
        handle.addChild(dots);

        handle.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                toolbarDragging = true;
                toolbarDragStartX = e.x;
                toolbarDragStartY = e.y;
                toolbarDragOriginX = toolbarOffsetX;
                toolbarDragOriginY = toolbarOffsetY;
                e.stopImmediatePropagation();
            }
        });
        return handle;
    }

    private static UIElement buildToolbarCollapseBtn() {
        var btn = new UIElement();
        btn.layout(l -> l.width(26).height(10));
        btn.style(s -> s.background(Sprites.RECT_DARK));
        btn.setId("toolbarCollapseBtn");

        var inner = new Label();
        inner.setText(Component.literal(toolbarExpanded ? "<" : ">"));
        inner.textStyle(ts -> ts.textColor(0xFFAAAAAA).textShadow(false).fontSize(8));
        btn.addChild(inner);

        btn.addEventListener(UIEvents.MOUSE_DOWN, e -> toggleToolbar());
        return btn;
    }

    private static void toggleToolbar() {
        toolbarExpanded = !toolbarExpanded;
        toolbarButtons.values().forEach(b -> b.setDisplay(toolbarExpanded));
        for (var id : new String[]{"toolbarModeSep", "toolbar_mode_btn", "toolbarResetSep", "toolbarResetBtn"}) {
            var el = findById(rootElement, id);
            if (el != null) el.setDisplay(toolbarExpanded);
        }
        var btn = findById(rootElement, "toolbarCollapseBtn");
        if (btn != null) {
            var inner = btn.getChildren().stream()
                    .filter(c -> c instanceof Label)
                    .map(c -> (Label) c)
                    .findFirst().orElse(null);
            if (inner != null) {
                inner.setText(Component.literal(toolbarExpanded ? "<" : ">"));
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

        var icon = new UIElement();
        icon.setId("modeBtnIcon");
        icon.layout(l -> l.width(20).height(20));
        icon.style(s -> s.backgroundTexture(EditorIcons.VIEW_MODE));
        btn.addChild(icon);

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
        var modeIcon = findById(btn, "modeBtnIcon");
        if (modeIcon != null) {
            modeIcon.style(s -> s.backgroundTexture(mode == EditorMode.VIEW ? EditorIcons.VIEW_MODE : EditorIcons.EDIT_MODE));
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
        var model = session.getModel();
        var snapshots = entry.getSnapshots();
        var reversed = new ArrayList<Object[]>();
        for (int i = snapshots.length - 1; i >= 0; i--) {
            var s = snapshots[i];
            int x = (int) s[0], y = (int) s[1], z = (int) s[2];
            model.setBlockAtWithNbt(x, y, z, s[3], historyNbt(s, 5));
            reversed.add(new Object[]{s[0], s[1], s[2], s[4], s[3], historyNbt(s, 6), historyNbt(s, 5)});
        }
        if (entry.isLayerChange()) {
            model.restoreLayerState(entry.getBeforeLayerState());
        }
        session.markDirty();
        if (entry.isLayerChange()) {
            refreshLayersList();
            ViewportFactory.refreshFromModel(model);
        } else {
            ViewportFactory.applyBlockDeltasFromModel(reversed.toArray(Object[][]::new));
        }
        refreshMaterialList();
        refreshHistoryList();
    }

    private static void redo() {
        var entry = history.redo();
        if (entry == null) return;
        var model = session.getModel();
        for (var s : entry.getSnapshots()) {
            int x = (int) s[0], y = (int) s[1], z = (int) s[2];
            model.setBlockAtWithNbt(x, y, z, s[4], historyNbt(s, 6));
        }
        if (entry.isLayerChange()) {
            model.restoreLayerState(entry.getAfterLayerState());
        }
        session.markDirty();
        if (entry.isLayerChange()) {
            refreshLayersList();
            ViewportFactory.refreshFromModel(model);
        } else {
            ViewportFactory.applyBlockDeltasFromModel(entry.getSnapshots());
        }
        refreshMaterialList();
        refreshHistoryList();
    }

    private static net.minecraft.nbt.CompoundTag historyNbt(Object[] snapshot, int index) {
        if (snapshot == null || snapshot.length <= index) return null;
        return snapshot[index] instanceof net.minecraft.nbt.CompoundTag tag ? tag.copy() : null;
    }

    private static Object[] nbtSnapshot(BuildingModel model, int x, int y, int z, Object oldState, Object newState,
                                        net.minecraft.nbt.CompoundTag newNbt) {
        return new Object[]{x, y, z, oldState, newState, model.copyBlockEntityNbt(x, y, z),
                newNbt == null ? null : newNbt.copy()};
    }

    private static void goToHistoryEntry(int displayIdx) {
        int count = history.goToEntryCount(displayIdx);
        if (count <= 0) return;
        var undone = history.popUndoEntries(count);
        var model = session.getModel();
        var allReversed = new ArrayList<Object[]>();
        boolean needsFullRefresh = false;
        for (var entry : undone) {
            var snapshots = entry.getSnapshots();
            for (int i = snapshots.length - 1; i >= 0; i--) {
                var s = snapshots[i];
                int x = (int) s[0], y = (int) s[1], z = (int) s[2];
                model.setBlockAtWithNbt(x, y, z, s[3], historyNbt(s, 5));
                allReversed.add(new Object[]{s[0], s[1], s[2], s[4], s[3], historyNbt(s, 6), historyNbt(s, 5)});
            }
            if (entry.isLayerChange()) {
                model.restoreLayerState(entry.getBeforeLayerState());
                needsFullRefresh = true;
            }
        }
        session.markDirty();
        if (needsFullRefresh) {
            refreshLayersList();
            ViewportFactory.refreshFromModel(model);
        } else {
            ViewportFactory.applyBlockDeltasFromModel(allReversed.toArray(Object[][]::new));
        }
        refreshMaterialList();
        refreshHistoryList();
    }

    private static void rollbackToTag(com.l1ght.ebe.editor.history.HistoryManager.VersionTag tag) {
        int tagEntryIndex = history.findEntryIndexById(tag.entryId());
        if (tagEntryIndex < 0) return;
        int count = history.undoSize() - 1 - tagEntryIndex;
        if (count <= 0) return;
        var undone = history.popUndoEntries(count);
        var model = session.getModel();
        var allReversed = new ArrayList<Object[]>();
        boolean needsFullRefresh = false;
        for (var entry : undone) {
            var snapshots = entry.getSnapshots();
            for (int i = snapshots.length - 1; i >= 0; i--) {
                var s = snapshots[i];
                int x = (int) s[0], y = (int) s[1], z = (int) s[2];
                model.setBlockAtWithNbt(x, y, z, s[3], historyNbt(s, 5));
                allReversed.add(new Object[]{s[0], s[1], s[2], s[4], s[3], historyNbt(s, 6), historyNbt(s, 5)});
            }
            if (entry.isLayerChange()) {
                model.restoreLayerState(entry.getBeforeLayerState());
                needsFullRefresh = true;
            }
        }
        session.markDirty();
        if (needsFullRefresh) {
            refreshLayersList();
            ViewportFactory.refreshFromModel(model);
        } else {
            ViewportFactory.applyBlockDeltasFromModel(allReversed.toArray(Object[][]::new));
        }
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
        tabView.layout(l -> l.flexDirection(FlexDirection.COLUMN).flex(1).widthPercent(100).minHeight(0));
        var header = tabView.tabHeaderContainer;
        var tvContent = tabView.tabContentContainer;
        tabView.removeChild(header);
        tabView.removeChild(tvContent);
        tabView.addChild(header);
        tabView.addChild(tvContent);
        tvContent.layout(l -> l.flex(1).widthPercent(100).minHeight(0).paddingAll(5));
        return tabView;
    }

    private static UIElement wrapInScroller(UIElement content) {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1).minHeight(0));
        scroller.viewPort(vp -> vp.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN)));
        scroller.viewContainer(vc -> vc.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN)));
        scroller.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL).verticalScrollDisplay(ScrollDisplay.AUTO));
        scroller.addScrollViewChild(content);
        return scroller;
    }

    private static UIElement buildLeftPanel() {
        var panel = new UIElement();
        panel.layout(l -> l.widthPercent(leftPanelWidthPercent).minWidth(80).maxWidth(500).heightPercent(100).flexDirection(FlexDirection.COLUMN));
        panel.style(s -> s.background(Sprites.RECT_DARK));
        panel.setId("leftPanel");

        var tabView = createTopTabView();
        var filesTab = new Tab();
        filesTab.setText(Component.translatable("ebe.editor.panel.files"));
        tabView.addTab(filesTab, wrapInScroller(createFilesContent()));

        var layersTab = new Tab();
        layersTab.setText(Component.translatable("ebe.editor.panel.layers"));
        tabView.addTab(layersTab, wrapInScroller(createLayersContent()));

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
        tabView.addTab(propsTab, wrapInScroller(createPropertiesContent()));

        var matsTab = new Tab();
        matsTab.setText(Component.translatable("ebe.editor.panel.materials"));
        tabView.addTab(matsTab, wrapInScroller(createMaterialsContent()));

        var histTab = new Tab();
        histTab.setText(Component.translatable("ebe.editor.panel.history"));
        tabView.addTab(histTab, wrapInScroller(createHistoryContent()));

        var displayTab = new Tab();
        displayTab.setText(Component.translatable("ebe.editor.panel.display"));
        tabView.addTab(displayTab, wrapInScroller(buildDisplayFilterTab()));

        var heatmapTab = new Tab();
        heatmapTab.setText(Component.translatable("ebe.editor.panel.heatmap"));
        tabView.addTab(heatmapTab, wrapInScroller(createHeatmapContent()));

        var printerTab = new Tab();
        printerTab.setText(Component.translatable("ebe.editor.panel.printer"));
        tabView.addTab(printerTab, wrapInScroller(createPrinterContent()));

        var nbtTemplatesTab = new Tab();
        nbtTemplatesTab.setText(Component.translatable("ebe.nbt.templates"));
        tabView.addTab(nbtTemplatesTab, wrapInScroller(createNbtTemplatesContent()));

        panel.addChild(tabView);
        return panel;
    }

    private static UIElement createFilesContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));

        var actionRow = new UIElement();
        actionRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));

        var openFolderBtn = new Button();
        openFolderBtn.setText(Component.translatable("ebe.editor.open_folder"));
        openFolderBtn.layout(l -> l.flex(1).height(18));
        openFolderBtn.setOnClick(e -> ImportDialog.openSchematicFolder());
        actionRow.addChild(openFolderBtn);

        var importBtn = new Button();
        importBtn.setText(Component.translatable("ebe.editor.import"));
        importBtn.layout(l -> l.flex(1).height(18));
        importBtn.setOnClick(e -> ImportDialog.showImport(rootElement, file -> {
            onFileSelected(file);
            refreshFileList();
        }));
        actionRow.addChild(importBtn);

        var refreshBtn = new Button();
        refreshBtn.setText(Component.translatable("ebe.editor.refresh_short"));
        refreshBtn.layout(l -> l.width(22).height(18));
        refreshBtn.setOnClick(e -> refreshFileList());
        registerTooltip(refreshBtn, Component.translatable("ebe.editor.refresh"));
        actionRow.addChild(refreshBtn);
        container.addChild(actionRow);

        var hint = new Label();
        hint.setText(Component.translatable("ebe.editor.files.hot_reload_hint"));
        hint.textStyle(ts -> ts.textColor(0xFF888888).fontSize(8).textShadow(false)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        container.addChild(hint);

        fileListContainer = new UIElement();
        fileListContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.addScrollViewChild(fileListContainer);
        container.addChild(scroller);
        refreshFileList();
        return container;
    }

    private static UIElement createNbtTemplatesContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).flexDirection(FlexDirection.COLUMN).gapAll(3));

        var saveBtn = new Button();
        saveBtn.setText(Component.translatable("ebe.nbt.template.save_current"));
        saveBtn.layout(l -> l.widthPercent(100).height(20));
        saveBtn.setOnClick(e -> {
            var tag = session.getModel().getBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ());
            if (tag == null) return;
            var dialog = Dialog.stringEditorDialog(Component.translatable("ebe.nbt.template.name").getString(), "template", s -> !s.isBlank(), name -> {
                NbtTemplateManager.saveTemplate(name, tag);
                rebuildRightPanel();
            });
            dialog.overlay.layout(l -> l.width(220));
            dialog.show(rootElement);
        });
        container.addChild(saveBtn);

        var diffBtn = new Button();
        diffBtn.setText(Component.translatable("ebe.nbt.template.diff_selected"));
        diffBtn.layout(l -> l.widthPercent(100).height(20));
        diffBtn.setOnClick(e -> showNbtTemplateDiffDialog());
        container.addChild(diffBtn);

        for (var entry : NbtTemplateManager.all().entrySet()) {
            var row = new UIElement();
            row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(2));
            var name = new Label();
            name.setText(Component.literal(entry.getKey()));
            name.layout(l -> l.flex(1));
            name.textStyle(ts -> ts.textColor(0xFFFFFFFF).fontSize(8).textShadow(false));
            row.addChild(name);

            var apply = new Button();
            apply.setText(Component.translatable("ebe.nbt.template.apply"));
            apply.layout(l -> l.width(48).height(16));
            apply.setOnClick(e -> {
                var tag = NbtTemplateManager.get(entry.getKey());
                if (tag != null) {
                    session.getModel().setBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ(), tag);
                    session.markDirty();
                    refreshPropertiesPanel();
                }
            });
            row.addChild(apply);

            var del = new Button();
            del.setText(Component.literal("X"));
            del.layout(l -> l.width(20).height(16));
            del.setOnClick(e -> {
                NbtTemplateManager.delete(entry.getKey());
                rebuildRightPanel();
            });
            row.addChild(del);
            container.addChild(row);
        }
        return container;
    }

    private static void showNbtTemplateDiffDialog() {
        var current = session.getModel().getBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ());
        if (current == null) return;
        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.nbt.template.diff_title").getString());
        dialog.overlay.layout(l -> l.width(320));
        for (var entry : NbtTemplateManager.all().entrySet()) {
            var template = NbtTemplateManager.get(entry.getKey());
            var btn = new Button();
            btn.setText(Component.literal(entry.getKey()));
            btn.setOnClick(e -> {
                dialog.close();
                var result = new Dialog();
                result.setTitle(Component.translatable("ebe.nbt.template.diff_title_named", entry.getKey()).getString());
                result.overlay.layout(l -> l.width(320));
                var label = new Label();
                label.setText(Component.literal(diffNbtFields(template, current)));
                label.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(8).textShadow(false)
                        .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
                result.addContent(label);
                result.addButton(new Button().setText(Component.translatable("ebe.editor.close")).setOnClick(ev -> result.close()));
                result.show(rootElement);
            });
            dialog.addContent(btn);
        }
        dialog.addButton(new Button().setText(Component.translatable("ebe.editor.close")).setOnClick(e -> dialog.close()));
        dialog.show(rootElement);
    }

    private static String diffNbtFields(net.minecraft.nbt.CompoundTag base, net.minecraft.nbt.CompoundTag current) {
        if (base == null) return Component.translatable("ebe.nbt.template.parse_failed").getString();
        var lines = new StringBuilder();
        for (String key : current.getAllKeys()) {
            if (!base.contains(key)) lines.append("+ ").append(key).append(" = ").append(current.get(key)).append('\n');
            else if (!String.valueOf(base.get(key)).equals(String.valueOf(current.get(key)))) {
                lines.append("~ ").append(key).append(": ").append(base.get(key)).append(" -> ").append(current.get(key)).append('\n');
            }
        }
        for (String key : base.getAllKeys()) {
            if (!current.contains(key)) lines.append("- ").append(key).append(" = ").append(base.get(key)).append('\n');
        }
        return lines.isEmpty() ? Component.translatable("ebe.nbt.template.no_diff").getString() : lines.toString();
    }

    private static void rebuildRightPanel() {
        refreshPropertiesPanel();
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
                var before = session.getModel().captureLayerState();
                session.getModel().addLayer(Component.translatable("ebe.layer.default_name",
                        session.getModel().getLayers().size()).getString(), true, false);
                finishLayerEdit(before, com.l1ght.ebe.editor.history.HistoryActionType.LAYER_CREATE,
                        Component.translatable("ebe.layer.new").getString(), 1, false);
            }
        });
        registerTooltip(addBtn, Component.translatable("ebe.layer.new"));
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
    private static BuildingModel.LayerState renamingLayerBeforeState = null;

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
                var before = session.getModel().captureLayerState();
                layer.setVisible(!layer.isVisible());
                finishLayerEdit(before, com.l1ght.ebe.editor.history.HistoryActionType.LAYER_UPDATE,
                        layer.getName(), session.getModel().countBlocksInLayer(layer.getId()), true);
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
                var before = session.getModel().captureLayerState();
                layer.setLocked(!layer.isLocked());
                finishLayerEdit(before, com.l1ght.ebe.editor.history.HistoryActionType.LAYER_UPDATE,
                        layer.getName(), 1, false);
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
                        finishPendingLayerRename(layer);
                        renamingLayerId = null;
                        renamingLayerBeforeState = null;
                        refreshLayersList();
                    }
                });
                tf.addEventListener(UIEvents.BLUR, e2 -> {
                    finishPendingLayerRename(layer);
                    renamingLayerId = null;
                    renamingLayerBeforeState = null;
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
                        renamingLayerBeforeState = session.getModel().captureLayerState();
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
                var before = session.getModel().captureLayerState();
                session.getModel().removeLayer(layer.getId());
                finishLayerEdit(before, com.l1ght.ebe.editor.history.HistoryActionType.LAYER_DELETE,
                        layer.getName(), 1, true);
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
                var before = session.getModel().captureLayerState();
                int moved = assignSelectionToLayer(layer.getId());
                finishLayerEdit(before, com.l1ght.ebe.editor.history.HistoryActionType.LAYER_ASSIGN,
                        layer.getName(), moved, true);
            });
            row.addChild(moveToBtn);

            var mergeBtn = new UIElement();
            mergeBtn.layout(l -> l.width(14).height(14));
            mergeBtn.style(s -> s.backgroundTexture(EditorIcons.COPY));
            registerTooltip(mergeBtn, Component.translatable("ebe.layer.merge_into"));
            mergeBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                if (e.button == 0) showMergeLayerDialog(layer);
            });
            row.addChild(mergeBtn);

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
            var before = session.getModel().captureLayerState();
            var newLayer = session.getModel().addLayer(Component.translatable("ebe.layer.selection_name",
                    session.getModel().getLayers().size()).getString(), true, false);
            int moved = assignSelectionToLayer(newLayer.getId());
            finishLayerEdit(before, com.l1ght.ebe.editor.history.HistoryActionType.LAYER_CREATE,
                    newLayer.getName(), moved, true);
        });
        actionRow.addChild(createLayerBtn);

        layersListContainer.addChild(actionRow);
    }

    private static int assignSelectionToLayer(String layerId) {
        if (session == null || selection.isEmpty()) return 0;
        int moved = 0;
        for (var packed : selection.getAllPacked()) {
            int x = com.l1ght.ebe.editor.selection.SelectionManager.unpackX(packed);
            int y = com.l1ght.ebe.editor.selection.SelectionManager.unpackY(packed);
            int z = com.l1ght.ebe.editor.selection.SelectionManager.unpackZ(packed);
            if (session.getModel().assignBlockToLayer(x, y, z, layerId)) {
                moved++;
            }
        }
        return moved;
    }

    private static void finishPendingLayerRename(BuildingModel.Layer layer) {
        if (session == null || renamingLayerBeforeState == null || layer == null) return;
        finishLayerEdit(renamingLayerBeforeState,
                com.l1ght.ebe.editor.history.HistoryActionType.LAYER_UPDATE,
                layer.getName(), 1, false);
    }

    private static void finishLayerEdit(BuildingModel.LayerState before,
                                        com.l1ght.ebe.editor.history.HistoryActionType type,
                                        String label,
                                        int affected,
                                        boolean refreshViewport) {
        if (session == null || before == null) return;
        var after = session.getModel().captureLayerState();
        if (!before.equals(after)) {
            history.push(new com.l1ght.ebe.editor.history.HistoryEntry(
                    history.nextId(), type, before, after, label, Math.max(1, affected)));
            session.markDirty();
        }
        refreshLayersList();
        if (refreshViewport) {
            ViewportFactory.refreshFromModel(session.getModel());
        }
        refreshHistoryList();
        updateStatusBar();
    }

    private static void showMergeLayerDialog(BuildingModel.Layer sourceLayer) {
        if (session == null || sourceLayer == null || session.getModel().getLayers().size() <= 1) return;
        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.layer.merge_into_title", sourceLayer.getName()).getString());
        dialog.darkenBackground();

        var hint = new Label();
        hint.setText(Component.translatable("ebe.layer.merge_into_hint"));
        hint.textStyle(ts -> ts.fontSize(9).textColor(0xFFDDDDDD).textShadow(false)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        dialog.addContent(hint);

        for (var target : session.getModel().getLayers()) {
            if (target.getId().equals(sourceLayer.getId())) continue;
            var btn = new Button();
            btn.setText(Component.literal(target.getName()));
            btn.layout(l -> l.widthPercent(100).height(18));
            btn.textStyle(ts -> ts.fontSize(9).textShadow(false));
            btn.setOnClick(e -> {
                var before = session.getModel().captureLayerState();
                session.getModel().mergeLayerInto(sourceLayer.getId(), target.getId());
                finishLayerEdit(before, com.l1ght.ebe.editor.history.HistoryActionType.LAYER_MERGE,
                        sourceLayer.getName() + " -> " + target.getName(),
                        session.getModel().countBlocksInLayer(target.getId()), true);
                dialog.close();
            });
            dialog.addContent(btn);
        }

        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText("ebe.history.dialog.cancel")
                .addClass("__cancel-button__"));
        dialog.show(rootElement);
    }

    private static UIElement createMaterialsContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).flexDirection(FlexDirection.COLUMN).gapAll(2));
        container.setId("materialsContent");

        var header = new UIElement();
        header.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));

        var exportBtn = new UIElement();
        exportBtn.layout(l -> l.width(20).height(20));
        exportBtn.style(s -> s.backgroundTexture(EditorIcons.SAVE));
        exportBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) exportMaterialList(); });
        registerTooltip(exportBtn, Component.translatable("ebe.materials.export"));
        header.addChild(exportBtn);

        var compareBtn = new UIElement();
        compareBtn.layout(l -> l.width(20).height(20));
        compareBtn.style(s -> s.backgroundTexture(EditorIcons.SEARCH));
        compareBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) toggleInventoryCompare(); });
        registerTooltip(compareBtn, Component.translatable("ebe.materials.compare_inventory"));
        header.addChild(compareBtn);

        var diffBtn = new UIElement();
        diffBtn.layout(l -> l.width(20).height(20));
        diffBtn.style(s -> s.backgroundTexture(EditorIcons.DIFF));
        diffBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) showMaterialDiffDialog(); });
        registerTooltip(diffBtn, Component.translatable("ebe.materials.diff_calc"));
        header.addChild(diffBtn);

        var refreshBtn = new UIElement();
        refreshBtn.layout(l -> l.width(20).height(20));
        refreshBtn.style(s -> s.backgroundTexture(EditorIcons.REFRESH));
        refreshBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) refreshMaterialList(); });
        registerTooltip(refreshBtn, Component.translatable("ebe.editor.refresh"));
        header.addChild(refreshBtn);

        var totalLabel = new Label();
        totalLabel.setId("materialTotalLabel");
        totalLabel.textStyle(ts -> ts.fontSize(9).textColor(0xFFAAAAAA).textShadow(false));
        totalLabel.layout(l -> l.flex(1));
        header.addChild(totalLabel);

        container.addChild(header);

        materialListContainer = new UIElement();
        materialListContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(1));
        materialListContainer.setId("materialsList");

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.NEVER).horizontalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.addScrollViewChild(materialListContainer);
        container.addChild(scroller);

        updateMaterialList();
        return container;
    }

    private static UIElement createHistoryContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).flexDirection(FlexDirection.COLUMN).gapAll(2));
        container.setId("historyContent");

        var header = new UIElement();
        header.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).paddingHorizontal(2).gapAll(4));

        var undoBtn = new UIElement();
        undoBtn.layout(l -> l.width(20).height(20));
        undoBtn.style(s -> s.backgroundTexture(EditorIcons.UNDO));
        undoBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) undo(); });
        registerTooltip(undoBtn, Component.translatable("ebe.editor.undo"));
        header.addChild(undoBtn);

        var redoBtn = new UIElement();
        redoBtn.layout(l -> l.width(20).height(20));
        redoBtn.style(s -> s.backgroundTexture(EditorIcons.REDO));
        redoBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) redo(); });
        registerTooltip(redoBtn, Component.translatable("ebe.editor.redo"));
        header.addChild(redoBtn);

        var tagBtn = new UIElement();
        tagBtn.layout(l -> l.width(20).height(20));
        tagBtn.style(s -> s.backgroundTexture(EditorIcons.TAG));
        tagBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) showCreateVersionTagDialog(); });
        registerTooltip(tagBtn, Component.translatable("ebe.history.create_tag"));
        header.addChild(tagBtn);

        var branchBtn = new UIElement();
        branchBtn.layout(l -> l.width(20).height(20));
        branchBtn.style(s -> s.backgroundTexture(EditorIcons.BRANCH));
        branchBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) showBranchSwitchDialog(); });
        registerTooltip(branchBtn, Component.translatable("ebe.history.switch_branch"));
        header.addChild(branchBtn);

        var clearBtn = new UIElement();
        clearBtn.layout(l -> l.width(20).height(20));
        clearBtn.style(s -> s.backgroundTexture(EditorIcons.CLOSE));
        clearBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) { history.clear(session.getModel()); refreshHistoryList(); } });
        registerTooltip(clearBtn, Component.translatable("ebe.history.clear"));
        header.addChild(clearBtn);

        container.addChild(header);

        var branchLabel = new Label();
        branchLabel.setId("historyBranchLabel");
        branchLabel.layout(l -> l.widthPercent(100).paddingHorizontal(2));
        branchLabel.textStyle(ts -> ts.fontSize(9).textColor(0xFFAAAAFF).textShadow(false));
        branchLabel.setText(Component.translatable("ebe.history.branch_value", history.getCurrentBranch()));
        branchLabel.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) showBranchSwitchDialog(); });
        registerTooltip(branchLabel, Component.translatable("ebe.history.switch_branch"));
        container.addChild(branchLabel);

        historyListContainer = new UIElement();
        historyListContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(1));

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

    private static UIElement createHeatmapContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).flexDirection(FlexDirection.COLUMN).gapAll(2));
        container.setId("heatmapContent");

        var currentModeLabel = new Label();
        currentModeLabel.setId("heatmapCurrentMode");
        currentModeLabel.setText(Component.translatable(ViewportFactory.getHeatmapMode().getTranslationKey()));
        currentModeLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(9).textShadow(false));
        container.addChild(currentModeLabel);

        var modeLabel = new Label();
        modeLabel.setText(Component.translatable("ebe.heatmap.off"));
        modeLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9).textShadow(false));
        container.addChild(modeLabel);

        var modeRow = new UIElement();
        modeRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(3));
        modeRow.setId("heatmapModeRow");

        HeatmapMode[] modes = HeatmapMode.values();
        for (int i = 0; i < modes.length; i++) {
            final int idx = i;
            final HeatmapMode mode = modes[i];
            var btn = new Button();
            btn.setText(Component.translatable(mode.getTranslationKey()));
            btn.layout(l -> l.height(18).paddingHorizontal(4));
            btn.setId("heatmapBtn_" + i);
            btn.style(s -> s.background(idx == ViewportFactory.getHeatmapMode().ordinal() ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            btn.setOnClick(e -> {
                ViewportFactory.setHeatmapMode(mode);
                selectedHeatmapBtnId = "heatmapBtn_" + idx;
                updateHeatmapModeHighlight();
                updateHeatmapCurrentMode();
            });
            modeRow.addChild(btn);
        }
        container.addChild(modeRow);

        selectedHeatmapBtnId = "heatmapBtn_" + ViewportFactory.getHeatmapMode().ordinal();
        return container;
    }

    private static void updateHeatmapModeHighlight() {
        for (int i = 0; i < HeatmapMode.values().length; i++) {
            final String btnId = "heatmapBtn_" + i;
            var btn = UIUtils.findById(rightPanel, btnId);
            if (btn instanceof Button b) {
                b.style(s -> s.background(btnId.equals(selectedHeatmapBtnId) ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            }
        }
    }

    private static void updateHeatmapCurrentMode() {
        var label = UIUtils.findById(rightPanel, "heatmapCurrentMode");
        if (label instanceof Label l) {
            l.setText(Component.translatable(ViewportFactory.getHeatmapMode().getTranslationKey()));
        }
    }

    private static UIElement createPrinterContent() {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(4).flexDirection(FlexDirection.COLUMN).gapAll(2));
        container.setId("printerContent");

        var currentModeLabel = new Label();
        currentModeLabel.setId("printerCurrentMode");
        currentModeLabel.setText(Component.translatable(PrinterController.getMode().getTranslationKey()));
        currentModeLabel.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(9).textShadow(false));
        container.addChild(currentModeLabel);

        var modeRow = new UIElement();
        modeRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP).gapAll(3));
        modeRow.setId("printerModeRow");

        PrinterMode[] modes = PrinterMode.values();
        for (int i = 0; i < modes.length; i++) {
            final int idx = i;
            final PrinterMode mode = modes[i];
            var btn = new Button();
            btn.setText(Component.translatable(mode.getTranslationKey()));
            btn.layout(l -> l.height(18).paddingHorizontal(4));
            btn.setId("printerModeBtn_" + i);
            btn.style(s -> s.background(idx == PrinterController.getMode().ordinal() ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            btn.setOnClick(e -> {
                PrinterController.setMode(mode);
                selectedPrinterModeBtnId = "printerModeBtn_" + idx;
                updatePrinterModeHighlight();
                updatePrinterCurrentMode();
                updatePrinterToggleBtn();
            });
            modeRow.addChild(btn);
        }
        container.addChild(modeRow);

        selectedPrinterModeBtnId = "printerModeBtn_" + PrinterController.getMode().ordinal();

        var toggleBtn = new Button();
        toggleBtn.setId("printerToggleBtn");
        updatePrinterToggleText(toggleBtn);
        toggleBtn.layout(l -> l.widthPercent(100).height(22));
        toggleBtn.style(s -> s.background(PrinterController.isActive() ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
        toggleBtn.setOnClick(e -> {
            PrinterController.toggle();
            updatePrinterToggleBtn();
        });
        container.addChild(toggleBtn);

        var parallelLabel = new Label();
        parallelLabel.setText(Component.translatable("ebe.printer.parallelism")
                .append(Component.literal(": " + EBEClientConfig.printerParallelism.get())));
        parallelLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9).textShadow(false));
        container.addChild(parallelLabel);

        var parallelRow = new UIElement();
        parallelRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(3));
        var parallelMinus = new Button();
        parallelMinus.setText(Component.literal("-"));
        parallelMinus.layout(l -> l.flex(1).height(16));
        parallelMinus.setOnClick(e -> {
            int value = Math.max(1, EBEClientConfig.printerParallelism.get() - 1);
            EBEClientConfig.printerParallelism.set(value);
            EBEClientConfig.SPEC.save();
            parallelLabel.setText(Component.translatable("ebe.printer.parallelism").append(Component.literal(": " + value)));
        });
        parallelRow.addChild(parallelMinus);

        var parallelPlus = new Button();
        parallelPlus.setText(Component.literal("+"));
        parallelPlus.layout(l -> l.flex(1).height(16));
        parallelPlus.setOnClick(e -> {
            int value = Math.min(8, EBEClientConfig.printerParallelism.get() + 1);
            EBEClientConfig.printerParallelism.set(value);
            EBEClientConfig.SPEC.save();
            parallelLabel.setText(Component.translatable("ebe.printer.parallelism").append(Component.literal(": " + value)));
        });
        parallelRow.addChild(parallelPlus);
        container.addChild(parallelRow);

        var sourceLabel = new Label();
        sourceLabel.setId("printerSourceLabel");
        updatePrinterSourceLabel(sourceLabel);
        sourceLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9).textShadow(false));
        container.addChild(sourceLabel);

        var sourceRow = new UIElement();
        sourceRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(3));
        var selectSourceBtn = new Button();
        selectSourceBtn.setText(Component.translatable("ebe.printer.select_chest"));
        selectSourceBtn.layout(l -> l.flex(1).height(18));
        selectSourceBtn.setOnClick(e -> PrinterController.requestMaterialSourceSelection());
        sourceRow.addChild(selectSourceBtn);

        var clearSourceBtn = new Button();
        clearSourceBtn.setText(Component.translatable("ebe.printer.clear_chest"));
        clearSourceBtn.layout(l -> l.flex(1).height(18));
        clearSourceBtn.setOnClick(e -> {
            PrinterController.clearMaterialSource();
            updatePrinterSourceLabel(sourceLabel);
        });
        sourceRow.addChild(clearSourceBtn);
        container.addChild(sourceRow);

        var sourceRangeLabel = new Label();
        sourceRangeLabel.setText(Component.translatable("ebe.printer.material_source_range")
                .append(Component.literal(": " + formatPrinterRange(EBEClientConfig.printerMaterialSourceRange.get()))));
        sourceRangeLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9).textShadow(false));
        container.addChild(sourceRangeLabel);

        var sourceRangeRow = new UIElement();
        sourceRangeRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(3));
        var sourceRangeMinus = new Button();
        sourceRangeMinus.setText(Component.literal("-64"));
        sourceRangeMinus.layout(l -> l.flex(1).height(16));
        sourceRangeMinus.setOnClick(e -> {
            int current = EBEClientConfig.printerMaterialSourceRange.get();
            int value = current <= 0 ? 64 : Math.max(0, current - 64);
            EBEClientConfig.printerMaterialSourceRange.set(value);
            EBEClientConfig.SPEC.save();
            sourceRangeLabel.setText(Component.translatable("ebe.printer.material_source_range")
                    .append(Component.literal(": " + formatPrinterRange(value))));
        });
        sourceRangeRow.addChild(sourceRangeMinus);

        var sourceRangePlus = new Button();
        sourceRangePlus.setText(Component.literal("+64"));
        sourceRangePlus.layout(l -> l.flex(1).height(16));
        sourceRangePlus.setOnClick(e -> {
            int current = EBEClientConfig.printerMaterialSourceRange.get();
            int value = current <= 0 ? 64 : Math.min(Integer.MAX_VALUE, current + 64);
            EBEClientConfig.printerMaterialSourceRange.set(value);
            EBEClientConfig.SPEC.save();
            sourceRangeLabel.setText(Component.translatable("ebe.printer.material_source_range")
                    .append(Component.literal(": " + formatPrinterRange(value))));
        });
        sourceRangeRow.addChild(sourceRangePlus);

        var sourceRangeUnlimited = new Button();
        sourceRangeUnlimited.setText(Component.literal("0"));
        sourceRangeUnlimited.layout(l -> l.flex(1).height(16));
        sourceRangeUnlimited.setOnClick(e -> {
            EBEClientConfig.printerMaterialSourceRange.set(0);
            EBEClientConfig.SPEC.save();
            sourceRangeLabel.setText(Component.translatable("ebe.printer.material_source_range")
                    .append(Component.literal(": " + formatPrinterRange(0))));
        });
        sourceRangeRow.addChild(sourceRangeUnlimited);
        container.addChild(sourceRangeRow);

        var rangeLabel = new Label();
        rangeLabel.setId("printerRangeLabel");
        rangeLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9).textShadow(false));
        rangeLabel.layout(l -> l.widthPercent(100));
        var projection = ProjectionManager.getProjection();
        if (projection != null && ProjectionManager.isProjectionLoaded()) {
            rangeLabel.setText(Component.literal(
                    "[%d, %d, %d] ~ [%d, %d, %d]".formatted(
                            projection.getMinX(), projection.getMinY(), projection.getMinZ(),
                            projection.getMaxX(), projection.getMaxY(), projection.getMaxZ())));
        } else {
            rangeLabel.setText(Component.translatable("ebe.projection.no_projection"));
        }
        container.addChild(rangeLabel);

        var missingLabel = new Label();
        missingLabel.setId("printerMissingLabel");
        missingLabel.textStyle(ts -> ts.textColor(0xFFFF4444).fontSize(9).textShadow(false));
        missingLabel.layout(l -> l.widthPercent(100));
        updatePrinterMissingMaterials(missingLabel);
        container.addChild(missingLabel);

        return container;
    }

    private static void updatePrinterModeHighlight() {
        for (int i = 0; i < PrinterMode.values().length; i++) {
            final String btnId = "printerModeBtn_" + i;
            var btn = UIUtils.findById(rightPanel, btnId);
            if (btn instanceof Button b) {
                b.style(s -> s.background(btnId.equals(selectedPrinterModeBtnId) ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
            }
        }
    }

    private static void updatePrinterCurrentMode() {
        var label = UIUtils.findById(rightPanel, "printerCurrentMode");
        if (label instanceof Label l) {
            l.setText(Component.translatable(PrinterController.getMode().getTranslationKey()));
        }
    }

    private static void updatePrinterToggleBtn() {
        var btn = UIUtils.findById(rightPanel, "printerToggleBtn");
        if (btn instanceof Button b) {
            updatePrinterToggleText(b);
            b.style(s -> s.background(PrinterController.isActive() ? Sprites.RECT_RD_DARK : Sprites.RECT_RD));
        }
    }

    private static void updatePrinterToggleText(Button button) {
        button.setText(Component.translatable(PrinterController.isActive() ? "ebe.printer.running" : "ebe.printer.stopped"));
    }

    private static void updatePrinterSourceLabel(Label label) {
        var source = PrinterController.getMaterialSourcePos();
        if (source == null) {
            label.setText(Component.translatable("ebe.printer.no_chest_bound"));
        } else {
            label.setText(Component.translatable("ebe.printer.bound_chest")
                    .append(Component.literal(": " + source.getX() + ", " + source.getY() + ", " + source.getZ())));
        }
    }

    private static String formatPrinterRange(int range) {
        return range <= 0 ? "Unlimited" : Integer.toString(range);
    }

    private static void updatePrinterMissingMaterials(Label label) {
        var projection = ProjectionManager.getProjection();
        if (projection == null) {
            label.setText(Component.literal(""));
            return;
        }
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) {
            label.setText(Component.literal(""));
            return;
        }
        var inventory = player.getInventory();
        Map<net.minecraft.world.level.block.Block, Integer> needed = new java.util.LinkedHashMap<>();
        for (var pb : projection.getBlocks()) {
            var block = pb.state().getBlock();
            needed.merge(block, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : needed.entrySet()) {
            int have = 0;
            for (var stack : inventory.items) {
                if (stack.is(entry.getKey().asItem())) {
                    have += stack.getCount();
                }
            }
            int missing = entry.getValue() - have;
            if (missing > 0) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(entry.getKey().getName().getString()).append(" ×").append(missing);
            }
        }
        if (sb.isEmpty()) {
            label.setText(Component.literal(""));
        } else {
            label.setText(Component.literal(sb.toString()));
        }
    }

    public static void refreshHistoryList() {
        if (historyListContainer == null) return;
        historyListContainer.clearAllChildren();

        var branchLabel = findById(rootElement, "historyBranchLabel");
        if (branchLabel instanceof Label bl) {
            bl.setText(Component.translatable("ebe.history.branch_value", history.getCurrentBranch()));
        }

        var tags = history.getVersionTagsForCurrentBranch();
        var entries = history.getUndoEntries();
        if (entries.isEmpty() && tags.isEmpty()) {
            var empty = new Label();
            empty.setText(Component.translatable("ebe.history.empty"));
            empty.textStyle(ts -> ts.textColor(0xFF707070).fontSize(9));
            historyListContainer.addChild(empty);
            return;
        }

        var tagMap = new java.util.HashMap<Integer, com.l1ght.ebe.editor.history.HistoryManager.VersionTag>();
        for (var tag : tags) {
            tagMap.put(tag.entryId(), tag);
        }

        int idx = entries.size();
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            var tag = tagMap.get(entry.getId());
            if (tag != null) {
                var tagRow = buildVersionTagRow(tag);
                historyListContainer.addChild(tagRow);
            }
            var row = buildHistoryRow(entry, idx--);
            historyListContainer.addChild(row);
        }
    }

    private static UIElement buildVersionTagRow(com.l1ght.ebe.editor.history.HistoryManager.VersionTag tag) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN)
                .paddingHorizontal(4).paddingVertical(2).gapAll(1));
        row.style(s -> s.background(Sprites.RECT_RD));

        var topLine = new UIElement();
        topLine.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));

        var label = new Label();
        label.setText(Component.literal(tag.label()));
        label.textStyle(ts -> ts.textColor(0xFFFFD700).fontSize(10).textShadow(false));
        label.layout(l -> l.flex(1));
        topLine.addChild(label);

        var rollbackBtn = new UIElement();
        rollbackBtn.layout(l -> l.width(14).height(14));
        rollbackBtn.style(s -> s.background(Sprites.RECT_DARK));
        var rollbackIcon = new Label();
        rollbackIcon.setText(Component.literal("<-"));
        rollbackIcon.textStyle(ts -> ts.fontSize(8).textShadow(false));
        rollbackBtn.addChild(rollbackIcon);
        rollbackBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) rollbackToTag(tag);
        });
        registerTooltip(rollbackBtn, Component.translatable("ebe.history.rollback_tag"));
        topLine.addChild(rollbackBtn);

        row.addChild(topLine);

        if (!tag.description().isEmpty()) {
            var desc = new Label();
            desc.setText(Component.literal(tag.description()));
            desc.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(8).textShadow(false));
            row.addChild(desc);
        }

        var time = new Label();
        time.setText(Component.literal(formatTimestamp(tag.timestamp())));
        time.textStyle(ts -> ts.textColor(0xFF888888).fontSize(8).textShadow(false));
        row.addChild(time);

        return row;
    }

    private static UIElement buildHistoryRow(com.l1ght.ebe.editor.history.HistoryEntry entry, int displayIdx) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN)
                .paddingHorizontal(4).paddingVertical(3).gapAll(2));
        row.style(s -> s.background(Sprites.RECT_DARK));

        var topLine = new UIElement();
        topLine.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(4));

        var icon = new UIElement();
        icon.layout(l -> l.width(16).height(16).flexShrink(0));
        if (entry.getPrimaryBlock() instanceof net.minecraft.world.level.block.state.BlockState bs) {
            icon.style(s -> s.backgroundTexture(new ItemStackTexture(bs.getBlock().asItem())));
        } else {
            icon.style(s -> s.background(Sprites.RECT_RD));
        }
        topLine.addChild(icon);

        var actionName = Component.translatable(entry.getActionType().getKey());
        var countStr = entry.getAffectedCount() > 1 ? " ×" + entry.getAffectedCount() : "";
        var title = new Label();
        title.setText(Component.literal("#" + displayIdx + " ").append(actionName).append(Component.literal(countStr)));
        title.textStyle(ts -> ts.textColor(0xFFDDDDDD).fontSize(10).textShadow(false));
        title.layout(l -> l.flex(1).flexShrink(1));
        topLine.addChild(title);

        var jumpBtn = new UIElement();
        jumpBtn.layout(l -> l.width(16).height(16).flexShrink(0));
        jumpBtn.style(s -> s.background(Sprites.RECT_RD));
        var jumpLabel = new Label();
        jumpLabel.setText(Component.literal("->"));
        jumpLabel.textStyle(ts -> ts.fontSize(9).textShadow(false));
        jumpBtn.addChild(jumpLabel);
        var displayIdxCapture = displayIdx;
        jumpBtn.addEventListener(UIEvents.MOUSE_DOWN, e -> { if (e.button == 0) goToHistoryEntry(displayIdxCapture); });
        registerTooltip(jumpBtn, Component.translatable("ebe.history.go_to"));
        topLine.addChild(jumpBtn);

        row.addChild(topLine);

        var bottomLine = new UIElement();
        bottomLine.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN)
                .paddingLeft(20).gapAll(0));

        var posLabel = new Label();
        if (entry.isLayerChange()) {
            posLabel.setText(Component.literal(String.valueOf(entry.getPrimaryBlock())));
        } else {
            posLabel.setText(Component.literal(entry.getPrimaryX() + ", " + entry.getPrimaryY() + ", " + entry.getPrimaryZ()));
        }
        posLabel.textStyle(ts -> ts.textColor(0xFF808080).fontSize(8).textShadow(false));
        bottomLine.addChild(posLabel);

        var timeLabel = new Label();
        timeLabel.setText(Component.literal(formatTimestamp(entry.getTimestamp())));
        timeLabel.textStyle(ts -> ts.textColor(0xFF666666).fontSize(8).textShadow(false));
        bottomLine.addChild(timeLabel);

        row.addChild(bottomLine);

        return row;
    }

    private static String formatTimestamp(long timestamp) {
        var sdf = new java.text.SimpleDateFormat("HH:mm:ss");
        return sdf.format(new java.util.Date(timestamp));
    }

    private static void showCreateVersionTagDialog() {
        var dialog = new Dialog();
        dialog.setTitle("ebe.history.dialog.tag_title");
        dialog.darkenBackground();

        var labelField = new TextField().setText("v" + (history.getVersionTags().size() + 1), false);
        labelField.layout(layout -> layout.widthPercent(100));
        labelField.setTextValidator(text -> text != null && !text.isBlank());

        var labelLabel = new Label();
        labelLabel.setText(Component.translatable("ebe.history.dialog.tag_label"));
        labelLabel.textStyle(ts -> ts.fontSize(9).textShadow(false));
        dialog.addContent(labelLabel);
        dialog.addContent(labelField);

        var descField = new TextField().setText("", false);
        descField.layout(layout -> layout.widthPercent(100));
        descField.setAnyString();

        var descLabel = new Label();
        descLabel.setText(Component.translatable("ebe.history.dialog.tag_desc"));
        descLabel.textStyle(ts -> ts.fontSize(9).textShadow(false));
        dialog.addContent(descLabel);
        dialog.addContent(descField);

        dialog.addButton(new Button()
                .setOnClick(e -> {
                    history.createVersionTag(labelField.getText().trim(), descField.getText().trim());
                    dialog.close();
                    refreshHistoryList();
                })
                .setText("ebe.history.dialog.confirm")
                .addClass("__confirm-button__"));
        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText("ebe.history.dialog.cancel")
                .addClass("__cancel-button__"));
        dialog.show(rootElement);
    }

    private static void showCreateBranchDialog() {
        var dialog = new Dialog();
        dialog.setTitle("ebe.history.dialog.branch_title");
        dialog.darkenBackground();

        var nameField = new TextField().setText("branch-" + history.getBranches().size(), false);
        nameField.layout(layout -> layout.widthPercent(100));
        nameField.setTextValidator(text -> text != null && !text.isBlank() && text.matches("[a-zA-Z0-9_-]+"));

        var nameLabel = new Label();
        nameLabel.setText(Component.translatable("ebe.history.dialog.branch_name"));
        nameLabel.textStyle(ts -> ts.fontSize(9).textShadow(false));
        dialog.addContent(nameLabel);
        dialog.addContent(nameField);

        dialog.addButton(new Button()
                .setOnClick(e -> {
                    history.createBranch(nameField.getText().trim(), session.getModel());
                    var restored = history.switchBranch(nameField.getText().trim(), session.getModel());
                    applyBranchModel(restored);
                    dialog.close();
                    refreshHistoryList();
                })
                .setText("ebe.history.dialog.confirm")
                .addClass("__confirm-button__"));
        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText("ebe.history.dialog.cancel")
                .addClass("__cancel-button__"));
        dialog.show(rootElement);
    }

    private static void showBranchSwitchDialog() {
        var branches = history.getBranches();
        if (branches.size() <= 1) {
            showCreateBranchDialog();
            return;
        }

        var dialog = new Dialog();
        dialog.setTitle("ebe.history.dialog.branch_title");
        dialog.darkenBackground();

        for (var branch : branches) {
            var btn = new Button();
            boolean isCurrent = branch.name().equals(history.getCurrentBranch());
            btn.setText(Component.literal((isCurrent ? "* " : "  ") + branch.name()));
            btn.layout(l -> l.widthPercent(100).height(18));
            btn.textStyle(ts -> ts.fontSize(9).textColor(isCurrent ? 0xFF55FF55 : 0xFFDDDDDD).textShadow(false));
            if (!isCurrent) {
                var branchName = branch.name();
                btn.setOnClick(e -> {
                    var restored = history.switchBranch(branchName, session.getModel());
                    applyBranchModel(restored);
                    dialog.close();
                    refreshHistoryList();
                });
            }
            dialog.addContent(btn);
        }

        var sep = new UIElement();
        sep.layout(l -> l.widthPercent(100).height(4));
        dialog.addContent(sep);

        var createBtn = new Button();
        createBtn.setText(Component.translatable("ebe.history.create_branch"));
        createBtn.layout(l -> l.widthPercent(100).height(18));
        createBtn.textStyle(ts -> ts.fontSize(9).textShadow(false));
        createBtn.setOnClick(e -> {
            dialog.close();
            showCreateBranchDialog();
        });
        dialog.addContent(createBtn);

        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText("ebe.history.dialog.cancel")
                .addClass("__cancel-button__"));
        dialog.show(rootElement);
    }

    private static void applyBranchModel(BuildingModel restored) {
        if (restored == null || session == null) return;
        session.restoreModel(restored, true);
        selection.clear();
        updateSelectionCount();
        refreshLayersList();
        refreshPropertiesPanel();
        ViewportFactory.refreshFromModel(session.getModel());
        refreshMaterialList();
        updateStatusBar();
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
        modLabel.setText(Component.translatable("ebe.editor.properties.mod", modId));
        modLabel.textStyle(ts -> ts.textColor(0xFF888888).fontSize(9));
        propertiesContainer.addChild(modLabel);

        var posSection = new UIElement();
        posSection.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4).paddingTop(4));

        var posLabel = new Label();
        posLabel.setText(Component.translatable("ebe.editor.properties.position"));
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
            propsLabel.setText(Component.translatable("ebe.editor.properties.blockstates"));
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
            nbtLabel.setText(Component.translatable("ebe.nbt.label_colon"));
            nbtLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9));
            nbtLabel.layout(l -> l.marginTop(4));
            propertiesContainer.addChild(nbtLabel);

            addNbtTree(propertiesContainer, nbt, 0);
        } else if (bs.hasBlockEntity()) {
            var nbtLabel = new Label();
            nbtLabel.setText(Component.translatable("ebe.nbt.empty_label"));
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
                delBtn.setText(Component.literal("X"));
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
        var nameField = new TextField();
        nameField.setText("new_field");
        nameField.setId("nbtNewFieldName");
        nameField.layout(l -> l.widthPercent(100).height(18));

        var dialog = new UIElement();
        dialog.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4).paddingAll(4));
        dialog.style(s -> s.background(Sprites.BORDER));

        var label = new Label();
        label.setText(Component.translatable("ebe.nbt.add"));
        label.textStyle(ts -> ts.textColor(0xFFCCCCCC).fontSize(9).textShadow(false));
        dialog.addChild(label);
        dialog.addChild(nameField);

        var confirmBtn = new Button();
        confirmBtn.setText(Component.literal("OK"));
        confirmBtn.layout(l -> l.widthPercent(100).height(18));
        confirmBtn.setOnClick(ev -> {
            String name = nameField.getText();
            if (!name.isEmpty() && !parent.contains(name)) {
                parent.putString(name, "");
                session.getModel().setBlockEntityNbt(state.getCursorX(), state.getCursorY(), state.getCursorZ(), parent);
                refreshPropertiesPanel();
            }
        });
        dialog.addChild(confirmBtn);

        propertiesContainer.addChild(dialog);
    }

    private static UIElement buildCoordField(String label, int value) {
        return buildCoordField(label, value, null);
    }

    private static UIElement buildCoordField(String label, int value, String id) {
        var col = new UIElement();
        col.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(1).flex(1));

        var lbl = new Label();
        lbl.setText(Component.literal(label));
        lbl.textStyle(ts -> ts.textColor(0xFF888888).fontSize(8));
        col.addChild(lbl);

        var tf = new TextField();
        tf.setText(String.valueOf(value));
        if (id != null) tf.setId(id);
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

    public static void pollFileTreeRefresh() {
        if (fileListContainer == null) return;
        if (fileRefreshCooldown-- > 0) return;
        fileRefreshCooldown = 20;
        long signature = computeFileTreeSignature();
        if (signature != lastFileTreeSignature) {
            refreshFileList();
        }
    }

    private static void refreshFileList() {
        if (fileListContainer == null) return;
        fileListContainer.clearAllChildren();
        lastFileTreeSignature = computeFileTreeSignature();
        var dir = Path.of(EBEClientConfig.schematicDir.get());
        try {
            Files.createDirectories(dir);
            try (var stream = Files.list(dir)) {
                var files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> FileManager.SUPPORTED_EXTENSIONS.contains(FileManager.getFileExtension(path).toLowerCase()))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                        .limit(500)
                        .toList();
                if (files.isEmpty()) {
                    var empty = new Label();
                    empty.setText(Component.translatable("ebe.editor.files.empty"));
                    empty.textStyle(ts -> ts.textColor(0xFF888888).fontSize(9).textShadow(false));
                    fileListContainer.addChild(empty);
                    return;
                }
                for (Path file : files) {
                    fileListContainer.addChild(createFileRow(file));
                }
            }
        } catch (Exception e) {
            var error = new Label();
            error.setText(Component.translatable("ebe.editor.files.read_failed", e.getMessage()));
            error.textStyle(ts -> ts.textColor(0xFFFF6666).fontSize(9).textShadow(false));
            fileListContainer.addChild(error);
        }
    }

    public static void importDroppedFiles(List<Path> files) {
        if (files == null || files.isEmpty()) return;
        Path firstImported = null;
        for (Path file : files) {
            if (file == null || !FileManager.SUPPORTED_EXTENSIONS.contains(FileManager.getFileExtension(file).toLowerCase())) {
                continue;
            }
            final Path[] imported = new Path[1];
            ImportDialog.importFile(file, path -> imported[0] = path);
            if (firstImported == null && imported[0] != null) {
                firstImported = imported[0];
            }
        }
        refreshFileList();
        if (firstImported != null) {
            beginLoadFile(firstImported);
        }
    }

    private static UIElement createFileRow(Path file) {
        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(1).paddingAll(3));
        row.style(s -> s.background(Sprites.RECT_RD));
        row.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 1) {
                showFileContextDialog(file);
                e.stopPropagation();
            }
        });

        var top = new UIElement();
        top.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(3));

        var ext = new Label();
        ext.setText(Component.literal(FileManager.getFileExtension(file).replace(".", "").toUpperCase()));
        ext.layout(l -> l.width(42));
        ext.textStyle(ts -> ts.textColor(0xFFFFD166).fontSize(8).textShadow(false));
        top.addChild(ext);

        var name = new Label();
        name.setText(Component.literal(file.getFileName().toString()));
        name.layout(l -> l.flex(1));
        name.textStyle(ts -> ts.textColor(0xFFFFFFFF).fontSize(9).textShadow(false)
                .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        top.addChild(name);

        var open = new Button();
        open.setText(Component.translatable("ebe.editor.open"));
        open.layout(l -> l.width(42).height(16));
        open.setOnClick(e -> onFileSelected(file));
        top.addChild(open);

        var more = new Button();
        more.setText(Component.literal("..."));
        more.layout(l -> l.width(24).height(16));
        more.setOnClick(e -> showFileContextDialog(file));
        top.addChild(more);

        row.addChild(top);

        var meta = new Label();
        meta.setText(buildFileThumbnailText(file));
        meta.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(8).textShadow(false));
        row.addChild(meta);
        return row;
    }

    private static Component buildFileThumbnailText(Path file) {
        try {
            long size = Files.size(file);
            return Component.translatable("ebe.editor.files.thumbnail", fileTypeName(file), Math.max(1, size / 1024));
        } catch (Exception ignored) {
            return fileTypeName(file);
        }
    }

    private static Component fileTypeName(Path file) {
        return Component.translatable("ebe.editor.files.type." + FileManager.getFileType(file));
    }

    private static void showFileContextDialog(Path file) {
        var dialog = new Dialog();
        dialog.setTitle(file.getFileName().toString());
        dialog.overlay.layout(l -> l.width(240));

        dialog.addButton(new Button().setText(Component.translatable("ebe.editor.open")).setOnClick(e -> {
            onFileSelected(file);
            dialog.close();
        }));
        dialog.addButton(new Button().setText(Component.translatable("ebe.editor.files.rename")).setOnClick(e -> {
            dialog.close();
            renameFileDialog(file);
        }));
        dialog.addButton(new Button().setText(Component.translatable("ebe.editor.files.export_as")).setOnClick(e -> {
            dialog.close();
            onFileSelected(file);
            EditorDialogs.saveAsDialog(rootElement, session.getCurrentName(), name -> {
                try {
                    session.saveAs(name);
                    refreshFileList();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }));
        dialog.addButton(new Button().setText(Component.translatable("ebe.editor.files.delete")).setOnClick(e -> {
            dialog.close();
            EditorDialogs.confirmDialog(rootElement, Component.translatable("ebe.editor.files.delete_confirm", file.getFileName().toString()), () -> {
                try {
                    Files.deleteIfExists(file);
                    refreshFileList();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }));
        dialog.addButton(new Button().setText(Component.translatable("ebe.history.dialog.cancel")).setOnClick(e -> dialog.close()));
        dialog.show(rootElement);
    }

    private static void renameFileDialog(Path file) {
        var dialog = Dialog.stringEditorDialog(
                Component.translatable("ebe.editor.files.rename").getString(),
                file.getFileName().toString(),
                s -> !s.isBlank() && !s.matches(".*[\\\\/:*?\"<>|].*"),
                name -> {
                    try {
                        Files.move(file, file.resolveSibling(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        refreshFileList();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );
        dialog.overlay.layout(l -> l.width(220));
        dialog.show(rootElement);
    }

    private static long computeFileTreeSignature() {
        var dir = Path.of(EBEClientConfig.schematicDir.get());
        if (!Files.exists(dir)) return 0L;
        long hash = 1125899906842597L;
        try (var stream = Files.list(dir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                if (!FileManager.SUPPORTED_EXTENSIONS.contains(FileManager.getFileExtension(path).toLowerCase())) continue;
                hash = 31 * hash + path.getFileName().toString().hashCode();
                hash = 31 * hash + Files.size(path);
                hash = 31 * hash + Files.getLastModifiedTime(path).toMillis();
            }
        } catch (Exception ignored) {
        }
        return hash;
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
        loadDirectoryChildren(root, dir);
        return root;
    }

    private static void loadDirectoryChildren(FileTreeNode parent, Path dir) {
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.sorted(Comparator
                    .comparing((Path p) -> !Files.isDirectory(p))
                    .thenComparing(Path::getFileName))
                    .limit(500)
                    .forEach(p -> parent.addChild(Files.isDirectory(p)
                            ? FileTreeNode.ofDirectory(p)
                            : FileTreeNode.ofFile(p)));
        } catch (Exception ignored) {}
    }

    private static void onFileSelected(Path file) {
        beginLoadFile(file);
    }

    private static Button buildCollapseButton(boolean isLeft) {
        var btn = new Button();
        btn.setText(Component.literal(isLeft ? "<" : ">"));
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
                setHorizontalResizeCursor();
                e.stopPropagation();
            }
        });

        divider.addEventListener(UIEvents.MOUSE_ENTER, e -> {
            divider.style(s -> s.background(Sprites.RECT_RD_DARK));
            setHorizontalResizeCursor();
        });
        divider.addEventListener(UIEvents.MOUSE_LEAVE, e -> {
            if (activeDivider == 0 || (isLeft && activeDivider != 1) || (!isLeft && activeDivider != 2)) {
                divider.style(s -> s.background(Sprites.RECT_DARK));
                resetMouseCursor();
            }
        });

        return divider;
    }

    private static void setHorizontalResizeCursor() {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        if (horizontalResizeCursor == 0L) {
            horizontalResizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
        }
        if (horizontalResizeCursor != 0L) {
            GLFW.glfwSetCursor(mc.getWindow().getWindow(), horizontalResizeCursor);
            resizeCursorActive = true;
        }
    }

    public static void resetMouseCursor() {
        if (!resizeCursorActive) return;
        var mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null) {
            GLFW.glfwSetCursor(mc.getWindow().getWindow(), 0L);
        }
        resizeCursorActive = false;
    }

    private static void toggleLeftPanel() {
        leftPanelVisible = !leftPanelVisible;
        leftPanel.setDisplay(leftPanelVisible);
        leftDivider.setDisplay(leftPanelVisible);
        leftCollapseBtn.setText(Component.literal(leftPanelVisible ? "<" : ">"));
    }

    private static void toggleRightPanel() {
        rightPanelVisible = !rightPanelVisible;
        rightPanel.setDisplay(rightPanelVisible);
        rightDivider.setDisplay(rightPanelVisible);
        rightCollapseBtn.setText(Component.literal(rightPanelVisible ? ">" : "<"));
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
        panel.setAllowHitTest(false);

        var titleRow = new UIElement();
        titleRow.setId("replaceTitleBar");
        titleRow.layout(l -> l.widthPercent(100).height(18).flexDirection(FlexDirection.ROW)
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
                e.stopPropagation();
            }
        });
        titleRow.addChild(closeBtn);
        panel.addChild(titleRow);

        setupPanelDrag(panel, "replaceTitleBar");

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
                    state.setActiveBlockState(bs);
                    selectTool(EditorTool.REPLACE);
                    updateReplaceSingleDisplay();
                    updateActiveBlockIndicator();
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
                    selectTool(EditorTool.REPLACE);
                    updateReplaceSingleDisplay();
                }
            }
        });
        targetRow.addChild(grabBtn);
        targetSection.addChild(targetRow);
        content.addChild(targetSection);

        var executeBtn = new Button();
        executeBtn.setText(Component.translatable("ebe.editor.replace.execute"));
        executeBtn.layout(l -> l.widthPercent(100).height(22));
        executeBtn.setOnClick(e -> {
            if (replaceTargetState == null) return;
            var selection = getSelection();
            if (selection.isEmpty()) return;
            var model = session.getModel();
            var hist = getHistory();
            var beforeLayerState = model.captureLayerState();
            int beforeUndo = hist.undoSize();
            var snapshots = new ArrayList<Object[]>();
            for (var p : selection.getPositions()) {
                int x = (int) p[0], y = (int) p[1], z = (int) p[2];
                var old = model.getBlockAt(x, y, z);
                snapshots.add(nbtSnapshot(model, x, y, z, old, replaceTargetState, null));
                model.setBlockAtWithNbt(x, y, z, replaceTargetState, null);
            }
            if (!snapshots.isEmpty()) {
                pushModelEditHistory(hist, com.l1ght.ebe.editor.history.HistoryActionType.REPLACE,
                        snapshots, beforeLayerState, model,
                        (int) snapshots.get(0)[0], (int) snapshots.get(0)[1], (int) snapshots.get(0)[2],
                        replaceTargetState);
            }
            if (hist.undoSize() > beforeUndo) {
                refreshViewportAfterHistoryEntry(hist.getLastEntry());
                refreshMaterialList();
                refreshHistoryList();
            }
        });
        content.addChild(executeBtn);

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
                valLabel.setText(Component.literal("->"));
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
        panel.setAllowHitTest(false);

        var titleRow = new UIElement();
        titleRow.setId("fillTitleBar");
        titleRow.layout(l -> l.widthPercent(100).height(18).flexDirection(FlexDirection.ROW)
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
                e.stopPropagation();
            }
        });
        titleRow.addChild(closeBtn);
        panel.addChild(titleRow);

        setupPanelDrag(panel, "fillTitleBar");

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

        var centerLabel = new Label();
        centerLabel.setText(Component.translatable("ebe.rotate.center"));
        centerLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9).textShadow(false));
        content.addChild(centerLabel);

        var centerRow = new UIElement();
        centerRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4));
        centerRow.addChild(buildCoordField("X", 0, "rotateCenterX"));
        centerRow.addChild(buildCoordField("Y", 0, "rotateCenterY"));
        centerRow.addChild(buildCoordField("Z", 0, "rotateCenterZ"));
        content.addChild(centerRow);

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
        var hist = getHistory();

        int cx = getIntField(fillPanel, "rotateCenterX", 0);
        int cy = getIntField(fillPanel, "rotateCenterY", 0);
        int cz = getIntField(fillPanel, "rotateCenterZ", 0);

        var positions = selection.getPositions();
        var beforeLayerState = model.captureLayerState();
        if (cx == 0 && cy == 0 && cz == 0) {
            cx = 0; cy = 0; cz = 0;
            for (var p : positions) { cx += (int) p[0]; cy += (int) p[1]; cz += (int) p[2]; }
            cx /= positions.size(); cy /= positions.size(); cz /= positions.size();
        }

        var oldStates = new LinkedHashMap<Long, Object>();
        var oldLayerIds = new LinkedHashMap<Long, String>();
        var sourceKeys = new java.util.HashSet<Long>();
        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            long sourceKey = com.l1ght.ebe.editor.selection.SelectionManager.packPos(ox, oy, oz);
            sourceKeys.add(sourceKey);
            oldStates.put(sourceKey, model.getBlockAt(ox, oy, oz));
            oldLayerIds.put(sourceKey, model.getLayerIdAt(ox, oy, oz));
        }

        var newPositions = new LinkedHashMap<Long, Object>();
        var posMapping = new ArrayList<long[]>();

        for (var p : positions) {
            int ox = (int) p[0], oy = (int) p[1], oz = (int) p[2];
            var oldState = oldStates.get(com.l1ght.ebe.editor.selection.SelectionManager.packPos(ox, oy, oz));
            var bs = ViewportFactory.resolveBlockStatePublic(oldState);

            int dx = ox - cx, dy = oy - cy, dz = oz - cz;
            int nx, ny, nz;

            if (rotateAxis == 0) {
                nx = ox;
                ny = switch (rotateAngle) {
                    case 90 -> cy - dz;
                    case 180 -> 2 * cy - oy;
                    case 270 -> cy + dz;
                    default -> oy;
                };
                nz = switch (rotateAngle) {
                    case 90 -> cz + dy;
                    case 180 -> 2 * cz - oz;
                    case 270 -> cz - dy;
                    default -> oz;
                };
            } else if (rotateAxis == 1) {
                nx = switch (rotateAngle) {
                    case 90 -> cx + dz;
                    case 180 -> 2 * cx - ox;
                    case 270 -> cx - dz;
                    default -> ox;
                };
                ny = oy;
                nz = switch (rotateAngle) {
                    case 90 -> cz - dx;
                    case 180 -> 2 * cz - oz;
                    case 270 -> cz + dx;
                    default -> oz;
                };
            } else {
                nx = switch (rotateAngle) {
                    case 90 -> cx - dy;
                    case 180 -> 2 * cx - ox;
                    case 270 -> cx + dy;
                    default -> ox;
                };
                ny = switch (rotateAngle) {
                    case 90 -> cy + dx;
                    case 180 -> 2 * cy - oy;
                    case 270 -> cy - dx;
                    default -> oy;
                };
                nz = oz;
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
            newPositions.put(com.l1ght.ebe.editor.selection.SelectionManager.packPos(nx, ny, nz), rotatedBs);
            posMapping.add(new long[]{ox, oy, oz, nx, ny, nz});
        }

        var oldNbt = new LinkedHashMap<Long, net.minecraft.nbt.CompoundTag>();
        for (var mapping : posMapping) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            oldNbt.put(com.l1ght.ebe.editor.selection.SelectionManager.packPos(ox, oy, oz),
                    model.copyBlockEntityNbt(ox, oy, oz));
        }

        var existingAtTarget = new LinkedHashMap<Long, Object>();
        var existingNbtAtTarget = new LinkedHashMap<Long, net.minecraft.nbt.CompoundTag>();
        for (var mapping : posMapping) {
            int nx = (int) mapping[3], ny = (int) mapping[4], nz = (int) mapping[5];
            long packed = com.l1ght.ebe.editor.selection.SelectionManager.packPos(nx, ny, nz);
            if (!sourceKeys.contains(packed)) {
                existingAtTarget.put(packed, model.getBlockAt(nx, ny, nz));
                existingNbtAtTarget.put(packed, model.copyBlockEntityNbt(nx, ny, nz));
            }
        }

        var snapshots = new ArrayList<Object[]>();
        int repX = 0, repY = 0, repZ = 0;
        Object repBlock = null;
        for (var mapping : posMapping) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            long sourceKey = com.l1ght.ebe.editor.selection.SelectionManager.packPos(ox, oy, oz);
            var oldState = oldStates.get(sourceKey);
            snapshots.add(new Object[]{ox, oy, oz, oldState, "minecraft:air", oldNbt.get(sourceKey), null});
            if (repBlock == null) { repX = (int) mapping[3]; repY = (int) mapping[4]; repZ = (int) mapping[5]; repBlock = oldState; }
        }
        for (var mapping : posMapping) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            int nx = (int) mapping[3], ny = (int) mapping[4], nz = (int) mapping[5];
            long sourceKey = com.l1ght.ebe.editor.selection.SelectionManager.packPos(ox, oy, oz);
            long targetKey = com.l1ght.ebe.editor.selection.SelectionManager.packPos(nx, ny, nz);
            var existing = existingAtTarget.getOrDefault(targetKey, "minecraft:air");
            snapshots.add(new Object[]{nx, ny, nz, existing, newPositions.get(targetKey),
                    existingNbtAtTarget.get(targetKey), oldNbt.get(sourceKey)});
        }

        for (var p : positions) {
            model.setBlockAt((int) p[0], (int) p[1], (int) p[2], "minecraft:air");
        }
        for (var mapping : posMapping) {
            int ox = (int) mapping[0], oy = (int) mapping[1], oz = (int) mapping[2];
            int nx = (int) mapping[3], ny = (int) mapping[4], nz = (int) mapping[5];
            long sourceKey = com.l1ght.ebe.editor.selection.SelectionManager.packPos(ox, oy, oz);
            var newState = newPositions.get(com.l1ght.ebe.editor.selection.SelectionManager.packPos(nx, ny, nz));
            model.setBlockAtWithNbt(nx, ny, nz, newState, oldNbt.get(sourceKey));
            if (!BuildingModel.isAirLike(newState)) {
                model.setBlockLayerOverride(nx, ny, nz, oldLayerIds.get(sourceKey));
            }
        }

        selection.clear();
        for (var mapping : posMapping) {
            selection.add((int) mapping[3], (int) mapping[4], (int) mapping[5]);
        }

        if (!snapshots.isEmpty()) {
            var afterLayerState = model.captureLayerState();
            if (beforeLayerState.equals(afterLayerState)) {
                beforeLayerState = null;
                afterLayerState = null;
            }
            hist.push(new com.l1ght.ebe.editor.history.HistoryEntry(
                    hist.nextId(), com.l1ght.ebe.editor.history.HistoryActionType.ROTATE,
                    snapshots.toArray(Object[][]::new),
                    beforeLayerState, afterLayerState,
                    repX, repY, repZ, repBlock, snapshots.size(), System.currentTimeMillis()));
            session.markDirty();
        }

        var lastEntry = hist.getLastEntry();
        if (lastEntry != null) {
            refreshViewportAfterHistoryEntry(lastEntry);
        } else {
            ViewportFactory.applyBlockDeltasFromModel(snapshots.toArray(Object[][]::new));
        }
        refreshMaterialList();
        refreshHistoryList();
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

        var centerLabel = new Label();
        centerLabel.setText(Component.translatable("ebe.mirror.center"));
        centerLabel.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(9).textShadow(false));
        content.addChild(centerLabel);

        var centerRow = new UIElement();
        centerRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).gapAll(4));
        centerRow.addChild(buildCoordField("X", 0, "mirrorCenterX"));
        centerRow.addChild(buildCoordField("Y", 0, "mirrorCenterY"));
        centerRow.addChild(buildCoordField("Z", 0, "mirrorCenterZ"));
        content.addChild(centerRow);

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
        int cx = getIntField(fillPanel, "mirrorCenterX", 0);
        int cy = getIntField(fillPanel, "mirrorCenterY", 0);
        int cz = getIntField(fillPanel, "mirrorCenterZ", 0);
        int beforeUndo = history.undoSize();
        clipboard.mirror(session.getModel(), selection, mirrorAxis, cx, cy, cz, history);
        finishClipboardMutation(beforeUndo);
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
            delBtn.setText(Component.literal("X"));
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
        int beforeUndo = history.undoSize();
        clipboard.fillRandom(session.getModel(), selection, ratios, history);
        finishClipboardMutation(beforeUndo);
    }

    private static void executeFill() {
        if (session == null || fillBlockState == null) return;
        var selection = getSelection();
        if (selection.isEmpty()) return;
        int beforeUndo = history.undoSize();
        clipboard.fill(session.getModel(), selection, fillBlockState, history);
        finishClipboardMutation(beforeUndo);
    }

    private static void executeTranslate() {
        if (session == null) return;
        var selection = getSelection();
        if (selection.isEmpty()) return;
        int dx = getIntField(fillPanel, "fillDxField", 0);
        int dy = getIntField(fillPanel, "fillDyField", 0);
        int dz = getIntField(fillPanel, "fillDzField", 0);
        if (dx == 0 && dy == 0 && dz == 0) return;
        int beforeUndo = history.undoSize();
        clipboard.translateSelection(session.getModel(), selection, dx, dy, dz, history);
        finishClipboardMutation(beforeUndo);
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
        var beforeLayerState = model.captureLayerState();
        int beforeUndo = history.undoSize();
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
                            snapshots.add(nbtSnapshot(model, wx, wy, wz, old, replaceTargetState, null));
                            model.setBlockAtWithNbt(wx, wy, wz, replaceTargetState, null);
                        }
                    }
                }
            }
        }
        if (!snapshots.isEmpty()) {
            pushModelEditHistory(history, com.l1ght.ebe.editor.history.HistoryActionType.REPLACE,
                    snapshots, beforeLayerState, model,
                    (int) snapshots.get(0)[0], (int) snapshots.get(0)[1], (int) snapshots.get(0)[2],
                    replaceSourceBlock.getDescriptionId());
        }
        if (history.undoSize() > beforeUndo) {
            refreshViewportAfterHistoryEntry(history.getLastEntry());
            refreshMaterialList();
            refreshHistoryList();
        }
    }

    private static void executeByConditionReplace() {
        if (session == null || replaceTargetState == null) return;
        if (conditionType.equals("property") && replaceSourceBlock == null) return;
        var model = session.getModel();
        var history = getHistory();
        var beforeLayerState = model.captureLayerState();
        int beforeUndo = history.undoSize();

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
                        snapshots.add(nbtSnapshot(model, wx, wy, wz, old, newState, null));
                        model.setBlockAtWithNbt(wx, wy, wz, newState, null);
                    }
                }
            }
        }
        if (!snapshots.isEmpty()) {
            pushModelEditHistory(history, com.l1ght.ebe.editor.history.HistoryActionType.REPLACE,
                    snapshots, beforeLayerState, model,
                    (int) snapshots.get(0)[0], (int) snapshots.get(0)[1], (int) snapshots.get(0)[2],
                    replaceTargetState.getBlock().getDescriptionId());
        }
        if (history.undoSize() > beforeUndo) {
            refreshViewportAfterHistoryEntry(history.getLastEntry());
            refreshMaterialList();
            refreshHistoryList();
        }
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
        var beforeLayerState = model.captureLayerState();
        int beforeUndo = history.undoSize();
        var snapshots = new ArrayList<Object[]>();
        for (var packed : selection.getAllPacked()) {
            int x = com.l1ght.ebe.editor.selection.SelectionManager.unpackX(packed);
            int y = com.l1ght.ebe.editor.selection.SelectionManager.unpackY(packed);
            int z = com.l1ght.ebe.editor.selection.SelectionManager.unpackZ(packed);
            var old = model.getBlockAt(x, y, z);
            snapshots.add(nbtSnapshot(model, x, y, z, old, replaceTargetState, null));
            model.setBlockAtWithNbt(x, y, z, replaceTargetState, null);
        }
        if (!snapshots.isEmpty()) {
            pushModelEditHistory(history, com.l1ght.ebe.editor.history.HistoryActionType.REPLACE,
                    snapshots, beforeLayerState, model,
                    (int) snapshots.get(0)[0], (int) snapshots.get(0)[1], (int) snapshots.get(0)[2],
                    replaceTargetState.getBlock().getDescriptionId());
        }
        if (history.undoSize() > beforeUndo) {
            refreshViewportAfterHistoryEntry(history.getLastEntry());
            refreshMaterialList();
            refreshHistoryList();
        }
    }

    private static void refreshAfterEdit() {
        var model = session.getModel();
        ViewportFactory.refreshFromModel(model);
        refreshMaterialList();
        refreshHistoryList();
    }

    private static void refreshViewportAfterHistoryEntry(com.l1ght.ebe.editor.history.HistoryEntry entry) {
        if (entry == null || session == null) return;
        if (entry.isLayerChange()) {
            refreshLayersList();
            ViewportFactory.refreshFromModel(session.getModel());
        } else {
            ViewportFactory.applyBlockDeltasFromModel(entry.getSnapshots());
        }
    }

    private static boolean finishClipboardMutation(int previousUndoSize) {
        if (session == null || history.undoSize() <= previousUndoSize) return false;
        session.markDirty();
        refreshViewportAfterHistoryEntry(history.getLastEntry());
        refreshMaterialList();
        refreshHistoryList();
        updateStatusBar();
        return true;
    }

    private static void pushModelEditHistory(com.l1ght.ebe.editor.history.HistoryManager hist,
                                             com.l1ght.ebe.editor.history.HistoryActionType type,
                                             List<Object[]> snapshots,
                                             BuildingModel.LayerState beforeLayerState,
                                             BuildingModel model,
                                             int primaryX, int primaryY, int primaryZ,
                                             Object primaryBlock) {
        if (snapshots.isEmpty()) return;
        BuildingModel.LayerState afterLayerState = null;
        if (beforeLayerState != null) {
            afterLayerState = model.captureLayerState();
            if (beforeLayerState.equals(afterLayerState)) {
                beforeLayerState = null;
                afterLayerState = null;
            }
        }
        hist.push(new com.l1ght.ebe.editor.history.HistoryEntry(
                hist.nextId(), type, snapshots.toArray(Object[][]::new),
                beforeLayerState, afterLayerState,
                primaryX, primaryY, primaryZ, primaryBlock, snapshots.size(), System.currentTimeMillis()));
        if (session != null) {
            session.markDirty();
        }
    }

    public static void refreshKeybindHints() {
        if (keybindHintsPanel == null) return;
        var hintsLabel = UIUtils.findById(keybindHintsPanel, "keybindHintsText");
        if (!(hintsLabel instanceof Label l)) return;

        var tool = state.getActiveTool();
        String undo = EBEKeyBindings.UNDO.getDisplayName();
        String redo = EBEKeyBindings.REDO.getDisplayName();
        String copy = EBEKeyBindings.COPY.getDisplayName();
        String paste = EBEKeyBindings.PASTE.getDisplayName();
        String cut = EBEKeyBindings.CUT.getDisplayName();
        String selectMulti = EBEKeyBindings.SELECT_MULTI.getDisplayName();
        String boxSurface = EBEKeyBindings.BOX_SELECT_SURFACE.getDisplayName();
        String boxPenetrate = EBEKeyBindings.BOX_SELECT_PENETRATE.getDisplayName();
        String sameType = EBEKeyBindings.SELECT_SAME_TYPE.getDisplayName();
        String deselect = EBEKeyBindings.DESELECT_BLOCK.getDisplayName();
        String fillExec = EBEKeyBindings.FILL_EXECUTE.getDisplayName();
        String grabViewport = EBEKeyBindings.GRAB_VIEWPORT.getDisplayName();

        String text = switch (tool) {
            case SELECT -> "* " +
                    Component.translatable("ebe.hints.select.click").getString() + "\n" +
                    "* " + selectMulti + ": " + Component.translatable("ebe.hints.select.multi").getString() + "\n" +
                    "* " + boxSurface + ": " + Component.translatable("ebe.hints.select.box_surface").getString() + "\n" +
                    "* " + boxPenetrate + ": " + Component.translatable("ebe.hints.select.box_penetrate").getString() + "\n" +
                    "* " + sameType + ": " + Component.translatable("ebe.hints.select.same_type").getString() + "\n" +
                    "* " + deselect + ": " + Component.translatable("ebe.hints.select.deselect").getString() + "\n" +
                    "* " + Component.translatable("ebe.hints.common.undo").getString() + ": " + undo + "/" + redo + "\n" +
                    "* " + Component.translatable("ebe.hints.common.clipboard").getString() + ": " + copy + "/" + paste + "/" + cut;
            case PLACE -> "* " +
                    Component.translatable("ebe.hints.place.click").getString() + "\n" +
                    "* " + Component.translatable("ebe.hints.common.undo").getString() + ": " + undo;
            case DELETE -> "* " +
                    Component.translatable("ebe.hints.delete.click").getString() + "\n" +
                    "* " + Component.translatable("ebe.hints.common.undo").getString() + ": " + undo;
            case REPLACE -> "* " +
                    Component.translatable("ebe.hints.replace.click").getString() + "\n" +
                    "* " + Component.translatable("ebe.hints.common.undo").getString() + ": " + undo;
            case GRAB -> "* " +
                    Component.translatable("ebe.hints.grab.click").getString() + "\n" +
                    "* " + grabViewport + ": " + Component.translatable("ebe.hints.grab.middle").getString();
            case MEASURE -> "* " +
                    Component.translatable("ebe.hints.measure.click").getString();
            case FILL -> "* " +
                    Component.translatable("ebe.hints.fill.click").getString() + "\n" +
                    "* " + fillExec + ": " + Component.translatable("ebe.hints.fill.execute").getString();
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

    public static void markMaterialListStale() {
        if (materialListContainer != null) {
            materialListContainer.clearAllChildren();
            var label = new Label();
            label.setText(Component.translatable("ebe.materials.large_refresh_hint"));
            label.textStyle(ts -> ts.textColor(0xFFAAAAAA).fontSize(8).textShadow(false)
                    .textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
            materialListContainer.addChild(label);
        }
        var total = findById(rootElement, "materialTotalLabel");
        if (total instanceof Label label) {
            label.setText(Component.translatable("ebe.materials.needs_refresh"));
        }
    }

    private static boolean showInventoryCompare = false;

    private static void toggleInventoryCompare() {
        showInventoryCompare = !showInventoryCompare;
        updateMaterialList();
    }

    private static void showMaterialDiffDialog() {
        var tags = history.getVersionTagsForCurrentBranch();
        if (tags.isEmpty()) {
            Dialog.showNotification("ebe.materials.diff_calc",
                    Component.translatable("ebe.history.empty").getString(), (Runnable) null)
                    .show(rootElement);
            return;
        }

        var dialog = new Dialog();
        dialog.setTitle("ebe.materials.diff_calc");
        dialog.darkenBackground();

        var fromLabel = new Label();
        fromLabel.setText(Component.translatable("ebe.materials.diff_from_tag"));
        fromLabel.textStyle(ts -> ts.fontSize(9).textShadow(false));
        dialog.addContent(fromLabel);

        var tagButtons = new UIElement();
        tagButtons.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));

        for (var tag : tags) {
            var btn = new Button();
            btn.setText(Component.literal(tag.label() + " (" + formatTimestamp(tag.timestamp()) + ")"));
            btn.textStyle(ts -> ts.fontSize(9).textShadow(false));
            btn.layout(l -> l.widthPercent(100));
            var capturedTag = tag;
            btn.setOnClick(e -> {
                dialog.close();
                showMaterialDiffResult(capturedTag);
            });
            tagButtons.addChild(btn);
        }

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).height(120));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.addScrollViewChild(tagButtons);
        dialog.addContent(scroller);

        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText("ebe.history.dialog.cancel")
                .addClass("__cancel-button__"));
        dialog.show(rootElement);
    }

    private static void showMaterialDiffResult(com.l1ght.ebe.editor.history.HistoryManager.VersionTag tag) {
        var diff = history.computeMaterialDiffFromTag(tag);
        if (diff.isEmpty()) return;

        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.materials.diff_calc").getString() + ": " + tag.label());
        dialog.darkenBackground();

        var listContainer = new UIElement();
        listContainer.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(1));

        var added = new ArrayList<Map.Entry<String, Integer>>();
        var removed = new ArrayList<Map.Entry<String, Integer>>();
        for (var entry : diff.entrySet()) {
            if (entry.getValue() > 0) added.add(entry);
            else removed.add(entry);
        }

        if (!added.isEmpty()) {
            var addedHeader = new Label();
            addedHeader.setText(Component.translatable("ebe.materials.diff_added"));
            addedHeader.textStyle(ts -> ts.textColor(0xFF55FF55).fontSize(9).textShadow(false));
            listContainer.addChild(addedHeader);
            for (var entry : added) {
                var row = new UIElement();
                row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .alignItems(AlignItems.CENTER).gapAll(4).paddingHorizontal(4));
                var name = new Label();
                name.setText(Component.literal(entry.getKey()));
                name.textStyle(ts -> ts.fontSize(8).textColor(0xFFDDDDDD).textShadow(false));
                name.layout(l -> l.flex(1));
                row.addChild(name);
                var count = new Label();
                count.setText(Component.literal("+" + entry.getValue()));
                count.textStyle(ts -> ts.fontSize(8).textColor(0xFF55FF55).textShadow(false));
                row.addChild(count);
                listContainer.addChild(row);
            }
        }

        if (!removed.isEmpty()) {
            var removedHeader = new Label();
            removedHeader.setText(Component.translatable("ebe.materials.diff_removed"));
            removedHeader.textStyle(ts -> ts.textColor(0xFFFF5555).fontSize(9).textShadow(false));
            listContainer.addChild(removedHeader);
            for (var entry : removed) {
                var row = new UIElement();
                row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .alignItems(AlignItems.CENTER).gapAll(4).paddingHorizontal(4));
                var name = new Label();
                name.setText(Component.literal(entry.getKey()));
                name.textStyle(ts -> ts.fontSize(8).textColor(0xFFDDDDDD).textShadow(false));
                name.layout(l -> l.flex(1));
                row.addChild(name);
                var count = new Label();
                count.setText(Component.literal(String.valueOf(entry.getValue())));
                count.textStyle(ts -> ts.fontSize(8).textColor(0xFFFF5555).textShadow(false));
                row.addChild(count);
                listContainer.addChild(row);
            }
        }

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).height(200));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        scroller.addScrollViewChild(listContainer);
        dialog.addContent(scroller);

        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText("ebe.history.dialog.confirm")
                .addClass("__confirm-button__"));
        dialog.show(rootElement);
    }

    private static void updateMaterialList() {
        if (materialListContainer == null) return;
        materialListContainer.clearAllChildren();

        var model = session.getModel();
        if (model == null || model.getRegions().isEmpty()) return;

        record BlockKey(net.minecraft.world.level.block.Block block, String nbt) {}
        Map<BlockKey, Integer> counts = new LinkedHashMap<>();
        Map<String, List<BlockKey>> byMod = new LinkedHashMap<>();

        for (var region : model.getRegions()) {
            var blocks = region.getBlocks();
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = blocks.get(x, y, z);
                        net.minecraft.world.level.block.Block block = null;
                        if (obj instanceof net.minecraft.world.level.block.state.BlockState bs && !bs.isAir()) {
                            block = bs.getBlock();
                        } else if (obj instanceof String s && !s.isEmpty() && !s.equals("minecraft:air")) {
                            var locStr = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
                            var loc = net.minecraft.resources.ResourceLocation.parse(locStr);
                            var opt = BuiltInRegistries.BLOCK.getOptional(loc);
                            if (opt.isEmpty()) continue;
                            block = opt.get();
                        }
                        if (block == null) continue;

                        var beNbt = region.getBlockEntity(x, y, z);
                        String nbtKey = "";
                        if (beNbt != null && !beNbt.isEmpty()) {
                            var cleaned = beNbt.copy();
                            cleaned.remove("x"); cleaned.remove("y"); cleaned.remove("z"); cleaned.remove("id");
                            nbtKey = cleaned.toString();
                        }
                        var key = new BlockKey(block, nbtKey);
                        counts.merge(key, 1, Integer::sum);
                    }
                }
            }
        }

        for (var entry : counts.entrySet()) {
            var modId = BuiltInRegistries.BLOCK.getKey(entry.getKey().block()).getNamespace();
            byMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(entry.getKey());
        }

        var mc = Minecraft.getInstance();
        var player = mc.player;
        Map<net.minecraft.world.item.Item, Integer> inventoryCounts = new LinkedHashMap<>();
        if (showInventoryCompare && player != null) {
            for (var stack : player.getInventory().items) {
                if (!stack.isEmpty()) {
                    inventoryCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
        }

        int totalBlocks = counts.values().stream().mapToInt(Integer::intValue).sum();
        int totalTypes = counts.size();
        var totalLabel = findById(rootElement, "materialTotalLabel");
        if (totalLabel instanceof Label tl) {
            tl.setText(Component.translatable("ebe.materials.total", totalBlocks, totalTypes));
        }

        for (var modEntry : byMod.entrySet()) {
            var modId = modEntry.getKey();
            var modBlocks = modEntry.getValue();

            var modHeader = new UIElement();
            modHeader.layout(l -> l.widthPercent(100).paddingHorizontal(2).paddingVertical(1));
            modHeader.style(s -> s.background(Sprites.RECT_RD));
            var modLabel = new Label();
            modLabel.setText(Component.literal(modId + " (" + modBlocks.size() + ")"));
            modLabel.textStyle(ts -> ts.fontSize(9).textColor(0xFFAAAAFF).textShadow(false));
            modHeader.addChild(modLabel);
            materialListContainer.addChild(modHeader);

            for (var blockKey : modBlocks) {
                var count = counts.get(blockKey);
                var block = blockKey.block();
                var item = block.asItem();
                var blockName = block.getName().getString();
                var hasNbt = !blockKey.nbt().isEmpty();

                var row = new UIElement();
                row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN)
                        .paddingHorizontal(4).paddingVertical(2).gapAll(1));
                row.style(s -> s.background(Sprites.RECT_DARK));

                var mainLine = new UIElement();
                mainLine.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                        .alignItems(AlignItems.CENTER).gapAll(4).flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP));

                var icon = new UIElement();
                icon.layout(l -> l.width(16).height(16).flexShrink(0));
                icon.style(s -> s.backgroundTexture(new ItemStackTexture(item)));
                var tooltipText = Component.literal(blockName + (hasNbt ? " [NBT]" : "") + "\n" + net.minecraft.ChatFormatting.GRAY + modId);
                icon.addEventListener(UIEvents.MOUSE_ENTER, e -> icon.style(s -> s.tooltips(tooltipText)));
                mainLine.addChild(icon);

                var nameLabel = new Label();
                nameLabel.setText(Component.literal(blockName + (hasNbt ? " *" : "")));
                nameLabel.textStyle(ts -> ts.fontSize(9).textColor(hasNbt ? 0xFFAAFFFF : 0xFFDDDDDD).textShadow(false));
                nameLabel.layout(l -> l.flex(1).minWidth(0));
                mainLine.addChild(nameLabel);

                if (showInventoryCompare) {
                    int invCount = inventoryCounts.getOrDefault(item, 0);
                    int diff = count - invCount;
                    var diffLabel = new Label();
                    if (diff <= 0) {
                        diffLabel.setText(Component.translatable("ebe.materials.enough_count", count, invCount));
                        diffLabel.textStyle(ts -> ts.fontSize(8).textColor(0xFF55FF55).textShadow(false));
                    } else {
                        diffLabel.setText(Component.literal(count + "/" + invCount + " -" + diff));
                        diffLabel.textStyle(ts -> ts.fontSize(8).textColor(0xFFFF5555).textShadow(false));
                    }
                    diffLabel.layout(l -> l.width(60).flexShrink(0));
                    mainLine.addChild(diffLabel);
                } else {
                    var countLabel = new Label();
                    countLabel.setText(Component.literal("x" + count));
                    countLabel.textStyle(ts -> ts.fontSize(9).textColor(0xFFCCCCCC).textShadow(false));
                    countLabel.layout(l -> l.width(40).flexShrink(0));
                    mainLine.addChild(countLabel);
                }

                row.addChild(mainLine);

                if (hasNbt) {
                    var nbtHint = new Label();
                    var shortNbt = blockKey.nbt().length() > 60 ? blockKey.nbt().substring(0, 57) + "..." : blockKey.nbt();
                    nbtHint.setText(Component.translatable("ebe.nbt.value", shortNbt));
                    nbtHint.textStyle(ts -> ts.fontSize(7).textColor(0xFF888888).textShadow(false));
                    row.addChild(nbtHint);
                }

                materialListContainer.addChild(row);
            }
        }
    }

    private static void exportMaterialList() {
        var model = session.getModel();
        if (model == null) return;

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var region : model.getRegions()) {
            var blocks = region.getBlocks();
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = blocks.get(x, y, z);
                        if (obj instanceof net.minecraft.world.level.block.state.BlockState bs && !bs.isAir()) {
                            var key = BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString();
                            counts.merge(key, 1, Integer::sum);
                        } else if (obj instanceof String s && !s.isEmpty() && !s.equals("minecraft:air")) {
                            var locStr = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
                            counts.merge(locStr, 1, Integer::sum);
                        }
                    }
                }
            }
        }

        try {
            var dir = Path.of(EBEClientConfig.schematicDir.get());
            Files.createDirectories(dir);
            var baseName = session.getCurrentName();

            var csvFile = dir.resolve(baseName + "_materials.csv");
            try (var writer = Files.newBufferedWriter(csvFile)) {
                writer.write("block,count\n");
                for (var entry : counts.entrySet()) {
                    writer.write(entry.getKey() + "," + entry.getValue() + "\n");
                }
            }

            var jsonFile = dir.resolve(baseName + "_materials.json");
            var jsonArr = new com.google.gson.JsonArray();
            for (var entry : counts.entrySet()) {
                var obj = new com.google.gson.JsonObject();
                obj.addProperty("block", entry.getKey());
                obj.addProperty("count", entry.getValue());
                jsonArr.add(obj);
            }
            try (var writer = Files.newBufferedWriter(jsonFile)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(jsonArr, writer);
            }
        } catch (Exception ignored) {}
    }

    // ========== Status Bar ==========

    public static void updateStatusBar() {
        if (rootElement == null) return;
        if (ViewportFactory.isProgressiveLoadActive()) return;
        var status = findById(rootElement, "statusBar");
        if (status instanceof Label l) {
            l.setText(Component.literal(state.buildStatusText()));
        }
    }

    public static void setMeasuredFps(int fps) {
        state.setFps(Math.max(0, fps));
    }

    // ========== Key Input Handler (called from EditorScreen) ==========

    public static boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        if (isTextFieldFocused()) return false;
        if (KeyRecordingManager.isRecording()) return true;

        if (EBEKeyBindings.UNDO.matchesKey(keyCode, modifiers)) { undo(); return true; }
        if (EBEKeyBindings.REDO.matchesKey(keyCode, modifiers)) { redo(); return true; }
        if (EBEKeyBindings.COPY.matchesKey(keyCode, modifiers)) { clipboard.copy(session.getModel(), selection); return true; }
        if (EBEKeyBindings.PASTE.matchesKey(keyCode, modifiers)) {
            int beforeUndo = history.undoSize();
            clipboard.paste(session.getModel(), new net.minecraft.core.BlockPos(
                    state.getCursorX(), state.getCursorY(), state.getCursorZ()), history);
            finishClipboardMutation(beforeUndo);
            return true;
        }
        if (EBEKeyBindings.CUT.matchesKey(keyCode, modifiers)) {
            int beforeUndo = history.undoSize();
            clipboard.cut(session.getModel(), selection, history);
            finishClipboardMutation(beforeUndo);
            updateSelectionCount();
            return true;
        }
        if (EBEKeyBindings.SELECT_ALL.matchesKey(keyCode, modifiers)) { selectAll(); return true; }
        if (EBEKeyBindings.DESELECT.matchesKey(keyCode, modifiers)) { selection.clear(); updateSelectionCount(); return true; }

        if (EBEKeyBindings.TOOL_SELECT.matchesKey(keyCode, modifiers)) { selectTool(EditorTool.SELECT); return true; }
        if (EBEKeyBindings.TOOL_PLACE.matchesKey(keyCode, modifiers)) { selectTool(EditorTool.PLACE); return true; }
        if (EBEKeyBindings.TOOL_DELETE.matchesKey(keyCode, modifiers)) { selectTool(EditorTool.DELETE); return true; }
        if (EBEKeyBindings.TOOL_REPLACE.matchesKey(keyCode, modifiers)) { selectTool(EditorTool.REPLACE); return true; }
        if (EBEKeyBindings.TOOL_GRAB.matchesKey(keyCode, modifiers)) { selectTool(EditorTool.GRAB); return true; }
        if (EBEKeyBindings.TOOL_MEASURE.matchesKey(keyCode, modifiers)) { selectTool(EditorTool.MEASURE); return true; }
        if (EBEKeyBindings.TOOL_FILL.matchesKey(keyCode, modifiers)) { selectTool(EditorTool.FILL); return true; }
        return false;
    }

    private static void setupPanelDrag(UIElement panel, String titleBarId) {
        var titleBar = findById(panel, titleBarId);
        if (titleBar == null) return;
        titleBar.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                draggingPanel = panel;
                panelDragOffsetX = e.x - panel.getPositionX();
                panelDragOffsetY = e.y - panel.getPositionY();
            }
        });
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
