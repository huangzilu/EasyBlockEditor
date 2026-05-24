package com.l1ght.ebe.projection;

import com.l1ght.ebe.server.placement.PlacementStateOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectionSparseIndex {
    private static final ProjectionSparseIndex EMPTY = new ProjectionSparseIndex(
            List.of(),
            Map.of(),
            List.of(),
            true
    );

    private final List<ProjectionData.ProjectionBlock> blocks;
    private final Map<Long, List<ProjectionData.ProjectionBlock>> blocksByChunk;
    private final List<SectionSummary> sectionSummaries;
    private volatile Map<Integer, Map<Long, List<Entry>>> entriesByPhaseAndChunk = Map.of();
    private volatile List<Entry> phaseChunkOrderedEntries = List.of();
    private volatile List<ProjectionData.ProjectionBlock> phaseOrderedBlocks = List.of();
    private volatile boolean placementIndexBuilt;
    private volatile boolean phaseOrderBuilt;

    private ProjectionSparseIndex(
            List<ProjectionData.ProjectionBlock> blocks,
            Map<Long, List<ProjectionData.ProjectionBlock>> blocksByChunk,
            List<SectionSummary> sectionSummaries,
            boolean placementIndexBuilt
    ) {
        this.blocks = blocks;
        this.blocksByChunk = blocksByChunk;
        this.sectionSummaries = sectionSummaries;
        this.placementIndexBuilt = placementIndexBuilt;
        this.phaseOrderBuilt = placementIndexBuilt;
    }

    public static ProjectionSparseIndex empty() {
        return EMPTY;
    }

    public static ProjectionSparseIndex fromBlocks(List<ProjectionData.ProjectionBlock> sourceBlocks) {
        if (sourceBlocks == null || sourceBlocks.isEmpty()) {
            return EMPTY;
        }

        var blocks = new ArrayList<ProjectionData.ProjectionBlock>(sourceBlocks.size());
        var byChunk = new LinkedHashMap<Long, List<ProjectionData.ProjectionBlock>>();
        var sections = new LinkedHashMap<Long, SectionAccumulator>();

        for (var block : sourceBlocks) {
            if (block == null || block.state() == null || block.state().isAir()) continue;

            blocks.add(block);

            BlockPos pos = block.pos();
            long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            int sectionX = pos.getX() >> 4;
            int sectionY = pos.getY() >> 4;
            int sectionZ = pos.getZ() >> 4;
            long sectionKey = SectionPos.asLong(sectionX, sectionY, sectionZ);

            byChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(block);
            sections.computeIfAbsent(sectionKey, ignored -> new SectionAccumulator(sectionX, sectionY, sectionZ, pos))
                    .add(pos);
        }

        var summaries = new ArrayList<SectionSummary>(sections.size());
        for (var accumulator : sections.values()) {
            summaries.add(accumulator.toSummary());
        }

        return new ProjectionSparseIndex(
                Collections.unmodifiableList(blocks),
                freezeChunkMap(byChunk),
                Collections.unmodifiableList(summaries),
                false
        );
    }

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public List<ProjectionData.ProjectionBlock> blocks() {
        return blocks;
    }

    public Map<Long, List<ProjectionData.ProjectionBlock>> blocksByChunk() {
        return blocksByChunk;
    }

    public Map<Integer, Map<Long, List<Entry>>> entriesByPhaseAndChunk() {
        ensurePlacementIndex();
        return entriesByPhaseAndChunk;
    }

    public List<Entry> phaseChunkOrderedEntries() {
        ensurePlacementIndex();
        return phaseChunkOrderedEntries;
    }

    public List<ProjectionData.ProjectionBlock> phaseOrderedBlocks() {
        ensurePhaseOrder();
        return phaseOrderedBlocks;
    }

    public List<SectionSummary> sectionSummaries() {
        return sectionSummaries;
    }

    private void ensurePlacementIndex() {
        if (placementIndexBuilt) return;
        synchronized (this) {
            if (placementIndexBuilt) return;

            var byPhaseChunk = new LinkedHashMap<Integer, Map<Long, List<Entry>>>();
            for (var block : blocks) {
                BlockPos pos = block.pos();
                long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
                long sectionKey = SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
                int phase = PlacementStateOrder.phase(block.state());
                var entry = new Entry(block, Block.getId(block.state()), phase, chunkKey, sectionKey);
                byPhaseChunk.computeIfAbsent(phase, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(entry);
            }

            var orderedEntries = new ArrayList<Entry>(blocks.size());
            for (int phase = PlacementStateOrder.NORMAL; phase <= PlacementStateOrder.FLUID; phase++) {
                var phaseChunks = byPhaseChunk.get(phase);
                if (phaseChunks == null) continue;
                for (var entries : phaseChunks.values()) {
                    orderedEntries.addAll(entries);
                }
            }

            entriesByPhaseAndChunk = freezePhaseChunkMap(byPhaseChunk);
            phaseChunkOrderedEntries = Collections.unmodifiableList(orderedEntries);
            placementIndexBuilt = true;
        }
    }

    private void ensurePhaseOrder() {
        if (phaseOrderBuilt) return;
        synchronized (this) {
            if (phaseOrderBuilt) return;
            var ordered = new ArrayList<>(blocks);
            ordered.sort(Comparator
                    .comparingInt((ProjectionData.ProjectionBlock block) -> PlacementStateOrder.phase(block.state()))
                    .thenComparingInt(block -> block.pos().getY())
                    .thenComparingInt(block -> block.pos().getZ())
                    .thenComparingInt(block -> block.pos().getX()));
            phaseOrderedBlocks = Collections.unmodifiableList(ordered);
            phaseOrderBuilt = true;
        }
    }

    private static Map<Long, List<ProjectionData.ProjectionBlock>> freezeChunkMap(
            Map<Long, List<ProjectionData.ProjectionBlock>> mutable
    ) {
        var frozen = new LinkedHashMap<Long, List<ProjectionData.ProjectionBlock>>(mutable.size());
        for (var entry : mutable.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static Map<Integer, Map<Long, List<Entry>>> freezePhaseChunkMap(
            Map<Integer, Map<Long, List<Entry>>> mutable
    ) {
        var frozen = new LinkedHashMap<Integer, Map<Long, List<Entry>>>(mutable.size());
        for (int phase = PlacementStateOrder.NORMAL; phase <= PlacementStateOrder.FLUID; phase++) {
            var chunksForPhase = mutable.get(phase);
            if (chunksForPhase != null) {
                frozen.put(phase, freezeEntryChunkMap(chunksForPhase));
            }
        }
        for (var phaseEntry : mutable.entrySet()) {
            if (frozen.containsKey(phaseEntry.getKey())) continue;
            frozen.put(phaseEntry.getKey(), freezeEntryChunkMap(phaseEntry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static Map<Long, List<Entry>> freezeEntryChunkMap(Map<Long, List<Entry>> mutable) {
        var chunks = new LinkedHashMap<Long, List<Entry>>(mutable.size());
        for (var chunkEntry : mutable.entrySet()) {
            chunks.put(chunkEntry.getKey(), Collections.unmodifiableList(chunkEntry.getValue()));
        }
        return Collections.unmodifiableMap(chunks);
    }

    public record Entry(
            ProjectionData.ProjectionBlock block,
            int stateId,
            int phase,
            long chunkKey,
            long sectionKey
    ) {
        public BlockPos pos() {
            return block.pos();
        }

        public String nbtString() {
            return block.hasNbt() ? block.nbt().toString() : "";
        }
    }

    public record SectionSummary(
            int sectionX,
            int sectionY,
            int sectionZ,
            int count,
            BlockPos samplePos,
            BlockPos centerPos
    ) {
    }

    private static final class SectionAccumulator {
        private final int sectionX;
        private final int sectionY;
        private final int sectionZ;
        private int count;
        private BlockPos samplePos;

        private SectionAccumulator(int sectionX, int sectionY, int sectionZ, BlockPos samplePos) {
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
            this.samplePos = samplePos.immutable();
        }

        private void add(BlockPos pos) {
            count++;
            if (count == 1) {
                samplePos = pos.immutable();
            }
        }

        private SectionSummary toSummary() {
            return new SectionSummary(
                    sectionX,
                    sectionY,
                    sectionZ,
                    count,
                    samplePos,
                    new BlockPos((sectionX << 4) + 8, (sectionY << 4) + 8, (sectionZ << 4) + 8)
            );
        }
    }
}
