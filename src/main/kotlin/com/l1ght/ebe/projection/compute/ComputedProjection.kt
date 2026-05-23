package com.l1ght.ebe.projection.compute

import com.l1ght.ebe.data.BuildingModel
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState

data class ComputedProjection(
    val model: BuildingModel,
    val origin: BlockPos,
    val centerPoint: BlockPos,
    val rotation: Rotation,
    val mirror: Mirror,
    val blocks: List<BlockEntry>,
    val viewportBatches: List<ViewportBatch>,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
    val totalVolume: Int,
    val cameraFit: CameraFit
) {
    fun blockCount(): Int = blocks.size
    fun isEmpty(): Boolean = blocks.isEmpty()
}

data class BlockEntry(
    val pos: BlockPos,
    val state: BlockState,
    val nbt: CompoundTag?
) {
    fun hasNbt(): Boolean = nbt != null && !nbt.isEmpty
    fun nbtKey(): String = if (hasNbt()) nbt.toString() else ""
}

data class ViewportBatch(
    val entries: List<ViewportEntry>
) {
    fun size(): Int = entries.size
}

data class ViewportEntry(
    val pos: BlockPos,
    val state: BlockState,
    val source: Any
)

data class CameraFit(
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val zoom: Float,
    val yaw: Float = -135f,
    val pitch: Float = 25f
)
