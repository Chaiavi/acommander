package org.chaiware.acommander.dialog;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.tools.BundledToolCommands.CompareFilesOptions;
import org.chaiware.acommander.tools.BundledToolCommands.WhiteSpaceCompareMode;

import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks how ExamDiff should compare the two selected files. */
public final class CompareFilesDialog {
    private CompareFilesDialog() {
    }

    public static Optional<CompareFilesOptions> show(Window owner, String themeClass, FileItem leftFile, FileItem rightFile) {
        OptionsDialog<CompareFilesOptions> dialog = new OptionsDialog<>(owner, themeClass,
                "Compare Files", "Compare", "Open both files side by side in ExamDiff.");

        Label subtitle = new Label("Left: " + leftFile.getName() + " | Right: " + rightFile.getName());
        subtitle.setWrapText(true);
        Label leftPath = new Label("Left file: " + leftFile.getFullPath());
        leftPath.setWrapText(true);
        Label rightPath = new Label("Right file: " + rightFile.getFullPath());
        rightPath.setWrapText(true);

        CheckBox ignoreCase = tip(new CheckBox("Ignore Case"), "Treat upper and lower case letters as the same.");
        ComboBox<WhiteSpaceCompareMode> whitespaceMode = tip(new ComboBox<>(),
                "Choose which whitespace differences ExamDiff should ignore.");
        whitespaceMode.getItems().addAll(WhiteSpaceCompareMode.values());
        whitespaceMode.getSelectionModel().select(WhiteSpaceCompareMode.NONE);
        whitespaceMode.setPrefWidth(340);
        CheckBox differencesOnly = tip(new CheckBox("Show Differences Only"), "Hide the lines that are the same in both files.");

        dialog.add(subtitle, new Separator(), leftPath, rightPath, new Separator(), ignoreCase,
                new HBox(10, new Label("Whitespace Handling:"), whitespaceMode), differencesOnly);
        dialog.dialog().getDialogPane().setPrefSize(680, 340);
        return dialog.showAndWait(() -> new CompareFilesOptions(
                ignoreCase.isSelected(),
                Optional.ofNullable(whitespaceMode.getValue()).orElse(WhiteSpaceCompareMode.NONE),
                differencesOnly.isSelected()));
    }
}
