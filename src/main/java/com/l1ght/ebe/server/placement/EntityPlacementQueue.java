package com.l1ght.ebe.server.placement;

import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.network.NetworkLimits;
import com.l1ght.ebe.network.PlaceEntitiesPayload;
import com.l1ght.ebe.projection.ProjectionEntityTransforms;
import com.l1ght.ebe.server.ServerSettingsManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public final class EntityPlacementQueue {
    private static final Queue<Job> JOBS = new ArrayDeque<>();

    private EntityPlacementQueue() {
    }

    public static synchronized void enqueue(ServerLevel level, ServerPlayer player, PlaceEntitiesPayload.Purpose purpose,
                                            List<String> entityNbt) {
        if (level == null || player == null || entityNbt == null || entityNbt.isEmpty()) return;

        var parsed = new ArrayList<CompoundTag>(Math.min(entityNbt.size(), NetworkLimits.MAX_PLACE_ENTITIES_PER_PACKET));
        for (String raw : entityNbt) {
            if (raw == null || raw.isBlank() || raw.length() > NetworkLimits.MAX_ENTITY_NBT_CHARS) continue;
            try {
                CompoundTag tag = TagParser.parseTag(raw);
                if (!ProjectionEntityTransforms.isAllowedDecorativeEntityTag(tag)) continue;
                parsed.add(tag);
            } catch (Exception e) {
                EBEMod.LOGGER.warn("Failed to parse decorative entity placement NBT from {}", player.getGameProfile().getName(), e);
            }
        }
        if (parsed.isEmpty() || !withinMaxEditSize(parsed)) return;

        Map<ChunkPos, List<CompoundTag>> byChunk = new LinkedHashMap<>();
        for (CompoundTag tag : parsed) {
            var pos = ProjectionEntityTransforms.blockPos(tag);
            byChunk.computeIfAbsent(new ChunkPos(pos), ignored -> new ArrayList<>()).add(tag);
        }

        var chunks = new ArrayDeque<ArrayDeque<CompoundTag>>();
        for (var chunkEntities : byChunk.values()) {
            chunks.add(new ArrayDeque<>(chunkEntities));
        }

        Job existing = findMergeableJob(level, player, purpose);
        if (existing != null && existing.total + parsed.size() <= NetworkLimits.MAX_PLACE_ENTITIES) {
            existing.chunks.addAll(chunks);
            existing.total += parsed.size();
        } else if (parsed.size() <= NetworkLimits.MAX_PLACE_ENTITIES) {
            JOBS.add(new Job(level, player, purpose, chunks, parsed.size()));
        }
    }

    private static Job findMergeableJob(ServerLevel level, ServerPlayer player, PlaceEntitiesPayload.Purpose purpose) {
        UUID playerId = player.getUUID();
        for (var job : JOBS) {
            if (job.level == level && job.playerId.equals(playerId) && job.purpose == purpose) {
                return job;
            }
        }
        return null;
    }

    public static synchronized void tick() {
        if (JOBS.isEmpty()) return;

        long started = System.nanoTime();
        int entityBudget = Math.max(1, Math.min(16, ServerPlacementBudget.scaledInt(
                Math.max(1, ServerSettingsManager.get().placeBlocksPerTick / 16), 1)));
        int chunkBudget = ServerPlacementBudget.scaledInt(ServerSettingsManager.get().placeChunksPerTick, 1);
        int processed = 0;
        int visitedChunks = 0;

        try {
            while (processed < entityBudget && visitedChunks < chunkBudget && !JOBS.isEmpty()) {
                Job job = JOBS.poll();
                if (job == null || !job.isValid()) {
                    continue;
                }

                var chunk = job.currentChunk();
                if (chunk == null) {
                    logFinished(job);
                    continue;
                }

                while (processed < entityBudget && !chunk.isEmpty()) {
                    CompoundTag tag = chunk.poll();
                    if (place(job.level, job.purpose, tag)) {
                        job.placed++;
                    }
                    processed++;
                }

                visitedChunks++;
                if (!chunk.isEmpty()) {
                    job.chunks.add(chunk);
                }
                if (job.isDone()) {
                    logFinished(job);
                } else {
                    JOBS.add(job);
                }
            }
        } finally {
            ServerPlacementBudget.recordWork(System.nanoTime() - started);
        }
    }

    private static boolean place(ServerLevel level, PlaceEntitiesPayload.Purpose purpose, CompoundTag source) {
        if (source == null || !ProjectionEntityTransforms.isAllowedDecorativeEntityTag(source)) return false;
        var pos = ProjectionEntityTransforms.blockPos(source);
        if (!level.hasChunkAt(pos)) return false;
        CompoundTag tag = source.copy();
        tag.remove("UUID");
        tag.remove("UUIDMost");
        tag.remove("UUIDLeast");
        if (purpose == PlaceEntitiesPayload.Purpose.PRINTER) {
            stripPrinterGeneratedItems(tag);
        }
        try {
            Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> {
                loaded.setUUID(UUID.randomUUID());
                return loaded;
            });
            if (entity == null || !ProjectionEntityTransforms.isAllowedDecorativeEntity(entity)) return false;
            if (hasExistingSimilarEntity(level, entity)) return true;
            return level.addFreshEntity(entity);
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to place decorative entity {}", source.getString("id"), e);
            return false;
        }
    }

    private static void stripPrinterGeneratedItems(CompoundTag tag) {
        tag.remove("Item");
        tag.remove("item");
        tag.remove("Items");
        tag.remove("Inventory");
        tag.remove("ArmorItems");
        tag.remove("HandItems");
        tag.remove("body_armor_item");
        tag.remove("equipment");
    }

    private static boolean hasExistingSimilarEntity(ServerLevel level, Entity candidate) {
        AABB bounds = candidate.getBoundingBox().inflate(0.25D);
        for (Entity existing : level.getEntities(candidate, bounds, entity -> entity.getType() == candidate.getType())) {
            if (existing.distanceToSqr(candidate) <= 0.25D) {
                return true;
            }
        }
        return false;
    }

    private static boolean withinMaxEditSize(List<CompoundTag> entities) {
        if (entities.isEmpty()) return true;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (CompoundTag tag : entities) {
            var pos = ProjectionEntityTransforms.blockPos(tag);
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

    private static void logFinished(Job job) {
        EBEMod.LOGGER.debug("Decorative entity placement job finished for {}: {}/{} entities",
                job.player.getGameProfile().getName(), job.placed, job.total);
    }

    private static final class Job {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final UUID playerId;
        private final PlaceEntitiesPayload.Purpose purpose;
        private final ArrayDeque<ArrayDeque<CompoundTag>> chunks;
        private int total;
        private int placed;

        private Job(ServerLevel level, ServerPlayer player, PlaceEntitiesPayload.Purpose purpose,
                    ArrayDeque<ArrayDeque<CompoundTag>> chunks, int total) {
            this.level = level;
            this.player = player;
            this.playerId = player.getUUID();
            this.purpose = purpose == null ? PlaceEntitiesPayload.Purpose.PRINTER : purpose;
            this.chunks = chunks;
            this.total = total;
        }

        private boolean isValid() {
            return !player.isRemoved() && player.connection != null && player.level() == level;
        }

        private boolean isDone() {
            pruneEmptyChunks();
            return chunks.isEmpty();
        }

        private ArrayDeque<CompoundTag> currentChunk() {
            pruneEmptyChunks();
            return chunks.poll();
        }

        private void pruneEmptyChunks() {
            while (!chunks.isEmpty() && chunks.peek().isEmpty()) {
                chunks.poll();
            }
        }
    }
}
