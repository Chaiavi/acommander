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
        /** A web page that names the latest version, found with {@link #pattern} (group 1). */
        private String page;
        private String pattern;
    }

    /** True when {@code path} (apps/..., forward slashes) is one of this tool's files or inside one of its folders. */
    public boolean owns(String path) {
        return paths.stream().anyMatch(own -> path.equals(own) || path.startsWith(own + "/"));
    }
}
