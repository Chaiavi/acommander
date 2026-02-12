package org.chaiware.acommander.config;

import org.chaiware.acommander.keybinding.KeyBindingManager;

import java.util.Optional;

public enum ActionScope {
    FILE_PANE("filePane"),
    GLOBAL("global"),
    COMMAND_PALETTE("commandPalette"),
    PATH_COMBO("pathCombo"),
    DIALOG("dialog");

    private final String id;

    ActionScope(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<ActionScope> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (ActionScope scope : values()) {
            if (scope.id.equalsIgnoreCase(id)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }

    public static ActionScope fromKeyContext(KeyBindingManager.KeyContext context) {
        return switch (context) {
            case FILE_PANE -> FILE_PANE;
            case PATH_COMBO_BOX -> PATH_COMBO;
            case COMMAND_PALETTE -> COMMAND_PALETTE;
            case DIALOG -> DIALOG;
            case GLOBAL -> GLOBAL;
        };
    }
}
