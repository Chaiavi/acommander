package org.chaiware.acommander.dialog;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.chaiware.acommander.services.MediaConversionService;
import org.chaiware.acommander.services.MediaConversionService.AudioRequest;
import org.chaiware.acommander.services.MediaConversionService.Quality;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks how Convert Audio Files should encode the selected files. */
public final class AudioConversionDialog {
    private static final Map<String, Integer> SAMPLE_RATES = new LinkedHashMap<>();

    static {
        SAMPLE_RATES.put("Keep Original", null);
        SAMPLE_RATES.put("44100 Hz", 44100);
        SAMPLE_RATES.put("48000 Hz", 48000);
    }

    private AudioConversionDialog() {
    }

    public static Optional<AudioRequest> show(Window owner, String themeClass, int selectedCount, String outputFolder) {
        OptionsDialog<AudioRequest> dialog = new OptionsDialog<>(owner, themeClass,
                "Convert Audio Files", "Convert", "Convert the selected audio files into the other pane.");

        ToggleGroup formatGroup = new ToggleGroup();
        HBox formatsBox = new HBox(12);
        for (String format : MediaConversionService.AUDIO_FORMATS) {
            String name = format.toUpperCase(Locale.ROOT);
            RadioButton formatRadio = tip(new RadioButton(name), "Write the audio as " + name + " files.");
            formatRadio.setUserData(format);
            formatRadio.setToggleGroup(formatGroup);
            formatsBox.getChildren().add(formatRadio);
        }
        formatGroup.selectToggle(formatGroup.getToggles().getFirst());

        ComboBox<String> quality = tip(new ComboBox<>(), "Higher quality makes bigger files; lossless formats ignore it.");
        quality.getItems().addAll("High", "Normal", "Small");
        quality.getSelectionModel().select("Normal");
        ComboBox<String> sampleRate = tip(new ComboBox<>(), "Resample the audio to this rate, or keep the source rate.");
        sampleRate.getItems().addAll(SAMPLE_RATES.keySet());
        sampleRate.getSelectionModel().selectFirst();
        CheckBox normalize = tip(new CheckBox("Normalize Loudness"),
                "Evens out the volume to a standard loudness level; Keep Original then writes 48000 Hz.");
        TextField suffixField = tip(new TextField("_converted"), "Text added to each new file name before the extension.");
        suffixField.setPromptText("Filename suffix");
        ComboBox<String> conflictPolicy = tip(new ComboBox<>(),
                "What to do when the output file already exists: replace it, skip it, or pick a new name.");
        conflictPolicy.getItems().addAll("Overwrite", "Skip", "Auto-rename");
        conflictPolicy.getSelectionModel().select("Overwrite");

        Runnable sync = () -> {
            String format = selectedFormat(formatGroup);
            quality.setDisable(MediaConversionService.isLossless(format));
            sampleRate.setDisable("opus".equals(format));
        };
        formatGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> sync.run());
        sync.run();

        dialog.add(new Label("Selected files: " + selectedCount + " | Output folder: " + outputFolder), new Separator(),
                new Label("Convert to Format:"), formatsBox, new Separator(),
                new HBox(10, new Label("Quality:"), quality), new HBox(10, new Label("Sample Rate:"), sampleRate),
                normalize, new Separator(), new VBox(4, new Label("Filename Suffix:"), suffixField),
                new VBox(4, new Label("If Output File Exists:"), conflictPolicy));

        return dialog.showAndWait(() -> formatGroup.getSelectedToggle() == null ? null : new AudioRequest(
                selectedFormat(formatGroup),
                Quality.valueOf(quality.getValue().toUpperCase(Locale.ROOT)),
                "opus".equals(selectedFormat(formatGroup)) ? null : SAMPLE_RATES.get(sampleRate.getValue()),
                normalize.isSelected(),
                suffixField.getText().trim(),
                conflictPolicy.getValue()));
    }

    private static String selectedFormat(ToggleGroup formatGroup) {
        return formatGroup.getSelectedToggle() == null ? null : String.valueOf(formatGroup.getSelectedToggle().getUserData());
    }
}
