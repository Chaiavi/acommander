package org.chaiware.acommander.dialog;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.chaiware.acommander.helpers.BugReportUrl;

import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Collects a bug report or feature request; returns the prefilled GitHub issue URL. */
public final class ReportBugDialog {
    private ReportBugDialog() {
    }

    public static Optional<String> show(Window owner, String themeClass, String version) {
        OptionsDialog<String> dialog = new OptionsDialog<>(owner, themeClass,
                "Report Bug / Contact", "Submit", "Open a prefilled GitHub issue in your browser.");

        ComboBox<String> typeCombo = tip(new ComboBox<>(), "What kind of report this is; it sets the issue label.");
        typeCombo.getItems().addAll("Bug Report", "Feature Request", "Question", "Other");
        typeCombo.setValue("Bug Report");
        TextField titleField = tip(new TextField(), "A short summary that becomes the issue title.");
        titleField.setPromptText("Brief description of the issue");
        TextArea stepsArea = tip(new TextArea(), "What you did, step by step, so the problem can be repeated.");
        stepsArea.setPromptText("1.\n2.\n3.");
        stepsArea.setPrefRowCount(4);
        TextArea expectedArea = tip(new TextArea(), "What you expected to happen.");
        expectedArea.setPromptText("What should happen...");
        expectedArea.setPrefRowCount(2);
        TextArea actualArea = tip(new TextArea(), "What happened instead.");
        actualArea.setPromptText("What actually happens...");
        actualArea.setPrefRowCount(2);
        Label versionValue = new Label(version);
        versionValue.setStyle("-fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Type:"), typeCombo);
        grid.addRow(1, new Label("Title:"), titleField);
        grid.addRow(2, new Label("Steps to Reproduce:"), stepsArea);
        grid.addRow(3, new Label("Expected Behavior:"), expectedArea);
        grid.addRow(4, new Label("Actual Behavior:"), actualArea);
        grid.addRow(5, new Label("App Version:"), versionValue);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setVgrow(stepsArea, Priority.ALWAYS);

        dialog.add(grid);
        dialog.dialog().getDialogPane().setPrefSize(600, 520);
        return dialog.showAndWait(() -> BugReportUrl.build(typeCombo.getValue(), titleField.getText(),
                stepsArea.getText(), expectedArea.getText(), actualArea.getText(), version));
    }
}
