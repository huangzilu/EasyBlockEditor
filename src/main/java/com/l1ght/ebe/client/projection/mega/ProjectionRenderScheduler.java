package com.l1ght.ebe.client.projection.mega;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

public final class ProjectionRenderScheduler {
    private static final Logger LOG = LoggerFactory.getLogger("EBE/MegaProjectionScheduler");
    private final Map<Priority, ArrayDeque<Task>> queues = new EnumMap<>(Priority.class);
    private long sequence;

    public ProjectionRenderScheduler() {
        for (var priority : Priority.values()) {
            queues.put(priority, new ArrayDeque<>());
        }
    }

    public void submit(Priority priority, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        queues.get(priority == null ? Priority.BACKGROUND : priority)
                .addLast(new Task(++sequence, runnable));
    }

    public int runBudgeted(long budgetNanos, int maxTasks) {
        if (budgetNanos <= 0L || maxTasks <= 0) {
            return 0;
        }
        long deadline = System.nanoTime() + budgetNanos;
        int ran = 0;
        while (ran < maxTasks && System.nanoTime() < deadline) {
            Task task = pollNext();
            if (task == null) {
                break;
            }
            try {
                task.runnable().run();
            } catch (Throwable t) {
                LOG.warn("Projection render task {} failed", task.sequence(), t);
            }
            ran++;
        }
        return ran;
    }

    public void clear() {
        for (var queue : queues.values()) {
            queue.clear();
        }
    }

    public boolean isEmpty() {
        for (var queue : queues.values()) {
            if (!queue.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Task pollNext() {
        for (var priority : Priority.values()) {
            var queue = queues.get(priority);
            if (queue != null && !queue.isEmpty()) {
                return queue.pollFirst();
            }
        }
        return null;
    }

    public enum Priority {
        IMMEDIATE,
        VISIBLE,
        NEAR,
        BACKGROUND
    }

    private record Task(long sequence, Runnable runnable) {
    }
}
