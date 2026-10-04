package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.chaiware.acommander.helpers.BackgroundTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** A tag editor for one file: a text field per tag, Reload, Save and a status line. The tool runs off the FX thread. */
final class MetadataFormDialog {
    private static final Logger logger = LoggerFactory.getLogger(MetadataFormDialog.class);

    /** One tag: the key the tool reads it under, its label, the tool option that writes it, and its tooltip. */
    record Field(String key, String label, String option, String tooltip) {}

    interface Tool {
        /** Background: the file's tag values by field key; a missing key shows as empty. */
        Map<String, String> read() throws Exception;

        /** FX thread, so it may read extra controls: the command that writes {@code changes} (option, value, …). */
        List<String> writeCommand(List<String> changes, boolean preserveTime);

        /** Background: runs a {@link #writeCommand}. */
        void write(List<String> command) throws Exception;
    }

    private final Window owner;
    private final String themeClass;
    private final File file;
    private final Tool tool;
    private final List<Field> fields;
    private final Map<String, TextField> inputs = new HashMap<>();
    private final Map<String, String> loaded = new HashMap<>();
    private final List<Control> controls = new ArrayList<>();
    private final Label status = new Label("Ready");
    private boolean saved;

    private MetadataFormDialog(Window owner, String themeClass, File file, List<Field> fields, Tool tool) {
        this.owner = owner;
        this.themeClass = themeClass;
        this.file = file;
        this.fields = fields;
        this.tool = tool;
    }

    /** Shows the editor until closed; true when a save changed the file. {@code extraRows} are label → control. */
    static boolean show(Window owner, String themeClass, String title, File file, String toolName,
                        List<Field> fields, Map<String, Control> extraRows, Tool tool) {
        return new MetadataFormDialog(owner, themeClass, file, fields, tool).showAndWait(title, toolName, extraRows);
    }

    /** The option/value pairs of the fields whose {@code current} text differs from what was {@code loaded}. */
    static List<String> changes(List<Field> fields, Map<String, String> loaded, Map<String, String> current) {
        List<String> changes = new ArrayList<>();
        for (Field field : fields) {
            String value = current.getOrDefault(field.key(), "");
            if (!value.equals(loaded.getOrDefault(field.key(), ""))) {
                changes.add(field.option());
                changes.add(value);
            }
        }
        return changes;
    }

    private boolean showAndWait(String title, String toolName, Map<String, Control> extraRows) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        for (Field field : fields) {
            TextField input = OptionsDialog.tip(new TextField(), field.tooltip());
            GridPane.setHgrow(input, Priority.ALWAYS);
            grid.addRow(grid.getRowCount(), new Label(field.label()), input);
            inputs.put(field.key(), input);
            controls.add(input);
        }
        extraRows.forEach((label, control) -> {
            grid.addRow(grid.getRowCount(), new Label(label), control);
            controls.add(control);
        });
        CheckBox preserveTime = OptionsDialog.tip(new CheckBox("Preserve File Time"),
                "Keeps the file's modified date when the tags are saved.");
        preserveTime.setSelected(true);
        grid.add(preserveTime, 1, grid.getRowCount());

        Button reload = OptionsDialog.tip(new Button("Reload"), "Reads the tags from the file again and drops unsaved edits.");
        reload.setOnAction(e -> load());
        Button save = OptionsDialog.tip(new Button("Save Metadata"), "Writes the changed tags to the file.");
        save.setStyle("-fx-font-weight: bold;");
        save.setOnAction(e -> save(preserveTime.isSelected()));
        controls.addAll(List.of(preserveTime, reload, save));

        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitle = new Label("File: " + file.getName() + "  -  Powered by " + toolName);
        subtitle.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        status.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");
        VBox content = new VBox(10, heading, subtitle, new Separator(), grid, new HBox(10, reload, save), new Separator(), status);
        content.setPadding(new Insets(10));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        OptionsDialog.tip((Button) pane.lookupButton(ButtonType.CLOSE), "Closes the editor; unsaved edits are dropped.");
        pane.setMinWidth(760);
        pane.setContent(content);
        DialogTheme.apply(dialog, owner, themeClass);
        dialog.setOnShown(e -> load());
        dialog.showAndWait();
        return saved;
    }

    private void load() {
        inBackground("Loading metadata...", tool::read, values -> {
            loaded.clear();
            for (Field field : fields) {
                String value = values.getOrDefault(field.key(), "");
                inputs.get(field.key()).setText(value);
                loaded.put(field.key(), value);
            }
            status.setText("Metadata loaded");
        }, "Failed to Load Metadata");
    }

    private void save(boolean preserveTime) {
        if (!file.canWrite()) {
            showError("File Is Read-Only", "Cannot save the tags: " + file.getAbsolutePath() + " is read-only.");
            return;
        }
        Map<String, String> current = new HashMap<>();
        inputs.forEach((key, input) -> current.put(key, input.getText() == null ? "" : input.getText()));
        List<String> changes = changes(fields, loaded, current);
        if (changes.isEmpty()) {
            status.setText("No metadata changes to save");
            return;
        }
        List<String> command = tool.writeCommand(changes, preserveTime);
        inBackground("Saving metadata...", () -> {
            tool.write(command);
            return null;
        }, ignored -> {
            saved = true;
            status.setText("Metadata saved");
            load();
        }, "Failed to Save Metadata");
    }

    /** Runs {@code work} off the FX thread with the controls disabled; a failure is shown under {@code failureTitle}. */
    private <T> void inBackground(String busyText, Callable<T> work, Consumer<T> onSuccess, String failureTitle) {
        controls.forEach(control -> control.setDisable(true));
        status.setText(busyText);
        BackgroundTasks.supply(() -> {
            try {
                return work.call();
            } catch (Exception ex) {
                throw new CompletionException(ex);
            }
        }).whenComplete((result, error) -> Platform.runLater(() -> {
            controls.forEach(control -> control.setDisable(false));
            if (error == null) {
                onSuccess.accept(result);
                return;
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            logger.warn("{}: {}", failureTitle, file, cause);
            status.setText(failureTitle);
            showError(failureTitle, String.valueOf(cause.getMessage()));
        }));
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(520);
        DialogTheme.apply(alert, owner, themeClass);
        alert.showAndWait();
    }
}
