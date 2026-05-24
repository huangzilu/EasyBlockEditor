package com.l1ght.ebe.server.placement;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.network.PrinterPlaceBatchPayload;
import com.l1ght.ebe.server.ServerSettingsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;

public final class PrinterPlacementQueue {
    private static final Queue<Job> JOBS = new ArrayDeque<>();

    private PrinterPlacementQueue() {
    }

    public static synchronized void enqueue(ServerLevel level, ServerPlayer player,
                                            List<PrinterPlaceBatchPayload.Entry> entries,
                                            BlockPos materialSourcePos,
                                            boolean requireHeldItem,
                                            int materialSourceRange) {
        if (level == null || player == null || entries == null || entries.isEmpty()) return;

        var chunks = chunkEntries(entries);
        if (chunks.isEmpty()) return;

        Job existing = findMergeableJob(level, player, materialSourcePos, requireHeldItem, materialSourceRange);
        if (existing != null) {
            existing.chunks.addAll(chunks);
            existing.total += entries.size();
            return;
        }

        JOBS.add(new Job(level, player, chunks, entries.size(), materialSourcePos, requireHeldItem, materialSourceRange));
    }

    private static ArrayDeque<ArrayDeque<PrinterPlaceBatchPayload.Entry>> chunkEntries(
            List<PrinterPlaceBatchPayload.Entry> entries
    ) {
        Map<ChunkPos, List<PrinterPlaceBatchPayload.Entry>> byChunk = new LinkedHashMap<>();
        for (var entry : entries) {
            if (entry == null) continue;
            byChunk.computeIfAbsent(new ChunkPos(entry.pos()), ignored -> new ArrayList<>()).add(entry);
        }

        var chunks = new ArrayDeque<ArrayDeque<PrinterPlaceBatchPayload.Entry>>();
        for (var chunkEntries : byChunk.values()) {
            chunks.add(new ArrayDeque<>(chunkEntries));
        }
        return chunks;
    }

    private static Job findMergeableJob(ServerLevel level, ServerPlayer player, BlockPos materialSourcePos,
                                        boolean requireHeldItem, int materialSourceRange) {
        UUID playerId = player.getUUID();
        for (var job : JOBS) {
            if (job.level == level
                    && job.playerId.equals(playerId)
                    && Objects.equals(job.materialSourcePos, materialSourcePos)
                    && job.requireHeldItem == requireHeldItem
                    && job.materialSourceRange == materialSourceRange) {
                return job;
            }
        }
        return null;
    }

    public static synchronized void tick() {
        if (JOBS.isEmpty()) return;

        int perPlayerBudget = Math.max(1, ServerSettingsManager.get().printerBlocksPerTick);
        int activeJobs = JOBS.size();
        int globalBudget = Math.max(perPlayerBudget, perPlayerBudget * Math.min(activeJobs, 4));
        int processed = 0;
        int visitedJobs = 0;

        while (processed < globalBudget && visitedJobs < activeJobs && !JOBS.isEmpty()) {
            Job job = JOBS.poll();
            visitedJobs++;

            if (job == null || !job.isValid()) {
                continue;
            }

            int jobAttempts = 0;
            while (processed < globalBudget && jobAttempts < perPlayerBudget && !job.isDone()) {
                var currentChunk = job.currentChunk();
                if (currentChunk == null) break;

                var entry = currentChunk.peek();
                if (entry == null) {
                    job.discardCurrentChunk();
                    continue;
                }

                var result = PrinterPlacementService.place(
                        job.player,
                        job.level,
                        entry.pos(),
                        entry.stateId(),
                        entry.nbtStr(),
                        job.materialSourcePos,
                        job.requireHeldItem,
                        job.materialSourceRange
                );

                if (result == PrinterPlacementService.Result.RATE_LIMITED) {
                    break;
                }

                currentChunk.poll();
                job.finished++;
                jobAttempts++;
                processed++;
            }

            if (!job.isDone()) {
                JOBS.add(job);
            } else {
                EBEMod.LOGGER.debug("Printer placement job finished for {}: {}/{} entries",
                        job.player.getGameProfile().getName(), job.finished, job.total);
            }

        }
    }

    private static final class Job {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final UUID playerId;
        private final ArrayDeque<ArrayDeque<PrinterPlaceBatchPayload.Entry>> chunks;
        private final BlockPos materialSourcePos;
        private final boolean requireHeldItem;
        private final int materialSourceRange;
        private int total;
        private int finished;

        private Job(ServerLevel level, ServerPlayer player,
                    ArrayDeque<ArrayDeque<PrinterPlaceBatchPayload.Entry>> chunks,
                    int total,
                    BlockPos materialSourcePos,
                    boolean requireHeldItem,
                    int materialSourceRange) {
            this.level = level;
            this.player = player;
            this.playerId = player.getUUID();
            this.chunks = chunks;
            this.total = total;
            this.materialSourcePos = materialSourcePos == null ? null : materialSourcePos.immutable();
            this.requireHeldItem = requireHeldItem;
            this.materialSourceRange = Math.max(0, materialSourceRange);
        }

        private boolean isValid() {
            return !player.isRemoved() && player.connection != null && player.level() == level;
        }

        private boolean isDone() {
            pruneEmptyChunks();
            return chunks.isEmpty();
        }

        private ArrayDeque<PrinterPlaceBatchPayload.Entry> currentChunk() {
            pruneEmptyChunks();
            return chunks.peek();
        }

        private void discardCurrentChunk() {
            chunks.poll();
        }

        private void pruneEmptyChunks() {
            while (!chunks.isEmpty() && chunks.peek().isEmpty()) {
                chunks.poll();
            }
        }
    }
}
