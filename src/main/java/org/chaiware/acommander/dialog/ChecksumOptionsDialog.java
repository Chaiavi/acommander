package org.chaiware.acommander.dialog;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.chaiware.acommander.tools.BundledToolCommands.ChecksumOptions;

import java.util.List;
import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks which hash Checksum File / Checksum Folder Contents should compute, and how to print it. */
public final class ChecksumOptionsDialog {
    private static final List<String> ALGORITHMS = List.of("MD5", "SHA1", "SHA256", "SHA512", "CRC32");

    private ChecksumOptionsDialog() {
    }

    public static Optional<ChecksumOptions> show(Window owner, String themeClass, String title, String selectedName,
                                                 boolean includeNamesByDefault) {
        OptionsDialog<ChecksumOptions> dialog = new OptionsDialog<>(owner, themeClass,
                title, "Compute", "Compute the checksum with the chosen options.");

        ToggleGroup hashToggle = new ToggleGroup();
        GridPane hashGrid = new GridPane();
        hashGrid.setHgap(16);
        hashGrid.setVgap(8);
        for (int i = 0; i < ALGORITHMS.size(); i++) {
            String algorithm = ALGORITHMS.get(i);
            RadioButton button = tip(new RadioButton(algorithm), "Compute a " + algorithm + " hash.");
            button.setUserData(algorithm);
            button.setToggleGroup(hashToggle);
            button.setSelected(algorithm.equals("SHA256"));
            hashGrid.add(button, i % 2, i / 2);
        }

        CheckBox outputBase32 = tip(new CheckBox("Output as Base32"), "Print the hash in Base32 instead of hex.");
        CheckBox outputBase64 = tip(new CheckBox("Output as Base64"), "Print the hash in Base64 instead of hex.");
        CheckBox includeFileNames = tip(new CheckBox("Include File Names in Output"),
                "Print each file name next to its hash.");
        includeFileNames.setSelected(includeNamesByDefault);
        outputBase32.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (selected) {
                outputBase64.setSelected(false);
            }
        });
        outputBase64.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (selected) {
                outputBase32.setSelected(false);
            }
        });

        dialog.add(new Label("Selected item: " + selectedName), new Separator(),
                new Label("Checksum Type (Choose One):"), hashGrid, new Separator(),
                new Label("Options:"), outputBase32, outputBase64, includeFileNames);
        return dialog.showAndWait(() -> hashToggle.getSelectedToggle() == null ? null
                : ChecksumOptions.of((String) hashToggle.getSelectedToggle().getUserData(),
                outputBase32.isSelected(), outputBase64.isSelected(), includeFileNames.isSelected()));
    }
}
