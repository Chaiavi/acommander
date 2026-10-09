package org.chaiware.acommander.tools;

import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.ToolDefinition;
import org.chaiware.acommander.helpers.AppPaths;
import org.chaiware.acommander.helpers.FileHelper;
import org.chaiware.acommander.services.ToolUpdateService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * For the developer, run by the build's upgradeTools task: puts the newest upstream version of bundled tools under
 * apps/ and sets it in apps.json. It replaces each of the tool's files with the file of the same name in the download
 * ({@code upstream.files} names them when they differ). The build's tool tests then decide whether to keep it.
 */
public final class ToolUpgrader {

    interface Downloader {
        void download(URI uri, Path target) throws IOException;
    }

    private ToolUpgrader() {
    }

    /** {@code args[0]}: tool ids separated by commas; none means every tool with a newer release. Exits 1 if one failed. */
    public static void main(String[] args) throws IOException {
        Set<String> wanted = args.length == 0 ? Set.of() : Arrays.stream(args[0].split(","))
                .map(String::trim).filter(id -> !id.isEmpty()).collect(Collectors.toSet());
        Path root = AppPaths.root();
        List<ToolDefinition> tools = new AppConfigLoader().load(AppPaths.config("apps.json")).getTools();
        Map<String, String> hashes = ToolUpdateService.parseHashes(Files.readString(BundledTool.TOOL_HASHES.path()));
        ToolUpstreamCheck.Fetcher fetcher = ToolUpstreamCheck.http();
        Downloader downloader = http();
        boolean failed = false;
        for (ToolDefinition tool : tools) {
            if (!wanted.isEmpty() ? !wanted.contains(tool.getId()) : tool.getUpstream() == null) {
                continue;
            }
            ToolUpstreamCheck.Result result = ToolUpstreamCheck.check(tool, fetcher);
            String line;
            if (result.error() != null) {
                line = "FAILED " + tool.getId() + ": " + result.error();
                failed = true;
            } else if (!result.isNewer()) {
                line = "UP TO DATE " + tool.getId() + " " + tool.getVersion();
            } else {
                try {
                    upgrade(root, tool, result, hashes, downloader);
                    line = "UPGRADED " + tool.getId() + " " + tool.getVersion() + " -> " + result.version();
                } catch (IOException | RuntimeException e) {
                    line = "FAILED " + tool.getId() + ": " + e.getMessage();
                    failed = true;
                }
            }
            System.out.println(line);
        }
        if (failed) {
            System.exit(1);
        }
    }

    static void upgrade(Path root, ToolDefinition tool, ToolUpstreamCheck.Result result, Map<String, String> hashes,
                        Downloader downloader) throws IOException {
        Path appsJson = root.resolve("config/apps.json");
        if (tool.getRelease() != null) {
            // ffmpeg is too big for git: the build downloads the zip pinned in build.gradle
            if (result.sha256() == null) {
                throw new IOException("GitHub published no SHA-256 for " + result.download());
            }
            Path gradle = root.resolve("build.gradle");
            Files.writeString(gradle, setFfmpegSha256(Files.readString(gradle), result.sha256()));
            Files.writeString(appsJson, setVersion(Files.readString(appsJson), tool, result.version()));
            return;
        }
        if (result.download() == null) {
            throw new IOException("no automatic download; get " + result.version() + " from " + result.page());
        }
        Path work = root.resolve("build").resolve("tool-upgrades").resolve(tool.getId());
        FileHelper.deleteQuietly(work);
        Files.createDirectories(work);
        Path download = work.resolve(fileName(result.download()));
        downloader.download(URI.create(result.download()), download);
        if (result.sha256() != null && !result.sha256().equalsIgnoreCase(ToolUpdateService.sha256(download))) {
            throw new IOException(result.download() + " does not match the SHA-256 GitHub published for it");
        }
        Path unpacked = unpack(download, work.resolve("unpacked"), tool.getUpstream().getExtract());

        Map<Path, Path> copies = new LinkedHashMap<>();
        for (Map.Entry<String, Path> target : targets(root, tool, hashes).entrySet()) {
            Path source = find(unpacked, target.getKey());
            if (source == null) {
                throw new IOException(target.getKey() + " is not in " + download.getFileName());
            }
            copies.put(source, target.getValue());
        }
        for (Map.Entry<Path, Path> copy : copies.entrySet()) {
            Files.createDirectories(copy.getValue().getParent());
            Files.copy(copy.getKey(), copy.getValue(), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(appsJson, setVersion(Files.readString(appsJson), tool, result.version()));
    }

    /** File name → where it goes: the tool's file of that name, or a new file beside the tool's first file. */
    static Map<String, Path> targets(Path root, ToolDefinition tool, Map<String, String> hashes) {
        List<String> owned = hashes.keySet().stream().filter(tool::owns).sorted().toList();
        if (owned.isEmpty()) {
            throw new IllegalStateException(tool.getId() + " has no files in apps/tools.sha256");
        }
        List<String> names = tool.getUpstream().getFiles() != null ? tool.getUpstream().getFiles()
                : owned.stream().map(ToolUpgrader::name).distinct().toList();
        Map<String, Path> targets = new LinkedHashMap<>();
        for (String name : names) {
            String path = owned.stream().filter(file -> name(file).equalsIgnoreCase(name)).findFirst()
                    .orElse(owned.getFirst().substring(0, owned.getFirst().lastIndexOf('/') + 1) + name);
            targets.put(name, root.resolve(path));
        }
        return targets;
    }

    /** The file called {@code name} nearest the top of {@code folder}, or null. */
    static Path find(Path folder, String name) throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().equalsIgnoreCase(name))
                    .min(Comparator.comparingInt(Path::getNameCount))
                    .orElse(null);
        }
    }

    /** Sets {@code version} in the tool's apps.json entry (and in its {@code release}), leaving the rest as written. */
    static String setVersion(String appsJson, ToolDefinition tool, String version) {
        Matcher entry = Pattern.compile("\\{\"id\": \"" + Pattern.quote(tool.getId()) + "\"[^}]*").matcher(appsJson);
        if (!entry.find()) {
            throw new IllegalStateException(tool.getId() + " is not in apps.json");
        }
        String updated = entry.group().replace("\"version\": \"" + tool.getVersion() + "\"", "\"version\": \"" + version + "\"");
        if (tool.getRelease() != null) {
            updated = updated.replace("\"release\": \"" + tool.getRelease() + "\"",
                    "\"release\": \"" + tool.getRelease().replace(tool.getVersion(), version) + "\"");
        }
        if (updated.equals(entry.group())) {
            throw new IllegalStateException(tool.getId() + ": no \"version\": \"" + tool.getVersion() + "\" in apps.json");
        }
        return appsJson.substring(0, entry.start()) + updated + appsJson.substring(entry.end());
    }

    static String setFfmpegSha256(String buildGradle, String sha256) {
        String updated = buildGradle.replaceFirst("def ffmpegZipSha256 = '[0-9a-f]{64}'", "def ffmpegZipSha256 = '" + sha256 + "'");
        if (updated.equals(buildGradle) && !buildGradle.contains(sha256)) {
            throw new IllegalStateException("no ffmpegZipSha256 in build.gradle");
        }
        return updated;
    }

    private static Path unpack(Path download, Path folder, List<String> extractArgs) throws IOException {
        Files.createDirectories(folder);
        List<String> command = new ArrayList<>();
        if (extractArgs != null) {
            Path installer = download.toString().toLowerCase().endsWith(".exe") ? download
                    : Files.move(download, download.resolveSibling(download.getFileName() + ".exe"));
            command.add(installer.toString());
            extractArgs.forEach(arg -> command.add(arg.replace("{dir}", folder.toString())));
        } else {
            command.addAll(List.of(BundledTool.SEVEN_ZIP.path().toString(), "x", "-y", "-o" + folder, download.toString()));
        }
        try {
            ProcessRunner.Result run = ProcessRunner.of(command).mergeStderr().run();
            if (run.exitCode() != 0) {
                throw new IOException("unpacking " + download.getFileName() + " failed (exit " + run.exitCode() + "): "
                        + String.join(" ", run.stdout().subList(Math.max(0, run.stdout().size() - 5), run.stdout().size())));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return folder;
    }

    private static String name(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String fileName(String url) {
        String name = name(URI.create(url).getPath());
        return name.contains(".") ? name : "download.bin";
    }

    private static Downloader http() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        return (uri, target) -> {
            HttpRequest request = HttpRequest.newBuilder(uri).header("User-Agent", "ACommander-build")
                    .timeout(Duration.ofMinutes(10)).build();
            try {
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
                if (response.statusCode() != 200) {
                    throw new IOException(uri + " answered HTTP " + response.statusCode());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
        };
    }
}
