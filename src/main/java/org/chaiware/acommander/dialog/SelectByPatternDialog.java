package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks for a wildcard or regex pattern; a blank pattern selects everything. */
public final class SelectByPatternDialog {
    public record Result(String pattern, boolean useRegex) {}

    private SelectByPatternDialog() {
    }

    public static Optional<Result> show(Window owner, String themeClass, String lastPattern) {
        OptionsDialog<Result> dialog = new OptionsDialog<>(owner, themeClass,
                "Select by Pattern", "Select", "Select the files in this pane whose names match the pattern.");

        TextField patternField = tip(new TextField(lastPattern), "Wildcards: * matches any text and ? matches one character.");
        patternField.setPromptText("Example: *.java or file_??.txt");
        CheckBox regexCheckBox = tip(new CheckBox("Regular Expression"), "Treat the pattern as a Java regular expression.");
        Label validationLabel = new Label();

        dialog.add(new Label("Pattern:"), patternField, regexCheckBox, validationLabel);
        Runnable validate = () -> {
            String problem = null;
            if (regexCheckBox.isSelected()) {
                try {
                    Pattern.compile(patternField.getText().trim());
                } catch (PatternSyntaxException ex) {
                    problem = "Invalid regular expression: " + ex.getDescription();
                }
            }
            validationLabel.setText(problem == null ? "" : problem);
            dialog.okButton().setDisable(problem != null);
        };
        patternField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        regexCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();
        dialog.dialog().setOnShown(event -> Platform.runLater(patternField::requestFocus));
        return dialog.showAndWait(() -> new Result(
                !patternField.getText().isBlank() ? patternField.getText().trim() : regexCheckBox.isSelected() ? ".*" : "*.*",
                regexCheckBox.isSelected()));
    }
}
