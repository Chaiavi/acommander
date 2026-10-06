package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfig;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.ArchiveManager;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.vfs.ArchiveFileSystem;
import org.chaiware.acommander.vfs.FtpConnectionOptions;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.chaiware.acommander.services.TransferConflicts.Policy.OVERWRITE;
import static org.mockito.Mockito.*;

class FileOperationsTest {

    @TempDir
    Path tempDir;

    private final LocalFileSystem local = new LocalFileSystem("");
    private FileOperations operations;

    @BeforeEach
    void setUp() {
        AppConfig config = new AppConfig();
        config.setActions(List.of());
        operations = new FileOperations(new AppRegistry(config), new ExternalToolRunner(() -> {}));
    }

    private ClipboardTransfer.State capture(boolean cut, VFileSystem fs, Path folder, Path... items) {
        return ClipboardTransfer.capture(java.util.Arrays.stream(items).map(FileItem::new).toList(), cut,
                FilesPanesHelper.FocusSide.LEFT, fs, folder.toString());
    }

    @Test
    void renameRenamesInItsFolder() throws Exception {
        Path file = Files.writeString(tempDir.resolve("old.txt"), "rename");

        operations.rename(local, capture(false, local, tempDir, file).entries().getFirst(), "new.txt");

        assertThat(file).doesNotExist();
        assertThat(tempDir.resolve("new.txt")).hasContent("rename");
    }

    @Test
    void moveOnTheSameDriveMovesEveryItem() {
        Path targetDir = tempDir.resolve("target");
        Path first = write("one.txt", "first");
        Path second = write("two.txt", "second");
        targetDir.toFile().mkdir();

        ClipboardTransfer.PasteResult result = operations.transfer(capture(true, local, tempDir, first, second), local, targetDir.toString(), OVERWRITE, List.of());

        assertThat(result.failed()).isEmpty();
        assertThat(first).doesNotExist();
        assertThat(targetDir.resolve("one.txt")).hasContent("first");
        assertThat(targetDir.resolve("two.txt")).hasContent("second");
    }

    @Test
    void moveSkipsTheConflictsWhenAskedTo() throws Exception {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Path clash = write("clash.txt", "new");
        Path fresh = write("fresh.txt", "fresh");
        Files.writeString(targetDir.resolve("clash.txt"), "old");
        ClipboardTransfer.State state = capture(true, local, tempDir, clash, fresh);

        operations.transfer(state, local, targetDir.toString(), TransferConflicts.Policy.SKIP,
                TransferConflicts.find(state, local, targetDir.toString()));

        assertThat(targetDir.resolve("clash.txt")).hasContent("old");
        assertThat(clash).hasContent("new");
        assertThat(targetDir.resolve("fresh.txt")).hasContent("fresh");
    }

    @Test
    void moveIntoAnExistingFolderOnTheSameDriveMergesWithFastCopy() throws Exception {
        Path folder = Files.createDirectories(tempDir.resolve("src").resolve("photos"));
        Path targetDir = Files.createDirectories(tempDir.resolve("target").resolve("photos")).getParent();
        List<List<String>> commands = new ArrayList<>();
        ExternalToolRunner fastCopy = new ExternalToolRunner(() -> {}) {
            @Override
            public CompletableFuture<List<String>> runExecutable(List<String> command, boolean changesFiles, Set<Integer> accepted) {
                commands.add(command);
                return CompletableFuture.completedFuture(List.of());
            }
        };
        ActionDefinition move = new ActionDefinition();
        move.setId("move");
        move.setPath("apps/copy/fcp.exe");
        move.setArgs(List.of("/cmd=move", "${selectedFile}", "/to=${targetFolder}"));
        AppConfig config = new AppConfig();
        config.setActions(List.of(move));

        ClipboardTransfer.PasteResult result = new FileOperations(new AppRegistry(config), fastCopy)
                .transfer(capture(true, local, folder.getParent(), folder), local, targetDir.toString(), OVERWRITE, List.of());

        assertThat(result.failed()).isEmpty();
        assertThat(commands).hasSize(1);
        assertThat(commands.getFirst()).contains("/cmd=move", folder.toString());
    }

    @Test
    void copyIntoItsOwnFolderGetsADuplicateName() {
        Path file = write("sample.txt", "data");

        ClipboardTransfer.PasteResult result = operations.transfer(capture(false, local, tempDir, file), local, tempDir.toString(), OVERWRITE, List.of());

        assertThat(result.pasted()).extracting(ClipboardTransfer.Entry::name).containsExactly("sample_copy.txt");
        assertThat(tempDir.resolve("sample_copy.txt")).hasContent("data");
        assertThat(file).hasContent("data");
    }

    @Test
    void localCopyIsOneFastCopyRunAndListsWhatItSkipped() {
        Path targetDir = tempDir.resolve("target");
        targetDir.toFile().mkdir();
        Path copied = write("copied.txt", "x");
        Path skipped = write("skipped.txt", "y");
        List<List<String>> commands = new ArrayList<>();
        ExternalToolRunner fastCopy = new ExternalToolRunner(() -> {}) {
            @Override
            public CompletableFuture<List<String>> runExecutable(List<String> command, boolean changesFiles, Set<Integer> accepted) {
                commands.add(command);
                write("target/copied.txt", "x");
                return CompletableFuture.completedFuture(List.of());
            }
        };
        ActionDefinition copy = new ActionDefinition();
        copy.setId("copy");
        copy.setPath("apps/copy/fcp.exe");
        copy.setArgs(List.of("/cmd=${copyMode}", "${selectedFiles}", "/to=${targetFolder}"));
        AppConfig config = new AppConfig();
        config.setActions(List.of(copy));

        ClipboardTransfer.PasteResult result = new FileOperations(new AppRegistry(config), fastCopy)
                .transfer(capture(false, local, tempDir, copied, skipped), local, targetDir.toString(),
                        TransferConflicts.Policy.OVERWRITE_OLDER, List.of());

        assertThat(commands).hasSize(1);
        assertThat(commands.getFirst()).contains("/cmd=update", copied.toString(), skipped.toString(), "/to=" + targetDir + "\\");
        assertThat(result.pasted()).extracting(ClipboardTransfer.Entry::name).containsExactly("copied.txt");
        assertThat(result.failed()).extracting(ClipboardTransfer.Entry::name).containsExactly("skipped.txt");
        assertThat(result.firstFailure()).hasMessageContaining("skipped.txt");
    }

    @Test
    void mkdirAndMkFileCreateInTheFolder() throws Exception {
        operations.mkdir(local, tempDir.toString(), "created");
        operations.mkFile(local, tempDir.toString(), "file.txt");

        assertThat(tempDir.resolve("created")).isDirectory();
        assertThat(tempDir.resolve("file.txt")).isRegularFile();
    }

    @Test
    void mkdirOnFtpCreatesInTheGivenSubfolder() throws Exception {
        List<String> commands = new ArrayList<>();
        FtpFileSystem ftp = new FtpFileSystem(FtpConnectionOptions.builder()
                .host("ftp.example.com").port(21).username("u").password("mock").build()) {
            @Override
            public List<String> runCurl(List<String> command) {
                commands.add(String.join(" ", command));
                return List.of();
            }
        };

        operations.mkdir(ftp, "/pub", "created");

        assertThat(commands).anyMatch(command -> command.endsWith("MKD /pub/created"));
    }

    @Test
    void deleteSkipsTheParentEntryAndReturnsTheFailures() throws Exception {
        Path file = write("gone.txt", "x");
        ClipboardTransfer.State selection = ClipboardTransfer.capture(
                List.of(new FileItem(tempDir, ".."), new FileItem(file)), false, FilesPanesHelper.FocusSide.LEFT, local, tempDir.toString());

        assertThat(operations.delete(local, selection.entries())).isEmpty();
        assertThat(file).doesNotExist();
        assertThat(tempDir).exists();

        VFileSystem failing = mock(VFileSystem.class);
        doThrow(new java.io.IOException("denied")).when(failing).delete("/locked.txt");
        ClipboardTransfer.Entry locked = new ClipboardTransfer.Entry("locked.txt", false, "/locked.txt");
        assertThat(operations.delete(failing, List.of(locked))).containsExactly(locked);
    }

    @Test
    void fastCopyGetsTheTargetFolderWithATrailingBackslash() {
        // Without it FastCopy copies or moves a single folder's contents instead of the folder
        assertThat(FileOperations.toolTarget("D:\\backup")).isEqualTo("D:\\backup\\");
        assertThat(FileOperations.toolTarget("D:\\")).isEqualTo("D:\\");
    }

    @Test
    void copyingAFolderIntoAnArchiveAddsItsNameOnce() throws Exception {
        Path source = Files.createDirectories(tempDir.resolve("src").resolve("dirA"));
        Files.writeString(source.resolve("inner.txt"), "x");
        Path extracted = AppTempDir.createTempDirectory("archive_test_");
        ArchiveFileSystem archive = new ArchiveFileSystem(
                new ArchiveSession("a.zip", extracted, ArchiveMode.READ_WRITE), new ArchiveManager());
        try {
            operations.transfer(capture(false, local, source.getParent(), source), archive, extracted.toString(), OVERWRITE, List.of());

            assertThat(extracted.resolve("dirA").resolve("inner.txt")).hasContent("x");
            assertThat(extracted.resolve("dirA").resolve("dirA")).doesNotExist();
        } finally {
            AppTempDir.release(extracted);
            FileHelper.deleteQuietly(extracted);
        }
    }

    private Path write(String relative, String content) {
        try {
            return Files.writeString(tempDir.resolve(relative), content);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
