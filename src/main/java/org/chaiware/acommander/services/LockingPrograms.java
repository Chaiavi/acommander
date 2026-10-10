package org.chaiware.acommander.services;

import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.ProcessRunner;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The programs that hold files open, found with File Locksmith (PowerToys), and ending them so a delete can go
 * through. Without admin rights it sees and ends only the programs running as this user.
 */
public final class LockingPrograms {
    /** A program holding one of the files, or something inside one of the folders, open. */
    public record Locker(long pid, String user, String name) {
        public String describe() {
            return name + " (PID " + pid + ")";
        }
    }

    private LockingPrograms() {
    }

    /** The programs holding {@code paths} (files, or anything inside folders) open. Blocking. */
    public static List<Locker> find(List<String> paths) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(BundledTool.FILE_LOCKSMITH.path().toString());
        command.addAll(paths);
        // It writes the ANSI code page; its --json mode stops at the first name that code page can't hold, so the
        // table (no file names) is read instead.
        ProcessRunner.Result result = ProcessRunner.of(command)
                .charset(Charset.forName(System.getProperty("native.encoding"), Charset.defaultCharset()))
                .run();
        if (!result.succeeded()) {
            throw new IOException("File Locksmith failed (exit " + result.exitCode() + "): " + result.stderrText().strip());
        }
        return parse(result.stdout());
    }

    /** Its table: a "PID User Process" header, then one tab-separated row per program. */
    static List<Locker> parse(List<String> lines) {
        List<Locker> lockers = new ArrayList<>();
        for (String line : lines) {
            String[] columns = line.split("\t", 3);
            if (columns.length == 3 && columns[0].strip().matches("\\d+")) {
                lockers.add(new Locker(Long.parseLong(columns[0].strip()), columns[1].strip(), columns[2].strip()));
            }
        }
        return lockers;
    }

    /** Ends each locker and waits for it to exit; returns the ones still running (ACommander itself, or not allowed). */
    public static List<Locker> end(List<Locker> lockers) {
        List<Locker> running = new ArrayList<>();
        for (Locker locker : lockers) {
            if (locker.pid() == ProcessHandle.current().pid()) {
                running.add(locker);
                continue;
            }
            Optional<ProcessHandle> process = ProcessHandle.of(locker.pid()).filter(handle -> isSameProgram(handle, locker));
            if (process.isPresent() && !ended(process.get())) {
                running.add(locker);
            }
        }
        return running;
    }

    /** False when the program closed and Windows gave its PID to another one while the user decided. */
    private static boolean isSameProgram(ProcessHandle process, Locker locker) {
        return process.info().command()
                .map(command -> command.toLowerCase().endsWith("\\" + locker.name().toLowerCase()))
                .orElse(true);
    }

    private static boolean ended(ProcessHandle process) {
        process.destroyForcibly();
        try {
            process.onExit().get(5, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException | ExecutionException e) {
            return !process.isAlive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
    }
}
