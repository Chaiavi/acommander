package org.chaiware.acommander.helpers;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import org.chaiware.acommander.commands.Operation;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** The progress bar and Stop button under the panes: one count over running tools and background work. */
public final class ExternalProgressController {
    private final HBox box;
    private final Label label;
    private final Button stopButton;
    private final AtomicInteger running = new AtomicInteger();

    public ExternalProgressController(HBox box, ProgressBar bar, Label label, Button stopButton) {
        this.box = box;
        this.label = label;
        this.stopButton = stopButton;
        bar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        update(0);
    }

    /** Any thread. Shows the bar, labelled with {@code name} when it is the only running task. */
    public void started(String name) {
        int active = running.incrementAndGet();
        onFxThread(() -> {
            box.setVisible(true);
            box.setManaged(true);
            label.setText(active == 1 ? "Running: " + name : "Running " + active + " external tasks...");
            stopButton.setDisable(false);
        });
    }

    /** Any thread. Hides the bar when nothing runs any more. */
    public void finished() {
        int active = running.updateAndGet(current -> Math.max(0, current - 1));
        onFxThread(() -> update(active));
    }

    /**
     * Runs {@code work} on the background executor behind the bar, as an {@link Operation} the Stop button reaches.
     * {@code onSuccess} gets its result and {@code onFailure} its exception, both on the FX thread.
     */
    public <T> void run(String name, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        started(name);
        Operation operation = Operation.start();
        BackgroundTasks.supply(() -> {
            try {
                return operation.run(work);
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                operation.finish();
            }
        }).whenComplete((result, failure) -> {
            finished();
            Platform.runLater(() -> {
                if (failure == null) {
                    onSuccess.accept(result);
                } else {
                    onFailure.accept(failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure);
                }
            });
        });
    }

    /** The exe's file name, for the progress label. */
    public static String toolName(List<String> command) {
        String exe = exeName(command);
        return exe.isEmpty() ? "external command" : exe;
    }

    /** A non-zero exit that is a real failure: ExamDiff exits 27 when the files differ, Explorer exits 1 on success. */
    public static boolean isFailedExit(List<String> command, int exitCode) {
        String exe = exeName(command);
        return exitCode != 0
                && !(exitCode == 27 && "examdiff.exe".equalsIgnoreCase(exe))
                && !(exitCode == 1 && "explorer.exe".equalsIgnoreCase(exe));
    }

    private static String exeName(List<String> command) {
        if (command == null || command.isEmpty() || command.getFirst() == null || command.getFirst().isBlank()) {
            return "";
        }
        String exe = command.getFirst();
        try {
            Path fileName = Paths.get(exe).getFileName();
            return fileName == null ? exe : fileName.toString();
        } catch (InvalidPathException e) {
            return exe;
        }
    }

    private void update(int active) {
        if (active <= 0) {
            box.setVisible(false);
            box.setManaged(false);
            label.setText("");
            stopButton.setDisable(true);
        } else {
            label.setText("Running " + active + " external tasks...");
            stopButton.setDisable(false);
        }
    }

    private static void onFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
