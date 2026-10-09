package org.chaiware.acommander.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.ToolDefinition;
import org.chaiware.acommander.helpers.AppPaths;
import org.chaiware.acommander.services.ToolUpdateService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * For the developer, run by the build's checkToolUpdates task: asks each tool's upstream (apps.json {@code upstream})
 * for its latest version and prints every tool's current and latest version, with release dates where GitHub has
 * them, and a direct download for the newer ones. Users never run this; they update from this project's own GitHub
 * through {@link ToolUpdateService}.
 */
public final class ToolUpstreamCheck {
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * A tool's upstream answer; {@code latest} is null when there is no upstream or the check failed ({@code error}).
     * {@code download} is the file to fetch (null when only {@code page} can be visited), {@code sha256} what GitHub
     * published for it.
     */
    public record Result(ToolDefinition tool, String currentDate, String latest, String latestDate, String download,
                         String sha256, String page, String error) {
        public boolean isNewer() {
            return latest != null && ToolUpdateService.compareVersions(latest, tool.getVersion()) > 0;
        }

        /** The latest version as apps.json stores it: the tag without a leading v. */
        public String version() {
            return latest == null ? null : bare(latest);
        }
    }

    static String bare(String tag) {
        return tag.replaceFirst("^[vV](?=\\d)", "");
    }

    private ToolUpstreamCheck() {
    }

    public static void main(String[] args) throws IOException {
        List<ToolDefinition> tools = new AppConfigLoader().load(AppPaths.config("apps.json")).getTools();
        Fetcher fetcher = http();
        List<Result> results = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            results.add(check(tool, fetcher));
        }
        System.out.print(report(results));
    }

    /** Pages and the GitHub API over HTTPS; GITHUB_TOKEN, when set, lifts GitHub's 60 requests an hour. */
    static Fetcher http() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        String token = System.getenv("GITHUB_TOKEN");
        return uri -> get(client, uri, token);
    }

    interface Fetcher {
        String get(URI uri) throws IOException;
    }

    static Result check(ToolDefinition tool, Fetcher fetcher) {
        ToolDefinition.Upstream upstream = tool.getUpstream();
        if (upstream == null) {
            return new Result(tool, null, null, null, null, null, null, null);
        }
        try {
            if (upstream.getGithub() != null) {
                return fromReleases(tool, fetcher.get(URI.create(
                        "https://api.github.com/repos/" + upstream.getGithub() + "/releases?per_page=30")));
            }
            String page = fetcher.get(URI.create(upstream.getPage()));
            String latest = latestOnPage(page, upstream.getPattern());
            if (latest == null) {
                return new Result(tool, null, null, null, null, null, upstream.getPage(), "no version matching " + upstream.getPattern());
            }
            return new Result(tool, null, latest, null, pageDownload(upstream, page, bare(latest)), null, upstream.getPage(), null);
        } catch (IOException | RuntimeException e) {
            return new Result(tool, null, null, null, null, null, null, e.getMessage());
        }
    }

    /** The download of a page tool: its {@code url} with the version filled in, or the link {@code downloadPattern} finds. */
    static String pageDownload(ToolDefinition.Upstream upstream, String page, String version) {
        if (upstream.getUrl() != null) {
            return upstream.getUrl().replace("{version}", version);
        }
        if (upstream.getDownloadPattern() != null) {
            Matcher link = Pattern.compile(upstream.getDownloadPattern()).matcher(page);
            if (link.find()) {
                return URI.create(upstream.getPage()).resolve(link.group(1).replace("&amp;", "&")).toString();
            }
        }
        return null;
    }

    /** From GitHub's release list (newest first): the latest full release, its download, and the current version's date. */
    static Result fromReleases(ToolDefinition tool, String releasesJson) throws IOException {
        JsonNode latest = null;
        String currentDate = null;
        for (JsonNode release : JSON.readTree(releasesJson)) {
            if (release.path("draft").asBoolean() || release.path("prerelease").asBoolean()) {
                continue;
            }
            if (latest == null) {
                latest = release;
            }
            if (currentDate == null && ToolUpdateService.compareVersions(release.path("tag_name").asText(), tool.getVersion()) == 0) {
                currentDate = date(release);
            }
        }
        if (latest == null) {
            throw new IOException("no full release on GitHub");
        }
        String download = null;
        String sha256 = null;
        String assetPattern = tool.getUpstream().getAsset();
        if (assetPattern != null) {
            Pattern asset = Pattern.compile(assetPattern);
            for (JsonNode file : latest.path("assets")) {
                if (asset.matcher(file.path("name").asText()).find()) {
                    download = file.path("browser_download_url").asText();
                    String digest = file.path("digest").asText("");
                    sha256 = digest.startsWith("sha256:") ? digest.substring("sha256:".length()) : null;
                    break;
                }
            }
        }
        return new Result(tool, currentDate, latest.path("tag_name").asText(), date(latest), download, sha256,
                latest.path("html_url").asText(), null);
    }

    private static String date(JsonNode release) {
        String published = release.path("published_at").asText();
        return published.length() >= 10 ? published.substring(0, 10) : null;
    }

    /** The highest version that {@code pattern} (group 1) finds on the page, or null. */
    static String latestOnPage(String page, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(page);
        String latest = null;
        while (matcher.find()) {
            if (latest == null || ToolUpdateService.compareVersions(matcher.group(1), latest) > 0) {
                latest = matcher.group(1);
            }
        }
        return latest;
    }

    /** Every tool, newer ones first (marked *): current and latest version with dates, and a download for the newer. */
    static String report(List<Result> results) {
        List<Result> rows = results.stream()
                .sorted(Comparator.comparing((Result result) -> !result.isNewer())
                        .thenComparing(result -> result.tool().getName().toLowerCase()))
                .toList();
        long newer = rows.stream().filter(Result::isNewer).count();
        StringBuilder text = new StringBuilder(newer == 0 ? "Bundled tools: all up to date.\n"
                : "Bundled tools: " + newer + " with a newer release (*). To update one: replace its files under apps/, "
                + "set its version in config/apps.json, build, commit and push.\n");
        text.append(String.format("    %-36s %-24s %-24s %s", "Tool", "Current", "Latest", "Download").stripTrailing()).append('\n');
        for (Result result : rows) {
            String latest = result.error() != null ? "could not check: " + result.error()
                    : result.latest() == null ? "(no upstream)" : withDate(result.latest(), result.latestDate());
            text.append(String.format("  %s %-36s %-24s %-24s %s", result.isNewer() ? "*" : " ", result.tool().getName(),
                    withDate(result.tool().getVersion(), result.currentDate()), latest,
                    result.isNewer() ? (result.download() != null ? result.download() : result.page()) : "").stripTrailing()).append('\n');
        }
        return text.toString();
    }

    private static String withDate(String version, String date) {
        return date == null ? version : version + " (" + date + ")";
    }

    private static String get(HttpClient client, URI uri, String token) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "ACommander-build")
                .timeout(Duration.ofSeconds(60));
        if (token != null && !token.isBlank() && "api.github.com".equals(uri.getHost())) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException(uri + " answered HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
