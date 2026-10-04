package org.chaiware.acommander.commands;

import org.chaiware.acommander.helpers.BackgroundTasks;
import org.chaiware.acommander.tools.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs external tools in the background: progress events to the listener, the Stop button, and failure reports. */
public class ExternalToolRunner {
    private static final Logger log = LoggerFactory.getLogger(ExternalToolRunner.class);

    private final Runnable onFilesChanged;
    private final Set<Process> runningProcesses = ConcurrentHashMap.newKeySet();
    private final AtomicInteger stopRequests = new AtomicInteger();
    private volatile ExternalCommandListener listener;

    /** {@code onFilesChanged} runs after a tool succeeds that was started with {@code changesFiles}. */
    public ExternalToolRunner(Runnable onFilesChanged) {
        this.onFilesChanged = onFilesChanged;
    }

    public void setListener(ExternalCommandListener listener) {
        this.listener = listener;
    }

    public CompletableFuture<List<String>> runExecutable(List<String> command, boolean changesFiles) {
        return runExecutable(command, changesFiles, Set.of());
    }

    /** Runs {@code command}; the future fails with {@link ExternalCommandException} on an exit code not accepted. */
    public CompletableFuture<List<String>> runExecutable(List<String> command, boolean changesFiles,
                                                         Set<Integer> acceptedNonZeroExitCodes) {
        List<String> commandSnapshot = List.copyOf(command);
        Set<Integer> acceptedExitCodes = new HashSet<>(acceptedNonZeroExitCodes);
        acceptedExitCodes.add(0);
        notifyStarted(commandSnapshot);
        return BackgroundTasks.supply(() -> {
            int exitCode = -1;
            Throwable failure = null;
            try {
                log.debug("Running: {}", String.join(" ", commandSnapshot));
                ProcessRunner.Result result = ProcessRunner.of(commandSnapshot).mergeStderr().trackIn(runningProcesses).run();
                List<String> output = result.stdout();
                exitCode = result.exitCode();
                log.debug("Process completed with exit code: {}", exitCode);
                if (!acceptedExitCodes.contains(exitCode)) {
                    String toolOutput = summarizeOutput(output);
                    ExternalCommandException ex = new ExternalCommandException(exitCode, String.join(" ", commandSnapshot), toolOutput);
                    failure = ex;
                    log.error("External command failed. exitCode={} command={} outputTail={}",
                            exitCode, String.join(" ", commandSnapshot), toolOutput);
                    throw ex;
                }
                if (changesFiles) {
                    onFilesChanged.run();
                }
                return output;
            } catch (IOException | InterruptedException e) {
                failure = e;
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("Error running external process. command={}", String.join(" ", commandSnapshot), e);
                throw new RuntimeException(e);
            } finally {
                notifyFinished(commandSnapshot, exitCode, failure);
            }
        });
    }

    /**
     * Shows the user a failure of work nobody waits on (otherwise it is only logged). A tool the user stopped with
     * the Stop button is not reported.
     */
    public void reportFailure(CompletableFuture<?> work, String title) {
        int stopsBefore = stopRequests.get();
        work.exceptionally(ex -> {
            Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
            log.error("{} failed", title, cause);
            // ponytail: one stop counter for all tools, so Stop also mutes a different tool failing meanwhile.
            // Track the stopped processes if that matters.
            boolean stoppedByUser = cause instanceof ExternalCommandException && stopRequests.get() != stopsBefore;
            ExternalCommandListener current = listener;
            if (!stoppedByUser && current != null) {
                current.onFailure(title, cause);
            }
            return null;
        });
    }

    /** Stops every running tool; returns how many were still alive. */
    public int stopAll() {
        stopRequests.incrementAndGet();
        int stopped = 0;
        for (Process process : new ArrayList<>(runningProcesses)) {
            if (!process.isAlive()) {
                continue;
            }
            try {
                process.destroy();
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                stopped++;
            } catch (Exception ex) {
                log.warn("Failed stopping external process", ex);
            }
        }
        return stopped;
    }

    private void notifyStarted(List<String> command) {
        ExternalCommandListener current = listener;
        if (current == null) {
            return;
        }
        try {
            current.onCommandStarted(command);
        } catch (Exception ex) {
            log.debug("External command listener failed on start", ex);
        }
    }

    private void notifyFinished(List<String> command, int exitCode, Throwable error) {
        ExternalCommandListener current = listener;
        if (current == null) {
            return;
        }
        try {
            current.onCommandFinished(command, exitCode, error);
        } catch (Exception ex) {
            log.debug("External command listener failed on finish", ex);
        }
    }

    private static String summarizeOutput(List<String> output) {
        if (output == null || output.isEmpty()) {
            return "<no output>";
        }
        String tail = String.join(" | ", output.subList(Math.max(0, output.size() - 20), output.size()));
        return tail.length() > 4000 ? tail.substring(tail.length() - 4000) : tail;
    }
}
