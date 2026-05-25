package com.l1ght.ebe.data.io;

import com.google.gson.JsonObject;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class SchematicReaders {

    private static final Logger LOG = LoggerFactory.getLogger("EBE/SchematicReaders");

    private static BlockState safeReadBlockState(CompoundTag tag) {
        try {
            return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
        } catch (Exception e) {
            var nameTag = tag.get("Name");
            if (nameTag != null) {
                var name = nameTag.getAsString();
                LOG.warn("Unknown block '{}', falling back to stone", name);
                try {
                    var loc = net.minecraft.resources.ResourceLocation.parse(name);
                    var block = BuiltInRegistries.BLOCK.getOptional(loc);
                    if (block.isPresent()) {
                        var state = block.get().defaultBlockState();
                        return applyProperties(state, tag);
                    }
                } catch (Exception parseError) {
                    LOG.warn("Failed to parse fallback block state name {}", name, parseError);
                }
            }
            return Blocks.STONE.defaultBlockState();
        }
    }

    private static BlockState applyProperties(BlockState state, CompoundTag tag) {
        var propsTag = tag.get("Properties");
        if (propsTag == null) return state;
        if (!(propsTag instanceof CompoundTag properties)) return state;

        for (var key : properties.getAllKeys()) {
            var value = properties.getString(key);
            for (Property<?> prop : state.getProperties()) {
                if (prop.getName().equals(key)) {
                    state = setProperty(state, prop, value);
                    break;
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> prop, String value) {
        Optional<T> parsed = prop.getValue(value);
        if (parsed.isPresent()) {
            return state.setValue(prop, parsed.get());
        }
        return state;
    }

    public static BuildingModel readLitematic(Path file) throws Exception {
        var root = readNbtAnyFormat(file);
        if (root == null) throw new IllegalArgumentException("Failed to read NBT from " + file);

        int version = root.getInt("Version");
        if (version < 1 || version > 7) {
            throw new IllegalArgumentException("Unsupported litematic version: " + version);
        }

        var model = new BuildingModel();
        var meta = root.getCompound("Metadata");

        model.getMetadata().setName(meta.getString("Name"));
        model.getMetadata().setAuthor(meta.getString("Author"));
        model.getMetadata().setDescription(meta.getString("Description"));
        model.getMetadata().setCreated(meta.getLong("TimeCreated"));
        model.getMetadata().setModified(meta.getLong("TimeModified"));

        var enclosingSize = meta.getCompound("EnclosingSize");
        int totalX = enclosingSize.getInt("x");
        int totalY = enclosingSize.getInt("y");
        int totalZ = enclosingSize.getInt("z");
        model.getMetadata().setSize(totalX, totalY, totalZ);

        var regions = root.getCompound("Regions");
        for (var regionName : regions.getAllKeys()) {
            var regionTag = regions.getCompound(regionName);

            var posTag = regionTag.getCompound("Position");
            int posX = posTag.getInt("x");
            int posY = posTag.getInt("y");
            int posZ = posTag.getInt("z");

            var sizeTag = regionTag.getCompound("Size");
            int sizeX = sizeTag.getInt("x");
            int sizeY = sizeTag.getInt("y");
            int sizeZ = sizeTag.getInt("z");

            int endX = posX + sizeX - Integer.signum(sizeX);
            int endY = posY + sizeY - Integer.signum(sizeY);
            int endZ = posZ + sizeZ - Integer.signum(sizeZ);

            int minX = Math.min(posX, endX);
            int minY = Math.min(posY, endY);
            int minZ = Math.min(posZ, endZ);
            int absSizeX = Math.abs(sizeX);
            int absSizeY = Math.abs(sizeY);
            int absSizeZ = Math.abs(sizeZ);

            var paletteTag = regionTag.getList("BlockStatePalette", 10);
            BlockState[] palette = new BlockState[paletteTag.size()];
            for (int i = 0; i < paletteTag.size(); i++) {
                palette[i] = safeReadBlockState(paletteTag.getCompound(i));
            }

            var blockStatesTag = regionTag.get("BlockStates");
            if (blockStatesTag == null) continue;

            long[] packed;
            if (blockStatesTag instanceof net.minecraft.nbt.LongArrayTag longArrayTag) {
                packed = longArrayTag.getAsLongArray();
            } else {
                continue;
            }

            int bitsPerEntry = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(0, palette.length - 1)));
            long maxEntryValue = (1L << bitsPerEntry) - 1L;

            var region = model.addRegion(regionName, minX, minY, minZ, absSizeX, absSizeY, absSizeZ);

            int sizeLayer = absSizeX * absSizeZ;
            for (int y = 0; y < absSizeY; y++) {
                for (int z = 0; z < absSizeZ; z++) {
                    for (int x = 0; x < absSizeX; x++) {
                        int index = y * sizeLayer + z * absSizeX + x;
                        long startOffset = (long) index * bitsPerEntry;
                        int startArrIdx = (int) (startOffset >> 6);
                        int endArrIdx = (int) (((long) (index + 1) * bitsPerEntry - 1) >> 6);
                        int startBitOffset = (int) (startOffset & 0x3F);

                        int paletteIdx;
                        if (startArrIdx == endArrIdx) {
                            paletteIdx = (int) (packed[startArrIdx] >>> startBitOffset & maxEntryValue);
                        } else {
                            int endOffset = 64 - startBitOffset;
                            paletteIdx = (int) ((packed[startArrIdx] >>> startBitOffset | packed[endArrIdx] << endOffset) & maxEntryValue);
                        }

                        BlockState bs = paletteIdx < palette.length ? palette[paletteIdx] : Blocks.AIR.defaultBlockState();
                        if (!bs.isAir()) {
                            region.setWorldBlock(x + minX, y + minY, z + minZ, bs);
                        }
                    }
                }
            }

            var tileEntitiesTag = regionTag.getList("TileEntities", 10);
            for (int i = 0; i < tileEntitiesTag.size(); i++) {
                var teTag = tileEntitiesTag.getCompound(i);
                var posArr = teTag.getIntArray("Pos");
                if (posArr.length == 3) {
                    region.setWorldBlockEntity(posArr[0] + minX, posArr[1] + minY, posArr[2] + minZ, teTag);
                }
            }

            var entitiesTag = regionTag.getList("Entities", 10);
            for (int i = 0; i < entitiesTag.size(); i++) {
                model.addEntity(entitiesTag.getCompound(i));
            }
        }

        return model;
    }

    public static BuildingModel readNbtStructure(Path file) throws Exception {
        var root = readNbtAnyFormat(file);
        if (root == null) throw new IllegalArgumentException("Failed to read NBT from " + file);

        var model = new BuildingModel();
        model.getMetadata().setName(file.getFileName().toString());

        var paletteTag = root.getList("palette", 10);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = safeReadBlockState(paletteTag.getCompound(i));
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        var blocksList = root.getList("blocks", 10);
        for (int i = 0; i < blocksList.size(); i++) {
            var block = blocksList.getCompound(i);
            var pos = block.getIntArray("pos");
            int x = pos[0], y = pos[1], z = pos[2];
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }

        boolean hasBlocks = minX <= maxX;
        if (!hasBlocks) {
            var sizeTag = root.getList("size", 3);
            if (sizeTag.size() >= 3) {
                minX = 0;
                minY = 0;
                minZ = 0;
                maxX = Math.max(0, sizeTag.getInt(0) - 1);
                maxY = Math.max(0, sizeTag.getInt(1) - 1);
                maxZ = Math.max(0, sizeTag.getInt(2) - 1);
            } else {
                return model;
            }
        }

        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;
        model.getMetadata().setSize(sx, sy, sz);

        var region = model.addRegion("structure", minX, minY, minZ, sx, sy, sz);

        for (int i = 0; i < blocksList.size(); i++) {
            var block = blocksList.getCompound(i);
            var pos = block.getIntArray("pos");
            int paletteIdx = block.getInt("state");
            BlockState bs = paletteIdx < palette.length ? palette[paletteIdx] : Blocks.AIR.defaultBlockState();
            if (!bs.isAir()) {
                region.setWorldBlock(pos[0], pos[1], pos[2], bs);
            }
            if (block.contains("nbt", 10)) {
                region.setWorldBlockEntity(pos[0], pos[1], pos[2], block.getCompound("nbt"));
            }
        }

        var entitiesList = root.getList("entities", 10);
        for (int i = 0; i < entitiesList.size(); i++) {
            var entityEntry = entitiesList.getCompound(i);
            if (entityEntry.contains("nbt", 10)) {
                var entity = entityEntry.getCompound("nbt").copy();
                var pos = entityEntry.getList("pos", 6);
                if (pos.size() >= 3) {
                    entity.put("Pos", doubleList(pos.getDouble(0) + minX, pos.getDouble(1) + minY, pos.getDouble(2) + minZ));
                }
                model.addEntity(entity);
            } else {
                model.addEntity(entityEntry);
            }
        }

        return model;
    }

    public static BuildingModel readSpongeSchem(Path file) throws Exception {
        var root = readNbtAnyFormat(file);
        if (root == null) throw new IllegalArgumentException("Failed to read NBT from " + file);

        int width = root.getShort("Width");
        int height = root.getShort("Height");
        int length = root.getShort("Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IllegalArgumentException("Invalid Sponge schematic dimensions");
        }

        int[] offset = root.getIntArray("Offset");
        int ox = offset.length > 0 ? offset[0] : 0;
        int oy = offset.length > 1 ? offset[1] : 0;
        int oz = offset.length > 2 ? offset[2] : 0;

        var model = new BuildingModel();
        model.getMetadata().setName(file.getFileName().toString());
        model.getMetadata().setSize(width, height, length);
        var region = model.addRegion("schem", ox, oy, oz, width, height, length);

        Map<Integer, BlockState> palette = new LinkedHashMap<>();
        var paletteTag = root.getCompound("Palette");
        for (var key : paletteTag.getAllKeys()) {
            palette.put(paletteTag.getInt(key), parseStateString(key));
        }

        int[] indexes;
        if (root.contains("BlockData", 7)) {
            indexes = readVarInts(root.getByteArray("BlockData"), width * height * length);
        } else if (root.contains("BlockData", 11)) {
            indexes = root.getIntArray("BlockData");
        } else {
            throw new IllegalArgumentException("Sponge schematic has no BlockData");
        }

        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    BlockState state = palette.getOrDefault(idx < indexes.length ? indexes[idx] : 0, Blocks.AIR.defaultBlockState());
                    if (!state.isAir()) {
                        region.setWorldBlock(ox + x, oy + y, oz + z, state);
                    }
                    idx++;
                }
            }
        }

        var blockEntities = root.getList("BlockEntities", 10);
        for (int i = 0; i < blockEntities.size(); i++) {
            var tag = blockEntities.getCompound(i);
            int[] pos = tag.getIntArray("Pos");
            if (pos.length == 3) {
                region.setWorldBlockEntity(ox + pos[0], oy + pos[1], oz + pos[2], tag);
            }
        }

        var entities = root.getList("Entities", 10);
        for (int i = 0; i < entities.size(); i++) {
            model.addEntity(entities.getCompound(i));
        }

        return model;
    }

    public static BuildingModel readSchematica(Path file) throws Exception {
        var root = readNbtAnyFormat(file);
        if (root == null) throw new IllegalArgumentException("Failed to read NBT from " + file);

        int width = root.getShort("Width");
        int height = root.getShort("Height");
        int length = root.getShort("Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IllegalArgumentException("Invalid Schematica dimensions");
        }
        if (root.contains("Materials") && !"Alpha".equalsIgnoreCase(root.getString("Materials"))) {
            throw new IllegalArgumentException("Unsupported Schematica materials format: " + root.getString("Materials"));
        }

        var model = new BuildingModel();
        model.getMetadata().setName(file.getFileName().toString());
        model.getMetadata().setSize(width, height, length);
        var region = model.addRegion("schematic", 0, 0, 0, width, height, length);

        byte[] blocks = root.getByteArray("Blocks");
        byte[] data = root.getByteArray("Data");
        byte[] addBlocks = readSchematicaExtraBlocks(root, width * height * length);
        Map<Integer, ResourceLocation> mapping = readSchematicaMapping(root);
        int volume = width * height * length;
        for (int i = 0; i < Math.min(volume, blocks.length); i++) {
            int id = blocks[i] & 0xFF;
            if (addBlocks.length > i) {
                id |= (addBlocks[i] & 0x0F) << 8;
            }
            int meta = data.length > i ? data[i] & 0x0F : 0;
            BlockState state = readLegacySchematicaState(id, meta, mapping);
            if (state == null) state = Blocks.AIR.defaultBlockState();
            if (!state.isAir()) {
                int x = i % width;
                int z = (i / width) % length;
                int y = i / (width * length);
                region.setWorldBlock(x, y, z, state);
            }
        }

        var tileEntities = root.getList("TileEntities", 10);
        for (int i = 0; i < tileEntities.size(); i++) {
            var tag = tileEntities.getCompound(i);
            region.setWorldBlockEntity(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"), tag);
        }

        var entities = root.getList("Entities", 10);
        for (int i = 0; i < entities.size(); i++) {
            model.addEntity(entities.getCompound(i));
        }

        return model;
    }

    private static byte[] readSchematicaExtraBlocks(CompoundTag root, int volume) {
        if (root.contains("AddBlocksSchematica", 7)) {
            byte[] raw = root.getByteArray("AddBlocksSchematica");
            if (raw.length >= volume) return raw;
            byte[] expanded = new byte[volume];
            System.arraycopy(raw, 0, expanded, 0, raw.length);
            return expanded;
        }
        if (!root.contains("AddBlocks", 7)) return new byte[0];
        byte[] nibble = root.getByteArray("AddBlocks");
        byte[] expanded = new byte[volume];
        for (int i = 0; i < nibble.length; i++) {
            int value = nibble[i] & 0xFF;
            int even = i * 2;
            int odd = even + 1;
            if (even < expanded.length) expanded[even] = (byte) ((value >> 4) & 0x0F);
            if (odd < expanded.length) expanded[odd] = (byte) (value & 0x0F);
        }
        return expanded;
    }

    private static Map<Integer, ResourceLocation> readSchematicaMapping(CompoundTag root) {
        Map<Integer, ResourceLocation> mapping = new LinkedHashMap<>();
        if (!root.contains("SchematicaMapping", 10)) return mapping;
        var tag = root.getCompound("SchematicaMapping");
        for (String name : tag.getAllKeys()) {
            try {
                mapping.put((int) tag.getShort(name), ResourceLocation.parse(name));
            } catch (Exception e) {
                LOG.warn("Ignoring invalid SchematicaMapping entry {}", name, e);
            }
        }
        return mapping;
    }

    private static BlockState readLegacySchematicaState(int id, int meta, Map<Integer, ResourceLocation> mapping) {
        ResourceLocation mapped = mapping.get(id);
        if (mapped != null) {
            var block = BuiltInRegistries.BLOCK.getOptional(mapped);
            if (block.isPresent()) {
                if (meta != 0) {
                    LOG.debug("Schematica metadata {} for {} cannot be losslessly migrated on Minecraft 1.21; using default state", meta, mapped);
                }
                return block.get().defaultBlockState();
            }
            LOG.warn("Unknown block in SchematicaMapping: {}, falling back to stone", mapped);
            return Blocks.STONE.defaultBlockState();
        }
        return Block.stateById((id << 4) | meta);
    }

    public static BlockState resolveBlockStateFromJson(JsonObject obj) {
        if (!obj.has("id")) return Blocks.AIR.defaultBlockState();
        var id = obj.get("id").getAsString();
        if (id.isEmpty() || id.equals("minecraft:air") || id.equals("air")) {
            return Blocks.AIR.defaultBlockState();
        }
        try {
            var loc = ResourceLocation.parse(id);
            var block = BuiltInRegistries.BLOCK.getOptional(loc);
            if (block.isEmpty()) {
                LOG.warn("Unknown block ID in EBE file: {}, falling back to stone", id);
                return Blocks.STONE.defaultBlockState();
            }
            var state = block.get().defaultBlockState();
            if (obj.has("properties")) {
                var props = obj.getAsJsonObject("properties");
                for (var entry : props.entrySet()) {
                    var propName = entry.getKey();
                    var propValue = entry.getValue().getAsString();
                    for (Property<?> prop : state.getProperties()) {
                        if (prop.getName().equals(propName)) {
                            state = setProperty(state, prop, propValue);
                            break;
                        }
                    }
                }
            }
            return state;
        } catch (Exception e) {
            LOG.warn("Failed to resolve block state from JSON: {}", id, e);
            return Blocks.STONE.defaultBlockState();
        }
    }

    private static BlockState parseStateString(String raw) {
        if (raw == null || raw.isBlank()) return Blocks.AIR.defaultBlockState();
        String id = raw;
        Map<String, String> props = new LinkedHashMap<>();
        int propStart = raw.indexOf('[');
        if (propStart >= 0 && raw.endsWith("]")) {
            id = raw.substring(0, propStart);
            String body = raw.substring(propStart + 1, raw.length() - 1);
            for (String part : body.split(",")) {
                int eq = part.indexOf('=');
                if (eq > 0) props.put(part.substring(0, eq), part.substring(eq + 1));
            }
        }
        try {
            var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id));
            if (block.isEmpty()) return Blocks.STONE.defaultBlockState();
            BlockState state = block.get().defaultBlockState();
            for (var entry : props.entrySet()) {
                for (Property<?> prop : state.getProperties()) {
                    if (prop.getName().equals(entry.getKey())) {
                        state = setProperty(state, prop, entry.getValue());
                        break;
                    }
                }
            }
            return state;
        } catch (Exception e) {
            LOG.warn("Failed to parse block state string: {}", raw, e);
            return Blocks.STONE.defaultBlockState();
        }
    }

    private static int[] readVarInts(byte[] bytes, int expected) {
        int[] values = new int[expected];
        int valueIndex = 0;
        int value = 0;
        int shift = 0;
        for (byte b : bytes) {
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                if (valueIndex < values.length) values[valueIndex++] = value;
                value = 0;
                shift = 0;
            } else {
                shift += 7;
            }
        }
        return values;
    }

    private static ListTag doubleList(double x, double y, double z) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(x));
        list.add(DoubleTag.valueOf(y));
        list.add(DoubleTag.valueOf(z));
        return list;
    }

    private static CompoundTag readNbtAnyFormat(Path file) {
        CompoundTag result = null;

        try {
            result = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (result != null) {
                LOG.info("Read NBT from {} using NbtIo.readCompressed (GZIP)", file.getFileName());
                return result;
            }
        } catch (Exception e) {
            LOG.debug("NbtIo.readCompressed failed: {}", e.getMessage());
        }

        try (var fis = Files.newInputStream(file);
             var bis = new BufferedInputStream(fis)) {
            bis.mark(2);
            byte[] header = new byte[2];
            int read = bis.read(header);
            bis.reset();

            if (read >= 2 && (header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B) {
                try (var gzis = new GZIPInputStream(bis);
                     var dis = new DataInputStream(gzis)) {
                    result = NbtIo.read(dis, NbtAccounter.unlimitedHeap());
                    if (result != null) {
                        LOG.info("Read NBT from {} using manual GZIP", file.getFileName());
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Manual GZIP failed: {}", e.getMessage());
        }

        try (var fis = Files.newInputStream(file);
             var iis = new InflaterInputStream(fis);
             var dis = new DataInputStream(iis)) {
            result = NbtIo.read(dis, NbtAccounter.unlimitedHeap());
            if (result != null) {
                LOG.info("Read NBT from {} using Zlib/Deflate", file.getFileName());
                return result;
            }
        } catch (Exception e) {
            LOG.debug("Zlib/Deflate failed: {}", e.getMessage());
        }

        try (var fis = Files.newInputStream(file);
             var iis = new java.util.zip.InflaterInputStream(fis, new java.util.zip.Inflater(true));
             var dis = new DataInputStream(iis)) {
            result = NbtIo.read(dis, NbtAccounter.unlimitedHeap());
            if (result != null) {
                LOG.info("Read NBT from {} using raw Deflate (no Zlib header)", file.getFileName());
                return result;
            }
        } catch (Exception e) {
            LOG.debug("Raw Deflate failed: {}", e.getMessage());
        }

        try {
            result = NbtIo.read(file);
            if (result != null) {
                LOG.info("Read NBT from {} using uncompressed format", file.getFileName());
                return result;
            }
        } catch (Exception e) {
            LOG.debug("Uncompressed failed: {}", e.getMessage());
        }

        try (var fis = Files.newInputStream(file)) {
            byte[] header = new byte[Math.min(16, fis.available())];
            fis.read(header);
            var hex = new StringBuilder();
            for (byte b : header) hex.append(String.format("%02X ", b & 0xFF));
            LOG.error("Failed to read NBT from {}: all formats failed. File header bytes: [{}]", file.getFileName(), hex.toString().trim());
        } catch (Exception e) {
            LOG.warn("Failed to inspect unreadable NBT header for {}", file.getFileName(), e);
        }

        return null;
    }
}
