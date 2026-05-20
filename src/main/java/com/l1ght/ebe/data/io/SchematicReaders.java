package com.l1ght.ebe.data.io;

import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;

public class SchematicReaders {

    public static BuildingModel readLitematic(Path file) throws Exception {
        var root = NbtIo.read(file);
        if (root == null) throw new IllegalArgumentException("Failed to read NBT from " + file);

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

            var pos = regionTag.getCompound("Position");
            int ox = pos.getInt("x");
            int oy = pos.getInt("y");
            int oz = pos.getInt("z");

            var size = regionTag.getCompound("Size");
            int sx = Math.abs(size.getInt("x"));
            int sy = Math.abs(size.getInt("y"));
            int sz = Math.abs(size.getInt("z"));

            var region = model.addRegion(regionName, ox, oy, oz, sx, sy, sz);

            var paletteTag = regionTag.getList("BlockStatePalette", 10);
            BlockState[] palette = new BlockState[paletteTag.size()];
            for (int i = 0; i < paletteTag.size(); i++) {
                var entry = paletteTag.getCompound(i);
                palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry);
            }

            var dataArray = regionTag.getByteArray("BlockStates");
            int bitsPerEntry = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.length - 1));
            long[] packed = new long[(dataArray.length + 7) / 8];
            for (int i = 0; i < dataArray.length; i++) {
                packed[i / 8] |= ((long) dataArray[i] & 0xFFL) << ((i % 8) * 8);
            }

            int idx = 0;
            int entriesPerLong = 64 / bitsPerEntry;
            long mask = (1L << bitsPerEntry) - 1L;

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        int longIdx = idx / entriesPerLong;
                        int bitOffset = (idx % entriesPerLong) * bitsPerEntry;
                        int paletteIdx = longIdx < packed.length
                                ? (int) ((packed[longIdx] >>> bitOffset) & mask) : 0;

                        BlockState bs = paletteIdx < palette.length ? palette[paletteIdx] : Blocks.AIR.defaultBlockState();
                        if (!bs.isAir()) {
                            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString();
                            region.setWorldBlock(x + ox, y + oy, z + oz, id);
                        }
                        idx++;
                    }
                }
            }
        }

        return model;
    }

    public static BuildingModel readNbtStructure(Path file) throws Exception {
        var root = NbtIo.read(file);
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
                var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bs.getBlock()).toString();
                region.setWorldBlock(pos[0], pos[1], pos[2], id);
            }
        }

        return model;
    }
}
