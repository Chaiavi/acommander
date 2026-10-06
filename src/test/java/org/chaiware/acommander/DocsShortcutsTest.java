package org.chaiware.acommander;

import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfigLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** Every shortcut in apps.json must be documented in the README shortcuts table. */
class DocsShortcutsTest {

    @Test
    void everyShortcutIsInReadme() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        List<String> missing = shortcuts().stream()
                .filter(shortcut -> !readme.contains("`" + shortcut + "`")
                        && !readme.contains("`" + shortAlias(shortcut) + "`"))
                .toList();
        assertThat(missing).as("apps.json shortcuts missing from the README shortcuts table").isEmpty();
    }

    private static List<String> shortcuts() throws IOException {
        return new AppConfigLoader().load(Path.of("config", "apps.json")).getActions().stream()
                .map(ActionDefinition::getShortcut)
                .filter(Objects::nonNull)
                .filter(shortcut -> !shortcut.isBlank())
                .distinct()
                .toList();
    }

    // The docs write "Shift+Del" for "Shift+Delete".
    private static String shortAlias(String shortcut) {
        return shortcut.replaceAll("Delete$", "Del");
    }
}
