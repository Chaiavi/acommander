package org.chaiware.acommander.services;

import org.chaiware.acommander.commands.ExternalToolRunner;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfig;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.services.ClipboardTransfer.Entry;
import org.chaiware.acommander.vfs.FtpConnectionOptions;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ArchiveOperationsTest {

    @TempDir
    Path tempDir;

    private VFileSystem sourceFs;
    private final LocalFileSystem target = new LocalFileSystem("");
    private ArchiveOperations operations;

    @BeforeEach
    void setUp() {
        sourceFs = mock(FtpFileSystem.class);
        AppConfig config = new AppConfig();
        config.setActions(List.of());
        operations = new ArchiveOperations(new AppRegistry(config), new ExternalToolRunner(() -> {}));
    }

    @Test
    void packStagesRemoteItemsUnderTheirOwnNames() throws Exception {
        List<Entry> entries = List.of(new Entry("dir1", true, "/dir1"), new Entry("file1.txt", false, "/file1.txt"));

        // No pack tool configured, so it stops after the downloads
        assertThatThrownBy(() -> operations.pack(sourceFs, entries, target, tempDir.toString(), "test.zip"))
                .hasMessageContaining("Missing action config: pack");

        ArgumentCaptor<String> dirTarget = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fileTarget = ArgumentCaptor.forClass(String.class);
        verify(sourceFs).copy(eq("/dir1"), any(LocalFileSystem.class), dirTarget.capture());
        verify(sourceFs).copy(eq("/file1.txt"), any(LocalFileSystem.class), fileTarget.capture());
        Path stagedDir = Path.of(dirTarget.getValue());
        Path stagedFile = Path.of(fileTarget.getValue());
        assertThat(stagedDir.getFileName()).hasToString("dir1");
        assertThat(stagedFile.getFileName()).hasToString("file1.txt");
        assertThat(stagedDir.getParent()).isEqualTo(stagedFile.getParent());
        assertThat(stagedDir.getParent()).as("the staging folder is cleaned up").doesNotExist();
    }

    @Test
    void packRejectsANameThatLeavesTheStagingFolder() throws Exception {
        assertThatThrownBy(() -> operations.pack(sourceFs, List.of(new Entry("../evil.txt", false, "/../evil.txt")),
                target, tempDir.toString(), "test.zip"))
                .hasMessageContaining("Can't pack an item with this name");
        verify(sourceFs, never()).copy(anyString(), any(), anyString());
    }

    @Test
    void unpackRejectsAFileThatIsNotAnArchive() {
        assertThatThrownBy(() -> operations.unpack(sourceFs, new Entry("notes.txt", false, "/notes.txt"), target, tempDir.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a supported archive");
    }

    @Test
    void packIntoAnFtpPaneReturnsOnlyAfterTheUpload() throws Exception {
        Path local = Files.writeString(tempDir.resolve("a.txt"), "x");
        List<String> uploads = new ArrayList<>();
        FtpFileSystem ftp = new FtpFileSystem(FtpConnectionOptions.builder().host("ftp.example.com").username("u").password("mock").build()) {
            @Override
            public List<String> runCurl(List<String> command) {
                uploads.add(String.join(" ", command));
                return List.of();
            }
        };
        ActionDefinition pack = new ActionDefinition();
        pack.setId("pack");
        pack.setPath("apps/pack_unpack/7zG.exe");
        pack.setArgs(List.of("a", "${archiveFile}", "${selectedFiles}"));
        AppConfig config = new AppConfig();
        config.setActions(List.of(pack));
        ExternalToolRunner sevenZip = new ExternalToolRunner(() -> {}) {
            @Override
            public CompletableFuture<List<String>> runExecutable(List<String> command, boolean changesFiles, Set<Integer> accepted) {
                try {
                    Files.writeString(Path.of(command.get(2)), "zip");
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                return CompletableFuture.completedFuture(List.of());
            }
        };

        new ArchiveOperations(new AppRegistry(config), sevenZip)
                .pack(new LocalFileSystem(""), List.of(new Entry("a.txt", false, local.toString())), ftp, "/pub", "a.zip");

        assertThat(uploads).anyMatch(command -> command.contains("-T") && command.endsWith("/pub/a.zip"));
    }
}
