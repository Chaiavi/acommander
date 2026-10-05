package org.chaiware.acommander.helpers;

import org.chaiware.acommander.tools.Dpapi;
import org.chaiware.acommander.vfs.FtpConnectionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.RIGHT;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsStoreTest {

    @TempDir
    Path dir;

    @Test
    void everySettingSurvivesSaveAndLoad() throws IOException {
        Path file = dir.resolve("config").resolve("acommander.properties");
        SettingsStore store = new SettingsStore(file, new FakeDpapi());
        store.setFolder(LEFT, "C:\\תיקייה");
        store.setFolder(RIGHT, "D:\\");
        store.setThemeMode("dark");
        store.setLastSelectionPattern("*.jpg");
        store.setBookmarks(Map.of("work", "C:\\work"));
        FtpConnectionOptions ftp = FtpConnectionOptions.builder().name("home").host("nas").port(2121)
                .username("me").password("pw").protocol(FtpConnectionOptions.Protocol.SFTP).trustAnyCertificate(true).build();
        store.setFtpConnections(Map.of("home", ftp));
        store.save();

        SettingsStore loaded = new SettingsStore(file, new FakeDpapi());
        loaded.load();
        Map<String, FtpConnectionOptions> connections = new HashMap<>(loaded.ftpConnections());
        loaded.unlockFtpPasswords(connections);

        assertThat(loaded.folder(LEFT)).isEqualTo("C:\\תיקייה");
        assertThat(loaded.folder(RIGHT)).isEqualTo("D:\\");
        assertThat(loaded.themeMode()).isEqualTo("dark");
        assertThat(loaded.lastSelectionPattern()).isEqualTo("*.jpg");
        assertThat(loaded.bookmarks()).containsExactly(Map.entry("work", "C:\\work"));
        assertThat(connections).containsExactly(Map.entry("home", ftp));
        assertThat(Files.readString(file)).doesNotStartWith("#").doesNotContain("password=");
    }

    @Test
    void ftpPasswordsAreDecryptedOnlyWhenUnlockedAndEncryptedOnlyWhenChanged() throws IOException {
        Path file = dir.resolve("acommander.properties");
        SettingsStore writer = new SettingsStore(file, new FakeDpapi());
        writer.setFtpConnections(Map.of("home", ftp("pw")));
        writer.save();
        FakeDpapi dpapi = new FakeDpapi();
        SettingsStore store = new SettingsStore(file, dpapi);
        store.load();

        Map<String, FtpConnectionOptions> connections = new HashMap<>(store.ftpConnections());
        assertThat(connections.get("home").getPassword()).isNull();
        assertThat(dpapi.unprotectRuns).isZero();

        store.unlockFtpPasswords(connections);
        store.unlockFtpPasswords(connections);
        store.setFtpConnections(connections);
        assertThat(connections.get("home").getPassword()).isEqualTo("pw");
        assertThat(dpapi.unprotectRuns).isEqualTo(1);
        assertThat(dpapi.protectRuns).isZero();

        store.setFtpConnections(Map.of("home", ftp("new")));
        assertThat(dpapi.protectRuns).isEqualTo(1);
    }

    @Test
    void plainPasswordFromAnOlderVersionIsEncryptedOnSave() throws IOException {
        Path file = dir.resolve("acommander.properties");
        Files.writeString(file, "ftp.home.host=nas\nftp.home.username=me\nftp.home.password=old\n");
        SettingsStore store = new SettingsStore(file, new FakeDpapi());
        store.load();

        Map<String, FtpConnectionOptions> connections = store.ftpConnections();
        assertThat(store.hasPlainFtpPasswords()).isTrue();
        assertThat(connections.get("home").getPassword()).isEqualTo("old");

        store.setFtpConnections(connections);
        store.save();
        assertThat(Files.readString(file)).contains("passwordDpapi=").doesNotContain("password=");
    }

    @Test
    void passwordThatCannotBeDecryptedComesBackEmpty() throws IOException {
        Path file = dir.resolve("acommander.properties");
        Files.writeString(file, "ftp.home.host=nas\nftp.home.username=me\nftp.home.passwordDpapi=from-another-pc\n");
        SettingsStore store = new SettingsStore(file, new FakeDpapi());
        store.load();

        Map<String, FtpConnectionOptions> connections = new HashMap<>(store.ftpConnections());
        store.unlockFtpPasswords(connections);

        assertThat(connections.get("home").getPassword()).isEmpty();
    }

    @Test
    void failedEncryptionKeepsTheSavedConnections() throws IOException {
        Path file = dir.resolve("acommander.properties");
        SettingsStore writer = new SettingsStore(file, new FakeDpapi());
        writer.setFtpConnections(Map.of("home", ftp("pw")));
        writer.save();
        Dpapi broken = mock(Dpapi.class);
        when(broken.protect(anyList())).thenThrow(new IOException("no PowerShell"));
        SettingsStore store = new SettingsStore(file, broken);
        store.load();
        store.ftpConnections();

        assertThatThrownBy(() -> store.setFtpConnections(Map.of("work", ftp("secret")))).isInstanceOf(IOException.class);
        store.save();
        assertThat(Files.readString(file)).contains("ftp.home.passwordDpapi=").doesNotContain("work").doesNotContain("secret");
    }

    private static FtpConnectionOptions ftp(String password) {
        return FtpConnectionOptions.builder().name("home").host("nas").username("me").password(password).build();
    }

    /** Reversible stand-in for DPAPI that counts the PowerShell runs the real one would start. */
    private static class FakeDpapi extends Dpapi {
        int protectRuns;
        int unprotectRuns;

        @Override
        public List<String> protect(List<String> texts) {
            protectRuns += texts.isEmpty() ? 0 : 1;
            return texts.stream().map(text -> "enc-" + text).toList();
        }

        @Override
        public List<String> unprotect(List<String> ciphers) {
            unprotectRuns += ciphers.isEmpty() ? 0 : 1;
            return ciphers.stream().map(cipher -> cipher.startsWith("enc-") ? cipher.substring(4) : "").toList();
        }
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
