package org.chaiware.acommander;

import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfigLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps docs/CODEMAP.md in step with the code: every main class and every apps.json action must be listed. */
class CodeMapTest {
    private static final Path CODE_MAP = Path.of("docs", "CODEMAP.md");

    @Test
    void everyMainClassIsListed() throws IOException {
        String map = Files.readString(CODE_MAP);
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            List<String> missing = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .filter(className -> !map.contains("`" + className + "`"))
                    .toList();
            assertThat(missing).as("classes missing from docs/CODEMAP.md").isEmpty();
        }
    }

    @Test
    void everyActionIsListed() throws IOException {
        String map = Files.readString(CODE_MAP);
        List<String> missing = new AppConfigLoader().load(Path.of("config", "apps.json")).getActions().stream()
                .filter(action -> !map.contains("`" + action.getId() + "`")
                        && (action.getBuiltin() == null || !map.contains("`" + action.getBuiltin() + "`")))
                .map(ActionDefinition::getId)
                .toList();
        assertThat(missing).as("apps.json actions missing from docs/CODEMAP.md").isEmpty();
    }
}
