package org.chaiware.acommander.dialog;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A themed options dialog: a bold heading, rows of controls, an OK button that Enter fires (unless disabled)
 * and a Cancel button that Escape fires.
 */
public final class OptionsDialog<T> {
    private final Dialog<T> dialog = new Dialog<>();
    private final ButtonType okType;
    private final Button okButton;
    private final VBox content = new VBox(10);

    public OptionsDialog(Window owner, String themeClass, String title, String okLabel, String okTooltip) {
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        okType = new ButtonType(okLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);
        okButton = (Button) dialog.getDialogPane().lookupButton(okType);
        okButton.setDefaultButton(true);
        okButton.setTooltip(new Tooltip(okTooltip));
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setCancelButton(true);
        cancelButton.setTooltip(new Tooltip("Close this dialog without doing anything."));

        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        content.getChildren().add(heading);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ENTER
                    || event.getTarget() instanceof TextArea
                    || (event.getTarget() instanceof ComboBoxBase<?> combo && combo.isShowing())) {
                return;
            }
            if (!okButton.isDisabled()) {
                okButton.fire();
            }
            event.consume();
        });
        DialogTheme.apply(dialog, owner, themeClass);
    }

    /** Sets a control's one-sentence tooltip and returns the control, so it can be built inline. */
    public static <C extends Control> C tip(C control, String tooltip) {
        control.setTooltip(new Tooltip(tooltip));
        return control;
    }

    /** Appends rows below the heading. */
    public OptionsDialog<T> add(Node... rows) {
        content.getChildren().addAll(rows);
        return this;
    }

    public Button okButton() {
        return okButton;
    }

    public Dialog<T> dialog() {
        return dialog;
    }

    /** Shows the dialog; OK returns {@code result.get()}, Cancel / Escape / a null result return empty. */
    public Optional<T> showAndWait(Supplier<T> result) {
        dialog.setResultConverter(button -> button == okType ? result.get() : null);
        return dialog.showAndWait();
    }
}
