package org.chaiware.acommander.dialog;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import org.chaiware.acommander.services.MediaConversionService;
import org.chaiware.acommander.services.MediaConversionService.TrimRequest;

import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks which part of an audio or video file Trim Media keeps. */
public final class TrimMediaDialog {
    private TrimMediaDialog() {
    }

    public static Optional<TrimRequest> show(Window owner, String themeClass, String fileName, boolean video) {
        OptionsDialog<TrimRequest> dialog = new OptionsDialog<>(owner, themeClass,
                "Trim Media", "Trim", "Write the chosen part as a new file in the other pane.");

        TextField start = tip(new TextField("0:00"), "Where the kept part starts: seconds, m:ss or h:mm:ss, with optional .ms.");
        TextField end = tip(new TextField(), "Where the kept part ends, in the same form; empty keeps everything to the end.");
        end.setPromptText("End of file");
        CheckBox exact = tip(new CheckBox("Exact Cut (Re-encode to MP4)"),
                "Cuts on the exact frame by re-encoding; otherwise the cut starts at the nearest keyframe, without quality loss.");
        exact.setDisable(!video);
        Label validation = new Label();

        Runnable validate = () -> {
            Long startMillis = MediaConversionService.parseTime(start.getText());
            Long endMillis = end.getText().isBlank() ? null : MediaConversionService.parseTime(end.getText());
            String problem = startMillis == null ? "Start must be a time like 90, 1:30 or 1:02:03."
                    : !end.getText().isBlank() && endMillis == null ? "End must be a time like 90, 1:30 or 1:02:03."
                    : endMillis != null && endMillis <= startMillis ? "End must be after start." : null;
            validation.setText(problem == null ? "" : problem);
            dialog.okButton().setDisable(problem != null);
        };
        start.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        end.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        dialog.add(new Label("File: " + fileName), new HBox(10, new Label("Start:"), start),
                new HBox(10, new Label("End:"), end), exact, validation);

        return dialog.showAndWait(() -> new TrimRequest(MediaConversionService.parseTime(start.getText()),
                end.getText().isBlank() ? null : MediaConversionService.parseTime(end.getText()), exact.isSelected()));
    }
}
