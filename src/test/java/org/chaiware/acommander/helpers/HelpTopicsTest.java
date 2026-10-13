package org.chaiware.acommander.helpers;

import org.chaiware.acommander.config.ActionDefinition;
import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.helpers.HelpTopics.Entry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class HelpTopicsTest {

    @Test
    void mergesShortcutsOfOneHandlerAndOrdersByFunctionKey() {
        List<Entry> entries = HelpTopics.entries(List.of(
                action("copySelection", null, "Copy", "Ctrl+C", "filePane"),
                action("rename", null, "Rename", "F2", "filePane"),
                action("renameShift", "rename", "Rename", "Shift+F6", "filePane"),
                action("copy", null, "Copy", "F5", "filePane"),
                action("extractAll", null, "Extract Anything", "Alt+F12", "global"),
                action("help", null, "Help", "F1", "global")));

        assertThat(actionRows(entries)).extracting(Entry::keys)
                .containsExactly("F1", "F2 / Shift+F6", "F5", "Alt+F12", "Ctrl+C");
    }

    @Test
    void listsPaletteOnlyActionsLastAndSkipsHiddenOnes() {
        List<Entry> entries = HelpTopics.entries(List.of(
                action("wipeDelete", null, "SDelete", null),
                action("mergePdf", null, "Merge PDF Files", null, "commandPalette"),
                action("analyzeFile", null, "Analyze File", null, "commandPalette"),
                action("view", null, "View", "F3", "filePane")));

        assertThat(actionRows(entries)).extracting(Entry::action, Entry::keys).containsExactly(
                tuple("View", "F3"),
                tuple("Analyze File", HelpTopics.PALETTE_ONLY),
                tuple("Merge PDF Files", HelpTopics.PALETTE_ONLY));
    }

    @Test
    void filterNeedsEveryWordInKeysActionDescriptionOrAliases() {
        Entry pack = new Entry("Archives & PDF", "F11", "Pack to Zip", "Packs the selected items.", List.of("archive"));

        assertThat(HelpTopics.matches(pack, "")).isTrue();
        assertThat(HelpTopics.matches(pack, "f11 ZIP")).isTrue();
        assertThat(HelpTopics.matches(pack, "archive selected")).isTrue();
        assertThat(HelpTopics.matches(pack, "zip pdf")).isFalse();
    }

    @Test
    void everyActionShownInHelpHasADescriptionAndAKnownCategory() throws IOException {
        List<ActionDefinition> actions = new AppConfigLoader().load(Path.of("config", "apps.json")).getActions();

        assertThat(HelpTopics.entries(actions)).filteredOn(entry -> entry.description().isBlank())
                .extracting(Entry::action)
                .as("apps.json actions shown in F1 help without a \"description\"")
                .isEmpty();
        assertThat(HelpTopics.entries(actions)).filteredOn(entry -> !HelpTopics.CATEGORIES.contains(entry.category()))
                .extracting(Entry::action)
                .as("apps.json actions shown in F1 help without a \"category\" from HelpTopics.CATEGORIES")
                .isEmpty();
    }

    @Test
    void everyAppsJsonLabelIsTitleCase() throws IOException {
        List<String> labels = new AppConfigLoader().load(Path.of("config", "apps.json")).getActions().stream()
                .map(ActionDefinition::getLabel).filter(java.util.Objects::nonNull).toList();

        assertThat(labels).filteredOn(label -> !isTitleCase(label))
                .as("apps.json labels not in Title Case (see .github/instructions/ui-text.instructions.md)")
                .isEmpty();
    }

    private static boolean isTitleCase(String label) {
        java.util.Set<String> minor = java.util.Set.of("a", "an", "the", "of", "to", "in", "on", "for", "and", "or", "as", "at", "by", "from");
        String[] words = label.trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (i > 0 && i < words.length - 1 && minor.contains(word.toLowerCase(java.util.Locale.ROOT))) {
                if (!word.equals(word.toLowerCase(java.util.Locale.ROOT))) {
                    return false;
                }
            } else if (Character.isLowerCase(word.codePointAt(0))) {
                return false;
            }
        }
        return true;
    }

    @Test
    void groupsByCategoryInSectionOrderKeepingRowOrder() {
        List<Entry> entries = List.of(
                new Entry("Media", "Command Palette", "Convert Audio Files", "", List.of()),
                new Entry("Getting Around", "F1", "Help", "", List.of()),
                new Entry("Media", "Command Palette", "Analyze", "", List.of()),
                new Entry("Getting Around", "Ctrl+R", "Refresh Panels", "", List.of()));

        assertThat(HelpTopics.byCategory(entries).keySet()).containsExactly("Getting Around", "Media");
        assertThat(HelpTopics.byCategory(entries).get("Media")).extracting(Entry::action)
                .containsExactly("Convert Audio Files", "Analyze");
    }

    private static List<Entry> actionRows(List<Entry> entries) {
        return entries.subList(HelpTopics.FIXED_KEYS.size(), entries.size());
    }

    private static ActionDefinition action(String id, String builtin, String label, String shortcut, String... contexts) {
        ActionDefinition action = new ActionDefinition();
        action.setId(id);
        action.setBuiltin(builtin);
        action.setLabel(label);
        action.setShortcut(shortcut);
        action.setDescription(label + " description.");
        action.setContexts(List.of(contexts));
        return action;
    }
}
