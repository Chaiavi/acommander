package org.chaiware.acommander.dialog;

import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.chaiware.acommander.helpers.ExecutableCompressionSupport.UpxAction;
import org.chaiware.acommander.model.FileItem;

import java.util.List;
import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks whether UPX should compress (and how hard) or decompress the selected executables. */
public final class ExecutableCompressionDialog {
    private ExecutableCompressionDialog() {
    }

    /** @param selectedItems not empty */
    public static Optional<UpxAction> show(Window owner, String themeClass, List<FileItem> selectedItems) {
        OptionsDialog<UpxAction> dialog = new OptionsDialog<>(owner, themeClass,
                "Compress Executable", "Run", "Run UPX on the selected files in place.");

        FileItem firstItem = selectedItems.getFirst();
        Label subtitle = new Label(selectedItems.size() == 1
                ? "Selected file: " + firstItem.getName()
                : "Selected files: " + selectedItems.size());
        subtitle.setWrapText(true);

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton compressMode = tip(new RadioButton("Compress"), "Make the executables smaller; they still run as before.");
        RadioButton decompressMode = tip(new RadioButton("Decompress"), "Restore executables that UPX compressed earlier.");
        compressMode.setToggleGroup(modeGroup);
        decompressMode.setToggleGroup(modeGroup);
        compressMode.setSelected(true);

        GridPane metadataGrid = new GridPane();
        metadataGrid.setHgap(12);
        metadataGrid.setVgap(6);
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(90);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setHgrow(Priority.ALWAYS);
        metadataGrid.getColumnConstraints().addAll(labelCol, valueCol);
        long sizeBytes = selectedItems.stream().mapToLong(FileItem::getSizeInBytes).sum();
        addRow(metadataGrid, 0, "Path:", firstItem.getFullPath());
        addRow(metadataGrid, 1, "Size:", sizeBytes > 0 ? FileItem.humanSize(sizeBytes) : "Unknown");
        addRow(metadataGrid, 2, "Modified:", firstItem.getDate());
        if (selectedItems.size() > 1) {
            addRow(metadataGrid, 3, "Selection:", selectedItems.size() + " files");
        }

        ToggleGroup levelGroup = new ToggleGroup();
        RadioButton good = tip(new RadioButton("Good Compression (-9)"), "Fast, and usually close to the best size.");
        RadioButton veryGood = tip(new RadioButton("Very Good Compression (Slow, --brute)"), "Tries many methods; takes much longer.");
        RadioButton best = tip(new RadioButton("Best Compression (Slowest, --ultra-brute)"), "Tries every method; can take minutes per file.");
        good.setUserData(UpxAction.GOOD);
        veryGood.setUserData(UpxAction.VERY_GOOD);
        best.setUserData(UpxAction.BEST);
        good.setToggleGroup(levelGroup);
        veryGood.setToggleGroup(levelGroup);
        best.setToggleGroup(levelGroup);
        good.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> {
            good.setDisable(decompressMode.isSelected());
            veryGood.setDisable(decompressMode.isSelected());
            best.setDisable(decompressMode.isSelected());
        });

        dialog.add(subtitle, new Separator(), new Label("Mode:"), compressMode, decompressMode, new Separator(),
                new Label("File Metadata:"), metadataGrid, new Separator(),
                new Label("Compression Level:"), good, veryGood, best);
        dialog.dialog().getDialogPane().setPrefSize(620, 460);
        dialog.dialog().setOnShown(event -> good.requestFocus());
        return dialog.showAndWait(() -> decompressMode.isSelected()
                ? UpxAction.DECOMPRESS : (UpxAction) levelGroup.getSelectedToggle().getUserData());
    }

    private static void addRow(GridPane grid, int row, String labelText, String valueText) {
        Label value = new Label(valueText == null ? "" : valueText);
        value.setWrapText(true);
        grid.addRow(row, new Label(labelText), value);
    }
}
