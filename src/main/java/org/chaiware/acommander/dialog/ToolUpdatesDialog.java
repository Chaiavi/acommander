package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.chaiware.acommander.services.ToolUpdateService.ToolStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Tool Updates: every bundled tool with its installed and available version, an Update button where main has newer files. */
public final class ToolUpdatesDialog {
    private ToolUpdatesDialog() {
    }

    public static void show(Window owner, String themeClass, List<ToolStatus> statuses, boolean checkAtStart,
                            Consumer<Boolean> onCheckAtStart, Function<ToolStatus, CompletableFuture<Void>> update,
                            Consumer<String> openUrl) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Tool Updates");
        dialog.setHeaderText(null);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeType);
        tip((Button) dialog.getDialogPane().lookupButton(closeType), "Close this window; updates already started still finish.");

        Label heading = new Label("Tool Updates");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label note = new Label("Updates come only from this project's GitHub, and every file is checked against its published SHA-256.");
        note.setWrapText(true);
        Label message = new Label();
        message.setWrapText(true);

        GridPane grid = new GridPane(16, 6);
        grid.addRow(0, header("Tool"), header("Installed"), header("Available"), header("Status"));
        List<Button> updateButtons = new ArrayList<>();
        List<ToolStatus> rows = statuses.stream()
                .sorted(Comparator.comparing((ToolStatus status) -> !status.canUpdate())
                        .thenComparing(status -> status.tool().getName().toLowerCase()))
                .toList();
        for (int i = 0; i < rows.size(); i++) {
            ToolStatus status = rows.get(i);
            Hyperlink name = tip(new Hyperlink(status.tool().getName()), "Open the tool's home page in your browser.");
            name.setPadding(Insets.EMPTY);
            name.setOnAction(event -> openUrl.accept(status.tool().getLink()));
            Label installed = new Label(status.installed().isEmpty() ? "Not Installed" : status.installed());
            Label available = new Label(status.available());
            Label state = new Label(status.state().label);
            Button updateButton = tip(new Button("Update"), "Download this tool's changed files from GitHub and replace them.");
            updateButton.setVisible(status.canUpdate());
            updateButton.setManaged(status.canUpdate());
            updateButton.setOnAction(event -> {
                updateButton.setDisable(true);
                state.setText("Updating...");
                update.apply(status).whenComplete((done, error) -> Platform.runLater(() -> {
                    if (error == null) {
                        state.setText("Updated");
                        installed.setText(status.available());
                        updateButtons.remove(updateButton);
                        updateButton.setVisible(false);
                    } else {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                        state.setText("Failed");
                        message.setText(status.tool().getName() + ": " + cause.getMessage());
                        updateButton.setDisable(false);
                    }
                }));
            });
            if (status.canUpdate()) {
                updateButtons.add(updateButton);
            }
            grid.addRow(i + 1, name, installed, available, state, updateButton);
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button updateAll = tip(new Button("Update All"), "Update every tool that has an update.");
        updateAll.setDisable(updateButtons.isEmpty());
        updateAll.setOnAction(event -> {
            List.copyOf(updateButtons).stream().filter(button -> !button.isDisabled()).forEach(Button::fire);
            updateAll.setDisable(true);
        });
        CheckBox atStart = tip(new CheckBox("Check at Start"), "Look for tool updates once a day when ACommander starts.");
        atStart.setSelected(checkAtStart);
        atStart.selectedProperty().addListener((obs, old, selected) -> onCheckAtStart.accept(selected));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, updateAll, spacer, atStart);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, heading, note, scroll, message, actions);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(760, 600);
        dialog.setResizable(true);
        DialogTheme.apply(dialog, owner, themeClass);
        dialog.showAndWait();
    }

    private static Label header(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }
}
