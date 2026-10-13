package org.chaiware.acommander.dialog;

import javafx.scene.control.Label;
import javafx.stage.Window;

/** Asks a yes/no question; Enter confirms, Escape cancels. */
public final class ConfirmDialog {
    private ConfirmDialog() {
    }

    /** True when the user pressed {@code okLabel}. */
    public static boolean show(Window owner, String themeClass, String title, String message, String okLabel, String okTooltip) {
        OptionsDialog<Boolean> dialog = new OptionsDialog<>(owner, themeClass, title, okLabel, okTooltip);
        Label text = new Label(message);
        text.setWrapText(true);
        text.setMaxWidth(460);
        dialog.add(text);
        return dialog.showAndWait(() -> true).isPresent();
    }
}
