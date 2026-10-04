package org.chaiware.acommander.dialog;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.chaiware.acommander.services.ImageConversionService.ImageCompressionMode;
import org.chaiware.acommander.services.ImageConversionService.ImageConversionRequest;
import org.chaiware.acommander.services.ImageConversionService.ImageResizeMode;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.positiveIntOrNull;
import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks how Convert Graphics Files should convert, compress and resize the selected images. */
public final class ImageConversionDialog {
    private ImageConversionDialog() {
    }

    /** @param targetFormats lower-case extensions every selected image can be converted to; not empty */
    public static Optional<ImageConversionRequest> show(Window owner, String themeClass, int selectedCount,
                                                        String outputFolder, List<String> targetFormats) {
        OptionsDialog<ImageConversionRequest> dialog = new OptionsDialog<>(owner, themeClass,
                "Convert Graphics Files", "Convert", "Convert the selected images into the other pane.");

        ToggleGroup formatGroup = new ToggleGroup();
        VBox formatsBox = new VBox(8);
        for (String format : targetFormats) {
            String name = format.toUpperCase(Locale.ROOT);
            RadioButton formatRadio = tip(new RadioButton(name), "Write the images as " + name + " files.");
            formatRadio.setUserData(format);
            formatRadio.setToggleGroup(formatGroup);
            formatsBox.getChildren().add(formatRadio);
        }
        formatGroup.selectToggle(formatGroup.getToggles().getFirst());

        ToggleGroup compressionModeGroup = new ToggleGroup();
        RadioButton losslessMode = tip(new RadioButton("Lossless"), "Compress without losing any image detail.");
        RadioButton qualityMode = tip(new RadioButton("Lossy"), "Compress to the quality below; lower is smaller.");
        RadioButton targetSizeMode = tip(new RadioButton("Target Max Output Size"),
                "Lower the quality until each file fits the size below.");
        losslessMode.setUserData(ImageCompressionMode.LOSSLESS);
        qualityMode.setUserData(ImageCompressionMode.QUALITY);
        targetSizeMode.setUserData(ImageCompressionMode.MAX_SIZE);
        losslessMode.setToggleGroup(compressionModeGroup);
        qualityMode.setToggleGroup(compressionModeGroup);
        targetSizeMode.setToggleGroup(compressionModeGroup);
        losslessMode.setSelected(true);

        Slider qualitySlider = tip(new Slider(0, 100, 80), "Lossy quality from 0 (smallest) to 100 (best).");
        qualitySlider.setShowTickLabels(true);
        qualitySlider.setShowTickMarks(true);
        qualitySlider.setMajorTickUnit(20);
        qualitySlider.setMinorTickCount(4);
        qualitySlider.setBlockIncrement(1);
        Label qualityValue = new Label("80");
        qualitySlider.valueProperty().addListener((obs, oldValue, newValue) -> qualityValue.setText(String.valueOf(newValue.intValue())));
        HBox qualityRow = new HBox(10, new Label("Quality:"), qualitySlider, qualityValue);
        HBox.setHgrow(qualitySlider, Priority.ALWAYS);

        TextField maxSizeField = tip(new TextField(), "Largest allowed output file, such as 150KB or 1MB.");
        maxSizeField.setPromptText("Examples: 150KB, 1MB, 0.5MB");
        CheckBox keepExif = tip(new CheckBox("Keep EXIF Metadata"), "Copy camera and date metadata into the new files.");
        CheckBox keepDates = tip(new CheckBox("Keep Original File Dates"), "Give the new files the originals' modified dates.");

        ComboBox<ImageResizeMode> resizeMode = tip(new ComboBox<>(), "Which side the resize value below applies to.");
        resizeMode.getItems().addAll(ImageResizeMode.values());
        resizeMode.getSelectionModel().select(ImageResizeMode.NONE);
        TextField resizeValueField = tip(new TextField(), "New size in pixels for the chosen side.");
        resizeValueField.setPromptText("Pixels");
        CheckBox noUpscale = tip(new CheckBox("Do Not Upscale Resized Images"), "Leave images that are already smaller untouched.");

        TextField suffixField = tip(new TextField("_converted"), "Text added to each new file name before the extension.");
        suffixField.setPromptText("Filename suffix");
        ComboBox<String> overwritePolicy = tip(new ComboBox<>(),
                "What to do when the output file already exists: always replace, never replace, or replace only if bigger.");
        overwritePolicy.getItems().addAll("Always", "Never", "Bigger");
        overwritePolicy.getSelectionModel().select("Always");
        Label validationLabel = new Label();

        Runnable validate = () -> {
            ImageCompressionMode mode = (ImageCompressionMode) compressionModeGroup.getSelectedToggle().getUserData();
            ImageResizeMode resize = resizeMode.getValue();
            boolean resizing = resize != null && resize != ImageResizeMode.NONE;
            maxSizeField.setDisable(mode != ImageCompressionMode.MAX_SIZE);
            qualitySlider.setDisable(mode != ImageCompressionMode.QUALITY);
            resizeValueField.setDisable(!resizing);
            noUpscale.setDisable(!resizing);
            String problem = mode == ImageCompressionMode.MAX_SIZE && maxSizeField.getText().isBlank()
                    ? "Max size is required for target-size mode."
                    : resizing && positiveIntOrNull(resizeValueField.getText()) == null
                    ? "Resize value must be a positive number." : null;
            validationLabel.setText(problem == null ? "" : problem);
            dialog.okButton().setDisable(problem != null || formatGroup.getSelectedToggle() == null);
        };
        maxSizeField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        resizeValueField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        formatGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> validate.run());
        compressionModeGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> validate.run());
        resizeMode.valueProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        dialog.add(new Label("Selected files: " + selectedCount + " | Output folder: " + outputFolder), new Separator(),
                new Label("Convert to Format:"), formatsBox, new Separator(),
                new Label("Compression Mode:"), losslessMode, qualityMode, qualityRow, targetSizeMode, maxSizeField,
                new Separator(),
                new Label("Resize (Optional):"), new HBox(10, new Label("Mode:"), resizeMode),
                new HBox(10, new Label("Value:"), resizeValueField), noUpscale, new Separator(),
                new Label("Options:"), keepExif, keepDates, new Label("Filename Suffix:"), suffixField,
                new Label("Overwrite Policy:"), overwritePolicy, validationLabel);
        dialog.dialog().getDialogPane().setPrefSize(620, 760);

        return dialog.showAndWait(() -> {
            if (formatGroup.getSelectedToggle() == null) {
                return null;
            }
            ImageCompressionMode mode = (ImageCompressionMode) compressionModeGroup.getSelectedToggle().getUserData();
            ImageResizeMode resize = resizeMode.getValue() == null ? ImageResizeMode.NONE : resizeMode.getValue();
            return new ImageConversionRequest(
                    String.valueOf(formatGroup.getSelectedToggle().getUserData()),
                    mode,
                    mode == ImageCompressionMode.QUALITY ? (int) Math.round(qualitySlider.getValue()) : null,
                    mode == ImageCompressionMode.MAX_SIZE ? maxSizeField.getText().trim() : null,
                    keepExif.isSelected(),
                    keepDates.isSelected(),
                    resize,
                    resize == ImageResizeMode.NONE ? null : positiveIntOrNull(resizeValueField.getText()),
                    noUpscale.isSelected(),
                    suffixField.getText().trim(),
                    overwritePolicy.getValue());
        });
    }
}
