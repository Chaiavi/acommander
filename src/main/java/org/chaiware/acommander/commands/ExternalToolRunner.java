package org.chaiware.acommander.commands;

import org.chaiware.acommander.helpers.BackgroundTasks;
import org.chaiware.acommander.tools.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Runs external tools in the background: progress events to the listener, failure reports. Each run belongs to the
 * {@link Operation} it was started from (or its own one), so the Stop button reaches it.
 */
public class ExternalToolRunner {
    private static final Logger log = LoggerFactory.getLogger(ExternalToolRunner.class);

    private final Runnable onFilesChanged;
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

    /**
     * Runs {@code command}; the future fails with {@link ExternalCommandException} on an exit code not accepted, and
     * with {@link OperationStoppedException} when the user pressed Stop.
     */
    public CompletableFuture<List<String>> runExecutable(List<String> command, boolean changesFiles,
                                                         Set<Integer> acceptedNonZeroExitCodes) {
        return runExecutable(command, changesFiles, acceptedNonZeroExitCodes, null);
    }

    /** Same, run in {@code directory}; null keeps the app's working folder. */
    public CompletableFuture<List<String>> runExecutable(List<String> command, boolean changesFiles,
                                                         Set<Integer> acceptedNonZeroExitCodes, File directory) {
        List<String> commandSnapshot = List.copyOf(command);
        Set<Integer> acceptedExitCodes = new HashSet<>(acceptedNonZeroExitCodes);
        acceptedExitCodes.add(0);
        Operation current = Operation.current();
        Operation operation = current != null ? current.retain() : Operation.start();
        notifyStarted(commandSnapshot);
        return BackgroundTasks.supply(() -> {
            int[] exitCode = {-1};
            Throwable failure = null;
            try {
                return operation.run(() -> {
                    log.debug("Running: {}", String.join(" ", commandSnapshot));
                    ProcessRunner.Result result = ProcessRunner.of(commandSnapshot).directory(directory).mergeStderr().trackIn(operation).run();
                    exitCode[0] = result.exitCode();
                    log.debug("Process completed with exit code: {}", exitCode[0]);
                    operation.throwIfStopped();
                    if (!acceptedExitCodes.contains(exitCode[0])) {
                        String toolOutput = summarizeOutput(result.stdout());
                        log.error("External command failed. exitCode={} command={} outputTail={}",
                                exitCode[0], String.join(" ", commandSnapshot), toolOutput);
                        throw new ExternalCommandException(exitCode[0], String.join(" ", commandSnapshot), toolOutput);
                    }
                    if (changesFiles) {
                        onFilesChanged.run();
                    }
                    return result.stdout();
                });
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                // A killed process ends with an exit code or an I/O error; after Stop either one means "stopped"
                RuntimeException thrown = operation.isStopped() ? new OperationStoppedException()
                        : e instanceof RuntimeException runtime ? runtime : new RuntimeException(e);
                if (!(thrown instanceof OperationStoppedException) && !(thrown instanceof ExternalCommandException)) {
                    log.error("Error running external process. command={}", String.join(" ", commandSnapshot), e);
                }
                failure = thrown;
                throw thrown;
            } finally {
                operation.finish();
                notifyFinished(commandSnapshot, exitCode[0], failure);
            }
        });
    }

    /**
     * Shows the user a failure of work nobody waits on (otherwise it is only logged). Work the user stopped is not
     * reported; a different tool failing meanwhile still is.
     */
    public void reportFailure(CompletableFuture<?> work, String title) {
        work.exceptionally(ex -> {
            Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
            if (Operation.isStop(cause)) {
                log.info("{} stopped by the user", title);
                return null;
            }
            log.error("{} failed", title, cause);
            ExternalCommandListener current = listener;
            if (current != null) {
                current.onFailure(title, cause);
            }
            return null;
        });
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
