package org.chaiware.acommander.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Runs an external program and always drains its output, so a full stdout/stderr pipe can never hang it. */
public final class ProcessRunner {
    private final List<String> command;
    private File directory;
    private Charset charset = StandardCharsets.UTF_8;
    private boolean mergeStderr;
    private Set<Process> tracker;

    private ProcessRunner(List<String> command) {
        this.command = List.copyOf(command);
    }

    public static ProcessRunner of(List<String> command) {
        return new ProcessRunner(command);
    }

    public static ProcessRunner of(String... command) {
        return new ProcessRunner(List.of(command));
    }

    public ProcessRunner directory(File directory) {
        this.directory = directory;
        return this;
    }

    public ProcessRunner charset(Charset charset) {
        this.charset = charset;
        return this;
    }

    public ProcessRunner mergeStderr() {
        this.mergeStderr = true;
        return this;
    }

    /** Keeps the process in {@code tracker} while it runs, so a Stop button can kill it. */
    public ProcessRunner trackIn(Set<Process> tracker) {
        this.tracker = tracker;
        return this;
    }

    /** Runs to completion and returns the exit code with stdout and stderr lines. */
    public Result run() throws IOException, InterruptedException {
        Process process = builder().redirectErrorStream(mergeStderr).start();
        if (tracker != null) {
            tracker.add(process);
        }
        boolean finished = false;
        try {
            process.getOutputStream().close();
            FutureTask<List<String>> stderr = new FutureTask<>(() -> readLines(process.getErrorStream()));
            if (mergeStderr) {
                stderr.run();
            } else {
                Thread.ofVirtual().start(stderr);
            }
            List<String> stdout = readLines(process.getInputStream());
            int exitCode = process.waitFor();
            Result result = new Result(exitCode, stdout, stderr.get());
            finished = true;
            return result;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof IOException io ? io : new IOException(e.getCause());
        } finally {
            if (tracker != null) {
                tracker.remove(process);
            }
            if (!finished) {
                process.destroyForcibly();
            }
        }
    }

    /** Starts a program the user interacts with (viewer, dialog) without waiting; its output is discarded. */
    public Process launch() throws IOException {
        return builder()
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private ProcessBuilder builder() {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null) {
            builder.directory(directory);
        }
        return builder;
    }

    private List<String> readLines(InputStream stream) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    public record Result(int exitCode, List<String> stdout, List<String> stderr) {
        public boolean succeeded() {
            return exitCode == 0;
        }

        /** Stdout with every line followed by {@code \n}. */
        public String stdoutText() {
            return text(stdout);
        }

        /** Stderr with every line followed by {@code \n}. */
        public String stderrText() {
            return text(stderr);
        }

        private static String text(List<String> lines) {
            StringBuilder text = new StringBuilder();
            for (String line : lines) {
                text.append(line).append('\n');
            }
            return text.toString();
        }
    }
}
