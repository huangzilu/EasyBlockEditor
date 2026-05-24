package com.l1ght.ebe.server.workgroup.print

import net.minecraft.core.BlockPos
import java.util.UUID

object WorkgroupPrintSessionManager {
    private val activeByGroup = LinkedHashMap<UUID, WorkgroupPrintSession>()
    private var lastBroadcastVersion = LinkedHashMap<UUID, Long>()

    @JvmStatic
    @Synchronized
    fun startSession(
        groupId: UUID,
        ownerId: UUID,
        ownerName: String,
        fileName: String,
        dimension: String,
        targets: List<PrintBlockTarget>
    ): UUID {
        val sessionId = UUID.randomUUID()
        if (targets.isEmpty()) {
            activeByGroup.remove(groupId)
            lastBroadcastVersion.remove(groupId)
            return sessionId
        }
        activeByGroup[groupId] = WorkgroupPrintSession(
            sessionId = sessionId,
            groupId = groupId,
            ownerId = ownerId,
            ownerName = ownerName,
            fileName = fileName,
            dimension = dimension,
            targets = targets
        )
        lastBroadcastVersion[groupId] = -1L
        return sessionId
    }

    @JvmStatic
    @Synchronized
    fun cancelGroup(groupId: UUID): Boolean {
        val removed = activeByGroup.remove(groupId) != null
        lastBroadcastVersion.remove(groupId)
        return removed
    }

    @JvmStatic
    @Synchronized
    fun reserveForPlayer(
        groupId: UUID,
        playerId: UUID,
        playerName: String,
        dimension: String,
        nowTick: Long,
        maxReservations: Int,
        center: BlockPos,
        range: Int
    ): List<BlockReservation> {
        val session = activeByGroup[groupId] ?: return emptyList()
        if (session.dimension != dimension) return emptyList()
        return session.reserve(playerId, playerName, nowTick, maxReservations, center, range)
    }

    @JvmStatic
    @Synchronized
    fun verifyReservation(groupId: UUID, sessionId: UUID, token: UUID, playerId: UUID, dimension: String): BlockReservation? {
        val session = activeByGroup[groupId] ?: return null
        if (session.sessionId != sessionId) return null
        if (session.dimension != dimension) return null
        return session.verify(token, playerId)
    }

    @JvmStatic
    @Synchronized
    fun completeReservation(
        groupId: UUID,
        sessionId: UUID,
        token: UUID,
        playerId: UUID,
        status: PrintBlockStatus
    ): Boolean {
        val session = activeByGroup[groupId] ?: return false
        if (session.sessionId != sessionId) return false
        val changed = session.complete(token, playerId, status)
        if (changed && session.isComplete()) {
            activeByGroup.remove(groupId)
            lastBroadcastVersion.remove(groupId)
        }
        return changed
    }

    @JvmStatic
    @Synchronized
    fun tick(nowTick: Long): Boolean {
        var changed = false
        for ((_, session) in activeByGroup) {
            changed = session.expireReservations(nowTick) || changed
        }
        return changed
    }

    @JvmStatic
    @Synchronized
    fun snapshotForGroup(groupId: UUID): Map<String, Any>? {
        return activeByGroup[groupId]?.snapshot()
    }

    @JvmStatic
    @Synchronized
    fun allSnapshots(): List<Map<String, Any>> {
        return activeByGroup.values.map { it.snapshot() }
    }

    @JvmStatic
    @Synchronized
    fun hasActiveSession(groupId: UUID): Boolean {
        return activeByGroup.containsKey(groupId)
    }

    @JvmStatic
    @Synchronized
    fun progressForGroup(groupId: UUID): IntArray {
        val session = activeByGroup[groupId] ?: return intArrayOf(0, 0)
        return intArrayOf(session.progressPlaced(), session.progressTotal())
    }

    @JvmStatic
    @Synchronized
    fun consumeChangedGroupsForBroadcast(): List<UUID> {
        val changed = ArrayList<UUID>()
        for ((groupId, session) in activeByGroup) {
            val version = session.currentVersion()
            if (lastBroadcastVersion[groupId] != version) {
                lastBroadcastVersion[groupId] = version
                changed.add(groupId)
            }
        }
        val removed = lastBroadcastVersion.keys.filter { !activeByGroup.containsKey(it) }
        if (removed.isNotEmpty()) {
            for (groupId in removed) {
                lastBroadcastVersion.remove(groupId)
                changed.add(groupId)
            }
        }
        return changed
    }
}
