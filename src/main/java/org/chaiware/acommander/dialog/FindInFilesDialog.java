package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.chaiware.acommander.tools.BundledToolCommands.FindInFilesOptions;

import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks what text Find in Files should look for, and where. */
public final class FindInFilesDialog {
    private FindInFilesDialog() {
    }

    public static Optional<FindInFilesOptions> show(Window owner, String themeClass, String folder) {
        OptionsDialog<FindInFilesOptions> dialog = new OptionsDialog<>(owner, themeClass,
                "Find in Files", "Find", "List the files in this folder and below that contain the text.");

        TextField queryField = tip(new TextField(), "The exact text to look for inside the files.");
        queryField.setPromptText("Text to find");
        CheckBox caseInsensitive = tip(new CheckBox("Case Insensitive"), "Match the text in any mix of upper and lower case.");
        CheckBox findInSpecificExtension = tip(new CheckBox("Find in Specific Extension"),
                "Search only files with the extension below.");
        TextField extensionField = tip(new TextField(), "File extension to search, without the dot.");
        extensionField.setPromptText("Example: java");
        extensionField.setDisable(true);
        CheckBox includeHiddenAndIgnored = tip(new CheckBox("Search Including Hidden & Ignored Files"),
                "Also search hidden files and files that .gitignore excludes.");

        dialog.add(new Label("Find text in: " + folder), queryField, caseInsensitive, findInSpecificExtension,
                extensionField, includeHiddenAndIgnored);

        Runnable validate = () -> {
            extensionField.setDisable(!findInSpecificExtension.isSelected());
            dialog.okButton().setDisable(queryField.getText().isBlank()
                    || (findInSpecificExtension.isSelected() && extensionField.getText().isBlank()));
        };
        queryField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        extensionField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        findInSpecificExtension.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (!selected) {
                extensionField.clear();
            }
            validate.run();
        });
        validate.run();

        dialog.dialog().setOnShown(event -> Platform.runLater(queryField::requestFocus));
        return dialog.showAndWait(() -> new FindInFilesOptions(
                queryField.getText().trim(),
                caseInsensitive.isSelected(),
                findInSpecificExtension.isSelected(),
                extensionField.getText().trim(),
                includeHiddenAndIgnored.isSelected()));
    }
}
