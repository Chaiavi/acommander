package org.chaiware.acommander.helpers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * The app's one executor for background work. Virtual threads, so tasks that block for a long time (a viewer open
 * until the user closes it, an FTP transfer) never starve each other the way they do on the small common pool.
 */
public final class BackgroundTasks {
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private BackgroundTasks() {
    }

    public static CompletableFuture<Void> run(Runnable task) {
        return CompletableFuture.runAsync(task, EXECUTOR);
    }

    public static <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, EXECUTOR);
    }
}
