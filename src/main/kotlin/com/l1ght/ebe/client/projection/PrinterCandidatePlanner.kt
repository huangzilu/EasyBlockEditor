package com.l1ght.ebe.client.projection

import com.l1ght.ebe.projection.ProjectionData
import com.l1ght.ebe.server.placement.PlacementStateOrder
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import java.util.LinkedHashMap
import java.util.function.Predicate

class PrinterCandidatePlanner {
    private val sectionBuckets = LinkedHashMap<Long, MutableList<ProjectionData.ProjectionBlock>>()
    private val sectionPhase = LinkedHashMap<Long, Int>()
    private val allTargets = ArrayList<ProjectionData.ProjectionBlock>()
    private val finiteStates = LinkedHashMap<SearchKey, Cursor>()
    private val finiteSectionCache = LinkedHashMap<SearchKey, List<Long>>()
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
        sectionBuckets.clear()
        sectionPhase.clear()
        allTargets.clear()
        finiteStates.clear()
        finiteSectionCache.clear()
        unlimitedCursor = 0
    }

    private fun rebuildIfNeeded(projection: ProjectionData) {
        if (projectionIdentity === projection && projectionVersion == projection.renderVersion) {
            return
        }

        projectionIdentity = projection
        projectionVersion = projection.renderVersion
        sectionBuckets.clear()
        sectionPhase.clear()
        allTargets.clear()
        finiteStates.clear()
        finiteSectionCache.clear()
        unlimitedCursor = 0

        val ordered = projection.sparseIndex.blocks().sortedWith(
            compareBy<ProjectionData.ProjectionBlock> { PlacementStateOrder.phase(it.state()) }
                .thenBy { it.pos().y }
                .thenBy { it.pos().z }
                .thenBy { it.pos().x }
        )
        for (block in ordered) {
            val key = sectionKey(block.pos())
            val phase = PlacementStateOrder.phase(block.state())
            allTargets.add(block)
            sectionBuckets.computeIfAbsent(key) { ArrayList() }.add(block)
            sectionPhase[key] = minOf(sectionPhase[key] ?: phase, phase)
        }
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
        val key = SearchKey(center.x shr 4, center.y shr 4, center.z shr 4, range)
        val sectionKeys = sectionKeysNear(key, center, range)
        if (sectionKeys.isEmpty()) return emptyList()

        val cursor = finiteStates.computeIfAbsent(key) { Cursor() }
        pruneFiniteStates()
        if (cursor.sectionIndex >= sectionKeys.size) {
            cursor.sectionIndex = 0
            cursor.blockIndex = 0
        }

        val result = ArrayList<ProjectionData.ProjectionBlock>(maxCandidates)
        var scanned = 0
        var advancedSections = 0
        while (scanned < scanBudget && result.size < maxCandidates && advancedSections <= sectionKeys.size) {
            val sectionKey = sectionKeys[cursor.sectionIndex]
            val blocks = sectionBuckets[sectionKey]
            if (blocks.isNullOrEmpty()) {
                advanceSection(cursor, sectionKeys.size)
                advancedSections++
                continue
            }

            if (cursor.blockIndex >= blocks.size) {
                cursor.blockIndex = 0
                advanceSection(cursor, sectionKeys.size)
                advancedSections++
                continue
            }

            val block = blocks[cursor.blockIndex++]
            scanned++
            if (cursor.blockIndex >= blocks.size) {
                cursor.blockIndex = 0
                advanceSection(cursor, sectionKeys.size)
                advancedSections++
            }

            if (pending.contains(block.pos())) continue
            if (!isWithinBlockRange(block.pos(), center, range)) continue
            if (!eligible.test(block)) continue
            result.add(block)
        }

        return result
    }

    private fun sectionKeysNear(key: SearchKey, center: BlockPos, range: Int): List<Long> {
        finiteSectionCache[key]?.let { return it }
        pruneSectionCache()

        val radius = range.toDouble() + 24.0
        val radiusSqr = radius * radius
        val keys = sectionBuckets.keys
            .asSequence()
            .filter { distanceToSectionSqr(it, center) <= radiusSqr }
            .sortedWith(
                compareBy<Long> { sectionPhase[it] ?: PlacementStateOrder.NORMAL }
                    .thenBy { distanceToSectionSqr(it, center) }
            )
            .toList()

        finiteSectionCache[key] = keys
        return keys
    }

    private fun advanceSection(cursor: Cursor, sectionCount: Int) {
        cursor.sectionIndex = (cursor.sectionIndex + 1) % sectionCount
    }

    private fun pruneFiniteStates() {
        while (finiteStates.size > 64) {
            val first = finiteStates.keys.firstOrNull() ?: return
            finiteStates.remove(first)
        }
    }

    private fun pruneSectionCache() {
        while (finiteSectionCache.size > 64) {
            val first = finiteSectionCache.keys.firstOrNull() ?: return
            finiteSectionCache.remove(first)
        }
    }

    private fun isWithinBlockRange(pos: BlockPos, center: BlockPos, range: Int): Boolean {
        val dx = pos.x.toDouble() - center.x.toDouble()
        val dy = pos.y.toDouble() - center.y.toDouble()
        val dz = pos.z.toDouble() - center.z.toDouble()
        return dx * dx + dy * dy + dz * dz <= range.toDouble() * range.toDouble()
    }

    private fun sectionKey(pos: BlockPos): Long = SectionPos.asLong(pos.x shr 4, pos.y shr 4, pos.z shr 4)

    private fun distanceToSectionSqr(key: Long, center: BlockPos): Double {
        val cx = (SectionPos.x(key) shl 4) + 8.0
        val cy = (SectionPos.y(key) shl 4) + 8.0
        val cz = (SectionPos.z(key) shl 4) + 8.0
        val dx = cx - center.x.toDouble()
        val dy = cy - center.y.toDouble()
        val dz = cz - center.z.toDouble()
        return dx * dx + dy * dy + dz * dz
    }

    private fun floorMod(value: Int, divisor: Int): Int {
        val mod = value % divisor
        return if (mod < 0) mod + divisor else mod
    }

    private data class SearchKey(val centerSectionX: Int, val centerSectionY: Int, val centerSectionZ: Int, val range: Int)

    private class Cursor {
        var sectionIndex: Int = 0
        var blockIndex: Int = 0
    }
}
