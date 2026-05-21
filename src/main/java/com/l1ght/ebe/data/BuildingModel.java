package com.l1ght.ebe.data;

import java.util.*;

public class BuildingModel {
    private final BuildingMetadata metadata;
    private final List<Region> regions = new ArrayList<>();
    private final List<Layer> layers = new ArrayList<>();
    private int nextRegionId = 1;
    private int nextLayerId = 1;

    public BuildingModel() {
        this.metadata = new BuildingMetadata();
        addLayer("default", true, false);
    }

    public BuildingMetadata getMetadata() { return metadata; }

    public Region addRegion(int sizeX, int sizeY, int sizeZ) {
        String name = "region_" + nextRegionId++;
        var region = new Region(name, 0, 0, 0, sizeX, sizeY, sizeZ);
        if (!layers.isEmpty()) {
            region.setLayerId(layers.get(0).getId());
        }
        regions.add(region);
        metadata.setSize(
            Math.max(metadata.getSizeX(), sizeX),
            Math.max(metadata.getSizeY(), sizeY),
            Math.max(metadata.getSizeZ(), sizeZ)
        );
        return region;
    }

    public Region addRegion(String name, int ox, int oy, int oz, int sx, int sy, int sz) {
        var region = new Region(name, ox, oy, oz, sx, sy, sz);
        if (!layers.isEmpty()) {
            region.setLayerId(layers.get(0).getId());
        }
        regions.add(region);
        return region;
    }

    public List<Region> getRegions() { return Collections.unmodifiableList(regions); }

    public List<Region> getRegionsForLayer(String layerId) {
        var result = new ArrayList<Region>();
        for (var r : regions) {
            if (layerId.equals(r.getLayerId())) result.add(r);
        }
        return result;
    }

    public Layer addLayer(String name, boolean visible, boolean locked) {
        var layer = new Layer("layer_" + nextLayerId++, name, visible, locked);
        layers.add(layer);
        return layer;
    }

    public Layer getLayer(String id) {
        for (var l : layers) {
            if (l.getId().equals(id)) return l;
        }
        return null;
    }

    public List<Layer> getLayers() { return Collections.unmodifiableList(layers); }

    public void removeLayer(String layerId) {
        layers.removeIf(l -> l.getId().equals(layerId));
        for (var r : regions) {
            if (layerId.equals(r.getLayerId())) {
                r.setLayerId(layers.isEmpty() ? null : layers.get(0).getId());
            }
        }
    }

    public void moveRegionToLayer(Region region, String newLayerId) {
        region.setLayerId(newLayerId);
    }

    public Layer mergeLayers(String layerId1, String layerId2, String newName) {
        var merged = addLayer(newName, true, false);
        for (var r : regions) {
            if (layerId1.equals(r.getLayerId()) || layerId2.equals(r.getLayerId())) {
                r.setLayerId(merged.getId());
            }
        }
        removeLayer(layerId1);
        removeLayer(layerId2);
        return merged;
    }

    public boolean isLayerVisible(String layerId) {
        var l = getLayer(layerId);
        return l != null && l.isVisible();
    }

    public boolean isLayerLocked(String layerId) {
        var l = getLayer(layerId);
        return l != null && l.isLocked();
    }

    public boolean canEditAt(int wx, int wy, int wz) {
        for (var region : regions) {
            if (region.containsWorldPos(wx, wy, wz)) {
                var layer = getLayer(region.getLayerId());
                if (layer != null && (!layer.isVisible() || layer.isLocked())) {
                    return false;
                }
            }
        }
        return true;
    }

    public Object getBlockAt(int wx, int wy, int wz) {
        for (var region : regions) {
            if (region.containsWorldPos(wx, wy, wz)) {
                return region.getWorldBlock(wx, wy, wz);
            }
        }
        return "minecraft:air";
    }

    public void setBlockAt(int wx, int wy, int wz, Object state) {
        for (var region : regions) {
            if (region.containsWorldPos(wx, wy, wz)) {
                region.setWorldBlock(wx, wy, wz, state);
                return;
            }
        }
    }

    public static class Layer {
        private final String id;
        private String name;
        private boolean visible;
        private boolean locked;
        private float opacity = 1.0f;

        public Layer(String id, String name, boolean visible, boolean locked) {
            this.id = id;
            this.name = name;
            this.visible = visible;
            this.locked = locked;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isVisible() { return visible; }
        public void setVisible(boolean visible) { this.visible = visible; }
        public boolean isLocked() { return locked; }
        public void setLocked(boolean locked) { this.locked = locked; }
        public float getOpacity() { return opacity; }
        public void setOpacity(float opacity) { this.opacity = opacity; }
    }
}
