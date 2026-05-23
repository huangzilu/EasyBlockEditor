package com.l1ght.ebe.data;

import com.l1ght.ebe.util.PosKey;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public class BuildingModel {
    private final BuildingMetadata metadata;
    private final List<Region> regions = new ArrayList<>();
    private final List<Layer> layers = new ArrayList<>();
    private final Map<Long, String> blockLayerOverrides = new LinkedHashMap<>();
    private final List<CompoundTag> entities = new ArrayList<>();
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

    public Map<Long, String> getBlockLayerOverrides() { return Collections.unmodifiableMap(blockLayerOverrides); }

    public void setBlockLayerOverride(int wx, int wy, int wz, String layerId) {
        long key = PosKey.pack(wx, wy, wz);
        if (layerId == null || getLayer(layerId) == null) {
            blockLayerOverrides.remove(key);
            return;
        }
        var region = findRegionAt(wx, wy, wz);
        if (region == null || layerId.equals(region.getLayerId())) {
            blockLayerOverrides.remove(key);
        } else {
            blockLayerOverrides.put(key, layerId);
        }
    }

    public List<CompoundTag> getEntities() { return Collections.unmodifiableList(entities); }

    public void addEntity(CompoundTag entity) {
        if (entity != null) entities.add(entity.copy());
    }

    public void clearEntities() {
        entities.clear();
    }

    public List<Region> getRegionsForLayer(String layerId) {
        var result = new ArrayList<Region>();
        for (var r : regions) {
            if (layerId.equals(r.getLayerId())) result.add(r);
        }
        return result;
    }

    public int countBlocksInLayer(String layerId) {
        int count = 0;
        for (var region : regions) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        Object state = region.getBlocks().get(x, y, z);
                        if (isAirLike(state)) continue;
                        int wx = x + region.getOffsetX();
                        int wy = y + region.getOffsetY();
                        int wz = z + region.getOffsetZ();
                        if (layerId.equals(getLayerIdAt(region, wx, wy, wz))) count++;
                    }
                }
            }
        }
        return count;
    }

    public Layer addLayer(String name, boolean visible, boolean locked) {
        return addLayer("layer_" + nextLayerId++, name, visible, locked);
    }

    public Layer addLayer(String id, String name, boolean visible, boolean locked) {
        if (id == null || id.isBlank()) {
            id = "layer_" + nextLayerId++;
        } else {
            bumpNextLayerId(id);
        }
        var layer = new Layer(id, name, visible, locked);
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
        String fallback = layers.isEmpty() ? null : layers.get(0).getId();
        for (var r : regions) {
            if (layerId.equals(r.getLayerId())) {
                r.setLayerId(fallback);
            }
        }
        if (fallback == null) {
            blockLayerOverrides.clear();
        } else {
            blockLayerOverrides.replaceAll((pos, current) -> layerId.equals(current) ? fallback : current);
            normalizeLayerOverrides();
        }
    }

    public void moveRegionToLayer(Region region, String newLayerId) {
        region.setLayerId(newLayerId);
        normalizeLayerOverrides();
    }

    public Layer mergeLayers(String layerId1, String layerId2, String newName) {
        var merged = addLayer(newName, true, false);
        for (var r : regions) {
            if (layerId1.equals(r.getLayerId()) || layerId2.equals(r.getLayerId())) {
                r.setLayerId(merged.getId());
            }
        }
        blockLayerOverrides.replaceAll((pos, current) ->
                layerId1.equals(current) || layerId2.equals(current) ? merged.getId() : current);
        removeLayer(layerId1);
        removeLayer(layerId2);
        return merged;
    }

    public void mergeLayerInto(String sourceLayerId, String targetLayerId) {
        if (sourceLayerId == null || targetLayerId == null || sourceLayerId.equals(targetLayerId)) return;
        if (getLayer(sourceLayerId) == null || getLayer(targetLayerId) == null) return;
        for (var r : regions) {
            if (sourceLayerId.equals(r.getLayerId())) {
                r.setLayerId(targetLayerId);
            }
        }
        blockLayerOverrides.replaceAll((pos, current) -> sourceLayerId.equals(current) ? targetLayerId : current);
        layers.removeIf(l -> l.getId().equals(sourceLayerId));
        normalizeLayerOverrides();
    }

    public boolean isLayerVisible(String layerId) {
        var l = getLayer(layerId);
        return l != null && l.isVisible();
    }

    public boolean isLayerVisibleAt(Region region, int wx, int wy, int wz) {
        String layerId = getLayerIdAt(region, wx, wy, wz);
        return layerId == null || isLayerVisible(layerId);
    }

    public boolean isLayerLocked(String layerId) {
        var l = getLayer(layerId);
        return l != null && l.isLocked();
    }

    public boolean canEditAt(int wx, int wy, int wz) {
        for (var region : regions) {
            if (region.containsWorldPos(wx, wy, wz)) {
                var layer = getLayer(getLayerIdAt(region, wx, wy, wz));
                if (layer != null && (!layer.isVisible() || layer.isLocked())) {
                    return false;
                }
            }
        }
        return true;
    }

    public String getLayerIdAt(Region region, int wx, int wy, int wz) {
        if (region == null) return null;
        return blockLayerOverrides.getOrDefault(PosKey.pack(wx, wy, wz), region.getLayerId());
    }

    public String getLayerIdAt(int wx, int wy, int wz) {
        var region = findRegionAt(wx, wy, wz);
        return region == null ? null : getLayerIdAt(region, wx, wy, wz);
    }

    public boolean assignBlockToLayer(int wx, int wy, int wz, String layerId) {
        if (getLayer(layerId) == null) return false;
        var region = findRegionAt(wx, wy, wz);
        if (region == null) return false;
        Object state = region.getWorldBlock(wx, wy, wz);
        if (isAirLike(state)) return false;
        setBlockLayerOverride(wx, wy, wz, layerId);
        return true;
    }

    private void bumpNextLayerId(String id) {
        if (!id.startsWith("layer_")) return;
        try {
            int numeric = Integer.parseInt(id.substring("layer_".length()));
            nextLayerId = Math.max(nextLayerId, numeric + 1);
        } catch (NumberFormatException ignored) {
        }
    }

    public Object getBlockAt(int wx, int wy, int wz) {
        Object air = "minecraft:air";
        for (var region : regions) {
            if (region.containsWorldPos(wx, wy, wz)) {
                Object state = region.getWorldBlock(wx, wy, wz);
                if (!isAirLike(state)) return state;
                air = state;
            }
        }
        return air;
    }

    public void setBlockAt(int wx, int wy, int wz, Object state) {
        var region = findRegionAt(wx, wy, wz);
        if (region != null) {
            region.setWorldBlock(wx, wy, wz, state);
            if (isAirLike(state)) blockLayerOverrides.remove(PosKey.pack(wx, wy, wz));
            return;
        }
    }

    public CompoundTag getBlockEntityNbt(int wx, int wy, int wz) {
        for (var region : regions) {
            if (region.containsWorldPos(wx, wy, wz)) {
                return region.getWorldBlockEntity(wx, wy, wz);
            }
        }
        return null;
    }

    public void setBlockEntityNbt(int wx, int wy, int wz, CompoundTag tag) {
        for (var region : regions) {
            if (region.containsWorldPos(wx, wy, wz)) {
                region.setWorldBlockEntity(wx, wy, wz, tag);
                return;
            }
        }
    }

    public LayerState captureLayerState() {
        var layerCopies = new ArrayList<LayerData>();
        for (var layer : layers) {
            layerCopies.add(new LayerData(layer.id, layer.name, layer.visible, layer.locked, layer.opacity));
        }
        var regionLayerIds = new ArrayList<String>();
        for (var region : regions) {
            regionLayerIds.add(region.getLayerId());
        }
        return new LayerState(layerCopies, regionLayerIds, new LinkedHashMap<>(blockLayerOverrides), nextLayerId);
    }

    public void restoreLayerState(LayerState state) {
        if (state == null) return;
        layers.clear();
        for (var data : state.layers()) {
            var layer = new Layer(data.id(), data.name(), data.visible(), data.locked());
            layer.setOpacity(data.opacity());
            layers.add(layer);
        }
        nextLayerId = Math.max(1, state.nextLayerId());
        for (int i = 0; i < regions.size() && i < state.regionLayerIds().size(); i++) {
            regions.get(i).setLayerId(state.regionLayerIds().get(i));
        }
        blockLayerOverrides.clear();
        blockLayerOverrides.putAll(state.blockLayerOverrides());
        normalizeLayerOverrides();
    }

    public BuildingModel deepCopy() {
        var copy = new BuildingModel();
        copy.layers.clear();
        copy.regions.clear();
        copy.blockLayerOverrides.clear();
        copy.entities.clear();
        copy.nextLayerId = this.nextLayerId;
        copy.nextRegionId = this.nextRegionId;

        copy.metadata.setName(metadata.getName());
        copy.metadata.setAuthor(metadata.getAuthor());
        copy.metadata.setDescription(metadata.getDescription());
        copy.metadata.setSize(metadata.getSizeX(), metadata.getSizeY(), metadata.getSizeZ());
        copy.metadata.setCreated(metadata.getCreated());
        copy.metadata.setModified(metadata.getModified());

        for (var layer : layers) {
            var layerCopy = new Layer(layer.getId(), layer.getName(), layer.isVisible(), layer.isLocked());
            layerCopy.setOpacity(layer.getOpacity());
            copy.layers.add(layerCopy);
        }

        for (var region : regions) {
            var regionCopy = new Region(region.getName(), region.getOffsetX(), region.getOffsetY(), region.getOffsetZ(),
                    region.getSizeX(), region.getSizeY(), region.getSizeZ());
            regionCopy.setLayerId(region.getLayerId());
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        regionCopy.getBlocks().set(x, y, z, region.getBlocks().get(x, y, z));
                    }
                }
            }
            for (var entry : region.getBlockEntities().entrySet()) {
                long key = entry.getKey();
                int lx = (int) (key & 0xFFF);
                int ly = (int) ((key >> 12) & 0xFFF);
                int lz = (int) ((key >> 24) & 0xFFF);
                regionCopy.setBlockEntity(lx, ly, lz, entry.getValue().copy());
            }
            copy.regions.add(regionCopy);
        }

        copy.blockLayerOverrides.putAll(blockLayerOverrides);
        for (var entity : entities) {
            copy.entities.add(entity.copy());
        }
        return copy;
    }

    public static boolean isAirLike(Object state) {
        if (state == null) return true;
        if (isMinecraftBlockState(state)) {
            try {
                return Boolean.TRUE.equals(state.getClass().getMethod("isAir").invoke(state));
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        if (state instanceof String s) return s.isEmpty() || s.equals("minecraft:air") || s.equals("air");
        return false;
    }

    private static boolean isMinecraftBlockState(Object state) {
        return state != null && "net.minecraft.world.level.block.state.BlockState".equals(state.getClass().getName());
    }

    private Region findRegionAt(int wx, int wy, int wz) {
        Region airRegion = null;
        for (var region : regions) {
            if (region.containsWorldPos(wx, wy, wz)) {
                Object state = region.getWorldBlock(wx, wy, wz);
                if (!isAirLike(state)) return region;
                if (airRegion == null) airRegion = region;
            }
        }
        return airRegion;
    }

    private void normalizeLayerOverrides() {
        blockLayerOverrides.entrySet().removeIf(entry -> {
            String layerId = entry.getValue();
            if (layerId == null || getLayer(layerId) == null) return true;
            int x = PosKey.unpackX(entry.getKey());
            int y = PosKey.unpackY(entry.getKey());
            int z = PosKey.unpackZ(entry.getKey());
            var region = findRegionAt(x, y, z);
            return region == null || isAirLike(region.getWorldBlock(x, y, z)) || layerId.equals(region.getLayerId());
        });
    }

    public record LayerData(String id, String name, boolean visible, boolean locked, float opacity) {}
    public record LayerState(List<LayerData> layers, List<String> regionLayerIds,
                             Map<Long, String> blockLayerOverrides, int nextLayerId) {}

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
