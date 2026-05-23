package com.l1ght.ebe.server.workgroup.print

import net.minecraft.core.BlockPos
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
            pendingQueue.addLast(target.id)
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
        val result = ArrayList<BlockReservation>(limit)
        val attempts = pendingQueue.size
        val unlimitedRange = range <= 0

        repeat(attempts) {
            if (result.size >= limit || pendingQueue.isEmpty()) return@repeat
            val id = pendingQueue.removeFirst()
            val target = targetFor(id) ?: return@repeat
            if (status(id) != PrintBlockStatus.PENDING) return@repeat
            if (!unlimitedRange && !isWithinBlockRange(target.pos, center, range)) {
                pendingQueue.addLast(id)
                return@repeat
            }

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
            setStatus(id, PrintBlockStatus.RESERVED)
            reservedCount++
            result.add(reservation)
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
                    pendingQueue.addLast(reservation.blockId)
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
                    pendingQueue.addLast(reservation.blockId)
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
        val pending = (total - placedCount - reservedCount - failedMissingMaterialCount - failedBlockedCount - cancelledCount)
            .coerceAtLeast(0)
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
    fun progressPlaced(): Int = placedCount

    @Synchronized
    fun progressTotal(): Int = targetsByIndex.size

    @Synchronized
    fun currentVersion(): Long = version

    private fun targetFor(id: Long): PrintBlockTarget? {
        val index = id.toInt()
        return targetsByIndex.getOrNull(index)?.takeIf { it.id == id }
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

    private fun isWithinBlockRange(pos: BlockPos, center: BlockPos, range: Int): Boolean {
        val dx = pos.x.toDouble() - center.x.toDouble()
        val dy = pos.y.toDouble() - center.y.toDouble()
        val dz = pos.z.toDouble() - center.z.toDouble()
        return dx * dx + dy * dy + dz * dz <= range.toDouble() * range.toDouble()
    }
}
