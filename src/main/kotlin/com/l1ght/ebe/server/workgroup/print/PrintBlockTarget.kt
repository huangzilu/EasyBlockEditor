package com.l1ght.ebe.server.workgroup.print

import net.minecraft.core.BlockPos

data class PrintBlockTarget(
    val id: Long,
    val pos: BlockPos,
    val stateId: Int,
    val nbt: String
)
