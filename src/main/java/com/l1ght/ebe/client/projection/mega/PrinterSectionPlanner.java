package com.l1ght.ebe.client.projection.mega;

import com.l1ght.ebe.projection.ProjectionData;
import com.l1ght.ebe.projection.mega.ProjectionSparseStore;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class PrinterSectionPlanner {
    private ProjectionData projection;
    private int projectionVersion = -1;
    private ProjectionSparseStore store = ProjectionSparseStore.empty();
    private Long2ObjectOpenHashMap<ProjectionData.ProjectionBlock> blocksByPos = new Long2ObjectOpenHashMap<>();
    private List<Long> orderedSections = List.of();
    private int cursor;

    public List<ProjectionData.ProjectionBlock> nextCandidates(
            ProjectionData projection,
            BlockPos center,
            int range,
            int maxCandidates,
            int scanBudget,
            Set<BlockPos> pending,
            Predicate<ProjectionData.ProjectionBlock> eligible
    ) {
        rebuildIfNeeded(projection, center);
        if (store.isEmpty() || maxCandidates <= 0 || scanBudget <= 0) {
            return List.of();
        }

        var result = new ArrayList<ProjectionData.ProjectionBlock>(maxCandidates);
        int scanned = 0;
        int visitedSections = 0;
        while (scanned < scanBudget && result.size() < maxCandidates && visitedSections < orderedSections.size()) {
            long sectionKey = orderedSections.get(cursor);
            cursor = (cursor + 1) % orderedSections.size();
            visitedSections++;
            var entries = store.entriesBySection().get(sectionKey);
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            for (var entry : entries) {
                if (scanned >= scanBudget || result.size() >= maxCandidates) {
                    break;
                }
                scanned++;
                var pos = entry.pos();
                if (range > 0 && !withinRange(pos, center, range)) {
                    continue;
                }
                if (pending != null && pending.contains(pos)) {
                    continue;
                }
                ProjectionData.ProjectionBlock block = blocksByPos.get(pos.asLong());
                if (block == null || eligible != null && !eligible.test(block)) {
                    continue;
                }
                result.add(block);
            }
        }
        return result;
    }

    public void reset() {
        projection = null;
        projectionVersion = -1;
        store = ProjectionSparseStore.empty();
        blocksByPos.clear();
        orderedSections = List.of();
        cursor = 0;
    }

    private void rebuildIfNeeded(ProjectionData projection, BlockPos center) {
        if (this.projection == projection && projectionVersion == projection.getRenderVersion()) {
            return;
        }
        this.projection = projection;
        this.projectionVersion = projection == null ? -1 : projection.getRenderVersion();
        this.store = ProjectionSparseStore.fromProjection(projection);
        this.blocksByPos = new Long2ObjectOpenHashMap<>();
        if (projection != null) {
            for (var block : projection.getSparseIndex().blocks()) {
                blocksByPos.put(block.pos().asLong(), block);
            }
        }
        var sections = new ArrayList<>(store.entriesBySection().keySet());
        if (center != null) {
            sections.sort(Comparator.comparingDouble(section -> distanceToSectionSqr(section, center)));
        }
        this.orderedSections = List.copyOf(sections);
        this.cursor = 0;
    }

    private static boolean withinRange(BlockPos pos, BlockPos center, int range) {
        double dx = pos.getX() - center.getX();
        double dy = pos.getY() - center.getY();
        double dz = pos.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz <= (double) range * range;
    }

    private static double distanceToSectionSqr(long sectionKey, BlockPos center) {
        var key = com.l1ght.ebe.projection.mega.ProjectionSectionKey.fromLong(sectionKey);
        return key.distanceToSqr(center.getX(), center.getY(), center.getZ());
    }
}
