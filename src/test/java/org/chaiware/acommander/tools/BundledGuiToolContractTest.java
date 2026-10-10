package org.chaiware.acommander.tools;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppPaths;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.services.ArchiveOperations;
import org.chaiware.acommander.services.ClipboardTransfer;
import org.chaiware.acommander.services.FileOperations;
import org.chaiware.acommander.services.TransferConflicts;
import org.chaiware.acommander.tools.BundledToolCommands.CompareFilesOptions;
import org.chaiware.acommander.tools.BundledToolCommands.WhiteSpaceCompareMode;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The tools under apps/ that open a window, run the way the app runs them. They flash windows, so `build` skips them;
 * the guiToolTest task runs them after a tool update. Tools that do their work from the command line (FastCopy,
 * 7-Zip GUI, Universal Extractor, Ant Renamer) are checked for the result; the ones that only show a file (Notepad4,
 * Universal Viewer, ExamDiff) are checked to start without crashing.
 */
@Tag("gui")
class BundledGuiToolContractTest {
    private static final Duration TOOL_TIMEOUT = Duration.ofMinutes(2);
    private static AppRegistry registry;

    @TempDir
    Path dir;

    @BeforeAll
    static void loadActions() throws IOException {
        registry = new AppRegistry(new AppConfigLoader().load(AppPaths.config("apps.json")));
    }

    @Test
    void fastCopyCopiesFiles() throws Exception {
        Path source = Files.createDirectory(dir.resolve("source"));
        Path target = Files.createDirectory(dir.resolve("target"));
        List<FileItem> items = new ArrayList<>();
        for (String name : List.of("one.txt", "two.txt")) {
            items.add(new FileItem(Files.writeString(source.resolve(name), name)));
        }

        ClipboardTransfer.PasteResult result = transfer(items, source, target, false);

        assertThat(result.failed()).isEmpty();
        assertThat(target.resolve("two.txt")).hasContent("two.txt");
    }

    @Test
    void fastCopyMovesAFolderIntoAnExistingOne() throws Exception {
        Path source = Files.createDirectories(dir.resolve("source/photos"));
        Files.writeString(source.resolve("new.txt"), "new");
        Path existing = Files.createDirectories(dir.resolve("target/photos"));
        Files.writeString(existing.resolve("old.txt"), "old");

        ClipboardTransfer.PasteResult result = transfer(List.of(new FileItem(source)), source.getParent(), existing.getParent(), true);

        assertThat(result.failed()).isEmpty();
        assertThat(existing.resolve("new.txt")).hasContent("new");
        assertThat(existing.resolve("old.txt")).hasContent("old");
        assertThat(source).doesNotExist();
    }

    @Test
    void sevenZipGuiPacksAndUnpacks() throws Exception {
        // Without 7z.dll beside it, 7zG.exe borrows an installed 7-Zip's, so this passes only where one is installed.
        assertThat(AppPaths.resolve(registry.findAction("pack").orElseThrow().getPath()).resolveSibling("7z.dll")).exists();
        LocalFileSystem local = new LocalFileSystem("");
        ArchiveOperations archives = new ArchiveOperations(registry, new ExternalToolRunner(() -> {}));
        Path a = Files.writeString(dir.resolve("a.txt"), "alpha");
        Path b = Files.writeString(dir.resolve("b.txt"), "beta");
        Path out = Files.createDirectory(dir.resolve("out"));

        withTimeout(() -> archives.pack(local, List.of(entry(a), entry(b)), local, dir.toString(), "box.7z"));
        withTimeout(() -> archives.unpack(local, entry(dir.resolve("box.7z")), local, out.toString()));

        assertThat(out.resolve("a.txt")).hasContent("alpha");
        assertThat(out.resolve("b.txt")).hasContent("beta");
    }

    @Test
    void universalExtractorExtractsAnArchive() throws Exception {
        LocalFileSystem local = new LocalFileSystem("");
        ArchiveOperations archives = new ArchiveOperations(registry, new ExternalToolRunner(() -> {}));
        Path a = Files.writeString(dir.resolve("a.txt"), "alpha");
        withTimeout(() -> archives.pack(local, List.of(entry(a)), local, dir.toString(), "box.zip"));
        Path out = dir.resolve("extracted");

        withTimeout(() -> archives.extractAll(local, entry(dir.resolve("box.zip")), local, out.toString()));

        try (var files = Files.walk(out)) {
            assertThat(files.filter(file -> file.getFileName().toString().equals("a.txt")).toList())
                    .singleElement().satisfies(file -> assertThat(file).hasContent("alpha"));
        }
    }

    @Test
    void antRenamerRenamesTheFilesItIsGiven() throws Exception {
        Path file = Files.writeString(dir.resolve("old_name.txt"), "x");
        Path batch = Files.writeString(dir.resolve("rename.arb"), """
                <?xml version="1.0" encoding="UTF-8"?><AntRenamer><Batch>\
                <StrRepl Search="old" Repl="new" AllOccurences="True" CaseSensitive="True" IncludeExt="False" OnlyExt="False"/>\
                </Batch></AntRenamer>""");
        ActionDefinition rename = registry.findAction("multiRename").orElseThrow();
        List<String> command = new ArrayList<>(ToolCommandBuilder.buildCommand(rename.getPath(), rename.getArgs(), null,
                Map.of(), List.of(file.toString())));
        // The app opens Ant Renamer on the files; the test adds a batch, starts it and quits (-b, -g, -x).
        command.addAll(1, List.of("-b", batch.toString()));
        command.addAll(List.of("-g", "-x"));

        Process process = ProcessRunner.of(command).launch();
        assertThat(process.waitFor(TOOL_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).as("Ant Renamer quit").isTrue();

        assertThat(dir.resolve("new_name.txt")).exists();
    }

    @Test
    void notepad4OpensAFile() throws Exception {
        startsWithoutCrashing(actionCommand("edit", Files.writeString(dir.resolve("notes.txt"), "text")));
    }

    @Test
    void universalViewerOpensAFile() throws Exception {
        startsWithoutCrashing(actionCommand("view", Files.writeString(dir.resolve("notes.txt"), "text")));
    }

    @Test
    void examDiffComparesTwoFiles() throws Exception {
        Path left = Files.writeString(dir.resolve("left.txt"), "one\ntwo\n");
        Path right = Files.writeString(dir.resolve("right.txt"), "one\nthree\n");

        startsWithoutCrashing(BundledToolCommands.compareFiles(BundledTool.EXAM_DIFF.path().toString(), left.toString(),
                right.toString(), new CompareFilesOptions(false, WhiteSpaceCompareMode.NONE, false)));
    }

    @Test
    void minesPerfectStarts() throws Exception {
        startsWithoutCrashing(gameCommand("minesPerfect"), gameFolder("minesPerfect"));
    }

    @Test
    void puzzleCollectionStarts() throws Exception {
        startsWithoutCrashing(gameCommand("puzzleCollection"), gameFolder("puzzleCollection"));
    }

    @Test
    void arkanoidCloneStarts() throws Exception {
        startsWithoutCrashing(gameCommand("arkanoidClone"), gameFolder("arkanoidClone"));
    }

    private ClipboardTransfer.PasteResult transfer(List<FileItem> items, Path from, Path to, boolean cut) {
        LocalFileSystem local = new LocalFileSystem("");
        FileOperations operations = new FileOperations(registry, new ExternalToolRunner(() -> {}));
        return assertTimeoutPreemptively(TOOL_TIMEOUT, () -> operations.transfer(
                ClipboardTransfer.capture(items, cut, FilesPanesHelper.FocusSide.LEFT, local, from.toString()),
                local, to.toString(), TransferConflicts.Policy.OVERWRITE, List.of()));
    }

    private static List<String> actionCommand(String actionId, Path file) {
        ActionDefinition action = registry.findAction(actionId).orElseThrow();
        return ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), null, Map.of(), List.of(file.toString()));
    }

    private static List<String> gameCommand(String actionId) {
        ActionDefinition action = registry.findAction(actionId).orElseThrow();
        return ToolCommandBuilder.buildCommand(action.getPath(), action.getArgs(), null, Map.of(), null);
    }

    /** External actions run in the exe's folder; Mines-Perfect reads its boards and writes its settings there. */
    private static java.io.File gameFolder(String actionId) {
        return new java.io.File(gameCommand(actionId).getFirst()).getParentFile();
    }

    /** Running after a few seconds, or handed the file to an open copy (exit 0), means the window came up. */
    private static void startsWithoutCrashing(List<String> command) throws Exception {
        startsWithoutCrashing(command, null);
    }

    private static void startsWithoutCrashing(List<String> command, java.io.File directory) throws Exception {
        Process process = ProcessRunner.of(command).directory(directory).launch();
        try {
            if (process.waitFor(4, TimeUnit.SECONDS)) {
                assertThat(process.exitValue()).as(String.join(" ", command) + " exited").isZero();
            }
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private static void withTimeout(Executable work) {
        assertTimeoutPreemptively(TOOL_TIMEOUT, work);
    }

    private static ClipboardTransfer.Entry entry(Path file) {
        return new ClipboardTransfer.Entry(file.getFileName().toString(), false, file.toString());
    }
}
