package com.l1ght.ebe.projection.compute

import com.l1ght.ebe.async.EbeScopes
import com.l1ght.ebe.data.BuildingModel
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ProjectionComputePlanner {
    private const val VIEWPORT_BATCH_SIZE = 2048
    private const val CACHE_LIMIT = 8

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ComputedProjection>(CACHE_LIMIT, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ComputedProjection>): Boolean {
                return size > CACHE_LIMIT
            }
        }
    )

    @JvmStatic
    fun computeAsync(
        cacheKey: String,
        model: BuildingModel,
        origin: BlockPos,
        rotation: Rotation,
        mirror: Mirror,
        centerPoint: BlockPos,
        includeViewportPlan: Boolean
    ): CompletableFuture<ComputedProjection> {
        val key = buildKey(cacheKey, origin, rotation, mirror, centerPoint, includeViewportPlan)
        cache[key]?.let { return CompletableFuture.completedFuture(it) }

        return EbeScopes.submitCompute {
            val computed = compute(model, origin, rotation, mirror, centerPoint, includeViewportPlan)
            cache[key] = computed
            computed
        }
    }

    @JvmStatic
    fun compute(
        model: BuildingModel,
        origin: BlockPos,
        rotation: Rotation,
        mirror: Mirror,
        centerPoint: BlockPos,
        includeViewportPlan: Boolean
    ): ComputedProjection {
        val blocks = ArrayList<BlockEntry>()
        val viewportEntries = ArrayList<ViewportEntry>()
        var totalVolume = 0L
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        val localCenter = centerPoint.subtract(origin)
        for (region in model.regions) {
            val container = region.blocks
            val ox = region.offsetX
            val oy = region.offsetY
            val oz = region.offsetZ
            val sx = region.sizeX
            val sy = region.sizeY
            val sz = region.sizeZ
            totalVolume += sx.toLong() * sy.toLong() * sz.toLong()

            for (y in 0 until sy) {
                for (z in 0 until sz) {
                    for (x in 0 until sx) {
                        val source = container.get(x, y, z)
                        val rawState = resolveBlockState(source)
                        if (rawState.isAir) continue

                        val localPos = BlockPos(ox + x, oy + y, oz + z)
                        var worldPos = origin.offset(localPos)
                        var state = rawState
                        if (rotation != Rotation.NONE || mirror != Mirror.NONE) {
                            val relToCenter = localPos.subtract(localCenter)
                            val rotated = rotatePos(relToCenter, rotation)
                            val mirrored = mirrorPos(rotated, mirror)
                            worldPos = centerPoint.offset(mirrored)
                            state = transformState(state, rotation, mirror)
                        }

                        val immutablePos = worldPos.immutable()
                        val nbt: CompoundTag? = region.getBlockEntity(x, y, z)?.copy()
                        blocks.add(BlockEntry(immutablePos, state, nbt))
                        if (includeViewportPlan && model.isLayerVisibleAt(region, ox + x, oy + y, oz + z)) {
                            viewportEntries.add(ViewportEntry(localPos.immutable(), rawState, source))
                        }

                        minX = min(minX, immutablePos.x)
                        minY = min(minY, immutablePos.y)
                        minZ = min(minZ, immutablePos.z)
                        maxX = max(maxX, immutablePos.x)
                        maxY = max(maxY, immutablePos.y)
                        maxZ = max(maxZ, immutablePos.z)
                    }
                }
            }
        }

        if (blocks.isEmpty()) {
            minX = origin.x
            minY = origin.y
            minZ = origin.z
            maxX = origin.x
            maxY = origin.y
            maxZ = origin.z
        }

        val fit = cameraFit(minX, minY, minZ, maxX, maxY, maxZ)
        val batches = if (includeViewportPlan) {
            viewportEntries
                .sortedWith(
                    compareBy<ViewportEntry> { viewportPriorityDistance(it, fit) }
                        .thenBy { it.pos.y }
                        .thenBy { it.pos.z shr 4 }
                        .thenBy { it.pos.x shr 4 }
                )
                .chunked(VIEWPORT_BATCH_SIZE)
                .map { ViewportBatch(it) }
        } else {
            emptyList()
        }

        return ComputedProjection(
            model = model,
            origin = origin.immutable(),
            centerPoint = centerPoint.immutable(),
            rotation = rotation,
            mirror = mirror,
            blocks = blocks,
            viewportBatches = batches,
            minX = minX,
            minY = minY,
            minZ = minZ,
            maxX = maxX,
            maxY = maxY,
            maxZ = maxZ,
            totalVolume = totalVolume.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1),
            cameraFit = fit
        )
    }

    @JvmStatic
    fun clearCache() {
        cache.clear()
    }

    private fun buildKey(
        cacheKey: String,
        origin: BlockPos,
        rotation: Rotation,
        mirror: Mirror,
        centerPoint: BlockPos,
        includeViewportPlan: Boolean
    ): String {
        return "$cacheKey|${origin.asLong()}|${rotation.name}|${mirror.name}|${centerPoint.asLong()}|$includeViewportPlan"
    }

    private fun viewportPriorityDistance(entry: ViewportEntry, fit: CameraFit): Long {
        val sectionX = entry.pos.x shr 4
        val sectionY = entry.pos.y shr 4
        val sectionZ = entry.pos.z shr 4
        val centerSectionX = fit.centerX.toInt() shr 4
        val centerSectionY = fit.centerY.toInt() shr 4
        val centerSectionZ = fit.centerZ.toInt() shr 4
        val dx = (sectionX - centerSectionX).toLong()
        val dy = (sectionY - centerSectionY).toLong()
        val dz = (sectionZ - centerSectionZ).toLong()
        return dx * dx + dy * dy + dz * dz
    }

    private fun rotatePos(pos: BlockPos, rotation: Rotation): BlockPos {
        return when (rotation) {
            Rotation.CLOCKWISE_90 -> BlockPos(-pos.z, pos.y, pos.x)
            Rotation.CLOCKWISE_180 -> BlockPos(-pos.x, pos.y, -pos.z)
            Rotation.COUNTERCLOCKWISE_90 -> BlockPos(pos.z, pos.y, -pos.x)
            else -> pos
        }
    }

    private fun mirrorPos(pos: BlockPos, mirror: Mirror): BlockPos {
        return when (mirror) {
            Mirror.LEFT_RIGHT -> BlockPos(pos.x, pos.y, -pos.z)
            Mirror.FRONT_BACK -> BlockPos(-pos.x, pos.y, pos.z)
            else -> pos
        }
    }

    private fun transformState(state: BlockState, rotation: Rotation, mirror: Mirror): BlockState {
        var transformed = state
        if (mirror != Mirror.NONE) transformed = transformed.mirror(mirror)
        if (rotation != Rotation.NONE) transformed = transformed.rotate(rotation)
        return transformed
    }

    private fun resolveBlockState(source: Any?): BlockState {
        if (source is BlockState) return source
        val raw = source as? String ?: return Blocks.AIR.defaultBlockState()
        if (raw.isBlank() || raw == "minecraft:air" || raw == "air") {
            return Blocks.AIR.defaultBlockState()
        }
        return try {
            var id = raw
            var props: String? = null
            val bracket = raw.indexOf('[')
            if (bracket >= 0) {
                id = raw.substring(0, bracket)
                val end = raw.indexOf(']', bracket)
                if (end > bracket + 1) props = raw.substring(bracket + 1, end)
            }
            if (id.startsWith("Block{") && id.endsWith("}")) {
                id = id.substring(6, id.length - 1)
            }
            val block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(Blocks.STONE)
            var state = block.defaultBlockState()
            if (props != null) {
                for (pair in props.split(",")) {
                    val parts = pair.split("=")
                    if (parts.size == 2) {
                        state = applyProperty(state, parts[0], parts[1])
                    }
                }
            }
            state
        } catch (_: Exception) {
            Blocks.STONE.defaultBlockState()
        }
    }

    private fun applyProperty(state: BlockState, name: String, value: String): BlockState {
        val property = state.properties.firstOrNull { it.name == name } ?: return state
        return applyPropertyUnchecked(state, property, value)
    }

    private fun <T : Comparable<T>> applyPropertyUnchecked(state: BlockState, property: Property<T>, value: String): BlockState {
        val parsed = property.getValue(value).orElse(null) ?: return state
        return state.setValue(property, parsed)
    }

    private fun cameraFit(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): CameraFit {
        val centerX = (minX + maxX + 1) * 0.5f
        val centerY = (minY + maxY + 1) * 0.5f
        val centerZ = (minZ + maxZ + 1) * 0.5f
        val dx = (maxX - minX + 1).coerceAtLeast(1)
        val dy = (maxY - minY + 1).coerceAtLeast(1)
        val dz = (maxZ - minZ + 1).coerceAtLeast(1)
        val diagonal = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        val zoom = max(6f, diagonal * 0.7f)
        return CameraFit(centerX, centerY, centerZ, zoom)
    }
}
