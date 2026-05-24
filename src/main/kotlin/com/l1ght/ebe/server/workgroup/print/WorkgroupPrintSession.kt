package com.l1ght.ebe.server.workgroup.print

import com.l1ght.ebe.server.placement.PlacementStateOrder
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import java.util.UUID

class WorkgroupPrintSession(
    val sessionId: UUID,
    val groupId: UUID,
    val ownerId: UUID,
    val ownerName: String,
    val fileName: String,
    val dimension: String,
    targets: List<PrintBlockTarget>,
    private val reservationTtlTicks: Long = 80L,
    private val missingMaterialRetryLimit: Int = 3
) {
    private val targetsByIndex: List<PrintBlockTarget> = targets.sortedBy { it.id }
    private val pendingQueue = ArrayDeque<Long>(targetsByIndex.size)
    private val pendingBySection = LinkedHashMap<Long, ArrayDeque<Long>>()
    private val sectionPhase = LinkedHashMap<Long, Int>()
    private val rangeCursors = LinkedHashMap<RangeKey, SectionCursor>()
    private val statusCodes = IntArray(targetsByIndex.size) { PrintBlockStatus.PENDING.ordinal }
    private val missingMaterialFailures = IntArray(targetsByIndex.size)
    private val reservationsByToken = LinkedHashMap<UUID, BlockReservation>()

    private var placedCount = 0
    private var reservedCount = 0
    private var failedMissingMaterialCount = 0
    private var failedBlockedCount = 0
    private var cancelledCount = 0
    private var version = 0L
    private var lastUpdatedAt = System.currentTimeMillis()

    init {
        for (target in targetsByIndex) {
            enqueuePending(target.id)
        }
    }

    @Synchronized
    fun reserve(
        playerId: UUID,
        playerName: String,
        nowTick: Long,
        maxReservations: Int,
        center: BlockPos,
        range: Int
    ): List<BlockReservation> {
        expireReservations(nowTick)
        val limit = maxReservations.coerceIn(1, 64)
        val unlimitedRange = range <= 0

        val result = if (unlimitedRange) {
            reserveFromQueue(pendingQueue, playerId, playerName, nowTick, limit)
        } else {
            reserveFromSections(playerId, playerName, nowTick, limit, center, range)
        }

        if (result.isNotEmpty()) touch()
        return result
    }

    @Synchronized
    fun verify(token: UUID, playerId: UUID): BlockReservation? {
        val reservation = reservationsByToken[token] ?: return null
        if (reservation.assignedTo != playerId) return null
        return reservation
    }

    @Synchronized
    fun complete(token: UUID, playerId: UUID, resultStatus: PrintBlockStatus): Boolean {
        val reservation = reservationsByToken[token] ?: return false
        if (reservation.assignedTo != playerId) return false
        reservationsByToken.remove(token)
        if (status(reservation.blockId) == PrintBlockStatus.RESERVED) {
            reservedCount = (reservedCount - 1).coerceAtLeast(0)
        }

        val finalStatus = when (resultStatus) {
            PrintBlockStatus.PLACED -> PrintBlockStatus.PLACED
            PrintBlockStatus.FAILED_BLOCKED -> PrintBlockStatus.FAILED_BLOCKED
            PrintBlockStatus.FAILED_MISSING_MATERIAL -> {
                val index = reservation.blockId.toInt()
                missingMaterialFailures[index]++
                if (missingMaterialFailures[index] >= missingMaterialRetryLimit) {
                    PrintBlockStatus.FAILED_MISSING_MATERIAL
                } else {
                    setStatus(reservation.blockId, PrintBlockStatus.PENDING)
                    enqueuePending(reservation.blockId)
                    touch()
                    return true
                }
            }
            PrintBlockStatus.CANCELLED -> PrintBlockStatus.CANCELLED
            else -> PrintBlockStatus.PENDING
        }

        setTerminalStatus(reservation.blockId, finalStatus)
        touch()
        return true
    }

    @Synchronized
    fun expireReservations(nowTick: Long): Boolean {
        var changed = false
        val iterator = reservationsByToken.entries.iterator()
        while (iterator.hasNext()) {
            val reservation = iterator.next().value
            if (reservation.expiresAtTick <= nowTick) {
                iterator.remove()
                if (status(reservation.blockId) == PrintBlockStatus.RESERVED) {
                    setStatus(reservation.blockId, PrintBlockStatus.PENDING)
                    reservedCount = (reservedCount - 1).coerceAtLeast(0)
                    enqueuePending(reservation.blockId)
                    changed = true
                }
            }
        }
        if (changed) touch()
        return changed
    }

    @Synchronized
    fun snapshot(): Map<String, Any> {
        val total = targetsByIndex.size
        val pending = pendingCount(total)
        return linkedMapOf(
            "active" to true,
            "sessionId" to sessionId.toString(),
            "groupId" to groupId.toString(),
            "ownerId" to ownerId.toString(),
            "ownerName" to ownerName,
            "fileName" to fileName,
            "dimension" to dimension,
            "placed" to placedCount,
            "reserved" to reservedCount,
            "pending" to pending,
            "failedMissingMaterial" to failedMissingMaterialCount,
            "failedBlocked" to failedBlockedCount,
            "cancelled" to cancelledCount,
            "total" to total,
            "complete" to (pending == 0 && reservedCount == 0),
            "updatedAt" to lastUpdatedAt,
            "version" to version
        )
    }

    @Synchronized
    fun isComplete(): Boolean = pendingCount(targetsByIndex.size) == 0 && reservedCount == 0

    @Synchronized
    fun progressPlaced(): Int = placedCount

    @Synchronized
    fun progressTotal(): Int = targetsByIndex.size

    @Synchronized
    fun currentVersion(): Long = version

    private fun targetFor(id: Long): PrintBlockTarget? {
        val index = id.toInt()
        return targetsByIndex.getOrNull(index)?.takeIf { it.id == id }
    }

    private fun reserveFromQueue(
        queue: ArrayDeque<Long>,
        playerId: UUID,
        playerName: String,
        nowTick: Long,
        limit: Int
    ): List<BlockReservation> {
        val result = ArrayList<BlockReservation>(limit)
        val scanBudget = queue.size.coerceAtMost((limit * 256).coerceAtLeast(1024))
        repeat(scanBudget) {
            if (result.size >= limit || queue.isEmpty()) return@repeat
            val id = queue.removeFirst()
            val target = targetFor(id) ?: return@repeat
            if (status(id) != PrintBlockStatus.PENDING) return@repeat
            result.add(createReservation(target, playerId, playerName, nowTick))
        }
        return result
    }

    private fun reserveFromSections(
        playerId: UUID,
        playerName: String,
        nowTick: Long,
        limit: Int,
        center: BlockPos,
        range: Int
    ): List<BlockReservation> {
        val sectionKeys = sectionKeysNear(center, range)
        if (sectionKeys.isEmpty()) return emptyList()

        val key = RangeKey(center.x shr 4, center.y shr 4, center.z shr 4, range)
        val cursor = rangeCursors.computeIfAbsent(key) { SectionCursor() }
        pruneRangeCursors()
        if (cursor.sectionIndex >= sectionKeys.size) cursor.sectionIndex = 0

        val result = ArrayList<BlockReservation>(limit)
        val scanBudget = (limit * 256).coerceAtLeast(1024)
        var scanned = 0
        var advancedSections = 0
        while (result.size < limit && scanned < scanBudget && advancedSections <= sectionKeys.size) {
            val queue = pendingBySection[sectionKeys[cursor.sectionIndex]]
            if (queue.isNullOrEmpty()) {
                advanceSection(cursor, sectionKeys.size)
                advancedSections++
                continue
            }

            val id = queue.removeFirst()
            scanned++
            val target = targetFor(id)
            if (target == null || status(id) != PrintBlockStatus.PENDING) {
                continue
            }
            if (!isWithinBlockRange(target.pos, center, range)) {
                queue.addLast(id)
                advanceSection(cursor, sectionKeys.size)
                advancedSections++
                continue
            }

            result.add(createReservation(target, playerId, playerName, nowTick))
        }

        return result
    }

    private fun createReservation(
        target: PrintBlockTarget,
        playerId: UUID,
        playerName: String,
        nowTick: Long
    ): BlockReservation {
        val token = UUID.randomUUID()
        val reservation = BlockReservation(
            token = token,
            groupId = groupId,
            sessionId = sessionId,
            blockId = target.id,
            pos = target.pos,
            stateId = target.stateId,
            nbt = target.nbt,
            assignedTo = playerId,
            assignedName = playerName,
            expiresAtTick = nowTick + reservationTtlTicks
        )
        reservationsByToken[token] = reservation
        setStatus(target.id, PrintBlockStatus.RESERVED)
        reservedCount++
        return reservation
    }

    private fun enqueuePending(id: Long) {
        val target = targetFor(id) ?: return
        pendingQueue.addLast(id)
        val key = sectionKey(target.pos)
        sectionPhase[key] = minOf(sectionPhase[key] ?: PlacementStateOrder.phaseFromStateId(target.stateId), PlacementStateOrder.phaseFromStateId(target.stateId))
        pendingBySection.computeIfAbsent(key) { ArrayDeque() }.addLast(id)
    }

    private fun status(id: Long): PrintBlockStatus {
        return PrintBlockStatus.entries[statusCodes[id.toInt()]]
    }

    private fun setStatus(id: Long, status: PrintBlockStatus) {
        statusCodes[id.toInt()] = status.ordinal
    }

    private fun setTerminalStatus(id: Long, status: PrintBlockStatus) {
        setStatus(id, status)
        when (status) {
            PrintBlockStatus.PLACED -> placedCount++
            PrintBlockStatus.FAILED_MISSING_MATERIAL -> failedMissingMaterialCount++
            PrintBlockStatus.FAILED_BLOCKED -> failedBlockedCount++
            PrintBlockStatus.CANCELLED -> cancelledCount++
            else -> {}
        }
    }

    private fun touch() {
        version++
        lastUpdatedAt = System.currentTimeMillis()
    }

    private fun pendingCount(total: Int): Int {
        return (total - placedCount - reservedCount - failedMissingMaterialCount - failedBlockedCount - cancelledCount)
            .coerceAtLeast(0)
    }

    private fun isWithinBlockRange(pos: BlockPos, center: BlockPos, range: Int): Boolean {
        val dx = pos.x.toDouble() - center.x.toDouble()
        val dy = pos.y.toDouble() - center.y.toDouble()
        val dz = pos.z.toDouble() - center.z.toDouble()
        return dx * dx + dy * dy + dz * dz <= range.toDouble() * range.toDouble()
    }

    private fun sectionKeysNear(center: BlockPos, range: Int): List<Long> {
        val radius = range.toDouble() + 24.0
        val radiusSqr = radius * radius
        return pendingBySection.keys
            .asSequence()
            .filter { pendingBySection[it]?.isNotEmpty() == true }
            .filter { distanceToSectionSqr(it, center) <= radiusSqr }
            .sortedWith(
                compareBy<Long> { sectionPhase[it] ?: PlacementStateOrder.NORMAL }
                    .thenBy { distanceToSectionSqr(it, center) }
            )
            .toList()
    }

    private fun sectionKey(pos: BlockPos): Long = SectionPos.asLong(pos.x shr 4, pos.y shr 4, pos.z shr 4)

    private fun advanceSection(cursor: SectionCursor, sectionCount: Int) {
        cursor.sectionIndex = (cursor.sectionIndex + 1) % sectionCount
    }

    private fun pruneRangeCursors() {
        while (rangeCursors.size > 64) {
            val first = rangeCursors.keys.firstOrNull() ?: return
            rangeCursors.remove(first)
        }
    }

    private fun distanceToSectionSqr(key: Long, center: BlockPos): Double {
        val cx = (SectionPos.x(key) shl 4) + 8.0
        val cy = (SectionPos.y(key) shl 4) + 8.0
        val cz = (SectionPos.z(key) shl 4) + 8.0
        val dx = cx - center.x.toDouble()
        val dy = cy - center.y.toDouble()
        val dz = cz - center.z.toDouble()
        return dx * dx + dy * dy + dz * dz
    }

    private data class RangeKey(val centerSectionX: Int, val centerSectionY: Int, val centerSectionZ: Int, val range: Int)

    private class SectionCursor {
        var sectionIndex: Int = 0
    }
}
