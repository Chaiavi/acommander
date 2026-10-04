package org.chaiware.acommander;

import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfigLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Every shortcut in apps.json must be documented in the F1 help page and the README shortcuts table. */
class DocsShortcutsTest {

    @Test
    void everyShortcutIsInF1Help() throws IOException {
        String help = Files.readString(Path.of("config", "f1-help.html"))
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s*\\+\\s*", "+");
        List<String> missing = shortcuts().stream()
                .filter(shortcut -> !containsKey(help, shortcut) && !containsKey(help, shortAlias(shortcut)))
                .toList();
        assertThat(missing).as("apps.json shortcuts missing from config/f1-help.html").isEmpty();
    }

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

    // Boundaries stop "Ctrl+A" matching inside "Ctrl+Alt+V" and "F1" inside "F10".
    private static boolean containsKey(String text, String shortcut) {
        return Pattern.compile("(?<![A-Za-z0-9+])" + Pattern.quote(shortcut) + "(?![A-Za-z0-9])").matcher(text).find();
    }
}
