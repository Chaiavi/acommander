package org.chaiware.acommander.dialog;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

/** Gives a dialog the app's theme: the theme style class plus the owner window's stylesheets. */
public final class DialogTheme {
    public static final String DARK = "theme-dark";
    public static final String LIGHT = "theme-light";

    private DialogTheme() {
    }

    public static void apply(Dialog<?> dialog, Window owner, String themeClass) {
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().removeAll(DARK, LIGHT);
        pane.getStyleClass().add(themeClass);
        if (owner == null || owner.getScene() == null) {
            return;
        }
        for (String stylesheet : owner.getScene().getStylesheets()) {
            if (!pane.getStylesheets().contains(stylesheet)) {
                pane.getStylesheets().add(stylesheet);
            }
        }
    }
}
