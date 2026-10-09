package org.chaiware.acommander.services;

import org.chaiware.acommander.config.ToolDefinition;
import org.chaiware.acommander.services.ToolUpdateService.FileChange;
import org.chaiware.acommander.services.ToolUpdateService.State;
import org.chaiware.acommander.services.ToolUpdateService.ToolStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolUpdateServiceTest {
    @TempDir
    Path root;

    /** What "GitHub" serves, by URL. */
    private final Map<String, byte[]> served = new HashMap<>();
    private ToolUpdateService service;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(root.resolve("apps"));
        service = new ToolUpdateService(root, uri -> {
            byte[] bytes = served.get(uri.toString());
            if (bytes == null) {
                throw new FileNotFoundException(uri.toString());
            }
            return new ByteArrayInputStream(bytes);
        }, "4.6");
    }

    @Test
    void planDownloadsMissingFilesAndFilesMainChanged() throws IOException {
        write("apps/rg/rg.exe", "old");
        write("apps/rg/settings.ini", "edited by the user");
        Map<String, String> shipped = Map.of("apps/rg/rg.exe", sha("old"), "apps/rg/settings.ini", sha("default"));
        Map<String, String> remote = Map.of("apps/rg/rg.exe", sha("new"), "apps/rg/settings.ini", sha("default"),
                "apps/rg/README.txt", sha("readme"), "apps/other/x.exe", sha("x"));

        ToolDefinition rg = tool("rg", "1", "apps/rg");
        assertThat(service.plan(rg, shipped, remote, List.of(rg))).containsExactlyInAnyOrder(
                new FileChange("apps/rg/rg.exe", sha("new")),
                new FileChange("apps/rg/README.txt", sha("readme")));
    }

    @Test
    void aFileInsideAnotherToolsFolderBelongsToTheToolThatNamesIt() throws IOException {
        ToolDefinition uniExtract = tool("uniExtract", "2", "apps/extract_all");
        ToolDefinition sevenZip = tool("sevenZipConsole", "26.04", "apps/extract_all/bin/7z.exe");
        Map<String, String> remote = Map.of("apps/extract_all/bin/7z.exe", sha("7z"), "apps/extract_all/UniExtract.exe", sha("ue"));
        List<ToolDefinition> tools = List.of(uniExtract, sevenZip);

        assertThat(service.plan(sevenZip, Map.of(), remote, tools)).containsExactly(new FileChange("apps/extract_all/bin/7z.exe", sha("7z")));
        assertThat(service.plan(uniExtract, Map.of(), remote, tools)).containsExactly(new FileChange("apps/extract_all/UniExtract.exe", sha("ue")));
    }

    @Test
    void planSkipsAFileThatAlreadyMatchesMain() throws IOException {
        write("apps/rg/rg.exe", "new");

        ToolDefinition rg = tool("rg", "1", "apps/rg");
        assertThat(service.plan(rg, Map.of("apps/rg/rg.exe", sha("old")),
                Map.of("apps/rg/rg.exe", sha("new")), List.of(rg))).isEmpty();
    }

    @Test
    void planDeletesADroppedFileOnlyWhileUnchanged() throws IOException {
        write("apps/rg/old.dll", "shipped");
        write("apps/rg/notes.txt", "user notes");
        Map<String, String> shipped = Map.of("apps/rg/old.dll", sha("shipped"), "apps/rg/notes.txt", sha("shipped notes"));

        ToolDefinition rg = tool("rg", "1", "apps/rg");
        assertThat(service.plan(rg, shipped, Map.of(), List.of(rg)))
                .containsExactly(new FileChange("apps/rg/old.dll", null));
    }

    @Test
    void checkReportsEachToolsState() throws IOException {
        write("apps/rg/rg.exe", "old");
        write("apps/upx/upx.exe", "same");
        write("apps/tools.sha256", ToolUpdateService.formatHashes(Map.of(
                "apps/rg/rg.exe", sha("old"), "apps/upx/upx.exe", sha("same"), "apps/curl/curl.exe", sha("curl"))));
        serveMain("""
                {"tools": [
                  {"id": "rg", "name": "ripgrep", "version": "15.2.0", "paths": ["apps/rg"]},
                  {"id": "upx", "name": "UPX", "version": "5.1.1", "paths": ["apps/upx"]},
                  {"id": "curl", "name": "curl", "version": "8.18.0", "paths": ["apps/curl"]},
                  {"id": "pdf", "name": "PDF", "version": "3", "paths": ["apps/pdf"], "minAppVersion": "5.0"}
                ]}""", Map.of("apps/rg/rg.exe", sha("new"), "apps/upx/upx.exe", sha("same"),
                "apps/curl/curl.exe", sha("curl"), "apps/pdf/pdf.exe", sha("pdf")));

        List<ToolStatus> statuses = service.check(List.of(tool("rg", "15.1.0", "apps/rg"), tool("upx", "5.1.1", "apps/upx"),
                tool("curl", "8.18.0", "apps/curl"), tool("pdf", "2", "apps/pdf")));

        assertThat(statuses).extracting(status -> status.tool().getId(), ToolStatus::installed, ToolStatus::state).containsExactly(
                org.assertj.core.groups.Tuple.tuple("rg", "15.1.0", State.UPDATE_AVAILABLE),
                org.assertj.core.groups.Tuple.tuple("upx", "5.1.1", State.UP_TO_DATE),
                org.assertj.core.groups.Tuple.tuple("curl", "8.18.0", State.MISSING_FILES),
                org.assertj.core.groups.Tuple.tuple("pdf", "2", State.NEEDS_NEWER_APP));
        assertThat(ToolUpdateService.offerKey(statuses)).isEqualTo("curl:8.18.0:MISSING_FILES,rg:15.2.0:UPDATE_AVAILABLE");
    }

    @Test
    void aCopyAheadOfMainIsNotOfferedTheOlderFiles() throws IOException {
        write("apps/rg/rg.exe", "newer, not pushed yet");
        ToolDefinition onMain = tool("rg", "16.0.0", "apps/rg");
        ToolStatus status = service.status(onMain, tool("rg", "16.1.0", "apps/rg"),
                Map.of("apps/rg/rg.exe", sha("newer, not pushed yet")), Map.of("apps/rg/rg.exe", sha("released")), List.of(onMain));

        assertThat(status.state()).isEqualTo(State.UP_TO_DATE);
        assertThat(status.changes()).isEmpty();
    }

    @Test
    void updateReplacesTheFilesAndRecordsTheirHashes() throws IOException {
        write("apps/rg/rg.exe", "old");
        write("apps/rg/gone.dll", "shipped");
        write("apps/tools.sha256", ToolUpdateService.formatHashes(Map.of("apps/rg/rg.exe", sha("old"), "apps/rg/gone.dll", sha("shipped"))));
        served.put(ToolUpdateService.RAW_MAIN + "apps/rg/rg.exe", bytes("new"));
        served.put(ToolUpdateService.RAW_MAIN + "apps/rg/My%20Docs/read%20me.txt", bytes("doc"));

        service.update(status(tool("rg", "2", "apps/rg"), new FileChange("apps/rg/rg.exe", sha("new")),
                new FileChange("apps/rg/My Docs/read me.txt", sha("doc")), new FileChange("apps/rg/gone.dll", null)));

        assertThat(Files.readString(root.resolve("apps/rg/rg.exe"))).isEqualTo("new");
        assertThat(Files.readString(root.resolve("apps/rg/My Docs/read me.txt"))).isEqualTo("doc");
        assertThat(root.resolve("apps/rg/gone.dll")).doesNotExist();
        assertThat(root.resolve("apps/rg/rg.exe.acommander-old")).doesNotExist();
        assertThat(ToolUpdateService.parseHashes(Files.readString(root.resolve("apps/tools.sha256")))).isEqualTo(Map.of(
                "apps/rg/rg.exe", sha("new"), "apps/rg/My Docs/read me.txt", sha("doc")));
    }

    @Test
    void aDownloadThatDoesNotMatchItsHashChangesNothing() throws IOException {
        write("apps/rg/rg.exe", "old");
        write("apps/rg/a.dll", "old dll");
        served.put(ToolUpdateService.RAW_MAIN + "apps/rg/a.dll", bytes("new dll"));
        served.put(ToolUpdateService.RAW_MAIN + "apps/rg/rg.exe", bytes("tampered"));

        assertThatThrownBy(() -> service.update(status(tool("rg", "2", "apps/rg"),
                new FileChange("apps/rg/a.dll", sha("new dll")), new FileChange("apps/rg/rg.exe", sha("new")))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("apps/rg/rg.exe does not match");
        assertThat(Files.readString(root.resolve("apps/rg/rg.exe"))).isEqualTo("old");
        assertThat(Files.readString(root.resolve("apps/rg/a.dll"))).isEqualTo("old dll");
    }

    @Test
    void aFailedSwapPutsTheOldFilesBack() throws IOException {
        write("apps/rg/rg.exe", "old");
        Files.createDirectories(root.resolve("apps/rg/blocked"));
        Files.writeString(root.resolve("apps/rg/blocked/inside.txt"), "a folder can't be moved aside onto");
        Files.createDirectories(root.resolve("apps/rg/blocked.acommander-old/x"));
        served.put(ToolUpdateService.RAW_MAIN + "apps/rg/rg.exe", bytes("new"));
        served.put(ToolUpdateService.RAW_MAIN + "apps/rg/blocked", bytes("file"));

        assertThatThrownBy(() -> service.update(status(tool("rg", "2", "apps/rg"),
                new FileChange("apps/rg/rg.exe", sha("new")), new FileChange("apps/rg/blocked", sha("file")))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Could not replace");
        assertThat(Files.readString(root.resolve("apps/rg/rg.exe"))).isEqualTo("old");
        assertThat(root.resolve("apps/rg/rg.exe.acommander-old")).doesNotExist();
    }

    @Test
    void refusesFilesOutsideApps() {
        for (String path : List.of("apps/../config/apps.json", "../outside.exe", "C:/Windows/evil.exe", "apps\\rg\\rg.exe", "apps")) {
            assertThatThrownBy(() -> service.resolve(path)).as(path).isInstanceOf(IOException.class);
        }
    }

    @Test
    void downloadsOnlyFromThisProjectsGitHubOverHttps() throws IOException {
        ToolUpdateService.requireAllowedHost(URI.create("https://raw.githubusercontent.com/Chaiavi/acommander/main/x"));
        ToolUpdateService.requireAllowedHost(URI.create("https://release-assets.githubusercontent.com/x"));
        assertThatThrownBy(() -> ToolUpdateService.requireAllowedHost(URI.create("http://raw.githubusercontent.com/x")))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ToolUpdateService.requireAllowedHost(URI.create("https://example.com/rg.exe")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void releaseToolsDownloadFromTheirRelease() {
        ToolDefinition ffmpeg = tool("ffmpeg", "9.0.2", "apps/media");
        ffmpeg.setRelease("ffmpeg-9.0.2");

        assertThat(service.downloadUri(ffmpeg, "apps/media/ffmpeg.exe"))
                .hasToString("https://github.com/Chaiavi/acommander/releases/download/ffmpeg-9.0.2/ffmpeg.exe");
        assertThat(service.downloadUri(tool("rg", "1", "apps/rg"), "apps/rg/Chinese (Simplified).lng"))
                .hasToString(ToolUpdateService.RAW_MAIN + "apps/rg/Chinese%20%28Simplified%29.lng");
    }

    @Test
    void comparesTheNumbersOfVersions() {
        assertThat(ToolUpdateService.compareVersions("v26.08r6282", "25.05.5670")).isPositive();
        assertThat(ToolUpdateService.compareVersions("15.1.0", "v15.1")).isZero();
        assertThat(ToolUpdateService.compareVersions("1.4.5", "1.4.10")).isNegative();
        assertThat(ToolUpdateService.compareVersions("dev", "5.0")).isZero();
    }

    @Test
    void startCheckRunsOncePerDayWhenOn() {
        long day = 24L * 60 * 60 * 1000;
        assertThat(ToolUpdateService.startCheckDue(true, 0, day)).isTrue();
        assertThat(ToolUpdateService.startCheckDue(true, day, day + day - 1)).isFalse();
        assertThat(ToolUpdateService.startCheckDue(false, 0, day * 10)).isFalse();
    }

    private void serveMain(String appsJson, Map<String, String> hashes) {
        served.put(ToolUpdateService.RAW_MAIN + "config/apps.json", bytes(appsJson));
        served.put(ToolUpdateService.RAW_MAIN + "apps/tools.sha256", bytes(ToolUpdateService.formatHashes(hashes)));
    }

    private static ToolStatus status(ToolDefinition tool, FileChange... changes) {
        return new ToolStatus(tool, "1", tool.getVersion(), State.UPDATE_AVAILABLE, List.of(changes));
    }

    private static ToolDefinition tool(String id, String version, String path) {
        ToolDefinition tool = new ToolDefinition();
        tool.setId(id);
        tool.setName(id);
        tool.setVersion(version);
        tool.setPaths(List.of(path));
        return tool;
    }

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(content)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
