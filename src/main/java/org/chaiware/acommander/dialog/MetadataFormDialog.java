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
import org.chaiware.acommander.helpers.MediaTagSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** A tag editor for one audio or video file: a text field per tag, Reload, Save and a status line. ffmpeg runs off the FX thread. */
public final class MetadataFormDialog {
    private static final Logger logger = LoggerFactory.getLogger(MetadataFormDialog.class);

    /** One tag: its ffmpeg key, its label and its tooltip. */
    record Field(String key, String label, String tooltip) {}

    private static final List<Field> AUDIO_FIELDS = List.of(
            new Field("title", "Title", "The song title."),
            new Field("artist", "Artist", "The performing artist."),
            new Field("album", "Album", "The album the song is on."),
            new Field("track", "Track", "The track number, e.g. 3 or 3/12."),
            new Field("date", "Year", "The release year."),
            new Field("genre", "Genre", "The genre name."),
            new Field("comment", "Comment", "A free-text comment."));

    private static final List<Field> VIDEO_FIELDS = List.of(
            new Field("title", "Title", "The video title."),
            new Field("artist", "Artist", "The artist or creator."),
            new Field("album", "Album", "The album or series the video belongs to."),
            new Field("genre", "Genre", "The genre name."),
            new Field("date", "Year", "The release year or date."),
            new Field("track", "Track", "The track number, e.g. 3 or 3/12."),
            new Field("disc", "Disc", "The disc number, e.g. 1 or 1/2."),
            new Field("comment", "Comment", "A free-text comment."),
            new Field("composer", "Composer", "The composer or writer."),
            new Field("description", "Description", "A short description of the video."));

    private final Window owner;
    private final String themeClass;
    private final File file;
    private final List<Field> fields;
    private final Map<String, TextField> inputs = new HashMap<>();
    private final Map<String, String> loaded = new HashMap<>();
    private final List<Control> controls = new ArrayList<>();
    private final Label status = new Label("Ready");
    private boolean saved;

    private MetadataFormDialog(Window owner, String themeClass, File file, List<Field> fields) {
        this.owner = owner;
        this.themeClass = themeClass;
        this.file = file;
        this.fields = fields;
    }

    /** Shows the editor until closed; true when a save changed the file. */
    public static boolean editAudio(Window owner, String themeClass, File file) {
        return new MetadataFormDialog(owner, themeClass, file, AUDIO_FIELDS).showAndWait("Edit Audio Metadata");
    }

    /** Shows the editor until closed; true when a save changed the file. */
    public static boolean editVideo(Window owner, String themeClass, File file) {
        return new MetadataFormDialog(owner, themeClass, file, VIDEO_FIELDS).showAndWait("Edit Video Metadata");
    }

    /** Tag key → new value for the fields whose {@code current} text differs from what was {@code loaded}. */
    static Map<String, String> changes(List<Field> fields, Map<String, String> loaded, Map<String, String> current) {
        Map<String, String> changes = new LinkedHashMap<>();
        for (Field field : fields) {
            String value = current.getOrDefault(field.key(), "");
            if (!value.equals(loaded.getOrDefault(field.key(), ""))) {
                changes.put(field.key(), value);
            }
        }
        return changes;
    }

    private boolean showAndWait(String title) {
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
        Label subtitle = new Label("File: " + file.getName() + "  -  Powered by ffmpeg");
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
        inBackground("Loading metadata...", () -> MediaTagSupport.read(file), values -> {
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
        Map<String, String> changes = changes(fields, loaded, current);
        if (changes.isEmpty()) {
            status.setText("No metadata changes to save");
            return;
        }
        inBackground("Saving metadata...", () -> {
            MediaTagSupport.write(file, changes, preserveTime);
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
