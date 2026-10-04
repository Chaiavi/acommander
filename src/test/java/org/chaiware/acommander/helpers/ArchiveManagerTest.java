package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.ArchiveSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void failedRepackKeepsTheEditsNextToTheArchive() throws IOException {
        Path extracted = extractedFolderWithEdits();
        // The archive's folder does not exist, so the repack cannot even create its temp archive.
        Path archive = tempDir.resolve("gone").resolve("photos.zip");
        ArchiveSession session = new ArchiveSession(archive.toString(), extracted, ArchiveMode.READ_WRITE);
        session.setNeedsRepack(true);

        assertThatThrownBy(() -> new ArchiveManager().closeArchive(session))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("photos.zip")
                .hasMessageContaining("copied to");

        List<Path> recovered;
        try (Stream<Path> siblings = Files.list(archive.getParent())) {
            recovered = siblings.filter(p -> p.getFileName().toString().startsWith("photos.zip.recovered-")).toList();
        }
        assertThat(recovered).hasSize(1);
        assertThat(recovered.getFirst().resolve("sub").resolve("edited.txt")).hasContent("my edit");
        assertThat(extracted).doesNotExist();
    }

    @Test
    void unchangedArchiveJustDeletesTheExtractedFolder() throws IOException {
        Path extracted = extractedFolderWithEdits();
        ArchiveSession session = new ArchiveSession(tempDir.resolve("a.zip").toString(), extracted, ArchiveMode.READ_WRITE);

        new ArchiveManager().closeArchive(session);

        assertThat(extracted).doesNotExist();
    }

    private Path extractedFolderWithEdits() throws IOException {
        Path extracted = Files.createDirectories(tempDir.resolve("extracted").resolve("sub"));
        Files.writeString(extracted.resolve("edited.txt"), "my edit");
        return extracted.getParent();
    }
}
