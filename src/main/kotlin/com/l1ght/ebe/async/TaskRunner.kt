package com.l1ght.ebe.async

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture

interface TaskRunner {
    fun <T> submitCompute(task: Callable<T>): CompletableFuture<T>

    fun shutdown()
}
