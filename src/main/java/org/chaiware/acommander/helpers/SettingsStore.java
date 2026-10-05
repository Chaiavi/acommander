package org.chaiware.acommander.helpers;

import org.chaiware.acommander.vfs.FtpConnectionOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;

/** The user's settings file ({@code config/acommander.properties}): typed access and a save that keeps the old file if it fails. */
public class SettingsStore {
    private static final String LEFT_FOLDER = "left_folder";
    private static final String RIGHT_FOLDER = "right_folder";
    private static final String THEME_MODE = "theme_mode";
    private static final String LAST_SELECTION_PATTERN = "last_selection_pattern";
    private static final String BOOKMARK_PREFIX = "bookmark.";
    private static final String FTP_PREFIX = "ftp.";

    private final Path file;
    private final Properties properties = new Properties();

    public SettingsStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /**
     * Reads the file; no file means defaults. An unreadable file is moved to {@code <name>.unreadable}, so the next
     * save can't overwrite it, and the exception says where it went.
     */
    public void load() throws IOException {
        properties.clear();
        if (!Files.exists(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException | IllegalArgumentException e) {
            properties.clear();
            Path aside = file.resolveSibling(file.getFileName() + ".unreadable");
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("Settings could not be read (" + e.getMessage() + "). The file was moved to "
                    + aside + " and ACommander starts with default settings.", e);
        }
    }

    /** Writes a sibling temp file, then moves it over the old one. */
    public void save() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        properties.store(out, null); // the stream form escapes non-Latin-1 characters (Hebrew paths) as unicode escapes
        String text = out.toString(StandardCharsets.ISO_8859_1);
        String withoutTimestamp = text.substring(text.indexOf('\n') + 1);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, withoutTimestamp, StandardCharsets.ISO_8859_1);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public String folder(FilesPanesHelper.FocusSide side) {
        return properties.getProperty(side == LEFT ? LEFT_FOLDER : RIGHT_FOLDER);
    }

    public void setFolder(FilesPanesHelper.FocusSide side, String path) {
        properties.setProperty(side == LEFT ? LEFT_FOLDER : RIGHT_FOLDER, path);
    }

    public String themeMode() {
        return properties.getProperty(THEME_MODE);
    }

    public void setThemeMode(String themeMode) {
        properties.setProperty(THEME_MODE, themeMode);
    }

    public String lastSelectionPattern() {
        return properties.getProperty(LAST_SELECTION_PATTERN, "*.*");
    }

    public void setLastSelectionPattern(String pattern) {
        properties.setProperty(LAST_SELECTION_PATTERN, pattern);
    }

    /** Bookmark name → folder. */
    public Map<String, String> bookmarks() {
        Map<String, String> bookmarks = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(BOOKMARK_PREFIX)) {
                String name = key.substring(BOOKMARK_PREFIX.length()).trim();
                String path = properties.getProperty(key, "").trim();
                if (!name.isEmpty() && !path.isEmpty()) {
                    bookmarks.put(name, path);
                }
            }
        }
        return bookmarks;
    }

    public void setBookmarks(Map<String, String> bookmarks) {
        properties.keySet().removeIf(key -> key.toString().startsWith(BOOKMARK_PREFIX));
        bookmarks.forEach((name, path) -> properties.setProperty(BOOKMARK_PREFIX + name, path));
    }

    /** Saved FTP connections by name; the password is stored as plain text. */
    public Map<String, FtpConnectionOptions> ftpConnections() {
        Map<String, FtpConnectionOptions.FtpConnectionOptionsBuilder> builders = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(FTP_PREFIX)) {
                continue;
            }
            String sub = key.substring(FTP_PREFIX.length());
            int lastDot = sub.lastIndexOf('.');
            if (lastDot <= 0) {
                continue;
            }
            String name = sub.substring(0, lastDot);
            String value = properties.getProperty(key);
            FtpConnectionOptions.FtpConnectionOptionsBuilder builder =
                    builders.computeIfAbsent(name, n -> FtpConnectionOptions.builder().name(n));
            switch (sub.substring(lastDot + 1)) {
                case "host" -> builder.host(value);
                case "port" -> {
                    try {
                        builder.port(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        // keep the default port
                    }
                }
                case "username" -> builder.username(value);
                case "password" -> builder.password(value);
                case "trustAnyCertificate" -> builder.trustAnyCertificate(Boolean.parseBoolean(value));
                case "protocol" -> {
                    try {
                        builder.protocol(FtpConnectionOptions.Protocol.valueOf(value));
                    } catch (IllegalArgumentException ignored) {
                        // keep the default protocol
                    }
                }
                default -> { }
            }
        }
        Map<String, FtpConnectionOptions> connections = new LinkedHashMap<>();
        builders.forEach((name, builder) -> connections.put(name, builder.build()));
        return connections;
    }

    public void setFtpConnections(Map<String, FtpConnectionOptions> connections) {
        properties.keySet().removeIf(key -> key.toString().startsWith(FTP_PREFIX));
        connections.forEach((name, options) -> {
            String prefix = FTP_PREFIX + name + ".";
            properties.setProperty(prefix + "host", options.getHost());
            properties.setProperty(prefix + "port", String.valueOf(options.getPort()));
            properties.setProperty(prefix + "username", options.getUsername());
            properties.setProperty(prefix + "password", options.getPassword());
            properties.setProperty(prefix + "protocol", options.getProtocol().name());
            properties.setProperty(prefix + "trustAnyCertificate", String.valueOf(options.isTrustAnyCertificate()));
        });
    }
}
