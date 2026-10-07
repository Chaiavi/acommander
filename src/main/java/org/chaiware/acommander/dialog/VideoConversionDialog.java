package org.chaiware.acommander.dialog;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.chaiware.acommander.services.MediaConversionService.Quality;
import org.chaiware.acommander.services.MediaConversionService.VideoFormat;
import org.chaiware.acommander.services.MediaConversionService.VideoRequest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks how Convert Video Files should convert the selected videos, or which audio to extract from them. */
public final class VideoConversionDialog {
    private static final Map<String, Integer> SIZES = new LinkedHashMap<>();

    static {
        SIZES.put("Keep Original", null);
        SIZES.put("1080p", 1080);
        SIZES.put("720p", 720);
        SIZES.put("480p", 480);
    }

    private VideoConversionDialog() {
    }

    public static Optional<VideoRequest> show(Window owner, String themeClass, int selectedCount, String outputFolder) {
        OptionsDialog<VideoRequest> dialog = new OptionsDialog<>(owner, themeClass,
                "Convert Video Files", "Convert", "Convert the selected videos into the other pane.");

        ToggleGroup formatGroup = new ToggleGroup();
        VBox video = new VBox(6,
                format(formatGroup, VideoFormat.MP4_H264, "MP4 (H.264)", "Plays on every device and browser."),
                format(formatGroup, VideoFormat.MP4_H265, "MP4 (H.265)", "About half the size of H.264 at the same quality; slower to convert."),
                format(formatGroup, VideoFormat.MP4_COPY, "MP4, Same Streams",
                        "Moves the video and audio into an MP4 file without re-encoding; fast and lossless."),
                format(formatGroup, VideoFormat.MKV_COPY, "MKV, Same Streams",
                        "Moves every stream, subtitles too, into an MKV file without re-encoding; fast and lossless."));
        VBox audio = new VBox(6,
                format(formatGroup, VideoFormat.MP3, "MP3", "Extracts the sound track as an MP3 file."),
                format(formatGroup, VideoFormat.M4A, "M4A (AAC)", "Extracts the sound track as an M4A file."),
                format(formatGroup, VideoFormat.ORIGINAL_AUDIO, "Original Audio",
                        "Extracts the first sound track as it is, without re-encoding."));
        formatGroup.selectToggle(formatGroup.getToggles().getFirst());

        ComboBox<String> quality = tip(new ComboBox<>(), "Higher quality makes bigger files; ignored when nothing is re-encoded.");
        quality.getItems().addAll("High", "Normal", "Small");
        quality.getSelectionModel().select("Normal");
        ComboBox<String> size = tip(new ComboBox<>(), "Shrinks taller videos to this height; a smaller video keeps its size.");
        size.getItems().addAll(SIZES.keySet());
        size.getSelectionModel().selectFirst();
        TextField suffixField = tip(new TextField("_converted"), "Text added to each new file name before the extension.");
        ComboBox<String> conflictPolicy = tip(new ComboBox<>(),
                "What to do when the output file already exists: replace it, skip it, or pick a new name.");
        conflictPolicy.getItems().addAll("Overwrite", "Skip", "Auto-rename");
        conflictPolicy.getSelectionModel().select("Overwrite");

        Runnable sync = () -> {
            VideoFormat format = selectedFormat(formatGroup);
            quality.setDisable(!format.usesQuality());
            size.setDisable(!format.reencodesVideo());
        };
        formatGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> sync.run());
        sync.run();

        dialog.add(new Label("Selected files: " + selectedCount + " | Output folder: " + outputFolder), new Separator(),
                new HBox(30, new VBox(6, new Label("Video:"), video), new VBox(6, new Label("Audio Only:"), audio)),
                new Separator(), new HBox(10, new Label("Quality:"), quality), new HBox(10, new Label("Size:"), size),
                new Separator(), new VBox(4, new Label("Filename Suffix:"), suffixField),
                new VBox(4, new Label("If Output File Exists:"), conflictPolicy));

        return dialog.showAndWait(() -> new VideoRequest(
                selectedFormat(formatGroup),
                Quality.valueOf(quality.getValue().toUpperCase(Locale.ROOT)),
                SIZES.get(size.getValue()),
                suffixField.getText().trim(),
                conflictPolicy.getValue()));
    }

    private static RadioButton format(ToggleGroup group, VideoFormat format, String label, String tooltip) {
        RadioButton radio = tip(new RadioButton(label), tooltip);
        radio.setUserData(format);
        radio.setToggleGroup(group);
        return radio;
    }

    private static VideoFormat selectedFormat(ToggleGroup formatGroup) {
        return (VideoFormat) formatGroup.getSelectedToggle().getUserData();
    }
}
