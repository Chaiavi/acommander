package org.chaiware.acommander.helpers;

import org.chaiware.acommander.vfs.FtpConnectionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.RIGHT;

class SettingsStoreTest {

    @TempDir
    Path dir;

    @Test
    void everySettingSurvivesSaveAndLoad() throws IOException {
        Path file = dir.resolve("config").resolve("acommander.properties");
        SettingsStore store = new SettingsStore(file);
        store.setFolder(LEFT, "C:\\תיקייה");
        store.setFolder(RIGHT, "D:\\");
        store.setThemeMode("dark");
        store.setLastSelectionPattern("*.jpg");
        store.setBookmarks(Map.of("work", "C:\\work"));
        FtpConnectionOptions ftp = FtpConnectionOptions.builder().name("home").host("nas").port(2121)
                .username("me").password("pw").protocol(FtpConnectionOptions.Protocol.SFTP).build();
        store.setFtpConnections(Map.of("home", ftp));
        store.save();

        SettingsStore loaded = new SettingsStore(file);
        loaded.load();

        assertThat(loaded.folder(LEFT)).isEqualTo("C:\\תיקייה");
        assertThat(loaded.folder(RIGHT)).isEqualTo("D:\\");
        assertThat(loaded.themeMode()).isEqualTo("dark");
        assertThat(loaded.lastSelectionPattern()).isEqualTo("*.jpg");
        assertThat(loaded.bookmarks()).containsExactly(Map.entry("work", "C:\\work"));
        assertThat(loaded.ftpConnections()).containsExactly(Map.entry("home", ftp));
        assertThat(Files.readString(file)).doesNotStartWith("#");
    }

    @Test
    void missingFileMeansDefaults() throws IOException {
        SettingsStore store = new SettingsStore(dir.resolve("none.properties"));
        store.load();

        assertThat(store.folder(LEFT)).isNull();
        assertThat(store.lastSelectionPattern()).isEqualTo("*.*");
        assertThat(store.bookmarks()).isEmpty();
    }

    @Test
    void failedSaveKeepsTheOldFile() throws IOException {
        Path file = dir.resolve("acommander.properties");
        Files.writeString(file, "theme_mode=dark\n");
        Files.createDirectory(dir.resolve("acommander.properties.tmp")); // the temp file can't be written
        SettingsStore store = new SettingsStore(file);
        store.load();
        store.setThemeMode("regular");

        assertThatThrownBy(store::save).isInstanceOf(IOException.class);
        assertThat(Files.readString(file)).isEqualTo("theme_mode=dark\n");
    }

    @Test
    void unreadableFileIsMovedAsideNotOverwritten() throws IOException {
        Path file = dir.resolve("acommander.properties");
        Files.writeString(file, "left_folder=C:\\u12\n"); // malformed unicode escape
        SettingsStore store = new SettingsStore(file);

        assertThatThrownBy(store::load).isInstanceOf(IOException.class).hasMessageContaining(".unreadable");
        store.save();

        assertThat(Files.readString(dir.resolve("acommander.properties.unreadable"))).isEqualTo("left_folder=C:\\u12\n");
        assertThat(store.folder(LEFT)).isNull();
    }
}
