package org.chaiware.acommander.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** One bundled tool in apps.json {@code tools}: what it is, which files under apps/ it owns, and where new versions come from. */
@Data
public class ToolDefinition {
    private String id;
    private String name;
    private String version;
    private String link;
    /** Files or folders under apps/ that belong to this tool, written with forward slashes. */
    private List<String> paths = new ArrayList<>();
    /** The oldest ACommander version that can run this tool version; null when any can. */
    private String minAppVersion;
    /** A release of this project holding the tool's files, for files too big for git (ffmpeg). */
    private String release;
    /** Where the build looks for a newer version; null for tools that are no longer developed. */
    private Upstream upstream;

    @Data
    public static class Upstream {
        /** owner/repo of a GitHub project whose latest release tag is the version. */
        private String github;
        /** Regex for the release file to download (the Windows x64 zip); without it the release page is linked. */
        private String asset;
        /** A web page that names the latest version, found with {@link #pattern} (group 1). */
        private String page;
        private String pattern;
        /** The download for a {@link #page} tool, with {@code {version}} for the latest version. */
        private String url;
        /** Or a regex whose group 1 is the download link on the {@link #page}. */
        private String downloadPattern;
        /** Arguments that make a downloaded installer unpack itself into {@code {dir}}; without them 7-Zip unpacks it. */
        private List<String> extract;
        /** File names to take from the download; without them, the names of the tool's files under apps/. */
        private List<String> files;
    }

    /** True when {@code path} (apps/..., forward slashes) is one of this tool's files or inside one of its folders. */
    public boolean owns(String path) {
        return claim(path) >= 0;
    }

    /** The length of this tool's longest own path that holds {@code path}; -1 when none does. */
    public int claim(String path) {
        return paths.stream()
                .filter(own -> path.equals(own) || path.startsWith(own + "/"))
                .mapToInt(String::length)
                .max().orElse(-1);
    }

    /** The tool whose path holds {@code path} most specifically (7z.exe inside Universal Extractor's folder), or null. */
    public static ToolDefinition owner(List<ToolDefinition> tools, String path) {
        ToolDefinition owner = null;
        for (ToolDefinition tool : tools) {
            if (tool.claim(path) >= 0 && (owner == null || tool.claim(path) > owner.claim(path))) {
                owner = tool;
            }
        }
        return owner;
    }
}
