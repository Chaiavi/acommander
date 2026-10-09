package org.chaiware.acommander.tools;

import org.chaiware.acommander.config.ToolDefinition;
import org.chaiware.acommander.services.ToolUpdateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolUpgraderTest {
    private static final String APPS_JSON = """
            {
              "tools": [
                {"id": "ripgrep", "name": "ripgrep", "version": "15.1.0", "paths": ["apps/search_in_files"],
                  "upstream": {"github": "BurntSushi/ripgrep", "asset": "x86_64-pc-windows-msvc\\\\.zip$"}},
                {"id": "ffmpeg", "name": "FFmpeg", "version": "9.0.2", "paths": ["apps/media"], "release": "ffmpeg-9.0.2",
                  "upstream": {"github": "GyanD/codexffmpeg"}}
              ]
            }
            """;

    @TempDir
    Path root;

    @Test
    void setsTheVersionInTheToolsEntryOnly() {
        String updated = ToolUpgrader.setVersion(APPS_JSON, tool("ripgrep", "15.1.0", "apps/search_in_files"), "15.2.0");

        assertThat(updated).isEqualTo(APPS_JSON.replace("\"version\": \"15.1.0\"", "\"version\": \"15.2.0\""));
    }

    @Test
    void anFfmpegUpdateRenamesItsReleaseAndPinsTheZip() {
        ToolDefinition ffmpeg = tool("ffmpeg", "9.0.2", "apps/media");
        ffmpeg.setRelease("ffmpeg-9.0.2");

        assertThat(ToolUpgrader.setVersion(APPS_JSON, ffmpeg, "9.1.0"))
                .contains("\"version\": \"9.1.0\", \"paths\": [\"apps/media\"], \"release\": \"ffmpeg-9.1.0\"")
                .contains("\"version\": \"15.1.0\"");
        assertThat(ToolUpgrader.setFfmpegSha256("def ffmpegZipSha256 = '" + "a".repeat(64) + "'\n", "b".repeat(64)))
                .isEqualTo("def ffmpegZipSha256 = '" + "b".repeat(64) + "'\n");
    }

    @Test
    void newFilesGoBesideTheToolsFirstFile() {
        ToolDefinition sevenZip = tool("sevenZip", "24.09", "apps/pack_unpack");
        sevenZip.getUpstream().setFiles(List.of("7zG.exe", "7z.dll"));

        assertThat(ToolUpgrader.targets(root, sevenZip, Map.of("apps/pack_unpack/7zG.exe", "x", "apps/other/a.exe", "y")))
                .containsExactly(Map.entry("7zG.exe", root.resolve("apps/pack_unpack/7zG.exe")),
                        Map.entry("7z.dll", root.resolve("apps/pack_unpack/7z.dll")));
    }

    @Test
    void replacesTheToolsFilesFromTheDownloadAndSetsTheVersion() throws Exception {
        Path rg = write("apps/search_in_files/rg.exe", "old rg");
        write("config/apps.json", APPS_JSON);
        byte[] zip = zip(Map.of("ripgrep-15.2.0/rg.exe", "new rg", "ripgrep-15.2.0/doc/rg.1", "manual"));
        ToolDefinition ripgrep = tool("ripgrep", "15.1.0", "apps/search_in_files");

        ToolUpgrader.upgrade(root, ripgrep, result(ripgrep, sha256(zip)), Map.of("apps/search_in_files/rg.exe", "x"),
                (uri, target) -> Files.write(target, zip));

        assertThat(rg).hasContent("new rg");
        assertThat(Files.readString(root.resolve("config/apps.json"))).contains("\"version\": \"15.2.0\"");
    }

    @Test
    void aDownloadThatDoesNotMatchGitHubsHashChangesNothing() throws Exception {
        Path rg = write("apps/search_in_files/rg.exe", "old rg");
        write("config/apps.json", APPS_JSON);
        ToolDefinition ripgrep = tool("ripgrep", "15.1.0", "apps/search_in_files");

        assertThatThrownBy(() -> ToolUpgrader.upgrade(root, ripgrep, result(ripgrep, "0".repeat(64)),
                Map.of("apps/search_in_files/rg.exe", "x"), (uri, target) -> Files.write(target, zip(Map.of("rg.exe", "evil"))))
        ).isInstanceOf(IOException.class).hasMessageContaining("does not match");
        assertThat(rg).hasContent("old rg");
        assertThat(Files.readString(root.resolve("config/apps.json"))).contains("\"version\": \"15.1.0\"");
    }

    @Test
    void aFileMissingFromTheDownloadChangesNothing() throws Exception {
        Path exe = write("apps/image_metadata/exiv2.exe", "old exe");
        write("apps/image_metadata/exiv2.dll", "old dll");
        write("config/apps.json", APPS_JSON);
        byte[] zip = zip(Map.of("bin/exiv2.exe", "new exe"));
        ToolDefinition exiv2 = tool("exiv2", "0.28.8", "apps/image_metadata");

        assertThatThrownBy(() -> ToolUpgrader.upgrade(root, exiv2, result(exiv2, null),
                Map.of("apps/image_metadata/exiv2.exe", "x", "apps/image_metadata/exiv2.dll", "y"), (uri, target) -> Files.write(target, zip))
        ).hasMessageContaining("exiv2.dll is not in");
        assertThat(exe).hasContent("old exe");
    }

    private static ToolUpstreamCheck.Result result(ToolDefinition tool, String sha256) {
        return new ToolUpstreamCheck.Result(tool, null, "15.2.0", null, "https://github.com/x/y/releases/download/15.2.0/rg.zip",
                sha256, null, null);
    }

    private static ToolDefinition tool(String id, String version, String path) {
        ToolDefinition tool = new ToolDefinition();
        tool.setId(id);
        tool.setName(id);
        tool.setVersion(version);
        tool.setPaths(List.of(path));
        tool.setUpstream(new ToolDefinition.Upstream());
        return tool;
    }

    private Path write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
            }
        }
        return bytes.toByteArray();
    }

    private String sha256(byte[] content) throws IOException {
        return ToolUpdateService.sha256(Files.write(root.resolve("hash.tmp"), content));
    }
}
