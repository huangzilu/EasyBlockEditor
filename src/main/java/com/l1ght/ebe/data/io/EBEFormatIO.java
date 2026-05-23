package com.l1ght.ebe.data.io;

import com.google.gson.*;
import com.l1ght.ebe.data.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EBEFormatIO {
    private static final Logger LOG = LoggerFactory.getLogger("EBE/Format");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new GsonBuilder().create();

    public static void write(BuildingModel model, Path file) throws IOException {
        write(model, file, false);
    }

    public static void write(BuildingModel model, Path file, boolean compact) throws IOException {
        var root = new JsonObject();
        root.addProperty("format", "ebe");
        root.addProperty("version", 1);

        var meta = model.getMetadata();
        var metaJson = new JsonObject();
        metaJson.addProperty("name", meta.getName());
        metaJson.addProperty("author", meta.getAuthor());
        metaJson.addProperty("description", meta.getDescription());
        metaJson.addProperty("created", meta.getCreated());
        metaJson.addProperty("modified", meta.getModified());
        var sizeJson = new JsonObject();
        sizeJson.addProperty("x", meta.getSizeX());
        sizeJson.addProperty("y", meta.getSizeY());
        sizeJson.addProperty("z", meta.getSizeZ());
        metaJson.add("size", sizeJson);
        root.add("metadata", metaJson);

        var layersArray = new JsonArray();
        for (var l : model.getLayers()) {
            var lj = new JsonObject();
            lj.addProperty("id", l.getId());
            lj.addProperty("name", l.getName());
            lj.addProperty("visible", l.isVisible());
            lj.addProperty("locked", l.isLocked());
            lj.addProperty("opacity", l.getOpacity());
            layersArray.add(lj);
        }
        root.add("layers", layersArray);

        var regionsArray = new JsonArray();
        for (var region : model.getRegions()) {
            var rj = new JsonObject();
            rj.addProperty("name", region.getName());
            var posJson = new JsonObject();
            posJson.addProperty("x", region.getOffsetX());
            posJson.addProperty("y", region.getOffsetY());
            posJson.addProperty("z", region.getOffsetZ());
            rj.add("position", posJson);
            var szJson = new JsonObject();
            szJson.addProperty("x", region.getSizeX());
            szJson.addProperty("y", region.getSizeY());
            szJson.addProperty("z", region.getSizeZ());
            rj.add("size", szJson);

            var palette = region.getBlocks().getPalette();
            var paletteArray = new JsonArray();
            for (Object state : palette.allStates()) {
                if (state instanceof BlockState bs) {
                    var entry = new JsonObject();
                    entry.addProperty("id", BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString());
                    if (!bs.getProperties().isEmpty()) {
                        var props = new JsonObject();
                        for (Property<?> prop : bs.getProperties()) {
                            props.addProperty(prop.getName(), bs.getValue(prop).toString());
                        }
                        entry.add("properties", props);
                    }
                    paletteArray.add(entry);
                } else {
                    paletteArray.add(state.toString());
                }
            }
            rj.add("palette", paletteArray);

            var data = region.getBlocks();
            var blockData = new JsonArray();
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        var obj = data.get(x, y, z);
                        if (obj instanceof BlockState bs) {
                            blockData.add(BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString());
                        } else {
                            blockData.add(obj.toString());
                        }
                    }
                }
            }
            rj.add("block_data", blockData);

            var beArray = new JsonArray();
            for (var entry : region.getBlockEntities().entrySet()) {
                long key = entry.getKey();
                int lx = (int) (key & 0xFFF);
                int ly = (int) ((key >> 12) & 0xFFF);
                int lz = (int) ((key >> 24) & 0xFFF);
                int wx = lx + region.getOffsetX();
                int wy = ly + region.getOffsetY();
                int wz = lz + region.getOffsetZ();
                var beObj = new JsonObject();
                beObj.add("pos", new JsonArray());
                beObj.getAsJsonArray("pos").add(wx);
                beObj.getAsJsonArray("pos").add(wy);
                beObj.getAsJsonArray("pos").add(wz);
                beObj.addProperty("nbt", entry.getValue().toString());
                beArray.add(beObj);
            }
            rj.add("block_entities", beArray);

            regionsArray.add(rj);
        }
        root.add("regions", regionsArray);

        var entitiesArray = new JsonArray();
        for (var entity : model.getEntities()) {
            entitiesArray.add(entity.toString());
        }
        root.add("entities", entitiesArray);

        Files.createDirectories(file.getParent());
        OutputStream out = Files.newOutputStream(file);
        if (compact) {
            out = new GZIPOutputStream(out, 65536);
        }
        try (var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            (compact ? GSON_COMPACT : GSON).toJson(root, writer);
        }
    }

    public static BuildingModel read(Path file) throws IOException {
        JsonObject root;
        try (InputStream in = openPossiblyCompressed(file);
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        var model = new BuildingModel();
        var metaJson = root.getAsJsonObject("metadata");
        var meta = model.getMetadata();
        meta.setName(metaJson.get("name").getAsString());
        meta.setAuthor(metaJson.has("author") ? metaJson.get("author").getAsString() : "");
        meta.setDescription(metaJson.has("description") ? metaJson.get("description").getAsString() : "");
        meta.setCreated(metaJson.get("created").getAsLong());
        meta.setModified(metaJson.has("modified") ? metaJson.get("modified").getAsLong() : meta.getCreated());

        while (!model.getLayers().isEmpty()) {
            model.removeLayer(model.getLayers().get(0).getId());
        }
        if (root.has("layers")) {
            for (var le : root.getAsJsonArray("layers")) {
                var lj = le.getAsJsonObject();
                var layer = model.addLayer(
                    lj.get("name").getAsString(),
                    !lj.has("visible") || lj.get("visible").getAsBoolean(),
                    lj.has("locked") && lj.get("locked").getAsBoolean()
                );
                if (lj.has("opacity")) layer.setOpacity(lj.get("opacity").getAsFloat());
            }
        }
        if (model.getLayers().isEmpty()) model.addLayer("default", true, false);

        for (var re : root.getAsJsonArray("regions")) {
            var rj = re.getAsJsonObject();
            var pos = rj.getAsJsonObject("position");
            var sz = rj.getAsJsonObject("size");
            var region = model.addRegion(
                rj.get("name").getAsString(),
                pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt(),
                sz.get("x").getAsInt(), sz.get("y").getAsInt(), sz.get("z").getAsInt()
            );

            var paletteArray = rj.getAsJsonArray("palette");
            Object[] palette = new Object[paletteArray.size()];
            for (int i = 0; i < palette.length; i++) {
                var elem = paletteArray.get(i);
                if (elem.isJsonObject()) {
                    var obj = elem.getAsJsonObject();
                    palette[i] = SchematicReaders.resolveBlockStateFromJson(obj);
                } else {
                    palette[i] = elem.getAsString();
                }
            }

            var blockData = rj.getAsJsonArray("block_data");
            int idx = 0;
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        if (idx < blockData.size()) {
                            var elem = blockData.get(idx);
                            if (elem.isJsonObject()) {
                                region.getBlocks().set(x, y, z, SchematicReaders.resolveBlockStateFromJson(elem.getAsJsonObject()));
                            } else {
                                region.getBlocks().set(x, y, z, resolveBlockStateFromString(elem.getAsString()));
                            }
                        }
                        idx++;
                    }
                }
            }

            if (rj.has("block_entities")) {
                for (var beElem : rj.getAsJsonArray("block_entities")) {
                    var beObj = beElem.getAsJsonObject();
                    var posArr = beObj.getAsJsonArray("pos");
                    int wx = posArr.get(0).getAsInt();
                    int wy = posArr.get(1).getAsInt();
                    int wz = posArr.get(2).getAsInt();
                    String nbtStr = beObj.get("nbt").getAsString();
                    try {
                        CompoundTag tag = TagParser.parseTag(nbtStr);
                        region.setWorldBlockEntity(wx, wy, wz, tag);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (root.has("entities")) {
            for (var elem : root.getAsJsonArray("entities")) {
                try {
                    model.addEntity(TagParser.parseTag(elem.getAsString()));
                } catch (Exception ignored) {
                }
            }
        }
        return model;
    }

    private static BlockState resolveBlockStateFromString(String id) {
        if (id == null || id.isEmpty() || id.equals("minecraft:air") || id.equals("air")) {
            return Blocks.AIR.defaultBlockState();
        }
        try {
            var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id));
            return block.map(value -> value.defaultBlockState()).orElseGet(() -> {
                LOG.warn("Unknown block ID in EBE file: {}, falling back to stone", id);
                return Blocks.STONE.defaultBlockState();
            });
        } catch (Exception e) {
            LOG.warn("Failed to resolve block ID in EBE file: {}", id, e);
            return Blocks.STONE.defaultBlockState();
        }
    }

    private static InputStream openPossiblyCompressed(Path file) throws IOException {
        var buffered = new BufferedInputStream(Files.newInputStream(file));
        buffered.mark(2);
        int b0 = buffered.read();
        int b1 = buffered.read();
        buffered.reset();
        if (b0 == 0x1F && b1 == 0x8B) {
            return new GZIPInputStream(buffered, 65536);
        }
        return buffered;
    }
}
