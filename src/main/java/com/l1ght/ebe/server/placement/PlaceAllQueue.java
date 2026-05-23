package com.l1ght.ebe.server.placement;

import com.l1ght.ebe.network.PlaceBlocksPayload;
import com.l1ght.ebe.network.PlaceProgressPayload;
import com.l1ght.ebe.server.ServerSettingsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class PlaceAllQueue {
    private static final Queue<Job> JOBS = new ArrayDeque<>();

    public static synchronized void enqueue(ServerLevel level, ServerPlayer player, List<PlaceBlocksPayload.Entry> entries) {
        Map<ChunkPos, List<PlaceBlocksPayload.Entry>> byChunk = new LinkedHashMap<>();
        for (var entry : entries) {
            byChunk.computeIfAbsent(new ChunkPos(entry.pos()), ignored -> new ArrayList<>()).add(entry);
        }
        JOBS.add(new Job(level, player, new ArrayDeque<>(byChunk.values()), entries.size()));
    }

    public static synchronized void tick() {
        int chunkBudget = ServerSettingsManager.get().placeChunksPerTick;
        int processed = 0;
        while (processed < chunkBudget && !JOBS.isEmpty()) {
            Job job = JOBS.peek();
            if (job.player.isRemoved() || job.player.connection == null) {
                JOBS.remove();
                continue;
            }
            List<PlaceBlocksPayload.Entry> chunkEntries = job.chunks.poll();
            if (chunkEntries == null) {
                PacketDistributor.sendToPlayer(job.player, new PlaceProgressPayload(job.placed, job.total));
                JOBS.remove();
                continue;
            }
            for (var entry : chunkEntries) {
                var state = Block.stateById(entry.stateId());
                if (state != null && !state.isAir()) {
                    if (job.level.setBlock(entry.pos(), state, Block.UPDATE_ALL)) {
                        job.placed++;
                    } else if (job.level.getBlockState(entry.pos()).getBlock() == state.getBlock()) {
                        job.placed++;
                    }
                }
            }
            processed++;
            if (job.chunks.isEmpty()) {
                PacketDistributor.sendToPlayer(job.player, new PlaceProgressPayload(job.placed, job.total));
                JOBS.remove();
            }
        }
    }

    public static boolean withinMaxEditSize(List<PlaceBlocksPayload.Entry> entries) {
        if (entries.isEmpty()) return true;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var entry : entries) {
            BlockPos pos = entry.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int limit = ServerSettingsManager.get().maxEditSize;
        return maxX - minX + 1 <= limit && maxY - minY + 1 <= limit && maxZ - minZ + 1 <= limit;
    }

    private static class Job {
        final ServerLevel level;
        final ServerPlayer player;
        final Queue<List<PlaceBlocksPayload.Entry>> chunks;
        final int total;
        int placed;

        Job(ServerLevel level, ServerPlayer player, Queue<List<PlaceBlocksPayload.Entry>> chunks, int total) {
            this.level = level;
            this.player = player;
            this.chunks = chunks;
            this.total = total;
        }
    }
}
