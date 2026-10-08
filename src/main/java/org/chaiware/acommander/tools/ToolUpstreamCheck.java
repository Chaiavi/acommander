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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * For the developer, run by the build's checkToolUpdates task: asks each tool's upstream (apps.json {@code upstream})
 * for its latest version and prints the tools that have a newer one. Users never run this; they update from this
 * project's own GitHub through {@link ToolUpdateService}.
 */
public final class ToolUpstreamCheck {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A tool's upstream answer: {@code latest} or {@code error} is set. */
    public record Result(ToolDefinition tool, String latest, String url, String error) {
        public boolean isNewer() {
            return latest != null && ToolUpdateService.compareVersions(latest, tool.getVersion()) > 0;
        }
    }

    private ToolUpstreamCheck() {
    }

    public static void main(String[] args) throws IOException {
        List<ToolDefinition> tools = new AppConfigLoader().load(AppPaths.config("apps.json")).getTools();
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        List<Result> results = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            if (tool.getUpstream() != null) {
                results.add(check(tool, uri -> get(client, uri)));
            }
        }
        System.out.print(report(results));
    }

    interface Fetcher {
        String get(URI uri) throws IOException;
    }

    static Result check(ToolDefinition tool, Fetcher fetcher) {
        ToolDefinition.Upstream upstream = tool.getUpstream();
        try {
            if (upstream.getGithub() != null) {
                String url = "https://github.com/" + upstream.getGithub() + "/releases/latest";
                String json = fetcher.get(URI.create("https://api.github.com/repos/" + upstream.getGithub() + "/releases/latest"));
                return new Result(tool, latestTag(json), url, null);
            }
            String latest = latestOnPage(fetcher.get(URI.create(upstream.getPage())), upstream.getPattern());
            if (latest == null) {
                return new Result(tool, null, upstream.getPage(), "no version matching " + upstream.getPattern());
            }
            return new Result(tool, latest, upstream.getPage(), null);
        } catch (IOException | RuntimeException e) {
            return new Result(tool, null, null, e.getMessage());
        }
    }

    static String latestTag(String releaseJson) throws IOException {
        JsonNode tag = JSON.readTree(releaseJson).get("tag_name");
        if (tag == null || tag.asText().isBlank()) {
            throw new IOException("the latest release has no tag");
        }
        return tag.asText();
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

    static String report(List<Result> results) {
        StringBuilder text = new StringBuilder();
        List<Result> newer = results.stream().filter(Result::isNewer).toList();
        if (newer.isEmpty()) {
            text.append("Bundled tools: none of the ").append(results.size()).append(" checked has a newer release.\n");
        } else {
            text.append("Bundled tools with a newer release (replace the files under apps/, set the version in "
                    + "config/apps.json, build, commit and push):\n");
            newer.forEach(result -> text.append("  ").append(result.tool().getName()).append(' ')
                    .append(result.tool().getVersion()).append(" -> ").append(result.latest())
                    .append("  ").append(result.url()).append('\n'));
        }
        results.stream().filter(result -> result.error() != null).forEach(result -> text
                .append("  Could not check ").append(result.tool().getName()).append(": ").append(result.error()).append('\n'));
        return text.toString();
    }

    private static String get(HttpClient client, URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "ACommander-build")
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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
