package com.l1ght.ebe.projection.mega;

import com.l1ght.ebe.projection.ProjectionData;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectionSparseStore {
    private static final ProjectionSparseStore EMPTY = new ProjectionSparseStore(
            List.of(),
            List.of(),
            Map.of(),
            new Long2ObjectOpenHashMap<>(),
            0,
            0
    );

    private final List<Entry> entries;
    private final List<BlockState> palette;
    private final Map<Long, List<Entry>> entriesBySection;
    private final Long2ObjectOpenHashMap<Entry> entriesByPos;
    private final int renderVersion;
    private final int meshVersion;

    private ProjectionSparseStore(
            List<Entry> entries,
            List<BlockState> palette,
            Map<Long, List<Entry>> entriesBySection,
            Long2ObjectOpenHashMap<Entry> entriesByPos,
            int renderVersion,
            int meshVersion
    ) {
        this.entries = entries;
        this.palette = palette;
        this.entriesBySection = entriesBySection;
        this.entriesByPos = entriesByPos;
        this.renderVersion = renderVersion;
        this.meshVersion = meshVersion;
    }

    public static ProjectionSparseStore empty() {
        return EMPTY;
    }

    public static ProjectionSparseStore fromProjection(ProjectionData projection) {
        if (projection == null || projection.getBlocks().isEmpty()) {
            return EMPTY;
        }

        var entries = new ArrayList<Entry>(projection.getBlocks().size());
        var palette = new ArrayList<BlockState>();
        var paletteIds = new LinkedHashMap<BlockState, Integer>();
        var bySection = new LinkedHashMap<Long, List<Entry>>();
        var byPos = new Long2ObjectOpenHashMap<Entry>(projection.getBlocks().size());

        for (var block : projection.getBlocks()) {
            if (block == null || block.state() == null || block.state().isAir()) {
                continue;
            }
            int stateId = paletteIds.computeIfAbsent(block.state(), state -> {
                palette.add(state);
                return palette.size() - 1;
            });
            long packedPos = block.pos().asLong();
            long sectionKey = ProjectionSectionKey.fromBlockPos(block.pos()).asLong();
            var entry = new Entry(packedPos, stateId, block.nbt(), sectionKey);
            entries.add(entry);
            byPos.put(packedPos, entry);
            bySection.computeIfAbsent(sectionKey, ignored -> new ArrayList<>()).add(entry);
        }

        return new ProjectionSparseStore(
                Collections.unmodifiableList(entries),
                Collections.unmodifiableList(palette),
                freezeSectionMap(bySection),
                byPos,
                projection.getRenderVersion(),
                projection.getMeshVersion()
        );
    }

    public List<Entry> entries() {
        return entries;
    }

    public List<BlockState> palette() {
        return palette;
    }

    public Map<Long, List<Entry>> entriesBySection() {
        return entriesBySection;
    }

    public Entry entryAt(BlockPos pos) {
        return entriesByPos.get(pos.asLong());
    }

    public BlockState stateById(int stateId) {
        if (stateId < 0 || stateId >= palette.size()) {
            return null;
        }
        return palette.get(stateId);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int sectionCount() {
        return entriesBySection.size();
    }

    public int renderVersion() {
        return renderVersion;
    }

    public int meshVersion() {
        return meshVersion;
    }

    private static Map<Long, List<Entry>> freezeSectionMap(Map<Long, List<Entry>> mutable) {
        var frozen = new LinkedHashMap<Long, List<Entry>>(mutable.size());
        for (var entry : mutable.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    public record Entry(long packedPos, int stateId, CompoundTag nbt, long sectionKey) {
        public BlockPos pos() {
            return BlockPos.of(packedPos);
        }

        public boolean hasNbt() {
            return nbt != null && !nbt.isEmpty();
        }
    }
}
