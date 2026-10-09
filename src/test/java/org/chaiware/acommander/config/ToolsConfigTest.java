package org.chaiware.acommander.config;

import org.chaiware.acommander.helpers.AppPaths;
import org.chaiware.acommander.services.ToolUpdateService;
import org.chaiware.acommander.tools.BundledTool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** apps.json {@code tools} must cover every shipped file once, or Tool Updates can't update or describe it. */
class ToolsConfigTest {
    private static AppConfig config;

    @BeforeAll
    static void load() throws IOException {
        config = new AppConfigLoader().load(AppPaths.config("apps.json"));
    }

    @Test
    void everyToolIsDescribedAndItsPathsExist() {
        assertThat(config.getTools()).isNotEmpty().allSatisfy(tool -> {
            assertThat(List.of(tool.getId(), tool.getName(), tool.getVersion(), tool.getLink())).as(tool.getId()).allMatch(text -> text != null && !text.isBlank());
            assertThat(tool.getPaths()).as(tool.getId()).isNotEmpty()
                    .allMatch(path -> Files.exists(AppPaths.resolve(path)), "exists under the app root");
            if (tool.getUpstream() != null) {
                assertThat(tool.getUpstream().getGithub() != null
                        || (tool.getUpstream().getPage() != null && tool.getUpstream().getPattern() != null))
                        .as(tool.getId() + " upstream needs github, or page and pattern").isTrue();
            }
        });
        assertThat(config.getTools()).extracting(ToolDefinition::getId).doesNotHaveDuplicates();
    }

    @Test
    void everyHashedFileHasOneMostSpecificOwner() throws IOException {
        Map<String, String> hashes = ToolUpdateService.parseHashes(Files.readString(BundledTool.TOOL_HASHES.path()));
        assertThat(hashes).as("apps/tools.sha256 (written by the toolHashes task)").isNotEmpty();
        assertThat(hashes.keySet()).as("files under apps/ owned by no tool, or by two tools equally, in apps.json tools")
                .allSatisfy(path -> {
                    ToolDefinition owner = ToolDefinition.owner(config.getTools(), path);
                    assertThat(owner).as(path).isNotNull();
                    assertThat(config.getTools()).as(path).filteredOn(tool -> tool.claim(path) == owner.claim(path)).hasSize(1);
                });
    }

    @Test
    void everyToolTheCodeRunsBelongsToATool() {
        List<String> paths = Arrays.stream(BundledTool.values())
                .filter(tool -> tool != BundledTool.TOOL_HASHES)
                .map(BundledTool::relativePath)
                .collect(Collectors.toList());
        config.getActions().stream().map(ActionDefinition::getPath)
                .filter(path -> path != null && path.startsWith("apps/")).forEach(paths::add);

        assertThat(paths).allMatch(path -> config.getTools().stream().anyMatch(tool -> tool.owns(path)), "owned by an apps.json tool");
    }

    @Test
    void ffmpegReleaseIsNamedAfterItsVersion() {
        ToolDefinition ffmpeg = config.getTools().stream().filter(tool -> "ffmpeg".equals(tool.getId())).findFirst().orElseThrow();

        assertThat(ffmpeg.getRelease()).isEqualTo("ffmpeg-" + ffmpeg.getVersion());
    }
}
