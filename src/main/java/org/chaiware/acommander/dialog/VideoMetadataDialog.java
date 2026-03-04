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
import org.chaiware.acommander.Commander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dialog for editing MP4/M4V/3GP metadata using AtomicParsley.
 */
public class VideoMetadataDialog {
    private static final Logger logger = LoggerFactory.getLogger(VideoMetadataDialog.class);
    private static final String ATOMIC_PARSLEY_PATH = "apps/video_metadata/AtomicParsley.exe";
    private static final Pattern ATOM_TEXTDATA_PATTERN =
            Pattern.compile("^Atom\\s+\"([^\"]+)\"(?:\\s+\\[[^\\]]+\\])?\\s+contains:\\s*(.*)$");

    private final Window owner;
    private final File videoFile;
    private final Commander commander;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private TextField titleField;
    private TextField artistField;
    private TextField albumField;
    private TextField genreField;
    private TextField yearField;
    private TextField trackField;
    private TextField diskField;
    private TextField commentField;
    private TextField composerField;
    private TextField descriptionField;
    private CheckBox preserveTimeCheck;
    private Label statusLabel;
    private Button reloadButton;
    private Button saveButton;
    private DialogPane dialogPane;

    private final Map<String, String> originalValues = new LinkedHashMap<>();
    private boolean metadataModified;

    public VideoMetadataDialog(Window owner, File videoFile, Commander commander) {
        this.owner = owner;
        this.videoFile = videoFile;
        this.commander = commander;
    }

    public boolean showAndWait() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit Video Metadata");
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.CLOSE);
        dialogPane.setMinWidth(860);
        dialogPane.setMinHeight(560);

        applyTheme(dialog);
        dialogPane.setContent(buildContent());
        dialog.setOnShown(e -> loadMetadata());
        dialog.setResultConverter(button -> metadataModified);
        dialog.showAndWait();
        return metadataModified;
    }

    private VBox buildContent() {
        Label title = new Label("Video Metadata Editor");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label subtitle = new Label("File: " + videoFile.getName() + "  -  Powered by AtomicParsley");
        subtitle.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        GridPane form = buildForm();

        reloadButton = new Button("Reload");
        reloadButton.setOnAction(e -> loadMetadata());

        saveButton = new Button("Save Metadata");
        saveButton.setStyle("-fx-font-weight: bold;");
        saveButton.setOnAction(e -> saveMetadata());

        HBox actions = new HBox(10, reloadButton, saveButton);
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        VBox root = new VBox(10, title, subtitle, new Separator(), form, actions, new Separator(), statusLabel);
        root.setPadding(new Insets(10));
        return root;
    }

    private GridPane buildForm() {
        titleField = new TextField();
        artistField = new TextField();
        albumField = new TextField();
        genreField = new TextField();
        yearField = new TextField();
        trackField = new TextField();
        diskField = new TextField();
        commentField = new TextField();
        composerField = new TextField();
        descriptionField = new TextField();
        preserveTimeCheck = new CheckBox("Preserve original file timestamps");
        preserveTimeCheck.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Title"), titleField);
        grid.addRow(1, new Label("Artist"), artistField);
        grid.addRow(2, new Label("Album"), albumField);
        grid.addRow(3, new Label("Genre"), genreField);
        grid.addRow(4, new Label("Year"), yearField);
        grid.addRow(5, new Label("Track (n or n/total)"), trackField);
        grid.addRow(6, new Label("Disk (n or n/total)"), diskField);
        grid.addRow(7, new Label("Comment"), commentField);
        grid.addRow(8, new Label("Composer"), composerField);
        grid.addRow(9, new Label("Description"), descriptionField);
        grid.add(preserveTimeCheck, 1, 10);

        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(artistField, Priority.ALWAYS);
        GridPane.setHgrow(albumField, Priority.ALWAYS);
        GridPane.setHgrow(genreField, Priority.ALWAYS);
        GridPane.setHgrow(yearField, Priority.ALWAYS);
        GridPane.setHgrow(trackField, Priority.ALWAYS);
        GridPane.setHgrow(diskField, Priority.ALWAYS);
        GridPane.setHgrow(commentField, Priority.ALWAYS);
        GridPane.setHgrow(composerField, Priority.ALWAYS);
        GridPane.setHgrow(descriptionField, Priority.ALWAYS);
        return grid;
    }

    private void applyTheme(Dialog<?> dialog) {
        if (commander == null || dialog == null) {
            return;
        }
        DialogPane pane = dialog.getDialogPane();
        String themeClass = commander.getCurrentThemeMode() == Commander.ThemeMode.DARK ? "theme-dark" : "theme-light";
        pane.getStyleClass().removeAll("theme-dark", "theme-light");
        pane.getStyleClass().add(themeClass);
        if (commander.rootPane != null && commander.rootPane.getScene() != null) {
            for (String stylesheet : commander.rootPane.getScene().getStylesheets()) {
                if (!pane.getStylesheets().contains(stylesheet)) {
                    pane.getStylesheets().add(stylesheet);
                }
            }
        }
    }

    private void loadMetadata() {
        setStatus("Loading metadata...");
        setControlsDisabled(true);

        CompletableFuture.supplyAsync(this::readMetadata, executor)
                .thenAcceptAsync(result -> {
                    if (!result.success()) {
                        setStatus("Failed to load metadata");
                        showError(result.message(), "Failed to load metadata", result.details());
                    } else {
                        populateFields(result.metadata());
                        setStatus("Metadata loaded");
                    }
                    setControlsDisabled(false);
                }, Platform::runLater);
    }

    private LoadResult readMetadata() {
        File atomicParsley = new File(ATOMIC_PARSLEY_PATH);
        if (!atomicParsley.exists()) {
            return new LoadResult(false, Map.of(), "AtomicParsley.exe not found", atomicParsley.getAbsolutePath());
        }
        if (!videoFile.exists()) {
            return new LoadResult(false, Map.of(), "Video file does not exist", videoFile.getAbsolutePath());
        }
        try {
            List<String> command = List.of(
                    ATOMIC_PARSLEY_PATH,
                    videoFile.getAbsolutePath(),
                    "--textdata"
            );
            ProcessResult processResult = runCommand(command);
            if (processResult.exitCode() != 0) {
                return new LoadResult(false, Map.of(), "AtomicParsley returned an error", processResult.stderr());
            }
            return new LoadResult(true, parseTextData(processResult.stdout()), "", "");
        } catch (Exception ex) {
            logger.error("Failed reading video metadata", ex);
            return new LoadResult(false, Map.of(), "Failed reading metadata", ex.getMessage());
        }
    }

    private void populateFields(Map<String, String> values) {
        setFieldValue("title", titleField, values);
        setFieldValue("artist", artistField, values);
        setFieldValue("album", albumField, values);
        setFieldValue("genre", genreField, values);
        setFieldValue("year", yearField, values);
        setFieldValue("tracknum", trackField, values);
        setFieldValue("disk", diskField, values);
        setFieldValue("comment", commentField, values);
        setFieldValue("composer", composerField, values);
        setFieldValue("description", descriptionField, values);
    }

    private void setFieldValue(String key, TextField field, Map<String, String> values) {
        String value = values.getOrDefault(key, "");
        field.setText(value);
        originalValues.put(key, value);
    }

    private Map<String, String> parseTextData(String output) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (output == null || output.isBlank()) {
            return parsed;
        }
        String[] lines = output.split("\\R");
        for (String line : lines) {
            String normalizedLine = stripBom(line).trim();
            Matcher matcher = ATOM_TEXTDATA_PATTERN.matcher(normalizedLine);
            if (!matcher.matches()) {
                continue;
            }
            String atom = matcher.group(1);
            String value = matcher.group(2) == null ? "" : matcher.group(2).trim();
            mapAtomToKey(parsed, atom, value);
        }
        return parsed;
    }

    private void mapAtomToKey(Map<String, String> parsed, String atom, String value) {
        if ("©nam".equals(atom)) {
            parsed.put("title", value);
        } else if ("©ART".equals(atom)) {
            parsed.put("artist", value);
        } else if ("©alb".equals(atom)) {
            parsed.put("album", value);
        } else if ("©gen".equals(atom) || "gnre".equals(atom)) {
            parsed.put("genre", value);
        } else if ("©day".equals(atom)) {
            parsed.put("year", value);
        } else if ("trkn".equals(atom)) {
            parsed.put("tracknum", value);
        } else if ("disk".equals(atom)) {
            parsed.put("disk", value);
        } else if ("©cmt".equals(atom)) {
            parsed.put("comment", value);
        } else if ("©wrt".equals(atom)) {
            parsed.put("composer", value);
        } else if ("desc".equals(atom)) {
            parsed.put("description", value);
        }
    }

    private void saveMetadata() {
        if (!videoFile.canWrite()) {
            showError("Cannot modify metadata: File is read-only", "File is read-only", videoFile.getAbsolutePath());
            return;
        }

        List<String> command = buildSaveCommand();
        if (command.isEmpty()) {
            setStatus("No metadata changes to save");
            return;
        }

        setControlsDisabled(true);
        setStatus("Saving metadata...");

        boolean preserveTime = preserveTimeCheck.isSelected();
        CompletableFuture.supplyAsync(() -> runSave(command, preserveTime), executor)
                .thenAcceptAsync(result -> {
                    if (result.success()) {
                        metadataModified = true;
                        setStatus("Metadata saved successfully");
                        loadMetadata();
                    } else {
                        setStatus("Failed to save metadata");
                        showError(result.message(), "Failed to save metadata", result.details());
                        setControlsDisabled(false);
                    }
                }, Platform::runLater);
    }

    private List<String> buildSaveCommand() {
        List<String> command = new ArrayList<>();
        command.add(ATOMIC_PARSLEY_PATH);
        command.add(videoFile.getAbsolutePath());

        appendIfChanged(command, "title", "--title", titleField.getText());
        appendIfChanged(command, "artist", "--artist", artistField.getText());
        appendIfChanged(command, "album", "--album", albumField.getText());
        appendIfChanged(command, "genre", "--genre", genreField.getText());
        appendIfChanged(command, "year", "--year", yearField.getText());
        appendIfChanged(command, "tracknum", "--tracknum", trackField.getText());
        appendIfChanged(command, "disk", "--disk", diskField.getText());
        appendIfChanged(command, "comment", "--comment", commentField.getText());
        appendIfChanged(command, "composer", "--composer", composerField.getText());
        appendIfChanged(command, "description", "--description", descriptionField.getText());

        if (command.size() <= 2) {
            return List.of();
        }
        if (preserveTimeCheck.isSelected()) {
            command.add("--preserveTime");
        }
        command.add("--overWrite");
        return command;
    }

    private void appendIfChanged(List<String> command, String key, String option, String currentValue) {
        String normalizedCurrent = normalize(currentValue);
        String normalizedOriginal = normalize(originalValues.get(key));
        if (!Objects.equals(normalizedCurrent, normalizedOriginal)) {
            command.add(option);
            command.add(currentValue == null ? "" : currentValue);
        }
    }

    private SaveResult runSave(List<String> command, boolean preserveTime) {
        Set<String> existingArtifacts = listAtomicParsleyArtifacts();
        try {
            ProcessResult processResult = runCommand(command);
            if (processResult.exitCode() != 0) {
                return new SaveResult(false, "AtomicParsley returned an error", processResult.stderr());
            }
            logger.info("Video metadata saved (preserveTime={}): {}", preserveTime, videoFile.getAbsolutePath());
            return new SaveResult(true, "", "");
        } catch (Exception ex) {
            logger.error("Failed saving video metadata", ex);
            return new SaveResult(false, "Failed saving metadata", ex.getMessage());
        } finally {
            cleanupNewArtifacts(existingArtifacts);
        }
    }

    private ProcessResult runCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(System.getProperty("user.dir")));
        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line).append('\n');
            }
        }

        StringBuilder stderr = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stderr.append(line).append('\n');
            }
        }

        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, stdout.toString(), stderr.toString());
    }

    private void setControlsDisabled(boolean disabled) {
        titleField.setDisable(disabled);
        artistField.setDisable(disabled);
        albumField.setDisable(disabled);
        genreField.setDisable(disabled);
        yearField.setDisable(disabled);
        trackField.setDisable(disabled);
        diskField.setDisable(disabled);
        commentField.setDisable(disabled);
        composerField.setDisable(disabled);
        descriptionField.setDisable(disabled);
        preserveTimeCheck.setDisable(disabled);
        reloadButton.setDisable(disabled);
        saveButton.setDisable(disabled);
    }

    private void setStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status == null ? "" : status);
        }
    }

    private void showError(String message, String title, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);

        StringBuilder content = new StringBuilder(message == null ? "" : message);
        if (details != null && !details.isBlank()) {
            content.append("\n\n").append(details);
        }
        alert.setContentText(content.toString());
        alert.setResizable(true);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        alert.getDialogPane().setPrefWidth(520);
        applyThemeToAlert(alert);
        alert.showAndWait();
    }

    private void applyThemeToAlert(Alert alert) {
        if (commander == null || alert == null) {
            return;
        }
        DialogPane pane = alert.getDialogPane();
        String themeClass = commander.getCurrentThemeMode() == Commander.ThemeMode.DARK ? "theme-dark" : "theme-light";
        pane.getStyleClass().removeAll("theme-dark", "theme-light");
        pane.getStyleClass().add(themeClass);
        if (commander.rootPane != null && commander.rootPane.getScene() != null) {
            for (String stylesheet : commander.rootPane.getScene().getStylesheets()) {
                if (!pane.getStylesheets().contains(stylesheet)) {
                    pane.getStylesheets().add(stylesheet);
                }
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private String stripBom(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        if (value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private Set<String> listAtomicParsleyArtifacts() {
        Set<String> artifacts = new HashSet<>();
        File parent = videoFile.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            return artifacts;
        }
        String baseName = baseName(videoFile.getName());
        File[] files = parent.listFiles();
        if (files == null) {
            return artifacts;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.startsWith(baseName + "-data-") || name.startsWith(baseName + "-temp-")) {
                artifacts.add(file.getAbsolutePath());
            }
        }
        return artifacts;
    }

    private void cleanupNewArtifacts(Set<String> existingArtifacts) {
        Set<String> after = listAtomicParsleyArtifacts();
        for (String path : after) {
            if (existingArtifacts.contains(path)) {
                continue;
            }
            File file = new File(path);
            if (!file.delete()) {
                logger.warn("Could not delete AtomicParsley temp artifact: {}", path);
            } else {
                logger.info("Deleted AtomicParsley temp artifact: {}", path);
            }
        }
    }

    private String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private record LoadResult(boolean success, Map<String, String> metadata, String message, String details) {}

    private record SaveResult(boolean success, String message, String details) {}

    private record ProcessResult(int exitCode, String stdout, String stderr) {}
}
