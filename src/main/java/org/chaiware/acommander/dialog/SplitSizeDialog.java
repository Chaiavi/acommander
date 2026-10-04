package org.chaiware.acommander.dialog;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.BundledToolCommands;
import org.chaiware.acommander.tools.BundledToolCommands.SplitSize;

import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks for the size of each part when splitting a large file; returns the 7-Zip volume argument. */
public final class SplitSizeDialog {
    private SplitSizeDialog() {
    }

    public static Optional<String> show(Window owner, String themeClass, String fileName, long originalFileSize) {
        OptionsDialog<String> dialog = new OptionsDialog<>(owner, themeClass,
                "Split a Large File", "Split", "Split the file into parts of this size.");

        TextField sizeField = tip(new TextField("16m"), "Size of each part: a number with k, m or g, such as 16m.");
        sizeField.setPromptText("Chunk size (examples: 16m, 64k, 1g, 16mb)");
        Label validationLabel = new Label();
        validationLabel.setWrapText(true);
        dialog.add(new Label("File: " + fileName + " (" + FileItem.humanSize(originalFileSize) + ")"),
                new Label("Size per Split File:"), sizeField, validationLabel);

        SplitSize[] parsed = new SplitSize[1];
        Runnable validate = () -> {
            parsed[0] = BundledToolCommands.parseSplitSize(sizeField.getText());
            String problem = !parsed[0].valid() ? parsed[0].message()
                    : parsed[0].bytes() > originalFileSize
                    ? "Requested split size (" + FileItem.humanSize(parsed[0].bytes()) + ") is larger than the original file ("
                    + FileItem.humanSize(originalFileSize) + "). Splitting does not make sense."
                    : null;
            dialog.okButton().setDisable(problem != null);
            validationLabel.setText(problem != null ? problem
                    : "This will create " + (originalFileSize + parsed[0].bytes() - 1) / parsed[0].bytes() + " file(s).");
        };
        sizeField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        return dialog.showAndWait(() -> parsed[0] != null && parsed[0].valid() ? parsed[0].sevenZipArg() : null);
    }
}
