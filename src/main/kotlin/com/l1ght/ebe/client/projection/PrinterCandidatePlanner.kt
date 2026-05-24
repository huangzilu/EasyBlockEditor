package com.l1ght.ebe.client.projection

import com.l1ght.ebe.projection.ProjectionData
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.function.Predicate

class PrinterCandidatePlanner {
    private val buckets = LinkedHashMap<Long, MutableList<ProjectionData.ProjectionBlock>>()
    private val allTargets = ArrayList<ProjectionData.ProjectionBlock>()
    private val finiteStates = LinkedHashMap<SearchKey, Cursor>()
    private var projectionIdentity: ProjectionData? = null
    private var projectionVersion: Int = -1
    private var unlimitedCursor: Int = 0

    fun nextCandidates(
        projection: ProjectionData,
        center: BlockPos,
        range: Int,
        maxCandidates: Int,
        scanBudget: Int,
        pending: Set<BlockPos>,
        eligible: Predicate<ProjectionData.ProjectionBlock>
    ): List<ProjectionData.ProjectionBlock> {
        rebuildIfNeeded(projection)
        if (allTargets.isEmpty() || maxCandidates <= 0 || scanBudget <= 0) {
            return emptyList()
        }
        return if (range <= 0) {
            nextUnlimited(maxCandidates, scanBudget, pending, eligible)
        } else {
            nextFinite(center, range, maxCandidates, scanBudget, pending, eligible)
        }
    }

    fun reset() {
        projectionIdentity = null
        projectionVersion = -1
        buckets.clear()
        allTargets.clear()
        finiteStates.clear()
        unlimitedCursor = 0
    }

    private fun rebuildIfNeeded(projection: ProjectionData) {
        if (projectionIdentity === projection && projectionVersion == projection.renderVersion) {
            return
        }

        projectionIdentity = projection
        projectionVersion = projection.renderVersion
        buckets.clear()
        allTargets.clear()
        finiteStates.clear()
        unlimitedCursor = 0

        val index = projection.sparseIndex
        for ((key, chunkBlocks) in index.blocksByChunk()) {
            buckets[key] = ArrayList(chunkBlocks)
        }
        allTargets.addAll(index.phaseOrderedBlocks())
    }

    private fun nextUnlimited(
        maxCandidates: Int,
        scanBudget: Int,
        pending: Set<BlockPos>,
        eligible: Predicate<ProjectionData.ProjectionBlock>
    ): List<ProjectionData.ProjectionBlock> {
        val result = ArrayList<ProjectionData.ProjectionBlock>(maxCandidates)
        var scanned = 0
        var index = floorMod(unlimitedCursor, allTargets.size)

        while (scanned < scanBudget && result.size < maxCandidates && allTargets.isNotEmpty()) {
            val block = allTargets[index]
            scanned++
            index = (index + 1) % allTargets.size
            if (!pending.contains(block.pos()) && eligible.test(block)) {
                result.add(block)
            }
        }

        unlimitedCursor = index
        return result
    }

    private fun nextFinite(
        center: BlockPos,
        range: Int,
        maxCandidates: Int,
        scanBudget: Int,
        pending: Set<BlockPos>,
        eligible: Predicate<ProjectionData.ProjectionBlock>
    ): List<ProjectionData.ProjectionBlock> {
        val chunkKeys = chunkKeysNear(center, range)
        if (chunkKeys.isEmpty()) return emptyList()

        val key = SearchKey(center.x shr 4, center.z shr 4, range)
        val cursor = finiteStates.computeIfAbsent(key) { Cursor() }
        pruneFiniteStates()
        if (cursor.chunkIndex >= chunkKeys.size) {
            cursor.chunkIndex = 0
            cursor.blockIndex = 0
        }

        val result = ArrayList<ProjectionData.ProjectionBlock>(maxCandidates)
        var scanned = 0
        var advancedChunks = 0
        while (scanned < scanBudget && result.size < maxCandidates && advancedChunks <= chunkKeys.size) {
            val chunkKey = chunkKeys[cursor.chunkIndex]
            val blocks = buckets[chunkKey]
            if (blocks.isNullOrEmpty()) {
                advanceChunk(cursor, chunkKeys.size)
                advancedChunks++
                continue
            }

            if (cursor.blockIndex >= blocks.size) {
                cursor.blockIndex = 0
                advanceChunk(cursor, chunkKeys.size)
                advancedChunks++
                continue
            }

            val block = blocks[cursor.blockIndex++]
            scanned++
            if (cursor.blockIndex >= blocks.size) {
                cursor.blockIndex = 0
                advanceChunk(cursor, chunkKeys.size)
                advancedChunks++
            }

            if (pending.contains(block.pos())) continue
            if (!isWithinBlockRange(block.pos(), center, range)) continue
            if (!eligible.test(block)) continue
            result.add(block)
        }

        return result
    }

    private fun chunkKeysNear(center: BlockPos, range: Int): List<Long> {
        val radius = (range shr 4).coerceAtLeast(1) + 1
        val centerChunkX = center.x shr 4
        val centerChunkZ = center.z shr 4
        val keys = LinkedHashSet<Long>()

        keys.add(ChunkPos.asLong(centerChunkX, centerChunkZ))
        for (ring in 1..radius) {
            for (dx in -ring..ring) {
                keys.add(ChunkPos.asLong(centerChunkX + dx, centerChunkZ - ring))
                keys.add(ChunkPos.asLong(centerChunkX + dx, centerChunkZ + ring))
            }
            for (dz in (-ring + 1)..(ring - 1)) {
                keys.add(ChunkPos.asLong(centerChunkX - ring, centerChunkZ + dz))
                keys.add(ChunkPos.asLong(centerChunkX + ring, centerChunkZ + dz))
            }
        }

        return keys.filter { buckets.containsKey(it) }
    }

    private fun advanceChunk(cursor: Cursor, chunkCount: Int) {
        cursor.chunkIndex = (cursor.chunkIndex + 1) % chunkCount
    }

    private fun pruneFiniteStates() {
        while (finiteStates.size > 64) {
            val first = finiteStates.keys.firstOrNull() ?: return
            finiteStates.remove(first)
        }
    }

    private fun isWithinBlockRange(pos: BlockPos, center: BlockPos, range: Int): Boolean {
        val dx = pos.x.toDouble() - center.x.toDouble()
        val dy = pos.y.toDouble() - center.y.toDouble()
        val dz = pos.z.toDouble() - center.z.toDouble()
        return dx * dx + dy * dy + dz * dz <= range.toDouble() * range.toDouble()
    }

    private fun floorMod(value: Int, divisor: Int): Int {
        val mod = value % divisor
        return if (mod < 0) mod + divisor else mod
    }

    private data class SearchKey(val centerChunkX: Int, val centerChunkZ: Int, val range: Int)

    private class Cursor {
        var chunkIndex: Int = 0
        var blockIndex: Int = 0
    }
}
