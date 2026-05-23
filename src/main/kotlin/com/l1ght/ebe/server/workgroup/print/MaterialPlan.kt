package com.l1ght.ebe.server.workgroup.print

data class MaterialPlan(
    val requiredByState: Map<String, Int>
) {
    companion object {
        @JvmStatic
        fun fromTargets(targets: Collection<PrintBlockTarget>): MaterialPlan {
            val counts = LinkedHashMap<String, Int>()
            for (target in targets) {
                val key = if (target.nbt.isBlank()) {
                    target.stateId.toString()
                } else {
                    "${target.stateId}|${target.nbt}"
                }
                counts[key] = (counts[key] ?: 0) + 1
            }
            return MaterialPlan(counts)
        }
    }
}
