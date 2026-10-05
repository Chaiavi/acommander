package org.chaiware.acommander.vfs;

import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.ArchiveManager;
import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.ArchiveSession;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveFileSystemTest {

    @Test
    void firstChangeKeepsTheExtractedFolderAcrossExits() throws IOException {
        Path extracted = AppTempDir.createTempDirectory("archive_test_");
        Path marker = AppTempDir.root().resolve("KEEP-unsaved-edits.txt");
        ArchiveSession session = new ArchiveSession("photos.zip", extracted, ArchiveMode.READ_WRITE);
        try {
            new ArchiveFileSystem(session, new ArchiveManager()).markModified();

            assertThat(session.isNeedsRepack()).isTrue();
            assertThat(marker).content().contains(extracted.toString());
        } finally {
            AppTempDir.release(extracted);
            Files.deleteIfExists(extracted);
        }
    }

    @Test
    void readOnlyArchiveIsNeverMarked() throws IOException {
        Path extracted = AppTempDir.createTempDirectory("archive_test_");
        ArchiveSession session = new ArchiveSession("photos.rar", extracted, ArchiveMode.READ_ONLY);
        try {
            new ArchiveFileSystem(session, new ArchiveManager()).markModified();

            assertThat(session.isNeedsRepack()).isFalse();
            assertThat(AppTempDir.root().resolve("KEEP-unsaved-edits.txt")).doesNotExist();
        } finally {
            Files.deleteIfExists(extracted);
        }
    }

    @Test
    void closingWaitsUntilARunningOperationReleasesTheArchive() throws Exception {
        Path extracted = AppTempDir.createTempDirectory("archive_test_");
        ArchiveSession root = new ArchiveSession("photos.zip", extracted, ArchiveMode.READ_WRITE);
        ArchiveSession child = root.createChild("sub");
        child.acquire();

        Thread closer = Thread.ofVirtual().start(() -> {
            try {
                new ArchiveManager().closeArchive(root);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        closer.join(300);
        assertThat(closer.isAlive()).as("close waits for the operation").isTrue();
        assertThat(extracted).exists();

        child.release();
        closer.join(5000);
        assertThat(closer.isAlive()).isFalse();
        assertThat(extracted).doesNotExist();
    }
}
