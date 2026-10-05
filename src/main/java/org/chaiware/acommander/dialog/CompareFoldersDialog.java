package org.chaiware.acommander.dialog;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.stage.Window;
import org.chaiware.acommander.services.FolderComparer;

import java.nio.file.Path;
import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks how Compare Folders should match the two panes. */
public final class CompareFoldersDialog {
    private CompareFoldersDialog() {
    }

    public static Optional<FolderComparer.Options> show(Window owner, String themeClass, Path leftRoot, Path rightRoot) {
        OptionsDialog<FolderComparer.Options> dialog = new OptionsDialog<>(owner, themeClass,
                "Compare Folders", "Compare", "Mark the files that differ between the two panes.");

        Label subtitle = new Label("Left: " + leftRoot + " | Right: " + rightRoot);
        subtitle.setWrapText(true);
        CheckBox compareByDate = tip(new CheckBox("Compare Also by Date"),
                "Treat files with the same size but a different modified date as different.");
        CheckBox compareContents = tip(new CheckBox("Compare File Contents (Slower)"),
                "Read both files byte by byte when their sizes match, instead of trusting size and date.");
        CheckBox recursive = tip(new CheckBox("Compare Also Subfolders (Recursively)"),
                "Walk into subfolders on both sides, not only the top level.");
        recursive.setSelected(true);
        CheckBox caseSensitive = tip(new CheckBox("Case-Sensitive Filename Matching"),
                "Pair files only when their names match in upper and lower case too.");

        dialog.add(subtitle, new Separator(), compareByDate, compareContents, recursive, caseSensitive);
        dialog.dialog().getDialogPane().setPrefSize(700, 320);
        return dialog.showAndWait(() -> new FolderComparer.Options(
                compareByDate.isSelected(),
                compareContents.isSelected(),
                recursive.isSelected(),
                caseSensitive.isSelected()));
    }
}
