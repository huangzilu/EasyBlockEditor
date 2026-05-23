package com.l1ght.ebe.server.workgroup.print

import com.l1ght.ebe.async.EbeScopes
import net.minecraft.core.BlockPos
import java.util.concurrent.CompletableFuture

object WorkgroupPrintPlanner {
    data class RawPrintEntry(
        val pos: BlockPos,
        val stateId: Int,
        val nbt: String
    )

    @JvmStatic
    fun buildTargetsAsync(entries: List<RawPrintEntry>): CompletableFuture<List<PrintBlockTarget>> {
        val immutableEntries = entries.toList()
        return EbeScopes.submitCompute {
            val unique = LinkedHashMap<BlockPos, RawPrintEntry>(immutableEntries.size)
            for (entry in immutableEntries) {
                if (entry.stateId > 0) {
                    unique[entry.pos.immutable()] = entry
                }
            }
            unique.values
                .sortedWith(compareBy<RawPrintEntry> { it.pos.y }.thenBy { it.pos.z }.thenBy { it.pos.x })
                .mapIndexed { index, entry ->
                    PrintBlockTarget(index.toLong(), entry.pos.immutable(), entry.stateId, entry.nbt)
                }
        }
    }

    @JvmStatic
    fun estimateMaterialsAsync(targets: List<PrintBlockTarget>): CompletableFuture<MaterialPlan> {
        val immutableTargets = targets.toList()
        return EbeScopes.submitCompute {
            MaterialPlan.fromTargets(immutableTargets)
        }
    }
}
