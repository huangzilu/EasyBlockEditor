package com.l1ght.ebe.server.placement;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.nbt.NbtPathRules;
import com.l1ght.ebe.network.PlaceBlocksPayload;
import com.l1ght.ebe.network.PlaceProgressPayload;
import com.l1ght.ebe.server.ServerSettingsManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.TagParser;
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
        Queue<Queue<PlaceBlocksPayload.Entry>> chunks = new ArrayDeque<>();
        for (var chunkEntries : byChunk.values()) {
            chunks.add(new ArrayDeque<>(chunkEntries));
        }
        Job existing = findMergeableJob(level, player);
        if (existing != null) {
            existing.chunks.addAll(chunks);
            existing.total += entries.size();
            PacketDistributor.sendToPlayer(player, new PlaceProgressPayload(existing.placed, existing.total));
        } else {
            JOBS.add(new Job(level, player, chunks, entries.size()));
            PacketDistributor.sendToPlayer(player, new PlaceProgressPayload(0, entries.size()));
        }
    }

    private static Job findMergeableJob(ServerLevel level, ServerPlayer player) {
        for (var job : JOBS) {
            if (job.level == level && job.player.getUUID().equals(player.getUUID())) {
                return job;
            }
        }
        return null;
    }

    public static synchronized void tick() {
        int chunkBudget = ServerSettingsManager.get().placeChunksPerTick;
        int blockBudget = ServerSettingsManager.get().placeBlocksPerTick;
        int processedChunks = 0;
        int processedBlocks = 0;
        while (processedChunks < chunkBudget && processedBlocks < blockBudget && !JOBS.isEmpty()) {
            Job job = JOBS.peek();
            if (job.player.isRemoved() || job.player.connection == null) {
                JOBS.remove();
                continue;
            }
            Queue<PlaceBlocksPayload.Entry> chunkEntries = job.chunks.poll();
            if (chunkEntries == null) {
                PacketDistributor.sendToPlayer(job.player, new PlaceProgressPayload(job.placed, job.total));
                JOBS.remove();
                continue;
            }
            while (!chunkEntries.isEmpty() && processedBlocks < blockBudget) {
                var entry = chunkEntries.poll();
                processedBlocks++;
                var state = Block.stateById(entry.stateId());
                if (state != null && !state.isAir()) {
                    boolean placed = false;
                    if (job.level.setBlock(entry.pos(), state, Block.UPDATE_ALL)) {
                        job.placed++;
                        placed = true;
                    } else if (job.level.getBlockState(entry.pos()).equals(state) && blockEntityMatches(job.level, entry.pos(), entry.nbt())) {
                        job.placed++;
                        placed = true;
                    }
                    if (placed) {
                        applyBlockEntityNbt(job.level, entry.pos(), state, entry.nbt());
                    }
                }
            }
            if (!chunkEntries.isEmpty()) {
                job.chunks.add(chunkEntries);
            }
            processedChunks++;
            if (job.progressCooldown-- <= 0) {
                job.progressCooldown = 5;
                PacketDistributor.sendToPlayer(job.player, new PlaceProgressPayload(job.placed, job.total));
            }
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

    private static void applyBlockEntityNbt(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, String nbtStr) {
        if (nbtStr == null || nbtStr.isBlank()) return;
        try {
            var beNbt = TagParser.parseTag(nbtStr);
            beNbt.remove("x");
            beNbt.remove("y");
            beNbt.remove("z");
            beNbt.remove("id");
            var be = level.getBlockEntity(pos);
            if (be != null) {
                beNbt.putInt("x", pos.getX());
                beNbt.putInt("y", pos.getY());
                beNbt.putInt("z", pos.getZ());
                be.loadWithComponents(beNbt, level.registryAccess());
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
            }
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to apply block entity NBT at {}", pos, e);
        }
    }

    private static boolean blockEntityMatches(ServerLevel level, BlockPos pos, String nbtStr) {
        if (nbtStr == null || nbtStr.isBlank()) return true;
        try {
            var target = TagParser.parseTag(nbtStr);
            cleanPlacementNbt(target);
            var be = level.getBlockEntity(pos);
            if (be == null) return false;
            var existing = be.saveWithId(level.registryAccess());
            cleanPlacementNbt(existing);
            if (!ServerSettingsManager.get().strictNbtMatching) return true;
            target = NbtPathRules.filteredCopy(target, ServerSettingsManager.nbtIgnoreRules());
            existing = NbtPathRules.filteredCopy(existing, ServerSettingsManager.nbtIgnoreRules());
            return existing.equals(target);
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to compare block entity NBT at {}", pos, e);
            return false;
        }
    }

    private static void cleanPlacementNbt(net.minecraft.nbt.CompoundTag tag) {
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
        tag.remove("id");
    }

    private static class Job {
        final ServerLevel level;
        final ServerPlayer player;
        final Queue<Queue<PlaceBlocksPayload.Entry>> chunks;
        int total;
        int placed;
        int progressCooldown;

        Job(ServerLevel level, ServerPlayer player, Queue<Queue<PlaceBlocksPayload.Entry>> chunks, int total) {
            this.level = level;
            this.player = player;
            this.chunks = chunks;
            this.total = total;
        }
    }
}
