package com.l1ght.ebe.async

import com.l1ght.ebe.EBEMod
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object EbeScopes {
    internal val computeExecutor: ExecutorService = Executors.newFixedThreadPool(
        maxOf(2, Runtime.getRuntime().availableProcessors() - 1),
        NamedThreadFactory("EBE Compute")
    )
    internal val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor(NamedThreadFactory("EBE IO"))
    private val runner: TaskRunner = createRunner()

    @JvmStatic
    fun <T> submitCompute(task: Callable<T>): CompletableFuture<T> {
        return runner.submitCompute(task)
    }

    @JvmStatic
    fun submitRaw(task: Runnable): Future<*> {
        return computeExecutor.submit(task)
    }

    @JvmStatic
    fun shutdown() {
        runner.shutdown()
        computeExecutor.shutdownNow()
        ioExecutor.shutdownNow()
    }

    private fun createRunner(): TaskRunner {
        return try {
            val type = Class.forName("com.l1ght.ebe.async.CoroutineTaskRunner")
            val runner = type.getDeclaredConstructor().newInstance() as TaskRunner
            EBEMod.LOGGER.info("Using Kotlin coroutine runner for EBE background compute tasks")
            runner
        } catch (t: Throwable) {
            EBEMod.LOGGER.info("Kotlin coroutine runtime is unavailable; using JDK executor fallback for EBE background compute tasks")
            EBEMod.LOGGER.debug("Coroutine runner initialization failed", t)
            ExecutorTaskRunner(computeExecutor)
        }
    }

    internal class NamedThreadFactory(private val prefix: String) : ThreadFactory {
        private val index = AtomicInteger(1)

        override fun newThread(runnable: Runnable): Thread {
            return Thread(runnable, "$prefix-${index.getAndIncrement()}").apply {
                isDaemon = true
            }
        }
    }
}
