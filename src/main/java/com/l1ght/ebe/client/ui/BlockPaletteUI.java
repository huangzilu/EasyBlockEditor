package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class BlockPaletteUI {

    private static final Logger LOG = LoggerFactory.getLogger("EBE/BlockPalette");

    private static UIElement palettePanel;
    private static boolean paletteOpen = false;
    private static Label currentBlockLabel;
    private static float dragOffsetX, dragOffsetY;
    private static boolean dragging = false;

    private static boolean persistedOpen = false;
    private static float persistedLeft = 8;
    private static float persistedTop = 30;
    private static Consumer<BlockState> pendingCallback = null;
    private static Dialog pendingCallbackDialog = null;

    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 9;
    private static final int GRID_WIDTH = SLOT_SIZE * SLOTS_PER_ROW + SLOTS_PER_ROW - 1;

    // Building the full grid for a tab instantiates 2 UIElements + 1 ItemStackTexture per block.
    // With many mods the "All" tab has thousands of blocks, so we render in batches and let the
    // user pull more in with a button instead of freezing while 10k+ nodes lay out at once.
    private static final int GRID_BATCH = 512;

    // buildTabMap() calls CreativeModeTab.buildContents() for every category tab — expensive and
    // pointless to repeat on every dialog open, since the registry doesn't change mid-session.
    private static Map<CreativeModeTab, List<ItemStack>> cachedTabMap = null;

    // One ItemStackTexture per Block, shared across every slot/tab/search result that shows it.
    private static final Map<Block, ItemStackTexture> ICON_CACHE = new java.util.HashMap<>();

    private static ItemStackTexture iconFor(BlockItem bi) {
        return ICON_CACHE.computeIfAbsent(bi.getBlock(), b -> new ItemStackTexture(bi));
    }

    private static List<ItemStack> getTabBlockItems(CreativeModeTab tab) {
        List<ItemStack> result = new ArrayList<>();
        try {
            var mc = Minecraft.getInstance();
            var featureFlags = mc.level != null ? mc.level.enabledFeatures() : FeatureFlagSet.of();
            boolean hasPermissions = mc.player != null && mc.player.canUseGameMasterBlocks();
            var registryAccess = mc.level != null
                    ? mc.level.registryAccess()
                    : mc.getConnection().registryAccess();

            var params = new CreativeModeTab.ItemDisplayParameters(featureFlags, hasPermissions, registryAccess);
            tab.buildContents(params);
            for (var stack : tab.getDisplayItems()) {
                if (stack.getItem() instanceof BlockItem) {
                    result.add(stack.copy());
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to get tab items for {}", BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab), e);
        }
        return result;
    }

    private static Map<CreativeModeTab, List<ItemStack>> buildTabMap() {
        if (cachedTabMap != null) return cachedTabMap;
        Map<CreativeModeTab, List<ItemStack>> tabMap = new LinkedHashMap<>();

        for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            var items = getTabBlockItems(tab);
            if (!items.isEmpty()) {
                tabMap.put(tab, items);
            }
        }

        if (tabMap.isEmpty()) {
            LOG.warn("No tab items found, falling back to simple block list");
            List<ItemStack> allBlocks = new ArrayList<>();
            BuiltInRegistries.BLOCK.forEach(block -> {
                var item = block.asItem();
                if (item != Items.AIR && item instanceof BlockItem) {
                    allBlocks.add(new ItemStack(item));
                }
            });
            tabMap.put(null, allBlocks);
        }

        cachedTabMap = tabMap;
        return tabMap;
    }

    public static void togglePalette(UIElement parent) {
        if (paletteOpen && palettePanel != null) {
            closePalette();
        } else {
            openPalette(parent);
        }
    }

    public static void openPalette(UIElement parent) {
        closePalette();

        var panel = new UIElement();
        panel.layout(l -> l.positionType(TaffyPosition.ABSOLUTE)
                .left(persistedLeft).top(persistedTop).width(GRID_WIDTH + 10).heightPercent(70)
                .flexDirection(FlexDirection.COLUMN).paddingAll(4).gapAll(4));
        panel.style(s -> s.background(Sprites.BORDER).zIndex(500));
        panel.setId("blockPalette");

        var titleBar = new UIElement();
        titleBar.setId("paletteTitleBar");
        titleBar.layout(l -> l.widthPercent(100).height(18).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER));
        var title = new Label();
        title.setText(Component.translatable("ebe.editor.block_palette"));
        title.textStyle(ts -> ts.textColor(0xFFE0E0E0).textShadow(false));
        titleBar.addChild(title);
        var spacer = new UIElement();
        spacer.layout(l -> l.flex(1));
        titleBar.addChild(spacer);
        var closeBtn = new Button();
        closeBtn.setText(Component.literal("X"));
        closeBtn.layout(l -> l.width(16).height(16));
        closeBtn.setOnClick(e -> closePalette());
        titleBar.addChild(closeBtn);
        panel.addChild(titleBar);

        currentBlockLabel = new Label();
        currentBlockLabel.setText(Component.translatable("ebe.editor.palette.selected_none"));
        currentBlockLabel.textStyle(ts -> ts.textColor(0xFFFFD700).textShadow(false));
        panel.addChild(currentBlockLabel);

        var invLabel = new Label();
        invLabel.setText(Component.translatable("ebe.editor.palette.inventory"));
        invLabel.textStyle(ts -> ts.textColor(0xFFC0C0C0).textShadow(false));
        panel.addChild(invLabel);

        var invGrid = buildInventoryGrid();
        panel.addChild(invGrid);

        var player = Minecraft.getInstance().player;
        var isCreative = player != null && player.isCreative();
        var browseBtn = new Button();
        browseBtn.setText(Component.translatable("ebe.editor.palette.browse_all"));
        browseBtn.layout(l -> l.widthPercent(100).height(18));
        if (isCreative) {
            browseBtn.setOnClick(e -> showBrowseDialog(parent));
        } else {
            browseBtn.style(s -> s.opacity(0.4f));
        }
        panel.addChild(browseBtn);

        setupDrag(panel);

        parent.addChild(panel);
        palettePanel = panel;
        paletteOpen = true;
    }

    private static UIElement buildInventoryGrid() {
        var grid = new UIElement();
        grid.setId("inventoryGrid");
        grid.layout(l -> l.width(GRID_WIDTH).flexDirection(FlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP).gapAll(1));

        var player = Minecraft.getInstance().player;
        if (player == null) return grid;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < SLOTS_PER_ROW; col++) {
                int slotIdx = 9 + row * SLOTS_PER_ROW + col;
                var stack = player.getInventory().getItem(slotIdx);
                grid.addChild(createInventorySlot(stack, slotIdx));
            }
        }

        var separator = new UIElement();
        separator.layout(l -> l.widthPercent(100).height(2));
        grid.addChild(separator);

        for (int col = 0; col < SLOTS_PER_ROW; col++) {
            var stack = player.getInventory().getItem(col);
            grid.addChild(createInventorySlot(stack, col));
        }

        return grid;
    }

    private static UIElement createInventorySlot(ItemStack stack, int slotIdx) {
        var slot = new UIElement();
        slot.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE));
        slot.style(s -> s.background(Sprites.RECT_DARK));

        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi) {
            var icon = new UIElement();
            icon.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE));
            icon.style(s -> s.backgroundTexture(iconFor(bi)));
            icon.addEventListener(UIEvents.CLICK, e -> {
                if (e.button == 0) {
                    EditorUI.getState().setActiveBlockState(bi.getBlock().defaultBlockState());
                    updateCurrentBlockLabel();
                    EditorUI.updateActiveBlockIndicator();
                }
            });
            icon.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                icon.style(s -> s.tooltips(buildBlockTooltip(bi.getBlock())));
            });
            slot.addChild(icon);
        }

        return slot;
    }

    private static Component buildBlockTooltip(Block block) {
        var name = block.getName();
        var id = BuiltInRegistries.BLOCK.getKey(block);
        var modId = id != null ? id.getNamespace() : "minecraft";
        return Component.empty().append(name).append(Component.literal("\n"))
                .append(Component.literal(modId).withStyle(s -> s.withColor(0xFF888888)));
    }

    private static void setupDrag(UIElement panel) {
        var titleBar = UIUtils.findById(panel, "paletteTitleBar");
        if (titleBar == null) return;

        titleBar.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button == 0) {
                dragging = true;
                dragOffsetX = e.x - panel.getPositionX();
                dragOffsetY = e.y - panel.getPositionY();
            }
        });

        panel.addEventListener(UIEvents.MOUSE_MOVE, e -> {
            if (dragging) {
                float newX = e.x - dragOffsetX;
                float newY = e.y - dragOffsetY;
                panel.layout(l -> l.left(newX).top(newY));
            }
        });

        panel.addEventListener(UIEvents.MOUSE_UP, e -> {
            if (e.button == 0) dragging = false;
        });
    }

    public static void closePalette() {
        if (palettePanel != null) {
            persistedLeft = palettePanel.getPositionX();
            persistedTop = palettePanel.getPositionY();
            palettePanel.removeSelf();
            palettePanel = null;
        }
        paletteOpen = false;
        persistedOpen = false;
        currentBlockLabel = null;
        dragging = false;
    }

    public static void saveState() {
        persistedOpen = paletteOpen;
        if (palettePanel != null) {
            persistedLeft = palettePanel.getPositionX();
            persistedTop = palettePanel.getPositionY();
        }
    }

    public static void restorePalette(UIElement parent) {
        if (persistedOpen) {
            openPalette(parent);
            updateCurrentBlockLabel();
        }
    }

    public static boolean isPaletteOpen() {
        return paletteOpen;
    }

    public static void updateCurrentBlockLabel() {
        if (currentBlockLabel == null) return;
        var blockState = EditorUI.getState().getActiveBlockState();
        if (blockState != null) {
            currentBlockLabel.setText(Component.translatable("ebe.editor.palette.selected",
                    blockState.getBlock().getName()));
        } else {
            currentBlockLabel.setText(Component.translatable("ebe.editor.palette.selected_none"));
        }
    }

    private static void showBrowseDialog(UIElement parent) {
        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.editor.palette.browse_all").getString());
        dialog.overlay.layout(l -> l.width(GRID_WIDTH + 40).maxHeightPercent(80));

        var searchRow = new UIElement();
        searchRow.layout(l -> l.widthPercent(100).height(20).flexDirection(FlexDirection.ROW).gapAll(4));

        var searchLabel = new Label();
        searchLabel.setText(Component.literal("🔍"));
        searchLabel.textStyle(ts -> ts.textShadow(false));
        searchRow.addChild(searchLabel);

        var searchInput = new TextField();
        searchInput.layout(l -> l.flex(1).height(18));
        searchInput.textFieldStyle(s -> s.placeholder(Component.translatable("ebe.editor.palette.search")));
        searchRow.addChild(searchInput);

        dialog.addContent(searchRow);

        var tabView = new TabView();
        tabView.layout(l -> l.widthPercent(100).flex(1));
        tabView.setId("browseTabView");
        tabView.tabScroller(s -> s.scrollerStyle(style -> style.horizontalScrollDisplay(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay.ALWAYS)));

        var tabMap = buildTabMap();

        // Lazy tab content: each tab starts with an empty container and its (batched) grid is built
        // only the first time the tab is actually selected. Opening the dialog no longer eagerly
        // builds every tab's grid — especially the giant "All" tab.
        Map<Tab, Runnable> lazyBuilders = new java.util.HashMap<>();
        tabView.setOnTabSelected(tab -> {
            Runnable builder = lazyBuilders.remove(tab);
            if (builder != null) builder.run();
        });

        List<ItemStack> allBlocks = new ArrayList<>();
        for (var items : tabMap.values()) allBlocks.addAll(items);
        if (!allBlocks.isEmpty()) {
            var allTab = new Tab();
            allTab.layout(l -> l.width(22).height(22).paddingAll(3));
            var allIcon = new UIElement();
            allIcon.layout(l -> l.widthPercent(100).heightPercent(100));
            allIcon.style(s -> s.backgroundTexture(Sprites.RECT_DARK));
            allIcon.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                allIcon.style(s2 -> s2.tooltips(Component.translatable("ebe.editor.palette.tab_all")));
            });
            allTab.addChild(allIcon);
            var allContent = new UIElement();
            allContent.layout(l -> l.widthPercent(100).flex(1));
            lazyBuilders.put(allTab, () -> allContent.addChild(buildScrolledGrid(allBlocks)));
            tabView.addTab(allTab, allContent);
        }

        for (var entry : tabMap.entrySet()) {
            var tab = entry.getKey();
            var tabItems = entry.getValue();
            if (tabItems.isEmpty()) continue;

            var tabBtn = new Tab();
            tabBtn.layout(l -> l.width(22).height(22).paddingAll(3));

            if (tab != null) {
                var iconStack = tab.getIconItem();
                var iconEl = new UIElement();
                iconEl.layout(l -> l.widthPercent(100).heightPercent(100));
                if (iconStack != null && !iconStack.isEmpty()) {
                    iconEl.style(s -> s.backgroundTexture(new ItemStackTexture(iconStack)));
                }
                var tabName = tab.getDisplayName();
                iconEl.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                    iconEl.style(s2 -> s2.tooltips(tabName));
                });
                tabBtn.addChild(iconEl);
            }

            var content = new UIElement();
            content.layout(l -> l.widthPercent(100).flex(1));
            lazyBuilders.put(tabBtn, () -> content.addChild(buildScrolledGrid(tabItems)));
            tabView.addTab(tabBtn, content);
        }

        // Populate whichever tab is initially selected (otherwise its onTabSelected won't fire).
        if (tabView.getSelectedTab() != null) {
            Runnable initial = lazyBuilders.remove(tabView.getSelectedTab());
            if (initial != null) initial.run();
        }

        var searchResultsContainer = new UIElement();
        searchResultsContainer.setId("searchResultsContainer");
        searchResultsContainer.layout(l -> l.widthPercent(100).flex(1));
        searchResultsContainer.setDisplay(false);
        dialog.addContent(searchResultsContainer);

        searchInput.setTextResponder(text -> {
            var filter = text.toLowerCase();
            if (filter.isEmpty()) {
                tabView.setDisplay(true);
                searchResultsContainer.setDisplay(false);
                new ArrayList<>(searchResultsContainer.getChildren()).forEach(UIElement::removeSelf);
                return;
            }

            List<ItemStack> filtered = new ArrayList<>();
            for (var entry : tabMap.entrySet()) {
                for (var stack : entry.getValue()) {
                    if (stack.getItem() instanceof BlockItem bi) {
                        var name = bi.getBlock().getName().getString().toLowerCase();
                        var id = BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString().toLowerCase();
                        if (name.contains(filter) || id.contains(filter)) {
                            filtered.add(stack.copy());
                        }
                    }
                }
            }

            tabView.setDisplay(false);
            new ArrayList<>(searchResultsContainer.getChildren()).forEach(UIElement::removeSelf);
            var searchContent = buildScrolledGrid(filtered);
            searchResultsContainer.addChild(searchContent);
            searchResultsContainer.setDisplay(true);
        });

        dialog.addContent(tabView);

        dialog.addButton(new Button()
                .setText(Component.translatable("ebe.editor.palette.close"))
                .setOnClick(e -> dialog.close()));

        dialog.show(parent);
    }

    private static UIElement buildScrolledGrid(List<ItemStack> items) {
        return buildBatchedGrid(items, bi -> {
            EditorUI.getState().setActiveBlockState(bi.getBlock().defaultBlockState());
            updateCurrentBlockLabel();
            EditorUI.updateActiveBlockIndicator();
        });
    }

    /**
     * Builds a scrollable block grid that materialises at most {@link #GRID_BATCH} slots up front
     * and reveals the rest in batches via a "show more" button. This keeps the "All" tab from
     * instantiating tens of thousands of UI nodes (and laying them all out) in a single frame.
     */
    private static UIElement buildBatchedGrid(List<ItemStack> items, Consumer<BlockItem> onPick) {
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).maxHeight(200));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));

        var grid = new UIElement();
        grid.layout(l -> l.width(GRID_WIDTH).flexDirection(FlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP).gapAll(1));

        // Only BlockItems get a slot; pre-filter so batching counts real slots, not skipped items.
        List<BlockItem> blockItems = new ArrayList<>();
        for (var stack : items) {
            if (stack.getItem() instanceof BlockItem bi) blockItems.add(bi);
        }

        int[] shown = {0};
        var moreBtn = new Button();

        Runnable[] revealMore = new Runnable[1];
        revealMore[0] = () -> {
            int end = Math.min(shown[0] + GRID_BATCH, blockItems.size());
            for (int i = shown[0]; i < end; i++) {
                grid.addChild(buildBlockSlot(blockItems.get(i), onPick));
            }
            shown[0] = end;
            if (shown[0] >= blockItems.size()) {
                moreBtn.setDisplay(false);
            } else {
                moreBtn.setText(Component.translatable("ebe.editor.palette.show_more",
                        blockItems.size() - shown[0]));
            }
        };

        revealMore[0].run();

        scroller.addScrollViewChild(grid);
        container.addChild(scroller);

        if (shown[0] < blockItems.size()) {
            moreBtn.setText(Component.translatable("ebe.editor.palette.show_more",
                    blockItems.size() - shown[0]));
            moreBtn.layout(l -> l.widthPercent(100).height(16));
            moreBtn.textStyle(ts -> ts.fontSize(8).textShadow(false));
            moreBtn.setOnClick(e -> revealMore[0].run());
            container.addChild(moreBtn);
        } else {
            moreBtn.setDisplay(false);
        }

        return container;
    }

    private static UIElement buildBlockSlot(BlockItem bi, Consumer<BlockItem> onPick) {
        var slotEl = new UIElement();
        slotEl.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE));
        slotEl.style(s -> s.background(Sprites.RECT_DARK));

        var icon = new UIElement();
        icon.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE));
        icon.style(s -> s.backgroundTexture(iconFor(bi)));
        icon.addEventListener(UIEvents.CLICK, e -> onPick.accept(bi));
        icon.addEventListener(UIEvents.MOUSE_ENTER, e -> {
            icon.style(s -> s.tooltips(buildBlockTooltip(bi.getBlock())));
        });
        slotEl.addChild(icon);
        return slotEl;
    }

    public static void openWithCallback(UIElement parent, Consumer<BlockState> callback) {
        pendingCallback = callback;
        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.editor.palette.browse_all").getString());
        dialog.overlay.layout(l -> l.width(GRID_WIDTH + 40).maxHeightPercent(80));
        pendingCallbackDialog = dialog;

        var searchRow = new UIElement();
        searchRow.layout(l -> l.widthPercent(100).height(20).flexDirection(FlexDirection.ROW).gapAll(4));
        var searchLabel = new Label();
        searchLabel.setText(Component.literal("🔍"));
        searchLabel.textStyle(ts -> ts.textShadow(false));
        searchRow.addChild(searchLabel);
        var searchInput = new TextField();
        searchInput.layout(l -> l.flex(1).height(18));
        searchInput.textFieldStyle(s -> s.placeholder(Component.translatable("ebe.editor.palette.search")));
        searchRow.addChild(searchInput);
        dialog.addContent(searchRow);

        var tabView = new TabView();
        tabView.layout(l -> l.widthPercent(100).flex(1));
        tabView.setId("browseCallbackTabView");
        tabView.tabScroller(s -> s.scrollerStyle(style -> style.horizontalScrollDisplay(com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay.ALWAYS)));

        var tabMap = buildTabMap();

        Map<Tab, Runnable> lazyBuilders = new java.util.HashMap<>();
        tabView.setOnTabSelected(tab -> {
            Runnable builder = lazyBuilders.remove(tab);
            if (builder != null) builder.run();
        });

        List<ItemStack> allBlocks = new ArrayList<>();
        for (var items : tabMap.values()) allBlocks.addAll(items);
        if (!allBlocks.isEmpty()) {
            var allTab = new Tab();
            allTab.layout(l -> l.width(22).height(22).paddingAll(3));
            var allIcon = new UIElement();
            allIcon.layout(l -> l.widthPercent(100).heightPercent(100));
            allIcon.style(s -> s.backgroundTexture(Sprites.RECT_DARK));
            allIcon.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                allIcon.style(s2 -> s2.tooltips(Component.translatable("ebe.editor.palette.tab_all")));
            });
            allTab.addChild(allIcon);
            var allContent = new UIElement();
            allContent.layout(l -> l.widthPercent(100).flex(1));
            lazyBuilders.put(allTab, () -> allContent.addChild(buildCallbackScrolledGrid(allBlocks)));
            tabView.addTab(allTab, allContent);
        }

        for (var entry : tabMap.entrySet()) {
            var tab = entry.getKey();
            var tabItems = entry.getValue();
            if (tabItems.isEmpty()) continue;
            var tabBtn = new Tab();
            tabBtn.layout(l -> l.width(22).height(22).paddingAll(3));
            if (tab != null) {
                var iconStack = tab.getIconItem();
                var iconEl = new UIElement();
                iconEl.layout(l -> l.widthPercent(100).heightPercent(100));
                if (iconStack != null && !iconStack.isEmpty()) {
                    iconEl.style(s -> s.backgroundTexture(new ItemStackTexture(iconStack)));
                }
                var tabName = tab.getDisplayName();
                iconEl.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                    iconEl.style(s2 -> s2.tooltips(tabName));
                });
                tabBtn.addChild(iconEl);
            }
            var content = new UIElement();
            content.layout(l -> l.widthPercent(100).flex(1));
            lazyBuilders.put(tabBtn, () -> content.addChild(buildCallbackScrolledGrid(tabItems)));
            tabView.addTab(tabBtn, content);
        }

        if (tabView.getSelectedTab() != null) {
            Runnable initial = lazyBuilders.remove(tabView.getSelectedTab());
            if (initial != null) initial.run();
        }

        var searchResultsContainer = new UIElement();
        searchResultsContainer.setId("callbackSearchResults");
        searchResultsContainer.layout(l -> l.widthPercent(100).flex(1));
        searchResultsContainer.setDisplay(false);
        dialog.addContent(searchResultsContainer);

        searchInput.setTextResponder(text -> {
            var filter = text.toLowerCase();
            if (filter.isEmpty()) {
                tabView.setDisplay(true);
                searchResultsContainer.setDisplay(false);
                new ArrayList<>(searchResultsContainer.getChildren()).forEach(UIElement::removeSelf);
                return;
            }
            List<ItemStack> filtered = new ArrayList<>();
            for (var items : tabMap.values()) {
                for (var stack : items) {
                    if (stack.getItem() instanceof BlockItem bi) {
                        var name = bi.getBlock().getName().getString().toLowerCase();
                        var id = BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString().toLowerCase();
                        if (name.contains(filter) || id.contains(filter)) {
                            filtered.add(stack.copy());
                        }
                    }
                }
            }
            tabView.setDisplay(false);
            new ArrayList<>(searchResultsContainer.getChildren()).forEach(UIElement::removeSelf);
            searchResultsContainer.addChild(buildCallbackScrolledGrid(filtered));
            searchResultsContainer.setDisplay(true);
        });

        dialog.addContent(tabView);
        dialog.addButton(new Button()
                .setText(Component.translatable("ebe.editor.palette.close"))
                .setOnClick(e -> {
                    pendingCallback = null;
                    pendingCallbackDialog = null;
                    dialog.close();
                }));
        dialog.show(parent);
    }

    private static UIElement buildCallbackScrolledGrid(List<ItemStack> items) {
        return buildBatchedGrid(items, bi -> {
            if (pendingCallback != null) {
                pendingCallback.accept(bi.getBlock().defaultBlockState());
            }
            if (pendingCallbackDialog != null) {
                pendingCallbackDialog.close();
            }
            pendingCallback = null;
            pendingCallbackDialog = null;
        });
    }
}
