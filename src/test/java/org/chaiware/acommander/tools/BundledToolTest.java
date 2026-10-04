package org.chaiware.acommander.tools;

import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.helpers.AppPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BundledToolTest {

    @Test
    void everyBundledToolIsShipped() {
        List<Path> missing = Arrays.stream(BundledTool.values())
                .map(BundledTool::path)
                .filter(path -> !Files.isRegularFile(path))
                .toList();
        assertThat(missing).as("BundledTool paths missing under apps/").isEmpty();
    }

    @Test
    void everyActionToolIsShipped() throws IOException {
        List<String> missing = new AppConfigLoader().load(AppPaths.config("apps.json")).getActions().stream()
                .map(ActionDefinition::getPath)
                .filter(path -> path != null && !path.isBlank())
                .filter(path -> !Files.isRegularFile(AppPaths.resolve(path)))
                .toList();
        assertThat(missing).as("apps.json action paths missing on disk").isEmpty();
    }
}
