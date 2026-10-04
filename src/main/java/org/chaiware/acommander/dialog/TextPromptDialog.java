package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** A one-line text prompt (rename, new folder, bookmark name, ...). */
public final class TextPromptDialog {
    private TextPromptDialog() {
    }

    /** @param selectionEnd how much of {@code defaultValue} starts selected, e.g. the name without its extension */
    public static Optional<String> show(Window owner, String themeClass, String defaultValue, String title, String question,
                                        int selectionEnd) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setHeaderText("");
        dialog.setTitle(title);
        dialog.setContentText(question);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        TextField editor = tip(dialog.getEditor(), "Type the text and press Enter to confirm.");
        editor.setPrefWidth(300);
        tip((Button) dialog.getDialogPane().lookupButton(ButtonType.OK), "Confirm the text.");
        tip((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL), "Close this dialog without doing anything.");
        // Left / Right collapse the selection and move by one character instead of jumping to its edge.
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isAltDown() || event.isControlDown() || event.isMetaDown() || event.isShiftDown()) {
                return;
            }
            if (event.getCode() == KeyCode.LEFT) {
                editor.backward();
                event.consume();
            } else if (event.getCode() == KeyCode.RIGHT) {
                editor.forward();
                event.consume();
            }
        });
        dialog.setOnShown(event -> Platform.runLater(() -> {
            int textLength = editor.getText() == null ? 0 : editor.getText().length();
            editor.requestFocus();
            editor.selectRange(0, Math.max(0, Math.min(selectionEnd, textLength)));
        }));
        DialogTheme.apply(dialog, owner, themeClass);
        return dialog.showAndWait();
    }
}
