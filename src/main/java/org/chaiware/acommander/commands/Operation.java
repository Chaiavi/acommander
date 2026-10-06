package org.chaiware.acommander.commands;

import org.chaiware.acommander.tools.ProcessRunner;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One thing the user started: a copy, a pack, a tool run. The Stop button stops every running one: the processes it
 * started are killed, and its next {@link #checkNotStopped} or process start throws {@link OperationStoppedException},
 * so a batch never goes on to its next item, a fallback, or a delete after an interrupted copy. Nothing is rolled back.
 */
public final class Operation implements ProcessRunner.Tracker {
    private static final Set<Operation> RUNNING = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Operation> CURRENT = new ThreadLocal<>();

    private final Set<Process> processes = ConcurrentHashMap.newKeySet();
    private final AtomicInteger holds = new AtomicInteger(1);
    private volatile boolean stopped;

    private Operation() {
    }

    /** A new operation, reachable by Stop until {@link #finish}. Start it before scheduling its work. */
    public static Operation start() {
        Operation operation = new Operation();
        RUNNING.add(operation);
        return operation;
    }

    /** One more {@link #finish} before Stop lets go: a viewer started by an operation may outlive it. */
    public Operation retain() {
        holds.incrementAndGet();
        return this;
    }

    public void finish() {
        if (holds.decrementAndGet() == 0) {
            RUNNING.remove(this);
        }
    }

    /** Runs {@code work} on this thread as this operation, so the processes it starts belong to it. */
    public <T> T run(Callable<T> work) throws Exception {
        Operation outer = CURRENT.get();
        CURRENT.set(this);
        try {
            throwIfStopped();
            return work.call();
        } finally {
            CURRENT.set(outer);
        }
    }

    /** The operation this thread runs for ({@link #run}), or null. */
    public static Operation current() {
        return CURRENT.get();
    }

    /** Throws if the operation this thread runs for was stopped. Call it between items and before deleting a source. */
    public static void checkNotStopped() {
        Operation operation = CURRENT.get();
        if (operation != null) {
            operation.throwIfStopped();
        }
    }

    public void throwIfStopped() {
        if (stopped) {
            throw new OperationStoppedException();
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    /** The Stop button: stops every running operation; returns how many there were. */
    public static int stopAll() {
        RUNNING.forEach(Operation::stop);
        return RUNNING.size();
    }

    private void stop() {
        stopped = true;
        processes.forEach(Operation::kill);
    }

    /** Called right after a process starts; one that started while Stop was pressed is killed at once. */
    @Override
    public void attach(Process process) {
        processes.add(process);
        if (stopped) {
            kill(process);
        }
    }

    @Override
    public void detach(Process process) {
        processes.remove(process);
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /** True if {@code failure} is a stop or was caused by one. */
    public static boolean isStop(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OperationStoppedException) {
                return true;
            }
        }
        return false;
    }

    /** Rethrows a stop, so a catch-all around one item never turns it into that item's failure and goes on. */
    public static void rethrowIfStopped(Throwable failure) {
        if (isStop(failure)) {
            throw new OperationStoppedException();
        }
    }
}
