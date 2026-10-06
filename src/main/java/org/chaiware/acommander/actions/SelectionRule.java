package org.chaiware.acommander.actions;

import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Locale;

public enum SelectionRule {
    NONE,
    ANY,
    SINGLE,
    MULTI,
    SINGLE_FILE,
    SINGLE_FOLDER,
    SINGLE_OR_MULTIPLE_FILES;

    public static SelectionRule fromString(String value) {
        if (value == null) {
            return NONE;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "any" -> ANY;
            case "single" -> SINGLE;
            case "multi" -> MULTI;
            case "singlefile" -> SINGLE_FILE;
            case "singlefolder" -> SINGLE_FOLDER;
            case "singleormultiplefiles" -> SINGLE_OR_MULTIPLE_FILES;
            default -> NONE;
        };
    }

    public boolean isSatisfied(List<FileItem> selectedItems) {
        int count = selectedItems == null ? 0 : selectedItems.size();
        return switch (this) {
            case NONE -> true;
            case ANY -> count > 0;
            case SINGLE -> count == 1;
            case MULTI -> count > 1;
            case SINGLE_FILE -> count == 1 && !selectedItems.getFirst().isDirectory();
            case SINGLE_FOLDER -> count == 1 && selectedItems.getFirst().isDirectory();
            case SINGLE_OR_MULTIPLE_FILES -> count >= 1 && selectedItems.stream().allMatch(item -> !item.isDirectory());
        };
    }

    /** Why {@code label} can't run on {@code selectedItems}, or null when it can. */
    public String blockedMessage(String label, List<FileItem> selectedItems) {
        if (isSatisfied(selectedItems)) {
            return null;
        }
        String needs = switch (this) {
            case NONE -> throw new IllegalStateException("NONE is always satisfied");
            case ANY -> "at least one selected item";
            case SINGLE -> "exactly one selected item";
            case MULTI -> "two or more selected items";
            case SINGLE_FILE -> "exactly one selected file";
            case SINGLE_FOLDER -> "exactly one selected folder";
            case SINGLE_OR_MULTIPLE_FILES -> "selected files only, no folders";
        };
        int count = selectedItems == null ? 0 : selectedItems.size();
        return label + " needs " + needs + " (" + count + " selected)";
    }
}
