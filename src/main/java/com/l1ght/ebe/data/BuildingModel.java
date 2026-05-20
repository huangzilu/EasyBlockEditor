package com.l1ght.ebe.data;

import java.util.*;

public class BuildingModel {
    private final BuildingMetadata metadata;
    private final List<Region> regions = new ArrayList<>();
    private final Map<String, Layer> layers = new LinkedHashMap<>();
    private int nextRegionId = 1;

    public BuildingModel() {
        this.metadata = new BuildingMetadata();
        addLayer("default", true, false);
    }

    public BuildingMetadata getMetadata() { return metadata; }

    public Region addRegion(int sizeX, int sizeY, int sizeZ) {
        String name = "region_" + nextRegionId++;
        var region = new Region(name, 0, 0, 0, sizeX, sizeY, sizeZ);
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
        regions.add(region);
        return region;
    }

    public List<Region> getRegions() { return Collections.unmodifiableList(regions); }

    public Layer addLayer(String name, boolean visible, boolean locked) {
        var layer = new Layer(name, visible, locked);
        layers.put(name, layer);
        return layer;
    }

    public Map<String, Layer> getLayers() { return Collections.unmodifiableMap(layers); }

    public void clearLayers() { layers.clear(); }

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
        private String name;
        private boolean visible;
        private boolean locked;
        private float opacity = 1.0f;

        public Layer(String name, boolean visible, boolean locked) {
            this.name = name;
            this.visible = visible;
            this.locked = locked;
        }

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
