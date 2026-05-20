package com.l1ght.ebe.data.io;

import com.google.gson.JsonObject;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
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
                } catch (Exception ignored) {}
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

        if (minX > maxX) return model;

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
        }

        return model;
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

    private static CompoundTag readNbtAnyFormat(Path file) {
        CompoundTag result = null;

        try {
            result = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (result != null) {
                LOG.debug("Read NBT from {} using GZIP format", file.getFileName());
                return result;
            }
        } catch (Exception ignored) {}

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
                        LOG.debug("Read NBT from {} using manual GZIP format", file.getFileName());
                        return result;
                    }
                }
            }
        } catch (Exception ignored) {}

        try (var fis = Files.newInputStream(file);
             var iis = new InflaterInputStream(fis);
             var dis = new DataInputStream(iis)) {
            result = NbtIo.read(dis, NbtAccounter.unlimitedHeap());
            if (result != null) {
                LOG.debug("Read NBT from {} using Zlib/Deflate format", file.getFileName());
                return result;
            }
        } catch (Exception ignored) {}

        try {
            result = NbtIo.read(file);
            if (result != null) {
                LOG.debug("Read NBT from {} using uncompressed format", file.getFileName());
                return result;
            }
        } catch (Exception ignored) {}

        LOG.error("Failed to read NBT from {}: all formats (GZIP, Zlib, uncompressed) failed", file);
        return null;
    }
}
