package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class BlockPaletteUI {

    private static UIElement palettePanel;
    private static boolean paletteOpen = false;
    private static Label currentBlockLabel;
    private static float dragOffsetX, dragOffsetY;
    private static boolean dragging = false;

    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 9;

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
                .left(8).top(30).width(220).heightPercent(70)
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
        grid.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
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

        var hotbarRow = new UIElement();
        hotbarRow.layout(l -> l.widthPercent(100).height(2));
        grid.addChild(hotbarRow);

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
        dialog.overlay.layout(l -> l.width(350).heightPercent(70));

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).heightPercent(100));

        var container = new UIElement();
        container.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP).gapAll(1));

        List<Block> blocks = new ArrayList<>();
        BuiltInRegistries.BLOCK.forEach(blocks::add);

        blocks.stream()
                .filter(b -> b.asItem() != net.minecraft.world.item.Items.AIR)
                .forEach(block -> {
                    var slotEl = new UIElement();
                    slotEl.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE));
                    slotEl.style(s -> s.background(Sprites.RECT_DARK));

                    var icon = new UIElement();
                    icon.layout(l -> l.width(SLOT_SIZE).height(SLOT_SIZE));
                    icon.style(s -> s.backgroundTexture(new ItemStackTexture(block.asItem())));
                    icon.addEventListener(UIEvents.CLICK, e -> {
                        EditorUI.getState().setActiveBlockState(block.defaultBlockState());
                        updateCurrentBlockLabel();
                        EditorUI.updateActiveBlockIndicator();
                    });
                    icon.addEventListener(UIEvents.MOUSE_ENTER, e -> {
                        icon.style(s -> s.tooltips(block.getName()));
                    });
                    slotEl.addChild(icon);
                    container.addChild(slotEl);
                });

        scroller.addScrollViewChild(container);
        dialog.addContent(scroller);

        dialog.addButton(new Button()
                .setText(Component.literal("Close"))
                .setOnClick(e -> dialog.close()));

        dialog.show(parent);
    }
}
