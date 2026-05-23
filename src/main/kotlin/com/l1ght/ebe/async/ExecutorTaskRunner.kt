package com.l1ght.ebe.async

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

class ExecutorTaskRunner(
    private val executor: ExecutorService
) : TaskRunner {
    override fun <T> submitCompute(task: Callable<T>): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({
            task.call()
        }, executor)
    }

    override fun shutdown() {
    }
}
