package org.chaiware.acommander.dialog;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

/** The app's dark / light theme: a style class on the window root, copied onto each dialog with the stylesheets. */
public final class DialogTheme {
    public static final String DARK = "theme-dark";
    public static final String LIGHT = "theme-light";

    public enum ThemeMode {
        DARK("dark", DialogTheme.DARK),
        REGULAR("regular", DialogTheme.LIGHT);

        /** The {@code theme_mode} value in the settings file. */
        public final String configValue;
        public final String styleClass;

        ThemeMode(String configValue, String styleClass) {
            this.configValue = configValue;
            this.styleClass = styleClass;
        }

        /** "dark" is dark; anything else (light, regular, blank, unknown) is the regular theme. */
        public static ThemeMode from(String value) {
            return "dark".equalsIgnoreCase(value) ? DARK : REGULAR;
        }
    }

    private DialogTheme() {
    }

    public static void apply(Scene scene, ThemeMode mode) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        scene.getRoot().getStyleClass().removeAll(DARK, LIGHT);
        scene.getRoot().getStyleClass().add(mode.styleClass);
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
