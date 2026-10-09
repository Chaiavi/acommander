package org.chaiware.acommander.tools;

import org.chaiware.acommander.config.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolUpstreamCheckTest {
    // Cut from https://api.github.com/repos/BurntSushi/ripgrep/releases?per_page=30 (newest first; assets trimmed)
    private static final String RIPGREP_RELEASES = """
            [{"html_url": "https://github.com/BurntSushi/ripgrep/releases/tag/15.3.0-beta", "tag_name": "15.3.0-beta",
              "draft": false, "prerelease": true, "published_at": "2026-09-01T10:00:00Z", "assets": []},
             {"html_url": "https://github.com/BurntSushi/ripgrep/releases/tag/15.2.0", "tag_name": "15.2.0",
              "draft": false, "prerelease": false, "published_at": "2026-08-01T12:30:00Z",
              "assets": [{"name": "ripgrep-15.2.0-aarch64-pc-windows-msvc.zip",
                          "browser_download_url": "https://github.com/BurntSushi/ripgrep/releases/download/15.2.0/ripgrep-15.2.0-aarch64-pc-windows-msvc.zip"},
                         {"name": "ripgrep-15.2.0-x86_64-pc-windows-msvc.zip",
                          "digest": "sha256:6ca2a5e0b1ee6c4fcb5f13e3c25e2b2df4a5a8e0a2c7ab5bd0e0d1d7bc4f3a10",
                          "browser_download_url": "https://github.com/BurntSushi/ripgrep/releases/download/15.2.0/ripgrep-15.2.0-x86_64-pc-windows-msvc.zip"}]},
             {"html_url": "https://github.com/BurntSushi/ripgrep/releases/tag/15.1.0", "tag_name": "15.1.0",
              "draft": false, "prerelease": false, "published_at": "2025-10-22T08:00:00Z", "assets": []}]""";

    @Test
    void readsTheLatestFullReleaseItsDownloadAndTheCurrentVersionsDate() throws IOException {
        ToolUpstreamCheck.Result result = ToolUpstreamCheck.fromReleases(
                tool("ripgrep", "15.1.0", "BurntSushi/ripgrep", "x86_64-pc-windows-msvc\\.zip$"), RIPGREP_RELEASES);

        assertThat(result.latest()).isEqualTo("15.2.0");
        assertThat(result.latestDate()).isEqualTo("2026-08-01");
        assertThat(result.currentDate()).isEqualTo("2025-10-22");
        assertThat(result.download()).endsWith("/ripgrep-15.2.0-x86_64-pc-windows-msvc.zip");
        assertThat(result.sha256()).isEqualTo("6ca2a5e0b1ee6c4fcb5f13e3c25e2b2df4a5a8e0a2c7ab5bd0e0d1d7bc4f3a10");
        assertThat(result.isNewer()).isTrue();
    }

    @Test
    void pageToolsDownloadFromTheirUrlOrTheLinkOnThePage() {
        ToolDefinition.Upstream fastCopy = new ToolDefinition.Upstream();
        fastCopy.setPage("https://fastcopy.jp/");
        fastCopy.setUrl("https://fastcopy.jp/archive/FastCopy{version}_installer.exe");
        ToolDefinition.Upstream curl = new ToolDefinition.Upstream();
        curl.setPage("https://curl.se/windows/");
        curl.setDownloadPattern("href=\"(dl-[^\"]+/curl-[\\d.]+_\\d+-win64-mingw\\.zip)\"");
        String curlPage = "<a href=\"dl-8.22.0_3/curl-8.22.0_3-win64-mingw.zip\">zip</a>";

        assertThat(ToolUpstreamCheck.pageDownload(fastCopy, "", "5.12.0")).isEqualTo("https://fastcopy.jp/archive/FastCopy5.12.0_installer.exe");
        assertThat(ToolUpstreamCheck.pageDownload(curl, curlPage, "8.22.0"))
                .isEqualTo("https://curl.se/windows/dl-8.22.0_3/curl-8.22.0_3-win64-mingw.zip");
    }

    @Test
    void theVersionDropsTheTagsLeadingV() {
        assertThat(ToolUpstreamCheck.bare("v26.08r6282")).isEqualTo("26.08r6282");
        assertThat(ToolUpstreamCheck.bare("15.2.0")).isEqualTo("15.2.0");
    }

    @Test
    void withoutAnAssetPatternLinksTheReleasePage() throws IOException {
        ToolUpstreamCheck.Result result = ToolUpstreamCheck.fromReleases(tool("ripgrep", "15.1.0", "BurntSushi/ripgrep", null), RIPGREP_RELEASES);

        assertThat(result.download()).isNull();
        assertThat(result.page()).isEqualTo("https://github.com/BurntSushi/ripgrep/releases/tag/15.2.0");
    }

    @Test
    void findsTheHighestVersionOnAPage() {
        String page = "<a href=\"dl/curl-8.17.0_3-win64-mingw.zip\">old</a> <a href=\"dl/curl-8.18.1_1-win64-mingw.zip\">new</a>"
                + " <a href=\"dl/curl-8.18.1_1-win32-mingw.zip\">32-bit</a>";

        assertThat(ToolUpstreamCheck.latestOnPage(page, "curl-(\\d+\\.\\d+\\.\\d+)_\\d+-win64")).isEqualTo("8.18.1");
        assertThat(ToolUpstreamCheck.latestOnPage("no versions here", "curl-(\\d+\\.\\d+\\.\\d+)")).isNull();
    }

    @Test
    void reportsEveryToolNewerOnesFirst() {
        ToolUpstreamCheck.Result newer = ToolUpstreamCheck.check(tool("ripgrep", "15.1.0", "BurntSushi/ripgrep", "x86_64-pc-windows-msvc\\.zip$"),
                uri -> RIPGREP_RELEASES);
        ToolUpstreamCheck.Result failed = ToolUpstreamCheck.check(tool("RHash", "1.4.5", "rhash/RHash", null), uri -> {
            throw new IOException("HTTP 403");
        });
        ToolDefinition renamer = tool("Ant Renamer", "2.13", null, null);
        renamer.setUpstream(null);

        assertThat(ToolUpstreamCheck.report(List.of(failed, ToolUpstreamCheck.check(renamer, uri -> ""), newer))).isEqualTo("""
                Bundled tools: 1 with a newer release (*). To update one: replace its files under apps/, set its version in config/apps.json, build, commit and push.
                    Tool                                 Current                  Latest                   Download
                  * ripgrep                              15.1.0 (2025-10-22)      15.2.0 (2026-08-01)      https://github.com/BurntSushi/ripgrep/releases/download/15.2.0/ripgrep-15.2.0-x86_64-pc-windows-msvc.zip
                    Ant Renamer                          2.13                     (no upstream)
                    RHash                                1.4.5                    could not check: HTTP 403
                """);
    }

    private static ToolDefinition tool(String name, String version, String github, String asset) {
        ToolDefinition tool = new ToolDefinition();
        tool.setName(name);
        tool.setVersion(version);
        ToolDefinition.Upstream upstream = new ToolDefinition.Upstream();
        upstream.setGithub(github);
        upstream.setAsset(asset);
        tool.setUpstream(upstream);
        return tool;
    }
}
