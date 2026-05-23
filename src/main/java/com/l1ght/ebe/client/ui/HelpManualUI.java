package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HelpManualUI {
    private static final String MOD_ID = "ebe";
    private static final String DEFAULT_VERSION = "0.1.0";

    private static final int COVER = 0xFFE4D1A6;
    private static final int COVER_DARK = 0xFFF7EEDC;
    private static final int PAPER = 0xFFFFF7E8;
    private static final int PAPER_ALT = 0xFFF4E4BE;
    private static final int INK = 0xFF2A2118;
    private static final int INK_SOFT = 0xFF5D4A35;
    private static final int ACCENT = 0xFF1F7A8C;
    private static final int ACCENT_DARK = 0xFF155260;
    private static final int SELECTION = 0xFF2F6FDB;

    private static final List<ManualCategory> CATEGORIES = List.of(
            new ManualCategory("about", "ebe.help.manual.category.about"),
            new ManualCategory("start", "ebe.help.manual.category.start"),
            new ManualCategory("ui", "ebe.help.manual.category.ui"),
            new ManualCategory("files", "ebe.help.manual.category.files"),
            new ManualCategory("edit", "ebe.help.manual.category.edit"),
            new ManualCategory("projection", "ebe.help.manual.category.projection"),
            new ManualCategory("collab", "ebe.help.manual.category.collab"),
            new ManualCategory("settings", "ebe.help.manual.category.settings"),
            new ManualCategory("trouble", "ebe.help.manual.category.trouble")
    );

    private static final List<ManualPage> PAGES = List.of(
            page("about", "about"),
            page("start", "quick_start"),
            page("ui", "ui_overview"),
            page("ui", "layout_operations"),
            page("files", "file_library"),
            page("files", "open_import"),
            page("files", "save_export"),
            page("edit", "editing_tools"),
            page("edit", "selection_display"),
            page("edit", "materials"),
            page("projection", "projection"),
            page("projection", "printer"),
            page("collab", "workgroups"),
            page("collab", "admin"),
            page("settings", "client_settings"),
            page("settings", "render_performance"),
            page("settings", "shortcuts"),
            page("trouble", "nbt_advanced"),
            page("trouble", "troubleshooting")
    );

    private HelpManualUI() {
    }

    public static void show(UIElement parent) {
        show(parent, "about");
    }

    public static void showAbout(UIElement parent) {
        show(parent, "about");
    }

    public static void show(UIElement parent, String initialPageId) {
        if (parent == null) return;
        var runtime = new ManualRuntime();
        runtime.activePage = findPage(initialPageId);
        runtime.activeCategory = runtime.activePage.categoryId();

        var dialog = new Dialog();
        dialog.setTitle(Component.translatable("ebe.help.manual.title").getString());
        dialog.setAutoClose(false);
        dialog.setClickOutsideClose(false);
        dialog.darkenBackground();
        dialog.overlay.layout(l -> l.width(830).maxHeightPercent(92));
        swallowMouse(dialog.overlay);
        dialog.contentContainer.layout(l -> l.widthPercent(100).height(438).paddingAll(0));
        dialog.contentContainer.style(s -> s.backgroundTexture(new ColorRectTexture(COVER_DARK)));
        swallowMouse(dialog.contentContainer);

        dialog.addContent(buildBook(runtime));
        dialog.addButton(new Button()
                .setText(Component.translatable("ebe.editor.close"))
                .setOnClick(e -> dialog.close()));

        renderAll(runtime);
        dialog.show(parent);
    }

    private static void swallowMouse(UIElement element) {
        element.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            e.stopPropagation();
            e.stopImmediatePropagation();
        });
        element.addEventListener(UIEvents.MOUSE_UP, e -> {
            e.stopPropagation();
            e.stopImmediatePropagation();
        });
        element.addEventListener(UIEvents.CLICK, e -> {
            e.stopPropagation();
            e.stopImmediatePropagation();
        });
        element.addEventListener(UIEvents.DOUBLE_CLICK, e -> {
            e.stopPropagation();
            e.stopImmediatePropagation();
        });
        element.addEventListener(UIEvents.MOUSE_MOVE, e -> {
            e.stopPropagation();
            e.stopImmediatePropagation();
        });
        element.addEventListener(UIEvents.MOUSE_WHEEL, e -> {
            e.stopPropagation();
            e.stopImmediatePropagation();
        });
    }

    public static String version() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(DEFAULT_VERSION);
    }

    private static UIElement buildBook(ManualRuntime runtime) {
        var frame = new UIElement();
        frame.layout(l -> l.widthPercent(100).heightPercent(100).paddingAll(8).flexDirection(FlexDirection.COLUMN));
        frame.style(s -> s.backgroundTexture(new ColorRectTexture(COVER)));

        var book = new UIElement();
        book.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.ROW));
        book.style(s -> s.backgroundTexture(new ColorRectTexture(PAPER_ALT)));

        var leftPage = pagePanel(true);
        leftPage.addChild(buildLeftPage(runtime));
        book.addChild(leftPage);

        var spine = new UIElement();
        spine.layout(l -> l.width(16).heightPercent(100).flexDirection(FlexDirection.COLUMN).alignItems(AlignItems.CENTER));
        spine.style(s -> s.backgroundTexture(new ColorRectTexture(0xFFB88D4D)));
        var spineLine = new UIElement();
        spineLine.layout(l -> l.width(3).heightPercent(100));
        spineLine.style(s -> s.backgroundTexture(new ColorRectTexture(0xFF7A5C2E)));
        spine.addChild(spineLine);
        book.addChild(spine);

        var rightPage = pagePanel(false);
        rightPage.addChild(buildRightPage(runtime));
        book.addChild(rightPage);

        frame.addChild(book);
        return frame;
    }

    private static UIElement pagePanel(boolean left) {
        var page = new UIElement();
        page.layout(l -> {
            if (left) {
                l.width(274).heightPercent(100).paddingAll(8).flexDirection(FlexDirection.COLUMN).gapAll(5);
            } else {
                l.flex(1).heightPercent(100).paddingAll(8).flexDirection(FlexDirection.COLUMN).gapAll(5);
            }
        });
        page.style(s -> s.backgroundTexture(new ColorRectTexture(PAPER)));
        return page;
    }

    private static UIElement buildLeftPage(ManualRuntime runtime) {
        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(5));

        var brand = new UIElement();
        brand.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(5));
        var icon = new UIElement();
        icon.layout(l -> l.width(18).height(18));
        icon.style(s -> s.backgroundTexture(EditorIcons.INFO));
        brand.addChild(icon);
        var title = label(Component.translatable("ebe.help.manual.book_title", version()).getString(), INK, 10.5f, true);
        title.layout(l -> l.flex(1));
        brand.addChild(title);
        root.addChild(brand);

        var searchRow = new UIElement();
        searchRow.layout(l -> l.widthPercent(100).height(22).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        var searchIcon = new UIElement();
        searchIcon.layout(l -> l.width(16).height(16));
        searchIcon.style(s -> s.backgroundTexture(EditorIcons.SEARCH));
        searchRow.addChild(searchIcon);
        runtime.searchField = new TextField();
        runtime.searchField.layout(l -> l.flex(1).height(20));
        runtime.searchField.textFieldStyle(s -> s.placeholder(Component.translatable("ebe.help.manual.search")));
        runtime.searchField.setTextResponder(text -> {
            runtime.searchQuery = text == null ? "" : text.trim();
            renderSearchResults(runtime);
            renderPage(runtime);
        });
        searchRow.addChild(runtime.searchField);
        root.addChild(searchRow);

        runtime.searchResults = new UIElement();
        runtime.searchResults.layout(l -> l.widthPercent(100).maxHeight(112).flexDirection(FlexDirection.COLUMN));
        root.addChild(runtime.searchResults);

        var contentsLabel = label(Component.translatable("ebe.help.manual.contents").getString(), INK, 10.5f, false);
        contentsLabel.style(s -> s.backgroundTexture(new ColorRectTexture(0xFFFFF2D2)));
        contentsLabel.layout(l -> l.widthPercent(100).height(16).paddingHorizontal(4));
        root.addChild(contentsLabel);

        var tocScroller = new ScrollerView();
        tocScroller.layout(l -> l.widthPercent(100).flex(1));
        tocScroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        runtime.toc = new UIElement();
        runtime.toc.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        tocScroller.addScrollViewChild(runtime.toc);
        root.addChild(tocScroller);

        return root;
    }

    private static UIElement buildRightPage(ManualRuntime runtime) {
        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(5));

        runtime.tabs = new UIElement();
        runtime.tabs.layout(l -> l.widthPercent(100).height(25).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(2));
        root.addChild(runtime.tabs);

        runtime.pageContent = new UIElement();
        runtime.pageContent.layout(l -> l.widthPercent(100).flex(1).flexDirection(FlexDirection.COLUMN));
        root.addChild(runtime.pageContent);

        return root;
    }

    private static void renderAll(ManualRuntime runtime) {
        renderTabs(runtime);
        renderSearchResults(runtime);
        renderToc(runtime);
        renderPage(runtime);
    }

    private static void renderTabs(ManualRuntime runtime) {
        runtime.tabs.clearAllChildren();
        for (var category : CATEGORIES) {
            boolean active = category.id().equals(runtime.activeCategory);
            var btn = new Button();
            btn.setText(Component.translatable(category.titleKey()));
            btn.layout(l -> l.flex(1).height(22).paddingHorizontal(2));
            btn.textStyle(s -> s.textColor(active ? 0xFFFFFFFF : INK).fontSize(7.8f).textShadow(false));
            btn.buttonStyle(s -> s
                    .baseTexture(new ColorRectTexture(active ? ACCENT : 0xFFE7D7B5))
                    .hoverTexture(new ColorRectTexture(active ? ACCENT : 0xFFD8C493))
                    .pressedTexture(new ColorRectTexture(ACCENT_DARK)));
            btn.setOnClick(e -> selectCategory(runtime, category.id()));
            runtime.tabs.addChild(btn);
        }
    }

    private static void renderSearchResults(ManualRuntime runtime) {
        runtime.searchResults.clearAllChildren();
        if (runtime.searchQuery.isBlank()) {
            runtime.searchResults.setDisplay(false);
            return;
        }
        runtime.searchResults.setDisplay(true);
        var matches = search(runtime.searchQuery);
        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).maxHeight(112));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        var list = new UIElement();
        list.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(2));
        if (matches.isEmpty()) {
            list.addChild(label(Component.translatable("ebe.help.manual.no_results").getString(), INK_SOFT, 8.5f, true));
        } else {
            for (var page : matches) {
                var btn = new Button();
                btn.setText(Component.translatable("ebe.help.manual.result",
                        pageTitle(page), categoryTitle(page.categoryId())));
                btn.layout(l -> l.widthPercent(100).height(20).paddingHorizontal(4));
                btn.textStyle(s -> s.textColor(INK).fontSize(8.0f).textShadow(false));
                btn.buttonStyle(s -> s
                        .baseTexture(new ColorRectTexture(0xFFECDDBD))
                        .hoverTexture(new ColorRectTexture(0xFFD8C493))
                        .pressedTexture(new ColorRectTexture(0xFFC1A671)));
                btn.setOnClick(e -> selectPage(runtime, page));
                list.addChild(btn);
            }
        }
        scroller.addScrollViewChild(list);
        runtime.searchResults.addChild(scroller);
    }

    private static void renderToc(ManualRuntime runtime) {
        runtime.toc.clearAllChildren();
        for (var category : CATEGORIES) {
            var cat = label(Component.translatable(category.titleKey()).getString(), ACCENT_DARK, 8.5f, false);
            cat.layout(l -> l.widthPercent(100).height(14).marginTop(3));
            runtime.toc.addChild(cat);
            for (var page : pagesIn(category.id())) {
                boolean active = page.id().equals(runtime.activePage.id());
                var btn = new Button();
                btn.setText(Component.literal((active ? "> " : "  ") + pageTitle(page)));
                btn.layout(l -> l.widthPercent(100).height(19).paddingHorizontal(4));
                btn.textStyle(s -> s.textColor(active ? 0xFFFFFFFF : INK).fontSize(8.2f).textShadow(false));
                btn.buttonStyle(s -> s
                    .baseTexture(new ColorRectTexture(active ? ACCENT : 0xFFFFF7E8))
                    .hoverTexture(new ColorRectTexture(active ? ACCENT : 0xFFFFEDC0))
                        .pressedTexture(new ColorRectTexture(ACCENT_DARK)));
                btn.setOnClick(e -> selectPage(runtime, page));
                runtime.toc.addChild(btn);
            }
        }
    }

    private static void renderPage(ManualRuntime runtime) {
        runtime.pageContent.clearAllChildren();

        var page = runtime.activePage;
        var header = new UIElement();
        header.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3).paddingAll(5));
        header.style(s -> s.backgroundTexture(new ColorRectTexture(0xFFEAD9AF)));

        var titleRow = new UIElement();
        titleRow.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(5));
        var bookmark = new UIElement();
        bookmark.layout(l -> l.width(6).height(22));
        bookmark.style(s -> s.backgroundTexture(new ColorRectTexture(ACCENT)));
        titleRow.addChild(bookmark);
        var title = highlightedLine(pageTitle(page), runtime.searchQuery, INK, 14.0f, true);
        title.layout(l -> l.flex(1));
        titleRow.addChild(title);
        var pageNo = label(Component.translatable("ebe.help.manual.page_number",
                PAGES.indexOf(page) + 1, PAGES.size()).getString(), INK_SOFT, 8.0f, false);
        pageNo.layout(l -> l.width(54).height(14));
        titleRow.addChild(pageNo);
        header.addChild(titleRow);
        header.addChild(highlightedLine(pageSummary(page), runtime.searchQuery, INK_SOFT, 8.8f, true));
        runtime.pageContent.addChild(header);

        var scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1).marginTop(4));
        scroller.scrollerStyle(s -> s.verticalScrollDisplay(ScrollDisplay.ALWAYS));
        var content = new UIElement();
        content.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4).paddingAll(2));

        if ("ui_overview".equals(page.id())) {
            content.addChild(buildUiMap(runtime.searchQuery));
        }

        for (var raw : pageBody(page).split("\\|")) {
            var line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("## ")) {
                var heading = highlightedLine(line.substring(3), runtime.searchQuery, ACCENT_DARK, 10.5f, true);
                heading.layout(l -> l.widthPercent(100).marginTop(4));
                content.addChild(heading);
            } else if (line.startsWith("! ")) {
                var callout = new UIElement();
                callout.layout(l -> l.widthPercent(100).paddingAll(5).flexDirection(FlexDirection.COLUMN));
                callout.style(s -> s.backgroundTexture(new ColorRectTexture(0xFFDDEFF2)));
                callout.addChild(highlightedLine(line.substring(2), runtime.searchQuery, 0xFF174C57, 8.7f, true));
                content.addChild(callout);
            } else {
                content.addChild(highlightedLine(line, runtime.searchQuery, INK, 8.7f, true));
            }
        }

        scroller.addScrollViewChild(content);
        runtime.pageContent.addChild(scroller);
    }

    private static UIElement buildUiMap(String query) {
        var card = new UIElement();
        card.layout(l -> l.widthPercent(100).height(94).flexDirection(FlexDirection.COLUMN).gapAll(4).paddingAll(5));
        card.style(s -> s.backgroundTexture(new ColorRectTexture(0xFFE0F0EA)));
        card.addChild(highlightedLine(Component.translatable("ebe.help.manual.ui_map.title").getString(), query, 0xFF225244, 9.0f, true));

        var map = new UIElement();
        map.layout(l -> l.widthPercent(100).flex(1).flexDirection(FlexDirection.ROW).gapAll(4));
        map.addChild(mapBlock("ebe.help.manual.ui_map.left", 82, 0xFFB7D7CA));
        var center = new UIElement();
        center.layout(l -> l.flex(1).heightPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(3));
        center.addChild(mapBlock("ebe.help.manual.ui_map.menu", 0, 0xFFC9C2E5));
        center.addChild(mapBlock("ebe.help.manual.ui_map.viewport", 0, 0xFFB9D5EA));
        center.addChild(mapBlock("ebe.help.manual.ui_map.toolbar", 0, 0xFFE7D7B5));
        map.addChild(center);
        map.addChild(mapBlock("ebe.help.manual.ui_map.right", 92, 0xFFE0C1B0));
        card.addChild(map);
        return card;
    }

    private static UIElement mapBlock(String key, int width, int color) {
        var block = new UIElement();
        block.layout(l -> {
            if (width > 0) l.width(width);
            else l.flex(1);
            l.heightPercent(100).alignItems(AlignItems.CENTER).justifyContent(AlignContent.CENTER).paddingAll(2);
        });
        block.style(s -> s.backgroundTexture(new ColorRectTexture(color)));
        var label = label(Component.translatable(key).getString(), INK, 7.5f, true);
        label.textStyle(s -> s.textColor(INK).fontSize(7.5f).textShadow(false));
        block.addChild(label);
        return block;
    }

    private static UIElement highlightedLine(String text, String query, int color, float fontSize, boolean wrap) {
        if (query == null || query.isBlank() || !normalize(text).contains(normalize(query))) {
            return label(text, color, fontSize, wrap);
        }

        var row = new UIElement();
        row.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.ROW).flexWrap(FlexWrap.WRAP).gapAll(0));

        String lower = text.toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        int cursor = 0;
        while (cursor < text.length()) {
            int hit = lower.indexOf(needle, cursor);
            if (hit < 0) {
                addSegment(row, text.substring(cursor), color, fontSize, false);
                break;
            }
            if (hit > cursor) {
                addSegment(row, text.substring(cursor, hit), color, fontSize, false);
            }
            addSegment(row, text.substring(hit, hit + needle.length()), 0xFFFFFFFF, fontSize, true);
            cursor = hit + needle.length();
        }
        return row;
    }

    private static void addSegment(UIElement row, String text, int color, float fontSize, boolean selected) {
        if (text.isEmpty()) return;
        var label = label(text, color, fontSize, false);
        label.layout(l -> l.height((int) Math.ceil(fontSize + 4)).paddingHorizontal(selected ? 2 : 0));
        if (selected) {
            label.style(s -> s.backgroundTexture(new ColorRectTexture(SELECTION)));
        }
        row.addChild(label);
    }

    private static Label label(String text, int color, float fontSize, boolean wrap) {
        var label = new Label();
        label.setText(Component.literal(text));
        label.layout(l -> l.widthPercent(100));
        label.textStyle(s -> {
            s.textColor(color).fontSize(fontSize).textShadow(false).lineSpacing(2.0f);
            if (wrap) {
                s.textWrap(TextWrap.WRAP).adaptiveHeight(true);
            } else {
                s.adaptiveWidth(true);
            }
        });
        return label;
    }

    private static void selectCategory(ManualRuntime runtime, String categoryId) {
        runtime.activeCategory = categoryId;
        if (!runtime.activePage.categoryId().equals(categoryId)) {
            runtime.activePage = pagesIn(categoryId).stream().findFirst().orElse(runtime.activePage);
        }
        renderAll(runtime);
    }

    private static void selectPage(ManualRuntime runtime, ManualPage page) {
        runtime.activePage = page;
        runtime.activeCategory = page.categoryId();
        renderAll(runtime);
    }

    private static List<ManualPage> pagesIn(String categoryId) {
        var result = new ArrayList<ManualPage>();
        for (var page : PAGES) {
            if (page.categoryId().equals(categoryId)) {
                result.add(page);
            }
        }
        return result;
    }

    private static List<ManualPage> search(String query) {
        var result = new ArrayList<ManualPage>();
        var needle = normalize(query);
        if (needle.isBlank()) return result;
        for (var page : PAGES) {
            var haystack = normalize(pageTitle(page) + " " + categoryTitle(page.categoryId()) + " "
                    + pageSummary(page) + " " + pageBody(page));
            if (haystack.contains(needle)) {
                result.add(page);
            }
        }
        return result;
    }

    private static ManualPage findPage(String id) {
        for (var page : PAGES) {
            if (page.id().equals(id)) return page;
        }
        return PAGES.getFirst();
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static ManualPage page(String categoryId, String id) {
        return new ManualPage(categoryId, id,
                "ebe.help.manual.page." + id + ".title",
                "ebe.help.manual.page." + id + ".summary",
                "ebe.help.manual.page." + id + ".body");
    }

    private static String categoryTitle(String categoryId) {
        return CATEGORIES.stream()
                .filter(category -> category.id().equals(categoryId))
                .findFirst()
                .map(category -> Component.translatable(category.titleKey()).getString())
                .orElse(categoryId);
    }

    private static String pageTitle(ManualPage page) {
        return Component.translatable(page.titleKey()).getString();
    }

    private static String pageSummary(ManualPage page) {
        return Component.translatable(page.summaryKey()).getString();
    }

    private static String pageBody(ManualPage page) {
        if ("about".equals(page.id())) {
            return Component.translatable(page.bodyKey(), version()).getString();
        }
        return Component.translatable(page.bodyKey()).getString();
    }

    private record ManualCategory(String id, String titleKey) {
    }

    private record ManualPage(String categoryId, String id, String titleKey, String summaryKey, String bodyKey) {
    }

    private static final class ManualRuntime {
        UIElement tabs;
        UIElement toc;
        UIElement searchResults;
        UIElement pageContent;
        TextField searchField;
        ManualPage activePage;
        String activeCategory;
        String searchQuery = "";
    }
}
