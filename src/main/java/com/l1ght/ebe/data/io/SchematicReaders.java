package com.l1ght.ebe.data.io;

import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class SchematicReaders {

    private static final Logger LOG = LoggerFactory.getLogger("EBE/SchematicReaders");

    public static BuildingModel readLitematic(Path file) throws Exception {
        var root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
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
                palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i));
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
            long totalVolume = (long) absSizeX * absSizeY * absSizeZ;

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
                            var id = BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString();
                            region.setWorldBlock(x + minX, y + minY, z + minZ, id);
                        }
                    }
                }
            }
        }

        return model;
    }

    public static BuildingModel readNbtStructure(Path file) throws Exception {
        CompoundTag root = null;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (Exception e) {
            try {
                root = NbtIo.read(file);
            } catch (Exception e2) {
                LOG.error("Failed to read NBT structure from {}: compressed and uncompressed both failed", file, e2);
            }
        }
        if (root == null) throw new IllegalArgumentException("Failed to read NBT from " + file);

        var model = new BuildingModel();
        model.getMetadata().setName(file.getFileName().toString());

        var paletteTag = root.getList("palette", 10);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i));
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
                var id = BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString();
                region.setWorldBlock(pos[0], pos[1], pos[2], id);
            }
        }

        return model;
    }
}
