package org.chaiware.acommander.tools;

import org.chaiware.acommander.config.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolUpstreamCheckTest {

    @Test
    void readsTheTagOfAGitHubRelease() throws IOException {
        // Cut from https://api.github.com/repos/zufuliu/notepad4/releases/latest
        String json = """
                {"html_url": "https://github.com/zufuliu/notepad4/releases/tag/v26.08r6282", "id": 370978510,
                 "tag_name": "v26.08r6282", "target_commitish": "main", "name": "v26.08r6282", "draft": false,
                 "prerelease": false, "assets": []}""";

        assertThat(ToolUpstreamCheck.latestTag(json)).isEqualTo("v26.08r6282");
    }

    @Test
    void findsTheHighestVersionOnAPage() {
        String page = "<a href=\"dl/curl-8.17.0_3-win64-mingw.zip\">old</a> <a href=\"dl/curl-8.18.1_1-win64-mingw.zip\">new</a>"
                + " <a href=\"dl/curl-8.18.1_1-win32-mingw.zip\">32-bit</a>";

        assertThat(ToolUpstreamCheck.latestOnPage(page, "curl-(\\d+\\.\\d+\\.\\d+)_\\d+-win64")).isEqualTo("8.18.1");
        assertThat(ToolUpstreamCheck.latestOnPage("no versions here", "curl-(\\d+\\.\\d+\\.\\d+)")).isNull();
    }

    @Test
    void reportsNewerToolsAndOnesThatCouldNotBeChecked() {
        ToolUpstreamCheck.Result newer = ToolUpstreamCheck.check(tool("ripgrep", "15.1.0", "BurntSushi/ripgrep"),
                uri -> "{\"tag_name\": \"15.2.0\"}");
        ToolUpstreamCheck.Result same = ToolUpstreamCheck.check(tool("UPX", "5.1.1", "upx/upx"),
                uri -> "{\"tag_name\": \"v5.1.1\"}");
        ToolUpstreamCheck.Result failed = ToolUpstreamCheck.check(tool("RHash", "1.4.5", "rhash/RHash"), uri -> {
            throw new IOException(uri + " answered HTTP 403");
        });

        assertThat(ToolUpstreamCheck.report(List.of(newer, same, failed))).isEqualTo("""
                Bundled tools with a newer release (replace the files under apps/, set the version in config/apps.json, build, commit and push):
                  ripgrep 15.1.0 -> 15.2.0  https://github.com/BurntSushi/ripgrep/releases/latest
                  Could not check RHash: https://api.github.com/repos/rhash/RHash/releases/latest answered HTTP 403
                """);
    }

    private static ToolDefinition tool(String name, String version, String github) {
        ToolDefinition tool = new ToolDefinition();
        tool.setName(name);
        tool.setVersion(version);
        ToolDefinition.Upstream upstream = new ToolDefinition.Upstream();
        upstream.setGithub(github);
        tool.setUpstream(upstream);
        return tool;
    }
}
