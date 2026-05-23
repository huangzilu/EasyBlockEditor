package com.l1ght.ebe.async

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object EbeScopes {
    private val computeExecutor: ExecutorService = Executors.newFixedThreadPool(
        maxOf(2, Runtime.getRuntime().availableProcessors() - 1),
        NamedThreadFactory("EBE Compute")
    )
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor(NamedThreadFactory("EBE IO"))

    @JvmField
    val computeDispatcher: CoroutineDispatcher = computeExecutor.asCoroutineDispatcher()

    @JvmField
    val ioDispatcher: CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()

    private val supervisor = SupervisorJob()

    @JvmField
    val computeScope: CoroutineScope = CoroutineScope(supervisor + computeDispatcher + CoroutineName("EBE Compute Scope"))

    @JvmStatic
    fun <T> submitCompute(task: Callable<T>): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        computeScope.launch {
            try {
                future.complete(task.call())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        return future
    }

    @JvmStatic
    fun shutdown() {
        supervisor.cancel()
        computeExecutor.shutdownNow()
        ioExecutor.shutdownNow()
    }

    private class NamedThreadFactory(private val prefix: String) : ThreadFactory {
        private val index = AtomicInteger(1)

        override fun newThread(runnable: Runnable): Thread {
            return Thread(runnable, "$prefix-${index.getAndIncrement()}").apply {
                isDaemon = true
            }
        }
    }
}
