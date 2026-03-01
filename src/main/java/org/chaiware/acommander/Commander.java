package org.chaiware.acommander;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Window;
import org.chaiware.acommander.actions.ActionContext;
import org.chaiware.acommander.actions.ActionExecutor;
import org.chaiware.acommander.actions.ActionRegistry;
import org.chaiware.acommander.commands.ACommands;
import org.chaiware.acommander.commands.CommandsAdvancedImpl;
import org.chaiware.acommander.commands.ExternalCommandListener;
import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.helpers.*;
import org.chaiware.acommander.keybinding.KeyBindingManager;
import org.chaiware.acommander.keybinding.KeyBindingManager.KeyContext;
import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.palette.CommandPaletteController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static java.awt.Desktop.getDesktop;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.RIGHT;


public class Commander {
    private static final String LEFT_FOLDER_KEY = "left_folder";
    private static final String RIGHT_FOLDER_KEY = "right_folder";
    private static final String THEME_MODE_KEY = "theme_mode";
    private static final String BOOKMARK_KEY_PREFIX = "bookmark.";
    private static final String THEME_DARK_CLASS = "theme-dark";
    private static final String THEME_LIGHT_CLASS = "theme-light";

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
    Label leftNameHeader, leftSizeHeader, leftModifiedHeader, rightNameHeader, rightSizeHeader, rightModifiedHeader;
    @FXML
    Label leftPaneSummaryLabel, rightPaneSummaryLabel;
    @FXML
    HBox leftHeaderBox, rightHeaderBox;
    @FXML
    Region leftIconHeaderSpacer, rightIconHeaderSpacer;
    @FXML
    Button btnF1, btnF2, btnF3, btnF4, btnF5, btnF6, btnF7, btnF8, btnF9, btnF10, btnF11, btnF12;
    @FXML
    HBox externalProgressBox;
    @FXML
    ProgressBar externalProgressBar;
    @FXML
    Label externalProgressLabel;
    @FXML
    Button externalStopButton;
    @FXML
    private CommandPaletteController commandPaletteController;

    Properties properties = new Properties();
    ACommands commands;
    private AppRegistry appRegistry;
    private ActionExecutor actionExecutor;
    private final Map<String, String> bookmarks = new LinkedHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(Commander.class);
    public FilesPanesHelper filesPanesHelper;
    private final FileAttributesHelper attributesHelper = new FileAttributesHelper();
    private ThemeMode currentThemeMode = ThemeMode.REGULAR;
    private KeyCode bottomButtonModifier;
    private final AtomicInteger runningExternalCommands = new AtomicInteger(0);
    private volatile boolean restoreFileListFocusAfterSettingsEdit = false;
    private final Map<FilesPanesHelper.FocusSide, String> incrementalCharFilters = new EnumMap<>(FilesPanesHelper.FocusSide.class);
    private final Map<FilesPanesHelper.FocusSide, List<FileItem>> incrementalFilterBases = new EnumMap<>(FilesPanesHelper.FocusSide.class);
    private final Map<FilesPanesHelper.FocusSide, Map<String, FolderCompareMark>> folderCompareMarks = new EnumMap<>(FilesPanesHelper.FocusSide.class);
    private Popup incrementalFilterPopup;
    private Label incrementalFilterPopupLabel;


    @FXML
    public void initialize() {
        logger.debug("Loading Properties");
        loadConfigFile();

        // Configure left & right defaults
        filesPanesHelper = new FilesPanesHelper(leftFileList, leftPathComboBox, rightFileList, rightPathComboBox);
        appRegistry = loadAppRegistry();
        actionExecutor = new ActionExecutor(this, appRegistry);
        commands = new CommandsAdvancedImpl(filesPanesHelper, appRegistry);
        configureExternalProgressUi();
        commands.setExternalCommandListener(buildExternalCommandListener());
        configMouseDoubleClick();

        logger.debug("Loading file lists into the double panes file views");
        ComboBoxSetup setup = new ComboBoxSetup();
        setup.setupComboBox(leftPathComboBox);
        setup.setupComboBox(rightPathComboBox);
        filesPanesHelper.setFileListPath(LEFT, resolveInitialPath(LEFT_FOLDER_KEY));
        filesPanesHelper.setFileListPath(RIGHT, resolveInitialPath(RIGHT_FOLDER_KEY));
        folderCompareMarks.put(LEFT, new HashMap<>());
        folderCompareMarks.put(RIGHT, new HashMap<>());
        leftPathComboBox.valueProperty().addListener((observable, oldValue, newValue) -> onPathChanged(LEFT, newValue));
        rightPathComboBox.valueProperty().addListener((observable, oldValue, newValue) -> onPathChanged(RIGHT, newValue));

        configListViewLookAndBehavior(LEFT, leftFileList);
        configListViewLookAndBehavior(RIGHT, rightFileList);
        configSortHeaders();
        configFileListsFocus();
        configurePaneSummary();
        commandPaletteController.configure(new ActionRegistry(appRegistry, actionExecutor), new ActionContext(this));

        updateBottomButtons(null);
        filesPanesHelper.refreshFileListViews();
        updatePaneSummary(LEFT);
        updatePaneSummary(RIGHT);
        filesPanesHelper.getFileList(true).getSelectionModel().selectFirst();
        Platform.runLater(() -> leftFileList.requestFocus());
    }

    private void configureExternalProgressUi() {
        if (externalProgressBar != null) {
            externalProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        }
        if (externalProgressBox != null) {
            externalProgressBox.setVisible(false);
            externalProgressBox.setManaged(false);
        }
        if (externalProgressLabel != null) {
            externalProgressLabel.setText("");
        }
        if (externalStopButton != null) {
            externalStopButton.setDisable(true);
        }
    }

    private ExternalCommandListener buildExternalCommandListener() {
        return new ExternalCommandListener() {
            @Override
            public void onCommandStarted(List<String> command) {
                int active = runningExternalCommands.incrementAndGet();
                String toolName = extractToolName(command);
                Platform.runLater(() -> showExternalProgress(active, toolName));
            }

            @Override
            public void onCommandFinished(List<String> command, int exitCode, Throwable error) {
                int active = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
                boolean finishedSettingsEditor = isSettingsEditCommand(command);
                if (error != null || isUnexpectedNonZeroExit(command, exitCode)) {
                    logger.warn(
                            "External action failed. exitCode={} command={} error={}",
                            exitCode,
                            command == null ? "<null>" : String.join(" ", command),
                            error == null ? "<none>" : error.getMessage()
                    );
                }
                Platform.runLater(() -> {
                    hideOrUpdateExternalProgress(active);
                    if (finishedSettingsEditor && restoreFileListFocusAfterSettingsEdit) {
                        restoreFileListFocusAfterSettingsEdit = false;
                        focusCurrentFileList();
                    }
                });
            }
        };
    }

    private boolean isUnexpectedNonZeroExit(List<String> command, int exitCode) {
        if (exitCode == 0) {
            return false;
        }
        return !(exitCode == 27 && isExamDiffCommand(command));
    }

    private boolean isExamDiffCommand(List<String> command) {
        if (command == null || command.isEmpty() || command.getFirst() == null) {
            return false;
        }
        try {
            Path executable = Paths.get(command.getFirst());
            Path fileName = executable.getFileName();
            return fileName != null && "examdiff.exe".equalsIgnoreCase(fileName.toString());
        } catch (Exception ex) {
            return command.getFirst().toLowerCase(Locale.ROOT).contains("examdiff.exe");
        }
    }

    private void showExternalProgress(int activeCommands, String toolName) {
        if (externalProgressBox == null || externalProgressLabel == null || externalProgressBar == null) {
            return;
        }
        externalProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        externalProgressBox.setVisible(true);
        externalProgressBox.setManaged(true);
        if (activeCommands == 1) {
            externalProgressLabel.setText("Running: " + toolName);
        } else {
            externalProgressLabel.setText("Running " + activeCommands + " external tasks...");
        }
        if (externalStopButton != null) {
            externalStopButton.setDisable(false);
        }
    }

    private void hideOrUpdateExternalProgress(int activeCommands) {
        if (externalProgressBox == null || externalProgressLabel == null) {
            return;
        }
        if (activeCommands <= 0) {
            externalProgressBox.setVisible(false);
            externalProgressBox.setManaged(false);
            externalProgressLabel.setText("");
            if (externalStopButton != null) {
                externalStopButton.setDisable(true);
            }
            return;
        }
        externalProgressLabel.setText("Running " + activeCommands + " external tasks...");
        if (externalStopButton != null) {
            externalStopButton.setDisable(false);
        }
    }

    private String extractToolName(List<String> command) {
        if (command == null || command.isEmpty() || command.getFirst() == null || command.getFirst().isBlank()) {
            return "external command";
        }
        String executable = command.getFirst();
        try {
            Path path = Paths.get(executable);
            Path fileName = path.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        } catch (Exception ignored) {
            // Keep raw value when path parsing fails.
        }
        return executable;
    }

    @FXML
    public void stopExternalTasks() {
        int stopped = commands.stopRunningExternalCommands();
        logger.info("Stop requested for running external tasks. Requested stops: {}", stopped);
        if (externalStopButton != null) {
            externalStopButton.setDisable(true);
        }
    }

    private void configSortHeaders() {
        leftNameHeader.setMaxWidth(Double.MAX_VALUE);
        rightNameHeader.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(leftNameHeader, Priority.ALWAYS);
        HBox.setHgrow(rightNameHeader, Priority.ALWAYS);

        leftSizeHeader.setMinWidth(100);
        leftSizeHeader.setPrefWidth(100);
        leftSizeHeader.setMaxWidth(100);
        leftModifiedHeader.setMinWidth(120);
        leftModifiedHeader.setPrefWidth(120);
        leftModifiedHeader.setMaxWidth(120);
        rightSizeHeader.setMinWidth(100);
        rightSizeHeader.setPrefWidth(100);
        rightSizeHeader.setMaxWidth(100);
        rightModifiedHeader.setMinWidth(120);
        rightModifiedHeader.setPrefWidth(120);
        rightModifiedHeader.setMaxWidth(120);

        leftIconHeaderSpacer.setMinWidth(36);
        leftIconHeaderSpacer.setPrefWidth(36);
        leftIconHeaderSpacer.setMaxWidth(36);
        rightIconHeaderSpacer.setMinWidth(36);
        rightIconHeaderSpacer.setPrefWidth(36);
        rightIconHeaderSpacer.setMaxWidth(36);

        leftFileList.widthProperty().addListener((obs, oldVal, newVal) -> alignHeaderToList(leftHeaderBox, newVal.doubleValue()));
        rightFileList.widthProperty().addListener((obs, oldVal, newVal) -> alignHeaderToList(rightHeaderBox, newVal.doubleValue()));
        alignHeaderToList(leftHeaderBox, leftFileList.getWidth());
        alignHeaderToList(rightHeaderBox, rightFileList.getWidth());

        configureSortableHeader(leftNameHeader, () -> onSortHeaderClicked(LEFT, FilesPanesHelper.SortColumn.NAME));
        configureSortableHeader(leftSizeHeader, () -> onSortHeaderClicked(LEFT, FilesPanesHelper.SortColumn.SIZE));
        configureSortableHeader(leftModifiedHeader, () -> onSortHeaderClicked(LEFT, FilesPanesHelper.SortColumn.MODIFIED));

        configureSortableHeader(rightNameHeader, () -> onSortHeaderClicked(RIGHT, FilesPanesHelper.SortColumn.NAME));
        configureSortableHeader(rightSizeHeader, () -> onSortHeaderClicked(RIGHT, FilesPanesHelper.SortColumn.SIZE));
        configureSortableHeader(rightModifiedHeader, () -> onSortHeaderClicked(RIGHT, FilesPanesHelper.SortColumn.MODIFIED));

        updateSortHeaderTexts(LEFT);
        updateSortHeaderTexts(RIGHT);
    }

    private void configureSortableHeader(Label label, Runnable action) {
        label.setCursor(Cursor.HAND);
        label.setOnMouseClicked(event -> action.run());
    }

    private void alignHeaderToList(HBox headerBox, double listWidth) {
        double contentWidth = Math.max(0, listWidth - 20);
        headerBox.setMinWidth(contentWidth);
        headerBox.setPrefWidth(contentWidth);
        headerBox.setMaxWidth(contentWidth);
    }

    private void onSortHeaderClicked(FilesPanesHelper.FocusSide side, FilesPanesHelper.SortColumn column) {
        filesPanesHelper.toggleSort(side, column);
        updateSortHeaderTexts(side);
    }

    public void sortByName() {
        applySortFromPalette(FilesPanesHelper.SortColumn.NAME);
    }

    public void sortBySize() {
        applySortFromPalette(FilesPanesHelper.SortColumn.SIZE);
    }

    public void sortByDate() {
        applySortFromPalette(FilesPanesHelper.SortColumn.MODIFIED);
    }

    private void applySortFromPalette(FilesPanesHelper.SortColumn column) {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        filesPanesHelper.toggleSort(side, column);
        updateSortHeaderTexts(side);
        requestFocusedFileListFocus();
    }

    private void updateSortHeaderTexts(FilesPanesHelper.FocusSide side) {
        FilesPanesHelper.SortColumn activeColumn = filesPanesHelper.getSortColumn(side);
        boolean ascending = filesPanesHelper.isSortAscending(side);

        Label nameHeader = side == LEFT ? leftNameHeader : rightNameHeader;
        Label sizeHeader = side == LEFT ? leftSizeHeader : rightSizeHeader;
        Label modifiedHeader = side == LEFT ? leftModifiedHeader : rightModifiedHeader;

        nameHeader.setText("Name" + sortIndicator(activeColumn == FilesPanesHelper.SortColumn.NAME, ascending));
        sizeHeader.setText("Size" + sortIndicator(activeColumn == FilesPanesHelper.SortColumn.SIZE, ascending));
        modifiedHeader.setText("Modified" + sortIndicator(activeColumn == FilesPanesHelper.SortColumn.MODIFIED, ascending));
    }

    private String sortIndicator(boolean active, boolean ascending) {
        if (!active) {
            return "";
        }
        return ascending ? " ▲" : " ▼";
    }

    private void onPathChanged(FilesPanesHelper.FocusSide side, Folder newValue) {
        if (newValue == null) {
            return;
        }

        properties.setProperty(side == LEFT ? LEFT_FOLDER_KEY : RIGHT_FOLDER_KEY, newValue.getPath());
        saveConfigFile();
        clearCharFilter(side);
        clearFolderCompareHighlights(false);
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

    public void initializeTheme(Scene scene) {
        applyTheme(scene, ThemeMode.from(properties.getProperty(THEME_MODE_KEY)), false);
    }

    public void setDarkMode() {
        applyTheme(rootPane.getScene(), ThemeMode.DARK, true);
    }

    public void setLightMode() {
        setRegularMode();
    }

    public void setRegularMode() {
        applyTheme(rootPane.getScene(), ThemeMode.REGULAR, true);
    }

    public void toggleDarkMode() {
        if (currentThemeMode == ThemeMode.DARK) {
            setRegularMode();
        } else {
            setDarkMode();
        }
        requestFocusedFileListFocus();
    }

    private void loadConfigFile() {
        Path configFile = getConfigFilePath();
        if (!Files.exists(configFile)) {
            bookmarks.clear();
            return;
        }

        try (FileInputStream input = new FileInputStream(configFile.toFile())) {
            properties.load(input);
            loadBookmarksFromProperties();
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
            syncBookmarksToProperties();
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

        // Save the archive file path (not temp folder) if currently in an archive
        properties.setProperty(LEFT_FOLDER_KEY, getPersistPath(LEFT));
        properties.setProperty(RIGHT_FOLDER_KEY, getPersistPath(RIGHT));
        properties.setProperty(THEME_MODE_KEY, currentThemeMode.configValue);
        saveConfigFile();
    }
    
    /**
     * Gets the path to persist for a pane.
     * If in an archive, returns the archive file's parent folder.
     * Otherwise returns the current path.
     */
    private String getPersistPath(FilesPanesHelper.FocusSide side) {
        ArchiveSession session = filesPanesHelper.getArchiveSession(side);
        if (session != null) {
            // In archive - save the parent folder of the archive file
            File archiveFile = new File(session.getArchivePath());
            File parentFolder = archiveFile.getParentFile();
            if (parentFolder != null) {
                return parentFolder.getAbsolutePath();
            }
            // If no parent, use archive file's directory
            return archiveFile.getParent();
        }
        // Not in archive - use current path
        return filesPanesHelper.getPath(side);
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

    private void configListViewLookAndBehavior(FilesPanesHelper.FocusSide side, ListView<FileItem> listView) {
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
                iconLabel.getStyleClass().add("file-cell-icon");

                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                nameLabel.setMaxWidth(Double.MAX_VALUE);
                nameLabel.setEllipsisString("...");
                nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                nameLabel.getStyleClass().add("file-cell-name");

                sizeLabel.setMinWidth(100);
                sizeLabel.setMaxWidth(100);
                sizeLabel.setAlignment(Pos.CENTER_RIGHT);
                sizeLabel.getStyleClass().add("file-cell-size");

                dateLabel.setMinWidth(120);
                dateLabel.setMaxWidth(120);
                dateLabel.setAlignment(Pos.CENTER_RIGHT);
                dateLabel.getStyleClass().add("file-cell-date");

                hbox.setSpacing(8);
                hbox.getStyleClass().add("file-cell-row");

                listView.widthProperty().addListener((obs, oldVal, newVal) -> {
                    double width = newVal.doubleValue() - 20;
                    hbox.setMaxWidth(width);
                    hbox.setPrefWidth(width);
                });
            }

            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("compare-left-only", "compare-right-only", "compare-different");
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    IconSpec iconSpec = resolveIconSpec(item);
                    iconLabel.setText(iconSpec.glyph());
                    iconLabel.setStyle("-fx-text-fill: " + iconSpec.textColor() + ";");
                    nameLabel.setText(item.getPresentableFilename());
                    sizeLabel.setText(String.format("%s", item.getHumanReadableSize()));
                    dateLabel.setText(item.getDate());

                    // Constrain width to ListView cell
                    double width = listView.getWidth() - 20; // leave margin for scrollbar
                    hbox.setMaxWidth(width);
                    hbox.setPrefWidth(width);

                    applyFolderCompareStyle(side, item, this);
                    setGraphic(hbox);
                }
            }
        });
    }

    private void applyFolderCompareStyle(FilesPanesHelper.FocusSide side, FileItem item, ListCell<FileItem> cell) {
        if (item == null || "..".equals(item.getPresentableFilename())) {
            return;
        }
        Map<String, FolderCompareMark> marks = folderCompareMarks.get(side);
        if (marks == null || marks.isEmpty()) {
            return;
        }
        String key = normalizePathKey(item.getFile().toPath());
        FolderCompareMark mark = marks.get(key);
        if (mark == null) {
            return;
        }
        switch (mark) {
            case LEFT_ONLY -> cell.getStyleClass().add("compare-left-only");
            case RIGHT_ONLY -> cell.getStyleClass().add("compare-right-only");
            case DIFFERENT -> cell.getStyleClass().add("compare-different");
        }
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
        // Use ArchiveMode for comprehensive list of supported archive formats
        return ArchiveMode.isReadWriteExtension(extension) || ArchiveMode.isReadOnlyExtension(extension);
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

    private void configurePaneSummary() {
        bindPaneSummaryUpdates(LEFT);
        bindPaneSummaryUpdates(RIGHT);
    }

    private void bindPaneSummaryUpdates(FilesPanesHelper.FocusSide side) {
        ListView<FileItem> listView = side == LEFT ? leftFileList : rightFileList;
        listView.getItems().addListener((ListChangeListener<FileItem>) change -> updatePaneSummary(side));
        listView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<FileItem>) change -> updatePaneSummary(side));
    }

    private void updatePaneSummary(FilesPanesHelper.FocusSide side) {
        Label summaryLabel = side == LEFT ? leftPaneSummaryLabel : rightPaneSummaryLabel;
        if (summaryLabel == null) {
            return;
        }

        ListView<FileItem> listView = side == LEFT ? leftFileList : rightFileList;
        long totalFilesSize = listView.getItems().stream()
                .filter(item -> item != null && !"..".equals(item.getPresentableFilename()) && !item.isDirectory())
                .mapToLong(item -> item.getFile().length())
                .sum();

        long selectedFilesSize = listView.getSelectionModel().getSelectedItems().stream()
                .filter(item -> item != null && !"..".equals(item.getPresentableFilename()) && !item.isDirectory())
                .mapToLong(item -> item.getFile().length())
                .sum();
        int selectedFileCount = (int) listView.getSelectionModel().getSelectedItems().stream()
                .filter(item -> item != null && !"..".equals(item.getPresentableFilename()) && !item.isDirectory())
                .count();

        int fileCount = (int) listView.getItems().stream()
                .filter(item -> item != null && !"..".equals(item.getPresentableFilename()) && !item.isDirectory())
                .count();

        if (selectedFileCount > 0) {
            summaryLabel.setText("Files: " + fileCount + " | Size: " + humanSize(selectedFilesSize) + " / " + humanSize(totalFilesSize));
        } else {
            summaryLabel.setText("Files: " + fileCount + " | Size: " + humanSize(totalFilesSize));
        }
    }

    /**
     * Runs the command of clicking on an item with the ENTER key (run associated program / goto folder)
     */
    public void enterSelectedItem() {
        logger.debug("User clicked ENTER (or mouse double-click)");
        clearCharFilter();

        FileItem selectedItem = filesPanesHelper.getSelectedItem();
        logger.debug("Running: {}", selectedItem.getName());

        // Handle archive navigation
        FilesPanesHelper.FocusSide focusedSide = filesPanesHelper.getFocusedSide();
        if (filesPanesHelper.isInArchive(focusedSide)) {
            handleArchiveEnter(selectedItem);
            return;
        }

        if ("..".equals(selectedItem.getPresentableFilename())) {
            String currentPath = filesPanesHelper.getFocusedPath();
            File parent = new File(currentPath).getParentFile();
            if (parent != null)
                filesPanesHelper.setFocusedFileListPath(parent.getAbsolutePath());
        } else if (selectedItem.isDirectory()) {
            filesPanesHelper.setFocusedFileListPath(selectedItem.getFullPath());
        } else {
            // It's a file - check if it's an archive we can enter
            String extension = getFileExtension(selectedItem.getName());

            if (ArchiveMode.isReadWriteExtension(extension) || ArchiveMode.isReadOnlyExtension(extension)) {
                // Enter the archive (extract to temp folder)
                int active = runningExternalCommands.incrementAndGet();
                showExternalProgress(active, "VFS: Opening " + selectedItem.getName());
                
                CompletableFuture.runAsync(() -> {
                    filesPanesHelper.enterArchive(focusedSide, selectedItem.getFullPath());
                }).thenRun(() -> {
                    int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
                    Platform.runLater(() -> {
                        hideOrUpdateExternalProgress(remaining);
                        focusCurrentFileList();
                    });
                }).exceptionally(ex -> {
                    logger.error("Failed to enter archive: {}", selectedItem.getName(), ex);
                    int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
                    Platform.runLater(() -> hideOrUpdateExternalProgress(remaining));
                    return null;
                });
            } else if (isExecutableExtension(extension)) {
                try {
                    List<String> command = switch (extension) {
                        case "bat", "cmd" -> List.of("cmd.exe", "/c", selectedItem.getFullPath());
                        case "ps1" -> List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", selectedItem.getFullPath());
                        default -> List.of(selectedItem.getFullPath());
                    };
                    runExternal(command, false).exceptionally(ex -> {
                        logger.error("Failed running executable: {}", selectedItem.getName(), ex);
                        return Collections.emptyList();
                    });
                } catch (Exception ex) {
                    logger.error("Failed running executable: {}", selectedItem.getName(), ex);
                }
            } else {
                // Open with default application
                try {
                    getDesktop().open(selectedItem.getFile());
                } catch (Exception ex) {
                    logger.error("Failed opening: {}", selectedItem.getName(), ex);
                }
            }
        }
    }
    
    /**
     * Handles ENTER key when inside an archive.
     */
    private void handleArchiveEnter(FileItem selectedItem) {
        FilesPanesHelper.FocusSide focusedSide = filesPanesHelper.getFocusedSide();

        if ("..".equals(selectedItem.getPresentableFilename())) {
            // Check if this is an archive parent item
            if (selectedItem instanceof FilesPanesHelper.ArchiveParentItem api) {
                if (api.isArchiveRoot()) {
                    // At archive root - exit archive and show parent folder of archive file
                    exitArchiveAndShowParent(focusedSide, api.getSession());
                } else {
                    // In subdirectory - go up one level in archive
                    int active = runningExternalCommands.incrementAndGet();
                    showExternalProgress(active, "VFS: Navigating up");
                    CompletableFuture.runAsync(() -> {
                        filesPanesHelper.goUpInArchive(focusedSide);
                    }).thenRun(() -> {
                        int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
                        Platform.runLater(() -> {
                            hideOrUpdateExternalProgress(remaining);
                            focusCurrentFileList();
                        });
                    }).exceptionally(ex -> {
                        logger.error("Failed to navigate up in archive", ex);
                        int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
                        Platform.runLater(() -> hideOrUpdateExternalProgress(remaining));
                        return null;
                    });
                }
            } else {
                // Fallback - go up in archive
                int active = runningExternalCommands.incrementAndGet();
                showExternalProgress(active, "VFS: Navigating up");
                CompletableFuture.runAsync(() -> {
                    filesPanesHelper.goUpInArchive(focusedSide);
                }).thenRun(() -> {
                    int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
                    Platform.runLater(() -> {
                        hideOrUpdateExternalProgress(remaining);
                        focusCurrentFileList();
                    });
                }).exceptionally(ex -> {
                    logger.error("Failed to navigate up in archive", ex);
                    int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
                    Platform.runLater(() -> hideOrUpdateExternalProgress(remaining));
                    return null;
                });
            }
        } else if (selectedItem.isDirectory()) {
            // Enter subdirectory in archive
            int active = runningExternalCommands.incrementAndGet();
            showExternalProgress(active, "VFS: Entering " + selectedItem.getName());
            CompletableFuture.runAsync(() -> {
                filesPanesHelper.enterArchiveSubdirectory(focusedSide, selectedItem.getName());
            }).thenRun(() -> {
                int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
                Platform.runLater(() -> {
                    hideOrUpdateExternalProgress(remaining);
                    focusCurrentFileList();
                });
            }).exceptionally(ex -> {
                logger.error("Failed to enter archive subdirectory: {}", selectedItem.getName(), ex);
                int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
                Platform.runLater(() -> hideOrUpdateExternalProgress(remaining));
                return null;
            });
        } else {
            // It's a file - open it with default viewer
            try {
                getDesktop().open(selectedItem.getFile());
            } catch (Exception ex) {
                logger.error("Failed opening file in archive: {}", selectedItem.getName(), ex);
            }
        }
    }
    
    /**
     * Exits the archive and shows the parent folder of the archive file.
     */
    private void exitArchiveAndShowParent(FilesPanesHelper.FocusSide focusedSide, ArchiveSession session) {
        String archivePath = session.getArchivePath();
        
        // Exit the archive (repack if needed, cleanup temp)
        int active = runningExternalCommands.incrementAndGet();
        showExternalProgress(active, "VFS: Closing archive");

        CompletableFuture.runAsync(() -> {
            filesPanesHelper.exitArchive(focusedSide);
        }).thenRun(() -> {
            int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
            Platform.runLater(() -> {
                hideOrUpdateExternalProgress(remaining);
                // Show the parent folder of the archive file
                File archiveFile = new File(archivePath);
                File parentFolder = archiveFile.getParentFile();
                if (parentFolder != null) {
                    filesPanesHelper.setFileListPath(focusedSide, parentFolder.getAbsolutePath());
                    logger.info("Exited archive, showing parent folder: {}", parentFolder.getAbsolutePath());
                }
            });
        }).exceptionally(ex -> {
            logger.error("Failed to exit archive: {}", archivePath, ex);
            int remaining = runningExternalCommands.updateAndGet(current -> Math.max(0, current - 1));
            Platform.runLater(() -> hideOrUpdateExternalProgress(remaining));
            return null;
        });
    }
    
    /**
     * Gets the file extension from a filename.
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        }
        return "";
    }

    @FXML
    public void help() {
        logger.info("Help (F1)");

        try {
            File helpFile = Paths.get(System.getProperty("user.dir"), "config", "f1-help.html").toFile();
            FileItem selectedItem = new FileItem(helpFile, helpFile.getName());
            commands.view(selectedItem);
        } catch (Exception ex) {
            error("Failed Viewing file", ex);
        }
    }

    public void openSettings() {
        logger.info("Open Settings");
        try {
            Path configFile = getConfigFilePath();
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            if (!Files.exists(configFile)) {
                Files.createFile(configFile);
            }
            FileItem selectedItem = new FileItem(configFile.toFile(), configFile.getFileName().toString());
            restoreFileListFocusAfterSettingsEdit = true;
            commands.edit(selectedItem);
        } catch (Exception ex) {
            restoreFileListFocusAfterSettingsEdit = false;
            error("Failed Opening settings", ex);
        }
    }

    private boolean isSettingsEditCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        String settingsPath = getConfigFilePath().toAbsolutePath().normalize().toString();
        for (String arg : command) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            try {
                if (settingsPath.equalsIgnoreCase(Paths.get(arg).toAbsolutePath().normalize().toString())) {
                    return true;
                }
            } catch (Exception ignored) {
                // Some command args are flags, not paths.
            }
        }
        return false;
    }

    private void focusCurrentFileList() {
        if (filesPanesHelper.getFocusedSide() == LEFT) {
            leftFileList.requestFocus();
            return;
        }
        rightFileList.requestFocus();
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
            for (FileItem fileItem : fileItems) {
                if (org.chaiware.acommander.helpers.FileHelper.isTextFile(fileItem)) {
                    commands.edit(fileItem);
                } else {
                    showError("Edit File", "Cannot edit binary file: " + fileItem.getName());
                }
            }
        } catch (Exception ex) {
            error("Failed Editing file", ex);
        }
    }

    @FXML
    public void copyFile() {
        logger.info("Copy (F5)");

        try {
            List<FileItem> selectedItems = new ArrayList<>(commands.filterValidItems(filesPanesHelper.getSelectedItems()));
            if (selectedItems.isEmpty()) {
                return;
            }

            String targetFolder = filesPanesHelper.getUnfocusedPath();
            if (selectedItems.size() > 1 && commands instanceof CommandsAdvancedImpl advancedCommands) {
                advancedCommands.copyBatch(selectedItems, targetFolder);
                for (FileItem selectedItem : selectedItems) {
                    File target = new File(targetFolder, selectedItem.getName());
                    filesPanesHelper.selectFileItem(false, new FileItem(target));
                }
                return;
            }

            for (FileItem selectedItem : selectedItems) {
                targetFolder = filesPanesHelper.getUnfocusedPath();
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
    public void handleF5Button() {
        if (bottomButtonModifier == KeyCode.ALT) {
            convertMediaFile();
            return;
        }
        copyFile();
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
    public void handleF10Button() {
        if (bottomButtonModifier == KeyCode.ALT) {
            findInFiles();
            return;
        }
        search();
    }

    public void findInFiles() {
        logger.info("Find in Files (ALT+F10)");
        Optional<FindInFilesOptions> options = promptFindInFilesOptions();
        if (options.isEmpty()) {
            return;
        }
        runFindInFiles(options.get());
    }

    private Optional<FindInFilesOptions> promptFindInFilesOptions() {
        Dialog<FindInFilesOptions> dialog = new Dialog<>();
        dialog.setTitle("Find in Files");
        dialog.setHeaderText(null);

        ButtonType findType = new ButtonType("Find", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(findType, ButtonType.CANCEL);

        TextField queryField = new TextField();
        queryField.setPromptText("Text to find");

        CheckBox caseInsensitive = new CheckBox("Case Insensitive");
        CheckBox findInSpecificExtension = new CheckBox("Find in specific extention");
        TextField extensionField = new TextField();
        extensionField.setPromptText("Example: java");
        extensionField.setDisable(true);
        findInSpecificExtension.selectedProperty().addListener((obs, oldValue, selected) -> {
            extensionField.setDisable(!selected);
            if (!selected) {
                extensionField.clear();
            }
        });

        CheckBox includeHiddenAndIgnored = new CheckBox("Search including hidden & ignored files");

        VBox content = new VBox(10,
                new Label("Find text in: " + filesPanesHelper.getFocusedPath()),
                queryField,
                caseInsensitive,
                findInSpecificExtension,
                extensionField,
                includeHiddenAndIgnored
        );
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        applyThemeToDialog(dialog);

        Button findButton = (Button) dialog.getDialogPane().lookupButton(findType);
        Runnable validate = () -> {
            String query = queryField.getText() == null ? "" : queryField.getText().trim();
            boolean extensionRequired = findInSpecificExtension.isSelected();
            String ext = extensionField.getText() == null ? "" : extensionField.getText().trim();
            findButton.setDisable(query.isEmpty() || (extensionRequired && ext.isEmpty()));
        };
        queryField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        extensionField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        findInSpecificExtension.selectedProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        dialog.setOnShown(event -> Platform.runLater(queryField::requestFocus));
        dialog.setResultConverter(button -> {
            if (button != findType) {
                return null;
            }
            String query = queryField.getText() == null ? "" : queryField.getText().trim();
            String extension = extensionField.getText() == null ? "" : extensionField.getText().trim();
            return new FindInFilesOptions(
                    query,
                    caseInsensitive.isSelected(),
                    findInSpecificExtension.isSelected(),
                    extension,
                    includeHiddenAndIgnored.isSelected()
            );
        });
        return dialog.showAndWait();
    }

    private void runFindInFiles(FindInFilesOptions options) {
        String sourcePath = filesPanesHelper.getFocusedPath();
        Path rgPath = Paths.get(System.getProperty("user.dir"), "apps", "search_in_files", "rg.exe");
        if (!Files.isRegularFile(rgPath)) {
            showError("Find in Files", "Ripgrep executable was not found at: " + rgPath);
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(rgPath.toString());
        command.add("--files-with-matches");
        command.add("--no-messages");
        command.add("--fixed-strings");
        if (options.caseInsensitive()) {
            command.add("--ignore-case");
        }
        if (options.includeHiddenAndIgnored()) {
            command.add("--hidden");
            command.add("--no-ignore");
        }
        if (options.findInSpecificExtension()) {
            String ext = options.extension().replaceFirst("^\\.+", "");
            command.add("--glob");
            command.add("*." + ext);
        }
        command.add(options.query());
        command.add(sourcePath);

        runExternal(command, false)
                .thenAccept(output -> Platform.runLater(() -> {
                    if (output == null || output.isEmpty()) {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION, "No files found :-(");
                        alert.setHeaderText(null);
                        applyThemeToDialog(alert);
                        alert.showAndWait();
                        return;
                    }

                    List<String> files = output.stream()
                            .map(String::trim)
                            .filter(line -> !line.isEmpty())
                            .map(line -> {
                                Path path = Paths.get(line);
                                if (!path.isAbsolute()) {
                                    path = Paths.get(sourcePath).resolve(path);
                                }
                                return path.normalize().toString();
                            })
                            .distinct()
                            .toList();

                    FileItem selectedFile = showFileResultsDialog(files);
                    if (selectedFile == null) {
                        return;
                    }
                    filesPanesHelper.setFocusedFileListPath(selectedFile.getFile().getParent());
                    filesPanesHelper.selectFileItem(true, selectedFile);
                    requestFocusedFileListFocus();
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> showError("Find in Files", "Failed running ripgrep: " + throwable.getMessage()));
                    return null;
                });
    }

    private FileItem showFileResultsDialog(List<String> files) {
        List<FileItem> fileItems = files.stream().map(filename -> new FileItem(new File(filename))).toList();
        ListView<FileItem> fileList = new ListView<>();
        fileList.getItems().setAll(fileItems);
        fileList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFullPath());
            }
        });
        fileList.getSelectionModel().selectFirst();
        fileList.getFocusModel().focus(0);
        fileList.setPrefSize(980, 420);

        Dialog<FileItem> dialog = new Dialog<>();
        dialog.setTitle("Files Found");
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(fileList);
        ButtonType goToFileButton = new ButtonType("Go to File", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(goToFileButton, ButtonType.CANCEL);
        pane.setPrefSize(1020, 480);
        dialog.setResizable(true);
        dialog.setResultConverter(buttonType -> buttonType == goToFileButton ? fileList.getSelectionModel().getSelectedItem() : null);
        dialog.setOnShown(event -> Platform.runLater(() -> {
            fileList.requestFocus();
            fileList.getSelectionModel().selectFirst();
            fileList.getFocusModel().focus(0);
        }));

        pane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialog.setResult(null);
                dialog.close();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ENTER) {
                dialog.setResult(fileList.getSelectionModel().getSelectedItem());
                dialog.close();
                event.consume();
            }
        });

        applyThemeToDialog(dialog);
        return dialog.showAndWait().orElse(null);
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

    public void splitLargeFile() {
        logger.info("Split Large File (ALT+F11)");
        try {
            List<FileItem> selectedItems = commands.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
            if (selectedItems.size() != 1) {
                showError(
                        "Split a Large File",
                        "Select exactly one file to split. Multiple selection is not supported."
                );
                return;
            }

            FileItem selectedItem = selectedItems.getFirst();
            if (selectedItem.isDirectory()) {
                showError("Split a Large File", "The selected item is a folder. Please select a single file.");
                return;
            }

            long originalFileSize = selectedItem.getFile().length();
            Optional<String> splitArg = promptSplitSize(selectedItem, originalFileSize);
            if (splitArg.isEmpty()) {
                logger.info("User cancelled split");
                return;
            }

            String outputFilename = buildSplitArchiveName(selectedItem.getName());
            String outputArchivePath = filesPanesHelper.getUnfocusedPath() + "\\" + outputFilename;
            String sevenZipPath = Paths.get(
                    System.getProperty("user.dir"),
                    "apps",
                    "extract_all",
                    "UniExtract",
                    "bin",
                    "x64",
                    "7z.exe"
            ).toString();

            List<String> command = List.of(
                    sevenZipPath,
                    "a",
                    outputArchivePath,
                    selectedItem.getFullPath(),
                    "-mx0",
                    "-v" + splitArg.get()
            );

            runExternal(command, true);
            filesPanesHelper.selectFileItem(false, new FileItem(new File(outputArchivePath)));
        } catch (Exception ex) {
            error("Failed splitting large file", ex);
        }
    }

    public void convertGraphicsFiles() {
        logger.info("Convert Graphics Files");
        List<FileItem> selectedItems = commands.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (!ImageConversionSupport.areAllConvertibleImages(selectedItems)) {
            showError("Convert Graphics Files", "Select one or more image files only.");
            requestFocusedFileListFocus();
            return;
        }

        Optional<ImageConversionRequest> request = promptImageConversionOptions(selectedItems);
        if (request.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }

        Path caesiumPath = Paths.get(System.getProperty("user.dir"), "apps", "image_convert", "caesiumclt.exe");
        if (!Files.isRegularFile(caesiumPath)) {
            showError("Convert Graphics Files", "caesiumclt executable was not found at: " + caesiumPath);
            requestFocusedFileListFocus();
            return;
        }

        String outputFolder = filesPanesHelper.getUnfocusedPath();
        ImageConversionRequest options = request.get();
        List<String> command = buildImageConvertCommand(caesiumPath, outputFolder, selectedItems, options);
        runExternal(command, true)
                .thenAccept(output -> Platform.runLater(() -> {
                    focusConvertedFileInOtherPane(selectedItems, outputFolder, options);
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> {
                        showError("Convert Graphics Files", "Image conversion failed: " + throwable.getMessage());
                        requestFocusedFileListFocus();
                    });
                    return null;
                });
    }

    private Optional<ImageConversionRequest> promptImageConversionOptions(List<FileItem> selectedItems) {
        List<String> targetFormats = ImageConversionSupport.targetFormatsForSelection(selectedItems);
        if (targetFormats.isEmpty()) {
            showError("Convert Graphics Files", "No supported target formats were found for the selected files.");
            return Optional.empty();
        }

        Dialog<ImageConversionRequest> dialog = new Dialog<>();
        dialog.setTitle("Convert Graphics Files");
        dialog.setHeaderText(null);

        ButtonType convertType = new ButtonType("Convert", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(convertType, ButtonType.CANCEL);

        Label title = new Label("Convert Graphics Files");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitle = new Label("Selected files: " + selectedItems.size() + " | Output folder: " + filesPanesHelper.getUnfocusedPath());

        ToggleGroup formatGroup = new ToggleGroup();
        VBox formatsBox = new VBox(8);
        for (String format : targetFormats) {
            RadioButton formatRadio = new RadioButton(format.toUpperCase(Locale.ROOT));
            formatRadio.setUserData(format);
            formatRadio.setToggleGroup(formatGroup);
            formatsBox.getChildren().add(formatRadio);
        }
        if (formatGroup.getToggles().getFirst() instanceof RadioButton first) {
            first.setSelected(true);
        }

        ToggleGroup compressionModeGroup = new ToggleGroup();
        RadioButton losslessMode = new RadioButton("Lossless");
        RadioButton qualityMode = new RadioButton("Lossy");
        RadioButton targetSizeMode = new RadioButton("Target max output size");
        losslessMode.setUserData(ImageCompressionMode.LOSSLESS);
        qualityMode.setUserData(ImageCompressionMode.QUALITY);
        targetSizeMode.setUserData(ImageCompressionMode.MAX_SIZE);
        losslessMode.setToggleGroup(compressionModeGroup);
        qualityMode.setToggleGroup(compressionModeGroup);
        targetSizeMode.setToggleGroup(compressionModeGroup);
        losslessMode.setSelected(true);

        Slider qualitySlider = new Slider(0, 100, 80);
        qualitySlider.setShowTickLabels(true);
        qualitySlider.setShowTickMarks(true);
        qualitySlider.setMajorTickUnit(20);
        qualitySlider.setMinorTickCount(4);
        qualitySlider.setBlockIncrement(1);
        Label qualityValue = new Label("80");
        qualitySlider.valueProperty().addListener((obs, oldValue, newValue) -> qualityValue.setText(String.valueOf(newValue.intValue())));
        HBox qualityRow = new HBox(10, new Label("Quality:"), qualitySlider, qualityValue);
        HBox.setHgrow(qualitySlider, Priority.ALWAYS);

        TextField maxSizeField = new TextField();
        maxSizeField.setPromptText("Examples: 150KB, 1MB, 0.5MB");
        maxSizeField.setDisable(true);

        CheckBox keepExif = new CheckBox("Keep EXIF metadata");
        CheckBox keepDates = new CheckBox("Keep original file dates");

        ComboBox<ImageResizeMode> resizeMode = new ComboBox<>();
        resizeMode.getItems().addAll(ImageResizeMode.NONE, ImageResizeMode.WIDTH, ImageResizeMode.HEIGHT, ImageResizeMode.LONG_EDGE, ImageResizeMode.SHORT_EDGE);
        resizeMode.getSelectionModel().select(ImageResizeMode.NONE);
        TextField resizeValueField = new TextField();
        resizeValueField.setPromptText("Pixels");
        resizeValueField.setDisable(true);
        CheckBox noUpscale = new CheckBox("Do not upscale resized images");
        noUpscale.setDisable(true);

        TextField suffixField = new TextField("_converted");
        suffixField.setPromptText("Filename suffix");

        ComboBox<String> overwritePolicy = new ComboBox<>();
        overwritePolicy.getItems().addAll("Always", "Never", "Bigger");
        overwritePolicy.getSelectionModel().select("Always");

        Runnable syncByMode = () -> {
            ImageCompressionMode mode = selectedCompressionMode(compressionModeGroup);
            maxSizeField.setDisable(mode != ImageCompressionMode.MAX_SIZE);
            qualitySlider.setDisable(mode != ImageCompressionMode.QUALITY);
        };
        Runnable syncByResizeMode = () -> {
            boolean resizeEnabled = resizeMode.getSelectionModel().getSelectedItem() != ImageResizeMode.NONE;
            resizeValueField.setDisable(!resizeEnabled);
            noUpscale.setDisable(!resizeEnabled);
        };
        compressionModeGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> syncByMode.run());
        resizeMode.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> syncByResizeMode.run());
        syncByMode.run();
        syncByResizeMode.run();

        Label validationLabel = new Label();
        Button convertButton = (Button) dialog.getDialogPane().lookupButton(convertType);
        convertButton.setDefaultButton(true);
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !convertButton.isDisabled()) {
                convertButton.fire();
                event.consume();
            }
        });
        Runnable validate = () -> {
            ImageCompressionMode mode = selectedCompressionMode(compressionModeGroup);
            if (mode == ImageCompressionMode.MAX_SIZE && (maxSizeField.getText() == null || maxSizeField.getText().trim().isEmpty())) {
                validationLabel.setText("Max size is required for target-size mode.");
                convertButton.setDisable(true);
                return;
            }
            ImageResizeMode resize = resizeMode.getSelectionModel().getSelectedItem();
            if (resize != null && resize != ImageResizeMode.NONE) {
                Integer resizeValue = parsePositiveOrNull(resizeValueField.getText());
                if (resizeValue == null) {
                    validationLabel.setText("Resize value must be a positive number.");
                    convertButton.setDisable(true);
                    return;
                }
            }
            validationLabel.setText("");
            convertButton.setDisable(formatGroup.getSelectedToggle() == null);
        };
        maxSizeField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        resizeValueField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        formatGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> validate.run());
        compressionModeGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> validate.run());
        resizeMode.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        VBox content = new VBox(
                10,
                title,
                subtitle,
                new Separator(),
                new Label("Convert to format:"),
                formatsBox,
                new Separator(),
                new Label("Compression mode:"),
                losslessMode,
                qualityMode,
                qualityRow,
                targetSizeMode,
                maxSizeField,
                new Separator(),
                new Label("Resize (optional):"),
                new HBox(10, new Label("Mode:"), resizeMode),
                new HBox(10, new Label("Value:"), resizeValueField),
                noUpscale,
                new Separator(),
                new Label("Options:"),
                keepExif,
                keepDates,
                new Label("Filename suffix:"),
                suffixField,
                new Label("Overwrite policy:"),
                overwritePolicy,
                validationLabel
        );
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(620, 760);
        applyThemeToDialog(dialog);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != convertType || formatGroup.getSelectedToggle() == null) {
                return null;
            }
            String targetFormat = String.valueOf(formatGroup.getSelectedToggle().getUserData());
            ImageCompressionMode compressionMode = selectedCompressionMode(compressionModeGroup);
            Integer quality = compressionMode == ImageCompressionMode.QUALITY ? (int) Math.round(qualitySlider.getValue()) : null;
            String maxSize = compressionMode == ImageCompressionMode.MAX_SIZE ? maxSizeField.getText().trim() : null;
            ImageResizeMode resize = resizeMode.getSelectionModel().getSelectedItem();
            Integer resizeValue = resize == null || resize == ImageResizeMode.NONE
                    ? null
                    : parsePositiveOrNull(resizeValueField.getText());
            return new ImageConversionRequest(
                    targetFormat,
                    compressionMode,
                    quality,
                    maxSize,
                    keepExif.isSelected(),
                    keepDates.isSelected(),
                    resize == null ? ImageResizeMode.NONE : resize,
                    resizeValue,
                    noUpscale.isSelected(),
                    suffixField.getText() == null ? "" : suffixField.getText().trim(),
                    overwritePolicy.getSelectionModel().getSelectedItem()
                );
        });

        return dialog.showAndWait();
    }

    private ImageCompressionMode selectedCompressionMode(ToggleGroup group) {
        if (group == null || group.getSelectedToggle() == null || group.getSelectedToggle().getUserData() == null) {
            return ImageCompressionMode.QUALITY;
        }
        return (ImageCompressionMode) group.getSelectedToggle().getUserData();
    }

    private Integer parsePositiveOrNull(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> buildImageConvertCommand(
            Path caesiumPath,
            String outputFolder,
            List<FileItem> selectedItems,
            ImageConversionRequest options
    ) {
        List<String> command = new ArrayList<>();
        command.add(caesiumPath.toString());

        switch (options.compressionMode()) {
            case QUALITY -> {
                command.add("--quality");
                command.add(String.valueOf(options.quality() == null ? 80 : options.quality()));
            }
            case LOSSLESS -> command.add("--lossless");
            case MAX_SIZE -> {
                command.add("--max-size");
                command.add(options.maxSize());
            }
        }

        command.add("--output");
        command.add(outputFolder);
        command.add("--format");
        command.add(options.targetFormat());

        if (options.keepExif()) {
            command.add("--exif");
        }
        if (options.keepDates()) {
            command.add("--keep-dates");
        }
        switch (options.resizeMode()) {
            case WIDTH -> {
                command.add("--width");
                command.add(String.valueOf(options.resizeValue()));
            }
            case HEIGHT -> {
                command.add("--height");
                command.add(String.valueOf(options.resizeValue()));
            }
            case LONG_EDGE -> {
                command.add("--long-edge");
                command.add(String.valueOf(options.resizeValue()));
            }
            case SHORT_EDGE -> {
                command.add("--short-edge");
                command.add(String.valueOf(options.resizeValue()));
            }
            case NONE -> {
            }
        }
        if (options.resizeMode() != ImageResizeMode.NONE && options.noUpscale()) {
            command.add("--no-upscale");
        }
        if ("png".equals(options.targetFormat())) {
            command.add("--png-opt-level");
            command.add("3");
            command.add("--zopfli");
        }
        if (options.suffix() != null && !options.suffix().isBlank()) {
            command.add("--suffix");
            command.add(options.suffix());
        }

        command.add("--overwrite");
        command.add(mapCaesiumOverwritePolicy(options.overwritePolicy()));

        for (FileItem item : selectedItems) {
            command.add(item.getFullPath());
        }
        return command;
    }

    private String mapCaesiumOverwritePolicy(String label) {
        if (label == null) {
            return "all";
        }
        return switch (label.trim().toLowerCase(Locale.ROOT)) {
            case "never" -> "never";
            case "bigger" -> "bigger";
            default -> "all";
        };
    }

    private void focusConvertedFileInOtherPane(
            List<FileItem> selectedItems,
            String outputFolder,
            ImageConversionRequest options
    ) {
        FileItem firstFound = findFirstConvertedItem(selectedItems, outputFolder, options);
        if (firstFound != null) {
            filesPanesHelper.selectFileItem(false, firstFound);
        }
        requestUnfocusedFileListFocus();
    }

    private FileItem findFirstConvertedItem(
            List<FileItem> selectedItems,
            String outputFolder,
            ImageConversionRequest options
    ) {
        for (FileItem source : selectedItems) {
            for (String outputName : buildCandidateOutputNames(source, options)) {
                Path candidate = Paths.get(outputFolder, outputName);
                if (Files.exists(candidate)) {
                    return new FileItem(candidate.toFile());
                }
            }
        }
        return null;
    }

    private List<String> buildCandidateOutputNames(FileItem source, ImageConversionRequest options) {
        String baseName = source.getName();
        int dotIndex = baseName.lastIndexOf('.');
        String stem = dotIndex > 0 ? baseName.substring(0, dotIndex) : baseName;
        String suffix = options.suffix() == null ? "" : options.suffix();
        String normalizedFormat = options.targetFormat().toLowerCase(Locale.ROOT);

        List<String> extensions = switch (normalizedFormat) {
            case "jpeg" -> List.of("jpeg", "jpg");
            case "tiff" -> List.of("tiff", "tif");
            default -> List.of(normalizedFormat);
        };
        return extensions.stream()
                .map(ext -> stem + suffix + "." + ext)
                .toList();
    }

    public void convertMediaFile() {
        logger.info("Convert Media File");
        List<FileItem> selectedItems = commands.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (ImageConversionSupport.areAllConvertibleImages(selectedItems)) {
            convertGraphicsFiles();
            return;
        }
        if (AudioConversionSupport.areAllConvertibleAudio(selectedItems)) {
            convertAudioFiles();
            return;
        }
        showError("Convert Media File", "Select one or more image files or one or more audio files only.");
        requestFocusedFileListFocus();
    }

    public void convertAudioFiles() {
        logger.info("Convert Audio Files");
        List<FileItem> selectedItems = commands.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (!AudioConversionSupport.areAllConvertibleAudio(selectedItems)) {
            showError("Convert Audio Files", "Select one or more audio files only.");
            requestFocusedFileListFocus();
            return;
        }

        Optional<AudioConversionRequest> request = promptAudioConversionOptions(selectedItems);
        if (request.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }

        Path converterPath = Paths.get(System.getProperty("user.dir"), "apps", "sound_convert", "sndfile-convert.exe");
        if (!Files.isRegularFile(converterPath)) {
            showError("Convert Audio Files", "sndfile-convert executable was not found at: " + converterPath);
            requestFocusedFileListFocus();
            return;
        }

        String outputFolder = filesPanesHelper.getUnfocusedPath();
        AudioConversionRequest options = request.get();
        Path outputFolderPath = Paths.get(outputFolder);
        final Path[] firstConverted = {null};

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (FileItem source : selectedItems) {
            chain = chain.thenCompose(ignored -> {
                Path outputPath = buildAudioOutputPath(outputFolderPath, source, options);
                try {
                    outputPath = resolveAudioOutputCollision(outputPath, options.conflictPolicy());
                } catch (IOException ioException) {
                    return CompletableFuture.failedFuture(ioException);
                }
                if (outputPath == null) {
                    return CompletableFuture.completedFuture(null);
                }
                List<String> command = buildAudioConvertCommand(converterPath, source.getFullPath(), outputPath, options);
                Path finalOutputPath = outputPath;
                return runExternal(command, false).thenAccept(lines -> {
                    if (firstConverted[0] == null) {
                        firstConverted[0] = finalOutputPath;
                    }
                });
            });
        }

        chain.thenAccept(ignored -> Platform.runLater(() -> {
                    filesPanesHelper.refreshFileListViews();
                    if (firstConverted[0] != null) {
                        filesPanesHelper.selectFileItem(false, new FileItem(firstConverted[0].toFile()));
                    }
                    requestUnfocusedFileListFocus();
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> {
                        showError("Convert Audio Files", "Audio conversion failed: " + throwable.getMessage());
                        requestFocusedFileListFocus();
                    });
                    return null;
                });
    }

    private Optional<AudioConversionRequest> promptAudioConversionOptions(List<FileItem> selectedItems) {
        List<String> targetFormats = AudioConversionSupport.targetFormatsForSelection(selectedItems);
        if (targetFormats.isEmpty()) {
            showError("Convert Audio Files", "No supported target formats were found for the selected files.");
            return Optional.empty();
        }

        Dialog<AudioConversionRequest> dialog = new Dialog<>();
        dialog.setTitle("Convert Audio Files");
        dialog.setHeaderText(null);

        ButtonType convertType = new ButtonType("Convert", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(convertType, ButtonType.CANCEL);

        Label title = new Label("Convert Audio Files");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitle = new Label("Selected files: " + selectedItems.size() + " | Output folder: " + filesPanesHelper.getUnfocusedPath());

        ToggleGroup formatGroup = new ToggleGroup();
        VBox formatsBox = new VBox(8);
        for (String format : targetFormats) {
            RadioButton formatRadio = new RadioButton(format.toUpperCase(Locale.ROOT));
            formatRadio.setUserData(format);
            formatRadio.setToggleGroup(formatGroup);
            formatsBox.getChildren().add(formatRadio);
        }
        if (formatGroup.getToggles().getFirst() instanceof RadioButton first) {
            first.setSelected(true);
        }

        ToggleGroup profileGroup = new ToggleGroup();
        RadioButton lossless = new RadioButton("Lossless");
        RadioButton lossy = new RadioButton("Lossy");
        RadioButton custom = new RadioButton("Custom encoding");
        lossless.setUserData(AudioCompressionProfile.LOSSLESS);
        lossy.setUserData(AudioCompressionProfile.LOSSY);
        custom.setUserData(AudioCompressionProfile.CUSTOM);
        lossless.setToggleGroup(profileGroup);
        lossy.setToggleGroup(profileGroup);
        custom.setToggleGroup(profileGroup);
        lossless.setSelected(true);

        ComboBox<String> encodingCombo = new ComboBox<>();
        encodingCombo.setPrefWidth(260);
        Map<String, String> encodingChoices = new LinkedHashMap<>();

        CheckBox normalize = new CheckBox("Normalize output audio");
        CheckBox overrideSampleRate = new CheckBox("Override sample rate (Hz)");
        TextField sampleRateField = new TextField("44100");
        sampleRateField.setDisable(true);

        ComboBox<String> endianCombo = new ComboBox<>();
        endianCombo.getItems().addAll("Auto", "CPU", "Little", "Big");
        endianCombo.getSelectionModel().select("Auto");

        TextField suffixField = new TextField("_converted");
        suffixField.setPromptText("Filename suffix");

        ComboBox<String> conflictPolicy = new ComboBox<>();
        conflictPolicy.getItems().addAll("Overwrite", "Skip", "Auto-rename");
        conflictPolicy.getSelectionModel().select("Overwrite");

        Runnable syncEncodingChoices = () -> {
            String targetFormat = formatGroup.getSelectedToggle() == null
                    ? null
                    : String.valueOf(formatGroup.getSelectedToggle().getUserData());
            AudioCompressionProfile profile = selectedAudioCompressionProfile(profileGroup);
            encodingChoices.clear();
            encodingChoices.putAll(audioEncodingOptionsFor(targetFormat, profile));
            encodingCombo.getItems().setAll(encodingChoices.keySet());
            if (!encodingCombo.getItems().isEmpty()) {
                encodingCombo.getSelectionModel().selectFirst();
            }
            encodingCombo.setDisable(profile != AudioCompressionProfile.CUSTOM);
        };

        overrideSampleRate.selectedProperty().addListener((obs, oldValue, newValue) -> sampleRateField.setDisable(!newValue));
        formatGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> syncEncodingChoices.run());
        profileGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> syncEncodingChoices.run());
        syncEncodingChoices.run();

        Button convertButton = (Button) dialog.getDialogPane().lookupButton(convertType);
        convertButton.setDefaultButton(true);
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !convertButton.isDisabled()) {
                convertButton.fire();
                event.consume();
            }
        });
        Label validationLabel = new Label();
        Runnable validate = () -> {
            if (formatGroup.getSelectedToggle() == null) {
                convertButton.setDisable(true);
                validationLabel.setText("Choose a target format.");
                return;
            }
            Integer sampleRate = overrideSampleRate.isSelected() ? parsePositiveOrNull(sampleRateField.getText()) : null;
            if (overrideSampleRate.isSelected() && sampleRate == null) {
                convertButton.setDisable(true);
                validationLabel.setText("Sample rate must be a positive number.");
                return;
            }
            if (encodingCombo.getSelectionModel().getSelectedItem() == null) {
                convertButton.setDisable(true);
                validationLabel.setText("Choose an encoding option.");
                return;
            }
            String targetFormat = String.valueOf(formatGroup.getSelectedToggle().getUserData());
            String selectedEncodingLabel = encodingCombo.getSelectionModel().getSelectedItem();
            String selectedEncodingFlag = selectedEncodingLabel == null ? "" : encodingChoices.getOrDefault(selectedEncodingLabel, "");
            if (sampleRate != null && isOpusOutput(targetFormat, selectedEncodingFlag) && !isValidOpusSampleRate(sampleRate)) {
                convertButton.setDisable(true);
                validationLabel.setText("Opus sample rate must be one of: 8000, 12000, 16000, 24000, 48000.");
                return;
            }
            convertButton.setDisable(false);
            validationLabel.setText("");
        };
        sampleRateField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        formatGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> validate.run());
        profileGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> validate.run());
        encodingCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        VBox content = new VBox(
                10,
                title,
                subtitle,
                new Separator(),
                new Label("Convert to format:"),
                formatsBox,
                new Separator(),
                new Label("Compression profile:"),
                lossless,
                lossy,
                custom,
                new HBox(10, new Label("Encoding:"), encodingCombo),
                new Separator(),
                new Label("Options:"),
                normalize,
                new HBox(10, overrideSampleRate, sampleRateField),
                new HBox(10, new Label("Endian:"), endianCombo),
                new Label("Filename suffix:"),
                suffixField,
                new Label("If output file exists:"),
                conflictPolicy,
                validationLabel
        );
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(620, 680);
        applyThemeToDialog(dialog);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != convertType || formatGroup.getSelectedToggle() == null) {
                return null;
            }
            String selectedEncodingLabel = encodingCombo.getSelectionModel().getSelectedItem();
            String targetFormat = String.valueOf(formatGroup.getSelectedToggle().getUserData());
            Integer sampleRate = overrideSampleRate.isSelected() ? parsePositiveOrNull(sampleRateField.getText()) : null;
            return new AudioConversionRequest(
                    targetFormat,
                    selectedAudioCompressionProfile(profileGroup),
                    selectedEncodingLabel == null ? "" : encodingChoices.getOrDefault(selectedEncodingLabel, ""),
                    normalize.isSelected(),
                    sampleRate,
                    endianCombo.getSelectionModel().getSelectedItem(),
                    suffixField.getText() == null ? "" : suffixField.getText().trim(),
                    conflictPolicy.getSelectionModel().getSelectedItem()
            );
        });

        return dialog.showAndWait();
    }

    private AudioCompressionProfile selectedAudioCompressionProfile(ToggleGroup group) {
        if (group == null || group.getSelectedToggle() == null || group.getSelectedToggle().getUserData() == null) {
            return AudioCompressionProfile.LOSSLESS;
        }
        return (AudioCompressionProfile) group.getSelectedToggle().getUserData();
    }

    private Map<String, String> audioEncodingOptionsFor(String targetFormat, AudioCompressionProfile profile) {
        String fmt = targetFormat == null ? "" : targetFormat.toLowerCase(Locale.ROOT);
        Map<String, String> options = new LinkedHashMap<>();
        switch (profile) {
            case LOSSLESS -> {
                switch (fmt) {
                    case "wav", "aif", "au", "rf64", "w64", "raw", "flac" -> {
                        options.put("16-bit PCM", "-pcm16");
                        options.put("24-bit PCM", "-pcm24");
                        if (!"flac".equals(fmt)) {
                            options.put("32-bit PCM", "-pcm32");
                            options.put("32-bit Float", "-float32");
                        }
                    }
                    case "caf" -> {
                        options.put("ALAC 16-bit", "-alac16");
                        options.put("ALAC 24-bit", "-alac24");
                        options.put("PCM 24-bit", "-pcm24");
                    }
                    default -> options.put("Auto (source/default)", "");
                }
            }
            case LOSSY -> {
                switch (fmt) {
                    case "ogg", "oga" -> {
                        options.put("Vorbis", "-vorbis");
                        options.put("Opus", "-opus");
                    }
                    case "opus" -> options.put("Opus", "-opus");
                    case "wav" -> {
                        options.put("IMA ADPCM", "-ima-adpcm");
                        options.put("MS ADPCM", "-ms-adpcm");
                        options.put("GSM 6.10", "-gsm610");
                    }
                    case "mp3" -> options.put("MP3 (container default)", "");
                    default -> options.put("Auto (source/default)", "");
                }
            }
            case CUSTOM -> {
                options.put("Auto (source/default)", "");
                options.put("16-bit PCM", "-pcm16");
                options.put("24-bit PCM", "-pcm24");
                options.put("32-bit PCM", "-pcm32");
                options.put("32-bit Float", "-float32");
                options.put("64-bit Float", "-float64");
                options.put("uLaw", "-ulaw");
                options.put("aLaw", "-alaw");
                if ("flac".equals(fmt)) {
                    options.put("FLAC-safe PCM 16-bit", "-pcm16");
                    options.put("FLAC-safe PCM 24-bit", "-pcm24");
                }
                if ("caf".equals(fmt)) {
                    options.put("ALAC 16-bit (CAF)", "-alac16");
                    options.put("ALAC 24-bit (CAF)", "-alac24");
                }
                if ("wav".equals(fmt)) {
                    options.put("IMA ADPCM (WAV)", "-ima-adpcm");
                    options.put("MS ADPCM (WAV)", "-ms-adpcm");
                    options.put("GSM 6.10 (WAV)", "-gsm610");
                }
                if ("ogg".equals(fmt) || "oga".equals(fmt) || "opus".equals(fmt)) {
                    options.put("Vorbis (OGG)", "-vorbis");
                    options.put("Opus (OGG)", "-opus");
                }
            }
        }
        if (options.isEmpty()) {
            options.put("Auto (source/default)", "");
        }
        return options;
    }

    private List<String> buildAudioConvertCommand(
            Path converterPath,
            String inputPath,
            Path outputPath,
            AudioConversionRequest options
    ) {
        List<String> command = new ArrayList<>();
        command.add(converterPath.toString());

        String encodingFlag = options.encodingFlag();
        if (encodingFlag == null || encodingFlag.isBlank()) {
            encodingFlag = defaultEncodingForTargetFormat(options.targetFormat());
        }

        boolean opusOutput = isOpusOutput(options.targetFormat(), encodingFlag);
        Integer effectiveSampleRate = normalizeSampleRateForOutput(options.sampleRateOverride(), opusOutput);
        if (effectiveSampleRate != null) {
            command.add("-override-sample-rate=" + effectiveSampleRate);
        }

        if (options.endian() != null && isEndianAllowedForTargetFormat(options.targetFormat())) {
            String endian = options.endian().trim().toLowerCase(Locale.ROOT);
            if (endian.equals("little") || endian.equals("big") || endian.equals("cpu")) {
                command.add("-endian=" + endian);
            }
        }
        if (options.normalize()) {
            command.add("-normalize");
        }
        if (encodingFlag != null && !encodingFlag.isBlank()) {
            command.add(encodingFlag);
        }

        command.add(inputPath);
        command.add(outputPath.toString());
        return command;
    }

    private String defaultEncodingForTargetFormat(String targetFormat) {
        String fmt = targetFormat == null ? "" : targetFormat.toLowerCase(Locale.ROOT);
        return switch (fmt) {
            case "flac", "wav", "aif", "au", "rf64", "w64", "raw", "caf" -> "-pcm16";
            case "ogg", "oga" -> "-vorbis";
            case "opus" -> "-opus";
            default -> "";
        };
    }

    private Integer normalizeSampleRateForOutput(Integer requestedSampleRate, boolean opusOutput) {
        if (!opusOutput) {
            return requestedSampleRate;
        }
        if (requestedSampleRate == null) {
            return 48000;
        }
        if (isValidOpusSampleRate(requestedSampleRate)) {
            return requestedSampleRate;
        }
        int normalized = nearestSupportedOpusSampleRate(requestedSampleRate);
        logger.warn("Adjusted unsupported Opus sample rate {} to {}", requestedSampleRate, normalized);
        return normalized;
    }

    private int nearestSupportedOpusSampleRate(int requested) {
        int[] supported = {8000, 12000, 16000, 24000, 48000};
        int nearest = supported[0];
        int bestDistance = Math.abs(requested - nearest);
        for (int candidate : supported) {
            int distance = Math.abs(requested - candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private boolean isValidOpusSampleRate(int sampleRate) {
        return sampleRate == 8000
                || sampleRate == 12000
                || sampleRate == 16000
                || sampleRate == 24000
                || sampleRate == 48000;
    }

    private boolean isOpusOutput(String targetFormat, String encodingFlag) {
        String fmt = targetFormat == null ? "" : targetFormat.toLowerCase(Locale.ROOT);
        String codec = encodingFlag == null ? "" : encodingFlag.trim().toLowerCase(Locale.ROOT);
        return "opus".equals(fmt) || "-opus".equals(codec);
    }

    private boolean isEndianAllowedForTargetFormat(String targetFormat) {
        String fmt = targetFormat == null ? "" : targetFormat.toLowerCase(Locale.ROOT);
        return switch (fmt) {
            case "flac", "ogg", "oga", "opus", "mp3" -> false;
            default -> true;
        };
    }

    private Path buildAudioOutputPath(Path outputFolder, FileItem source, AudioConversionRequest options) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String suffix = options.suffix() == null ? "" : options.suffix();
        return outputFolder.resolve(stem + suffix + "." + options.targetFormat().toLowerCase(Locale.ROOT));
    }

    private Path resolveAudioOutputCollision(Path outputPath, String conflictPolicy) throws IOException {
        if (!Files.exists(outputPath)) {
            return outputPath;
        }
        String policy = conflictPolicy == null ? "overwrite" : conflictPolicy.trim().toLowerCase(Locale.ROOT);
        if ("skip".equals(policy)) {
            return null;
        }
        if ("auto-rename".equals(policy)) {
            return nextAvailableFileName(outputPath);
        }
        Files.delete(outputPath);
        return outputPath;
    }

    private Path nextAvailableFileName(Path originalPath) {
        Path parent = originalPath.getParent();
        String filename = originalPath.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        String ext = dot > 0 ? filename.substring(dot) : "";

        int counter = 1;
        Path candidate = originalPath;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(stem + " (" + counter + ")" + ext);
            counter++;
        }
        return candidate;
    }

    public void checksumFile() {
        logger.info("Checksum File");
        List<FileItem> selectedItems = commands.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (selectedItems.size() != 1) {
            showError("Checksum File", "Select exactly one file.");
            requestFocusedFileListFocus();
            return;
        }

        FileItem selectedItem = selectedItems.getFirst();
        if (selectedItem.isDirectory()) {
            showError("Checksum File", "The selected item is a folder. Please select a single file.");
            requestFocusedFileListFocus();
            return;
        }

        Optional<ChecksumOptions> options = promptChecksumOptions("Checksum File", selectedItem.getName(), false);
        if (options.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }

        Path rhashPath = Paths.get(System.getProperty("user.dir"), "apps", "checksum", "rhash.exe");
        if (!Files.exists(rhashPath)) {
            showError("Checksum File", "rhash executable was not found at: " + rhashPath);
            requestFocusedFileListFocus();
            return;
        }

        List<String> command = buildChecksumCommand(rhashPath, selectedItem.getFullPath(), options.get(), false);
        runExternal(command, false)
                .thenAccept(output -> Platform.runLater(() -> {
                    String checksumValue = options.get().includeFileNames()
                            ? String.join(System.lineSeparator(), output)
                            : extractDigestValue(output);
                    if (checksumValue == null || checksumValue.isBlank()) {
                        showError("Checksum File", "No checksum value was returned by rhash.");
                    } else {
                        showChecksumResultDialog(
                                "Checksum File",
                                selectedItem.getName(),
                                options.get().algorithmLabel(),
                                checksumValue,
                                selectedItem.getFile().getParentFile().toPath(),
                                false
                        );
                    }
                    requestFocusedFileListFocus();
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> {
                        showError("Checksum File", "Failed running rhash: " + throwable.getMessage());
                        requestFocusedFileListFocus();
                    });
                    return null;
                });
    }

    public void checksumFolderContents() {
        logger.info("Checksum Folder Contents");
        List<FileItem> selectedItems = commands.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (selectedItems.size() != 1) {
            showError("Checksum Folder Contents", "Select exactly one folder.");
            requestFocusedFileListFocus();
            return;
        }

        FileItem selectedItem = selectedItems.getFirst();
        if (!selectedItem.isDirectory()) {
            showError("Checksum Folder Contents", "The selected item is a file. Please select a single folder.");
            requestFocusedFileListFocus();
            return;
        }

        Optional<ChecksumOptions> options = promptChecksumOptions("Checksum Folder Contents", selectedItem.getName(), true);
        if (options.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }

        Path rhashPath = Paths.get(System.getProperty("user.dir"), "apps", "checksum", "rhash.exe");
        if (!Files.exists(rhashPath)) {
            showError("Checksum Folder Contents", "rhash executable was not found at: " + rhashPath);
            requestFocusedFileListFocus();
            return;
        }

        List<String> command = buildChecksumCommand(rhashPath, selectedItem.getFullPath(), options.get(), true);
        runExternal(command, false)
                .thenAccept(output -> Platform.runLater(() -> {
                    String resultText = String.join(System.lineSeparator(), output).trim();
                    if (resultText.isBlank()) {
                        showError("Checksum Folder Contents", "No checksum values were returned by rhash.");
                    } else {
                        showChecksumResultDialog(
                                "Checksum Folder Contents",
                                selectedItem.getName(),
                                options.get().algorithmLabel(),
                                resultText,
                                selectedItem.getFile().toPath(),
                                true
                        );
                    }
                    requestFocusedFileListFocus();
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> {
                        showError("Checksum Folder Contents", "Failed running rhash: " + throwable.getMessage());
                        requestFocusedFileListFocus();
                    });
                    return null;
                });
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
        logger.info("Extract Anything (ALT+F12)");
        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems)
                commands.extractAll(selectedItem, filesPanesHelper.getUnfocusedPath());
        } catch (Exception e) {
            error("Failed UNPacking file", e);
        }
    }

    public void mergePDFFiles() {
        logger.info("Merge PDF Files");
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
        logger.info("Extract PDF Pages");
        try {
            List<FileItem> selectedItems = new ArrayList<>(filesPanesHelper.getSelectedItems());
            for (FileItem selectedItem : selectedItems)
                commands.extractPDFPages(selectedItem, filesPanesHelper.getUnfocusedPath());
        } catch (Exception e) {
            error("Failed Extracting Pages from PDF file", e);
        }
    }

    public boolean canCompareSelectedFiles() {
        FileItem leftSelected = getSingleSelectedFile(leftFileList);
        FileItem rightSelected = getSingleSelectedFile(rightFileList);
        if (leftSelected == null || rightSelected == null) {
            return false;
        }
        return org.chaiware.acommander.helpers.FileHelper.isTextFile(leftSelected) && org.chaiware.acommander.helpers.FileHelper.isTextFile(rightSelected);
    }

    public void compareFiles() {
        logger.info("Compare Files");

        FilesPanesHelper.FocusSide lastSelectedSide = filesPanesHelper.getFocusedSide();
        FileItem lastSelectedFile = lastSelectedSide == LEFT
                ? getSingleSelectedFile(leftFileList)
                : getSingleSelectedFile(rightFileList);

        FileItem leftSelected = getSingleSelectedFile(leftFileList);
        FileItem rightSelected = getSingleSelectedFile(rightFileList);
        if (leftSelected == null || rightSelected == null) {
            showError("Compare Files", "Select exactly one file in each panel.");
            restoreFocusToFile(lastSelectedSide, lastSelectedFile);
            return;
        }

        if (!org.chaiware.acommander.helpers.FileHelper.isTextFile(leftSelected) || !org.chaiware.acommander.helpers.FileHelper.isTextFile(rightSelected)) {
            showError("Compare Files", "Only text files can be compared. One of the selected files appears to be binary.");
            restoreFocusToFile(lastSelectedSide, lastSelectedFile);
            return;
        }

        Optional<CompareFilesOptions> options = promptCompareFilesOptions(leftSelected, rightSelected);
        if (options.isEmpty()) {
            restoreFocusToFile(lastSelectedSide, lastSelectedFile);
            return;
        }

        String examDiffPath = Paths.get(System.getProperty("user.dir"), "apps", "file_compare", "ExamDiff.exe").toString();
        if (!Files.exists(Path.of(examDiffPath))) {
            showError("Compare Files", "ExamDiff executable was not found at: " + examDiffPath);
            restoreFocusToFile(lastSelectedSide, lastSelectedFile);
            return;
        }

        List<String> command = buildCompareCommand(examDiffPath, leftSelected, rightSelected, options.get());
        runExternal(command, false, Set.of(27))
                .whenComplete((output, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        showError("Compare Files", "Failed running ExamDiff: " + throwable.getMessage());
                    }
                    restoreFocusToFile(lastSelectedSide, lastSelectedFile);
                }));
    }

    public void compareFolders() {
        logger.info("Compare Folders");

        Path leftRoot = Paths.get(filesPanesHelper.getPath(LEFT));
        Path rightRoot = Paths.get(filesPanesHelper.getPath(RIGHT));
        if (!Files.isDirectory(leftRoot) || !Files.isDirectory(rightRoot)) {
            showError("Compare Folders", "Both panel paths must be valid folders.");
            return;
        }

        Optional<CompareFoldersOptions> options = promptCompareFoldersOptions(leftRoot, rightRoot);
        if (options.isEmpty()) {
            return;
        }

        try {
            FolderCompareResult result = compareFolderTrees(leftRoot, rightRoot, options.get());
            folderCompareMarks.put(LEFT, new HashMap<>(result.leftMarks()));
            folderCompareMarks.put(RIGHT, new HashMap<>(result.rightMarks()));
            filesPanesHelper.refreshFileListViews();
            showInfo(
                    "Compare Folders",
                    "Only left: " + result.onlyLeftCount()
                            + "\nOnly right: " + result.onlyRightCount()
                            + "\nDifferent: " + result.differentCount()
            );
            requestFocusedFileListFocus();
        } catch (Exception ex) {
            showError("Compare Folders", "Failed comparing folders: " + ex.getMessage());
            logger.warn("Failed comparing folders", ex);
            requestFocusedFileListFocus();
        }
    }

    private Optional<CompareFoldersOptions> promptCompareFoldersOptions(Path leftRoot, Path rightRoot) {
        Dialog<CompareFoldersOptions> dialog = new Dialog<>();
        dialog.setTitle("Compare Folders");
        dialog.setHeaderText(null);

        ButtonType compareType = new ButtonType("Compare", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(compareType, ButtonType.CANCEL);

        Label title = new Label("Compare Folders");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitle = new Label("Left: " + leftRoot + " | Right: " + rightRoot);
        subtitle.setWrapText(true);

        CheckBox compareByDate = new CheckBox("Compare also by date");
        CheckBox checksum = new CheckBox("Checksum files for comparison (slower)");
        CheckBox recursive = new CheckBox("Compare also subfolders (recursively)");
        recursive.setSelected(true);
        CheckBox caseSensitive = new CheckBox("Case-sensitive filename matching");

        VBox content = new VBox(
                10,
                title,
                subtitle,
                new Separator(),
                compareByDate,
                checksum,
                recursive,
                caseSensitive
        );
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(700, 320);
        applyThemeToDialog(dialog);
        Button compareButton = (Button) dialog.getDialogPane().lookupButton(compareType);
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ENTER) {
                return;
            }
            if (compareButton != null && !compareButton.isDisabled()) {
                compareButton.fire();
            }
            event.consume();
        });

        dialog.setResultConverter(button -> {
            if (button != compareType) {
                return null;
            }
            return new CompareFoldersOptions(
                    compareByDate.isSelected(),
                    checksum.isSelected(),
                    recursive.isSelected(),
                    caseSensitive.isSelected()
            );
        });
        return dialog.showAndWait();
    }

    private FolderCompareResult compareFolderTrees(Path leftRoot, Path rightRoot, CompareFoldersOptions options) throws IOException {
        Map<String, FolderEntry> leftEntries = collectFolderEntries(leftRoot, options);
        Map<String, FolderEntry> rightEntries = collectFolderEntries(rightRoot, options);

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(leftEntries.keySet());
        allKeys.addAll(rightEntries.keySet());

        Map<String, FolderCompareMark> leftMarks = new HashMap<>();
        Map<String, FolderCompareMark> rightMarks = new HashMap<>();
        int onlyLeft = 0;
        int onlyRight = 0;
        int different = 0;
        Map<String, String> checksumCache = new HashMap<>();

        for (String key : allKeys) {
            FolderEntry left = leftEntries.get(key);
            FolderEntry right = rightEntries.get(key);

            if (left == null) {
                onlyRight++;
                mark(rightMarks, right.topLevelPath(), FolderCompareMark.RIGHT_ONLY);
                continue;
            }
            if (right == null) {
                onlyLeft++;
                mark(leftMarks, left.topLevelPath(), FolderCompareMark.LEFT_ONLY);
                continue;
            }
            if (left.directory() != right.directory()) {
                different++;
                mark(leftMarks, left.topLevelPath(), FolderCompareMark.DIFFERENT);
                mark(rightMarks, right.topLevelPath(), FolderCompareMark.DIFFERENT);
                continue;
            }
            if (left.directory()) {
                continue;
            }
            if (left.size() != right.size()) {
                different++;
                mark(leftMarks, left.topLevelPath(), FolderCompareMark.DIFFERENT);
                mark(rightMarks, right.topLevelPath(), FolderCompareMark.DIFFERENT);
                continue;
            }
            if (options.compareByDate() && left.modifiedMillis() != right.modifiedMillis()) {
                different++;
                mark(leftMarks, left.topLevelPath(), FolderCompareMark.DIFFERENT);
                mark(rightMarks, right.topLevelPath(), FolderCompareMark.DIFFERENT);
                continue;
            }
            if (options.checksum()) {
                String leftChecksum = getOrComputeChecksum(checksumCache, left.absolutePath());
                String rightChecksum = getOrComputeChecksum(checksumCache, right.absolutePath());
                if (!Objects.equals(leftChecksum, rightChecksum)) {
                    different++;
                    mark(leftMarks, left.topLevelPath(), FolderCompareMark.DIFFERENT);
                    mark(rightMarks, right.topLevelPath(), FolderCompareMark.DIFFERENT);
                }
            }
        }

        return new FolderCompareResult(leftMarks, rightMarks, onlyLeft, onlyRight, different);
    }

    private Map<String, FolderEntry> collectFolderEntries(Path root, CompareFoldersOptions options) throws IOException {
        Map<String, FolderEntry> entries = new HashMap<>();
        if (!options.recursive()) {
            try (var stream = Files.list(root)) {
                stream.forEach(path -> addFolderEntry(entries, root, path, options));
            }
            return entries;
        }
        try (var stream = Files.walk(root)) {
            stream
                    .filter(path -> !path.equals(root))
                    .forEach(path -> addFolderEntry(entries, root, path, options));
        }
        return entries;
    }

    private void addFolderEntry(Map<String, FolderEntry> entries, Path root, Path path, CompareFoldersOptions options) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        String key = options.caseSensitiveNames() ? relative : relative.toLowerCase(Locale.ROOT);
        String topLevelName = extractTopLevelName(relative);
        boolean directory = Files.isDirectory(path);
        long size = 0L;
        long modified = 0L;
        if (!directory) {
            try {
                size = Files.size(path);
            } catch (IOException ignored) {
                size = 0L;
            }
        }
        try {
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            modified = 0L;
        }
        entries.putIfAbsent(
                key,
                new FolderEntry(path.toAbsolutePath().normalize(), root.resolve(topLevelName).toAbsolutePath().normalize(), directory, size, modified)
        );
    }

    private String extractTopLevelName(String relativePath) {
        int slash = relativePath.indexOf('/');
        if (slash < 0) {
            return relativePath;
        }
        return relativePath.substring(0, slash);
    }

    private String getOrComputeChecksum(Map<String, String> cache, Path file) throws IOException {
        String cacheKey = normalizePathKey(file);
        String existing = cache.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        String computed = checksumSha256(file);
        cache.put(cacheKey, computed);
        return computed;
    }

    private String checksumSha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
        try (DigestInputStream dis = new DigestInputStream(Files.newInputStream(file), digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // consume stream
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void mark(Map<String, FolderCompareMark> marks, Path topLevelPath, FolderCompareMark mark) {
        if (topLevelPath == null) {
            return;
        }
        String key = normalizePathKey(topLevelPath);
        FolderCompareMark existing = marks.get(key);
        if (existing == FolderCompareMark.DIFFERENT || existing == mark) {
            return;
        }
        if ((existing == FolderCompareMark.LEFT_ONLY && mark == FolderCompareMark.RIGHT_ONLY)
                || (existing == FolderCompareMark.RIGHT_ONLY && mark == FolderCompareMark.LEFT_ONLY)) {
            marks.put(key, FolderCompareMark.DIFFERENT);
            return;
        }
        marks.put(key, mark);
    }

    private String normalizePathKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private void clearFolderCompareHighlights(boolean refresh) {
        folderCompareMarks.computeIfAbsent(LEFT, unused -> new HashMap<>()).clear();
        folderCompareMarks.computeIfAbsent(RIGHT, unused -> new HashMap<>()).clear();
        if (refresh) {
            filesPanesHelper.refreshFileListViews();
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
                applyThemeToDialog(alert);
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
        Platform.runLater(() -> filesPanesHelper.getFileList(true).requestFocus());
    }

    public void bookmarkCurrentPath() {
        String focusedPath = filesPanesHelper.getFocusedPath();
        if (focusedPath == null || focusedPath.isBlank()) {
            showError("Bookmark this path", "Current path is empty.");
            return;
        }

        String suggestedName = suggestBookmarkName(focusedPath);
        Optional<String> input = getUserFeedback(suggestedName, "Bookmark this path", "Bookmark name");
        if (input.isEmpty()) {
            return;
        }

        String name = input.get().trim();
        if (name.isBlank()) {
            showError("Bookmark this path", "Bookmark name cannot be empty.");
            return;
        }

        bookmarks.put(name, focusedPath);
        saveConfigFile();
    }

    public void gotoBookmark() {
        try {
            Optional<String> selected = promptBookmarkSelection("Goto Bookmark", "Go to selected bookmark", "Go");
            if (selected.isEmpty()) {
                return;
            }

            String path = bookmarks.get(selected.get());
            if (path == null || path.isBlank()) {
                showError("Goto Bookmark", "Bookmark path is missing.");
                return;
            }
            if (!Files.isDirectory(Path.of(path))) {
                showError("Goto Bookmark", "Bookmark path does not exist: " + path);
                return;
            }

            filesPanesHelper.setFocusedFileListPath(path);
        } finally {
            requestFocusedFileListFocus();
        }
    }

    public void removeBookmark() {
        Optional<String> selected = promptBookmarkSelection("Remove Bookmark", "Select bookmark to remove", "Remove");
        if (selected.isEmpty()) {
            return;
        }
        bookmarks.remove(selected.get());
        saveConfigFile();
    }

    public void filterByChar(char selectedChar) {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        ListView<FileItem> listView = side == LEFT ? leftFileList : rightFileList;
        List<FileItem> currentItems = List.copyOf(listView.getItems());
        String existingPrefix = incrementalCharFilters.getOrDefault(side, "");

        List<FileItem> baseItems = incrementalFilterBases.get(side);
        if (baseItems == null || !isSubset(currentItems, baseItems) || !matchesActiveFilter(currentItems, baseItems, existingPrefix)) {
            baseItems = currentItems;
            existingPrefix = "";
        }

        String nextPrefix = existingPrefix + Character.toLowerCase(selectedChar);
        incrementalFilterBases.put(side, baseItems);
        incrementalCharFilters.put(side, nextPrefix);
        applyIncrementalFilter(side, nextPrefix);
        showIncrementalFilterPopup(nextPrefix);
    }

    public void clearCharFilter() {
        clearCharFilter(filesPanesHelper.getFocusedSide());
    }

    public boolean backspaceCharFilter() {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        String existingPrefix = incrementalCharFilters.getOrDefault(side, "");
        if (existingPrefix.isEmpty()) {
            return false;
        }

        String updatedPrefix = existingPrefix.substring(0, existingPrefix.length() - 1);
        if (updatedPrefix.isEmpty()) {
            clearCharFilter(side);
            hideIncrementalFilterPopup();
            return true;
        }

        incrementalCharFilters.put(side, updatedPrefix);
        applyIncrementalFilter(side, updatedPrefix);
        showIncrementalFilterPopup(updatedPrefix);
        return true;
    }

    private void clearCharFilter(FilesPanesHelper.FocusSide side) {
        List<FileItem> baseItems = incrementalFilterBases.remove(side);
        incrementalCharFilters.remove(side);
        hideIncrementalFilterPopup();
        if (baseItems == null) {
            return;
        }

        ListView<FileItem> listView = side == LEFT ? leftFileList : rightFileList;
        FileItem selectedItem = listView.getSelectionModel().getSelectedItem();
        listView.getItems().setAll(baseItems);
        if (selectedItem != null && listView.getItems().contains(selectedItem)) {
            listView.getSelectionModel().select(selectedItem);
            return;
        }
        if (!listView.getItems().isEmpty()) {
            listView.getSelectionModel().selectFirst();
        }
    }

    private void applyIncrementalFilter(FilesPanesHelper.FocusSide side, String prefix) {
        List<FileItem> baseItems = incrementalFilterBases.getOrDefault(side, List.of());
        List<FileItem> filteredItems = baseItems.stream()
                .filter(item -> "..".equals(item.getPresentableFilename())
                        || item.getPresentableFilename().toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();

        ListView<FileItem> listView = side == LEFT ? leftFileList : rightFileList;
        listView.getItems().setAll(filteredItems);
        filteredItems.stream()
                .filter(item -> !"..".equals(item.getPresentableFilename()))
                .findFirst()
                .ifPresentOrElse(
                        item -> listView.getSelectionModel().select(item),
                        () -> {
                            if (!filteredItems.isEmpty()) {
                                listView.getSelectionModel().selectFirst();
                            }
                        }
                );
    }

    private boolean isSubset(List<FileItem> currentItems, List<FileItem> baseItems) {
        return currentItems.stream().allMatch(baseItems::contains);
    }

    private boolean matchesActiveFilter(List<FileItem> currentItems, List<FileItem> baseItems, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return currentItems.equals(baseItems);
        }

        List<FileItem> expectedItems = baseItems.stream()
                .filter(item -> "..".equals(item.getPresentableFilename())
                        || item.getPresentableFilename().toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
        return currentItems.equals(expectedItems);
    }

    private void showIncrementalFilterPopup(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            hideIncrementalFilterPopup();
            return;
        }

        ListView<FileItem> focusedList = filesPanesHelper.getFocusedSide() == LEFT ? leftFileList : rightFileList;
        if (focusedList == null || focusedList.getScene() == null) {
            return;
        }

        Bounds bounds = focusedList.localToScreen(focusedList.getBoundsInLocal());
        if (bounds == null) {
            return;
        }

        initializeIncrementalFilterPopupIfNeeded();
        incrementalFilterPopupLabel.setText(prefix);

        double x = bounds.getMaxX() - 32;
        double y = bounds.getMinY() + 8;
        if (incrementalFilterPopup.isShowing()) {
            incrementalFilterPopup.hide();
        }
        incrementalFilterPopup.show(focusedList, x, y);
    }

    private void hideIncrementalFilterPopup() {
        if (incrementalFilterPopup != null && incrementalFilterPopup.isShowing()) {
            incrementalFilterPopup.hide();
        }
    }

    private void initializeIncrementalFilterPopupIfNeeded() {
        if (incrementalFilterPopup != null) {
            return;
        }

        incrementalFilterPopup = new Popup();
        incrementalFilterPopupLabel = new Label();

        incrementalFilterPopupLabel.setStyle(
                "-fx-background-color: rgba(20, 20, 20, 0.92);"
                        + "-fx-text-fill: white;"
                        + "-fx-padding: 4 8 4 8;"
                        + "-fx-background-radius: 6;"
                        + "-fx-font-size: 12px;"
                        + "-fx-font-weight: bold;"
        );
        incrementalFilterPopup.getContent().add(incrementalFilterPopupLabel);
        incrementalFilterPopup.setAutoHide(false);
        incrementalFilterPopup.setHideOnEscape(false);
    }

    /** Opens a dialog with the title asking the requested question returning the optional user's input */
    private Optional<String> getUserFeedback(String defaultValue, String title, String question) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setHeaderText("");
        dialog.setTitle(title);
        dialog.setContentText(question);
        dialog.getEditor().setPrefWidth(300);
        applyThemeToDialog(dialog);

        return dialog.showAndWait();
    }

    private void requestFocusedFileListFocus() {
        Platform.runLater(() -> {
            if (filesPanesHelper.getFocusedSide() == LEFT) {
                leftFileList.requestFocus();
            } else {
                rightFileList.requestFocus();
            }
        });
    }

    private void requestUnfocusedFileListFocus() {
        Platform.runLater(() -> {
            if (filesPanesHelper.getFocusedSide() == LEFT) {
                rightFileList.requestFocus();
            } else {
                leftFileList.requestFocus();
            }
        });
    }

    public Optional<String> promptUser(String defaultValue, String title, String question) {
        return getUserFeedback(defaultValue, title, question);
    }

    private Optional<String> promptBookmarkSelection(String title, String hint, String actionButtonLabel) {
        if (bookmarks.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText("No bookmarks found.");
            applyThemeToDialog(alert);
            alert.showAndWait();
            return Optional.empty();
        }

        List<String> names = bookmarks.keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        ButtonType actionType = new ButtonType(actionButtonLabel, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(actionType, ButtonType.CANCEL);

        ListView<String> listView = new ListView<>();
        listView.getItems().setAll(names);
        listView.getSelectionModel().selectFirst();
        listView.setPrefWidth(460);
        listView.setPrefHeight(Math.min(320, Math.max(140, names.size() * 34)));
        listView.setCellFactory(unused -> new ListCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Label nameLabel = new Label(name);
                nameLabel.setStyle("-fx-font-weight: bold;");
                Label pathLabel = new Label(bookmarks.getOrDefault(name, ""));
                pathLabel.setStyle("-fx-opacity: 0.8;");
                VBox row = new VBox(2, nameLabel, pathLabel);
                setGraphic(row);
            }
        });

        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dialog.setResult(selected);
                    dialog.close();
                }
            }
        });

        Label hintLabel = new Label(hint);
        VBox content = new VBox(10, hintLabel, listView);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        Button actionButton = (Button) dialog.getDialogPane().lookupButton(actionType);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        actionButton.setDefaultButton(true);
        cancelButton.setCancelButton(true);
        Runnable syncActionButton = () -> actionButton.setDisable(listView.getSelectionModel().getSelectedItem() == null);
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> syncActionButton.run());
        syncActionButton.run();

        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (!actionButton.isDisabled()) {
                    actionButton.fire();
                }
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                cancelButton.fire();
                event.consume();
            }
        });

        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.UP && event.getCode() != KeyCode.DOWN) {
                return;
            }
            int size = listView.getItems().size();
            if (size == 0) {
                return;
            }
            int index = listView.getSelectionModel().getSelectedIndex();
            if (index < 0) {
                index = 0;
            } else if (event.getCode() == KeyCode.DOWN) {
                index = Math.min(index + 1, size - 1);
            } else {
                index = Math.max(index - 1, 0);
            }
            listView.getSelectionModel().select(index);
            listView.scrollTo(index);
            listView.requestFocus();
            event.consume();
        });

        dialog.setOnShown(event -> {
            if (!listView.getItems().isEmpty() && listView.getSelectionModel().getSelectedIndex() < 0) {
                listView.getSelectionModel().selectFirst();
            }
            Platform.runLater(listView::requestFocus);
        });

        dialog.setResultConverter(button -> {
            if (button == actionType) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });
        applyThemeToDialog(dialog);
        return dialog.showAndWait();
    }

    private String suggestBookmarkName(String path) {
        if (path == null || path.isBlank()) {
            return "bookmark";
        }
        try {
            Path normalized = Path.of(path.trim()).normalize();
            Path fileName = normalized.getFileName();
            if (fileName != null) {
                String candidate = fileName.toString().trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
            // Fall back to a simple string-based extraction below.
        }

        String normalizedText = path.trim().replace('\\', '/');
        while (normalizedText.endsWith("/") && normalizedText.length() > 1) {
            normalizedText = normalizedText.substring(0, normalizedText.length() - 1);
        }
        int lastSlash = normalizedText.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < normalizedText.length() - 1) {
            return normalizedText.substring(lastSlash + 1);
        }
        return normalizedText.isEmpty() ? "bookmark" : normalizedText;
    }

    private void loadBookmarksFromProperties() {
        bookmarks.clear();
        List<String> bookmarkKeys = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(BOOKMARK_KEY_PREFIX))
                .toList();
        for (String key : bookmarkKeys) {
            String name = key.substring(BOOKMARK_KEY_PREFIX.length()).trim();
            String path = properties.getProperty(key, "").trim();
            if (!name.isEmpty() && !path.isEmpty()) {
                bookmarks.put(name, path);
            }
        }
    }

    private void syncBookmarksToProperties() {
        List<String> keysToRemove = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(BOOKMARK_KEY_PREFIX))
                .toList();
        for (String key : keysToRemove) {
            properties.remove(key);
        }
        for (Map.Entry<String, String> entry : bookmarks.entrySet()) {
            properties.setProperty(BOOKMARK_KEY_PREFIX + entry.getKey(), entry.getValue());
        }
    }

    public CompletableFuture<List<String>> runExternal(List<String> command, boolean refreshAfter) {
        return commands.runExternal(command, refreshAfter);
    }

    public CompletableFuture<List<String>> runExternal(
            List<String> command,
            boolean refreshAfter,
            Set<Integer> acceptedNonZeroExitCodes
    ) {
        return commands.runExternal(command, refreshAfter, acceptedNonZeroExitCodes);
    }

    private Optional<String> promptSplitSize(FileItem selectedItem, long originalFileSize) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Split a Large File");
        dialog.setHeaderText(null);

        ButtonType splitType = new ButtonType("Split", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(splitType, ButtonType.CANCEL);

        Label title = new Label("Split a Large File");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label details = new Label(
                "File: " + selectedItem.getName() + " (" + humanSize(originalFileSize) + ")"
        );
        TextField sizeField = new TextField("16m");
        sizeField.setPromptText("Chunk size (examples: 16m, 64k, 1g, 16mb)");

        Label validationLabel = new Label();
        validationLabel.setWrapText(true);

        VBox content = new VBox(10,
                title,
                details,
                new Label("Size per split file:"),
                sizeField,
                validationLabel
        );
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        applyThemeToDialog(dialog);

        Button splitButton = (Button) dialog.getDialogPane().lookupButton(splitType);
        final SplitSize[] parsed = new SplitSize[1];
        Runnable validate = () -> {
            parsed[0] = parseSplitSize(sizeField.getText());
            if (!parsed[0].valid()) {
                validationLabel.setText(parsed[0].message());
                splitButton.setDisable(true);
                return;
            }

            long chunkBytes = parsed[0].bytes();
            if (chunkBytes > originalFileSize) {
                validationLabel.setText(
                        "Requested split size (" + humanSize(chunkBytes) + ") is larger than the original file ("
                                + humanSize(originalFileSize) + "). Splitting does not make sense."
                );
                splitButton.setDisable(true);
                return;
            }

            long filesCount = (originalFileSize + chunkBytes - 1) / chunkBytes;
            validationLabel.setText("This will create " + filesCount + " file(s).");
            splitButton.setDisable(false);
        };

        sizeField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        dialog.setResultConverter(buttonType -> {
            if (buttonType == splitType && parsed[0] != null && parsed[0].valid()) {
                return parsed[0].sevenZipArg();
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private Optional<CompareFilesOptions> promptCompareFilesOptions(FileItem leftFile, FileItem rightFile) {
        Dialog<CompareFilesOptions> dialog = new Dialog<>();
        dialog.setTitle("Compare Files");
        dialog.setHeaderText(null);

        ButtonType compareType = new ButtonType("Compare", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(compareType, ButtonType.CANCEL);

        Label title = new Label("Compare Files");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitle = new Label("Left: " + leftFile.getName() + " | Right: " + rightFile.getName());
        subtitle.setWrapText(true);

        Label leftPath = new Label("Left file: " + leftFile.getFullPath());
        leftPath.setWrapText(true);
        Label rightPath = new Label("Right file: " + rightFile.getFullPath());
        rightPath.setWrapText(true);

        CheckBox ignoreCase = new CheckBox("Ignore case");
        ComboBox<WhiteSpaceCompareMode> whitespaceMode = new ComboBox<>();
        whitespaceMode.getItems().addAll(
                WhiteSpaceCompareMode.NONE,
                WhiteSpaceCompareMode.ALL,
                WhiteSpaceCompareMode.AMOUNT,
                WhiteSpaceCompareMode.LEADING,
                WhiteSpaceCompareMode.TRAILING
        );
        whitespaceMode.getSelectionModel().select(WhiteSpaceCompareMode.NONE);
        whitespaceMode.setPrefWidth(340);

        CheckBox differencesOnly = new CheckBox("Show differences only");

        VBox content = new VBox(
                10,
                title,
                subtitle,
                new Separator(),
                leftPath,
                rightPath,
                new Separator(),
                ignoreCase,
                new HBox(10, new Label("Whitespace handling:"), whitespaceMode),
                differencesOnly
        );
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(680, 340);
        applyThemeToDialog(dialog);
        Button compareButton = (Button) dialog.getDialogPane().lookupButton(compareType);
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.ENTER) {
                return;
            }
            if (compareButton != null && !compareButton.isDisabled()) {
                compareButton.fire();
            }
            event.consume();
        });

        dialog.setResultConverter(button -> {
            if (button != compareType) {
                return null;
            }
            WhiteSpaceCompareMode selectedWhitespace = whitespaceMode.getSelectionModel().getSelectedItem();
            if (selectedWhitespace == null) {
                selectedWhitespace = WhiteSpaceCompareMode.NONE;
            }
            return new CompareFilesOptions(
                    ignoreCase.isSelected(),
                    selectedWhitespace,
                    differencesOnly.isSelected()
            );
        });
        return dialog.showAndWait();
    }

    private List<String> buildCompareCommand(String examDiffPath, FileItem leftFile, FileItem rightFile, CompareFilesOptions options) {
        List<String> command = new ArrayList<>();
        command.add(examDiffPath);
        command.add(leftFile.getFullPath());
        command.add(rightFile.getFullPath());
        command.add(options.ignoreCase() ? "/i" : "/!i");
        command.add("/t");
        command.add(options.differencesOnly() ? "/d" : "/!d");

        switch (options.whitespaceMode()) {
            case ALL -> {
                command.add("/w");
                command.add("/!b");
                command.add("/!l");
                command.add("/!e");
            }
            case AMOUNT -> {
                command.add("/!w");
                command.add("/b");
                command.add("/!l");
                command.add("/!e");
            }
            case LEADING -> {
                command.add("/!w");
                command.add("/!b");
                command.add("/l");
                command.add("/!e");
            }
            case TRAILING -> {
                command.add("/!w");
                command.add("/!b");
                command.add("/!l");
                command.add("/e");
            }
            case NONE -> {
                command.add("/!w");
                command.add("/!b");
                command.add("/!l");
                command.add("/!e");
            }
        }

        command.add("/n");
        return command;
    }

    private FileItem getSingleSelectedFile(ListView<FileItem> listView) {
        if (listView == null || listView.getSelectionModel() == null) {
            return null;
        }
        List<FileItem> selected = listView.getSelectionModel().getSelectedItems();
        if (selected == null || selected.size() != 1) {
            return null;
        }
        FileItem item = selected.getFirst();
        if (item == null || item.isDirectory() || "..".equals(item.getPresentableFilename())) {
            return null;
        }
        return item;
    }


    private void restoreFocusToFile(FilesPanesHelper.FocusSide side, FileItem fileItem) {
        Runnable restore = () -> {
            if (side == null || filesPanesHelper == null) {
                requestFocusedFileListFocus();
                return;
            }
            ListView<FileItem> targetList = side == LEFT ? leftFileList : rightFileList;
            if (targetList == null) {
                requestFocusedFileListFocus();
                return;
            }

            filesPanesHelper.setFocusedFileList(side);
            if (fileItem != null) {
                int index = targetList.getItems().indexOf(fileItem);
                if (index >= 0) {
                    targetList.getSelectionModel().clearAndSelect(index);
                    targetList.getFocusModel().focus(index);
                }
            }
            targetList.requestFocus();
        };

        if (Platform.isFxApplicationThread()) {
            restore.run();
        } else {
            Platform.runLater(restore);
        }
    }

    private SplitSize parseSplitSize(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) {
            return new SplitSize(false, 0L, "", "Please enter a split size.");
        }

        String unit = "";
        String digits = input;
        if (input.endsWith("kb")) {
            unit = "k";
            digits = input.substring(0, input.length() - 2);
        } else if (input.endsWith("mb")) {
            unit = "m";
            digits = input.substring(0, input.length() - 2);
        } else if (input.endsWith("gb")) {
            unit = "g";
            digits = input.substring(0, input.length() - 2);
        } else if (input.endsWith("k") || input.endsWith("m") || input.endsWith("g") || input.endsWith("b")) {
            unit = input.substring(input.length() - 1);
            digits = input.substring(0, input.length() - 1);
        }

        if (digits.isBlank() || !digits.chars().allMatch(Character::isDigit)) {
            return new SplitSize(false, 0L, "", "Use a positive size like 16m, 64k, 1g, or 16777216.");
        }

        long amount;
        try {
            amount = Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return new SplitSize(false, 0L, "", "Split size is too large.");
        }
        if (amount <= 0) {
            return new SplitSize(false, 0L, "", "Split size must be greater than zero.");
        }

        long bytes;
        switch (unit) {
            case "", "b" -> bytes = amount;
            case "k" -> bytes = amount * 1024L;
            case "m" -> bytes = amount * 1024L * 1024L;
            case "g" -> bytes = amount * 1024L * 1024L * 1024L;
            default -> {
                return new SplitSize(false, 0L, "", "Unsupported unit. Use k, m, g, or bytes.");
            }
        }

        if (bytes <= 0) {
            return new SplitSize(false, 0L, "", "Split size is too large.");
        }

        String sevenZipArg = amount + unit;
        return new SplitSize(true, bytes, sevenZipArg, "");
    }

    private String buildSplitArchiveName(String sourceFilename) {
        int dotIndex = sourceFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            return sourceFilename.substring(0, dotIndex) + ".7z";
        }
        return sourceFilename + ".7z";
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.2f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.2f MB", mb);
        }
        double gb = mb / 1024.0;
        return String.format(Locale.ROOT, "%.2f GB", gb);
    }

    private List<String> buildChecksumCommand(Path rhashPath, String targetPath, ChecksumOptions options, boolean recursive) {
        List<String> command = new ArrayList<>();
        command.add(rhashPath.toString());
        command.add(options.algorithmFlag());
        if (recursive) {
            command.add("--recursive");
        }
        if (options.base32()) {
            command.add("--base32");
        } else if (options.base64()) {
            command.add("--base64");
        } else {
            command.add("--hex");
        }
        if (!options.includeFileNames()) {
            command.add("--simple");
        }
        command.add(targetPath);
        return command;
    }

    private Optional<ChecksumOptions> promptChecksumOptions(String titleText, String selectedName, boolean includeNamesByDefault) {
        Dialog<ChecksumOptions> dialog = new Dialog<>();
        dialog.setTitle(titleText);
        dialog.setHeaderText(null);

        ButtonType computeType = new ButtonType("Compute", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(computeType, ButtonType.CANCEL);

        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitle = new Label("Selected item: " + selectedName);

        ToggleGroup hashToggle = new ToggleGroup();
        RadioButton md5 = new RadioButton("MD5");
        RadioButton sha1 = new RadioButton("SHA1");
        RadioButton sha256 = new RadioButton("SHA256");
        RadioButton sha512 = new RadioButton("SHA512");
        RadioButton crc32 = new RadioButton("CRC32");
        md5.setToggleGroup(hashToggle);
        sha1.setToggleGroup(hashToggle);
        sha256.setToggleGroup(hashToggle);
        sha512.setToggleGroup(hashToggle);
        crc32.setToggleGroup(hashToggle);
        sha256.setSelected(true);

        GridPane hashGrid = new GridPane();
        hashGrid.setHgap(16);
        hashGrid.setVgap(8);
        hashGrid.add(md5, 0, 0);
        hashGrid.add(sha1, 1, 0);
        hashGrid.add(sha256, 0, 1);
        hashGrid.add(sha512, 1, 1);
        hashGrid.add(crc32, 0, 2);

        CheckBox outputBase32 = new CheckBox("Output as Base32");
        CheckBox outputBase64 = new CheckBox("Output as Base64");
        CheckBox includeFileNames = new CheckBox("Include file names in output");
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

        VBox content = new VBox(
                12,
                title,
                subtitle,
                new Separator(),
                new Label("Checksum type (choose one):"),
                hashGrid,
                new Separator(),
                new Label("Options:"),
                outputBase32,
                outputBase64,
                includeFileNames
        );
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        applyThemeToDialog(dialog);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != computeType) {
                return null;
            }
            Toggle selected = hashToggle.getSelectedToggle();
            if (!(selected instanceof RadioButton selectedButton)) {
                return null;
            }

            return switch (selectedButton.getText()) {
                case "MD5" -> new ChecksumOptions("--md5", "MD5", outputBase32.isSelected(), outputBase64.isSelected(), includeFileNames.isSelected());
                case "SHA1" -> new ChecksumOptions("--sha1", "SHA1", outputBase32.isSelected(), outputBase64.isSelected(), includeFileNames.isSelected());
                case "SHA512" -> new ChecksumOptions("--sha512", "SHA512", outputBase32.isSelected(), outputBase64.isSelected(), includeFileNames.isSelected());
                case "CRC32" -> new ChecksumOptions("--crc32", "CRC32", outputBase32.isSelected(), outputBase64.isSelected(), includeFileNames.isSelected());
                default -> new ChecksumOptions("--sha256", "SHA256", outputBase32.isSelected(), outputBase64.isSelected(), includeFileNames.isSelected());
            };
        });
        return dialog.showAndWait();
    }

    private String extractDigestValue(List<String> outputLines) {
        if (outputLines == null) {
            return "";
        }
        Pattern valuePrefix = Pattern.compile("^\\s*([A-Za-z0-9+/=]+)");
        for (String line : outputLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("rhash:") || trimmed.toLowerCase(Locale.ROOT).contains("error")) {
                continue;
            }
            var matcher = valuePrefix.matcher(line);
            if (matcher.find()) {
                String candidate = matcher.group(1).trim();
                if (candidate.length() >= 8) {
                    return candidate;
                }
            }
        }
        return String.join(System.lineSeparator(), outputLines).trim();
    }

    private void showChecksumResultDialog(
            String titleText,
            String targetName,
            String algorithmLabel,
            String checksumValue,
            Path outputDirectory,
            boolean folderMode
    ) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(titleText);
        dialog.setHeaderText(null);

        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(closeType);

        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label details = new Label("Item: " + targetName + " | Type: " + algorithmLabel);

        TextArea checksumArea = new TextArea(checksumValue);
        checksumArea.setEditable(false);
        checksumArea.setWrapText(false);
        checksumArea.setPrefRowCount(Math.max(4, Math.min(16, checksumValue.lines().toArray().length + 1)));

        Button copyButton = new Button("Copy Value");
        copyButton.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(checksumArea.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        Button saveAsButton = new Button("Save Value As File");
        saveAsButton.setOnAction(event -> {
            Path savePath = buildChecksumOutputPath(outputDirectory, targetName, algorithmLabel, folderMode);
            try {
                Files.createDirectories(savePath.getParent());
                Files.writeString(savePath, checksumArea.getText(), StandardCharsets.UTF_8);
                showInfo(titleText, "Saved checksum value as:\n" + savePath);
            } catch (Exception ex) {
                showError(titleText, "Failed saving checksum file: " + ex.getMessage());
            }
        });

        HBox actions = new HBox(8, copyButton, saveAsButton);
        VBox content = new VBox(10, title, details, checksumArea, actions);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(760, 420);
        applyThemeToDialog(dialog);
        dialog.showAndWait();
    }

    private Path buildChecksumOutputPath(Path outputDirectory, String targetName, String algorithmLabel, boolean folderMode) {
        String algorithmLower = algorithmLabel.toLowerCase(Locale.ROOT);
        String fileName = folderMode
                ? targetName + "." + algorithmLabel.toUpperCase(Locale.ROOT) + "SUMS"
                : targetName + "." + algorithmLower;
        return outputDirectory.resolve(fileName);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.setResizable(true);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        applyThemeToDialog(alert);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.setResizable(true);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        applyThemeToDialog(alert);
        alert.showAndWait();
    }

    private record SplitSize(boolean valid, long bytes, String sevenZipArg, String message) {}
    private record ChecksumOptions(
            String algorithmFlag,
            String algorithmLabel,
            boolean base32,
            boolean base64,
            boolean includeFileNames
    ) {}
    private enum WhiteSpaceCompareMode {
        NONE("Do not ignore whitespace"),
        ALL("Ignore all whitespace"),
        AMOUNT("Ignore changes in amount of whitespace"),
        LEADING("Ignore leading whitespace"),
        TRAILING("Ignore trailing whitespace");

        private final String label;

        WhiteSpaceCompareMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
    private record CompareFilesOptions(
            boolean ignoreCase,
            WhiteSpaceCompareMode whitespaceMode,
            boolean differencesOnly
    ) {}
    private enum FolderCompareMark {
        LEFT_ONLY,
        RIGHT_ONLY,
        DIFFERENT
    }
    private record CompareFoldersOptions(
            boolean compareByDate,
            boolean checksum,
            boolean recursive,
            boolean caseSensitiveNames
    ) {}
    private record FolderEntry(
            Path absolutePath,
            Path topLevelPath,
            boolean directory,
            long size,
            long modifiedMillis
    ) {}
    private record FolderCompareResult(
            Map<String, FolderCompareMark> leftMarks,
            Map<String, FolderCompareMark> rightMarks,
            int onlyLeftCount,
            int onlyRightCount,
            int differentCount
    ) {}
    private enum ImageCompressionMode {
        QUALITY,
        LOSSLESS,
        MAX_SIZE
    }
    private enum AudioCompressionProfile {
        LOSSLESS,
        LOSSY,
        CUSTOM
    }
    private enum ImageResizeMode {
        NONE,
        WIDTH,
        HEIGHT,
        LONG_EDGE,
        SHORT_EDGE
    }
    private record ImageConversionRequest(
            String targetFormat,
            ImageCompressionMode compressionMode,
            Integer quality,
            String maxSize,
            boolean keepExif,
            boolean keepDates,
            ImageResizeMode resizeMode,
            Integer resizeValue,
            boolean noUpscale,
            String suffix,
            String overwritePolicy
    ) {}
    private record AudioConversionRequest(
            String targetFormat,
            AudioCompressionProfile compressionProfile,
            String encodingFlag,
            boolean normalize,
            Integer sampleRateOverride,
            String endian,
            String suffix,
            String conflictPolicy
    ) {}
    private record FindInFilesOptions(
            String query,
            boolean caseInsensitive,
            boolean findInSpecificExtension,
            String extension,
            boolean includeHiddenAndIgnored
    ) {}

    private Optional<FileAttributesHelper.AttributeChangeRequest> promptAttributes(List<FileItem> selectedItems) {
        Dialog<FileAttributesHelper.AttributeChangeRequest> dialog = new Dialog<>();
        dialog.setTitle("Change Attributes");
        dialog.setHeaderText(null);
        boolean darkTheme = currentThemeMode == ThemeMode.DARK;

        ButtonType applyType = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyType, ButtonType.CANCEL);

        Label title = new Label("Change Attributes");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label subtitle = new Label("Selected items: " + selectedItems.size() + " | Checked = set, unchecked = clear");
        subtitle.setStyle("-fx-text-fill: " + (darkTheme ? "#A9B7CF" : "#666666") + ";");

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
        content.setStyle(
                darkTheme
                        ? "-fx-background-color: #2a3443; -fx-background-radius: 8; -fx-border-color: #6f89aa; -fx-border-radius: 8; -fx-text-fill: #f3f7ff;"
                        : "-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #d5d9e0; -fx-border-radius: 8;"
        );
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setStyle(darkTheme ? "-fx-background-color: #1f2733;" : "-fx-background-color: #f3f5f8;");

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
        applyThemeToDialog(dialog);
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
        applyThemeToDialog(alert);
        alert.showAndWait();
    }

    public void updateBottomButtons(KeyCode whichKeyWasPressed) {
        bottomButtonModifier = whichKeyWasPressed;
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
                btnF10.setText("F10 Search for Files");
                btnF11.setText("F11 Pack");
                btnF12.setText("F12 UnPack");
            }
            case ALT -> {
                btnF1.setText("ALT+F1 Left Folder");
                btnF2.setText("ALT+F2 Right Folder");
                btnF3.setText("");
                btnF4.setText("ALT+F4 Exit");
                btnF5.setText("ALT+F5 Convert Media File");
                btnF6.setText("");
                btnF7.setText("ALT+F7 MkFile");
                btnF8.setText("");
                btnF9.setText("ALT+F9 Explorer");
                btnF10.setText("ALT+F10 Find in Files");
                btnF11.setText("ALT+F11 Split");
                btnF12.setText("ALT+F12 Extract Anything");
            }
            case SHIFT -> {
                btnF1.setText("");
                btnF2.setText("");
                btnF3.setText("");
                btnF4.setText("");
                btnF5.setText("");
                btnF6.setText("SHIFT+F6 Rename");
                btnF7.setText("");
                btnF8.setText("SHIFT+F8 Delete & Wipe");
                btnF9.setText("");
                btnF10.setText("");
                btnF11.setText("");
                btnF12.setText("");
            }
            case CONTROL -> {
            }

            default -> throw new IllegalStateException("Which key was pressed?: " + whichKeyWasPressed);
        }
    }

    /**
     * Checks if the focused pane is currently viewing an archive.
     */
    public boolean isInArchive() {
        return filesPanesHelper.isInArchive(filesPanesHelper.getFocusedSide());
    }

    /**
     * Checks if the focused pane is currently viewing a read-only archive.
     */
    public boolean isInReadOnlyArchive() {
        return filesPanesHelper.isArchiveReadOnly(filesPanesHelper.getFocusedSide());
    }
    
    /**
     * Checks if the unfocused (target) pane is currently viewing a read-only archive.
     */
    public boolean isUnfocusedPaneInReadOnlyArchive() {
        FilesPanesHelper.FocusSide unfocusedSide = filesPanesHelper.getFocusedSide() == FilesPanesHelper.FocusSide.LEFT 
            ? FilesPanesHelper.FocusSide.RIGHT 
            : FilesPanesHelper.FocusSide.LEFT;
        return filesPanesHelper.isArchiveReadOnly(unfocusedSide);
    }
    
    /**
     * Checks if either pane is currently viewing a read-only archive.
     */
    public boolean isAnyPaneInReadOnlyArchive() {
        return filesPanesHelper.isArchiveReadOnly(FilesPanesHelper.FocusSide.LEFT) ||
               filesPanesHelper.isArchiveReadOnly(FilesPanesHelper.FocusSide.RIGHT);
    }
    
    /**
     * Shows a warning dialog when attempting to modify a read-only archive.
     */
    public void showReadOnlyLocationWarning() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Read-Only Location");
        alert.setHeaderText("Cannot Modify Read-Only Location");
        alert.setContentText(
            "This location is read-only.\n\n" +
            "Any changes cannot be saved back to the source."
        );
        alert.getDialogPane().getStyleClass().removeAll("theme-dark", "theme-light");
        alert.getDialogPane().getStyleClass().add(currentThemeMode.styleClass);
        alert.showAndWait();
    }
    
    /**
     * Marks the current archive as needing repack if a file was modified.
     * Call this after operations that modify files in an archive.
     */
    public void markArchiveModified() {
        filesPanesHelper.markArchiveNeedsRepack(filesPanesHelper.getFocusedSide());
    }

    private void applyTheme(Scene scene, ThemeMode themeMode, boolean persist) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        scene.getRoot().getStyleClass().removeAll(THEME_DARK_CLASS, THEME_LIGHT_CLASS);
        scene.getRoot().getStyleClass().add(themeMode.styleClass);
        currentThemeMode = themeMode;
        if (persist) {
            properties.setProperty(THEME_MODE_KEY, themeMode.configValue);
            saveConfigFile();
        }
    }

    private void applyThemeToDialog(Dialog<?> dialog) {
        if (dialog == null || rootPane == null || rootPane.getScene() == null) {
            return;
        }
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().removeAll(THEME_DARK_CLASS, THEME_LIGHT_CLASS);
        pane.getStyleClass().add(currentThemeMode.styleClass);
        for (String stylesheet : rootPane.getScene().getStylesheets()) {
            if (!pane.getStylesheets().contains(stylesheet)) {
                pane.getStylesheets().add(stylesheet);
            }
        }
    }

    private enum ThemeMode {
        DARK("dark", THEME_DARK_CLASS),
        REGULAR("regular", THEME_LIGHT_CLASS);

        private final String configValue;
        private final String styleClass;

        ThemeMode(String configValue, String styleClass) {
            this.configValue = configValue;
            this.styleClass = styleClass;
        }

        private static ThemeMode from(String value) {
            if ("dark".equalsIgnoreCase(value)) {
                return DARK;
            }
            if ("light".equalsIgnoreCase(value) || "regular".equalsIgnoreCase(value)) {
                return REGULAR;
            }
            return REGULAR;
        }
    }
}

