package org.chaiware.acommander.services;

import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.ToolDefinition;
import org.chaiware.acommander.helpers.AppTempDir;
import org.chaiware.acommander.helpers.BugReportUrl;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.tools.BundledTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tool Updates: compares the tools under apps/ with the ones on this project's main branch and downloads what changed.
 * Files come only from this project on GitHub, and each must match the SHA-256 the build published beside it.
 */
public class ToolUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(ToolUpdateService.class);
    public static final String RAW_MAIN = BugReportUrl.PROJECT_URL.replace("https://github.com/", "https://raw.githubusercontent.com/") + "/main/";
    private static final String RELEASE_DOWNLOADS = BugReportUrl.PROJECT_URL + "/releases/download/";
    // GitHub answers release downloads with a redirect to one of its asset hosts.
    private static final Set<String> HOSTS = Set.of("raw.githubusercontent.com", "github.com",
            "objects.githubusercontent.com", "release-assets.githubusercontent.com");
    private static final String ASIDE_SUFFIX = ".acommander-old";
    private static final Pattern HASH_LINE = Pattern.compile("^([0-9a-fA-F]{64})\\s+(.+)$");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** Opens a download; the app uses {@link #https()}, tests pass local bytes. */
    public interface Source {
        InputStream open(URI uri) throws IOException;
    }

    public enum State {
        UP_TO_DATE("Up to Date"),
        UPDATE_AVAILABLE("Update Available"),
        MISSING_FILES("Missing Files"),
        NEEDS_NEWER_APP("Needs Newer ACommander");

        public final String label;

        State(String label) {
            this.label = label;
        }
    }

    /** A file to write ({@code sha256} set) or to delete ({@code sha256} null). */
    public record FileChange(String path, String sha256) {
    }

    /** One tool as main has it, against this copy: {@code installed} is empty when this copy lacks the tool. */
    public record ToolStatus(ToolDefinition tool, String installed, String available, State state, List<FileChange> changes) {
        public boolean canUpdate() {
            return state == State.UPDATE_AVAILABLE || state == State.MISSING_FILES;
        }
    }

    private final Path root;
    private final Path appsDir;
    private final Source source;
    private final String appVersion;

    public ToolUpdateService(Path root, Source source, String appVersion) {
        this.root = root.toAbsolutePath().normalize();
        this.appsDir = this.root.resolve(BundledTool.TOOL_HASHES.relativePath()).getParent();
        this.source = source;
        this.appVersion = appVersion;
    }

    /** Reads main's tool list and file hashes, then compares them with the files here. */
    public List<ToolStatus> check(List<ToolDefinition> localTools) throws IOException {
        List<ToolDefinition> remoteTools;
        try (InputStream in = source.open(URI.create(RAW_MAIN + "config/apps.json"))) {
            remoteTools = new AppConfigLoader().load(in).getTools();
        }
        Map<String, String> remoteHashes;
        try (InputStream in = source.open(URI.create(RAW_MAIN + BundledTool.TOOL_HASHES.relativePath()))) {
            remoteHashes = parseHashes(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        Map<String, String> shipped = readLocalHashes();
        Map<String, ToolDefinition> local = localTools.stream()
                .collect(Collectors.toMap(ToolDefinition::getId, tool -> tool, (a, b) -> a));
        List<ToolStatus> statuses = new ArrayList<>();
        for (ToolDefinition remote : remoteTools) {
            statuses.add(status(remote, local.get(remote.getId()), shipped, remoteHashes));
        }
        return statuses;
    }

    ToolStatus status(ToolDefinition remote, ToolDefinition here, Map<String, String> shipped,
                      Map<String, String> remoteHashes) throws IOException {
        String localVersion = here == null || here.getVersion() == null ? "" : here.getVersion();
        String available = remote.getVersion();
        if (remote.getMinAppVersion() != null && compareVersions(appVersion, remote.getMinAppVersion()) < 0) {
            return new ToolStatus(remote, localVersion, available, State.NEEDS_NEWER_APP, List.of());
        }
        // A developer copy ahead of main (tool replaced, not pushed yet) must not be offered the older files.
        if (!localVersion.isEmpty() && compareVersions(localVersion, available) > 0) {
            return new ToolStatus(remote, localVersion, available, State.UP_TO_DATE, List.of());
        }
        List<FileChange> changes = plan(remote, shipped, remoteHashes);
        if (changes.isEmpty()) {
            return new ToolStatus(remote, available, available, State.UP_TO_DATE, changes);
        }
        boolean onlyMissing = true;
        for (FileChange change : changes) {
            onlyMissing &= change.sha256() != null && !Files.exists(resolve(change.path()));
        }
        State state = onlyMissing && localVersion.equals(available) ? State.MISSING_FILES : State.UPDATE_AVAILABLE;
        return new ToolStatus(remote, localVersion, available, state, changes);
    }

    /**
     * The files of {@code tool} to write or delete. A file changes when it is missing, or when main changed it and this
     * copy differs from main's; a file main didn't change is left alone, so a tool's own settings file is never reset.
     * A file main dropped is deleted only while it is still the one this copy shipped.
     */
    List<FileChange> plan(ToolDefinition tool, Map<String, String> shipped, Map<String, String> remote) throws IOException {
        List<FileChange> changes = new ArrayList<>();
        for (Map.Entry<String, String> entry : remote.entrySet()) {
            if (!tool.owns(entry.getKey())) {
                continue;
            }
            Path file = resolve(entry.getKey());
            if (!Files.exists(file)) {
                changes.add(new FileChange(entry.getKey(), entry.getValue()));
            } else if (!entry.getValue().equalsIgnoreCase(shipped.getOrDefault(entry.getKey(), ""))
                    && !entry.getValue().equalsIgnoreCase(sha256(file))) {
                changes.add(new FileChange(entry.getKey(), entry.getValue()));
            }
        }
        for (Map.Entry<String, String> entry : shipped.entrySet()) {
            if (!tool.owns(entry.getKey()) || remote.containsKey(entry.getKey())) {
                continue;
            }
            Path file = resolve(entry.getKey());
            if (Files.isRegularFile(file) && entry.getValue().equalsIgnoreCase(sha256(file))) {
                changes.add(new FileChange(entry.getKey(), null));
            }
        }
        return changes;
    }

    /**
     * Downloads every changed file of the tool, checks its SHA-256, then swaps all of them in. If any step fails, the
     * old files stay (or are put back) and the exception says why.
     */
    // Synchronized: Update All runs several tools at once, and each rewrites apps/tools.sha256.
    public synchronized void update(ToolStatus status) throws IOException {
        Map<FileChange, Path> downloads = new LinkedHashMap<>();
        try {
            for (FileChange change : status.changes()) {
                resolve(change.path());
                if (change.sha256() == null) {
                    continue;
                }
                Path temp = AppTempDir.createTempFile("tool-", ".download");
                downloads.put(change, temp);
                String actual = download(downloadUri(status.tool(), change.path()), temp);
                if (!actual.equalsIgnoreCase(change.sha256())) {
                    throw new IOException(change.path() + " does not match the SHA-256 published for it. "
                            + "GitHub can serve the old file for a few minutes after a change; try again later.");
                }
            }
            apply(status.changes(), downloads);
        } finally {
            downloads.values().forEach(FileHelper::deleteQuietly);
        }
    }

    private void apply(List<FileChange> changes, Map<FileChange, Path> downloads) throws IOException {
        List<Path> movedAside = new ArrayList<>();
        List<Path> written = new ArrayList<>();
        try {
            for (FileChange change : changes) {
                Path target = resolve(change.path());
                Path aside = aside(target);
                Files.deleteIfExists(aside);
                if (Files.exists(target)) {
                    // Windows lets a running exe be renamed but not overwritten or deleted.
                    Files.move(target, aside);
                    movedAside.add(target);
                }
                if (change.sha256() != null) {
                    Files.createDirectories(target.getParent());
                    Files.copy(downloads.get(change), target);
                    written.add(target);
                }
            }
        } catch (IOException e) {
            written.forEach(FileHelper::deleteQuietly);
            for (Path target : movedAside) {
                try {
                    Files.move(aside(target), target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException restoreFailed) {
                    logger.error("Could not put back {}", target, restoreFailed);
                }
            }
            throw new IOException("Could not replace the tool's files (" + e.getMessage()
                    + "). Close the tool if it is running and try again.", e);
        }
        for (Path target : movedAside) {
            try {
                Files.deleteIfExists(aside(target));
            } catch (IOException stillRunning) {
                logger.info("Leaving {} until the tool exits", aside(target));
            }
        }
        Map<String, String> shipped = readLocalHashes();
        for (FileChange change : changes) {
            if (change.sha256() == null) {
                shipped.remove(change.path());
            } else {
                shipped.put(change.path(), change.sha256().toLowerCase());
            }
        }
        writeLocalHashes(shipped);
    }

    private static Path aside(Path target) {
        return target.resolveSibling(target.getFileName() + ASIDE_SUFFIX);
    }

    URI downloadUri(ToolDefinition tool, String path) {
        if (tool.getRelease() != null && !tool.getRelease().isBlank()) {
            return URI.create(RELEASE_DOWNLOADS + encode(tool.getRelease()) + "/" + encode(path.substring(path.lastIndexOf('/') + 1)));
        }
        return URI.create(RAW_MAIN + Arrays.stream(path.split("/")).map(ToolUpdateService::encode).collect(Collectors.joining("/")));
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** {@code path} from a hash list or apps.json as a file under apps/; anything outside apps/ is refused. */
    Path resolve(String path) throws IOException {
        try {
            Path target = root.resolve(path).normalize();
            if (path.contains("\\") || !target.startsWith(appsDir) || target.equals(appsDir)) {
                throw new IOException("Refusing a tool file outside apps/: " + path);
            }
            return target;
        } catch (InvalidPathException e) {
            throw new IOException("Refusing a tool file with an invalid name: " + path, e);
        }
    }

    private String download(URI uri, Path target) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream in = new DigestInputStream(source.open(uri), digest);
             OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Map<String, String> readLocalHashes() throws IOException {
        Path file = root.resolve(BundledTool.TOOL_HASHES.relativePath());
        return Files.exists(file) ? parseHashes(Files.readString(file)) : new TreeMap<>();
    }

    private void writeLocalHashes(Map<String, String> hashes) throws IOException {
        Path file = root.resolve(BundledTool.TOOL_HASHES.relativePath());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, formatHashes(hashes));
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Path → SHA-256 from {@code <sha256>  <path>} lines (the format of sha256sum and of the build's toolHashes task). */
    public static Map<String, String> parseHashes(String text) {
        Map<String, String> hashes = new TreeMap<>();
        for (String line : text.split("\\R")) {
            Matcher matcher = HASH_LINE.matcher(line.trim());
            if (matcher.matches()) {
                hashes.put(matcher.group(2).trim(), matcher.group(1).toLowerCase());
            }
        }
        return hashes;
    }

    static String formatHashes(Map<String, String> hashes) {
        StringBuilder text = new StringBuilder();
        new TreeMap<>(hashes).forEach((path, hash) -> text.append(hash).append("  ").append(path).append('\n'));
        return text.toString();
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Compares the numbers in two versions in order ({@code v26.08r6282} is 26, 8, 6282). A version without numbers
     * ({@code dev}) compares equal to anything.
     */
    public static int compareVersions(String a, String b) {
        List<Long> left = numbers(a);
        List<Long> right = numbers(b);
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            int compared = Long.compare(i < left.size() ? left.get(i) : 0, i < right.size() ? right.get(i) : 0);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static List<Long> numbers(String version) {
        List<Long> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(Objects.toString(version, ""));
        while (matcher.find()) {
            numbers.add(Long.parseLong(matcher.group()));
        }
        return numbers;
    }

    /** What the updatable tools offer, so the start check shows the dialog only when something new appeared. */
    public static String offerKey(List<ToolStatus> statuses) {
        return statuses.stream()
                .filter(ToolStatus::canUpdate)
                .map(status -> status.tool().getId() + ":" + status.available() + ":" + status.state())
                .sorted()
                .collect(Collectors.joining(","));
    }

    /** The start check runs when it is on and the last one is a day old. */
    public static boolean startCheckDue(boolean enabled, long lastCheckedMillis, long nowMillis) {
        return enabled && nowMillis - lastCheckedMillis >= Duration.ofDays(1).toMillis();
    }

    /** HTTPS downloads from this project's GitHub hosts only; a redirect to any other host is refused. */
    public static Source https() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        return uri -> {
            requireAllowedHost(uri);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", "ACommander")
                    .timeout(Duration.ofMinutes(10))
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while downloading " + uri, e);
            }
            try {
                requireAllowedHost(response.uri());
                if (response.statusCode() != 200) {
                    throw new IOException(uri + " answered HTTP " + response.statusCode());
                }
            } catch (IOException e) {
                response.body().close();
                throw e;
            }
            return response.body();
        };
    }

    static void requireAllowedHost(URI uri) throws IOException {
        if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost())) {
            throw new IOException("Refusing to download from " + uri);
        }
    }
}
