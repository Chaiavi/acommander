package org.chaiware.acommander.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Shows a computed checksum with Copy and Save buttons. */
public final class ChecksumResultDialog {
    private ChecksumResultDialog() {
    }

    /** @param savePath where "Save Value As File" writes */
    public static void show(Window owner, String themeClass, String title, String targetName, String algorithmLabel,
                            String checksumValue, Path savePath) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeType);
        tip((Button) dialog.getDialogPane().lookupButton(closeType), "Close this dialog.");

        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        TextArea checksumArea = tip(new TextArea(checksumValue), "The computed checksum; select text to copy part of it.");
        checksumArea.setEditable(false);
        checksumArea.setWrapText(false);
        checksumArea.setPrefRowCount(Math.max(4, Math.min(16, (int) checksumValue.lines().count() + 1)));
        Label status = new Label();
        status.setWrapText(true);

        Button copyButton = tip(new Button("Copy Value"), "Copy the checksum to the clipboard.");
        copyButton.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(checksumArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
            status.setText("Copied to the clipboard.");
        });
        Button saveAsButton = tip(new Button("Save Value As File"), "Save the checksum next to the item as " + savePath.getFileName() + ".");
        saveAsButton.setOnAction(event -> {
            try {
                Files.createDirectories(savePath.getParent());
                Files.writeString(savePath, checksumArea.getText(), StandardCharsets.UTF_8);
                status.setText("Saved checksum value as: " + savePath);
            } catch (Exception ex) {
                status.setText("Failed saving checksum file: " + ex.getMessage());
            }
        });

        VBox content = new VBox(10, heading, new Label("Item: " + targetName + " | Type: " + algorithmLabel),
                checksumArea, new HBox(8, copyButton, saveAsButton), status);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(760, 420);
        DialogTheme.apply(dialog, owner, themeClass);
        dialog.showAndWait();
    }
}
