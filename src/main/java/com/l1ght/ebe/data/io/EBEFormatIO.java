package com.l1ght.ebe.data.io;

import com.google.gson.*;
import com.l1ght.ebe.data.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class EBEFormatIO {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void write(BuildingModel model, Path file) throws IOException {
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
        for (var entry : model.getLayers().entrySet()) {
            var l = entry.getValue();
            var lj = new JsonObject();
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
                paletteArray.add(state.toString());
            }
            rj.add("palette", paletteArray);

            var data = region.getBlocks();
            var blockData = new JsonArray();
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        blockData.add(data.get(x, y, z).toString());
                    }
                }
            }
            rj.add("block_data", blockData);

            regionsArray.add(rj);
        }
        root.add("regions", regionsArray);

        Files.createDirectories(file.getParent());
        try (var writer = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    public static BuildingModel read(Path file) throws IOException {
        var root = JsonParser.parseReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)).getAsJsonObject();

        var model = new BuildingModel();
        var metaJson = root.getAsJsonObject("metadata");
        var meta = model.getMetadata();
        meta.setName(metaJson.get("name").getAsString());
        meta.setAuthor(metaJson.has("author") ? metaJson.get("author").getAsString() : "");
        meta.setDescription(metaJson.has("description") ? metaJson.get("description").getAsString() : "");
        meta.setCreated(metaJson.get("created").getAsLong());
        meta.setModified(metaJson.has("modified") ? metaJson.get("modified").getAsLong() : meta.getCreated());

        model.clearLayers();
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
            String[] palette = new String[paletteArray.size()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = paletteArray.get(i).getAsString();
            }

            var blockData = rj.getAsJsonArray("block_data");
            int idx = 0;
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        if (idx < blockData.size()) {
                            region.getBlocks().set(x, y, z, blockData.get(idx).getAsString());
                        }
                        idx++;
                    }
                }
            }
        }
        return model;
    }
}
