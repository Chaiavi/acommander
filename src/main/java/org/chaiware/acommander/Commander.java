package org.chaiware.acommander;

import javafx.animation.PauseTransition;
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
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import org.chaiware.acommander.actions.ActionContext;
import org.chaiware.acommander.actions.ActionExecutor;
import org.chaiware.acommander.actions.ActionRegistry;
import org.chaiware.acommander.commands.*;
import org.chaiware.acommander.config.AppConfigLoader;
import org.chaiware.acommander.config.AppRegistry;
import org.chaiware.acommander.dialog.*;
import org.chaiware.acommander.dialog.DialogTheme.ThemeMode;
import org.chaiware.acommander.helpers.*;
import org.chaiware.acommander.keybinding.KeyBindingManager;
import org.chaiware.acommander.keybinding.KeyBindingManager.KeyContext;
import org.chaiware.acommander.model.ArchiveMode;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.palette.CommandPaletteController;
import org.chaiware.acommander.services.FolderComparer;
import org.chaiware.acommander.services.MediaConversionService;
import org.chaiware.acommander.services.MediaConversionService.AudioRequest;
import org.chaiware.acommander.services.MediaConversionService.TrimRequest;
import org.chaiware.acommander.services.MediaConversionService.VideoRequest;
import org.chaiware.acommander.services.ClipboardTransfer;
import org.chaiware.acommander.services.ImageConversionService;
import org.chaiware.acommander.services.ImageConversionService.ImageConversionRequest;
import org.chaiware.acommander.helpers.PaneSorter.SortColumn;
import org.chaiware.acommander.services.ArchiveOperations;
import org.chaiware.acommander.services.FileOperations;
import org.chaiware.acommander.services.LinkedNavigation;
import org.chaiware.acommander.services.LockingPrograms;
import org.chaiware.acommander.services.PaneDragDrop;
import org.chaiware.acommander.services.PdfOperations;
import org.chaiware.acommander.services.ToolUpdateService;
import org.chaiware.acommander.services.TransferConflicts;
import org.chaiware.acommander.tools.BundledTool;
import org.chaiware.acommander.tools.BundledToolCommands;
import org.chaiware.acommander.tools.BundledToolCommands.ChecksumOptions;
import org.chaiware.acommander.tools.BundledToolCommands.CompareFilesOptions;
import org.chaiware.acommander.tools.BundledToolCommands.FindInFilesOptions;
import org.chaiware.acommander.tools.FilePropertiesLauncher;
import org.chaiware.acommander.tools.ProcessRunner;
import org.chaiware.acommander.vfs.ArchiveFileSystem;
import org.chaiware.acommander.vfs.FtpConnectionOptions;
import org.chaiware.acommander.vfs.FtpFileSystem;
import org.chaiware.acommander.vfs.LocalFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static java.awt.Desktop.getDesktop;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.RIGHT;
import static org.chaiware.acommander.model.FileItem.humanSize;


public class Commander {

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
    Button btnF1, btnF2, btnF3, btnF4, btnF5, btnF6, btnDup, btnF7, btnF8, btnF9, btnF10, btnF11, btnF12;
    @FXML
    HBox externalProgressBox;
    @FXML
    ProgressBar externalProgressBar;
    @FXML
    Label externalProgressLabel;
    @FXML
    Button externalStopButton;
    @FXML
    Button linkIndicator;
    @FXML
    private CommandPaletteController commandPaletteController;

    private final SettingsStore settings = new SettingsStore(AppPaths.config("acommander.properties"));
    FileOperations fileOps;
    private ArchiveOperations archiveOps;
    private PdfOperations pdfOps;
    private ComboBoxSetup comboBoxSetup;
    private ExternalToolRunner toolRunner;
    private ToolUpdateService toolUpdates;
    private AppRegistry appRegistry;
    private ActionExecutor actionExecutor;
    private final Map<String, String> bookmarks = new LinkedHashMap<>();
    private final Map<String, FtpConnectionOptions> ftpConnections = new LinkedHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(Commander.class);
    public FilesPanesHelper filesPanesHelper;
    private final FileAttributesHelper attributesHelper = new FileAttributesHelper();
    private ThemeMode currentThemeMode = ThemeMode.REGULAR;
    public final Set<KeyCode> activeModifiers = EnumSet.noneOf(KeyCode.class);
    private ExternalProgressController progress;
    private volatile boolean restoreFileListFocusAfterSettingsEdit = false;
    private final Map<FilesPanesHelper.FocusSide, IncrementalFilter> incrementalFilters = new EnumMap<>(Map.of(
            LEFT, new IncrementalFilter(), RIGHT, new IncrementalFilter()));
    private final Map<FilesPanesHelper.FocusSide, Map<String, FolderComparer.Mark>> folderCompareMarks = new EnumMap<>(FilesPanesHelper.FocusSide.class);
    private Popup incrementalFilterPopup;
    private Label incrementalFilterPopupLabel;
    private Popup toastPopup;
    private Label toastLabel;
    private PauseTransition toastHideTransition;
    private ClipboardTransfer.State clipboardTransferState;
    /** The selection being dragged from one of the panes; null when no such drag runs. */
    private ClipboardTransfer.State paneDragState;
    private boolean paneDragSecondary;
    private boolean navigationLinked;

    private KeyBindingManager keyBindingManager;
    private javafx.scene.input.MouseEvent functionButtonClick;


    @FXML
    public void initialize() {
        logger.debug("Loading Properties");
        loadSettings();

        // Configure left & right defaults
        filesPanesHelper = new FilesPanesHelper(leftFileList, leftPathComboBox, rightFileList, rightPathComboBox);
        progress = new ExternalProgressController(externalProgressBox, externalProgressBar, externalProgressLabel, externalStopButton);
        ExternalCommandListener externalCommandListener = buildExternalCommandListener();
        filesPanesHelper.setExternalCommandListener(externalCommandListener);
        appRegistry = loadAppRegistry();
        actionExecutor = new ActionExecutor(this);
        toolRunner = new ExternalToolRunner(() -> Platform.runLater(() -> {
            filesPanesHelper.markArchiveNeedsRepack(filesPanesHelper.getFocusedSide());
            filesPanesHelper.refreshFileListViews();
        }));
        toolRunner.setListener(externalCommandListener);
        fileOps = new FileOperations(appRegistry, toolRunner);
        archiveOps = new ArchiveOperations(appRegistry, toolRunner);
        pdfOps = new PdfOperations(appRegistry, toolRunner);
        configMouseDoubleClick();

        logger.debug("Loading file lists into the double panes file views");
        comboBoxSetup = new ComboBoxSetup();
        comboBoxSetup.setupComboBox(leftPathComboBox);
        comboBoxSetup.setupComboBox(rightPathComboBox);
        filesPanesHelper.setFileListPath(LEFT, resolveInitialPath(LEFT));
        filesPanesHelper.setFileListPath(RIGHT, resolveInitialPath(RIGHT));
        folderCompareMarks.put(LEFT, new HashMap<>());
        folderCompareMarks.put(RIGHT, new HashMap<>());
        leftPathComboBox.valueProperty().addListener((observable, oldValue, newValue) -> onPathChanged(LEFT, newValue));
        rightPathComboBox.valueProperty().addListener((observable, oldValue, newValue) -> onPathChanged(RIGHT, newValue));

        configListViewLookAndBehavior(LEFT, leftFileList);
        configListViewLookAndBehavior(RIGHT, rightFileList);
        configDragAndDrop(LEFT, leftFileList);
        configDragAndDrop(RIGHT, rightFileList);
        configSortHeaders();
        configFileListsFocus();
        configurePaneSummary();
        commandPaletteController.configure(new ActionRegistry(appRegistry, actionExecutor), new ActionContext(this));

        // Setup centralized function button actions mapping and handlers
        setupFunctionButtonActions();

        updateBottomButtons();
        updatePaneSummary(LEFT);
        updatePaneSummary(RIGHT);
        Platform.runLater(() -> leftFileList.requestFocus());
        toolUpdates = new ToolUpdateService(AppPaths.root(), ToolUpdateService.https(), AppVersion.current());
        checkToolUpdatesAtStart();
    }


    /** Each bottom button runs the apps.json action bound to its key (Alt+/Shift+ while held) through ActionExecutor. */
    private void setupFunctionButtonActions() {
        Map<Button, String> keys = new LinkedHashMap<>();
        keys.put(btnF1, "F1");
        keys.put(btnF2, "F2");
        keys.put(btnF3, "F3");
        keys.put(btnF4, "F4");
        keys.put(btnF5, "F5");
        keys.put(btnF6, "F6");
        keys.put(btnDup, "F6");
        keys.put(btnF7, "F7");
        keys.put(btnF8, "F8");
        keys.put(btnF9, "F9");
        keys.put(btnF10, "F10");
        keys.put(btnF11, "F11");
        keys.put(btnF12, "F12");
        keys.forEach((button, key) -> {
            // The release that fires the button has the real modifiers; the tracked key state can miss them.
            button.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, event -> {
                if (button.isArmed()) {
                    functionButtonClick = event;
                }
            });
            button.setOnAction(event -> {
                javafx.scene.input.MouseEvent click = functionButtonClick;
                functionButtonClick = null;
                boolean alt = button == btnDup || (click != null ? click.isAltDown() : activeModifiers.contains(KeyCode.ALT));
                boolean shift = click != null ? click.isShiftDown() : activeModifiers.contains(KeyCode.SHIFT);
                String shortcut = alt ? "Alt+" + key : shift ? "Shift+" + key : key;
                appRegistry.findByShortcut(shortcut)
                        .or(() -> appRegistry.findByShortcut(key))
                        .ifPresent(actionExecutor::execute);
            });
        });
    }

    private ExternalCommandListener buildExternalCommandListener() {
        return new ExternalCommandListener() {
            @Override
            public void onCommandStarted(List<String> command) {
                progress.started(ExternalProgressController.toolName(command));
            }

            @Override
            public void onCommandFinished(List<String> command, int exitCode, Throwable error) {
                progress.finished();
                boolean finishedSettingsEditor = isSettingsEditCommand(command);
                if (error != null || ExternalProgressController.isFailedExit(command, exitCode)) {
                    logger.warn(
                            "External action failed. exitCode={} command={} error={}",
                            exitCode,
                            command == null ? "<null>" : String.join(" ", command),
                            error == null ? "<none>" : error.getMessage()
                    );
                }
                if (!finishedSettingsEditor) {
                    return;
                }
                Platform.runLater(() -> {
                    // Take the user's edits; otherwise the next save writes the old values over them
                    loadSettings();
                    applyTheme(rootPane.getScene(), ThemeMode.from(settings.themeMode()), false);
                    if (restoreFileListFocusAfterSettingsEdit) {
                        restoreFileListFocusAfterSettingsEdit = false;
                        focusCurrentFileList();
                    }
                });
            }

            @Override
            public void onFailure(String title, Throwable error) {
                Platform.runLater(() -> showError(title + " Failed", error.getMessage()));
            }
        };
    }

    @FXML
    public void stopExternalTasks() {
        int stopped = Operation.stopAll();
        logger.info("Stop requested for {} running operation(s)", stopped);
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

        configureSortableHeader(leftNameHeader, () -> onSortHeaderClicked(LEFT, SortColumn.NAME));
        configureSortableHeader(leftSizeHeader, () -> onSortHeaderClicked(LEFT, SortColumn.SIZE));
        configureSortableHeader(leftModifiedHeader, () -> onSortHeaderClicked(LEFT, SortColumn.MODIFIED));

        configureSortableHeader(rightNameHeader, () -> onSortHeaderClicked(RIGHT, SortColumn.NAME));
        configureSortableHeader(rightSizeHeader, () -> onSortHeaderClicked(RIGHT, SortColumn.SIZE));
        configureSortableHeader(rightModifiedHeader, () -> onSortHeaderClicked(RIGHT, SortColumn.MODIFIED));

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

    private void onSortHeaderClicked(FilesPanesHelper.FocusSide side, SortColumn column) {
        filesPanesHelper.toggleSort(side, column);
        updateSortHeaderTexts(side);
    }

    public void sortByName() {
        applySortFromPalette(SortColumn.NAME);
    }

    public void sortBySize() {
        applySortFromPalette(SortColumn.SIZE);
    }

    public void sortByDate() {
        applySortFromPalette(SortColumn.MODIFIED);
    }

    private void applySortFromPalette(SortColumn column) {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        filesPanesHelper.toggleSort(side, column);
        updateSortHeaderTexts(side);
        requestFocusedFileListFocus();
    }

    private void updateSortHeaderTexts(FilesPanesHelper.FocusSide side) {
        SortColumn activeColumn = filesPanesHelper.getSortColumn(side);
        boolean ascending = filesPanesHelper.isSortAscending(side);

        Label nameHeader = side == LEFT ? leftNameHeader : rightNameHeader;
        Label sizeHeader = side == LEFT ? leftSizeHeader : rightSizeHeader;
        Label modifiedHeader = side == LEFT ? leftModifiedHeader : rightModifiedHeader;

        nameHeader.setText("Name" + sortIndicator(activeColumn == SortColumn.NAME, ascending));
        sizeHeader.setText("Size" + sortIndicator(activeColumn == SortColumn.SIZE, ascending));
        modifiedHeader.setText("Modified" + sortIndicator(activeColumn == SortColumn.MODIFIED, ascending));
    }

    private String sortIndicator(boolean active, boolean ascending) {
        if (!active) {
            return "";
        }
        return ascending ? " ▲" : " ▼";
    }

    public void refreshPanesAndDrives() {
        filesPanesHelper.refreshFileListViews();
        comboBoxSetup.refreshDrives(leftPathComboBox);
        comboBoxSetup.refreshDrives(rightPathComboBox);
    }

    private void onPathChanged(FilesPanesHelper.FocusSide side, Folder newValue) {
        if (newValue == null) {
            return;
        }

        String path = newValue.getPath();
        VFileSystem currentFs = filesPanesHelper.getFileSystem(side);
        if (!(currentFs instanceof LocalFileSystem) && isLocalPath(path)) {
            try {
                filesPanesHelper.setFileSystem(side, filesPanesHelper.getVfsManager().createLocalFileSystem(""), path);
            } catch (IOException e) {
                logger.error("Failed to switch back to local file system for path: {}", path, e);
                showError("Navigation Error", "Could not switch to local path: " + path + "\n\n" + e.getMessage());
                return;
            }
        }

        settings.setFolder(side, path);
        saveSettings();
        clearCharFilter(side);
        clearFolderCompareHighlights(false);
        VFileSystem fsAfterSwitch = filesPanesHelper.getFileSystem(side);
        if (fsAfterSwitch instanceof LocalFileSystem) {
            // setFileListPath sets the combo too; that echo must not list the folder a second time
            if (fsAfterSwitch != currentFs || !filesPanesHelper.isAt(side, path)) {
                filesPanesHelper.setFileListPath(side, path);
            }
        } else {
            filesPanesHelper.refreshFileListView(side);
        }
    }

    /** Setup all of the keyboard bindings */
    public void setupBindings() {
        Scene scene = rootPane.getScene();
        this.keyBindingManager = new KeyBindingManager(this, appRegistry, actionExecutor);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            this.keyBindingManager.setCurrentContext(determineCurrentContext(scene));
            this.keyBindingManager.handleKeyEvent(event);
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            this.keyBindingManager.setCurrentContext(determineCurrentContext(scene));
            this.keyBindingManager.handleReleasedKeyEvent(event);
        });

        // Clear tracked modifier state on window focus changes to avoid "stuck" modifiers
        // (e.g., Alt consumed by OS when Alt+Tab switches windows).
        Window window = scene.getWindow();
        if (window != null) {
            window.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    // Window lost focus - clear any tracked modifiers and update UI
                    activeModifiers.clear();
                    updateBottomButtons();
                } else {
                    // Window gained focus - ensure UI reflects current tracked state
                    updateBottomButtons();
                }
            });
        }
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
        applyTheme(scene, ThemeMode.from(settings.themeMode()), false);
    }

    private void setDarkMode() {
        applyTheme(rootPane.getScene(), ThemeMode.DARK, true);
    }

    private void setRegularMode() {
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

    /** Reads the settings file into the bookmark and FTP maps; an unreadable file is reported, not fatal. */
    private void loadSettings() {
        try {
            settings.load();
        } catch (IOException e) {
            logger.error("Failed reading settings {}", settings.file(), e);
            Platform.runLater(() -> showError("Settings Not Read", e.getMessage()));
        }
        bookmarks.clear();
        bookmarks.putAll(settings.bookmarks());
        ftpConnections.clear();
        ftpConnections.putAll(settings.ftpConnections());
        if (settings.hasPlainFtpPasswords()) {
            saveSettings();
        }
    }

    private Path getConfigFilePath() {
        return settings.file();
    }

    private String resolveInitialPath(FilesPanesHelper.FocusSide side) {
        String configuredPath = settings.folder(side);
        if (configuredPath != null && new File(configuredPath).exists())
            return configuredPath;
        return getDefaultRootPath();
    }

    private AppRegistry loadAppRegistry() {
        Path appConfig = AppPaths.config("apps.json");
        try {
            return new AppRegistry(new AppConfigLoader().load(appConfig));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed loading actions config from: " + appConfig, ex);
        }
    }

    public String getDefaultRootPath() {
        File[] roots = File.listRoots();
        if (roots != null && roots.length > 0)
            return roots[0].getPath();
        return System.getProperty("user.home");
    }

    private void saveSettings() {
        settings.setBookmarks(bookmarks);
        try {
            settings.setFtpConnections(ftpConnections);
        } catch (IOException ex) {
            logger.error("Failed encrypting FTP passwords", ex);
            showToast("FTP connections not saved: " + ex.getMessage());
        }
        try {
            settings.save();
        } catch (IOException ex) {
            logger.error("Failed saving settings {}", settings.file(), ex);
            showToast("Settings not saved: " + ex.getMessage());
        }
    }

    public void persistCurrentPaths() {
        if (filesPanesHelper == null)
            return;

        // Save the archive file path (not temp folder) if currently in an archive
        settings.setFolder(LEFT, getPersistPath(LEFT));
        settings.setFolder(RIGHT, getPersistPath(RIGHT));
        settings.setThemeMode(currentThemeMode.configValue);
        saveSettings();
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

                setOnDragDetected(event -> {
                    if (!isEmpty()) {
                        startPaneDrag(side, listView, this, event.isSecondaryButtonDown());
                    }
                    event.consume();
                });
                setOnDragOver(event -> {
                    if (isDropRow(getItem())) {
                        if (dropTarget(event, side, getItem()) != null) {
                            event.acceptTransferModes(TransferMode.COPY);
                        }
                        event.consume();
                    }
                });
                setOnDragEntered(event -> {
                    if (isDropRow(getItem()) && dropTarget(event, side, getItem()) != null) {
                        getStyleClass().add("drop-target");
                    }
                });
                setOnDragExited(event -> getStyleClass().removeAll("drop-target"));
                setOnDragDropped(event -> {
                    if (isDropRow(getItem())) {
                        dropOnPane(event, side, dropTarget(event, side, getItem()));
                        event.consume();
                    }
                });
            }

            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("compare-left-only", "compare-right-only", "compare-different", "drop-target");
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    FileIcons.Icon iconSpec = FileIcons.of(item);
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

    /** Drops on the rest of the pane go into its folder; folder rows take them first (see the cell factory). */
    private void configDragAndDrop(FilesPanesHelper.FocusSide side, ListView<FileItem> listView) {
        listView.setOnDragOver(event -> {
            if (dropTarget(event, side, null) != null) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        listView.setOnDragDropped(event -> {
            dropOnPane(event, side, dropTarget(event, side, null));
            event.consume();
        });
        listView.setOnDragDone(event -> {
            paneDragState = null;
            if (event.getTransferMode() == TransferMode.MOVE) {
                filesPanesHelper.refreshFileListViews();
            }
            event.consume();
        });
    }

    private static boolean isDropRow(FileItem item) {
        return item != null && item.isDirectory() && !"..".equals(item.getPresentableFilename());
    }

    // A plain drag offers only copy, so a drop into Explorer on the same drive can't move the files.
    private void startPaneDrag(FilesPanesHelper.FocusSide side, ListView<FileItem> listView, ListCell<FileItem> cell,
                               boolean secondary) {
        List<FileItem> items = fileOps.filterValidItems(new ArrayList<>(listView.getSelectionModel().getSelectedItems()));
        if (items.isEmpty()) {
            return;
        }
        VFileSystem fs = filesPanesHelper.getFileSystem(side);
        ClipboardTransfer.State state = ClipboardTransfer.capture(items, false, side, fs, filesPanesHelper.getPath(side));
        List<File> files;
        try {
            // ponytail: FTP files download at drag start (JavaFX can't fetch on drop), blocking the UI, until exit.
            files = fs instanceof FtpFileSystem
                    ? PaneDragDrop.download(state, AppTempDir.createTempDirectory("drag_"))
                    : PaneDragDrop.filesOnDisk(items);
        } catch (IOException e) {
            error("Drag failed", e);
            return;
        }
        Dragboard dragboard = listView.startDragAndDrop(secondary && PaneDragDrop.allowsMoveOut(fs)
                ? TransferMode.COPY_OR_MOVE : new TransferMode[]{TransferMode.COPY});
        ClipboardContent content = new ClipboardContent();
        content.putFiles(files);
        dragboard.setContent(content);
        dragboard.setDragView(cell.snapshot(null, null));
        paneDragState = state;
        paneDragSecondary = secondary;
    }

    /** The folder a drop on {@code row} (null: the pane itself) goes into, or null when it can't go there. */
    private String dropTarget(DragEvent event, FilesPanesHelper.FocusSide side, FileItem row) {
        VFileSystem fs = filesPanesHelper.getFileSystem(side);
        if (fs == null || fs.isReadOnly()) {
            return null;
        }
        Object source = event.getGestureSource();
        boolean fromPane = source == leftFileList || source == rightFileList;
        if (fromPane ? paneDragState == null : source != null || !event.getDragboard().hasFiles()) {
            return null;
        }
        String paneFolder = filesPanesHelper.getPath(side);
        if (row == null) {
            return source == (side == LEFT ? leftFileList : rightFileList) ? null : paneFolder;
        }
        if (fromPane && PaneDragDrop.isDraggedFolder(paneDragState, fs, paneFolder, row)) {
            return null;
        }
        return PaneDragDrop.folderOf(fs, row);
    }

    // Other apps' files are always copied: a source told "moved" may delete them while the copy still runs.
    private void dropOnPane(DragEvent event, FilesPanesHelper.FocusSide side, String folder) {
        if (folder == null) {
            event.setDropCompleted(false);
            return;
        }
        ClipboardTransfer.State dragged = paneDragState;
        Object source = event.getGestureSource();
        event.setDropCompleted(true);
        if (source != leftFileList && source != rightFileList) {
            transfer("Copy", PaneDragDrop.fromDroppedFiles(event.getDragboard().getFiles(), side), side, folder, result -> {});
            return;
        }
        if (!paneDragSecondary) {
            transfer("Copy", dragged, side, folder, result -> {});
            return;
        }
        MenuItem copy = new MenuItem("Copy Here");
        copy.setOnAction(e -> transfer("Copy", dragged, side, folder, result -> {}));
        MenuItem move = new MenuItem("Move Here");
        move.setDisable(dragged.sourceFs().isReadOnly());
        move.setOnAction(e -> transfer("Move", new ClipboardTransfer.State(dragged.entries(), true, dragged.sourceSide(),
                dragged.sourceFs(), dragged.sourceFolder()), side, folder, result -> {}));
        ContextMenu menu = new ContextMenu(copy, move, new SeparatorMenuItem(), new MenuItem("Cancel"));
        Node anchor = side == LEFT ? leftFileList : rightFileList;
        double x = event.getScreenX();
        double y = event.getScreenY();
        Platform.runLater(() -> menu.show(anchor, x, y));
    }

    private void applyFolderCompareStyle(FilesPanesHelper.FocusSide side, FileItem item, ListCell<FileItem> cell) {
        if (item == null || item.getPath() == null || "..".equals(item.getPresentableFilename())) {
            return;
        }
        Map<String, FolderComparer.Mark> marks = folderCompareMarks.get(side);
        if (marks == null || marks.isEmpty()) {
            return;
        }
        FolderComparer.Mark mark = marks.get(FolderComparer.key(item.getPath()));
        if (mark == null) {
            return;
        }
        switch (mark) {
            case LEFT_ONLY -> cell.getStyleClass().add("compare-left-only");
            case RIGHT_ONLY -> cell.getStyleClass().add("compare-right-only");
            case DIFFERENT -> cell.getStyleClass().add("compare-different");
        }
    }

    private void configFileListsFocus() {
        logger.debug("Configure focus setting (so we will know where focus was last been)");
        leftFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                filesPanesHelper.setFocusedFileList(LEFT);
                if (isCommandPaletteOpen()) {
                    commandPaletteController.refresh();
                }
            }
        });
        rightFileList.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                filesPanesHelper.setFocusedFileList(RIGHT);
                if (isCommandPaletteOpen()) {
                    commandPaletteController.refresh();
                }
            }
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
        listView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<FileItem>) change -> {
            updatePaneSummary(side);
            if (isCommandPaletteOpen()) {
                commandPaletteController.refresh();
            }
        });
    }

    private void updatePaneSummary(FilesPanesHelper.FocusSide side) {
        Label summaryLabel = side == LEFT ? leftPaneSummaryLabel : rightPaneSummaryLabel;
        if (summaryLabel == null) {
            return;
        }

        ListView<FileItem> listView = side == LEFT ? leftFileList : rightFileList;
        int fileCount = (int) listView.getItems().stream()
                .filter(item -> item != null && !"..".equals(item.getPresentableFilename()) && !item.isDirectory())
                .count();

        long totalFilesSize = listView.getItems().stream()
                .filter(item -> item != null && !"..".equals(item.getPresentableFilename()) && !item.isDirectory())
                .mapToLong(FileItem::getSizeInBytes)
                .sum();

        long selectedFilesSize = listView.getSelectionModel().getSelectedItems().stream()
                .filter(item -> item != null && !"..".equals(item.getPresentableFilename()) && !item.isDirectory())
                .mapToLong(FileItem::getSizeInBytes)
                .sum();
        int selectedFileCount = (int) listView.getSelectionModel().getSelectedItems().stream()
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
        if ("..".equals(selectedItem.getPresentableFilename())) {
            goUpOneFolder();
            return;
        }

        // Handle archive navigation
        FilesPanesHelper.FocusSide focusedSide = filesPanesHelper.getFocusedSide();
        if (filesPanesHelper.isInArchive(focusedSide)) {
            handleArchiveEnter(selectedItem);
            return;
        }

        // Handle FTP navigation
        VFileSystem fs = filesPanesHelper.getFileSystem(focusedSide);
        if (fs instanceof FtpFileSystem ftpFs) {
            if (selectedItem.isDirectory()) {
                filesPanesHelper.setFileListPath(focusedSide, ftpFs.getInternalPath(selectedItem));
            }
            return;
        }

        if (selectedItem.isDirectory()) {
            filesPanesHelper.setFocusedFileListPath(selectedItem.getFullPath());
            followInOtherPane(selectedItem.getName());
        } else {
            // It's a file - check if it's an archive we can enter
            String extension = selectedItem.extension();

            if (ArchiveMode.isSupportedExtension(extension)) {
                enterArchiveFile(focusedSide, selectedItem);
            } else if (FileIcons.isExecutableExtension(extension)) {
                try {
                    List<String> command = switch (extension) {
                        // ShellExecute, as Explorer does; cmd.exe /c would run any & or %VAR% in the name
                        case "bat", "cmd" -> List.of("rundll32.exe", "shell32.dll,ShellExec_RunDLL", selectedItem.getFullPath());
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
                openFileWithSystemDefault(selectedItem, false);
            }
        }
    }
    
    /**
     * Handles ENTER key when inside an archive.
     */
    private void handleArchiveEnter(FileItem selectedItem) {
        FilesPanesHelper.FocusSide focusedSide = filesPanesHelper.getFocusedSide();

        if (selectedItem.isDirectory()) {
            runWithProgress("VFS: Entering " + selectedItem.getName(),
                    () -> filesPanesHelper.enterArchiveSubdirectory(focusedSide, selectedItem.getName()),
                    this::focusCurrentFileList,
                    "Failed to enter archive subdirectory: " + selectedItem.getName());
        } else if (ArchiveMode.isSupportedExtension(selectedItem.extension())) {
            enterArchiveFile(focusedSide, selectedItem);
        } else {
            // It's a file - open it with default viewer
            openFileWithSystemDefault(selectedItem, true);
        }
    }

    /** Opens an archive file as a folder; inside an archive it opens nested, and leaving it goes back there. */
    private void enterArchiveFile(FilesPanesHelper.FocusSide side, FileItem archive) {
        runWithProgress("VFS: Opening " + archive.getName(),
                () -> filesPanesHelper.enterArchive(side, archive.getFullPath()),
                this::focusCurrentFileList,
                "Failed to enter archive: " + archive.getName());
    }

    private void openFileWithSystemDefault(FileItem selectedItem, boolean fromArchive) {
        File file = selectedItem.getPath().toFile();
        String fullPath = selectedItem.getFullPath();
        try {
            getDesktop().open(file);
            return;
        } catch (Exception desktopEx) {
            logger.warn("Desktop.open failed for {}, trying Windows shell fallback", selectedItem.getName(), desktopEx);
        }

        try {
            // Shell fallback that uses the current Windows file association (Desktop.open refuses executables).
            ProcessRunner.of("rundll32.exe", "shell32.dll,ShellExec_RunDLL", fullPath).launch();
        } catch (IOException shellEx) {
            logger.error("Failed opening{}: {}", fromArchive ? " file in archive" : "", selectedItem.getName(), shellEx);
        }
    }
    
    /** Backspace or "..": goes up one folder; at an FTP root disconnects, at an archive root leaves the archive. */
    public void goUpOneFolder() {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        String currentPath = filesPanesHelper.getPath(side);
        if (filesPanesHelper.getFileSystem(side) instanceof FtpFileSystem ftpFs) {
            if ("/".equals(currentPath) || currentPath.isEmpty()) {
                ftpDisconnect();
            } else {
                filesPanesHelper.setFileListPath(side, ftpFs.getParent(currentPath), leafName(currentPath));
            }
            return;
        }
        if (filesPanesHelper.isInArchive(side)) {
            goUpInArchive();
            return;
        }
        File parent = new File(currentPath).getParentFile();
        if (parent != null) {
            filesPanesHelper.setFileListPath(side, parent.getAbsolutePath(), new File(currentPath).getName());
            followInOtherPane(null);
        }
    }

    public void linkNavigation() {
        setNavigationLinked(true);
        showToast("Navigation linked: Enter and Backspace now move both panes.");
    }

    @FXML
    public void unlinkNavigation() {
        setNavigationLinked(false);
        showToast("Navigation unlinked.");
    }

    public boolean isNavigationLinked() {
        return navigationLinked;
    }

    private void setNavigationLinked(boolean linked) {
        navigationLinked = linked;
        linkIndicator.setVisible(linked);
        linkIndicator.setManaged(linked);
    }

    /** Linked navigation: repeats the focused pane's folder step (into {@code intoName}, or up when null) in the other pane. */
    private void followInOtherPane(String intoName) {
        FilesPanesHelper.FocusSide other = otherSide(filesPanesHelper.getFocusedSide());
        if (!navigationLinked || !(filesPanesHelper.getFileSystem(other) instanceof LocalFileSystem)) {
            return;
        }
        String otherPath = filesPanesHelper.getPath(other);
        LinkedNavigation.target(otherPath, intoName).ifPresentOrElse(
                target -> filesPanesHelper.setFileListPath(other, target, intoName == null ? leafName(otherPath) : null),
                () -> {
                    if (intoName != null) {
                        showToast("The other pane has no folder named " + intoName + ".");
                    }
                });
    }

    /** Goes up one level inside an archive; at its root, leaves it (repacking if it changed). */
    public void goUpInArchive() {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        runWithProgress("VFS: Navigating up",
                () -> filesPanesHelper.goUpInArchive(side),
                this::focusCurrentFileList,
                "Failed to leave the archive folder");
    }

    /** Runs {@code work} on the background executor behind the progress bar; {@code onSuccess} then runs on the FX thread. */
    private void runWithProgress(String label, Work work, Runnable onSuccess, String failureMessage) {
        runWithProgress(label, () -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run(), failureMessage);
    }

    private interface Work {
        void run() throws Exception;
    }

    /** Like the Runnable form, but hands {@code work}'s result to {@code onSuccess}. Failures are logged and shown. */
    private <T> void runWithProgress(String label, Callable<T> work, Consumer<T> onSuccess, String failureMessage) {
        progress.run(label, work, onSuccess, cause -> {
            if (Operation.isStop(cause)) {
                showToast("Stopped: " + label);
                return;
            }
            error(failureMessage, cause instanceof Exception e ? e : new RuntimeException(cause));
        });
    }

    /**
     * Runs file work, whose items and file systems were read on the FX thread, in the background behind the progress
     * bar. Archives in {@code uses} stay open until it ends; then, if {@code refresh}, both panes refresh once, and
     * {@code onDone} or the error dialog runs on the FX thread.
     */
    private <T> void runFileOperation(String title, List<VFileSystem> uses, boolean refresh, Callable<T> work, Consumer<T> onDone) {
        List<ArchiveSession> held = uses.stream()
                .filter(ArchiveFileSystem.class::isInstance)
                .map(fs -> ((ArchiveFileSystem) fs).getSession())
                .toList();
        held.forEach(ArchiveSession::acquire);
        progress.run(title, () -> {
            try {
                return work.call();
            } finally {
                held.forEach(ArchiveSession::release);
            }
        }, result -> {
            if (refresh) {
                filesPanesHelper.refreshFileListViews();
            }
            onDone.accept(result);
        }, failure -> {
            if (refresh) {
                filesPanesHelper.refreshFileListViews();
            }
            Throwable cause = unwrapCompletionException(failure);
            if (Operation.isStop(cause)) {
                showToast("Stopped: " + title);
                return;
            }
            error(title + " failed", cause instanceof Exception e ? e : new RuntimeException(cause));
        });
    }

    /**
     * F5, F6, Alt+F6 and Ctrl+V: what to transfer was captured from the source pane; the target is read now, then the
     * work runs in the background. {@code onDone} gets the result after the panes refreshed.
     */
    private void transfer(String title, ClipboardTransfer.State source, FilesPanesHelper.FocusSide targetSide,
                          Consumer<ClipboardTransfer.PasteResult> onDone) {
        transfer(title, source, targetSide, filesPanesHelper.getPath(targetSide), onDone);
    }

    /** Into {@code targetFolder} on the target pane's file system: its folder or a folder row in it. */
    private void transfer(String title, ClipboardTransfer.State source, FilesPanesHelper.FocusSide targetSide,
                          String targetFolder, Consumer<ClipboardTransfer.PasteResult> onDone) {
        VFileSystem targetFs = filesPanesHelper.getFileSystem(targetSide);
        if (source.entries().isEmpty() || targetFs == null) {
            return;
        }
        if (source.cut() && ClipboardTransfer.isSameFolder(source.sourceFs(), targetFs, source.sourceFolder(), targetFolder)) {
            showToast("Cannot move items into the same folder");
            return;
        }
        if (ClipboardTransfer.isSameFolder(source.sourceFs(), targetFs, source.sourceFolder(), targetFolder)) {
            runTransfer(title, source, targetSide, targetFs, targetFolder, TransferConflicts.Policy.OVERWRITE, List.of(), onDone);
            return;
        }
        runFileOperation(title, List.of(source.sourceFs(), targetFs), false,
                () -> TransferConflicts.find(source, targetFs, targetFolder),
                conflicts -> {
                    if (conflicts.isEmpty()) {
                        runTransfer(title, source, targetSide, targetFs, targetFolder, TransferConflicts.Policy.OVERWRITE, conflicts, onDone);
                        return;
                    }
                    OverwriteDialog.show(dialogOwner(), currentThemeMode.styleClass, title, targetFolder, conflicts)
                            .ifPresent(policy -> runTransfer(title, source, targetSide, targetFs, targetFolder, policy, conflicts, onDone));
                });
    }

    private void runTransfer(String title, ClipboardTransfer.State source, FilesPanesHelper.FocusSide targetSide,
                             VFileSystem targetFs, String targetFolder, TransferConflicts.Policy policy,
                             List<TransferConflicts.Conflict> conflicts, Consumer<ClipboardTransfer.PasteResult> onDone) {
        runFileOperation(title, List.of(source.sourceFs(), targetFs), true,
                () -> fileOps.transfer(source, targetFs, targetFolder, policy, conflicts),
                result -> {
                    filesPanesHelper.selectNames(targetSide, targetFolder,
                            result.pasted().stream().map(ClipboardTransfer.Entry::name).toList());
                    if (!result.failed().isEmpty()) {
                        String names = result.failed().stream().map(ClipboardTransfer.Entry::name).collect(java.util.stream.Collectors.joining(", "));
                        String cause = result.firstFailure() == null ? "" : "\n\n" + result.firstFailure().getMessage();
                        showError(title, "Failed for " + result.failed().size() + " of " + source.entries().size()
                                + " item(s): " + names + cause);
                    }
                    onDone.accept(result);
                });
    }

    private ClipboardTransfer.State captureSelection(boolean cut) {
        return capture(new ArrayList<>(filesPanesHelper.getSelectedItems()), cut);
    }

    private ClipboardTransfer.State capture(List<FileItem> items, boolean cut) {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        return ClipboardTransfer.capture(items, cut, side, filesPanesHelper.getFileSystem(side), filesPanesHelper.getPath(side));
    }

    private static FilesPanesHelper.FocusSide otherSide(FilesPanesHelper.FocusSide side) {
        return side == LEFT ? RIGHT : LEFT;
    }

    private String leafName(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path;
        while (normalized.length() > 1 && (normalized.endsWith("/") || normalized.endsWith("\\"))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    @FXML
    public void help() {
        logger.info("Help (F1)");
        HelpDialog.show(dialogOwner(), currentThemeMode.styleClass, "A Commander " + AppVersion.current() + " Help",
                HelpTopics.entries(appRegistry.actions(), appRegistry.tools()), this::about);
    }

    public void about() {
        logger.info("About");
        AboutDialog.show(dialogOwner(), currentThemeMode.styleClass, AppVersion.current(), url -> openInBrowser("About", url));
    }

    /** Opens the URL in the browser; on failure shows it so the user can copy it. */
    private boolean openInBrowser(String title, String url) {
        try {
            getDesktop().browse(java.net.URI.create(url));
            return true;
        } catch (Exception e) {
            logger.warn("Failed opening URL in browser: {}", url, e);
            showError(title, "Could not open the browser automatically.\nCopy and open this URL:\n" + url);
            return false;
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
            restoreFileListFocusAfterSettingsEdit = true;
            fileOps.edit(new LocalFileSystem(""), new ClipboardTransfer.Entry(configFile.getFileName().toString(), false, configFile.toString()));
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
            List<FileItem> selectedItems = fileOps.filterValidItems(filesPanesHelper.getSelectedItems());
            if (selectedItems.isEmpty())
                return;
            if (selectedItems.size() == 1) {
                FileItem selectedItem = selectedItems.getFirst();
                Optional<String> result = getUserFeedback(
                        selectedItem.getName(),
                        "File Rename",
                        "New name",
                        getRenameSelectionEnd(selectedItem)
                );
                if (result.isPresent()) { // if user dismisses the dialog it won't rename...
                    String newName = result.get();
                    ClipboardTransfer.State source = captureSelection(false);
                    runFileOperation("Rename", List.of(source.sourceFs()), true, () -> {
                        fileOps.rename(source.sourceFs(), source.entries().getFirst(), newName);
                        return null;
                    }, ignored -> filesPanesHelper.selectNames(source.sourceSide(), source.sourceFolder(), List.of(newName)));
                }
            } else // Multi files selected (multi rename)
                fileOps.multiRename(selectedItems);
        } catch (Exception e) {
            error("Failed Renaming file/s", e);
        }
    }

    @FXML
    public void viewFile() {
        logger.info("View (F3)");
        FileItem item = filesPanesHelper.getCursorItem();
        if (item == null) {
            return;
        }
        if (item.isDirectory()) {
            calculateDirSpace(item);
            return;
        }
        ClipboardTransfer.State source = capture(List.of(item), false);
        runFileOperation("View", List.of(source.sourceFs()), false, () -> {
            for (ClipboardTransfer.Entry entry : source.entries()) {
                fileOps.view(source.sourceFs(), entry);
            }
            return null;
        }, ignored -> {});
    }

    private void calculateDirSpace(FileItem selectedItem) {
        logger.info("calculateDirSpace (F3 (on folder))");
        Path folder = selectedItem.getPath();
        runWithProgress("Calculating size of " + selectedItem.getName(),
                () -> FileHelper.folderSize(folder),
                size -> {
                    selectedItem.setSize(size);
                    filesPanesHelper.getFileList(true).refresh();
                },
                "Failed calculating folder size");
    }

    @FXML
    public void editFile() {
        logger.info("Edit (F4)");
        FileItem item = filesPanesHelper.getCursorItem();
        if (item == null) {
            return;
        }
        List<FileItem> items = fileOps.filterValidItems(List.of(item));
        ClipboardTransfer.State source = capture(List.of(item), false);
        runFileOperation("Edit", List.of(source.sourceFs()), false, () -> {
            List<String> binary = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (FileHelper.isTextFile(items.get(i), source.sourceFs())) {
                    fileOps.edit(source.sourceFs(), source.entries().get(i));
                } else {
                    binary.add(items.get(i).getName());
                }
            }
            return binary;
        }, binary -> {
            if (!binary.isEmpty()) {
                showError("Edit File", "Cannot edit binary file: " + String.join(", ", binary));
            }
        });
    }

    @FXML
    public void copyFile() {
        logger.info("Copy (F5)");
        ClipboardTransfer.State source = captureSelection(false);
        transfer("Copy", source, otherSide(source.sourceSide()), result -> {});
    }

    /** Alt+F6: copies the selection into its own folder under duplicate names. */
    @FXML
    public void duplicateFile() {
        logger.info("Duplicate");
        ClipboardTransfer.State source = captureSelection(false);
        transfer("Duplicate", source, source.sourceSide(), result -> {});
    }

    @FXML
    public void moveFile() {
        logger.info("Move (F6)");
        ClipboardTransfer.State source = captureSelection(true);
        int nextIndex = Math.max(filesPanesHelper.getFileList(true).getSelectionModel().getSelectedIndex(), 0);
        transfer("Move", source, otherSide(source.sourceSide()),
                result -> filesPanesHelper.selectIndex(source.sourceSide(), source.sourceFolder(), nextIndex));
    }

    @FXML
    public void makeDirectory() {
        logger.info("Create Directory (F7)");
        getUserFeedback("", "Make Directory", "New Directory Name").ifPresent(name ->
                createInFocusedPane("Create Directory", name, (fs, folder) -> fileOps.mkdir(fs, folder, name)));
    }

    public void makeFile() {
        logger.info("Create File (ALT+F7)");
        getUserFeedback("", "Make File", "New File Name").ifPresent(name ->
                createInFocusedPane("Create File", name, (fs, folder) -> fileOps.mkFile(fs, folder, name)));
    }

    private interface FolderWork {
        void run(VFileSystem fs, String folder) throws IOException;
    }

    private void createInFocusedPane(String title, String name, FolderWork work) {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        VFileSystem fs = filesPanesHelper.getFileSystem(side);
        String folder = filesPanesHelper.getPath(side);
        runFileOperation(title, List.of(fs), true, () -> {
            work.run(fs, folder);
            return null;
        }, ignored -> filesPanesHelper.selectNames(side, folder, List.of(name)));
    }

    @FXML
    public void deleteFile() {
        logger.info("Delete (F8/DEL)");
        ClipboardTransfer.State source = captureSelection(false);
        if (source.entries().isEmpty()) {
            return;
        }
        int previousIndex = filesPanesHelper.getFileList(true).getSelectionModel().getSelectedIndex();
        runFileOperation("Delete", List.of(source.sourceFs()), true,
                () -> fileOps.delete(source.sourceFs(), source.entries()),
                failed -> {
                    filesPanesHelper.selectIndex(source.sourceSide(), source.sourceFolder(), previousIndex - 1);
                    if (failed.isEmpty()) {
                        return;
                    }
                    if (source.sourceFs() instanceof LocalFileSystem) {
                        deleteLocked(source.sourceFs(), failed);
                    } else {
                        showError("Delete", "Could not delete: " + failed.stream()
                                .map(ClipboardTransfer.Entry::name).collect(java.util.stream.Collectors.joining(", ")));
                    }
                });
    }

    /** Local items a delete left: finds the programs holding them open and, if the user agrees, ends them and retries. */
    private void deleteLocked(VFileSystem fs, List<ClipboardTransfer.Entry> locked) {
        runFileOperation("Find Programs Using the Items", List.of(), false,
                () -> LockingPrograms.find(locked.stream().map(ClipboardTransfer.Entry::sourceInternalPath).toList()),
                lockers -> {
                    if (lockers.isEmpty()) {
                        showError("Delete", FileOperations.notLockedMessage(locked));
                        return;
                    }
                    List<String> names = locked.stream().map(ClipboardTransfer.Entry::name).toList();
                    if (LockedItemsDialog.show(dialogOwner(), currentThemeMode.styleClass, names, lockers).isEmpty()) {
                        return;
                    }
                    runFileOperation("Delete", List.of(fs), true, () -> fileOps.endLockersAndDelete(fs, locked, lockers),
                            result -> {
                                if (!result.failed().isEmpty()) {
                                    showError("Delete", result.message());
                                }
                            });
                });
    }

    public void deleteWipe() {
        logger.info("Delete & Wipe (Shift+F8/DEL)");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (selectedItems.isEmpty()) {
            return;
        }
        int previousIndex = filesPanesHelper.getFileList(true).getSelectionModel().getSelectedIndex();
        fileOps.wipeDelete(selectedItems);
        filesPanesHelper.selectIndex(filesPanesHelper.getFocusedSide(), filesPanesHelper.getFocusedPath(), previousIndex - 1);
    }

    @FXML
    public void terminalHere() {
        logger.info("Open Terminal Here (F9)");
        String openHerePath = filesPanesHelper.getFocusedPath();
        try {
            fileOps.openTerminal(openHerePath);
        } catch (Exception ex) {
            error("Failed starting command line shell here: " + openHerePath, ex);
        }
    }

    public void explorerHere() {
        logger.info("Open Explorer Here (ALT+F9)");
        String openHerePath = filesPanesHelper.getFocusedPath();
        try {
            fileOps.openExplorer(openHerePath);
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
            String pattern = result.get().contains("*") ? result.get() : "*" + result.get() + "*";
            // ripgrep exits 1 when nothing matched and 2 when some folder could not be read
            runExternal(BundledToolCommands.findByName(BundledTool.RIPGREP.path(), searchFromPath, pattern), false, Set.of(1, 2))
                    .thenAccept(output -> Platform.runLater(() ->
                            pickFoundFile("Search", BundledToolCommands.foundFiles(output, searchFromPath))))
                    .exceptionally(throwable -> {
                        Platform.runLater(() -> showError("Search", "Failed searching for: " + result.get() + "\n" + throwable.getMessage()));
                        return null;
                    });
        }
    }

    /** Lists the files a search found and goes to the one the user picks. */
    private void pickFoundFile(String title, List<String> files) {
        if (files.isEmpty()) {
            showInfo(title, "No files found :-(");
            return;
        }
        FoundFilesDialog.show(dialogOwner(), currentThemeMode.styleClass, files).ifPresent(selectedFile -> {
            showLocalFolder(filesPanesHelper.getFocusedSide(), selectedFile.getPath().getParent().toString(), title);
            filesPanesHelper.selectFileItem(true, selectedFile);
            requestFocusedFileListFocus();
        });
    }

    public void findInFiles() {
        logger.info("Find in Files (ALT+F10)");
        String sourcePath = filesPanesHelper.getFocusedPath();
        Optional<FindInFilesOptions> options = FindInFilesDialog.show(dialogOwner(), currentThemeMode.styleClass, sourcePath);
        if (options.isEmpty()) {
            return;
        }
        Path rgPath = BundledTool.RIPGREP.path();
        if (!Files.isRegularFile(rgPath)) {
            showError("Find in Files", "Ripgrep executable was not found at: " + rgPath);
            return;
        }

        runExternal(BundledToolCommands.findInFiles(rgPath, sourcePath, options.get()), false, Set.of(1, 2))
                .thenAccept(output -> Platform.runLater(() ->
                        pickFoundFile("Find in Files", BundledToolCommands.foundFiles(output, sourcePath))))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> showError("Find in Files", "Failed running ripgrep: " + throwable.getMessage()));
                    return null;
                });
    }

    @FXML
    public void pack() {
        logger.info("Pack (F11)");
        ClipboardTransfer.State source = captureSelection(false);
        if (source.entries().isEmpty()) {
            return;
        }
        String firstFilename = source.entries().getFirst().name();
        String zipFilename = firstFilename.contains(".")
                ? firstFilename.substring(0, firstFilename.lastIndexOf('.')) + ".zip"
                : firstFilename + ".zip";
        getUserFeedback(zipFilename, "Pack to zip", "Zip filename").ifPresent(archiveName -> {
            FilesPanesHelper.FocusSide targetSide = otherSide(source.sourceSide());
            VFileSystem targetFs = filesPanesHelper.getFileSystem(targetSide);
            String targetFolder = filesPanesHelper.getPath(targetSide);
            runFileOperation("Pack", List.of(source.sourceFs(), targetFs), true, () -> {
                archiveOps.pack(source.sourceFs(), source.entries(), targetFs, targetFolder, archiveName);
                return null;
            }, ignored -> filesPanesHelper.selectNames(targetSide, targetFolder, List.of(archiveName)));
        });
    }

    public void splitLargeFile() {
        logger.info("Split Large File (ALT+F11)");
        try {
            List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
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

            long originalFileSize = selectedItem.getSizeInBytes();
            Optional<String> splitArg = SplitSizeDialog.show(dialogOwner(), currentThemeMode.styleClass,
                    selectedItem.getName(), originalFileSize);
            if (splitArg.isEmpty()) {
                logger.info("User cancelled split");
                return;
            }

            String outputFilename = buildSplitArchiveName(selectedItem.getName());
            String targetFolder = filesPanesHelper.getUnfocusedPath();
            String outputArchivePath = targetFolder + "\\" + outputFilename;
            String sevenZipPath = BundledTool.SEVEN_ZIP.path().toString();

            List<String> command = List.of(
                    sevenZipPath,
                    "a",
                    outputArchivePath,
                    selectedItem.getFullPath(),
                    "-mx0",
                    "-v" + splitArg.get()
            );

            runExternalReported(command, true, "Split File");
            filesPanesHelper.selectFileItem(false, targetFolder, outputFilename);
        } catch (Exception ex) {
            error("Failed splitting large file", ex);
        }
    }

    public void convertGraphicsFiles() {
        logger.info("Convert Graphics Files");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (!ImageConversionSupport.areAllConvertibleImages(selectedItems)) {
            showError("Convert Graphics Files", "Select one or more image files only.");
            requestFocusedFileListFocus();
            return;
        }

        List<String> targetFormats = ImageConversionSupport.targetFormatsForSelection(selectedItems);
        if (targetFormats.isEmpty()) {
            showError("Convert Graphics Files", "No supported target formats were found for the selected files.");
            requestFocusedFileListFocus();
            return;
        }
        Optional<ImageConversionRequest> request = ImageConversionDialog.show(dialogOwner(), currentThemeMode.styleClass,
                selectedItems.size(), filesPanesHelper.getUnfocusedPath(), targetFormats);
        if (request.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }

        Path caesiumPath = BundledTool.CAESIUM.path();
        if (!Files.isRegularFile(caesiumPath)) {
            showError("Convert Graphics Files", "caesiumclt executable was not found at: " + caesiumPath);
            requestFocusedFileListFocus();
            return;
        }

        String outputFolder = filesPanesHelper.getUnfocusedPath();
        ImageConversionRequest options = request.get();
        CompletableFuture<List<String>> runs = CompletableFuture.completedFuture(List.of());
        for (List<String> command : ImageConversionService.buildCommands(caesiumPath, outputFolder,
                selectedItems.stream().map(FileItem::getFullPath).toList(), options)) {
            runs = runs.thenCompose(earlier -> runExternal(command, true).thenApply(output -> {
                List<String> all = new ArrayList<>(earlier);
                all.addAll(output);
                return all;
            }));
        }
        runs
                .thenAccept(output -> Platform.runLater(() -> {
                    List<String> failures = ImageConversionService.failures(output);
                    if (!failures.isEmpty()) {
                        showError("Convert Graphics Files", "Some images were not converted:\n" + String.join("\n", failures));
                    }
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

    private void focusConvertedFileInOtherPane(
            List<FileItem> selectedItems,
            String outputFolder,
            ImageConversionRequest options
    ) {
        Path firstFound = ImageConversionService.findFirstConverted(
                selectedItems.stream().map(FileItem::getName).toList(), outputFolder, options);
        if (firstFound != null) {
            filesPanesHelper.selectFileItem(false, new FileItem(firstFound));
        }
        requestUnfocusedFileListFocus();
    }

    public void convertMediaFile() {
        logger.info("Convert Media File");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (ImageConversionSupport.areAllConvertibleImages(selectedItems)) {
            convertGraphicsFiles();
            return;
        }
        if (MediaFiles.areAllAudio(selectedItems)) {
            convertAudioFiles();
            return;
        }
        if (MediaFiles.areAllVideo(selectedItems)) {
            convertVideoFiles();
            return;
        }
        showError("Convert Media File", "Select image, audio or video files of one kind only.");
        requestFocusedFileListFocus();
    }

    public void convertAudioFiles() {
        logger.info("Convert Audio Files");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (!MediaFiles.areAllAudio(selectedItems)) {
            showError("Convert Audio Files", "Select one or more audio files only.");
            requestFocusedFileListFocus();
            return;
        }

        Optional<AudioRequest> request = AudioConversionDialog.show(dialogOwner(), currentThemeMode.styleClass,
                selectedItems.size(), filesPanesHelper.getUnfocusedPath());
        if (request.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }
        Optional<MediaConversionService> converter = mediaConverter("Convert Audio Files");
        if (converter.isEmpty()) {
            return;
        }

        List<Path> sources = selectedItems.stream().map(item -> Path.of(item.getFullPath())).toList();
        showConversionResult("Convert Audio Files",
                converter.get().convertAudio(sources, Paths.get(filesPanesHelper.getUnfocusedPath()), request.get()));
    }

    /** The ffmpeg converter, or empty after telling the user ffmpeg.exe is missing. */
    private Optional<MediaConversionService> mediaConverter(String title) {
        Path ffmpeg = BundledTool.FFMPEG.path();
        if (!Files.isRegularFile(ffmpeg)) {
            showError(title, "ffmpeg.exe was not found at: " + ffmpeg);
            requestFocusedFileListFocus();
            return Optional.empty();
        }
        return Optional.of(new MediaConversionService(command -> runExternal(command, false), ffmpeg));
    }

    /** Refreshes the panes and selects the first new file in the other pane, or shows why the conversion failed. */
    private void showConversionResult(String title, CompletableFuture<Path> conversion) {
        conversion
                .thenAccept(firstConverted -> Platform.runLater(() -> {
                    filesPanesHelper.refreshFileListViews();
                    if (firstConverted != null) {
                        filesPanesHelper.selectFileItem(false, new FileItem(firstConverted));
                    }
                    requestUnfocusedFileListFocus();
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> {
                        filesPanesHelper.refreshFileListViews();
                        showError(title, "Conversion failed: " + causeMessage(throwable));
                        requestFocusedFileListFocus();
                    });
                    return null;
                });
    }

    private static String causeMessage(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
        return cause.getMessage();
    }

    public void convertVideoFiles() {
        logger.info("Convert Video Files");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (!MediaFiles.areAllVideo(selectedItems)) {
            showError("Convert Video Files", "Select one or more video files only.");
            requestFocusedFileListFocus();
            return;
        }
        Optional<VideoRequest> request = VideoConversionDialog.show(dialogOwner(), currentThemeMode.styleClass,
                selectedItems.size(), filesPanesHelper.getUnfocusedPath());
        if (request.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }
        Optional<MediaConversionService> converter = mediaConverter("Convert Video Files");
        if (converter.isEmpty()) {
            return;
        }
        List<Path> sources = selectedItems.stream().map(item -> Path.of(item.getFullPath())).toList();
        showConversionResult("Convert Video Files",
                converter.get().convertVideo(sources, Paths.get(filesPanesHelper.getUnfocusedPath()), request.get()));
    }

    public void trimMedia() {
        logger.info("Trim Media");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (selectedItems.size() != 1 || !MediaFiles.areAllMedia(selectedItems)) {
            showError("Trim Media", "Select one audio or video file.");
            requestFocusedFileListFocus();
            return;
        }
        FileItem item = selectedItems.getFirst();
        Optional<TrimRequest> request = TrimMediaDialog.show(dialogOwner(), currentThemeMode.styleClass, item.getName(),
                MediaFiles.isVideo(item.extension()));
        if (request.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }
        mediaConverter("Trim Media").ifPresent(converter -> showConversionResult("Trim Media",
                converter.trim(Path.of(item.getFullPath()), Paths.get(filesPanesHelper.getUnfocusedPath()), request.get())));
    }

    public void joinMedia() {
        logger.info("Join Media");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        String problem = MediaFiles.joinProblem(selectedItems);
        if (problem != null) {
            showError("Join Media", problem);
            requestFocusedFileListFocus();
            return;
        }
        FileItem first = selectedItems.getFirst();
        String extension = first.extension();
        String name = first.getName();
        String suggested = name.substring(0, name.length() - extension.length() - 1) + "_joined." + extension;
        Optional<String> entered = getUserFeedback(suggested, "Join Media", "Joined file name",
                suggested.length() - extension.length() - 1);
        if (entered.isEmpty() || entered.get().isBlank()) {
            requestFocusedFileListFocus();
            return;
        }
        List<Path> sources = selectedItems.stream().map(item -> Path.of(item.getFullPath())).toList();
        mediaConverter("Join Media").ifPresent(converter -> showConversionResult("Join Media", converter.join(sources,
                Paths.get(filesPanesHelper.getUnfocusedPath()), MediaFiles.withExtension(entered.get(), extension))));
    }

    public void mediaInfo() {
        logger.info("Media Info");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (selectedItems.size() != 1 || !MediaFiles.areAllMedia(selectedItems)) {
            showError("Media Info", "Select one audio or video file.");
            requestFocusedFileListFocus();
            return;
        }
        FileItem item = selectedItems.getFirst();
        mediaConverter("Media Info").ifPresent(converter -> converter.probe(Path.of(item.getFullPath()))
                .thenAccept(output -> Platform.runLater(() -> {
                    showInfo("Media Info", item.getName() + "\n\n" + String.join("\n", MediaConversionService.mediaInfo(output)));
                    requestFocusedFileListFocus();
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> {
                        showError("Media Info", "Could not read the file: " + causeMessage(throwable));
                        requestFocusedFileListFocus();
                    });
                    return null;
                }));
    }

    private boolean containsNonAscii(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }

    public void analyzeFile() {
        logger.info("Analyze File");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
        if (selectedItems.size() != 1) {
            logger.warn("Analyze File requires exactly one selected file, but got {}", selectedItems.size());
            showError("Analyze File", "Select exactly one file.");
            requestFocusedFileListFocus();
            return;
        }

        FileItem selectedItem = selectedItems.getFirst();
        if (selectedItem.isDirectory()) {
            logger.warn("Analyze File rejected directory selection: {}", selectedItem.getFullPath());
            showError("Analyze File", "The selected item is a folder. Please select a single file.");
            requestFocusedFileListFocus();
            return;
        }
        Path selectedPath = Paths.get(selectedItem.getFullPath());
        if (!Files.isRegularFile(selectedPath)) {
            logger.error("Analyze File selected path is not a regular file: {}", selectedPath);
            showError("Analyze File", "Selected file does not exist: " + selectedPath);
            requestFocusedFileListFocus();
            return;
        }

        Path fileToolPath = BundledTool.FILE.path();
        if (!Files.isRegularFile(fileToolPath)) {
            logger.error("Analyze File tool was not found: {}", fileToolPath);
            showError("Analyze File", "file executable was not found at: " + fileToolPath);
            requestFocusedFileListFocus();
            return;
        }

        Path magicPath = BundledTool.FILE_MAGIC.path();
        List<String> command = BundledToolCommands.analyzeFile(fileToolPath, magicPath, selectedItem.getFullPath());
        logger.debug("Analyze File command: {}", command);

        runExternal(command, false)
                .thenAccept(output -> {
                    logger.debug("Analyze File raw output lines: {}", output == null ? 0 : output.size());
                    String resultText = sanitizeFileAnalysisOutput(output, selectedItem);
                    if (isFileAnalysisFailure(resultText)) {
                        logger.error("Analyze File primary attempt failed for {}: {}", selectedItem.getFullPath(), resultText);
                        if (containsNonAscii(selectedItem.getFullPath())) {
                            runAnalyzeFileFallbackForNonAsciiPath(selectedItem, fileToolPath, magicPath, resultText);
                            return;
                        }
                        Platform.runLater(() -> {
                            showError("Analyze File", resultText);
                            requestFocusedFileListFocus();
                        });
                        return;
                    }
                    if (resultText.isBlank()) {
                        logger.warn("Analyze File returned empty output for {}", selectedItem.getFullPath());
                        Platform.runLater(() -> {
                            showError("Analyze File", "No analysis output was returned by file.");
                            requestFocusedFileListFocus();
                        });
                        return;
                    }
                    logger.info("Analyze File response for {}: {}", selectedItem.getFullPath(), resultText);
                    logger.info("Analyze File completed for {}", selectedItem.getFullPath());
                    Platform.runLater(() -> {
                        showInfo("Analyze File", resultText);
                        requestFocusedFileListFocus();
                    });
                })
                .exceptionally(throwable -> {
                    logger.error("Analyze File failed for {}", selectedItem.getFullPath(), throwable);
                    Platform.runLater(() -> {
                        showError("Analyze File", "Failed running file analysis: " + throwable.getMessage());
                        requestFocusedFileListFocus();
                    });
                    return null;
                });
    }

    private void runAnalyzeFileFallbackForNonAsciiPath(
            FileItem selectedItem,
            Path fileToolPath,
            Path magicPath,
            String primaryFailureText
    ) {
        Path stagedFile = null;
        try {
            stagedFile = stageFileForAsciiAnalysis(selectedItem);
        } catch (Exception ex) {
            logger.error("Analyze File fallback staging failed for {}", selectedItem.getFullPath(), ex);
            Path finalStagedFile = stagedFile;
            Platform.runLater(() -> {
                showError("Analyze File", primaryFailureText);
                requestFocusedFileListFocus();
            });
            deleteStagedAnalysisFile(finalStagedFile);
            return;
        }

        Path finalStagedFile = stagedFile;
        List<String> fallbackCommand = BundledToolCommands.analyzeFile(fileToolPath, magicPath, finalStagedFile.toString());
        logger.debug("Analyze File fallback command: {}", fallbackCommand);
        runExternal(fallbackCommand, false)
                .thenAccept(fallbackOutput -> {
                    try {
                        String fallbackText = sanitizeFileAnalysisOutput(fallbackOutput, new FileItem(finalStagedFile));
                        if (isFileAnalysisFailure(fallbackText) || fallbackText.isBlank()) {
                            logger.error(
                                    "Analyze File fallback failed for {} using {}: {}",
                                    selectedItem.getFullPath(),
                                    finalStagedFile,
                                    fallbackText
                            );
                            Platform.runLater(() -> {
                                showError("Analyze File", primaryFailureText);
                                requestFocusedFileListFocus();
                            });
                            return;
                        }
                        logger.info("Analyze File fallback succeeded for {}", selectedItem.getFullPath());
                        logger.info("Analyze File response for {}: {}", selectedItem.getFullPath(), fallbackText);
                        logger.info("Analyze File completed for {}", selectedItem.getFullPath());
                        Platform.runLater(() -> {
                            showInfo("Analyze File", fallbackText);
                            requestFocusedFileListFocus();
                        });
                    } finally {
                        deleteStagedAnalysisFile(finalStagedFile);
                    }
                })
                .exceptionally(throwable -> {
                    try {
                        logger.error("Analyze File fallback execution failed for {}", selectedItem.getFullPath(), throwable);
                        Platform.runLater(() -> {
                            showError("Analyze File", primaryFailureText);
                            requestFocusedFileListFocus();
                        });
                    } finally {
                        deleteStagedAnalysisFile(finalStagedFile);
                    }
                    return null;
                });
    }

    private Path stageFileForAsciiAnalysis(FileItem selectedItem) throws IOException {
        String originalName = selectedItem.getName();
        String extension = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            String extPart = originalName.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
            if (!extPart.isEmpty()) {
                extension = "." + extPart.toLowerCase(Locale.ROOT);
            }
        }
        Path stagedFile = AppTempDir.createTempFile("acommander_file_analysis_", extension);
        Files.copy(Paths.get(selectedItem.getFullPath()), stagedFile, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Analyze File staged unicode path to temporary file: {}", stagedFile);
        return stagedFile;
    }

    private void deleteStagedAnalysisFile(Path stagedFile) {
        if (stagedFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(stagedFile);
        } catch (IOException ex) {
            logger.warn("Analyze File failed to delete staged temp file: {}", stagedFile, ex);
        }
    }

    private boolean isFileAnalysisFailure(String text) {
        if (text == null) {
            return true;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return true;
        }
        return normalized.contains("cannot open")
                || normalized.contains("no such file or directory")
                || normalized.startsWith("error:")
                || normalized.contains("failed");
    }

    private String sanitizeFileAnalysisOutput(List<String> output, FileItem selectedItem) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        String fullPath = selectedItem == null ? "" : selectedItem.getFullPath();
        String fileName = selectedItem == null ? "" : selectedItem.getName();
        List<String> cleaned = output.stream()
                .filter(line -> line != null && !line.isBlank())
                .map(String::trim)
                .map(line -> {
                    if (!fullPath.isEmpty() && line.startsWith(fullPath + ":")) {
                        return line.substring(fullPath.length() + 1).trim();
                    }
                    if (!fileName.isEmpty() && line.startsWith(fileName + ":")) {
                        return line.substring(fileName.length() + 1).trim();
                    }
                    return line;
                })
                .filter(line -> !line.isBlank())
                .toList();
        return String.join(System.lineSeparator(), cleaned).trim();
    }

    public void checksumFile() {
        logger.info("Checksum File");
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
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

        Optional<ChecksumOptions> options = ChecksumOptionsDialog.show(dialogOwner(), currentThemeMode.styleClass,
                "Checksum File", selectedItem.getName(), false);
        if (options.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }

        Path rhashPath = BundledTool.RHASH.path();
        if (!Files.exists(rhashPath)) {
            showError("Checksum File", "rhash executable was not found at: " + rhashPath);
            requestFocusedFileListFocus();
            return;
        }

        List<String> command = BundledToolCommands.checksum(rhashPath, selectedItem.getFullPath(), options.get(), false);
        runExternal(command, false)
                .thenAccept(output -> Platform.runLater(() -> {
                    String checksumValue = options.get().includeFileNames()
                            ? String.join(System.lineSeparator(), output)
                            : BundledToolCommands.checksumDigest(output);
                    if (checksumValue == null || checksumValue.isBlank()) {
                        showError("Checksum File", "No checksum value was returned by rhash.");
                    } else {
                        String algorithm = options.get().algorithmLabel();
                        ChecksumResultDialog.show(dialogOwner(), currentThemeMode.styleClass, "Checksum File",
                                selectedItem.getName(), algorithm, checksumValue,
                                BundledToolCommands.checksumOutputPath(selectedItem.getPath().getParent(),
                                        selectedItem.getName(), algorithm, false));
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
        List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
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

        Optional<ChecksumOptions> options = ChecksumOptionsDialog.show(dialogOwner(), currentThemeMode.styleClass,
                "Checksum Folder Contents", selectedItem.getName(), true);
        if (options.isEmpty()) {
            requestFocusedFileListFocus();
            return;
        }

        Path rhashPath = BundledTool.RHASH.path();
        if (!Files.exists(rhashPath)) {
            showError("Checksum Folder Contents", "rhash executable was not found at: " + rhashPath);
            requestFocusedFileListFocus();
            return;
        }

        List<String> command = BundledToolCommands.checksum(rhashPath, selectedItem.getFullPath(), options.get(), true);
        runExternal(command, false)
                .thenAccept(output -> Platform.runLater(() -> {
                    String resultText = String.join(System.lineSeparator(), output).trim();
                    if (resultText.isBlank()) {
                        showError("Checksum Folder Contents", "No checksum values were returned by rhash.");
                    } else {
                        String algorithm = options.get().algorithmLabel();
                        ChecksumResultDialog.show(dialogOwner(), currentThemeMode.styleClass, "Checksum Folder Contents",
                                selectedItem.getName(), algorithm, resultText,
                                BundledToolCommands.checksumOutputPath(selectedItem.getPath(),
                                        selectedItem.getName(), algorithm, true));
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
        unpackSelection("Unpack", (sourceFs, entry, targetFs, folder) -> archiveOps.unpack(sourceFs, entry, targetFs, folder));
    }

    public void extractAll() {
        logger.info("Extract Anything (ALT+F12)");
        unpackSelection("Extract All", (sourceFs, entry, targetFs, folder) -> archiveOps.extractAll(sourceFs, entry, targetFs, folder));
    }

    private interface UnpackWork {
        void run(VFileSystem sourceFs, ClipboardTransfer.Entry entry, VFileSystem targetFs, String folder) throws IOException;
    }

    private void unpackSelection(String title, UnpackWork work) {
        ClipboardTransfer.State source = captureSelection(false);
        if (source.entries().isEmpty()) {
            return;
        }
        FilesPanesHelper.FocusSide targetSide = otherSide(source.sourceSide());
        VFileSystem targetFs = filesPanesHelper.getFileSystem(targetSide);
        String targetFolder = filesPanesHelper.getPath(targetSide);
        runFileOperation(title, List.of(source.sourceFs(), targetFs), true, () -> {
            for (ClipboardTransfer.Entry entry : source.entries()) {
                work.run(source.sourceFs(), entry, targetFs, targetFolder);
            }
            return null;
        }, ignored -> {});
    }

    public void mergePDFFiles() {
        logger.info("Merge PDF Files");
        ClipboardTransfer.State source = captureSelection(false);
        if (source.entries().isEmpty()) {
            return;
        }
        String firstFilename = source.entries().getFirst().name();
        String suggested = firstFilename.contains(".")
                ? firstFilename.substring(0, firstFilename.lastIndexOf('.')) + ".pdf"
                : firstFilename + ".pdf";
        getUserFeedback(suggested, "Merge PDF Files", "PDF filename").ifPresent(entered -> {
            String fileName = entered.isBlank() ? "merged.pdf"
                    : entered.toLowerCase(Locale.ROOT).endsWith(".pdf") ? entered : entered + ".pdf";
            FilesPanesHelper.FocusSide targetSide = otherSide(source.sourceSide());
            VFileSystem targetFs = filesPanesHelper.getFileSystem(targetSide);
            String targetFolder = filesPanesHelper.getPath(targetSide);
            runFileOperation("Merge PDF Files", List.of(source.sourceFs(), targetFs), true, () -> {
                pdfOps.merge(source.sourceFs(), source.entries(), targetFs, targetFolder, fileName);
                return null;
            }, ignored -> filesPanesHelper.selectNames(targetSide, targetFolder, List.of(fileName)));
        });
    }

    public void extractPDFPages() {
        logger.info("Extract PDF Pages");
        ClipboardTransfer.State source = captureSelection(false);
        if (source.entries().isEmpty()) {
            return;
        }
        ClipboardTransfer.Entry first = source.entries().getFirst();
        FilesPanesHelper.FocusSide targetSide = otherSide(source.sourceSide());
        VFileSystem targetFs = filesPanesHelper.getFileSystem(targetSide);
        String targetFolder = filesPanesHelper.getPath(targetSide);
        runFileOperation("Read PDF Page Count", List.of(source.sourceFs()), false,
                () -> pdfOps.pageCount(source.sourceFs(), first),
                totalPages -> PdfExtractDialog.show(dialogOwner(), currentThemeMode.styleClass, first.name(), totalPages)
                        .ifPresent(options -> runFileOperation("Extract PDF Pages", List.of(source.sourceFs(), targetFs), true, () -> {
                            for (ClipboardTransfer.Entry pdf : source.entries()) {
                                pdfOps.extractPages(source.sourceFs(), pdf, targetFs, targetFolder, options.withKnownTotalPages(totalPages));
                            }
                            return null;
                        }, ignored -> {})));
    }

    public boolean canCompareSelectedFiles() {
        FileItem leftSelected = getSingleSelectedFile(leftFileList);
        FileItem rightSelected = getSingleSelectedFile(rightFileList);
        if (leftSelected == null || rightSelected == null) {
            return false;
        }
        return FileHelper.isTextFile(leftSelected, filesPanesHelper.getFileSystem(LEFT)) && 
               FileHelper.isTextFile(rightSelected, filesPanesHelper.getFileSystem(RIGHT));
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

        if (!FileHelper.isTextFile(leftSelected, filesPanesHelper.getFileSystem(LEFT)) || 
            !FileHelper.isTextFile(rightSelected, filesPanesHelper.getFileSystem(RIGHT))) {
            showError("Compare Files", "Only text files can be compared. One of the selected files appears to be binary.");
            restoreFocusToFile(lastSelectedSide, lastSelectedFile);
            return;
        }

        Optional<CompareFilesOptions> options = CompareFilesDialog.show(dialogOwner(), currentThemeMode.styleClass,
                leftSelected, rightSelected);
        if (options.isEmpty()) {
            restoreFocusToFile(lastSelectedSide, lastSelectedFile);
            return;
        }

        String examDiffPath = BundledTool.EXAM_DIFF.path().toString();
        if (!Files.exists(Path.of(examDiffPath))) {
            showError("Compare Files", "ExamDiff executable was not found at: " + examDiffPath);
            restoreFocusToFile(lastSelectedSide, lastSelectedFile);
            return;
        }

        List<String> command = BundledToolCommands.compareFiles(examDiffPath, leftSelected.getFullPath(), rightSelected.getFullPath(), options.get());
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

        Optional<FolderComparer.Options> options = CompareFoldersDialog.show(dialogOwner(), currentThemeMode.styleClass,
                leftRoot, rightRoot);
        if (options.isEmpty()) {
            return;
        }

        runWithProgress("Comparing folders",
                () -> FolderComparer.compare(leftRoot, rightRoot, options.get()),
                result -> {
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
                },
                "Failed comparing folders");
    }

    private void clearFolderCompareHighlights(boolean refresh) {
        folderCompareMarks.computeIfAbsent(LEFT, unused -> new HashMap<>()).clear();
        folderCompareMarks.computeIfAbsent(RIGHT, unused -> new HashMap<>()).clear();
        if (refresh) {
            filesPanesHelper.refreshFileListViews();
        }
    }

    public void fileProperties() {
        logger.info("File Properties");
        try {
            List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
            if (selectedItems.size() != 1) {
                logger.info("File Properties skipped: selection count is {}", selectedItems.size());
                return;
            }

            FileItem selectedItem = selectedItems.getFirst();
            Path selectedFile = selectedItem.getPath();
            if (selectedFile == null || !Files.exists(selectedFile)) {
                logger.warn("File Properties failed: selected item is missing. item={}", selectedItem.getFullPath());
                showError("File Properties", "Selected item does not exist on disk.");
                return;
            }

            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                logger.warn("File Properties unsupported OS: {}", System.getProperty("os.name"));
                showError("File Properties", "File properties dialog is only supported on Windows.");
                return;
            }

            logger.info("Opening properties for {}: {}", selectedItem.isDirectory() ? "folder" : "file", selectedFile);
            FilePropertiesLauncher.open(selectedFile);
        } catch (Exception ex) {
            error("Failed opening file properties", ex);
        }
    }

    public void changeAttributes() {
        logger.info("Change Attributes");

        try {
            List<FileItem> selectedItems = fileOps.filterValidItems(new ArrayList<>(filesPanesHelper.getSelectedItems()));
            if (selectedItems.isEmpty()) {
                return;
            }

            Optional<FileAttributesHelper.AttributeChangeRequest> request = AttributesDialog.show(dialogOwner(),
                    currentThemeMode.styleClass, selectedItems.size(),
                    attributesHelper.readExistingAttributes(selectedItems.getFirst().getPath()));
            if (request.isEmpty()) {
                return;
            }

            List<String> failures = new ArrayList<>();
            for (FileItem selectedItem : selectedItems) {
                try {
                    attributesHelper.applyAttributesWithFallback(selectedItem.getPath(), request.get());
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

    @FXML
    public void editImageMetadata() {
        editMetadata("image", ImageMetadataDialog::show);
    }

    /** Opens {@code editor} on the first selected file; refreshes the panes when it saved. */
    private void editMetadata(String kind, MetadataEditor editor) {
        logger.info("Edit {} metadata", kind);
        List<FileItem> selectedItems = fileOps.filterValidItems(filesPanesHelper.getSelectedItems());
        if (selectedItems.isEmpty() || selectedItems.getFirst().isDirectory()) {
            return;
        }
        Path path = selectedItems.getFirst().getPath();
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            if (editor.edit(dialogOwner(), currentThemeMode.styleClass, path.toFile())) {
                filesPanesHelper.refreshFileListViews();
            }
        } catch (Exception ex) {
            error("Failed editing " + kind + " metadata", ex);
        }
    }

    private interface MetadataEditor {
        boolean edit(Window owner, String themeClass, File file);
    }

    @FXML
    public void removeImageMetadata() {
        logger.info("Remove Image Metadata");
        List<FileItem> selectedItems = fileOps.filterValidItems(filesPanesHelper.getSelectedItems());
        if (selectedItems.isEmpty()) {
            return;
        }
        if (!ImageMetadataSupport.areAllSupportedImages(selectedItems)) {
            showError("Remove Image Metadata", "Select one or more image files only.");
            requestFocusedFileListFocus();
            return;
        }
        removeMetadata("Remove Image Metadata", "image(s)", selectedItems, ImageMetadataSupport::remove);
    }

    /** Confirms, then runs {@code removeOne} on each selected file off the FX thread and reports how many succeeded. */
    private void removeMetadata(String title, String kind, List<FileItem> selectedItems, MetadataRemover removeOne) {
        Alert confirmDialog = new Alert(Alert.AlertType.WARNING);
        confirmDialog.setTitle(title);
        confirmDialog.setHeaderText("Remove metadata from " + selectedItems.size() + " " + kind + "?");
        confirmDialog.setContentText("This will permanently remove all metadata from the selected " + kind + ".");
        confirmDialog.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        applyThemeToDialog(confirmDialog);
        if (confirmDialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        runWithProgress(title,
                () -> {
                    int successCount = 0;
                    for (FileItem item : selectedItems) {
                        Path path = item.getPath();
                        if (item.isDirectory() || path == null || !Files.exists(path)) {
                            continue;
                        }
                        File file = path.toFile();
                        try {
                            if (removeOne.remove(file)) {
                                logger.info("Removed metadata from: {}", file.getAbsolutePath());
                                successCount++;
                            } else {
                                logger.warn("Failed to remove metadata from: {}", file.getAbsolutePath());
                            }
                        } catch (Exception ex) {
                            logger.warn("Error removing metadata from: {}", file.getAbsolutePath(), ex);
                        }
                    }
                    return successCount;
                },
                successCount -> {
                    filesPanesHelper.refreshFileListViews();
                    Alert alert = new Alert(successCount > 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
                    alert.setTitle(successCount > 0 ? "Metadata Removed" : "Metadata Removal Failed");
                    alert.setHeaderText(successCount > 0 ? "Success" : "No metadata removed");
                    alert.setContentText(successCount > 0
                            ? "Metadata removed from " + successCount + " " + kind
                            : "Failed to remove metadata from selected " + kind);
                    applyThemeToDialog(alert);
                    alert.showAndWait();
                },
                "Failed removing metadata");
    }

    private interface MetadataRemover {
        boolean remove(File file) throws Exception;
    }

    @FXML
    public void editVideoMetadata() {
        editMetadata("video", MetadataFormDialog::editVideo);
    }

    @FXML
    public void removeVideoMetadata() {
        logger.info("Remove Video Metadata");
        List<FileItem> selectedItems = fileOps.filterValidItems(filesPanesHelper.getSelectedItems());
        if (selectedItems.isEmpty()) {
            return;
        }
        if (!MediaFiles.areAllTaggableVideo(selectedItems)) {
            showError("Remove Video Metadata", "Select one or more supported video files only.");
            requestFocusedFileListFocus();
            return;
        }
        removeMetadata("Remove Video Metadata", "video file(s)", selectedItems, MediaTagSupport::remove);
    }

    @FXML
    public void editAudioMetadata() {
        editMetadata("audio", MetadataFormDialog::editAudio);
    }

    @FXML
    public void removeAudioMetadata() {
        logger.info("Remove Audio Metadata");
        List<FileItem> selectedItems = fileOps.filterValidItems(filesPanesHelper.getSelectedItems());
        if (selectedItems.isEmpty()) {
            return;
        }
        if (!MediaFiles.areAllTaggableAudio(selectedItems)) {
            showError("Remove Audio Metadata", "Select one or more supported audio files only.");
            requestFocusedFileListFocus();
            return;
        }
        removeMetadata("Remove Audio Metadata", "audio file(s)", selectedItems, MediaTagSupport::remove);
    }

    @FXML
    public void compressExecutable() {
        logger.info("Compress Executable");

        try {
            List<FileItem> selectedItems = fileOps.filterValidItems(filesPanesHelper.getSelectedItems());
            if (selectedItems.isEmpty()) {
                return;
            }

            if (!ExecutableCompressionSupport.areAllSupportedExecutables(selectedItems)) {
                return;
            }

            if (!(filesPanesHelper.getFocusedFileSystem() instanceof LocalFileSystem)) {
                showError("Compress Executable", "Executable compression is only supported for local files.");
                return;
            }

            List<File> files = new ArrayList<>();
            for (FileItem selectedItem : selectedItems) {
                Path path = selectedItem.getPath();
                if (path == null || !Files.exists(path)) {
                    showError("Compress Executable", "One or more selected files do not exist on disk.");
                    return;
                }
                files.add(path.toFile());
            }

            if (files.isEmpty()) {
                return;
            }

            Optional<ExecutableCompressionSupport.UpxAction> action = ExecutableCompressionDialog.show(dialogOwner(),
                    currentThemeMode.styleClass, selectedItems);
            if (action.isEmpty()) {
                return;
            }

            Path upxPath = BundledTool.UPX.path();
            if (!Files.exists(upxPath)) {
                showError("Compress Executable", "Missing tool: " + upxPath);
                return;
            }

            long sizeBefore = files.stream().mapToLong(File::length).sum();
            List<String> command = ExecutableCompressionSupport.upxCommand(upxPath, action.get(), files);

            runExternal(command, true)
                    .thenAccept(output -> Platform.runLater(() -> showExecutableCompressionResult(files, sizeBefore)))
                    .exceptionally(throwable -> {
                        Platform.runLater(() -> handleExecutableCompressionFailure(files, command, throwable));
                        return null;
                    });
        } catch (Exception ex) {
            error("Failed compressing executable", ex);
        }
    }

    private void showExecutableCompressionResult(List<File> files, long sizeBefore) {
        long sizeAfter = files == null ? 0 : files.stream().mapToLong(File::length).sum();
        String beforeText = sizeBefore > 0 ? humanSize(sizeBefore) : "Unknown";
        String afterText = sizeAfter > 0 ? humanSize(sizeAfter) : "Unknown";
        String percentText = ExecutableCompressionSupport.percentChange(sizeBefore, sizeAfter);
        int count = files == null ? 0 : files.size();
        String message = "Files: " + count
                + System.lineSeparator()
                + "Size before: " + beforeText
                + System.lineSeparator()
                + "Size after: " + afterText
                + System.lineSeparator()
                + "Change: " + percentText;
        showInfo("Executable Compression Result", message);
    }


    private void handleExecutableCompressionFailure(List<File> files, List<String> command, Throwable throwable) {
        Throwable root = unwrapCompletionException(throwable);
        if (root instanceof ExternalCommandException ex) {
            String outputTail = ex.getOutputTail();
            String reason = describeUpxFailure(outputTail);
            logger.error(
                    "Executable compression failed. files={} exitCode={} command={} reason={} outputTail={}",
                    files == null ? "<null>" : files.size(),
                    ex.getExitCode(),
                    ex.getCommand(),
                    reason,
                    outputTail
            );
            showError("Compress Executable", buildExecutableCompressionError(reason, outputTail));
            return;
        }

        logger.error(
                "Executable compression failed. files={} command={}",
                files == null ? "<null>" : files.size(),
                command == null ? "<null>" : String.join(" ", command),
                root
        );
        showError("Compress Executable", "Executable compression failed: " + (root == null ? "Unknown error" : root.getMessage()));
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return unwrapCompletionException(throwable.getCause());
        }
        return throwable;
    }

    private String describeUpxFailure(String outputTail) {
        String output = outputTail == null ? "" : outputTail;
        String lower = output.toLowerCase(Locale.ROOT);
        if (lower.contains("alreadypackedexception") || lower.contains("already packed")) {
            return "The file is already packed by UPX. No additional compression was applied.";
        }
        if (lower.contains("not a") && lower.contains("executable")) {
            return "The selected file is not a supported executable for UPX.";
        }
        if (lower.contains("not packed") && lower.contains("cannot")) {
            return "UPX could not pack this executable with the selected options.";
        }
        return "Executable compression failed.";
    }

    private String buildExecutableCompressionError(String reason, String outputTail) {
        if (outputTail == null || outputTail.isBlank()) {
            return reason;
        }
        return reason + System.lineSeparator() + System.lineSeparator() + "Details:" + System.lineSeparator() + outputTail;
    }

    public void syncToOtherPane() {
        FilesPanesHelper.FocusSide focusedSide = filesPanesHelper.getFocusedSide();
        FilesPanesHelper.FocusSide targetSide = (focusedSide == LEFT) ? RIGHT : LEFT;
        VFileSystem focusedFs = filesPanesHelper.getFocusedFileSystem();
        String focusedPath = filesPanesHelper.getFocusedPath();

        // An archive or FTP path means nothing on disk: the other pane goes back to a local root
        showLocalFolder(targetSide, focusedFs instanceof LocalFileSystem ? focusedPath : getDefaultRootPath(),
                "Same Folder on Other Panel");
        Platform.runLater(() -> filesPanesHelper.getFileList(true).requestFocus());
    }

    /** Shows local {@code path} on {@code side}, first leaving the archive (repacking it) or FTP server it shows. */
    private void showLocalFolder(FilesPanesHelper.FocusSide side, String path, String errorTitle) {
        try {
            if (filesPanesHelper.getFileSystem(side) instanceof LocalFileSystem) {
                filesPanesHelper.setFileListPath(side, path);
            } else {
                filesPanesHelper.setFileSystem(side, filesPanesHelper.getVfsManager().createLocalFileSystem(""), path);
            }
        } catch (IOException e) {
            logger.error("Failed to leave the {} pane's file system for {}", side, path, e);
            showError(errorTitle, e.getMessage());
        }
    }

    private boolean isLocalPath(String path) {
        if (path == null || path.isEmpty()) return false;
        // Windows path: C:\ or \\network\
        return (path.length() >= 2 && path.charAt(1) == ':') || path.startsWith("\\\\");
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
        saveSettings();
    }

    public void gotoBookmark() {
        try {
            Optional<String> selected = pickBookmark("Go to Bookmark", "Go to selected bookmark", "Go",
                    "Open the selected bookmark in the focused pane.");
            if (selected.isEmpty()) {
                return;
            }

            String path = bookmarks.get(selected.get());
            if (path == null || path.isBlank()) {
                showError("Go to Bookmark", "Bookmark path is missing.");
                return;
            }
            if (!Files.isDirectory(Path.of(path))) {
                showError("Go to Bookmark", "Bookmark path does not exist: " + path);
                return;
            }

            showLocalFolder(filesPanesHelper.getFocusedSide(), path, "Go to Bookmark");
        } finally {
            requestFocusedFileListFocus();
        }
    }

    public void removeBookmark() {
        Optional<String> selected = pickBookmark("Remove Bookmark", "Select bookmark to remove", "Remove",
                "Delete the selected bookmark; the folder itself is not touched.");
        if (selected.isEmpty()) {
            return;
        }
        bookmarks.remove(selected.get());
        saveSettings();
    }

    public void selectAll() {
        filesPanesHelper.selectAllItems();
        updatePaneSummary(filesPanesHelper.getFocusedSide());
    }

    public void unselectAll() {
        filesPanesHelper.unselectAllItems();
        updatePaneSummary(filesPanesHelper.getFocusedSide());
    }

    public void invertSelection() {
        filesPanesHelper.invertSelection();
        updatePaneSummary(filesPanesHelper.getFocusedSide());
    }

    public void selectByPattern() {
        SelectByPatternDialog.show(dialogOwner(), currentThemeMode.styleClass, settings.lastSelectionPattern())
                .ifPresent(res -> {
                    settings.setLastSelectionPattern(res.pattern());
                    saveSettings();
                    filesPanesHelper.selectByPattern(res.pattern(), res.useRegex());
                    updatePaneSummary(filesPanesHelper.getFocusedSide());
                });
    }

    public void ftpDisconnect() {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        VFileSystem currentFs = filesPanesHelper.getFileSystem(side);
        if (!(currentFs instanceof LocalFileSystem)) {
            try {
                String localPath = getDefaultRootPath();
                filesPanesHelper.setFileSystem(side, filesPanesHelper.getVfsManager().createLocalFileSystem(""), localPath);
                logger.info("Disconnected from VFS on {} side, switched back to local path: {}", side, localPath);
            } catch (IOException e) {
                logger.error("Failed to disconnect and switch to local file system", e);
                showError("Disconnect Error", "Could not switch back to local file system: " + e.getMessage());
            }
        }
    }

    public void openHostsFile() {
        logger.info("Open hosts file");
        try {
            fileOps.openHostsFile();
        } catch (FileNotFoundException e) {
            showError("Hosts File Not Found", e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to open hosts file", e);
            showError("Error", "Failed to open hosts file: " + e.getMessage());
        }
    }

    public void ftpConnect() {
        try {
            settings.unlockFtpPasswords(ftpConnections);
        } catch (IOException e) {
            logger.error("Failed decrypting saved FTP passwords", e);
            showToast("Saved FTP passwords could not be read: " + e.getMessage());
        }
        Optional<FtpConnectDialog.Result> result = FtpConnectDialog.show(dialogOwner(), currentThemeMode.styleClass,
                ftpConnections, name -> {
                    ftpConnections.remove(name);
                    saveSettings();
                });
        result.ifPresent(choice -> {
            FtpConnectionOptions options = choice.options();
            boolean shouldSave = choice.save();
            // Auto-discover saves after discovery, with the protocol it found.
            if (shouldSave && !options.isAutoDiscover()) {
                ftpConnections.put(options.getName(), options);
                saveSettings();
            }
            logger.info("Attempting to connect to FTP: {} ({}:{}), Protocol: {}",
                options.getName(), options.getHost(), options.getPort(),
                options.isAutoDiscover() ? "Auto" : options.getProtocol());

            // Show progress
            progress.started((options.isAutoDiscover() ? "Auto" : options.getProtocol()) + ": " + options.getName());

            BackgroundTasks.run(() -> {
                try {
                    // If auto-discover is enabled, try to find the right protocol
                    FtpConnectionOptions connectionOptions = options;
                    if (options.isAutoDiscover()) {
                        FtpConnectionOptions discovered = FtpFileSystem.autoDiscoverProtocol(options);
                        if (discovered != null) {
                            connectionOptions = discovered;
                        } else {
                            throw new IOException("Could not auto-discover protocol. Please select the protocol manually.");
                        }
                    }

                    final FtpConnectionOptions finalConnectionOptions = connectionOptions;
                    VFileSystem ftpFs = filesPanesHelper.getVfsManager().createFtpFileSystem(finalConnectionOptions);
                    // Validation: attempt to list root
                    ftpFs.listContents("/");

                    Platform.runLater(() -> {
                        try {
                            filesPanesHelper.setFileSystem(filesPanesHelper.getFocusedSide(), ftpFs);
                            logger.info("Successfully connected to {}: {}", finalConnectionOptions.getProtocol(), finalConnectionOptions.getName());

                            // If auto-discover was used and save was selected, save with discovered values
                            if (options.isAutoDiscover() && shouldSave) {
                                ftpConnections.put(finalConnectionOptions.getName(), finalConnectionOptions);
                                saveSettings();
                            }

                            progress.finished();
                            filesPanesHelper.getFileList(true).requestFocus();
                        } catch (Exception e) {
                            handleFtpConnectionError(finalConnectionOptions, e);
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> handleFtpConnectionError(options, e));
                }
            });
        });
    }

    private void handleFtpConnectionError(FtpConnectionOptions options, Exception e) {
        progress.finished();
        logger.error("FTP Connection Failed for {}: {}", options.getName(), e.getMessage());
        String message = e.getMessage();
        if (message != null && message.contains("FTP operation failed:")) {
            message = message.replace("FTP operation failed:", "").trim();
        }
        showError("FTP Connection Failed", (message == null || message.isEmpty()) ? "Unknown error" : message);
    }

    public void filterByChar(char selectedChar) {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        ListView<FileItem> listView = side == LEFT ? leftFileList : rightFileList;
        IncrementalFilter filter = incrementalFilters.get(side);
        filter.type(selectedChar, List.copyOf(listView.getItems()));
        showFilteredItems(listView, filter.visibleItems());
        showIncrementalFilterPopup(filter.prefix());
    }

    public void clearCharFilter() {
        clearCharFilter(filesPanesHelper.getFocusedSide());
    }

    public boolean backspaceCharFilter() {
        FilesPanesHelper.FocusSide side = filesPanesHelper.getFocusedSide();
        IncrementalFilter filter = incrementalFilters.get(side);
        if (!filter.isActive()) {
            return false;
        }

        filter.backspace();
        if (!filter.isActive()) {
            clearCharFilter(side);
            return true;
        }

        showFilteredItems(side == LEFT ? leftFileList : rightFileList, filter.visibleItems());
        showIncrementalFilterPopup(filter.prefix());
        return true;
    }

    private void clearCharFilter(FilesPanesHelper.FocusSide side) {
        List<FileItem> baseItems = incrementalFilters.get(side).clear();
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

    /** Shows the filtered items and selects the first real one (not ".."). */
    private void showFilteredItems(ListView<FileItem> listView, List<FileItem> filteredItems) {
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
        return getUserFeedback(defaultValue, title, question, defaultValue == null ? 0 : defaultValue.length());
    }

    private Optional<String> getUserFeedback(String defaultValue, String title, String question, int selectionEndExclusive) {
        return TextPromptDialog.show(dialogOwner(), currentThemeMode.styleClass, defaultValue, title, question,
                selectionEndExclusive);
    }

    private int getRenameSelectionEnd(FileItem selectedItem) {
        if (selectedItem == null) {
            return 0;
        }
        String name = selectedItem.getName();
        if (name == null || name.isEmpty() || selectedItem.isDirectory()) {
            return name == null ? 0 : name.length();
        }

        int lastDot = name.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == name.length() - 1) {
            return name.length();
        }
        return lastDot;
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

    private Optional<String> pickBookmark(String title, String hint, String actionLabel, String actionTooltip) {
        if (bookmarks.isEmpty()) {
            showInfo(title, "No bookmarks found.");
            return Optional.empty();
        }
        return BookmarkPickerDialog.show(dialogOwner(), currentThemeMode.styleClass, title, hint, actionLabel,
                actionTooltip, bookmarks);
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

    public CompletableFuture<List<String>> runExternal(List<String> command, boolean refreshAfter) {
        return toolRunner.runExecutable(command, refreshAfter);
    }

    /** Runs a tool nobody waits on; a failure is shown to the user under {@code title}. */
    public void runExternalReported(List<String> command, boolean refreshAfter, String title) {
        toolRunner.reportFailure(toolRunner.runExecutable(command, refreshAfter), title);
    }

    /** Same, run in {@code directory}. */
    public void runExternalReported(List<String> command, boolean refreshAfter, String title, File directory) {
        toolRunner.reportFailure(toolRunner.runExecutable(command, refreshAfter, Set.of(), directory), title);
    }

    public CompletableFuture<List<String>> runExternal(
            List<String> command,
            boolean refreshAfter,
            Set<Integer> acceptedNonZeroExitCodes
    ) {
        return toolRunner.runExecutable(command, refreshAfter, acceptedNonZeroExitCodes);
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

    private String buildSplitArchiveName(String sourceFilename) {
        int dotIndex = sourceFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            return sourceFilename.substring(0, dotIndex) + ".7z";
        }
        return sourceFilename + ".7z";
    }

    public void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.setResizable(true);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        applyThemeToDialog(alert);
        alert.showAndWait();
    }

    public void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.setResizable(true);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        applyThemeToDialog(alert);
        alert.showAndWait();
    }

    public void copySelectionToClipboard() {
        setClipboardTransferState(false);
    }

    public void cutSelectionToClipboard() {
        setClipboardTransferState(true);
    }

    public boolean hasClipboardTransferEntries() {
        return clipboardTransferState != null && !clipboardTransferState.entries().isEmpty();
    }

    public void pasteClipboardSelection() {
        if (clipboardTransferState == null || clipboardTransferState.entries().isEmpty()) {
            showToast("Clipboard is empty");
            return;
        }
        ClipboardTransfer.State state = clipboardTransferState;
        VFileSystem targetFs = filesPanesHelper.getFocusedFileSystem();
        if (targetFs != null && targetFs.isReadOnly()) {
            showReadOnlyLocationWarning();
            return;
        }
        if (state.cut() && state.sourceFs().isReadOnly()) {
            showError("Paste", "Cannot move from a read-only source location.");
            return;
        }
        transfer("Paste", state, filesPanesHelper.getFocusedSide(), result -> {
            int successCount = result.pasted().size();
            if (successCount == 0) {
                return;
            }
            clipboardTransferState = null;
            commandPaletteController.refresh();
            showToast(result.failed().isEmpty()
                    ? "Pasted " + successCount + " file(s)"
                    : "Pasted " + successCount + " of " + state.entries().size() + " file(s)");
        });
    }

    private void setClipboardTransferState(boolean cut) {
        ClipboardTransfer.State state = captureSelection(cut);
        if (state.entries().isEmpty()) {
            showToast("No files selected");
            return;
        }
        clipboardTransferState = state;
        commandPaletteController.refresh();
        showToast((cut ? "Cut " : "Copied ") + state.entries().size() + " file(s)");
    }

    public void showToast(String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showToast(message));
            return;
        }
        if (rootPane == null || rootPane.getScene() == null || rootPane.getScene().getWindow() == null) {
            logger.info("Toast: {}", message);
            return;
        }

        initializeToastIfNeeded();
        toastLabel.setText(message);

        Window window = rootPane.getScene().getWindow();
        toastLabel.applyCss();
        toastLabel.layout();

        double popupWidth = Math.max(220, toastLabel.prefWidth(-1) + 24);
        double popupHeight = Math.max(36, toastLabel.prefHeight(-1) + 16);
        double x = window.getX() + window.getWidth() - popupWidth - 20;
        double y = window.getY() + window.getHeight() - popupHeight - 26;

        if (toastPopup.isShowing()) {
            toastPopup.hide();
        }
        toastPopup.show(window, x, y);

        if (toastHideTransition != null) {
            toastHideTransition.stop();
        }
        toastHideTransition = new PauseTransition(Duration.seconds(2.0));
        toastHideTransition.setOnFinished(event -> {
            if (toastPopup != null && toastPopup.isShowing()) {
                toastPopup.hide();
            }
        });
        toastHideTransition.playFromStart();
    }

    private void initializeToastIfNeeded() {
        if (toastPopup != null) {
            return;
        }
        toastPopup = new Popup();
        toastPopup.setAutoHide(false);
        toastPopup.setHideOnEscape(false);

        toastLabel = new Label();
        toastLabel.setAlignment(Pos.CENTER_LEFT);
        toastLabel.setPadding(new Insets(8, 12, 8, 12));
        toastLabel.setStyle(
                "-fx-background-color: rgba(28, 33, 44, 0.95);" +
                        "-fx-text-fill: #ffffff;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-color: rgba(255,255,255,0.15);" +
                        "-fx-font-size: 12px;"
        );
        toastPopup.getContent().add(toastLabel);
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

    public void updateBottomButtons() {
        // Check the actual current modifier key state from the tracked set
        // This prevents the "stuck titles" bug where alternate titles don't reset properly
        boolean altDown = activeModifiers.contains(KeyCode.ALT);
        boolean shiftDown = activeModifiers.contains(KeyCode.SHIFT);

        // Update buttons based on ACTUAL current state, not tracked state
        if (altDown) {
            btnF1.setText("ALT+F1 Left Folder");
            btnF2.setText("ALT+F2 Right Folder");
            btnF3.setText("");
            btnF4.setText("ALT+F4 Exit");
            btnF5.setText("ALT+F5 Convert Media File");
            btnF6.setText("");
            btnDup.setText("ALT+F6 Duplicate");
            btnDup.setVisible(true);
            btnDup.setManaged(true);
            btnF7.setText("ALT+F7 MkFile");
            btnF8.setText("");
            btnF9.setText("ALT+F9 Explorer");
            btnF10.setText("ALT+F10 Find in Files");
            btnF11.setText("ALT+F11 Split");
            btnF12.setText("ALT+F12 Extract Anything");
        } else if (shiftDown) {
            btnF1.setText("");
            btnF2.setText("");
            btnF3.setText("");
            btnF4.setText("");
            btnF5.setText("");
            btnF6.setText("SHIFT+F6 Rename");
            btnDup.setText("");
            btnDup.setVisible(false);
            btnDup.setManaged(false);
            btnF7.setText("");
            btnF8.setText("SHIFT+F8 Delete & Wipe");
            btnF9.setText("");
            btnF10.setText("");
            btnF11.setText("");
            btnF12.setText("");
        } else {
            // No modifier pressed - always show default titles
            btnF1.setText("F1 Help");
            btnF2.setText("F2 Rename");
            btnF3.setText("F3 View");
            btnF4.setText("F4 Edit");
            btnF5.setText("F5 Copy");
            btnF6.setText("F6 Move");
            btnDup.setText("");
            btnDup.setVisible(false);
            btnDup.setManaged(false);
            btnF7.setText("F7 MkDir");
            btnF8.setText("F8 Delete");
            btnF9.setText("F9 Terminal");
            btnF10.setText("F10 Search for Files");
            btnF11.setText("F11 Pack");
            btnF12.setText("F12 UnPack");
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

    public void reportBug() {
        logger.info("Opening bug report dialog");
        ReportBugDialog.show(dialogOwner(), currentThemeMode.styleClass, AppVersion.current()).ifPresent(this::submitBugReport);
    }

    private void submitBugReport(String url) {
        logger.info("Opening bug report URL: {}", url);
        if (openInBrowser("Report Bug", url)) {
            showInfo("Report Submitted", "Thank you for your feedback! The issue form has been opened in your browser.");
        }
    }

    public void checkToolUpdates() {
        logger.info("Check Tool Updates");
        showToast("Checking for tool updates...");
        BackgroundTasks.supply(this::toolStatuses).whenComplete((statuses, error) -> Platform.runLater(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                logger.warn("Tool update check failed", cause);
                showError("Tool Updates", "Could not check for tool updates: " + cause.getMessage());
                return;
            }
            showToolUpdates(statuses);
        }));
    }

    /** Once a day, when on: shows Tool Updates only if main offers something the user hasn't been shown. */
    private void checkToolUpdatesAtStart() {
        if (!ToolUpdateService.startCheckDue(settings.toolUpdatesAtStart(), settings.toolUpdatesChecked(), System.currentTimeMillis())) {
            return;
        }
        BackgroundTasks.supply(this::toolStatuses).whenComplete((statuses, error) -> Platform.runLater(() -> {
            if (error != null) {
                logger.info("Start check for tool updates failed: {}", error.getMessage());
                return;
            }
            settings.setToolUpdatesChecked(System.currentTimeMillis());
            String offer = ToolUpdateService.offerKey(statuses);
            boolean isNew = !offer.isEmpty() && !offer.equals(settings.toolUpdatesShown());
            settings.setToolUpdatesShown(offer);
            saveSettings();
            if (isNew) {
                showToolUpdates(statuses);
            }
        }));
    }

    private List<ToolUpdateService.ToolStatus> toolStatuses() {
        try {
            return toolUpdates.check(appRegistry.tools());
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private void showToolUpdates(List<ToolUpdateService.ToolStatus> statuses) {
        ToolUpdatesDialog.show(dialogOwner(), currentThemeMode.styleClass, statuses, settings.toolUpdatesAtStart(),
                atStart -> {
                    settings.setToolUpdatesAtStart(atStart);
                    saveSettings();
                },
                status -> BackgroundTasks.run(() -> {
                    try {
                        toolUpdates.update(status);
                        logger.info("Updated {} to {}", status.tool().getName(), status.available());
                    } catch (IOException e) {
                        logger.warn("Updating {} failed", status.tool().getName(), e);
                        throw new CompletionException(e);
                    }
                }),
                url -> openInBrowser("Tool Updates", url));
    }

    private void applyTheme(Scene scene, ThemeMode themeMode, boolean persist) {
        DialogTheme.apply(scene, themeMode);
        currentThemeMode = themeMode;
        if (persist) {
            settings.setThemeMode(themeMode.configValue);
            saveSettings();
        }
    }

    private Window dialogOwner() {
        return rootPane == null || rootPane.getScene() == null ? null : rootPane.getScene().getWindow();
    }

    private void applyThemeToDialog(Dialog<?> dialog) {
        if (dialog == null || rootPane == null || rootPane.getScene() == null) {
            return;
        }
        DialogTheme.apply(dialog, rootPane.getScene().getWindow(), currentThemeMode.styleClass);
    }
}
