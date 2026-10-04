package org.chaiware.acommander.helpers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppTempDirTest {
    private static final String DEAD_PID = String.valueOf(Long.MAX_VALUE);

    @TempDir
    Path base;

    @Test
    void onlyRootsOfDeadProcessesAreStale() {
        assertThat(AppTempDir.isStaleRoot("acommander-" + DEAD_PID)).isTrue();
        assertThat(AppTempDir.isStaleRoot("acommander-" + ProcessHandle.current().pid())).isFalse();
        assertThat(AppTempDir.isStaleRoot("acommander-audio-123")).isFalse();
        assertThat(AppTempDir.isStaleRoot("something-else")).isFalse();
    }

    @Test
    void deleteStaleRootsKeepsLiveAndForeignFolders() throws IOException {
        Path stale = Files.createDirectory(base.resolve("acommander-" + DEAD_PID));
        Files.writeString(stale.resolve("leftover.tmp"), "x");
        Path live = Files.createDirectory(base.resolve("acommander-" + ProcessHandle.current().pid()));
        Path foreign = Files.createDirectory(base.resolve("acommander-audio-1"));

        AppTempDir.deleteStaleRoots(base);

        assertThat(stale).doesNotExist();
        assertThat(live).exists();
        assertThat(foreign).exists();
    }

    @Test
    void tempFilesLiveUnderTheRoot() throws IOException {
        Path file = AppTempDir.createTempFile("test_", ".tmp");

        assertThat(file.getParent()).isEqualTo(AppTempDir.root());
        Files.delete(file);
    }
}
