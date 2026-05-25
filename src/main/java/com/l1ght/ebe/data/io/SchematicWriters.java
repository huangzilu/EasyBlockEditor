package com.l1ght.ebe.data.io;

import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.Region;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SchematicWriters {

    public static void write(BuildingModel model, Path file) throws IOException {
        String ext = FileManager.getFileExtension(file).toLowerCase();
        switch (ext) {
            case ".ebe" -> EBEFormatIO.write(model, file);
            case ".nbt" -> writeVanillaStructure(model, file);
            case ".schem" -> writeSpongeSchem(model, file);
            case ".litematic" -> writeLitematic(model, file);
            case ".schematic" -> writeSchematica(model, file);
            default -> throw new IOException("Unsupported export format: " + ext);
        }
    }

    public static void writeVanillaStructure(BuildingModel model, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Bounds bounds = Bounds.of(model);
        CompoundTag root = new CompoundTag();
        root.put("size", intList(bounds.sizeX(), bounds.sizeY(), bounds.sizeZ()));

        Map<String, Integer> paletteIds = new LinkedHashMap<>();
        List<BlockState> states = new ArrayList<>();
        ListTag blocks = new ListTag();
        forEachBlock(model, (region, lx, ly, lz, wx, wy, wz, state) -> {
            if (state.isAir()) return;
            String key = stateToString(state);
            int id = paletteIds.computeIfAbsent(key, ignored -> {
                states.add(state);
                return states.size() - 1;
            });

            CompoundTag block = new CompoundTag();
            block.put("pos", intList(wx - bounds.minX(), wy - bounds.minY(), wz - bounds.minZ()));
            block.putInt("state", id);
            CompoundTag be = region.getWorldBlockEntity(wx, wy, wz);
            if (be != null && state.hasBlockEntity()) block.put("nbt", cleanBlockEntityPos(be));
            blocks.add(block);
        });

        ListTag palette = new ListTag();
        for (BlockState state : states) {
            palette.add(NbtUtils.writeBlockState(state));
        }
        root.put("palette", palette);
        root.put("blocks", blocks);
        root.put("entities", toVanillaStructureEntities(model, bounds));
        writeCompressed(root, file);
    }

    public static void writeSpongeSchem(BuildingModel model, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Bounds bounds = Bounds.of(model);
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.putShort("Width", (short) bounds.sizeX());
        root.putShort("Height", (short) bounds.sizeY());
        root.putShort("Length", (short) bounds.sizeZ());
        root.putIntArray("Offset", new int[]{bounds.minX(), bounds.minY(), bounds.minZ()});

        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put(stateToString(Blocks.AIR.defaultBlockState()), 0);
        int[] indexes = new int[bounds.sizeX() * bounds.sizeY() * bounds.sizeZ()];
        forEachBlock(model, (region, lx, ly, lz, wx, wy, wz, state) -> {
            String key = stateToString(state);
            int id = palette.computeIfAbsent(key, ignored -> palette.size());
            int index = (wy - bounds.minY()) * bounds.sizeX() * bounds.sizeZ()
                    + (wz - bounds.minZ()) * bounds.sizeX()
                    + (wx - bounds.minX());
            indexes[index] = id;
        });

        CompoundTag paletteTag = new CompoundTag();
        for (var entry : palette.entrySet()) {
            paletteTag.putInt(entry.getKey(), entry.getValue());
        }
        root.put("Palette", paletteTag);
        root.putInt("PaletteMax", palette.size());
        root.putByteArray("BlockData", writeVarInts(indexes));

        ListTag blockEntities = new ListTag();
        for (Region region : model.getRegions()) {
            for (var entry : region.getBlockEntities().entrySet()) {
                int lx = (int) (entry.getKey() & 0xFFF);
                int ly = (int) ((entry.getKey() >> 12) & 0xFFF);
                int lz = (int) ((entry.getKey() >> 24) & 0xFFF);
                BlockState state = resolveBlockState(region.getBlocks().get(lx, ly, lz));
                if (state.isAir() || !state.hasBlockEntity()) continue;
                CompoundTag be = cleanBlockEntityPos(entry.getValue());
                be.putIntArray("Pos", new int[]{
                        region.getOffsetX() + lx - bounds.minX(),
                        region.getOffsetY() + ly - bounds.minY(),
                        region.getOffsetZ() + lz - bounds.minZ()
                });
                blockEntities.add(be);
            }
        }
        root.put("BlockEntities", blockEntities);
        root.put("Entities", copyEntityList(model));
        writeCompressed(root, file);
    }

    public static void writeLitematic(BuildingModel model, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Bounds bounds = Bounds.of(model);
        CompoundTag root = new CompoundTag();
        root.putInt("MinecraftDataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.putInt("Version", 6);
        root.putInt("SubVersion", 1);

        CompoundTag metadata = new CompoundTag();
        metadata.putString("Name", model.getMetadata().getName());
        metadata.putString("Author", model.getMetadata().getAuthor());
        metadata.putString("Description", model.getMetadata().getDescription());
        metadata.putLong("TimeCreated", model.getMetadata().getCreated());
        metadata.putLong("TimeModified", model.getMetadata().getModified());
        metadata.put("EnclosingSize", vecTag(bounds.sizeX(), bounds.sizeY(), bounds.sizeZ()));
        metadata.putInt("RegionCount", model.getRegions().size());
        root.put("Metadata", metadata);

        CompoundTag regions = new CompoundTag();
        boolean wroteRegionEntities = false;
        for (Region region : model.getRegions()) {
            regions.put(region.getName(), toLitematicRegion(region, wroteRegionEntities ? List.of() : model.getEntities()));
            wroteRegionEntities = true;
        }
        root.put("Regions", regions);
        root.put("Entities", copyEntityList(model));
        writeCompressed(root, file);
    }

    public static void writeSchematica(BuildingModel model, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Bounds bounds = Bounds.of(model);
        CompoundTag root = new CompoundTag();
        root.putShort("Width", (short) bounds.sizeX());
        root.putShort("Height", (short) bounds.sizeY());
        root.putShort("Length", (short) bounds.sizeZ());
        root.putString("Materials", "Alpha");

        int volume = bounds.sizeX() * bounds.sizeY() * bounds.sizeZ();
        byte[] blocks = new byte[volume];
        byte[] data = new byte[volume];
        byte[] extraBlocks = new byte[volume];
        boolean[] hasExtraBlocks = new boolean[]{false};
        Map<String, Integer> schematicaIds = new LinkedHashMap<>();
        schematicaIds.put(schematicaBlockName(Blocks.AIR.defaultBlockState()), 0);
        forEachBlock(model, (region, lx, ly, lz, wx, wy, wz, state) -> {
            int index = (wy - bounds.minY()) * bounds.sizeX() * bounds.sizeZ()
                    + (wz - bounds.minZ()) * bounds.sizeX()
                    + (wx - bounds.minX());
            String key = schematicaBlockName(state);
            int blockId = schematicaIds.computeIfAbsent(key, ignored -> schematicaIds.size());
            blocks[index] = (byte) (blockId & 0xFF);
            extraBlocks[index] = (byte) ((blockId >> 8) & 0x0F);
            if (extraBlocks[index] != 0) hasExtraBlocks[0] = true;
            data[index] = (byte) (net.minecraft.world.level.block.Block.getId(state) & 0x0F);
        });
        root.putByteArray("Blocks", blocks);
        root.putByteArray("Data", data);
        if (hasExtraBlocks[0]) {
            byte[] packed = new byte[(int) Math.ceil(volume / 2.0)];
            for (int i = 0; i < packed.length; i++) {
                int even = i * 2;
                int odd = even + 1;
                int high = even < extraBlocks.length ? extraBlocks[even] & 0x0F : 0;
                int low = odd < extraBlocks.length ? extraBlocks[odd] & 0x0F : 0;
                packed[i] = (byte) ((high << 4) | low);
            }
            root.putByteArray("AddBlocks", packed);
        }

        CompoundTag mapping = new CompoundTag();
        for (var entry : schematicaIds.entrySet()) {
            mapping.putShort(entry.getKey(), (short) (int) entry.getValue());
        }
        root.put("SchematicaMapping", mapping);

        ListTag tileEntities = new ListTag();
        for (Region region : model.getRegions()) {
            for (var entry : region.getBlockEntities().entrySet()) {
                int lx = (int) (entry.getKey() & 0xFFF);
                int ly = (int) ((entry.getKey() >> 12) & 0xFFF);
                int lz = (int) ((entry.getKey() >> 24) & 0xFFF);
                BlockState state = resolveBlockState(region.getBlocks().get(lx, ly, lz));
                if (state.isAir() || !state.hasBlockEntity()) continue;
                CompoundTag be = entry.getValue().copy();
                be.putInt("x", region.getOffsetX() + lx - bounds.minX());
                be.putInt("y", region.getOffsetY() + ly - bounds.minY());
                be.putInt("z", region.getOffsetZ() + lz - bounds.minZ());
                tileEntities.add(be);
            }
        }
        root.put("TileEntities", tileEntities);
        root.put("Entities", copyEntityList(model));
        writeCompressed(root, file);
    }

    private static CompoundTag toLitematicRegion(Region region, List<CompoundTag> entities) {
        CompoundTag tag = new CompoundTag();
        tag.put("Position", vecTag(region.getOffsetX(), region.getOffsetY(), region.getOffsetZ()));
        tag.put("Size", vecTag(region.getSizeX(), region.getSizeY(), region.getSizeZ()));

        Map<String, Integer> paletteIds = new LinkedHashMap<>();
        List<BlockState> states = new ArrayList<>();
        int volume = region.getSizeX() * region.getSizeY() * region.getSizeZ();
        int[] indexes = new int[volume];

        for (int y = 0; y < region.getSizeY(); y++) {
            for (int z = 0; z < region.getSizeZ(); z++) {
                for (int x = 0; x < region.getSizeX(); x++) {
                    BlockState state = resolveBlockState(region.getBlocks().get(x, y, z));
                    String key = stateToString(state);
                    int id = paletteIds.computeIfAbsent(key, ignored -> {
                        states.add(state);
                        return states.size() - 1;
                    });
                    indexes[y * region.getSizeX() * region.getSizeZ() + z * region.getSizeX() + x] = id;
                }
            }
        }

        ListTag palette = new ListTag();
        for (BlockState state : states) palette.add(NbtUtils.writeBlockState(state));
        tag.put("BlockStatePalette", palette);
        tag.putLongArray("BlockStates", packIndexes(indexes, Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(0, states.size() - 1)))));

        ListTag tileEntities = new ListTag();
        for (var entry : region.getBlockEntities().entrySet()) {
            int lx = (int) (entry.getKey() & 0xFFF);
            int ly = (int) ((entry.getKey() >> 12) & 0xFFF);
            int lz = (int) ((entry.getKey() >> 24) & 0xFFF);
            BlockState state = resolveBlockState(region.getBlocks().get(lx, ly, lz));
            if (state.isAir() || !state.hasBlockEntity()) continue;
            CompoundTag be = entry.getValue().copy();
            be.putIntArray("Pos", new int[]{lx, ly, lz});
            tileEntities.add(be);
        }
        tag.put("TileEntities", tileEntities);
        tag.put("Entities", copyEntityList(entities));
        return tag;
    }

    private static ListTag copyEntityList(BuildingModel model) {
        return copyEntityList(model.getEntities());
    }

    private static ListTag copyEntityList(List<CompoundTag> entities) {
        ListTag list = new ListTag();
        for (CompoundTag entity : entities) list.add(entity.copy());
        return list;
    }

    private static ListTag toVanillaStructureEntities(BuildingModel model, Bounds bounds) {
        ListTag list = new ListTag();
        for (CompoundTag entity : model.getEntities()) {
            if (entity == null || !entity.contains("Pos", 9)) continue;
            ListTag pos = entity.getList("Pos", 6);
            if (pos.size() < 3) continue;
            double relX = pos.getDouble(0) - bounds.minX();
            double relY = pos.getDouble(1) - bounds.minY();
            double relZ = pos.getDouble(2) - bounds.minZ();

            CompoundTag wrapper = new CompoundTag();
            wrapper.put("pos", doubleList(relX, relY, relZ));
            wrapper.put("blockPos", intList((int) Math.floor(relX), (int) Math.floor(relY), (int) Math.floor(relZ)));
            CompoundTag nbt = entity.copy();
            nbt.put("Pos", doubleList(relX, relY, relZ));
            wrapper.put("nbt", nbt);
            list.add(wrapper);
        }
        return list;
    }

    private static ListTag intList(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }

    private static ListTag doubleList(double x, double y, double z) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(x));
        list.add(DoubleTag.valueOf(y));
        list.add(DoubleTag.valueOf(z));
        return list;
    }

    private static CompoundTag vecTag(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    private static CompoundTag cleanBlockEntityPos(CompoundTag tag) {
        CompoundTag copy = tag.copy();
        copy.remove("x");
        copy.remove("y");
        copy.remove("z");
        return copy;
    }

    private static byte[] writeVarInts(int[] values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int value : values) {
            int current = value;
            do {
                int next = current & 0x7F;
                current >>>= 7;
                if (current != 0) next |= 0x80;
                out.write(next);
            } while (current != 0);
        }
        return out.toByteArray();
    }

    private static long[] packIndexes(int[] indexes, int bitsPerEntry) {
        long maxEntryValue = (1L << bitsPerEntry) - 1L;
        long[] packed = new long[(int) Math.ceil((double) indexes.length * bitsPerEntry / 64.0)];
        for (int i = 0; i < indexes.length; i++) {
            long value = indexes[i] & maxEntryValue;
            long bitIndex = (long) i * bitsPerEntry;
            int startLong = (int) (bitIndex >> 6);
            int startOffset = (int) (bitIndex & 0x3F);
            packed[startLong] = packed[startLong] & ~(maxEntryValue << startOffset) | value << startOffset;
            int endOffset = startOffset + bitsPerEntry;
            if (endOffset > 64) {
                packed[startLong + 1] = packed[startLong + 1] >>> (endOffset - 64) << (endOffset - 64)
                        | value >> (64 - startOffset);
            }
        }
        return packed;
    }

    private static String stateToString(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (state.getProperties().isEmpty()) return id.toString();
        StringBuilder builder = new StringBuilder(id.toString()).append('[');
        boolean first = true;
        for (Property<?> prop : state.getProperties()) {
            if (!first) builder.append(',');
            first = false;
            builder.append(prop.getName()).append('=').append(state.getValue(prop));
        }
        return builder.append(']').toString();
    }

    private static String schematicaBlockName(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static BlockState resolveBlockState(Object obj) {
        if (obj instanceof BlockState state) return state;
        if (obj instanceof String id && !id.isBlank()) {
            try {
                return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id))
                        .map(block -> block.defaultBlockState())
                        .orElse(Blocks.STONE.defaultBlockState());
            } catch (Exception ignored) {
                return Blocks.STONE.defaultBlockState();
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static void forEachBlock(BuildingModel model, BlockConsumer consumer) {
        for (Region region : model.getRegions()) {
            for (int y = 0; y < region.getSizeY(); y++) {
                for (int z = 0; z < region.getSizeZ(); z++) {
                    for (int x = 0; x < region.getSizeX(); x++) {
                        int wx = region.getOffsetX() + x;
                        int wy = region.getOffsetY() + y;
                        int wz = region.getOffsetZ() + z;
                        consumer.accept(region, x, y, z, wx, wy, wz, resolveBlockState(region.getBlocks().get(x, y, z)));
                    }
                }
            }
        }
    }

    private static void writeCompressed(CompoundTag root, Path file) throws IOException {
        NbtIo.writeCompressed(root, file);
    }

    private record Bounds(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        static Bounds of(BuildingModel model) {
            if (model.getRegions().isEmpty()) return new Bounds(0, 0, 0, 1, 1, 1);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (Region region : model.getRegions()) {
                minX = Math.min(minX, region.getOffsetX());
                minY = Math.min(minY, region.getOffsetY());
                minZ = Math.min(minZ, region.getOffsetZ());
                maxX = Math.max(maxX, region.getOffsetX() + region.getSizeX() - 1);
                maxY = Math.max(maxY, region.getOffsetY() + region.getSizeY() - 1);
                maxZ = Math.max(maxZ, region.getOffsetZ() + region.getSizeZ() - 1);
            }
            return new Bounds(minX, minY, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        }
    }

    @FunctionalInterface
    private interface BlockConsumer {
        void accept(Region region, int lx, int ly, int lz, int wx, int wy, int wz, BlockState state);
    }
}
