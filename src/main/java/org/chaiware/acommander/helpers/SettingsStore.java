package org.chaiware.acommander.helpers;

import org.chaiware.acommander.tools.Dpapi;
import org.chaiware.acommander.vfs.FtpConnectionOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;

/** The user's settings file ({@code config/acommander.properties}): typed access and a save that keeps the old file if it fails. */
public class SettingsStore {
    private static final String LEFT_FOLDER = "left_folder";
    private static final String RIGHT_FOLDER = "right_folder";
    private static final String THEME_MODE = "theme_mode";
    private static final String LAST_SELECTION_PATTERN = "last_selection_pattern";
    private static final String TOOL_UPDATES_AT_START = "tool_updates_at_start";
    private static final String TOOL_UPDATES_CHECKED = "tool_updates_checked";
    private static final String TOOL_UPDATES_SHOWN = "tool_updates_shown";
    private static final String BOOKMARK_PREFIX = "bookmark.";
    private static final String FTP_PREFIX = "ftp.";
    private static final String PASSWORD_DPAPI = "passwordDpapi";

    private final Path file;
    private final Properties properties = new Properties();
    private final Dpapi dpapi;
    /** Connection name → the encrypted password last read or written. */
    private final Map<String, String> savedCiphers = new HashMap<>();
    /** Cipher → its password, so an unchanged password is never decrypted or encrypted again. */
    private final Map<String, String> decrypted = new HashMap<>();
    private boolean plainFtpPasswordsFound;

    public SettingsStore(Path file) {
        this(file, new Dpapi());
    }

    public SettingsStore(Path file, Dpapi dpapi) {
        this.file = file;
        this.dpapi = dpapi;
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

    /** Whether the app looks for tool updates when it starts; on unless the user turned it off. */
    public boolean toolUpdatesAtStart() {
        return !"false".equals(properties.getProperty(TOOL_UPDATES_AT_START));
    }

    public void setToolUpdatesAtStart(boolean atStart) {
        properties.setProperty(TOOL_UPDATES_AT_START, String.valueOf(atStart));
    }

    /** When the start check last ran, in epoch milliseconds; 0 when never. */
    public long toolUpdatesChecked() {
        try {
            return Long.parseLong(properties.getProperty(TOOL_UPDATES_CHECKED, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setToolUpdatesChecked(long epochMillis) {
        properties.setProperty(TOOL_UPDATES_CHECKED, String.valueOf(epochMillis));
    }

    /** The updates the start check last showed ({@code ToolUpdateService.offerKey}), so it doesn't show them again. */
    public String toolUpdatesShown() {
        return properties.getProperty(TOOL_UPDATES_SHOWN, "");
    }

    public void setToolUpdatesShown(String offerKey) {
        properties.setProperty(TOOL_UPDATES_SHOWN, offerKey);
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

    /** Saved FTP connections by name; an encrypted password is null until {@link #unlockFtpPasswords}. */
    public Map<String, FtpConnectionOptions> ftpConnections() {
        savedCiphers.clear();
        plainFtpPasswordsFound = false;
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
                case "password" -> { // written before passwords were encrypted
                    builder.password(value);
                    plainFtpPasswordsFound = true;
                }
                case PASSWORD_DPAPI -> {
                    savedCiphers.put(name, value);
                    builder.password(decrypted.get(value));
                }
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

    /** True when the file holds a plain-text FTP password; saving the connections once encrypts it. */
    public boolean hasPlainFtpPasswords() {
        return plainFtpPasswordsFound;
    }

    /** Decrypts every password still null in {@code connections}, in one run; one that can't be decrypted becomes "". */
    public void unlockFtpPasswords(Map<String, FtpConnectionOptions> connections) throws IOException {
        List<String> locked = connections.entrySet().stream()
                .filter(entry -> entry.getValue().getPassword() == null)
                .map(entry -> savedCiphers.get(entry.getKey()))
                .filter(cipher -> cipher != null && !decrypted.containsKey(cipher))
                .distinct()
                .toList();
        List<String> passwords = dpapi.unprotect(locked);
        for (int i = 0; i < locked.size(); i++) {
            decrypted.put(locked.get(i), passwords.get(i));
        }
        connections.replaceAll((name, options) -> options.getPassword() != null ? options
                : options.toBuilder().password(decrypted.getOrDefault(savedCiphers.get(name), "")).build());
    }

    /**
     * Replaces the saved connections. New or changed passwords are encrypted first, in one run, so a failure leaves the
     * file's connections untouched; a password never unlocked (null) keeps its saved cipher. Never writes plain text.
     */
    public void setFtpConnections(Map<String, FtpConnectionOptions> connections) throws IOException {
        Map<String, String> ciphers = new HashMap<>();
        List<String> names = new ArrayList<>();
        List<String> passwords = new ArrayList<>();
        for (Map.Entry<String, FtpConnectionOptions> entry : connections.entrySet()) {
            String password = entry.getValue().getPassword();
            String saved = savedCiphers.get(entry.getKey());
            if (saved != null && (password == null || password.equals(decrypted.get(saved)))) {
                ciphers.put(entry.getKey(), saved);
            } else if (password != null && !password.isEmpty()) {
                names.add(entry.getKey());
                passwords.add(password);
            }
        }
        List<String> encrypted = dpapi.protect(passwords);
        for (int i = 0; i < names.size(); i++) {
            ciphers.put(names.get(i), encrypted.get(i));
            decrypted.put(encrypted.get(i), passwords.get(i));
        }
        savedCiphers.clear();
        savedCiphers.putAll(ciphers);
        properties.keySet().removeIf(key -> key.toString().startsWith(FTP_PREFIX));
        connections.forEach((name, options) -> {
            String prefix = FTP_PREFIX + name + ".";
            properties.setProperty(prefix + "host", options.getHost());
            properties.setProperty(prefix + "port", String.valueOf(options.getPort()));
            properties.setProperty(prefix + "username", options.getUsername());
            if (ciphers.containsKey(name)) {
                properties.setProperty(prefix + PASSWORD_DPAPI, ciphers.get(name));
            }
            properties.setProperty(prefix + "protocol", options.getProtocol().name());
            properties.setProperty(prefix + "trustAnyCertificate", String.valueOf(options.isTrustAnyCertificate()));
        });
    }
}
