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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class BlockPaletteUI {

    private static final Logger LOG = LoggerFactory.getLogger("EBE/BlockPalette");

    private static UIElement palettePanel;
    private static boolean paletteOpen = false;
    private static Label currentBlockLabel;
    private static float dragOffsetX, dragOffsetY;
    private static boolean dragging = false;

    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 9;
    private static final int GRID_WIDTH = SLOT_SIZE * SLOTS_PER_ROW + SLOTS_PER_ROW - 1;

    private static Field iconField;
    private static Field displayNameField;
    private static Field displayItemsGeneratorField;
    private static Class<?> itemDisplayParamsClass;
    private static Class<?> outputClass;
    private static Class<?> visibilityClass;
    private static boolean reflectionInitialized = false;

    private static void initReflection() {
        if (reflectionInitialized) return;
        try {
            iconField = CreativeModeTab.class.getDeclaredField("icon");
            iconField.setAccessible(true);

            displayNameField = CreativeModeTab.class.getDeclaredField("displayName");
            displayNameField.setAccessible(true);

            displayItemsGeneratorField = CreativeModeTab.class.getDeclaredField("displayItemsGenerator");
            displayItemsGeneratorField.setAccessible(true);

            for (var inner : CreativeModeTab.class.getDeclaredClasses()) {
                var name = inner.getSimpleName();
                LOG.info("Found inner class: {}", name);
                if (name.equals("ItemDisplayParameters")) {
                    itemDisplayParamsClass = inner;
                } else if (name.equals("Output")) {
                    outputClass = inner;
                } else if (name.equals("TabVisibility")) {
                    visibilityClass = inner;
                }
            }

            if (itemDisplayParamsClass == null) {
                try {
                    itemDisplayParamsClass = Class.forName("net.minecraft.world.item.CreativeModeTab$ItemDisplayParameters");
                } catch (ClassNotFoundException ignored) {}
            }

            if (outputClass == null) {
                try {
                    outputClass = Class.forName("net.minecraft.world.item.CreativeModeTab$Output");
                } catch (ClassNotFoundException ignored) {}
            }

            if (visibilityClass == null) {
                try {
                    visibilityClass = Class.forName("net.minecraft.world.item.CreativeModeTab$TabVisibility");
                } catch (ClassNotFoundException ignored) {}
            }

            if (itemDisplayParamsClass == null) {
                for (var method : CreativeModeTab.class.getDeclaredMethods()) {
                    if (method.getName().equals("buildContents") && method.getParameterCount() == 1) {
                        itemDisplayParamsClass = method.getParameterTypes()[0];
                        LOG.info("Found ItemDisplayParameters via buildContents method: {}", itemDisplayParamsClass.getName());
                        break;
                    }
                }
            }

            if (outputClass == null && displayItemsGeneratorField != null) {
                try {
                    var generatorType = displayItemsGeneratorField.getType();
                    for (var method : generatorType.getMethods()) {
                        if (method.getName().equals("accept") && method.getParameterCount() == 2) {
                            outputClass = method.getParameterTypes()[1];
                            LOG.info("Found Output via DisplayItemsGenerator: {}", outputClass.getName());
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (visibilityClass == null && outputClass != null) {
                for (var method : outputClass.getMethods()) {
                    if (method.getName().equals("accept") && method.getParameterCount() == 2) {
                        visibilityClass = method.getParameterTypes()[1];
                        LOG.info("Found TabVisibility via Output.accept: {}", visibilityClass.getName());
                        break;
                    }
                }
            }

            reflectionInitialized = true;
            LOG.info("CreativeModeTab reflection initialized: params={}, output={}, visibility={}",
                    itemDisplayParamsClass != null, outputClass != null, visibilityClass != null);
        } catch (Exception e) {
            LOG.error("Failed to init CreativeModeTab reflection", e);
            reflectionInitialized = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static Supplier<ItemStack> getTabIcon(CreativeModeTab tab) {
        try {
            return (Supplier<ItemStack>) iconField.get(tab);
        } catch (Exception e) {
            return null;
        }
    }

    private static Component getTabDisplayName(CreativeModeTab tab) {
        try {
            return (Component) displayNameField.get(tab);
        } catch (Exception e) {
            var key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            return Component.literal(key != null ? key.getPath() : "?");
        }
    }

    private static Object createItemDisplayParams() {
        if (itemDisplayParamsClass == null) {
            LOG.error("createItemDisplayParams: itemDisplayParamsClass is null");
            return null;
        }
        try {
            var mc = Minecraft.getInstance();
            var featureFlags = mc.level != null ? mc.level.enabledFeatures() : FeatureFlagSet.of();
            boolean hasPermissions = mc.player != null && mc.player.canUseGameMasterBlocks();
            var registryAccess = mc.level != null
                    ? mc.level.registryAccess()
                    : mc.getConnection().registryAccess();

            for (var ctor : itemDisplayParamsClass.getConstructors()) {
                var params = ctor.getParameterTypes();
                try {
                    if (params.length == 3
                            && params[0] == FeatureFlagSet.class
                            && params[1] == boolean.class) {
                        return ctor.newInstance(featureFlags, hasPermissions, registryAccess);
                    } else if (params.length == 2
                            && params[0] == FeatureFlagSet.class
                            && params[1] == boolean.class) {
                        return ctor.newInstance(featureFlags, hasPermissions);
                    }
                } catch (Exception e) {
                    LOG.debug("Constructor with {} params failed, trying next", params.length, e);
                }
            }

            LOG.error("No suitable constructor found for {}", itemDisplayParamsClass.getName());
            return null;
        } catch (Exception e) {
            LOG.error("Failed to create ItemDisplayParameters", e);
            return null;
        }
    }

    private static List<ItemStack> getTabBlockItems(CreativeModeTab tab, Object params) {
        List<ItemStack> result = new ArrayList<>();
        if (params == null || outputClass == null) return result;

        try {
            var generator = displayItemsGeneratorField.get(tab);
            if (generator == null) return result;

            var parentOnly = visibilityClass.getField("PARENT_TAB_ONLY").get(null);
            var parentAndSearch = visibilityClass.getField("PARENT_AND_SEARCH_TABS").get(null);

            var output = java.lang.reflect.Proxy.newProxyInstance(
                    outputClass.getClassLoader(),
                    new Class[]{outputClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals("accept") && args.length == 2) {
                            var stack = (ItemStack) args[0];
                            var visibility = args[1];
                            if (visibility == parentOnly || visibility == parentAndSearch) {
                                if (stack.getItem() instanceof BlockItem) {
                                    result.add(stack.copy());
                                }
                            }
                        } else if (method.getName().equals("accept") && args.length == 1) {
                            var stack = (ItemStack) args[0];
                            if (stack.getItem() instanceof BlockItem) {
                                result.add(stack.copy());
                            }
                        }
                        return null;
                    });

            var acceptMethod = generator.getClass().getMethod("accept", itemDisplayParamsClass, outputClass);
            acceptMethod.invoke(generator, params, output);
        } catch (Exception e) {
            LOG.error("Failed to get tab items via reflection for tab {}", BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab), e);
        }

        return result;
    }

    private static Map<CreativeModeTab, List<ItemStack>> buildTabMap() {
        initReflection();

        Map<CreativeModeTab, List<ItemStack>> tabMap = new LinkedHashMap<>();
        var params = createItemDisplayParams();

        if (params != null) {
            for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
                var items = getTabBlockItems(tab, params);
                if (!items.isEmpty()) {
                    tabMap.put(tab, items);
                }
            }
        }

        if (tabMap.isEmpty()) {
            LOG.warn("Reflection approach failed, falling back to simple block list");
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
                .left(8).top(30).width(GRID_WIDTH + 10).heightPercent(70)
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
                icon.style(s -> s.tooltips(bi.getBlock().getName()));
            });
            slot.addChild(icon);
        }

        return slot;
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
            palettePanel.removeSelf();
            palettePanel = null;
        }
        paletteOpen = false;
        currentBlockLabel = null;
        dragging = false;
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

        var tabMap = buildTabMap();

        for (var entry : tabMap.entrySet()) {
            var tab = entry.getKey();
            var tabItems = entry.getValue();
            if (tabItems.isEmpty()) continue;

            var tabBtn = new Tab();
            tabBtn.layout(l -> l.height(20).paddingHorizontal(6));

            if (tab != null) {
                var iconSupplier = getTabIcon(tab);
                if (iconSupplier != null) {
                    var iconStack = iconSupplier.get();
                    if (iconStack != null && !iconStack.isEmpty()) {
                        var iconEl = new UIElement();
                        iconEl.layout(l -> l.width(16).height(16));
                        iconEl.style(s -> s.backgroundTexture(new ItemStackTexture(iconStack)));
                        tabBtn.addChild(iconEl);
                    }
                }
                var tabName = getTabDisplayName(tab);
                var tabLabel = new Label();
                tabLabel.setText(tabName);
                tabLabel.textStyle(ts -> ts.textShadow(false).fontSize(9));
                tabBtn.addChild(tabLabel);
            } else {
                var tabLabel = new Label();
                tabLabel.setText(Component.literal("All"));
                tabLabel.textStyle(ts -> ts.textShadow(false).fontSize(9));
                tabBtn.addChild(tabLabel);
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
                searchResultsContainer.getChildren().forEach(UIElement::removeSelf);
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
            searchResultsContainer.getChildren().forEach(UIElement::removeSelf);
            var searchContent = buildScrolledGrid(filtered);
            searchResultsContainer.addChild(searchContent);
            searchResultsContainer.setDisplay(true);
        });

        dialog.addContent(tabView);

        dialog.addButton(new Button()
                .setText(Component.literal("Close"))
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
                icon.style(s -> s.tooltips(bi.getBlock().getName()));
            });
            slotEl.addChild(icon);
            grid.addChild(slotEl);
        }

        scroller.addScrollViewChild(grid);
        container.addChild(scroller);

        return container;
    }
}
