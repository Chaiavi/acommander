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
import org.chaiware.acommander.services.AudioConversionService;
import org.chaiware.acommander.services.AudioConversionService.AudioCompressionProfile;
import org.chaiware.acommander.services.AudioConversionService.AudioConversionRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.positiveIntOrNull;
import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks how Convert Audio Files should encode the selected files. */
public final class AudioConversionDialog {
    private AudioConversionDialog() {
    }

    /** @param targetFormats lower-case extensions every selected file can be converted to; not empty */
    public static Optional<AudioConversionRequest> show(Window owner, String themeClass, int selectedCount,
                                                        String outputFolder, List<String> targetFormats) {
        OptionsDialog<AudioConversionRequest> dialog = new OptionsDialog<>(owner, themeClass,
                "Convert Audio Files", "Convert", "Convert the selected audio files into the other pane.");

        ToggleGroup formatGroup = new ToggleGroup();
        VBox formatsBox = new VBox(8);
        for (String format : targetFormats) {
            String name = format.toUpperCase(Locale.ROOT);
            RadioButton formatRadio = tip(new RadioButton(name), "Write the audio as " + name + " files.");
            formatRadio.setUserData(format);
            formatRadio.setToggleGroup(formatGroup);
            formatsBox.getChildren().add(formatRadio);
        }
        formatGroup.selectToggle(formatGroup.getToggles().getFirst());

        ToggleGroup profileGroup = new ToggleGroup();
        RadioButton lossless = tip(new RadioButton("Lossless"), "Keep every bit of the audio; files stay large.");
        RadioButton lossy = tip(new RadioButton("Lossy"), "Use the format's default compressed encoding.");
        RadioButton custom = tip(new RadioButton("Custom Encoding"), "Pick the exact encoding from the list below.");
        lossless.setUserData(AudioCompressionProfile.LOSSLESS);
        lossy.setUserData(AudioCompressionProfile.LOSSY);
        custom.setUserData(AudioCompressionProfile.CUSTOM);
        lossless.setToggleGroup(profileGroup);
        lossy.setToggleGroup(profileGroup);
        custom.setToggleGroup(profileGroup);
        lossless.setSelected(true);

        ComboBox<String> encodingCombo = tip(new ComboBox<>(), "The encoding used for the chosen format and profile.");
        encodingCombo.setPrefWidth(260);
        Map<String, String> encodingChoices = new LinkedHashMap<>();

        CheckBox normalize = tip(new CheckBox("Normalize Output Audio"), "Raise or lower the volume to a standard peak level.");
        CheckBox overrideSampleRate = tip(new CheckBox("Override Sample Rate (Hz)"),
                "Resample the output to the rate on the right instead of keeping the source rate.");
        TextField sampleRateField = tip(new TextField("44100"), "Output sample rate in hertz, such as 44100 or 48000.");
        ComboBox<String> endianCombo = tip(new ComboBox<>(), "Byte order for raw formats; Auto keeps the format's default.");
        endianCombo.getItems().addAll("Auto", "CPU", "Little", "Big");
        endianCombo.getSelectionModel().select("Auto");
        TextField suffixField = tip(new TextField("_converted"), "Text added to each new file name before the extension.");
        suffixField.setPromptText("Filename suffix");
        ComboBox<String> conflictPolicy = tip(new ComboBox<>(),
                "What to do when the output file already exists: replace it, skip it, or pick a new name.");
        conflictPolicy.getItems().addAll("Overwrite", "Skip", "Auto-rename");
        conflictPolicy.getSelectionModel().select("Overwrite");
        Label validationLabel = new Label();

        Runnable syncEncodingChoices = () -> {
            AudioCompressionProfile profile = (AudioCompressionProfile) profileGroup.getSelectedToggle().getUserData();
            encodingChoices.clear();
            encodingChoices.putAll(AudioConversionService.encodingOptionsFor(selectedFormat(formatGroup), profile));
            encodingCombo.getItems().setAll(encodingChoices.keySet());
            encodingCombo.getSelectionModel().selectFirst();
            encodingCombo.setDisable(profile != AudioCompressionProfile.CUSTOM);
        };
        Runnable validate = () -> {
            sampleRateField.setDisable(!overrideSampleRate.isSelected());
            Integer sampleRate = overrideSampleRate.isSelected() ? positiveIntOrNull(sampleRateField.getText()) : null;
            String encodingFlag = encodingChoices.getOrDefault(encodingCombo.getValue(), "");
            String problem = formatGroup.getSelectedToggle() == null ? "Choose a target format."
                    : overrideSampleRate.isSelected() && sampleRate == null ? "Sample rate must be a positive number."
                    : encodingCombo.getValue() == null ? "Choose an encoding option."
                    : sampleRate != null && AudioConversionService.isOpus(selectedFormat(formatGroup), encodingFlag)
                    && !AudioConversionService.isOpusSampleRate(sampleRate)
                    ? "Opus sample rate must be one of: 8000, 12000, 16000, 24000, 48000." : null;
            validationLabel.setText(problem == null ? "" : problem);
            dialog.okButton().setDisable(problem != null);
        };
        formatGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {
            syncEncodingChoices.run();
            validate.run();
        });
        profileGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {
            syncEncodingChoices.run();
            validate.run();
        });
        overrideSampleRate.selectedProperty().addListener((obs, oldValue, newValue) -> validate.run());
        sampleRateField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        encodingCombo.valueProperty().addListener((obs, oldValue, newValue) -> validate.run());
        syncEncodingChoices.run();
        validate.run();

        dialog.add(new Label("Selected files: " + selectedCount + " | Output folder: " + outputFolder), new Separator(),
                new Label("Convert to Format:"), formatsBox, new Separator(),
                new Label("Compression Profile:"), lossless, lossy, custom,
                new HBox(10, new Label("Encoding:"), encodingCombo), new Separator(),
                new Label("Options:"), normalize, new HBox(10, overrideSampleRate, sampleRateField),
                new HBox(10, new Label("Endian:"), endianCombo), new Label("Filename Suffix:"), suffixField,
                new Label("If Output File Exists:"), conflictPolicy, validationLabel);
        dialog.dialog().getDialogPane().setPrefSize(620, 700);

        return dialog.showAndWait(() -> formatGroup.getSelectedToggle() == null ? null : new AudioConversionRequest(
                selectedFormat(formatGroup),
                (AudioCompressionProfile) profileGroup.getSelectedToggle().getUserData(),
                encodingChoices.getOrDefault(encodingCombo.getValue(), ""),
                normalize.isSelected(),
                overrideSampleRate.isSelected() ? positiveIntOrNull(sampleRateField.getText()) : null,
                endianCombo.getValue(),
                suffixField.getText().trim(),
                conflictPolicy.getValue()));
    }

    private static String selectedFormat(ToggleGroup formatGroup) {
        return formatGroup.getSelectedToggle() == null ? null : String.valueOf(formatGroup.getSelectedToggle().getUserData());
    }
}
