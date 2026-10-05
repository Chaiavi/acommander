package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.chaiware.acommander.vfs.FtpConnectionOptions;
import org.chaiware.acommander.vfs.FtpConnectionOptions.Protocol;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.chaiware.acommander.dialog.OptionsDialog.positiveIntOrNull;
import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks for FTP / FTPS / SFTP connection details, prefilled from a saved connection if one is picked. */
public final class FtpConnectDialog {
    private static final String AUTO = "Auto-discover";
    private static final String SFTP = "SFTP/SSH";

    /** {@code save}: remember the connection (after auto-discovery, with the discovered protocol). */
    public record Result(FtpConnectionOptions options, boolean save) {}

    private FtpConnectDialog() {
    }

    /** @param removeSaved called with a saved connection's name when the user removes it */
    public static Optional<Result> show(Window owner, String themeClass, Map<String, FtpConnectionOptions> saved,
                                        Consumer<String> removeSaved) {
        OptionsDialog<Result> dialog = new OptionsDialog<>(owner, themeClass,
                "FTP/FTPS/SFTP-SSH Connection", "Connect", "Connect the focused pane to this server.");

        ComboBox<String> savedCombo = tip(new ComboBox<>(), "Fill in the fields from a connection you saved before.");
        savedCombo.getItems().addAll(saved.keySet());
        savedCombo.setPromptText("Saved Connections");
        savedCombo.setMaxWidth(Double.MAX_VALUE);
        Button removeSavedButton = tip(new Button("Remove"), "Forget the selected saved connection.");
        removeSavedButton.setDisable(true);
        TextField hostField = tip(new TextField(), "Server name or IP address, without ftp://.");
        hostField.setPromptText("Host");
        TextField portField = tip(new TextField(), "Server port; leave blank for the protocol's default.");
        portField.setPromptText("Port");
        TextField userField = tip(new TextField(), "Your user name on the server.");
        userField.setPromptText("Username");
        PasswordField passField = tip(new PasswordField(), "Your password on the server.");
        passField.setPromptText("Password");
        ComboBox<String> protocolCombo = tip(new ComboBox<>(), "How to talk to the server; Auto-discover tries each one.");
        protocolCombo.getItems().addAll(AUTO, "FTP", "FTPS", SFTP);
        protocolCombo.setValue(AUTO);
        protocolCombo.setMaxWidth(Double.MAX_VALUE);
        TextField nameField = tip(new TextField(), "Name shown in Saved Connections; the host is used when blank.");
        nameField.setPromptText("Connection Name (optional)");
        CheckBox trustCheckBox = tip(new CheckBox("Trust Any Certificate"),
                "Skip checking the server's TLS certificate or SSH key; tick only for a self-signed server you trust.");
        CheckBox saveCheckBox = tip(new CheckBox("Save for Next Time"), "Add this connection to Saved Connections.");
        saveCheckBox.setSelected(true);

        protocolCombo.valueProperty().addListener((obs, oldValue, newValue) ->
                portField.setText(AUTO.equals(newValue) ? "" : String.valueOf(protocolOf(newValue).getDefaultPort())));
        savedCombo.setOnAction(e -> {
            FtpConnectionOptions option = saved.get(savedCombo.getValue());
            removeSavedButton.setDisable(option == null);
            if (option == null) {
                return;
            }
            protocolCombo.setValue(option.getProtocol() == Protocol.SFTP ? SFTP : option.getProtocol().name());
            hostField.setText(option.getHost());
            portField.setText(String.valueOf(option.getPort()));
            userField.setText(option.getUsername());
            passField.setText(option.getPassword());
            nameField.setText(option.getName());
            trustCheckBox.setSelected(option.isTrustAnyCertificate());
            saveCheckBox.setSelected(true);
        });
        removeSavedButton.setOnAction(e -> {
            String selected = savedCombo.getValue();
            if (selected != null) {
                removeSaved.accept(selected);
                savedCombo.getItems().remove(selected);
                savedCombo.getSelectionModel().clearSelection();
                removeSavedButton.setDisable(true);
            }
        });

        Label validationLabel = new Label();
        Runnable validate = () -> {
            String problem = hostField.getText().isBlank() ? "Enter the server's host name."
                    : !portField.getText().isBlank() && (positiveIntOrNull(portField.getText()) == null
                    || positiveIntOrNull(portField.getText()) > 65535) ? "Port must be a number from 1 to 65535." : null;
            validationLabel.setText(problem == null ? "" : problem);
            dialog.okButton().setDisable(problem != null);
        };
        hostField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        portField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        HBox savedBox = new HBox(10, savedCombo, removeSavedButton);
        HBox.setHgrow(savedCombo, Priority.ALWAYS);
        grid.addRow(0, new Label("Saved:"), savedBox);
        grid.addRow(1, new Label("Host:"), hostField);
        grid.addRow(2, new Label("Port:"), portField);
        grid.addRow(3, new Label("Username:"), userField);
        grid.addRow(4, new Label("Password:"), passField);
        grid.addRow(5, new Label("Protocol:"), protocolCombo);
        grid.addRow(6, new Label("Name:"), nameField);
        grid.add(trustCheckBox, 1, 7);
        grid.add(saveCheckBox, 1, 8);
        dialog.add(grid, validationLabel);
        dialog.dialog().setOnShown(event -> Platform.runLater(hostField::requestFocus));

        return dialog.showAndWait(() -> {
            boolean autoDiscover = AUTO.equals(protocolCombo.getValue());
            Protocol protocol = protocolOf(protocolCombo.getValue());
            Integer port = positiveIntOrNull(portField.getText());
            String host = hostField.getText().trim();
            FtpConnectionOptions options = FtpConnectionOptions.builder()
                    .host(host)
                    .port(port == null ? protocol.getDefaultPort() : port)
                    .username(userField.getText())
                    .password(passField.getText())
                    .name(nameField.getText().isBlank() ? host : nameField.getText().trim())
                    .protocol(protocol)
                    .autoDiscover(autoDiscover)
                    .trustAnyCertificate(trustCheckBox.isSelected())
                    .build();
            return new Result(options, saveCheckBox.isSelected());
        });
    }

    /** Auto-discover starts from plain FTP. */
    private static Protocol protocolOf(String label) {
        return SFTP.equals(label) ? Protocol.SFTP : "FTPS".equals(label) ? Protocol.FTPS : Protocol.FTP;
    }
}
