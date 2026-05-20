package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 9;
    private static final int GRID_WIDTH = SLOT_SIZE * SLOTS_PER_ROW + SLOTS_PER_ROW - 1;

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
            icon.style(s -> s.backgroundTexture(new ItemStackTexture(bi)));
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
            tabView.addTab(allTab, buildScrolledGrid(allBlocks));
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

            var content = buildScrolledGrid(tabItems);
            tabView.addTab(tabBtn, content);
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
        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).maxHeight(200));

        var grid = new UIElement();
        grid.layout(l -> l.width(GRID_WIDTH).flexDirection(FlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP).gapAll(1));

        for (var stack : items) {
            if (!(stack.getItem() instanceof BlockItem bi)) continue;
            var slotEl = new UIElement();
            slotEl.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE));
            slotEl.style(s -> s.background(Sprites.RECT_DARK));

            var icon = new UIElement();
            icon.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE));
            icon.style(s -> s.backgroundTexture(new ItemStackTexture(bi)));
            icon.addEventListener(UIEvents.CLICK, e -> {
                EditorUI.getState().setActiveBlockState(bi.getBlock().defaultBlockState());
                updateCurrentBlockLabel();
                EditorUI.updateActiveBlockIndicator();
            });
            icon.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                icon.style(s -> s.tooltips(buildBlockTooltip(bi.getBlock())));
            });
            slotEl.addChild(icon);
            grid.addChild(slotEl);
        }

        scroller.addScrollViewChild(grid);
        container.addChild(scroller);

        return container;
    }
}
