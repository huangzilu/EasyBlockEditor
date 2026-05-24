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
        return fromBlocks(projection.getBlocks(), projection.getRenderVersion(), projection.getMeshVersion());
    }

    public static ProjectionSparseStore fromBlocks(List<ProjectionData.ProjectionBlock> sourceBlocks, int renderVersion, int meshVersion) {
        if (sourceBlocks == null || sourceBlocks.isEmpty()) {
            return EMPTY;
        }

        var builder = new Builder(sourceBlocks.size(), renderVersion, meshVersion);

        for (var block : sourceBlocks) {
            if (block != null) {
                builder.add(block.pos(), block.state(), block.nbt());
            }
        }

        return builder.build();
    }

    public static Builder builder(int expectedSize, int renderVersion, int meshVersion) {
        return new Builder(expectedSize, renderVersion, meshVersion);
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

    public static final class Builder {
        private final List<Entry> entries;
        private final List<BlockState> palette = new ArrayList<>();
        private final Map<BlockState, Integer> paletteIds = new LinkedHashMap<>();
        private final Map<Long, List<Entry>> bySection = new LinkedHashMap<>();
        private final Long2ObjectOpenHashMap<Entry> byPos;
        private final int renderVersion;
        private final int meshVersion;

        private Builder(int expectedSize, int renderVersion, int meshVersion) {
            int capacity = Math.max(16, expectedSize);
            this.entries = new ArrayList<>(capacity);
            this.byPos = new Long2ObjectOpenHashMap<>(capacity);
            this.renderVersion = renderVersion;
            this.meshVersion = meshVersion;
        }

        public void add(BlockPos pos, BlockState state, CompoundTag nbt) {
            if (pos == null || state == null || state.isAir()) {
                return;
            }
            int stateId = paletteIds.computeIfAbsent(state, value -> {
                palette.add(value);
                return palette.size() - 1;
            });
            long packedPos = pos.asLong();
            long sectionKey = ProjectionSectionKey.fromBlockPos(pos).asLong();
            var entry = new Entry(packedPos, stateId, nbt, sectionKey);
            entries.add(entry);
            byPos.put(packedPos, entry);
            bySection.computeIfAbsent(sectionKey, ignored -> new ArrayList<>()).add(entry);
        }

        public int size() {
            return entries.size();
        }

        public ProjectionSparseStore build() {
            if (entries.isEmpty()) {
                return EMPTY;
            }
            return new ProjectionSparseStore(
                    Collections.unmodifiableList(entries),
                    Collections.unmodifiableList(palette),
                    freezeSectionMap(bySection),
                    byPos,
                    renderVersion,
                    meshVersion
            );
        }
    }
}
