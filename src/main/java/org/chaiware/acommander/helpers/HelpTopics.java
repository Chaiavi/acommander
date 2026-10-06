package org.chaiware.acommander.helpers;

import org.chaiware.acommander.config.ActionDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The F1 help rows: fixed keys, then apps.json actions with shortcuts (F-key order), then palette-only actions. */
public final class HelpTopics {
    public static final String PALETTE_ONLY = "Command Palette";

    /** Keys handled in code, not in apps.json. */
    static final List<Entry> FIXED_KEYS = List.of(
            new Entry("Tab", "Switch Pane", "Moves the focus to the other pane.", List.of()),
            new Entry("Enter / Double-Click", "Open", "Opens a folder or archive, or opens a file with its default app.", List.of()),
            new Entry("Backspace", "Go Up", "Goes to the parent folder; at the root of an archive or FTP site, leaves it.", List.of()),
            new Entry("A-Z / 0-9", "Filter", "Shows only the items starting with what you type; Backspace removes a character.", List.of("type")),
            new Entry("Num +", "Select by Pattern", "Selects the items whose names match a wildcard or regex.", List.of()),
            new Entry("Num -", "Unselect All", "Clears the selection.", List.of()),
            new Entry("Num *", "Invert Selection", "Selects what is not selected and unselects the rest.", List.of()),
            new Entry("Hold Alt / Shift", "Bottom Buttons", "Shows what the F-key buttons do with that key held.", List.of()),
            new Entry("Esc", "Close", "Closes the Command Palette or a dialog.", List.of()));

    public static final List<String> TIPS = List.of(
            "Most actions work on the selected items in the focused pane.",
            "Copy, move, delete and pack run in the background; Stop ends the whole operation.",
            "Name sorting is numeric-aware, so file2 comes before file10.",
            "Every action and shortcut is defined in config/apps.json.");

    private static final Pattern FUNCTION_KEY = Pattern.compile("^(?:(.*)\\+)?F(\\d{1,2})$");

    private HelpTopics() {
    }

    public record Entry(String keys, String action, String description, List<String> aliases) {
    }

    /** Fixed keys, then one row per action (shortcuts of the same handler and label merged), skipping hidden actions. */
    public static List<Entry> entries(List<ActionDefinition> actions) {
        Map<String, List<ActionDefinition>> byHandler = new LinkedHashMap<>();
        for (ActionDefinition action : actions) {
            if (action.getContexts().isEmpty()) {
                continue;
            }
            String handler = (action.getBuiltin() == null ? action.getId() : action.getBuiltin()) + "|" + action.getLabel();
            byHandler.computeIfAbsent(handler, key -> new ArrayList<>()).add(action);
        }

        List<Entry> withShortcut = new ArrayList<>();
        List<Entry> paletteOnly = new ArrayList<>();
        for (List<ActionDefinition> group : byHandler.values()) {
            ActionDefinition main = group.get(0);
            List<String> shortcuts = group.stream()
                    .map(ActionDefinition::getShortcut)
                    .filter(shortcut -> shortcut != null && !shortcut.isBlank())
                    .toList();
            String description = group.stream()
                    .map(ActionDefinition::getDescription)
                    .filter(text -> text != null && !text.isBlank())
                    .findFirst().orElse("");
            if (!shortcuts.isEmpty()) {
                withShortcut.add(new Entry(String.join(" / ", shortcuts), main.getLabel(), description, main.getAliases()));
            } else if (main.getContexts().contains("commandPalette")) {
                paletteOnly.add(new Entry(PALETTE_ONLY, main.getLabel(), description, main.getAliases()));
            }
        }
        withShortcut.sort(Comparator.comparing((Entry entry) -> shortcutOrder(entry.keys())));
        paletteOnly.sort(Comparator.comparing(entry -> entry.action().toLowerCase(Locale.ROOT)));

        List<Entry> all = new ArrayList<>(FIXED_KEYS);
        all.addAll(withShortcut);
        all.addAll(paletteOnly);
        return all;
    }

    /** True when every word of the query is in the row's keys, action, description or aliases. */
    public static boolean matches(Entry entry, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String text = String.join(" ", entry.keys(), entry.action(), entry.description(), String.join(" ", entry.aliases()))
                .toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    // F1..F12 by number, unmodified before Alt/Shift; then every other shortcut alphabetically.
    private static String shortcutOrder(String keys) {
        String first = keys.split(" / ")[0];
        Matcher fKey = FUNCTION_KEY.matcher(first);
        if (fKey.matches()) {
            String modifiers = fKey.group(1) == null ? "" : fKey.group(1);
            return "0" + String.format("%02d", Integer.parseInt(fKey.group(2))) + modifiers;
        }
        return "1" + first;
    }
}
