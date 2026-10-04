package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Locale;

/** Type-to-filter for one pane: each typed char narrows the list to names starting with the typed text. */
public final class IncrementalFilter {
    private List<FileItem> base;
    private String prefix = "";

    public String prefix() {
        return prefix;
    }

    public boolean isActive() {
        return !prefix.isEmpty();
    }

    /** The items to show: names starting with the prefix (any case); ".." always stays. */
    public List<FileItem> visibleItems() {
        if (base == null) {
            return List.of();
        }
        return base.stream()
                .filter(item -> "..".equals(item.getPresentableFilename())
                        || item.getPresentableFilename().toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

    /** Adds a typed char. If the pane no longer shows this filter's result (refresh, navigation), starts over from {@code shown}. */
    public void type(char typed, List<FileItem> shown) {
        if (base == null || !shown.equals(visibleItems())) {
            base = List.copyOf(shown);
            prefix = "";
        }
        prefix += Character.toLowerCase(typed);
    }

    public void backspace() {
        if (isActive()) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
    }

    /** Ends filtering; returns the full list to put back, or null when nothing was filtered. */
    public List<FileItem> clear() {
        List<FileItem> full = base;
        base = null;
        prefix = "";
        return full;
    }
}
