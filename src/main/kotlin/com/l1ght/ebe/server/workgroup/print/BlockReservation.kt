package com.l1ght.ebe.server.workgroup.print

import net.minecraft.core.BlockPos
import java.util.UUID

data class BlockReservation(
    val token: UUID,
    val groupId: UUID,
    val sessionId: UUID,
    val blockId: Long,
    val pos: BlockPos,
    val stateId: Int,
    val nbt: String,
    val assignedTo: UUID,
    val assignedName: String,
    val expiresAtTick: Long
)
