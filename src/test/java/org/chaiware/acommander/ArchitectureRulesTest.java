package org.chaiware.acommander;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Source rules the compiler can't enforce (docs/REFACTOR_PLAN.md). Each names the one file allowed to break it. */
class ArchitectureRulesTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    @Test
    void processesStartOnlyInProcessRunner() throws IOException {
        assertThat(violations("new ProcessBuilder\\(", "ProcessRunner.java"))
                .as("start processes with tools/ProcessRunner, which drains their output").isEmpty();
    }

    @Test
    void backgroundWorkRunsOnBackgroundTasks() throws IOException {
        assertThat(violations("Executors\\.new|CompletableFuture\\.(runAsync|supplyAsync)\\(", "BackgroundTasks.java"))
                .as("run background work with helpers/BackgroundTasks").isEmpty();
    }

    @Test
    void tempFilesLiveUnderAppTempDir() throws IOException {
        assertThat(violations("deleteOnExit|File\\.createTempFile\\(|Files\\.createTemp(File|Directory)\\(\"", null))
                .as("create temp files with helpers/AppTempDir; it deletes them on exit").isEmpty();
    }

    @Test
    void versionIsNeverHardCoded() throws IOException {
        assertThat(violations("(?i)version\\w*\\s*=\\s*\"\\d+\\.\\d", null))
                .as("read the version with helpers/AppVersion; build.gradle appVersion is the only source").isEmpty();
    }

    @Test
    void toolRunsAreNeverFireAndForget() throws IOException {
        assertThat(violations("^\\s*(runExecutable|runExternal)\\((?:[^()]|\\([^()]*\\))*\\);\\s*$", null))
                .as("a dropped tool future hides its failure; wrap it in reportFailure or use runExternalReported")
                .isEmpty();
    }

    private static List<String> violations(String regex, String allowedFileName) throws IOException {
        Pattern pattern = Pattern.compile(regex);
        List<Path> sources;
        try (Stream<Path> files = Files.walk(MAIN)) {
            sources = files.filter(file -> file.toString().endsWith(".java")).toList();
        }
        assertThat(sources).as("no sources found under " + MAIN.toAbsolutePath()).isNotEmpty();
        return sources.stream()
                .filter(file -> !file.getFileName().toString().equals(allowedFileName))
                .flatMap(file -> matchingLines(file, pattern))
                .toList();
    }

    private static Stream<String> matchingLines(Path file, Pattern pattern) {
        try {
            List<String> lines = Files.readAllLines(file);
            return IntStream.range(0, lines.size())
                    .filter(i -> pattern.matcher(lines.get(i)).find())
                    .mapToObj(i -> MAIN.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
