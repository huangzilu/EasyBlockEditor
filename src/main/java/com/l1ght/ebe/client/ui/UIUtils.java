package com.l1ght.ebe.client.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.resources.ResourceLocation;
import org.w3c.dom.Document;

public final class UIUtils {

    private UIUtils() {}

    public static UIElement findById(UIElement root, String id) {
        if (id == null || root == null) return null;
        if (id.equals(root.getId())) return root;
        for (var child : root.getChildren()) {
            var found = findById(child, id);
            if (found != null) return found;
        }
        return null;
    }

    public static Document loadXmlDoc(String path) {
        return XmlUtils.loadXml(ResourceLocation.fromNamespaceAndPath("ebe", path));
    }

    public static UI loadXmlUI(String path) {
        var doc = loadXmlDoc(path);
        if (doc == null) return null;
        return UI.of(doc);
    }
}
