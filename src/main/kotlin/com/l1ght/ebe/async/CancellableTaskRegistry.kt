package com.l1ght.ebe.async

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

object CancellableTaskRegistry {
    private val jobs = ConcurrentHashMap<UUID, Future<*>>()

    @JvmStatic
    fun register(id: UUID, job: Future<*>) {
        jobs.put(id, job)?.cancel(true)
    }

    @JvmStatic
    fun cancel(id: UUID): Boolean {
        return jobs.remove(id)?.also { it.cancel(true) } != null
    }

    @JvmStatic
    fun cancelAll() {
        jobs.values.forEach { it.cancel(true) }
        jobs.clear()
    }
}
