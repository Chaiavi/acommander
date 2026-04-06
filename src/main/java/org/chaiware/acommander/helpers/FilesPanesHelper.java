package org.chaiware.acommander.helpers;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import lombok.Data;
import org.chaiware.acommander.commands.ExternalCommandListener;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.vfs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.RIGHT;

public class FilesPanesHelper {
    public enum FocusSide {LEFT, RIGHT}
    public enum SortColumn {NAME, SIZE, MODIFIED}

    private static final Logger logger = LoggerFactory.getLogger(FilesPanesHelper.class);
    private final VfsManager vfsManager = new VfsManager();

    Map<FocusSide, FilePane> filePanes = new HashMap<>();
    private final Map<FocusSide, SortState> sortStates = new HashMap<>();
    private final Map<FocusSide, VFileSystem> fileSystems = new EnumMap<>(FocusSide.class);
    private final Map<FocusSide, String> currentInternalPaths = new EnumMap<>(FocusSide.class);
    private FocusSide focusedSide;
    private ExternalCommandListener externalCommandListener;

    public void setExternalCommandListener(ExternalCommandListener listener) {
        this.externalCommandListener = listener;
        // Apply to existing file systems
        for (VFileSystem fs : fileSystems.values()) {
            if (fs != null) {
                fs.setExternalCommandListener(listener);
            }
        }
    }

    public FocusSide getFocusedSide() {
        return focusedSide;
    }

    public VfsManager getVfsManager() {
        return vfsManager;
    }

    public org.chaiware.acommander.helpers.ArchiveManager getArchiveManager() {
        return vfsManager.getArchiveManager();
    }

    public VFileSystem getFileSystem(FocusSide side) {
        return fileSystems.get(side);
    }

    public VFileSystem getFocusedFileSystem() {
        return fileSystems.get(focusedSide);
    }

    public VFileSystem getUnfocusedFileSystem() {
        return fileSystems.get(focusedSide == LEFT ? RIGHT : LEFT);
    }

    public void setFileSystem(FocusSide side, VFileSystem fs) throws IOException {
        setFileSystem(side, fs, "/");
    }

    public void setFileSystem(FocusSide side, VFileSystem fs, String initialPath) throws IOException {
        logger.info("Switching {} pane to file system: {}", side, fs.getIdentifier());
        
        // Try to list the initial path before fully switching
        if (initialPath != null) {
            fs.listContents(initialPath);
        } else {
            fs.listContents("/"); // Default check
        }

        VFileSystem oldFs = fileSystems.put(side, fs);
        if (fs != null) {
            fs.setExternalCommandListener(externalCommandListener);
        }
        if (oldFs != null) {
            vfsManager.closeFileSystem(oldFs);
        }
        
        if (initialPath != null) {
            setFileListPath(side, initialPath);
        } else {
            refreshFileListView(side);
        }
    }

    public FilesPanesHelper(ListView<FileItem> leftFileList, ComboBox<Folder> leftPathComboBox, ListView<FileItem> rightFileList, ComboBox<Folder> rightPathComboBox) {
        setFocusedFileList(LEFT);

        filePanes.put(LEFT, new FilePane(leftFileList, leftPathComboBox));
        filePanes.put(RIGHT, new FilePane(rightFileList, rightPathComboBox));
        sortStates.put(LEFT, new SortState(SortColumn.NAME, true));
        sortStates.put(RIGHT, new SortState(SortColumn.NAME, true));
        
        // Initialize with default local file systems
        fileSystems.put(LEFT, vfsManager.createLocalFileSystem(""));
        fileSystems.put(RIGHT, vfsManager.createLocalFileSystem(""));
        currentInternalPaths.put(LEFT, "");
        currentInternalPaths.put(RIGHT, "");
    }
    
    /**
     * Cleans up all archive sessions when the application closes.
     */
    public void cleanup() {
        for (VFileSystem fs : fileSystems.values()) {
            if (fs != null) {
                vfsManager.closeFileSystem(fs);
            }
        }
        fileSystems.clear();
    }

    public void setFocusedFileList(FocusSide focusSide) {
        this.focusedSide = focusSide;
    }

    public void selectFileItem(boolean isFocused, FileItem fileItem) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> selectFileItem(isFocused, fileItem));
            return;
        }
        getFileList(isFocused).getSelectionModel().clearSelection();
        getFileList(isFocused).getSelectionModel().select(fileItem);
    }

    /** Sets the current file list's path */
    public void setFileListPath(FocusSide focusSide, String path) {
        setFileListPath(focusSide, path, null);
    }

    public void setFileListPath(FocusSide focusSide, String path, String preferredSelectionName) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> setFileListPath(focusSide, path, preferredSelectionName));
            return;
        }
        currentInternalPaths.put(focusSide, path);
        
        VFileSystem fs = fileSystems.get(focusSide);
        if (fs instanceof LocalFileSystem) {
            // Regular folder - exit archive mode if active
            exitArchive(focusSide);
        }

        refreshFileListView(focusSide);

        ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
        if (fs instanceof ArchiveFileSystem archiveFs && path.equals(archiveFs.getSession().getArchivePath())) {
            // Stay in archive mode
            pathComboBox.setValue(new ArchiveFolder(archiveFs.getDisplayName()));
        } else if (fs instanceof FtpFileSystem ftpFs) {
            // FTP filesystem - display name updated after refreshFileListView
            pathComboBox.setValue(new ArchiveFolder(ftpFs.getDisplayName()));
        } else if (!(fs instanceof LocalFileSystem)) {
            // Use virtual folder for display if not local FS (e.g. other VFS)
            pathComboBox.setValue(new ArchiveFolder(fs.getDisplayName()));
        } else {
            // Local file system
            Folder currentValue = pathComboBox.getValue();
            if (!samePath(currentValue != null ? currentValue.getPath() : null, path)) {
                pathComboBox.setValue(new Folder(path));
            }
        }

        if (!selectItemByPresentableFilename(focusSide, preferredSelectionName)) {
            ensureFirstEntrySelected(focusSide);
        }
    }
    
    /**
     * Enters an archive. For read-write archives, extracts to temp folder.
     * For read-only archives, also extracts but marks as read-only.
     */
    public void enterArchive(FocusSide focusSide, String archivePath) {
        try {
            // Open new archive session via VFS manager first
            VFileSystem fs = vfsManager.enterVirtualFolder(fileSystems.get(focusSide), new FileItem(new File(archivePath)));

            if (fs != null) {
                // setFileSystem handles closing oldFs if any
                try {
                    // Update internal path to archive root before refreshing
                    currentInternalPaths.put(focusSide, "");
                    setFileSystem(focusSide, fs, null);

                    Platform.runLater(() -> {
                        ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
                        pathComboBox.setValue(new ArchiveFolder(fs.getDisplayName()));

                        refreshFileListView(focusSide);
                        ensureFirstEntrySelected(focusSide);
                    });

                    logger.info("Entered archive ({} mode): {}", fs.isReadOnly() ? "READ_ONLY" : "READ_WRITE", archivePath);
                } catch (IOException e) {
                    logger.error("Failed to list archive contents: {}", archivePath, e);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to enter archive: {}", archivePath, e);
        }
    }
    
    /**
     * Exits an archive and cleans up the session.
     */
    public void exitArchive(FocusSide focusSide) {
        VFileSystem fs = fileSystems.get(focusSide);
        if (fs instanceof ArchiveFileSystem) {
            // setFileSystem handles closing the old FS
            try {
                setFileSystem(focusSide, vfsManager.createLocalFileSystem(""), null);
            } catch (IOException e) {
                logger.error("Failed to exit archive: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Navigates into a subdirectory within the current archive.
     */
    public void enterArchiveSubdirectory(FocusSide focusSide, String dirName) {
        VFileSystem fs = fileSystems.get(focusSide);
        if (!(fs instanceof ArchiveFileSystem currentArchiveFs)) {
            return;
        }
        
        ArchiveSession newSession = currentArchiveFs.getSession().createChild(dirName);
        ArchiveFileSystem newFs = new ArchiveFileSystem(newSession, vfsManager.getArchiveManager());
        fileSystems.put(focusSide, newFs);
        currentInternalPaths.put(focusSide, newSession.getEntryPath());
        
        Platform.runLater(() -> {
            ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
            pathComboBox.setValue(new ArchiveFolder(newFs.getDisplayName()));
            
            refreshFileListView(focusSide);
            ensureFirstEntrySelected(focusSide);
        });
        
        logger.debug("Entered archive subdirectory: {}", dirName);
    }
    
    /**
     * Navigates up one level in the archive hierarchy.
     * If at root, exits the archive and shows the archive file's parent folder.
     */
    public void goUpInArchive(FocusSide focusSide) {
        VFileSystem fs = fileSystems.get(focusSide);
        if (!(fs instanceof ArchiveFileSystem currentArchiveFs)) {
            return;
        }

        ArchiveSession currentSession = currentArchiveFs.getSession();
        if (currentSession.isRoot()) {
            // At archive root - exit archive and show parent folder of archive file
            String archivePath = currentSession.getArchivePath();
            exitArchive(focusSide);

            File archiveFile = new File(archivePath);
            File parentFolder = archiveFile.getParentFile();
            if (parentFolder != null) {
                setFileListPath(focusSide, parentFolder.getAbsolutePath(), archiveFile.getName());
            }
            return;
        }

        ArchiveSession parentSession = currentSession.getParent();
        if (parentSession == null) {
            // Exit archive and show parent folder of archive file
            String archivePath = currentSession.getArchivePath();
            exitArchive(focusSide);

            File archiveFile = new File(archivePath);
            File parentFolder = archiveFile.getParentFile();
            if (parentFolder != null) {
                setFileListPath(focusSide, parentFolder.getAbsolutePath(), archiveFile.getName());
            }
        } else {
            String childDirName = leafName(currentSession.getEntryPath());
            ArchiveFileSystem parentFs = new ArchiveFileSystem(parentSession, vfsManager.getArchiveManager());
            fileSystems.put(focusSide, parentFs);
            currentInternalPaths.put(focusSide, parentSession.getEntryPath());

            Platform.runLater(() -> {
                ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
                pathComboBox.setValue(new ArchiveFolder(parentFs.getDisplayName()));

                refreshFileListView(focusSide);
                if (!selectItemByPresentableFilename(focusSide, childDirName)) {
                    ensureFirstEntrySelected(focusSide);
                }
            });
        }

        logger.debug("Navigated up in archive hierarchy");
    }
    
    /**
     * Checks if the given side is currently viewing an archive.
     */
    public boolean isInArchive(FocusSide focusSide) {
        return fileSystems.get(focusSide) instanceof ArchiveFileSystem;
    }
    
    /**
     * Gets the current archive session for the given side, or null if not in archive.
     */
    public ArchiveSession getArchiveSession(FocusSide focusSide) {
        VFileSystem fs = fileSystems.get(focusSide);
        if (fs instanceof ArchiveFileSystem archiveFs) {
            return archiveFs.getSession();
        }
        return null;
    }
    
    /**
     * Checks if the current archive is read-only.
     */
    public boolean isArchiveReadOnly(FocusSide focusSide) {
        VFileSystem fs = fileSystems.get(focusSide);
        return fs != null && fs.isReadOnly();
    }
    
    /**
     * Marks the current archive as needing repack on exit.
     */
    public void markArchiveNeedsRepack(FocusSide focusSide) {
        VFileSystem fs = fileSystems.get(focusSide);
        if (fs != null) {
            fs.markModified();
        }
    }

    public void setFocusedFileListPath(String path) {
        setFileListPath(focusedSide, path);
    }

    public void setFocusedFileListPathAndSelect(String path, String preferredSelectionName) {
        setFileListPath(focusedSide, path, preferredSelectionName);
    }

    public ListView<FileItem> getFileList(boolean isFocused) {
        if (isFocused)
            return filePanes.get(focusedSide).getFileListView();
        else
            return filePanes.get(focusedSide == FocusSide.LEFT ? FocusSide.RIGHT : FocusSide.LEFT).getFileListView();
    }

    /* Refreshes both of the file views */
    public void refreshFileListViews() {
        FileItem focusedSelectedItem = getFileList(true).getSelectionModel().getSelectedItem();
        FileItem nonFocusedSelectedItem = getFileList(false).getSelectionModel().getSelectedItem();
        refreshFileListView(LEFT);
        refreshFileListView(RIGHT);
        selectFileItem(true, focusedSelectedItem);
        selectFileItem(false, nonFocusedSelectedItem);
    }

    /**
     * Loads the files in the path into the ListView.
     * For archives, loads from the temp folder.
     */
    public void refreshFileListView(FocusSide focusSide) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> refreshFileListView(focusSide));
            return;
        }

        ListView<FileItem> listView = filePanes.get(focusSide).getFileListView();
        FileItem previouslySelected = listView.getSelectionModel().getSelectedItem();

        ObservableList<FileItem> items = listView.getItems();
        items.clear();

        VFileSystem fs = fileSystems.get(focusSide);
        try {
            String path = currentInternalPaths.get(focusSide);
            if (path == null) {
                path = filePanes.get(focusSide).getPath();
            }
            logger.debug("Refreshing file list using VFS {}: {}", fs.getIdentifier(), path);
            List<FileItem> contents = fs.listContents(path);
            items.addAll(contents);
            logger.debug("Loaded {} items using VFS", contents.size());
        } catch (IOException e) {
            logger.error("Failed to list contents of {} using {}: {}", filePanes.get(focusSide).getPath(), fs.getIdentifier(), e.getMessage());
        }

        applySort(focusSide);
        if (previouslySelected != null) {
            listView.getSelectionModel().select(previouslySelected);
        }
        if (listView.getSelectionModel().getSelectedIndex() < 0) {
            ensureFirstEntrySelected(focusSide);
        }
    }

    public void ensureFirstEntrySelected(FocusSide focusSide) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> ensureFirstEntrySelected(focusSide));
            return;
        }
        ListView<FileItem> listView = filePanes.get(focusSide).getFileListView();
        if (listView.getItems().isEmpty()) {
            return;
        }
        listView.getSelectionModel().selectFirst();
        listView.getFocusModel().focus(0);
    }

    public void toggleSort(FocusSide focusSide, SortColumn column) {
        SortState current = sortStates.getOrDefault(focusSide, new SortState(SortColumn.NAME, true));
        SortState updated;
        if (current.column == column) {
            updated = new SortState(column, !current.ascending);
        } else {
            // Default ascending for NAME and SIZE, default descending (newest-first) for MODIFIED
            boolean defaultAscending = column == SortColumn.MODIFIED ? false : true;
            updated = new SortState(column, defaultAscending);
        }
        sortStates.put(focusSide, updated);
        applySort(focusSide);
    }

    public void setSort(FocusSide focusSide, SortColumn column, boolean ascending) {
        sortStates.put(focusSide, new SortState(column, ascending));
        applySort(focusSide);
    }

    public SortColumn getSortColumn(FocusSide focusSide) {
        return sortStates.getOrDefault(focusSide, new SortState(SortColumn.NAME, true)).column;
    }

    public boolean isSortAscending(FocusSide focusSide) {
        return sortStates.getOrDefault(focusSide, new SortState(SortColumn.NAME, true)).ascending;
    }

    private void applySort(FocusSide focusSide) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> applySort(focusSide));
            return;
        }
        ObservableList<FileItem> items = filePanes.get(focusSide).getFileListView().getItems();
        FileItem parent = items.stream()
                .filter(this::isParentFolder)
                .findFirst()
                .orElse(null);

        List<FileItem> sortable = items.stream()
                .filter(item -> !isParentFolder(item))
                .toList();

        SortState sortState = sortStates.getOrDefault(focusSide, new SortState(SortColumn.NAME, true));
        Comparator<FileItem> comparator = buildComparator(sortState);
        List<FileItem> sorted = sortable.stream().sorted(comparator).toList();

        items.clear();
        if (parent != null) {
            items.add(parent);
        }
        items.addAll(sorted);
    }

    private Comparator<FileItem> buildComparator(SortState state) {
        Comparator<FileItem> directoriesFirst = Comparator.comparing(FileItem::isDirectory).reversed();

        Comparator<FileItem> byColumn = switch (state.column) {
            case NAME -> this::compareByNameNatural;
            case SIZE -> Comparator.comparingLong(this::sizeForSort);
            case MODIFIED -> Comparator.comparingLong(this::modifiedForSort);
        };

        if (!state.ascending) {
            byColumn = byColumn.reversed();
        }

        Comparator<FileItem> byName = this::compareByNameNatural;
        return directoriesFirst.thenComparing(byColumn).thenComparing(byName);
    }

    private int compareByNameNatural(FileItem left, FileItem right) {
        return compareNaturalNames(left.getPresentableFilename(), right.getPresentableFilename());
    }

    static int compareNaturalNames(String left, String right) {
        if (left == null || right == null) {
            if (left == right) {
                return 0;
            }
            return left == null ? -1 : 1;
        }

        int leftIndex = 0;
        int rightIndex = 0;

        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);

            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftDigitsStart = leftIndex;
                int rightDigitsStart = rightIndex;

                while (leftIndex < left.length() && Character.isDigit(left.charAt(leftIndex))) {
                    leftIndex++;
                }
                while (rightIndex < right.length() && Character.isDigit(right.charAt(rightIndex))) {
                    rightIndex++;
                }

                int leftNonZero = leftDigitsStart;
                while (leftNonZero < leftIndex && left.charAt(leftNonZero) == '0') {
                    leftNonZero++;
                }
                int rightNonZero = rightDigitsStart;
                while (rightNonZero < rightIndex && right.charAt(rightNonZero) == '0') {
                    rightNonZero++;
                }

                int leftSignificantLength = leftIndex - leftNonZero;
                int rightSignificantLength = rightIndex - rightNonZero;
                if (leftSignificantLength != rightSignificantLength) {
                    return Integer.compare(leftSignificantLength, rightSignificantLength);
                }

                for (int i = 0; i < leftSignificantLength; i++) {
                    char leftDigit = left.charAt(leftNonZero + i);
                    char rightDigit = right.charAt(rightNonZero + i);
                    if (leftDigit != rightDigit) {
                        return Character.compare(leftDigit, rightDigit);
                    }
                }

                int leftRunLength = leftIndex - leftDigitsStart;
                int rightRunLength = rightIndex - rightDigitsStart;
                if (leftRunLength != rightRunLength) {
                    return Integer.compare(leftRunLength, rightRunLength);
                }
                continue;
            }

            char leftLower = Character.toLowerCase(leftChar);
            char rightLower = Character.toLowerCase(rightChar);
            if (leftLower != rightLower) {
                return Character.compare(leftLower, rightLower);
            }

            leftIndex++;
            rightIndex++;
        }

        int lengthCompare = Integer.compare(left.length(), right.length());
        if (lengthCompare != 0) {
            return lengthCompare;
        }
        return left.compareTo(right);
    }

    private long sizeForSort(FileItem item) {
        return item.isDirectory() ? 0L : item.getSizeInBytes();
    }

    private long modifiedForSort(FileItem item) {
        if (item.getLastModified() != null) {
            return item.getLastModified();
        }
        return (item.getFile() != null) ? item.getFile().lastModified() : 0L;
    }

    private boolean isParentFolder(FileItem item) {
        return "..".equals(item.getPresentableFilename());
    }

    private boolean selectItemByPresentableFilename(FocusSide focusSide, String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        ListView<FileItem> listView = filePanes.get(focusSide).getFileListView();
        ObservableList<FileItem> items = listView.getItems();
        for (int i = 0; i < items.size(); i++) {
            FileItem item = items.get(i);
            if (filename.equals(item.getPresentableFilename())) {
                listView.getSelectionModel().clearSelection();
                listView.getSelectionModel().select(i);
                listView.getFocusModel().focus(i);
                listView.scrollTo(i);
                return true;
            }
        }
        return false;
    }

    private String leafName(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path;
        while (normalized.endsWith("/") || normalized.endsWith("\\")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return null;
        }
        int lastSlash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        return lastSlash < 0 ? normalized : normalized.substring(lastSlash + 1);
    }

    public String getFocusedPath() {
        VFileSystem fs = fileSystems.get(focusedSide);
        if (fs instanceof LocalFileSystem || fs instanceof FtpFileSystem) {
            return currentInternalPaths.get(focusedSide);
        }
        if (fs instanceof ArchiveFileSystem archiveFs) {
            return archiveFs.getSession().getTempFolderPath().toString();
        }
        return filePanes.get(focusedSide).getPath();
    }

    public String getPath(FocusSide focusSide) {
        VFileSystem fs = fileSystems.get(focusSide);
        if (fs instanceof LocalFileSystem || fs instanceof FtpFileSystem) {
            return currentInternalPaths.get(focusSide);
        }
        if (fs instanceof ArchiveFileSystem archiveFs) {
            return archiveFs.getSession().getTempFolderPath().toString();
        }
        return filePanes.get(focusSide).getPath();
    }

    public String getUnfocusedPath() {
        FocusSide unfocusedSide = focusedSide == LEFT ? RIGHT : LEFT;
        VFileSystem fs = fileSystems.get(unfocusedSide);
        if (fs instanceof LocalFileSystem || fs instanceof FtpFileSystem) {
            return currentInternalPaths.get(unfocusedSide);
        }
        if (fs instanceof ArchiveFileSystem archiveFs) {
            return archiveFs.getSession().getTempFolderPath().toString();
        }
        if (focusedSide == LEFT) return filePanes.get(RIGHT).getPath();
        return filePanes.get(LEFT).getPath();
    }

    public FileItem getSelectedItem() {
        return getFileList(true).getSelectionModel().getSelectedItem();
    }

    public List<FileItem> getSelectedItems() {
        return getFileList(true).getSelectionModel().getSelectedItems();
    }

    /** Selects all items in the focused file pane */
    public void selectAllItems() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::selectAllItems);
            return;
        }
        ListView<FileItem> listView = getFileList(true);
        listView.getSelectionModel().selectAll();
    }

    /** Clears selection in the focused file pane */
    public void unselectAllItems() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::unselectAllItems);
            return;
        }
        ListView<FileItem> listView = getFileList(true);
        listView.getSelectionModel().clearSelection();
    }

    /** Inverts the current selection in the focused file pane */
    public void invertSelection() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::invertSelection);
            return;
        }
        ListView<FileItem> listView = getFileList(true);
        List<FileItem> allItems = new ArrayList<>(listView.getItems());
        // Skip ".." parent folder entry
        allItems.removeIf(item -> "..".equals(item.getPresentableFilename()));
        
        ObservableList<Integer> selectedIndices = listView.getSelectionModel().getSelectedIndices();
        List<Integer> newSelection = new ArrayList<>();
        
        for (int i = 0; i < allItems.size(); i++) {
            int actualIndex = listView.getItems().indexOf(allItems.get(i));
            if (actualIndex >= 0 && !selectedIndices.contains(actualIndex)) {
                newSelection.add(actualIndex);
            }
        }
        
        listView.getSelectionModel().clearSelection();
        if (!newSelection.isEmpty()) {
            int[] indices = newSelection.stream().mapToInt(Integer::intValue).toArray();
            listView.getSelectionModel().selectIndices(-1, indices);
        }
    }

    /** Selects items matching the given pattern (glob or regex) */
    public void selectByPattern(String pattern, boolean useRegex) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> selectByPattern(pattern, useRegex));
            return;
        }
        ListView<FileItem> listView = getFileList(true);
        List<FileItem> allItems = listView.getItems();
        java.util.regex.Pattern regexPattern;
        
        if (useRegex) {
            regexPattern = java.util.regex.Pattern.compile(pattern);
        } else {
            // Convert glob pattern to regex
            regexPattern = globToRegex(pattern);
        }

        List<Integer> matchingIndices = new ArrayList<>();
        for (int i = 0; i < allItems.size(); i++) {
            FileItem item = allItems.get(i);
            if ("..".equals(item.getPresentableFilename())) continue;

            String filename = item.getPresentableFilename();
            boolean matches = regexPattern.matcher(filename).matches();

            if (matches) {
                matchingIndices.add(i);
            }
        }

        listView.getSelectionModel().clearSelection();
        if (!matchingIndices.isEmpty()) {
            int[] indices = matchingIndices.stream().mapToInt(Integer::intValue).toArray();
            listView.getSelectionModel().selectIndices(-1, indices);
            int firstMatchIndex = matchingIndices.getFirst();
            listView.getFocusModel().focus(firstMatchIndex);
            listView.scrollTo(firstMatchIndex);
            listView.requestFocus();
        }
    }

    /**
     * Converts a glob pattern (with * and ? wildcards) to a regex pattern.
     * * matches any sequence of characters
     * ? matches any single character
     */
    private java.util.regex.Pattern globToRegex(String glob) {
        if (glob == null || glob.isEmpty()) {
            return java.util.regex.Pattern.compile(".*");
        }
        
        StringBuilder regex = new StringBuilder();
        regex.append("^");
        
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                case '.':
                case '+':
                case '^':
                case '$':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '|':
                case '\\':
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
                    break;
            }
        }
        
        regex.append("$");
        return java.util.regex.Pattern.compile(regex.toString(), java.util.regex.Pattern.CASE_INSENSITIVE);
    }

    @Data
    static class FilePane {
        private final ListView<FileItem> fileListView;
        private final ComboBox<Folder> pathComboBox;

        public FilePane(ListView<FileItem> fileListView, ComboBox<Folder> pathComboBox) {
            this.fileListView = fileListView;
            this.pathComboBox = pathComboBox;
        }

        String getPath() {
            Folder value = pathComboBox.getValue();
            if (value == null || value.getPath() == null) {
                return "";
            }
            String path = value.getPath().trim();
            path = path.replaceFirst("\\s*\\(\\s*[\\d.,]+\\s*[KMGTPE]?B\\s*/\\s*[\\d.,]+\\s*[KMGTPE]?B\\s*\\)\\s*$", "");
            path = path.replaceFirst("\\s*\\([^)]*free\\)\\s*$", "");
            return path.trim();
        }
    }

    private boolean samePath(String left, String right) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        String normalizedLeft = normalizePathForCompare(left);
        String normalizedRight = normalizePathForCompare(right);
        return normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private String normalizePathForCompare(String path) {
        String value = path.trim();
        value = value.replaceFirst("\\s*\\(\\s*[\\d.,]+\\s*[KMGTPE]?B\\s*/\\s*[\\d.,]+\\s*[KMGTPE]?B\\s*\\)\\s*$", "");
        value = value.replaceFirst("\\s*\\([^)]*free\\)\\s*$", "");
        return value.trim();
    }
    
    /**
     * Special Folder subclass for archive display in combo box.
     */
    public static class ArchiveFolder extends Folder {
        private final String displayPath;

        public ArchiveFolder(String displayPath) {
            super("");  // Real path is empty, we use displayPath
            this.displayPath = displayPath;
        }

        @Override
        public String toString() {
            return displayPath;
        }

        @Override
        public String getPath() {
            return displayPath;
        }
    }
    
    /**
     * Special FileItem for the ".." entry in archives.
     * Holds reference to the archive session for proper navigation.
     */
    public static class ArchiveParentItem extends FileItem {
        private final ArchiveSession session;
        
        public ArchiveParentItem(File file, String filename, ArchiveSession session) {
            super(file, filename);
            this.session = session;
        }
        
        public ArchiveSession getSession() {
            return session;
        }
        
        public boolean isArchiveRoot() {
            return session.isRoot();
        }
    }

    private record SortState(SortColumn column, boolean ascending) {}
}
