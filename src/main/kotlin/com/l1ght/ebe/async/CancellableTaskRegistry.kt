package com.l1ght.ebe.async

import kotlinx.coroutines.Job
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CancellableTaskRegistry {
    private val jobs = ConcurrentHashMap<UUID, Job>()

    @JvmStatic
    fun register(id: UUID, job: Job) {
        jobs.put(id, job)?.cancel()
        job.invokeOnCompletion { jobs.remove(id, job) }
    }

    @JvmStatic
    fun cancel(id: UUID): Boolean {
        return jobs.remove(id)?.also { it.cancel() } != null
    }

    @JvmStatic
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
