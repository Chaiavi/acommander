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
        assertThat(violations("deleteOnExit|File\\.createTempFile\\(|Files\\.createTemp(File|Directory)\\(\""))
                .as("create temp files with helpers/AppTempDir; it deletes them on exit").isEmpty();
    }

    @Test
    void versionIsNeverHardCoded() throws IOException {
        assertThat(violations("(?i)version\\w*\\s*=\\s*\"\\d+\\.\\d"))
                .as("read the version with helpers/AppVersion; build.gradle appVersion is the only source").isEmpty();
    }

    @Test
    void appFilesAreFoundThroughAppPaths() throws IOException {
        assertThat(violations("user\\.dir\"", "AppPaths.java"))
                .as("resolve config/ and apps/ files with helpers/AppPaths").isEmpty();
        assertThat(violations("\"apps[/\\\\\"]", "BundledTool.java"))
                .as("add a tool the code runs directly to tools/BundledTool; its test checks the file is shipped")
                .isEmpty();
    }

    @Test
    void toolRunsAreNeverFireAndForget() throws IOException {
        assertThat(violations("^\\s*(\\w+\\.)?(runExecutable|runExternal)\\((?:[^()]|\\([^()]*\\))*\\);\\s*$"))
                .as("a dropped tool future hides its failure; wrap it in reportFailure or use runExternalReported")
                .isEmpty();
    }

    @Test
    void servicesAndToolRunsStayFreeOfJavaFx() throws IOException {
        Path root = MAIN.resolve(Path.of("org", "chaiware", "acommander"));
        try (Stream<Path> files = Stream.concat(Files.list(root.resolve("services")), Files.list(root.resolve("commands")))) {
            assertThat(files.flatMap(file -> matchingLines(file, Pattern.compile("^import javafx\\."))).toList())
                    .as("services/ and commands/ run off the FX thread and are unit-tested; leave JavaFX to Commander and dialog/")
                    .isEmpty();
        }
    }

    @Test
    void commanderLeavesFileTreesHashesAndSettingsToTestableClasses() {
        Path commander = MAIN.resolve(Path.of("org", "chaiware", "acommander", "Commander.java"));
        assertThat(matchingLines(commander, Pattern.compile("Files\\.(walk|list)\\(|MessageDigest|\\bProperties\\s+\\w+\\s*[;=]|new Properties\\(")).toList())
                .as("walk folders, hash files and read settings outside Commander (services/, helpers/), where a test can reach them")
                .isEmpty();
    }

    @Test
    void curlGetsTheLoginOnStdinAndChecksCertificates() {
        Path ftp = MAIN.resolve(Path.of("org", "chaiware", "acommander", "vfs", "FtpFileSystem.java"));
        assertThat(matchingLines(ftp, Pattern.compile("\"(-u|--user|-k)\"")).toList())
                .as("the login goes to curl on stdin (-K -), never in its arguments; --insecure only for Trust Any Certificate")
                .isEmpty();
    }

    @Test
    void shellsNeverParseFileNames() throws IOException {
        assertThat(violations("\"/[ck]\""))
                .as("cmd /c parses & and %VAR% in names; use ShellExecute (rundll32 ShellExec_RunDLL) or a working directory")
                .isEmpty();
        assertThat(violations("\"-Command\"", "FileOperations.java", "ComboBoxSetup.java", "Dpapi.java"))
                .as("a PowerShell -Command must be a constant script; pass paths and secrets as the working directory or stdin")
                .isEmpty();
    }

    private static List<String> violations(String regex, String... allowedFileNames) throws IOException {
        Pattern pattern = Pattern.compile(regex);
        List<Path> sources;
        try (Stream<Path> files = Files.walk(MAIN)) {
            sources = files.filter(file -> file.toString().endsWith(".java")).toList();
        }
        assertThat(sources).as("no sources found under " + MAIN.toAbsolutePath()).isNotEmpty();
        return sources.stream()
                .filter(file -> !List.of(allowedFileNames).contains(file.getFileName().toString()))
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
