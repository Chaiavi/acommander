package org.chaiware.acommander.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class AppConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsConfigAndIgnoresUnknownFields() throws Exception {
        String json = """
                {
                  "actions": [
                    {
                      "id": "open",
                      "label": "Open File",
                      "contexts": ["global"],
                      "unknownField": "ignored"
                    }
                  ],
                  "extraTopLevel": "ignored"
                }
                """;
        Path config = tempDir.resolve("config.json");
        Files.writeString(config, json);

        AppConfigLoader loader = new AppConfigLoader();
        AppConfig loaded = loader.load(config);

        Assertions.assertThat(loaded.getActions())
                .hasSize(1);
        Assertions.assertThat(loaded.getActions().getFirst().getId())
                .isEqualTo("open");
        Assertions.assertThat(loaded.getActions().getFirst().getLabel())
                .isEqualTo("Open File");
    }

    @Test
    void throwsHelpfulErrorWhenConfigMissing() {
        Path missing = tempDir.resolve("missing.json");
        AppConfigLoader loader = new AppConfigLoader();

        Assertions.assertThatThrownBy(() -> loader.load(missing))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Missing config file:");
    }

    @Test
    void missingActionsDefaultsToEmptyList() throws Exception {
        String json = """
                {
                  "extraTopLevel": "ignored"
                }
                """;
        Path config = tempDir.resolve("config.json");
        Files.writeString(config, json);

        AppConfigLoader loader = new AppConfigLoader();
        AppConfig loaded = loader.load(config);

        Assertions.assertThat(loaded.getActions())
                .isNotNull()
                .isEmpty();
    }

    @Test
    void invalidJsonProducesIOException() throws Exception {
        String json = """
                {
                  "actions": [
                    { "id": "open", "label": "Open"
                  ]
                }
                """;
        Path config = tempDir.resolve("config.json");
        Files.writeString(config, json);

        AppConfigLoader loader = new AppConfigLoader();

        Assertions.assertThatThrownBy(() -> loader.load(config))
                .isInstanceOf(IOException.class);
    }

    @Test
    void readsActionRules() throws Exception {
        Path config = tempDir.resolve("config.json");
        Files.writeString(config, """
                { "actions": [ { "id": "a", "ftp": true, "writes": "target",
                                 "fileTypes": ["pdf", "archive"], "requires": ["clipboardHasFiles"] } ] }
                """);

        ActionDefinition action = new AppConfigLoader().load(config).getActions().getFirst();

        Assertions.assertThat(action.isFtp()).isTrue();
        Assertions.assertThat(action.getWrites()).isEqualTo(ActionDefinition.WriteTarget.TARGET);
        Assertions.assertThat(action.getFileTypes())
                .containsExactly(ActionDefinition.FileType.PDF, ActionDefinition.FileType.ARCHIVE);
        Assertions.assertThat(action.getRequires()).containsExactly(ActionDefinition.Requirement.CLIPBOARD_HAS_FILES);
    }

    @Test
    void actionRulesDefaultToNothing() throws Exception {
        Path config = tempDir.resolve("config.json");
        Files.writeString(config, "{ \"actions\": [ { \"id\": \"a\" } ] }");

        ActionDefinition action = new AppConfigLoader().load(config).getActions().getFirst();

        Assertions.assertThat(action.isFtp()).isFalse();
        Assertions.assertThat(action.getWrites()).isEqualTo(ActionDefinition.WriteTarget.NONE);
        Assertions.assertThat(action.getFileTypes()).isEmpty();
        Assertions.assertThat(action.getRequires()).isEmpty();
    }

    @Test
    void misspelledRuleValueFailsToLoad() throws Exception {
        Path config = tempDir.resolve("config.json");
        Files.writeString(config, "{ \"actions\": [ { \"id\": \"a\", \"writes\": \"targt\" } ] }");

        Assertions.assertThatThrownBy(() -> new AppConfigLoader().load(config))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("targt");
    }

    @Test
    void moveActionSupportsMultiSelectionInDefaultConfig() throws Exception {
        Path config = Path.of("config", "apps.json");
        AppConfigLoader loader = new AppConfigLoader();
        AppConfig loaded = loader.load(config);

        ActionDefinition moveAction = loaded.getActions().stream()
                .filter(action -> "move".equals(action.getId()))
                .findFirst()
                .orElseThrow();

        Assertions.assertThat(moveAction.getSelection())
                .isEqualTo("any");
    }
}
