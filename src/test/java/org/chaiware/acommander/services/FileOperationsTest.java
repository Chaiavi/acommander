package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileOperationsTest {

    @TempDir
    Path tempDir;

    private final FilesPanesHelper panes = mock(FilesPanesHelper.class);
    private FileOperations operations;

    @BeforeEach
    void setUp() {
        VFileSystem localFs = new LocalFileSystem("");
        when(panes.getFocusedFileSystem()).thenReturn(localFs);
        when(panes.getUnfocusedFileSystem()).thenReturn(localFs);
        AppConfig config = new AppConfig();
        config.setActions(List.of());
        operations = new FileOperations(panes, new AppRegistry(config), new ExternalToolRunner(() -> {}));
    }

    @Test
    void renameRenamesSingleFile() throws Exception {
        Path file = Files.writeString(tempDir.resolve("old.txt"), "rename");

        operations.rename(List.of(new FileItem(file)), "new.txt");

        assertThat(file).doesNotExist();
        assertThat(tempDir.resolve("new.txt")).exists();
        verify(panes).refreshFileListViews();
    }

    @Test
    void moveOnTheSameDriveMovesTheFile() throws Exception {
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));
        Path file = Files.writeString(tempDir.resolve("move.txt"), "move");

        operations.move(new FileItem(file), targetDir.toString());

        assertThat(file).doesNotExist();
        assertThat(targetDir.resolve("move.txt")).exists();
        verify(panes).refreshFileListViews();
    }

    @Test
    void moveBatchMovesEveryFile() throws Exception {
        Path targetDir = Files.createDirectory(tempDir.resolve("target"));
        Path first = Files.writeString(tempDir.resolve("one.txt"), "first");
        Path second = Files.writeString(tempDir.resolve("two.txt"), "second");

        operations.moveBatch(List.of(new FileItem(first), new FileItem(second)), targetDir.toString());

        assertThat(first).doesNotExist();
        assertThat(second).doesNotExist();
        assertThat(targetDir.resolve("one.txt")).hasContent("first");
        assertThat(targetDir.resolve("two.txt")).hasContent("second");
    }

    @Test
    void mkdirAndMkFileCreateInTheFolder() throws Exception {
        operations.mkdir(tempDir.toString(), "created");
        operations.mkFile(tempDir.toString(), "file.txt");

        assertThat(tempDir.resolve("created")).isDirectory();
        assertThat(tempDir.resolve("file.txt")).isRegularFile();
        verify(panes, times(2)).refreshFileListViews();
    }

    @Test
    void mkdirOnFtpCreatesInTheCurrentSubfolder() throws Exception {
        List<String> commands = new ArrayList<>();
        FtpFileSystem ftp = new FtpFileSystem(FtpConnectionOptions.builder()
                .host("ftp.example.com").port(21).username("u").password("mock").build()) {
            @Override
            public List<String> runCurl(List<String> command) {
                commands.add(String.join(" ", command));
                return List.of();
            }
        };
        ftp.listContents("/pub");
        when(panes.getFocusedFileSystem()).thenReturn(ftp);

        operations.mkdir("/pub", "created");

        assertThat(commands).anyMatch(command -> command.endsWith("MKD /pub/created"));
    }

    @Test
    void deleteSkipsTheParentEntry() throws Exception {
        Path file = Files.writeString(tempDir.resolve("gone.txt"), "x");

        operations.delete(List.of(new FileItem(tempDir, ".."), new FileItem(file)));

        assertThat(file).doesNotExist();
        assertThat(tempDir).exists();
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
        when(panes.getUnfocusedFileSystem()).thenReturn(archive);
        try {
            operations.copy(new FileItem(source), extracted.toString());

            assertThat(extracted.resolve("dirA").resolve("inner.txt")).hasContent("x");
            assertThat(extracted.resolve("dirA").resolve("dirA")).doesNotExist();
        } finally {
            AppTempDir.release(extracted);
            FileHelper.deleteQuietly(extracted);
        }
    }
}
