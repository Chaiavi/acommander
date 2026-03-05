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
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dialog for editing MP3 metadata using id3.exe.
 */
public class AudioMetadataDialog {
    private static final Logger logger = LoggerFactory.getLogger(AudioMetadataDialog.class);
    private static final String ID3_PATH = "apps/audio_metadata/id3.exe";
    private static final String QUERY_SEPARATOR = "\t";
    private static final List<String> QUERY_KEYS = List.of("title", "artist", "album", "track", "year", "genre", "comment");
    private static final String QUERY_FORMAT = "%t\t%a\t%l\t%n\t%y\t%g\t%c";

    private final Window owner;
    private final File audioFile;
    private final Commander commander;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Charset id3IoCharset = detectNativeProcessCharset();

    private TextField titleField;
    private TextField artistField;
    private TextField albumField;
    private TextField trackField;
    private TextField yearField;
    private TextField genreField;
    private TextField commentField;
    private ComboBox<TagVersion> tagVersionCombo;
    private CheckBox preserveTimeCheck;
    private Label statusLabel;
    private Button reloadButton;
    private Button saveButton;

    private final Map<String, String> originalValues = new LinkedHashMap<>();
    private boolean metadataModified;

    public AudioMetadataDialog(Window owner, File audioFile, Commander commander) {
        this.owner = owner;
        this.audioFile = audioFile;
        this.commander = commander;
    }

    public boolean showAndWait() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit Audio Metadata");
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.CLOSE);
        pane.setMinWidth(760);
        pane.setMinHeight(500);

        applyTheme(dialog);
        pane.setContent(buildContent());
        dialog.setOnShown(e -> loadMetadata());
        dialog.setResultConverter(button -> metadataModified);
        dialog.showAndWait();
        return metadataModified;
    }

    private VBox buildContent() {
        Label title = new Label("Audio Metadata Editor");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitle = new Label("File: " + audioFile.getName() + "  -  Powered by id3.exe");
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
        trackField = new TextField();
        yearField = new TextField();
        genreField = new TextField();
        commentField = new TextField();

        tagVersionCombo = new ComboBox<>();
        tagVersionCombo.getItems().addAll(TagVersion.values());
        tagVersionCombo.setValue(TagVersion.ID3V2);

        preserveTimeCheck = new CheckBox("Preserve file modification time");
        preserveTimeCheck.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Title"), titleField);
        grid.addRow(1, new Label("Artist"), artistField);
        grid.addRow(2, new Label("Album"), albumField);
        grid.addRow(3, new Label("Track"), trackField);
        grid.addRow(4, new Label("Year"), yearField);
        grid.addRow(5, new Label("Genre"), genreField);
        grid.addRow(6, new Label("Comment"), commentField);
        grid.addRow(7, new Label("Tag Version"), tagVersionCombo);
        grid.add(preserveTimeCheck, 1, 8);

        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(artistField, Priority.ALWAYS);
        GridPane.setHgrow(albumField, Priority.ALWAYS);
        GridPane.setHgrow(trackField, Priority.ALWAYS);
        GridPane.setHgrow(yearField, Priority.ALWAYS);
        GridPane.setHgrow(genreField, Priority.ALWAYS);
        GridPane.setHgrow(commentField, Priority.ALWAYS);
        GridPane.setHgrow(tagVersionCombo, Priority.ALWAYS);
        return grid;
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
                        populateFields(result.values());
                        setStatus("Metadata loaded");
                    }
                    setControlsDisabled(false);
                }, Platform::runLater);
    }

    private LoadResult readMetadata() {
        File id3 = new File(ID3_PATH);
        if (!id3.exists()) {
            return new LoadResult(false, Map.of(), "id3.exe not found", id3.getAbsolutePath());
        }
        if (!audioFile.exists()) {
            return new LoadResult(false, Map.of(), "Audio file does not exist", audioFile.getAbsolutePath());
        }
        try {
            logger.info("Loading audio metadata for file: {}", audioFile.getAbsolutePath());
            Map<String, String> values = queryAllTagValues();
            return new LoadResult(true, values, "", "");
        } catch (Exception ex) {
            logger.error("Failed reading audio metadata", ex);
            return new LoadResult(false, Map.of(), "Failed reading metadata", ex.getMessage());
        }
    }

    private Map<String, String> queryAllTagValues() throws IOException, InterruptedException {
        List<String> command = List.of(ID3_PATH, "-q", QUERY_FORMAT, audioFile.getAbsolutePath());
        ProcessResult result = runCommand(command);
        if (result.exitCode() != 0) {
            logger.warn("id3 read failed (exit {}): stdout='{}' stderr='{}'",
                    result.exitCode(),
                    truncateForLog(result.stdout()),
                    truncateForLog(result.stderr()));
            return emptyQueryValues();
        }

        String normalizedOutput = result.stdout()
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
        String[] rawValues = normalizedOutput.split(QUERY_SEPARATOR, -1);

        if (rawValues.length != QUERY_KEYS.size()) {
            logger.warn("Unexpected id3 query output field count. expected={} actual={} stdout='{}'",
                    QUERY_KEYS.size(),
                    rawValues.length,
                    truncateForLog(result.stdout()));
            return emptyQueryValues();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < QUERY_KEYS.size(); i++) {
            values.put(QUERY_KEYS.get(i), rawValues[i].trim());
        }
        return values;
    }

    private Map<String, String> emptyQueryValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : QUERY_KEYS) {
            values.put(key, "");
        }
        return values;
    }

    private void populateFields(Map<String, String> values) {
        setField("title", titleField, values.get("title"));
        setField("artist", artistField, values.get("artist"));
        setField("album", albumField, values.get("album"));
        setField("track", trackField, values.get("track"));
        setField("year", yearField, values.get("year"));
        setField("genre", genreField, values.get("genre"));
        setField("comment", commentField, values.get("comment"));
    }

    private void setField(String key, TextField field, String value) {
        String safe = value == null ? "" : value;
        field.setText(safe);
        originalValues.put(key, safe);
    }

    private void saveMetadata() {
        if (!audioFile.canWrite()) {
            showError("Cannot modify metadata: File is read-only", "File is read-only", audioFile.getAbsolutePath());
            return;
        }

        List<String> command = buildSaveCommand();
        if (command.isEmpty()) {
            setStatus("No metadata changes to save");
            return;
        }

        setControlsDisabled(true);
        setStatus("Saving metadata...");

        CompletableFuture.supplyAsync(() -> runSave(command), executor)
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
        command.add(ID3_PATH);

        TagVersion version = tagVersionCombo.getValue();
        if (version != null) {
            command.add(version.flag());
        }
        if (preserveTimeCheck.isSelected()) {
            command.add("-M");
        }

        int optionsBeforeTagEdits = command.size();
        appendIfChanged(command, "title", "-t", titleField.getText());
        appendIfChanged(command, "artist", "-a", artistField.getText());
        appendIfChanged(command, "album", "-l", albumField.getText());
        appendIfChanged(command, "track", "-n", trackField.getText());
        appendIfChanged(command, "year", "-y", yearField.getText());
        appendIfChanged(command, "genre", "-g", genreField.getText());
        appendIfChanged(command, "comment", "-c", commentField.getText());

        if (command.size() == optionsBeforeTagEdits) {
            return List.of();
        }

        command.add(audioFile.getAbsolutePath());
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

    private SaveResult runSave(List<String> command) {
        try {
            logger.info("Saving audio metadata for file: {}", audioFile.getAbsolutePath());
            String encodingError = findNonEncodableChangedField();
            if (encodingError != null) {
                logger.warn("id3 save blocked due to native encoding limitation: {}", encodingError);
                return new SaveResult(false, "Cannot save some characters with current id3.exe encoding", encodingError);
            }
            ProcessResult result = runCommand(command);
            if (result.exitCode() != 0) {
                String details = buildFailureDetails(result);
                logger.error("id3 save failed (exit {}): {}", result.exitCode(), details);
                return new SaveResult(false, "id3.exe returned an error", details);
            }
            logger.info("Audio metadata saved: {}", audioFile.getAbsolutePath());
            return new SaveResult(true, "", "");
        } catch (Exception ex) {
            logger.error("Failed saving audio metadata", ex);
            return new SaveResult(false, "Failed saving metadata", ex.getMessage());
        }
    }

    private ProcessResult runCommand(List<String> command) throws IOException, InterruptedException {
        logger.info("Executing id3 command: {}", formatCommand(command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(System.getProperty("user.dir")));
        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), id3IoCharset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line).append('\n');
            }
        }

        StringBuilder stderr = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), id3IoCharset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stderr.append(line).append('\n');
            }
        }

        int exit = process.waitFor();
        logger.info("id3 command completed with exit code {}", exit);
        if (!stderr.isEmpty()) {
            logger.warn("id3 stderr: {}", truncateForLog(stderr.toString()));
        }
        if (exit != 0 && !stdout.isEmpty()) {
            logger.warn("id3 stdout (on failure): {}", truncateForLog(stdout.toString()));
        }
        return new ProcessResult(exit, stdout.toString(), stderr.toString());
    }

    private void setControlsDisabled(boolean disabled) {
        titleField.setDisable(disabled);
        artistField.setDisable(disabled);
        albumField.setDisable(disabled);
        trackField.setDisable(disabled);
        yearField.setDisable(disabled);
        genreField.setDisable(disabled);
        commentField.setDisable(disabled);
        tagVersionCombo.setDisable(disabled);
        preserveTimeCheck.setDisable(disabled);
        reloadButton.setDisable(disabled);
        saveButton.setDisable(disabled);
    }

    private void setStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status == null ? "" : status);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private String formatCommand(List<String> command) {
        return command.stream()
                .map(part -> {
                    if (part == null) {
                        return "";
                    }
                    if (part.contains(" ") || part.contains("\t")) {
                        return "\"" + part.replace("\"", "\\\"") + "\"";
                    }
                    return part;
                })
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private String truncateForLog(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", "\\r").replace("\n", "\\n");
        int max = 1200;
        if (normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, max) + "...(truncated)";
    }

    private String buildFailureDetails(ProcessResult result) {
        String stderr = result.stderr() == null ? "" : result.stderr().trim();
        String stdout = result.stdout() == null ? "" : result.stdout().trim();
        StringBuilder details = new StringBuilder();
        if (!stderr.isEmpty()) {
            details.append(stderr);
        }
        if (!stdout.isEmpty()) {
            if (!details.isEmpty()) {
                details.append("\n\n");
            }
            details.append("stdout:\n").append(stdout);
        }
        if (details.isEmpty()) {
            details.append("id3.exe exited with code ").append(result.exitCode());
        }
        return details.toString();
    }

    private String findNonEncodableChangedField() {
        CharsetEncoder encoder = id3IoCharset.newEncoder();
        String[][] fields = new String[][]{
                {"title", titleField.getText()},
                {"artist", artistField.getText()},
                {"album", albumField.getText()},
                {"track", trackField.getText()},
                {"year", yearField.getText()},
                {"genre", genreField.getText()},
                {"comment", commentField.getText()}
        };

        for (String[] field : fields) {
            String key = field[0];
            String value = normalize(field[1]);
            if (Objects.equals(value, normalize(originalValues.get(key)))) {
                continue;
            }
            if (!encoder.canEncode(value)) {
                return "Field '" + key + "' contains characters not representable in current Windows ANSI code page (" +
                        id3IoCharset.displayName() + "). id3.exe has no UTF-8 input flag. " +
                        "Use a Windows system locale/code page that supports these characters (for Hebrew: Windows-1255 or UTF-8 ACP), then restart the app.";
            }
        }
        return null;
    }

    private Charset detectNativeProcessCharset() {
        String[] candidates = new String[]{
                System.getProperty("native.encoding"),
                System.getProperty("sun.jnu.encoding"),
                Charset.defaultCharset().name()
        };
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                return Charset.forName(candidate);
            } catch (Exception ignored) {
            }
        }
        return Charset.defaultCharset();
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

    private record LoadResult(boolean success, Map<String, String> values, String message, String details) {
    }

    private record SaveResult(boolean success, String message, String details) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }

    private enum TagVersion {
        ID3V2("ID3v2 (Recommended)", "-2"),
        ID3V1("ID3v1", "-1"),
        ID3V1_V2("ID3v1 + ID3v2", "-3");

        private final String label;
        private final String flag;

        TagVersion(String label, String flag) {
            this.label = label;
            this.flag = flag;
        }

        public String flag() {
            return flag;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
