package org.chaiware.acommander;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.chaiware.acommander.actions.ActionContext;
import org.chaiware.acommander.actions.ActionExecutor;
import org.chaiware.acommander.actions.ActionRegistry;
import org.chaiware.acommander.commands.ACommands;
import org.chaiware.acommander.commands.CommandsAdvancedImpl;
import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.ComboBoxSetup;
import org.chaiware.acommander.helpers.FileAttributesHelper;
import org.chaiware.acommander.helpers.FilesPanesHelper;
import org.chaiware.acommander.keybinding.KeyBindingManager;
import org.chaiware.acommander.keybinding.KeyBindingManager.KeyContext;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.palette.CommandPaletteController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.*;

import static java.awt.Desktop.getDesktop;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.RIGHT;


public class Commander {
    private static final String LEFT_FOLDER_KEY = "left_folder";
    private static final String RIGHT_FOLDER_KEY = "right_folder";

    @FXML
    public BorderPane rootPane;
    @FXML
    public ComboBox<Folder> leftPathComboBox;
    @FXML
    public ComboBox<Folder> rightPathComboBox;
    @FXML
    public ListView<FileItem> leftFileList;
    @FXML
    public ListView<FileItem> rightFileList;
    @FXML
    Button btnF1, btnF2, btnF3, btnF4, btnF5, btnF6, btnF7, btnF8, btnF9, btnF10, btnF11, btnF12;
    @FXML
    private CommandPaletteController commandPaletteController;

    Properties properties = new Properties();
    ACommands commands;
    private AppRegistry appRegistry;
    private ActionExecutor actionExecutor;

    private static final Logger logger = LoggerFactory.getLogger(Commander.class);
    public FilesPanesHelper filesPanesHelper;
    private final FileAttributesHelper attributesHelper = new FileAttributesHelper();


    @FXML
    public void initialize() {
        logger.debug("Loading Properties");
        loadConfigFile();

        // Configure left & right defaults
        filesPanesHelper = new FilesPanesHelper(leftFileList, leftPathComboBox, rightFileList, rightPathComboBox);
        appRegistry = loadAppRegistry();
        actionExecutor = new ActionExecutor(this, appRegistry);
        commands = new CommandsAdvancedImpl(filesPanesHelper, appRegistry);
        configMouseDoubleClick();

        logger.debug("Loading file lists into the double panes file views");
        ComboBoxSetup setup = new ComboBoxSetup();
        setup.setupComboBox(leftPathComboBox);
        setup.setupComboBox(rightPathComboBox);
        filesPanesHelper.setFileListPath(LEFT, resolveInitialPath(LEFT_FOLDER_KEY));
        filesPanesHelper.setFileListPath(RIGHT, resolveInitialPath(RIGHT_FOLDER_KEY));
        leftPathComboBox.valueProperty().addListener((observable, oldValue, newValue) -> onPathChanged(LEFT, newValue));
        rightPathComboBox.valueProperty().addListener((observable, oldValue, newValue) -> onPathChanged(RIGHT, newValue));

        configListViewLookAndBehavior(leftFileList);
        configListViewLookAndBehavior(rightFileList);
        configFileListsFocus();
        commandPaletteController.configure(new ActionRegistry(appRegistry, actionExecutor), new ActionContext(this));

        updateBottomButtons(null);
        filesPanesHelper.refreshFileListViews();
        filesPanesHelper.getFileList(true).getSelectionModel().selectFirst();
        Platform.runLater(() -> leftFileList.requestFocus());
    }

    private void onPathChanged(FilesPanesHelper.FocusSide side, Folder newValue) {
        if (newValue == null) {
            return;
        }

        properties.setProperty(side == LEFT ? LEFT_FOLDER_KEY : RIGHT_FOLDER_KEY, newValue.getPath());
        saveConfigFile();
        filesPanesHelper.refreshFileListView(side);
    }

    /** Setup all of the keyboard bindings */
    public void setupBindings() {
        Scene scene = rootPane.getScene();
        KeyBindingManager keyBindingManager = new KeyBindingManager(this, appRegistry, actionExecutor);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            keyBindingManager.setCurrentContext(determineCurrentContext(scene));
            keyBindingManager.handleKeyEvent(event);
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            keyBindingManager.setCurrentContext(determineCurrentContext(scene));
            keyBindingManager.handleReleasedKeyEvent(event);
        });
    }

    public KeyContext determineCurrentContext(Scene scene) {
        if (isCommandPaletteOpen())
            return KeyContext.COMMAND_PALETTE;

        Node focused = scene.getFocusOwner();

        // Check if we're in a JavaFX Dialog
        if (focused != null) {
            Window focusedWindow = focused.getScene().getWindow();
            // Check if the focused window is different from main window (likely a dialog)
            if (focusedWindow != scene.getWindow())
                return KeyContext.DIALOG;
        }

        if (focused == leftFileList || focused == rightFileList) return KeyContext.FILE_PANE;
        if (focused == leftPathComboBox || focused == rightPathComboBox) return KeyContext.PATH_COMBO_BOX;
        return KeyContext.GLOBAL;
    }

    public void openCommandPalette() {
        commandPaletteController.open();
    }

    public void closeCommandPalette() {
        commandPaletteController.close();
        if (filesPanesHelper.getFocusedSide() == LEFT)
            leftFileList.requestFocus();
        else
            rightFileList.requestFocus();
    }

    public boolean isCommandPaletteOpen() {
        return commandPaletteController != null && commandPaletteController.isOpen();
    }

    public void executeCommandPaletteSelection() {
        commandPaletteController.executeSelected();
    }

    public void selectNextCommandPaletteAction() {
        commandPaletteController.selectNext();
    }

    public void selectPreviousCommandPaletteAction() {
        commandPaletteController.selectPrevious();
    }

    private void loadConfigFile() {
        Path configFile = getConfigFilePath();
        if (!Files.exists(configFile))
            return;

        try (FileInputStream input = new FileInputStream(configFile.toFile())) {
            properties.load(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path getConfigFilePath() {
        return Paths.get(System.getProperty("user.dir"), "config", "acommander.properties");
    }

    private String resolveInitialPath(String key) {
        String configuredPath = properties.getProperty(key);
        if (configuredPath != null && new File(configuredPath).exists())
            return configuredPath;
        return getDefaultRootPath();
    }

    private AppRegistry loadAppRegistry() {
        Path appConfig = Paths.get(System.getProperty("user.dir"), "config", "apps.json");
        try {
            return new AppRegistry(new AppConfigLoader().load(appConfig));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed loading actions config from: " + appConfig, ex);
        }
    }

    private String getDefaultRootPath() {
        File[] roots = File.listRoots();
        if (roots != null && roots.length > 0)
            return roots[0].getPath();
        return System.getProperty("user.home");
    }

    private void saveConfigFile() {
        Path configFile = getConfigFilePath();
        try {
            Files.createDirectories(configFile.getParent());
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                properties.store(output, null);
                String raw = output.toString(StandardCharsets.ISO_8859_1);
                int firstLineEnd = raw.indexOf('\n');
                String withoutTimestamp = firstLineEnd >= 0 ? raw.substring(firstLineEnd + 1) : "";
                Files.writeString(configFile, withoutTimestamp, StandardCharsets.ISO_8859_1);
            }
        } catch (Exception ex) {
            logger.error("Failed saving config file {}", configFile, ex);
        }
    }

    public void persistCurrentPaths() {
        if (filesPanesHelper == null)
            return;

        properties.setProperty(LEFT_FOLDER_KEY, filesPanesHelper.getPath(LEFT));
        properties.setProperty(RIGHT_FOLDER_KEY, filesPanesHelper.getPath(RIGHT));
        saveConfigFile();
    }

    private void configMouseDoubleClick() {
        logger.debug("Configuring Mouse Double Click");
        leftFileList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                enterSelectedItem();
            }
        });
        rightFileList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                enterSelectedItem();
            }
        });
    }

    private void configListViewLookAndBehavior(ListView<FileItem> listView) {
        logger.debug("Configuring the ListViews look and experience");
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setCellFactory(lv -> new ListCell<>() {
            final Label iconLabel = new Label();
            final Label nameLabel = new Label();
            final Label sizeLabel = new Label();
            final Label dateLabel = new Label();
            final HBox hbox = new HBox(iconLabel, nameLabel, sizeLabel, dateLabel);

            {
                iconLabel.setMinWidth(36);
                iconLabel.setMaxWidth(36);
                iconLabel.setAlignment(Pos.CENTER);
                iconLabel.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Segoe UI Symbol'; -fx-font-size: 14px;");

                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                nameLabel.setEllipsisString("...");
                nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                HBox.setHgrow(nameLabel, Priority.ALWAYS);

                sizeLabel.setMinWidth(100);
                sizeLabel.setMaxWidth(100);
                sizeLabel.setAlignment(Pos.CENTER_RIGHT);

                dateLabel.setMinWidth(120);
                dateLabel.setMaxWidth(120);
                dateLabel.setAlignment(Pos.CENTER_RIGHT);

                hbox.setSpacing(8);
                hbox.setStyle("-fx-font-family: 'JetBrains Mono'; -fx-font-size: 14px; -fx-hbar-policy: never;");

                listView.widthProperty().addListener((obs, oldVal, newVal) -> {
                    double width = newVal.doubleValue() - 20;
                    hbox.setMaxWidth(width);
                    hbox.setPrefWidth(width);
                });
            }

            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    IconSpec iconSpec = resolveIconSpec(item);
                    iconLabel.setText(iconSpec.glyph());
                    iconLabel.setStyle(String.format(
                            "-fx-font-family: 'Segoe UI Emoji', 'Segoe UI Symbol'; -fx-font-size: 14px; -fx-text-fill: %s;",
                            iconSpec.textColor()
                    ));
                    nameLabel.setText(item.getPresentableFilename());
                    sizeLabel.setText(String.format("%s", item.getHumanReadableSize()));
                    dateLabel.setText(item.getDate());

                    // Constrain width to ListView cell
                    double width = listView.getWidth() - 20; // leave margin for scrollbar
                    hbox.setMaxWidth(width);
                    hbox.setPrefWidth(width);

                    setGraphic(hbox);
                }
            }
        });
    }

    private record IconSpec(String glyph, String textColor) {}

    private IconSpec resolveIconSpec(FileItem item) {
        if ("..".equals(item.getPresentableFilename())) {
            return new IconSpec("↩", "#E0E0E0");
        }
        if (item.isDirectory()) {
            return new IconSpec("📁", "#FFD54F");
        }

        String name = item.getName();
        String extension = "";
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < name.length() - 1) {
            extension = name.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        }

        if (isArchiveExtension(extension)) {
            return new IconSpec("📦", "#FFB74D");
        }
        if ("pdf".equals(extension)) {
            return new IconSpec("📕", "#EF9A9A");
        }
        if (isTextExtension(extension)) {
            return new IconSpec("📄", "#C8E6C9");
        }
        if (isImageExtension(extension)) {
            return new IconSpec("🖼", "#B2EBF2");
        }
        if (isAudioExtension(extension)) {
            return new IconSpec("🎵", "#FFE0B2");
        }
        if (isVideoExtension(extension)) {
            return new IconSpec("🎬", "#F8BBD0");
        }
        if (isExecutableExtension(extension)) {
            return new IconSpec("⚙", "#CFD8DC");
        }

        return new IconSpec("📃", "#E0E0E0");
    }

    private boolean isArchiveExtension(String extension) {
        return switch (extension) {
            case "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz" -> true;
            default -> false;
        };
    }

    private boolean isTextExtension(String extension) {
        return switch (extension) {
            case "txt", "md", "log", "json", "xml", "yml", "yaml", "csv", "ini", "conf", "properties",
                 "gradle", "kts", "java", "kt", "js", "ts", "html", "css" -> true;
            default -> false;
        };
    }

    private boolean isImageExtension(String extension) {
        return switch (extension) {
            case "png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "ico" -> true;
            default -> false;
        };
    }

    private boolean isAudioExtension(String extension) {
        return switch (extension) {
            case "mp3", "wav", "flac", "aac", "ogg", "opus", "m4a" -> true;
            default -> false;
        };
    }

    private boolean isVideoExtension(String extension) {
        return switch (extension) {
            case "mp4", "mkv", "avi", "mov", "wmv", "webm", "m4v" -> true;
            default -> false;
        };
    }

    private boolean isExecutableExtension(String extension) {
        return switch (extension) {
            case "exe", "msi", "bat", "cmd", "ps1", "sh" -> true;
            default -> false;
        };
    }

    private void configFileListsFocus() {
        logger.debug("Configure focus setting (so we will know where focus was last been)");
        leftFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) filesPanesHelper.setFocusedFileList(LEFT);
        });
        rightFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) filesPanesHelper.setFocusedFileList(RIGHT);
        });
        leftFileList.requestFocus();
    }

    /**
     * Runs the command of clicking on an item with the ENTER key (run associated program / goto folder)
     */
    public void enterSelectedItem() {
        logger.debug("User clicked ENTER (or mouse double-click)");

        String currentPath = filesPanesHelper.getFocusedPath();
        FileItem selectedItem = filesPanesHelper.getSelectedItem();
        logger.debug("Running: {}", selectedItem.getName());
        if ("..".equals(selectedItem.getPresentableFilename())) {
            File parent = new File(currentPath).getParentFile();
            if (parent != null)
                filesPanesHelper.setFocusedFileListPath(parent.getAbsolutePath());
        } else if (selectedItem.isDirectory())
            filesPanesHelper.setFocusedFileListPath(selectedItem.getFullPath());
        else {
            try {
                getDesktop().open(selectedItem.getFile());
            } catch (Exception ex) {
                logger.error("Failed opening: {}", selectedItem.getName(), ex);
            }
        }
    }

    @FXML
    public void help() {
        logger.info("Help (F1)");

        try {
            File helpFile = (Paths.get(System.getProperty("user.dir"), "config", "f1-help.md")).toFile();
            FileItem selectedItem = new FileItem(helpFile, helpFile.getName());
            commands.view(selectedItem);
        } catch (Exception ex) {
            error("Failed Viewing file", ex);
        }
    }

    @FXML
    public void renameFile() {
        logger.info("Rename (F2)");

        try {
            List<FileItem> selectedItems = commands.filterValidItems(filesPanesHelper.getSelectedItems());
            if (selectedItems.isEmpty())
                return;
            if (selectedItems.size() == 1) {
                FileItem selectedItem = selectedItems.getFirst();
                Optional<String> result = getUserFeedback(selectedItem.getFile().getName(), "File Rename", "New name");
                if (result.isPresent()) { // if user dismisses the dialog it won't rename...
                    commands.rename(Collections.singletonList(selectedItem), result.get());
                    FileItem renamedFileItem = new FileItem(new File(filesPanesHelper.getFocusedPath() + "\\" + result.get()));
                    filesPanesHelper.selectFileItem(true, renamedFileItem);
                }
            } else // Multi files selected (multi rename)
                commands.rename(selectedItems, "");
        } catch (Exception e) {
            error("Failed Renaming file/s", e);
        }
    }

    @FXML
    public void viewFile() {
        logger.info("View (F3)");

        try {
            List<FileItem> selectedItems = filesPanesHelper.getSelectedItems();
            for (FileItem selectedItem : selectedItems)
                commands.view(selectedItem);
        } catch (Exception ex) {
            error("Failed Viewing file", ex);
        }
    }

    public void calculateDirSpace() {
        logger.info("calculateDirSpace (F3 (on folder))");

        try {
            FileItem selectedItem = filesPanesHelper.getSelectedItem();
            if (!selectedItem.isDirectory()) {
                logger.error("Error: Trying to calculate size of a file and not a folder ??");
                return;
            }

            long sizeOfFolder = Files.walk(selectedItem.getFile().toPath())
                    .parallel()
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();

            selectedItem.setSize(sizeOfFolder);
            filesPanesHelper.getFileList(true).refresh();
        } catch (Exception ex) {
            error("Failed calculating folder size", ex);
        }
    }

    @FXML
    public void editFile() {
        logger.info("Edit (F4)");

        try {
            List<FileItem> fileItems = filesPanesHelper.getSelectedItems();
            for (FileItem fileItem : fileItems)
                commands.edit(fileItem);
        } catch (Exception ex) {
            error("Failed Editing file", ex);
        }
    }

    @FXML
    public void copyFile() {
        logger.info("Copy (F5)");

        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems) {
                String targetFolder = filesPanesHelper.getUnfocusedPath();
                if (selectedItem.isDirectory())
                    targetFolder += "\\" + selectedItem.getName();
                commands.copy(selectedItem, targetFolder);

                // taking care of the selected files
                File target = selectedItem.isDirectory()
                        ? new File(targetFolder)
                        : new File(targetFolder, selectedItem.getName());
                filesPanesHelper.selectFileItem(false, new FileItem(target));
            }
        } catch (Exception e) {
            error("Failed Copying file", e);
        }
    }

    @FXML
    public void moveFile() {
        logger.info("Move (F6)");

        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems) {
                String targetFolder = filesPanesHelper.getUnfocusedPath();
                if (selectedItem.isDirectory())
                    targetFolder += "\\" + selectedItem.getName();
                commands.move(selectedItem, targetFolder);

                // taking care of the selected files
                filesPanesHelper.getFileList(true)
                        .getSelectionModel()
                        .selectFirst();
                File target = selectedItem.isDirectory()
                        ? new File(targetFolder)
                        : new File(targetFolder, selectedItem.getName());
                filesPanesHelper.selectFileItem(false, new FileItem(target));
            }
        } catch (Exception ex) {
            error("Failed Moving file", ex);
        }
    }

    @FXML
    public void makeDirectory() {
        logger.info("Create Directory (F7)");

        try {
            Optional<String> result = getUserFeedback("", "Make Directory", "New Directory Name");
            if (result.isPresent()) { // if user dismisses the dialog it won't create a directory...
                commands.mkdir((filesPanesHelper.getFocusedPath()), result.get());
                FileItem newFolder = new FileItem(new File(filesPanesHelper.getFocusedPath() + "\\" + result.get()));
                filesPanesHelper.selectFileItem(true, newFolder);
            }
        } catch (Exception e) {
            error("Failed Creating Directory", e);
        }
    }

    public void makeFile() {
        logger.info("Create File (ALT+F7)");

        try {
            Optional<String> result = getUserFeedback("", "Make File", "New File Name");
            if (result.isPresent()) {// if user dismisses the dialog it won't create a file...
                commands.mkFile((filesPanesHelper.getFocusedPath()), result.get());
                FileItem newFile = new FileItem(new File(filesPanesHelper.getFocusedPath() + "\\" + result.get()));
                filesPanesHelper.selectFileItem(true, newFile);
            }
        } catch (Exception e) {
            error("Failed Creating File", e);
        }
    }

    @FXML
    public void deleteFile() {
        logger.info("Delete (F8/DEL)");
        try {
            commands.delete(new ArrayList<>(filesPanesHelper.getSelectedItems()));
            filesPanesHelper.getFileList(true).getSelectionModel().selectFirst();
        } catch (Exception ex) {
            error("Failed to delete", ex);
        }
    }

    public void deleteWipe() {
        logger.info("Delete & Wipe (Shift+F8/DEL)");
        try {
            commands.wipeDelete(new ArrayList<>(filesPanesHelper.getSelectedItems()));
            filesPanesHelper.getFileList(true).getSelectionModel().selectFirst();
        } catch (Exception ex) {
            error("Failed to delete", ex);
        }
    }

    @FXML
    public void terminalHere() {
        logger.info("Open Terminal Here (F9)");
        String openHerePath = filesPanesHelper.getFocusedPath();
        try {
            commands.openTerminal(openHerePath);
        } catch (Exception ex) {
            error("Failed starting command line shell here: " + openHerePath, ex);
        }
    }

    public void explorerHere() {
        logger.info("Open Explorer Here (ALT+F9)");
        String openHerePath = filesPanesHelper.getFocusedPath();
        try {
            commands.openExplorer(openHerePath);
        } catch (Exception ex) {
            error("Failed opening explorer here: " + openHerePath, ex);
        }
    }

    @FXML
    public void search() {
        logger.info("Search Files (F10)");

        Optional<String> result = getUserFeedback("", "Search for File/s", "Enter (partial/wildcard) filename");
        if (result.isPresent()) {
            String searchFromPath = filesPanesHelper.getFocusedPath();
            try {
                commands.searchFiles(searchFromPath, result.get().contains("*") ? result.get() : "*" + result.get() + "*");
            } catch (Exception e) {
                error("Failed searching for: " + result.get(), e);
            }
        }
    }

    @FXML
    public void pack() {
        logger.info("Pack (F11)");
        try {
            List<FileItem> selectedItems = filesPanesHelper.getSelectedItems();
            String firstFilename = selectedItems.getFirst().getName();
            String zipFilename = firstFilename.contains(".")
                    ? firstFilename.substring(0, firstFilename.lastIndexOf('.')) + ".zip"
                    : firstFilename + ".zip";
            Optional<String> result = getUserFeedback(zipFilename, "Pack to zip", "Zip filename");
            if (result.isPresent()) {
                String filenameWithPath = filesPanesHelper.getUnfocusedPath() + "\\" + result.get();
                commands.pack(selectedItems, filenameWithPath);
                FileItem packedFile = new FileItem(new File(filenameWithPath));
                filesPanesHelper.selectFileItem(false, packedFile);
            } else
                logger.info("User cancelled the packing");
        } catch (Exception e) {
            error("Failed Packing file", e);
        }
    }

    @FXML
    public void unpackFile() {
        logger.info("UnPack (F12)");
        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems)
                commands.unpack(selectedItem, filesPanesHelper.getUnfocusedPath());
        } catch (Exception e) {
            error("Failed UNPacking file", e);
        }
    }

    public void extractAll() {
        logger.info("Extract All (ALT+F12)");
        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems)
                commands.extractAll(selectedItem, filesPanesHelper.getUnfocusedPath());
        } catch (Exception e) {
            error("Failed UNPacking file", e);
        }
    }

    public void mergePDFFiles() {
        logger.info("Merge PDF Files (SHIFT+F1)");
        try {
            List<FileItem> selectedItems = filesPanesHelper.getSelectedItems();
            String firstFilename = selectedItems.getFirst().getName();
            String zipFilename = firstFilename.contains(".")
                    ? firstFilename.substring(0, firstFilename.lastIndexOf('.')) + ".pdf"
                    : firstFilename + ".pdf";
            Optional<String> result = getUserFeedback(zipFilename, "Merge PDF Files", "PDF filename");
            if (result.isPresent()) {
                FileItem mergedFile = new FileItem(new File(filesPanesHelper.getUnfocusedPath() + "\\" + result.get()));
                commands.mergePDFs(selectedItems, mergedFile.getFullPath());
                filesPanesHelper.selectFileItem(false, mergedFile);
            } else
                logger.info("User cancelled the packing");
        } catch (Exception e) {
            error("Failed Packing file", e);
        }
    }

    public void extractPDFPages() {
        logger.info("Extract PDF Pages (SHIFT+F2)");
        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems)
                commands.extractPDFPages(selectedItem, filesPanesHelper.getUnfocusedPath());
        } catch (Exception e) {
            error("Failed Extracting Pages from PDF file", e);
        }
    }

    public void changeAttributes() {
        logger.info("Change Attributes");

        try {
            List<FileItem> selectedItems = commands.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
            if (selectedItems.isEmpty()) {
                return;
            }

            Optional<FileAttributesHelper.AttributeChangeRequest> request = promptAttributes(selectedItems);
            if (request.isEmpty()) {
                return;
            }

            List<String> failures = new ArrayList<>();
            for (FileItem selectedItem : selectedItems) {
                try {
                    attributesHelper.applyAttributesWithFallback(selectedItem.getFile().toPath(), request.get());
                } catch (Exception ex) {
                    logger.warn("Failed changing attributes for {}", selectedItem.getFullPath(), ex);
                    failures.add(selectedItem.getName() + ": " + ex.getMessage());
                }
            }

            filesPanesHelper.refreshFileListViews();
            if (!failures.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Change Attributes");
                alert.setHeaderText("Some items failed to update");
                int max = Math.min(8, failures.size());
                String msg = String.join("\n", failures.subList(0, max));
                if (failures.size() > max) {
                    msg += "\n... and " + (failures.size() - max) + " more";
                }
                alert.setContentText(msg);
                alert.setResizable(true);
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                alert.showAndWait();
            }
        } catch (Exception ex) {
            error("Failed changing attributes", ex);
        }
    }

    public void syncToOtherPane() {
        String focusedPath = filesPanesHelper.getFocusedPath();
        if (filesPanesHelper.getFocusedSide() == LEFT)
            filesPanesHelper.setFileListPath(RIGHT, focusedPath);
        else
            filesPanesHelper.setFileListPath(LEFT, focusedPath);
    }

    public void filterByChar(char selectedChar) {
        ObservableList<FileItem> fileItems = filesPanesHelper.getFileList(true).getItems();
        fileItems.stream()
                .filter(f -> f.getName().toLowerCase().startsWith(String.valueOf(selectedChar)))
                .findFirst().ifPresent(match -> filesPanesHelper.selectFileItem(true, match));
    }

    /** Opens a dialog with the title asking the requested question returning the optional user's input */
    private Optional<String> getUserFeedback(String defaultValue, String title, String question) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setHeaderText("");
        dialog.setTitle(title);
        dialog.setContentText(question);
        dialog.getEditor().setPrefWidth(300);

        return dialog.showAndWait();
    }

    public Optional<String> promptUser(String defaultValue, String title, String question) {
        return getUserFeedback(defaultValue, title, question);
    }

    public CompletableFuture<List<String>> runExternal(List<String> command, boolean refreshAfter) {
        return commands.runExternal(command, refreshAfter);
    }

    private Optional<FileAttributesHelper.AttributeChangeRequest> promptAttributes(List<FileItem> selectedItems) {
        Dialog<FileAttributesHelper.AttributeChangeRequest> dialog = new Dialog<>();
        dialog.setTitle("Change Attributes");
        dialog.setHeaderText(null);

        ButtonType applyType = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyType, ButtonType.CANCEL);

        Label title = new Label("Change Attributes");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitle = new Label("Selected items: " + selectedItems.size() + " | Checked = set, unchecked = clear");
        subtitle.setStyle("-fx-text-fill: #666666;");

        CheckBox readOnly = new CheckBox("Read-only");
        CheckBox hidden = new CheckBox("Hidden");
        CheckBox system = new CheckBox("System");
        CheckBox archive = new CheckBox("Archive");
        readOnly.setStyle("-fx-font-size: 13px;");
        hidden.setStyle("-fx-font-size: 13px;");
        system.setStyle("-fx-font-size: 13px;");
        archive.setStyle("-fx-font-size: 13px;");

        FileItem firstSelected = selectedItems.getFirst();
        FileAttributesHelper.ExistingAttributes current =
                attributesHelper.readExistingAttributes(firstSelected.getFile().toPath());
        readOnly.setSelected(current.readOnly());
        hidden.setSelected(current.hidden());
        system.setSelected(current.system());
        archive.setSelected(current.archive());

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.add(readOnly, 0, 0);
        grid.add(hidden, 1, 0);
        grid.add(system, 0, 1);
        grid.add(archive, 1, 1);

        VBox content = new VBox(12, title, subtitle, new Separator(), grid);
        content.setPadding(new Insets(14));
        content.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #d5d9e0; -fx-border-radius: 8;");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setStyle("-fx-background-color: #f3f5f8;");

        dialog.setResultConverter(button -> {
            if (button == applyType) {
                return new FileAttributesHelper.AttributeChangeRequest(
                        readOnly.isSelected(),
                        hidden.isSelected(),
                        system.isSelected(),
                        archive.isSelected()
                );
            }
            return null;
        });
        return dialog.showAndWait();
    }

    /** Alerts of an error and logs it */
    private void error(String error, Exception ex) {
        logger.error(error, ex);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(error + " (" + ex.getMessage() + ")");
        alert.setResizable(true);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }

    public void updateBottomButtons(KeyCode whichKeyWasPressed) {
        switch (whichKeyWasPressed) {
            case null -> {
                btnF1.setText("F1 Help");
                btnF2.setText("F2 Rename");
                btnF3.setText("F3 View");
                btnF4.setText("F4 Edit");
                btnF5.setText("F5 Copy");
                btnF6.setText("F6 Move");
                btnF7.setText("F7 MkDir");
                btnF8.setText("F8 Delete");
                btnF9.setText("F9 Terminal");
                btnF10.setText("F10 Search");
                btnF11.setText("F11 Pack");
                btnF12.setText("F12 UnPack");
            }
            case ALT -> {
                btnF1.setText("ALT+F1 Left Folder");
                btnF2.setText("ALT+F2 Right Folder");
                btnF4.setText("ALT+F4 Exit");
                btnF7.setText("ALT+F7 MkFile");
                btnF9.setText("ALT+F9 Explorer");
                btnF12.setText("ALT+F12 Extract All");
            }
            case SHIFT -> {
                btnF1.setText("SHIFT+F1 Merge PDF");
                btnF2.setText("SHIFT+F2 Extract PDF");
                btnF6.setText("SHIFT+F6 Rename");
                btnF8.setText("SHIFT+F8 Delete & Wipe");
            }
            case CONTROL -> {
            }

            default -> throw new IllegalStateException("Which key was pressed?: " + whichKeyWasPressed);
        }
    }
}
