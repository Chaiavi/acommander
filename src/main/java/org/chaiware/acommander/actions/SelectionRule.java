package org.chaiware.acommander.actions;

import org.chaiware.acommander.model.FileItem;

import java.util.List;

public enum SelectionRule {
    NONE,
    ANY,
    SINGLE,
    MULTI,
    SINGLE_FILE,
    SINGLE_FOLDER;

    public static SelectionRule fromString(String value) {
        if (value == null) {
            return NONE;
        }
        return switch (value.toLowerCase()) {
            case "any" -> ANY;
            case "single" -> SINGLE;
            case "multi" -> MULTI;
            case "singlefile" -> SINGLE_FILE;
            case "singlefolder" -> SINGLE_FOLDER;
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
        };
    }
}
