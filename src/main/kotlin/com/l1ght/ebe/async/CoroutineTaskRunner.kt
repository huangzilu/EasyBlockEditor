package com.l1ght.ebe.async

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

class CoroutineTaskRunner : TaskRunner {
    private val computeDispatcher = EbeScopes.computeExecutor.asCoroutineDispatcher()
    private val supervisor = SupervisorJob()
    private val computeScope = CoroutineScope(supervisor + computeDispatcher + CoroutineName("EBE Compute Scope"))

    override fun <T> submitCompute(task: Callable<T>): CompletableFuture<T> {
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

    override fun shutdown() {
        computeScope.cancel()
        computeDispatcher.close()
    }
}
