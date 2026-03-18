package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
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

/**
 * Dialog for viewing and editing image metadata using exiv2.
 * Features an editable tree table for direct metadata modification.
 */
public class ImageMetadataDialog {
    private static final Logger logger = LoggerFactory.getLogger(ImageMetadataDialog.class);
    private static final String EXIV2_PATH = "apps/image_metadata/exiv2.exe";

    private final Window owner;
    private final File imageFile;
    private final Commander commander;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private TreeTableView<MetadataEntry> metadataTreeTable;
    private TreeItem<MetadataEntry> rootItem;
    private Button loadButton;
    private Button saveButton;
    private Button applyButton;
    private Button addTagButton;
    private ComboBox<String> metadataTypeCombo;
    private Label statusLabel;
    private DialogPane dialogPane;

    private final Map<String, MetadataEntry> entriesByKey = new LinkedHashMap<>();
    private final List<MetadataEntry> allParsedEntries = new ArrayList<>();
    private boolean metadataModified = false;
    private final List<MetadataModification> pendingModifications = new ArrayList<>();
    private static final List<MetadataTemplate> COMMON_METADATA_TEMPLATES = List.of(
            new MetadataTemplate("Title", "Xmp.dc.title", "LangAlt"),
            new MetadataTemplate("Description", "Xmp.dc.description", "LangAlt"),
            new MetadataTemplate("Creator", "Xmp.dc.creator", "XmpBag"),
            new MetadataTemplate("Copyright", "Xmp.dc.rights", "LangAlt"),
            new MetadataTemplate("Subject/Keywords", "Xmp.dc.subject", "XmpBag"),
            new MetadataTemplate("Rating", "Xmp.xmp.Rating", "XmpText"),
            new MetadataTemplate("Label", "Xmp.xmp.Label", "XmpText"),
            new MetadataTemplate("EXIF Artist", "Exif.Image.Artist", "Ascii"),
            new MetadataTemplate("EXIF Copyright", "Exif.Image.Copyright", "Ascii"),
            new MetadataTemplate("EXIF Description", "Exif.Image.ImageDescription", "Ascii"),
            new MetadataTemplate("Software", "Exif.Image.Software", "Ascii"),
            new MetadataTemplate("Date/Time Original", "Exif.Photo.DateTimeOriginal", "Ascii"),
            new MetadataTemplate("IPTC Headline", "Iptc.Application2.Headline", "String"),
            new MetadataTemplate("IPTC Caption", "Iptc.Application2.Caption", "String"),
            new MetadataTemplate("IPTC Byline", "Iptc.Application2.Byline", "String"),
            new MetadataTemplate("IPTC Copyright Notice", "Iptc.Application2.Copyright", "String"),
            new MetadataTemplate("IPTC Source", "Iptc.Application2.Source", "String")
    );

    public ImageMetadataDialog(Window owner, File imageFile, Commander commander) {
        this.owner = owner;
        this.imageFile = imageFile;
        this.commander = commander;
    }

    /**
     * Shows the metadata dialog and returns true if metadata was modified.
     */
    public boolean showAndWait() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit Image Metadata");
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);

        dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.CLOSE);
        dialogPane.setMinWidth(900);
        dialogPane.setMinHeight(650);

        // Apply theme
        if (commander != null) {
            applyTheme(dialog);
        }

        // Build UI
        VBox content = buildContent();
        dialogPane.setContent(content);

        // Handle ESC key to close
        dialogPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                dialog.setResult(false);
                dialog.close();
                event.consume();
            }
        });

        // Load metadata on show
        dialog.setOnShown(e -> loadMetadata());

        dialog.setResultConverter(button -> metadataModified);
        dialog.showAndWait();
        return metadataModified;
    }

    private VBox buildContent() {
        // Title
        Label title = new Label("Image Metadata Editor");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label subtitle = new Label("File: " + imageFile.getName() + "  •  Double-click value to edit  •  Apply changes to save");
        subtitle.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        Separator separator1 = new Separator();

        // Metadata type selector and toolbar
        HBox toolbar = buildToolbar();

        // Metadata tree table
        metadataTreeTable = buildTreeTable();

        // Status bar
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Action buttons
        HBox buttonBox = buildActionButtons();

        Separator separator2 = new Separator();
        VBox root = new VBox(10, title, subtitle, separator1, toolbar, metadataTreeTable, separator2, statusLabel, buttonBox);
        root.setPadding(new Insets(10));
        return root;
    }

    private HBox buildToolbar() {
        Label typeLabel = new Label("Filter");
        metadataTypeCombo = new ComboBox<>();
        metadataTypeCombo.getItems().addAll("All", "EXIF", "IPTC", "XMP", "Comment", "Thumbnail");
        metadataTypeCombo.setValue("All");
        metadataTypeCombo.setOnAction(e -> filterMetadata());

        addTagButton = new Button("Add Metadata Tag");
        addTagButton.setOnAction(e -> showAddTagDialog());

        HBox toolbar = new HBox(10, typeLabel, metadataTypeCombo, addTagButton);
        HBox.setHgrow(metadataTypeCombo, Priority.ALWAYS);
        return toolbar;
    }

    private TreeTableView<MetadataEntry> buildTreeTable() {
        // Create tree table
        metadataTreeTable = new TreeTableView<>();
        metadataTreeTable.setShowRoot(false);
        metadataTreeTable.setEditable(true);

        // Key column - 25% width
        TreeTableColumn<MetadataEntry, String> keyCol = new TreeTableColumn<>("Key");
        keyCol.setPrefWidth(225);
        keyCol.setCellValueFactory(param -> param.getValue().getValue().keyProperty());
        keyCol.setEditable(false);

        // Type column - 25% width
        TreeTableColumn<MetadataEntry, String> typeCol = new TreeTableColumn<>("Type");
        typeCol.setPrefWidth(225);
        typeCol.setCellValueFactory(param -> param.getValue().getValue().typeProperty());
        typeCol.setEditable(false);

        // Value column (editable) - 50% width
        TreeTableColumn<MetadataEntry, String> valueCol = new TreeTableColumn<>("Value");
        valueCol.setPrefWidth(450);
        valueCol.setCellValueFactory(param -> param.getValue().getValue().valueProperty());
        valueCol.setEditable(true);

        metadataTreeTable.getColumns().addAll(keyCol, typeCol, valueCol);
        metadataTreeTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Make value column editable with text field
        valueCol.setCellFactory(column -> new TreeTableCell<MetadataEntry, String>() {
            private final TextField textField = new TextField();
            private boolean escapePressed = false;

            {
                textField.setOnAction(e -> {
                    doCommit();
                    metadataTreeTable.requestFocus();
                });

                textField.setOnKeyPressed(e -> {
                    if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        escapePressed = true;
                        cancelEdit();
                        e.consume();
                    }
                });

                // Commit on focus loss - when user clicks away
                textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused && isEditing()) {
                        doCommit();
                    }
                });
            }

            private void doCommit() {
                String newValue = textField.getText();
                if (newValue == null) {
                    newValue = "";
                }
                
                // Update the entry FIRST, before calling super.commitEdit
                TreeItem<MetadataEntry> treeItem = getTreeTableRow().getTreeItem();
                if (treeItem != null) {
                    MetadataEntry entry = treeItem.getValue();
                    // Group rows are labels, not editable metadata entries.
                    if (entry == null || !entry.isEditableEntry()) {
                        cancelEdit();
                        return;
                    }
                    
                    String oldValue = entry.getOriginalValue();
                    if (!Objects.equals(oldValue, newValue)) {
                        entry.setValue(newValue);
                        entry.setModified(true);
                        upsertPendingModification(entry.getKey(), newValue);
                        setStatus("Pending: " + entry.getKey() + " = " + (newValue.isEmpty() ? "(empty)" : newValue));
                    }
                }
                
                // Now complete the edit
                if (isEditing()) {
                    super.commitEdit(newValue);
                } else {
                    setText(newValue);
                    setGraphic(null);
                }
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                // Don't update visual state while editing
                if (isEditing()) {
                    return;
                }
                
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Always use the entry's current value, not the cached item
                    TreeItem<MetadataEntry> treeItem = getTreeTableRow().getTreeItem();
                    if (treeItem != null) {
                        MetadataEntry entry = treeItem.getValue();
                        setText(entry.getValue());
                    } else {
                        setText(item);
                    }
                    setGraphic(null);
                }
                setEditable(true);
            }

            @Override
            public void startEdit() {
                escapePressed = false;
                TreeItem<MetadataEntry> treeItem = getTreeTableRow().getTreeItem();
                if (treeItem == null || treeItem.getValue() == null || !treeItem.getValue().isEditableEntry()) {
                    return;
                }
                super.startEdit();
                // Get current value from entry
                String currentValue = (treeItem != null) ? treeItem.getValue().getValue() : getItem();
                textField.setText(currentValue != null ? currentValue : "");
                setGraphic(textField);
                setText(null);
                
                Platform.runLater(() -> {
                    textField.requestFocus();
                    textField.selectAll();
                });
            }

            @Override
            public void cancelEdit() {
                if (!escapePressed) {
                    // JavaFX often routes click-away edits through cancelEdit().
                    // Commit here so edited values persist like pressing Enter.
                    doCommit();
                    return;
                }

                super.cancelEdit();
                TreeItem<MetadataEntry> treeItem = getTreeTableRow().getTreeItem();
                if (treeItem != null) {
                    setText(treeItem.getValue().getValue());
                } else {
                    setText(getItem());
                }
                setGraphic(null);
                escapePressed = false;
            }

            @Override
            public void commitEdit(String newValue) {
                // Handled by doCommit()
                super.commitEdit(newValue);
                // Update display
                setText(newValue != null ? newValue : "");
                setGraphic(null);
            }
        });

        return metadataTreeTable;
    }

    private HBox buildActionButtons() {
        loadButton = new Button("Reload");
        loadButton.setOnAction(e -> {
            if (!pendingModifications.isEmpty()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("Reload");
                confirm.setHeaderText(null);
                confirm.setContentText("You have " + pendingModifications.size() + " pending change(s) that will be discarded. Reload anyway?");
                confirm.getDialogPane().getStyleClass().removeAll("theme-dark", "theme-light");
                if (commander != null) {
                    confirm.getDialogPane().getStyleClass().add(commander.getCurrentThemeMode().getStyleClass());
                }
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                    return;
                }
            }
            loadMetadata();
        });

        Button extractButton = new Button("Extract to XMP");
        extractButton.setOnAction(e -> extractMetadata());

        Button insertButton = new Button("Insert from XMP");
        insertButton.setOnAction(e -> insertMetadata());

        applyButton = new Button("Apply All Changes");
        applyButton.setStyle("-fx-font-weight: bold; -fx-base: #4CAF50;");
        applyButton.setOnAction(e -> applyAllChanges());

        // Left side: utility buttons
        HBox leftButtons = new HBox(10, loadButton, extractButton, insertButton);
        
        // Right side: Apply button (near Close button which is in dialog footer)
        HBox rightButtons = new HBox(10, applyButton);
        
        HBox buttonBox = new HBox(10, leftButtons, rightButtons);
        HBox.setHgrow(leftButtons, Priority.ALWAYS);
        return buttonBox;
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
        setButtonsDisabled(true);
        pendingModifications.clear();

        CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Loading metadata for file: {}", imageFile.getAbsolutePath());
                
                // Check if exiv2.exe exists
                File exiv2File = new File(EXIV2_PATH);
                logger.info("Checking exiv2.exe at: {}", exiv2File.getAbsolutePath());
                if (!exiv2File.exists()) {
                    logger.error("exiv2.exe not found at: {}", exiv2File.getAbsolutePath());
                    return new LoadResult(false, "ERROR: exiv2.exe not found at: " + exiv2File.getAbsolutePath() +
                           "\nPlease ensure the image_metadata app is properly installed.", null);
                }
                logger.info("exiv2.exe found: {}", exiv2File.exists());

                // Check if image file exists and is readable
                if (!imageFile.exists()) {
                    logger.error("Image file not found: {}", imageFile.getAbsolutePath());
                    return new LoadResult(false, "ERROR: Image file not found: " + imageFile.getAbsolutePath(), null);
                }
                if (!imageFile.canRead()) {
                    logger.error("Cannot read image file (permission denied): {}", imageFile.getAbsolutePath());
                    return new LoadResult(false, "ERROR: Cannot read image file (permission denied): " + imageFile.getAbsolutePath(), null);
                }
                logger.info("Image file exists and is readable");
                
                String result = runExiv2Print();
                logger.info("exiv2 output length: {} characters", result.length());
                if (result.length() < 500) {
                    logger.info("exiv2 output: {}", result);
                } else {
                    logger.info("exiv2 output (first 500 chars): {}", result.substring(0, 500));
                }
                return new LoadResult(true, result, null);
            } catch (Exception e) {
                logger.error("Failed to load metadata", e);
                return new LoadResult(false, "ERROR: Failed to load metadata\n" + e.getMessage(), e);
            }
        }, executor).thenAcceptAsync(result -> {
            if (!result.success) {
                logger.warn("Metadata load resulted in error: {}", result.output);
                showErrorInTable(result.output);
                setStatus("Failed to load metadata");
            } else {
                populateTreeTable(result.output);
                setStatus("Metadata loaded - " + entriesByKey.size() + " entries found. Double-click values to edit.");
            }
            setButtonsDisabled(false);
        }, Platform::runLater);
    }

    private record LoadResult(boolean success, String output, Exception error) {}

    private String runExiv2Print() throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(EXIV2_PATH);
        command.add("-pa");
        
        // Quote the file path to handle spaces and special characters
        String quotedPath = "\"" + imageFile.getAbsolutePath() + "\"";
        command.add(quotedPath);

        logger.info("Executing exiv2 command: {}", String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(System.getProperty("user.dir")));

        Process process;
        try {
            process = pb.start();
            logger.info("exiv2 process started successfully");
        } catch (IOException e) {
            logger.error("Failed to start exiv2 process", e);
            return "ERROR: Failed to start exiv2.exe\n" + e.getMessage() +
                   "\n\nThis may indicate the executable is corrupted or incompatible.";
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        logger.debug("exiv2 stdout: {} bytes", output.length());
        logger.info("exiv2 raw output:\n{}", output.toString());

        StringBuilder error = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }
        }
        logger.debug("exiv2 stderr: {} bytes", error.length());
        if (error.length() > 0) {
            logger.warn("exiv2 stderr: {}", error.toString());
        }

        int exitCode = process.waitFor();
        logger.info("exiv2 exit code: {}", exitCode);

        if (exitCode != 0) {
            String errorMsg = error.length() > 0 ? error.toString() : "Unknown error (exit code: " + exitCode + ")";
            if (isNoMetadataPresentWarning(errorMsg)) {
                logger.info("No EXIF block present for this file. Continuing with available metadata output.");
                return output.toString();
            }
            logger.error("exiv2 failed with exit code {}. Error: {}", exitCode, errorMsg);
            throw new IOException("exiv2 failed with exit code " + exitCode + "\n\n" + errorMsg);
        }

        if (error.length() > 0 && output.length() == 0) {
            logger.error("exiv2 returned error with no output: {}", error.toString());
            return "ERROR: " + error.toString();
        }

        if (output.length() == 0) {
            logger.info("No metadata found in image file (this is normal for some images)");
            return "";
        }

        return output.toString();
    }

    private boolean isNoMetadataPresentWarning(String errorMsg) {
        if (errorMsg == null || errorMsg.isBlank()) {
            return false;
        }
        String msg = errorMsg.toLowerCase(Locale.ROOT);
        return msg.contains("no exif data found")
                || msg.contains("no iptc data found")
                || msg.contains("no xmp data found");
    }

    private void populateTreeTable(String output) {
        entriesByKey.clear();
        allParsedEntries.clear();
        rootItem = new TreeItem<>(new MetadataEntry("", "", "", ""));

        if (output == null || output.trim().isEmpty()) {
            metadataTreeTable.setRoot(rootItem);
            setStatus("No metadata found in this image file.");
            return;
        }

        // Group entries by namespace
        Map<String, List<MetadataEntry>> groups = new LinkedHashMap<>();

        String[] lines = output.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("ERROR")) {
                continue;
            }

            // Parse exiv2 -pa output:
            // Exif.Image.Make Ascii 6 Nikon
            // Xmp.dc.title XmpText 1 Example
            String[] parts = trimmedLine.split("\\s+", 4);
            if (parts.length >= 4) {
                String key = parts[0].trim();
                String type = parts[1].trim();
                String value = normalizeValueForDisplay(type, parts[3].trim());
                if (key.isEmpty()) {
                    continue;
                }

                MetadataEntry entry = new MetadataEntry(key, type, value, value);
                entriesByKey.put(key, entry);
                allParsedEntries.add(entry);

                String group = getGroupName(key);
                groups.computeIfAbsent(group, k -> new ArrayList<>()).add(entry);
            }
        }

        // Build tree structure
        for (Map.Entry<String, List<MetadataEntry>> groupEntry : groups.entrySet()) {
            String groupName = groupEntry.getKey();
            List<MetadataEntry> entries = groupEntry.getValue();

            TreeItem<MetadataEntry> groupItem = new TreeItem<>(
                new MetadataEntry(groupName, "", "", "")
            );

            for (MetadataEntry entry : entries) {
                TreeItem<MetadataEntry> entryItem = new TreeItem<>(entry);
                groupItem.getChildren().add(entryItem);
            }

            rootItem.getChildren().add(groupItem);
        }

        metadataTreeTable.setRoot(rootItem);

        // Expand all groups
        for (TreeItem<MetadataEntry> child : rootItem.getChildren()) {
            child.setExpanded(true);
        }
    }

    private String getGroupName(String key) {
        if (key.startsWith("Exif.")) {
            String[] parts = key.split("\\.");
            if (parts.length >= 3) {
                return "EXIF - " + parts[1];
            }
            return "EXIF";
        } else if (key.startsWith("Iptc.")) {
            String[] parts = key.split("\\.");
            if (parts.length >= 3) {
                return "IPTC - " + parts[1];
            }
            return "IPTC";
        } else if (key.startsWith("Xmp.")) {
            String[] parts = key.split("\\.");
            if (parts.length >= 3) {
                return "XMP - " + parts[1];
            }
            return "XMP";
        } else if (key.startsWith("Comment")) {
            return "Comment";
        } else if (key.startsWith("Thumbnail")) {
            return "Thumbnail";
        }
        return "Other";
    }

    private void filterMetadata() {
        String selectedType = metadataTypeCombo.getValue();

        if ("All".equals(selectedType)) {
            // Rebuild tree with all entries
            rebuildTreeTable(null);
            return;
        }

        // Rebuild tree with only matching entries
        rebuildTreeTable(selectedType);
    }

    /**
     * Rebuilds the tree table, optionally filtering by metadata type.
     */
    private void rebuildTreeTable(String filterType) {
        rootItem = new TreeItem<>(new MetadataEntry("", "", "", ""));

        // Group entries by namespace, applying filter
        Map<String, List<MetadataEntry>> groups = new LinkedHashMap<>();

        for (MetadataEntry entry : allParsedEntries) {
            String key = entry.getKey();
            
            // Apply filter
            if (filterType != null && !matchesTypeFilter(key, filterType)) {
                continue;
            }

            // Determine group
            String group = getGroupName(key);
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(entry);
        }

        // Build tree structure
        for (Map.Entry<String, List<MetadataEntry>> groupEntry : groups.entrySet()) {
            String groupName = groupEntry.getKey();
            List<MetadataEntry> entries = groupEntry.getValue();

            TreeItem<MetadataEntry> groupItem = new TreeItem<>(
                new MetadataEntry(groupName, "", "", "")
            );

            for (MetadataEntry entry : entries) {
                TreeItem<MetadataEntry> entryItem = new TreeItem<>(entry);
                groupItem.getChildren().add(entryItem);
            }

            rootItem.getChildren().add(groupItem);
        }

        metadataTreeTable.setRoot(rootItem);

        // Expand all groups
        for (TreeItem<MetadataEntry> child : rootItem.getChildren()) {
            child.setExpanded(true);
        }

        if (filterType != null) {
            setStatus("Showing: " + filterType + " metadata (" + groups.size() + " groups)");
        } else {
            setStatus("Showing all metadata (" + groups.size() + " groups)");
        }
    }

    private boolean matchesTypeFilter(String key, String selectedType) {
        if ("All".equals(selectedType)) {
            return true;
        }
        return switch (selectedType) {
            case "EXIF" -> key.startsWith("Exif.");
            case "IPTC" -> key.startsWith("Iptc.");
            case "XMP" -> key.startsWith("Xmp.");
            case "Comment" -> key.startsWith("Comment");
            case "Thumbnail" -> key.startsWith("Thumbnail");
            default -> true;
        };
    }

    private void highlightModifiedRow(TreeItem<MetadataEntry> item, boolean modified) {
        // Visual feedback for modified rows could be added here
        // For now, the status message provides feedback
    }

    private void applyAllChanges() {
        if (pendingModifications.isEmpty()) {
            showInfo("No Changes", "No modifications to apply.", null);
            return;
        }

        setStatus("Applying " + pendingModifications.size() + " change(s)...");
        setButtonsDisabled(true);

        CompletableFuture.runAsync(() -> {
            java.io.File tempCmdFile = null;
            try {
                // Check if file is writable
                if (!imageFile.canWrite()) {
                    Platform.runLater(() -> showError(
                        "Cannot modify metadata: File is read-only",
                        "Permission Denied",
                        "The image file is marked as read-only.\n\n" +
                        "Please change the file attributes or choose a different file."));
                    return;
                }

                // Create temporary command file for exiv2
                tempCmdFile = java.io.File.createTempFile("exiv2_cmd_", ".txt");
                tempCmdFile.deleteOnExit();
                
                // Write commands to temp file
                StringBuilder cmdContent = new StringBuilder();
                try (java.io.PrintWriter writer = new java.io.PrintWriter(tempCmdFile, StandardCharsets.UTF_8)) {
                    for (MetadataModification mod : pendingModifications) {
                        // Escape quotes for command-file parsing and quote when needed.
                        String value = normalizeValueForWrite(mod.key(), mod.value());
                        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
                        if (escaped.isEmpty() || escaped.chars().anyMatch(Character::isWhitespace) || escaped.contains("\"")) {
                            value = "\"" + escaped + "\"";
                        } else {
                            value = escaped;
                        }
                        String cmdLine = "set " + mod.key() + " " + value;
                        writer.println(cmdLine);
                        cmdContent.append(cmdLine).append("\n");
                    }
                }
                
                logger.info("Created temp command file: {}", tempCmdFile.getAbsolutePath());
                logger.info("Command file content:\n{}", cmdContent.toString());

                // Build modify command using temp file
                List<String> command = new ArrayList<>();
                command.add(EXIV2_PATH);
                command.add("-m");
                command.add(tempCmdFile.getAbsolutePath());
                command.add("\"" + imageFile.getAbsolutePath() + "\"");

                logger.info("Applying metadata changes using command file");

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File(System.getProperty("user.dir")));

                Process process;
                try {
                    process = pb.start();
                } catch (IOException e) {
                    Platform.runLater(() -> showError(
                        "Failed to start exiv2.exe: " + e.getMessage(),
                        "Execution Error",
                        "The exiv2 executable may be missing or corrupted."));
                    return;
                }

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    metadataModified = true;
                    Platform.runLater(() -> {
                        setStatus("All changes applied successfully");
                        pendingModifications.clear();
                        loadMetadata(); // Reload to show updated values
                    });
                } else {
                    StringBuilder errorOutput = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorOutput.append(line).append("\n");
                        }
                    } catch (IOException e) {
                        errorOutput.append("Failed to read error: ").append(e.getMessage());
                    }

                    String errorMsg = errorOutput.length() > 0 ? errorOutput.toString() : "Exit code: " + exitCode;
                    logger.error("Failed to apply metadata changes: {}", errorMsg);

                    Platform.runLater(() -> showError(
                        "Failed to apply changes",
                        "Modification Failed",
                        "exiv2 returned an error:\n\n" + errorMsg +
                        "\n\nPossible causes:\n" +
                        "  - Invalid value type for tag\n" +
                        "  - File format doesn't support this metadata\n" +
                        "  - Tag is read-only\n" +
                        "  - Invalid key format"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> showError("Operation was interrupted", "Interrupted", null));
            } catch (Exception e) {
                logger.error("Failed to apply metadata changes", e);
                Platform.runLater(() -> showError(
                    "Error: " + e.getMessage(),
                    "Unexpected Error",
                    "An unexpected error occurred while applying changes."));
            } finally {
                // Clean up temp file
                if (tempCmdFile != null && tempCmdFile.exists()) {
                    tempCmdFile.delete();
                }
                Platform.runLater(() -> setButtonsDisabled(false));
            }
        }, executor);
    }

    private void extractMetadata() {
        setStatus("Extracting metadata to sidecar file...");
        setButtonsDisabled(true);

        CompletableFuture.runAsync(() -> {
            try {
                if (!imageFile.canRead()) {
                    Platform.runLater(() -> showError(
                        "Cannot read image file",
                        "Permission Denied",
                        "The image file is not readable.\n\nPlease check file permissions."));
                    return;
                }

                List<String> command = new ArrayList<>();
                command.add(EXIV2_PATH);
                command.add("--extract");
                command.add("X");
                command.add("\"" + imageFile.getAbsolutePath() + "\"");

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File(System.getProperty("user.dir")));
                
                Process process;
                try {
                    process = pb.start();
                } catch (IOException e) {
                    Platform.runLater(() -> showError(
                        "Failed to start exiv2.exe: " + e.getMessage(),
                        "Execution Error",
                        "The exiv2 executable may be missing or corrupted."));
                    return;
                }
                
                int exitCode = process.waitFor();

                Platform.runLater(() -> {
                    if (exitCode == 0) {
                        String sidecarPath = imageFile.getAbsolutePath() + ".xmp";
                        setStatus("Metadata extracted to: " + sidecarPath);
                        showInfo("Success",
                                "Metadata extracted successfully",
                                "Sidecar file created:\n" + sidecarPath +
                                "\n\nThis XMP file contains all metadata from the image.");
                    } else {
                        StringBuilder errorOutput = new StringBuilder();
                        try {
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    errorOutput.append(line).append("\n");
                                }
                            }
                        } catch (IOException e) {
                            errorOutput.append("Failed to read error output: ").append(e.getMessage());
                        }
                        String errorMsg = errorOutput.length() > 0 ? errorOutput.toString() : "Exit code: " + exitCode;
                        showError("Failed to extract metadata",
                                "Extraction Failed",
                                "exiv2 returned an error:\n\n" + errorMsg);
                    }
                    setButtonsDisabled(false);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    showError("Operation was interrupted", "Interrupted", null);
                    setButtonsDisabled(false);
                });
            } catch (Exception e) {
                logger.error("Failed to extract metadata", e);
                Platform.runLater(() -> {
                    showError("Error: " + e.getMessage(), "Unexpected Error", null);
                    setButtonsDisabled(false);
                });
            }
        }, executor);
    }

    private void insertMetadata() {
        String sidecarPath = imageFile.getAbsolutePath() + ".xmp";
        File sidecarFile = new File(sidecarPath);

        if (!sidecarFile.exists()) {
            showError("No sidecar file found",
                      "File Not Found",
                      "No XMP sidecar file found at:\n" + sidecarPath + 
                      "\n\nUse 'Extract to XMP' first to create a sidecar file, " +
                      "or place a valid .xmp file with the same name as the image.");
            return;
        }

        setStatus("Inserting metadata from sidecar file...");
        setButtonsDisabled(true);

        CompletableFuture.runAsync(() -> {
            try {
                if (!imageFile.canWrite()) {
                    Platform.runLater(() -> showError(
                        "Cannot modify image file",
                        "Permission Denied",
                        "The image file is read-only.\n\n" +
                        "Please change file attributes before inserting metadata."));
                    return;
                }
                if (!sidecarFile.canRead()) {
                    Platform.runLater(() -> showError(
                        "Cannot read sidecar file",
                        "Permission Denied",
                        "The XMP sidecar file is not readable."));
                    return;
                }

                List<String> command = new ArrayList<>();
                command.add(EXIV2_PATH);
                command.add("--insert");
                command.add("X");
                command.add("\"" + imageFile.getAbsolutePath() + "\"");

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File(System.getProperty("user.dir")));
                
                Process process;
                try {
                    process = pb.start();
                } catch (IOException e) {
                    Platform.runLater(() -> showError(
                        "Failed to start exiv2.exe: " + e.getMessage(),
                        "Execution Error",
                        "The exiv2 executable may be missing or corrupted."));
                    return;
                }
                
                int exitCode = process.waitFor();

                Platform.runLater(() -> {
                    if (exitCode == 0) {
                        metadataModified = true;
                        setStatus("Metadata inserted successfully");
                        loadMetadata();
                    } else {
                        StringBuilder errorOutput = new StringBuilder();
                        try {
                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    errorOutput.append(line).append("\n");
                                }
                            }
                        } catch (IOException e) {
                            errorOutput.append("Failed to read error output: ").append(e.getMessage());
                        }
                        String errorMsg = errorOutput.length() > 0 ? errorOutput.toString() : "Exit code: " + exitCode;
                        showError("Failed to insert metadata",
                                "Insertion Failed",
                                "exiv2 returned an error:\n\n" + errorMsg +
                                "\n\nPossible causes:\n" +
                                "  - Invalid or corrupted XMP sidecar file\n" +
                                "  - Metadata format incompatible with image");
                    }
                    setButtonsDisabled(false);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> {
                    showError("Operation was interrupted", "Interrupted", null);
                    setButtonsDisabled(false);
                });
            } catch (Exception e) {
                logger.error("Failed to insert metadata", e);
                Platform.runLater(() -> {
                    showError("Error: " + e.getMessage(), "Unexpected Error", null);
                    setButtonsDisabled(false);
                });
            }
        }, executor);
    }

    private void setStatus(String status) {
        if (statusLabel != null) {
            Platform.runLater(() -> statusLabel.setText(status));
        }
    }

    private void showAddTagDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Metadata Tag");
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (commander != null) {
            applyTheme(dialog);
        }

        ComboBox<MetadataTemplate> tagCombo = new ComboBox<>();
        tagCombo.getItems().addAll(COMMON_METADATA_TEMPLATES);
        tagCombo.setValue(COMMON_METADATA_TEMPLATES.getFirst());
        tagCombo.setMaxWidth(Double.MAX_VALUE);

        Label keyLabel = new Label();
        keyLabel.setStyle("-fx-font-family: monospace;");
        keyLabel.setWrapText(true);

        TextField valueField = new TextField();
        valueField.setPromptText("Metadata value");

        Runnable syncKeyLabel = () -> {
            MetadataTemplate selected = tagCombo.getValue();
            if (selected == null) {
                keyLabel.setText("");
                return;
            }
            keyLabel.setText(selected.key());
        };
        syncKeyLabel.run();
        tagCombo.setOnAction(e -> syncKeyLabel.run());

        VBox content = new VBox(
                8,
                new Label("Choose Tag"),
                tagCombo,
                new Label("Key"),
                keyLabel,
                new Label("Value"),
                valueField
        );
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(false);

        Platform.runLater(valueField::requestFocus);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        MetadataTemplate selected = tagCombo.getValue();
        if (selected == null) {
            return;
        }
        String key = selected.key();
        String value = valueField.getText() == null ? "" : valueField.getText();

        MetadataEntry existing = entriesByKey.get(key);
        if (existing != null) {
            existing.setValue(value);
            existing.setModified(true);
            upsertPendingModification(key, value);
            metadataTypeCombo.setValue("All");
            rebuildTreeTable(null);
            setStatus("Pending update: " + key + " = " + (value.isEmpty() ? "(empty)" : value));
            logger.info("Updated pending metadata key '{}'", key);
            return;
        }

        MetadataEntry entry = new MetadataEntry(key, selected.type(), value, "");
        entry.setModified(true);
        entriesByKey.put(key, entry);
        allParsedEntries.add(entry);
        upsertPendingModification(key, value);
        metadataTypeCombo.setValue("All");
        rebuildTreeTable(null);
        setStatus("Pending new tag: " + key + " = " + (value.isEmpty() ? "(empty)" : value));
        logger.info("Added pending metadata key '{}'", key);
    }

    private void setButtonsDisabled(boolean disabled) {
        if (loadButton != null) loadButton.setDisable(disabled);
        if (saveButton != null) saveButton.setDisable(disabled);
        if (applyButton != null) applyButton.setDisable(disabled);
        if (addTagButton != null) addTagButton.setDisable(disabled);
        if (metadataTypeCombo != null) metadataTypeCombo.setDisable(disabled);
        if (metadataTreeTable != null) metadataTreeTable.setDisable(disabled);
    }

    private void upsertPendingModification(String key, String value) {
        pendingModifications.removeIf(mod -> mod.key().equals(key));
        pendingModifications.add(new MetadataModification(key, value));
    }

    private String normalizeValueForDisplay(String type, String value) {
        if (value == null) {
            return "";
        }
        if ("LangAlt".equalsIgnoreCase(type)) {
            return stripLangAltPrefix(value);
        }
        return value;
    }

    private String normalizeValueForWrite(String key, String value) {
        String normalized = value == null ? "" : value;
        MetadataEntry entry = entriesByKey.get(key);
        if (entry != null && "LangAlt".equalsIgnoreCase(entry.getType())) {
            return stripLangAltPrefix(normalized);
        }
        return normalized;
    }

    private String stripLangAltPrefix(String value) {
        return value.replaceFirst("^lang=\"[^\"]+\"\\s+", "");
    }

    private void showError(String message) {
        showError(message, "Error", null);
    }

    private void showError(String message, String title, String details) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title != null ? title : "Error");
            alert.setHeaderText(null);
            
            StringBuilder content = new StringBuilder(message);
            if (details != null && !details.isEmpty()) {
                content.append("\n\n").append(details);
            }
            alert.setContentText(content.toString());
            
            alert.setResizable(true);
            alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            alert.getDialogPane().setPrefWidth(500);
            
            applyThemeToAlert(alert);
            alert.showAndWait();
        });
    }

    private void showInfo(String message) {
        showInfo("Information", message, null);
    }

    private void showInfo(String title, String message, String details) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title != null ? title : "Information");
            alert.setHeaderText(null);
            
            StringBuilder content = new StringBuilder(message);
            if (details != null && !details.isEmpty()) {
                content.append("\n\n").append(details);
            }
            alert.setContentText(content.toString());
            
            alert.setResizable(true);
            alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            alert.getDialogPane().setPrefWidth(500);
            
            applyThemeToAlert(alert);
            alert.showAndWait();
        });
    }

    private void showErrorInTable(String errorMessage) {
        entriesByKey.clear();
        rootItem = new TreeItem<>(new MetadataEntry("", "", "", ""));
        
        TreeItem<MetadataEntry> errorItem = new TreeItem<>(
            new MetadataEntry("Error", "", errorMessage, "")
        );
        rootItem.getChildren().add(errorItem);
        metadataTreeTable.setRoot(rootItem);
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

    /**
     * Represents a metadata entry in the tree table.
     */
    private static class MetadataEntry {
        private final javafx.beans.property.SimpleStringProperty key;
        private final javafx.beans.property.SimpleStringProperty type;
        private final javafx.beans.property.SimpleStringProperty value;
        private final String originalValue;
        private boolean modified;

        public MetadataEntry(String key, String type, String value, String originalValue) {
            this.key = new javafx.beans.property.SimpleStringProperty(key);
            this.type = new javafx.beans.property.SimpleStringProperty(type);
            this.value = new javafx.beans.property.SimpleStringProperty(value);
            this.originalValue = originalValue;
            this.modified = false;
        }

        public javafx.beans.property.StringProperty keyProperty() { return key; }
        public javafx.beans.property.StringProperty typeProperty() { return type; }
        public javafx.beans.property.StringProperty valueProperty() { return value; }
        
        public String getKey() { return key.get(); }
        public String getType() { return type.get(); }
        public String getValue() { return value.get(); }
        public String getOriginalValue() { return originalValue; }
        public boolean isModified() { return modified; }
        public boolean isEditableEntry() { return key.get() != null && key.get().contains(".") && !type.get().isEmpty(); }
        
        public void setValue(String value) { this.value.set(value); }
        public void setModified(boolean modified) { this.modified = modified; }
    }

    /**
     * Represents a pending metadata modification.
     */
    private record MetadataModification(String key, String value) {}

    private record MetadataTemplate(String label, String key, String type) {
        @Override
        public String toString() {
            return label + " (" + key + ")";
        }
    }
}
