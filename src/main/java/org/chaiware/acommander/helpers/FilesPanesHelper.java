package org.chaiware.acommander.helpers;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import lombok.Data;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.vfs.ArchiveFileSystem;
import org.chaiware.acommander.vfs.VFileSystem;
import org.chaiware.acommander.vfs.VfsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
    private FocusSide focusedSide;

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

    public FilesPanesHelper(ListView<FileItem> leftFileList, ComboBox<Folder> leftPathComboBox, ListView<FileItem> rightFileList, ComboBox<Folder> rightPathComboBox) {
        setFocusedFileList(LEFT);

        filePanes.put(LEFT, new FilePane(leftFileList, leftPathComboBox));
        filePanes.put(RIGHT, new FilePane(rightFileList, rightPathComboBox));
        sortStates.put(LEFT, new SortState(SortColumn.NAME, true));
        sortStates.put(RIGHT, new SortState(SortColumn.NAME, true));
        
        // Initialize with default local file systems
        fileSystems.put(LEFT, vfsManager.createLocalFileSystem(""));
        fileSystems.put(RIGHT, vfsManager.createLocalFileSystem(""));
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
        getFileList(isFocused).getSelectionModel().clearSelection();
        getFileList(isFocused).getSelectionModel().select(fileItem);
    }

    /** Sets the current file list's path */
    public void setFileListPath(FocusSide focusSide, String path) {
        ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
        
        VFileSystem fs = fileSystems.get(focusSide);
        if (fs instanceof ArchiveFileSystem archiveFs && path.equals(archiveFs.getSession().getArchivePath())) {
            // Stay in archive mode
            pathComboBox.setValue(new ArchiveFolder(archiveFs.getDisplayName()));
        } else {
            // Regular folder - exit archive mode if active
            exitArchive(focusSide);
            pathComboBox.setValue(new Folder(path));
        }

        refreshFileListView(focusSide);
        ensureFirstEntrySelected(focusSide);
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
                // Close any existing session for this side
                exitArchive(focusSide);
                
                fileSystems.put(focusSide, fs);
                
                ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
                pathComboBox.setValue(new ArchiveFolder(fs.getDisplayName()));
                
                refreshFileListView(focusSide);
                ensureFirstEntrySelected(focusSide);
                
                logger.info("Entered archive ({} mode): {}", fs.isReadOnly() ? "READ_ONLY" : "READ_WRITE", archivePath);
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
            vfsManager.closeFileSystem(fs);
            // Revert to local file system
            fileSystems.put(focusSide, vfsManager.createLocalFileSystem(""));
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
        
        ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
        pathComboBox.setValue(new ArchiveFolder(newFs.getDisplayName()));
        
        refreshFileListView(focusSide);
        ensureFirstEntrySelected(focusSide);
        
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
                setFileListPath(focusSide, parentFolder.getAbsolutePath());
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
                setFileListPath(focusSide, parentFolder.getAbsolutePath());
            }
        } else {
            ArchiveFileSystem parentFs = new ArchiveFileSystem(parentSession, vfsManager.getArchiveManager());
            fileSystems.put(focusSide, parentFs);

            ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
            pathComboBox.setValue(new ArchiveFolder(parentFs.getDisplayName()));

            refreshFileListView(focusSide);
            ensureFirstEntrySelected(focusSide);
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
        ListView<FileItem> listView = filePanes.get(focusSide).getFileListView();
        FileItem previouslySelected = listView.getSelectionModel().getSelectedItem();

        ObservableList<FileItem> items = listView.getItems();
        items.clear();

        // Check if we're in an archive
        VFileSystem fs = fileSystems.get(focusSide);
        if (fs instanceof ArchiveFileSystem archiveFs) {
            ArchiveSession session = archiveFs.getSession();
            // Load from temp folder
            Path tempPath = session.getTempFolderPath();
            File folder = tempPath.toFile();

            if (!folder.exists()) {
                logger.error("Temp folder doesn't exist: {}", tempPath);
                // Exit archive mode
                exitArchive(focusSide);
                return;
            }

            File[] files = folder.listFiles();

            // Always add ".." entry when in archive
            // At root level: ".." exits archive and shows parent folder of archive file
            // In subdirectory: ".." goes up one level in archive
            items.add(new ArchiveParentItem(folder, "..", session));

            if (files != null) {
                for (File f : files) {
                    items.add(new FileItem(f));
                }
            }

            logger.debug("Loaded {} items from archive temp folder: {}", files != null ? files.length : 0, session.getDisplayPath());
        } else {
            // Regular folder loading
            File folder = new File(filePanes.get(focusSide).getPath());
            File[] files = folder.listFiles();

            if (folder.getParentFile() != null)
                items.add(new FileItem(folder, ".."));
            if (files != null)
                for (File f : files)
                    items.add(new FileItem(f));
        }

        applySort(focusSide);
        if (previouslySelected != null) {
            listView.getSelectionModel().select(previouslySelected);
        }
        if (listView.getSelectionModel().getSelectedIndex() < 0) {
            ensureFirstEntrySelected(focusSide);
        }
    }

    private void ensureFirstEntrySelected(FocusSide focusSide) {
        ListView<FileItem> listView = filePanes.get(focusSide).getFileListView();
        if (listView.getItems().isEmpty()) {
            return;
        }
        listView.getSelectionModel().selectFirst();
        listView.getFocusModel().focus(0);
    }

    public void toggleSort(FocusSide focusSide, SortColumn column) {
        SortState current = sortStates.getOrDefault(focusSide, new SortState(SortColumn.NAME, true));
        SortState updated = current.column == column
                ? new SortState(column, !current.ascending)
                : new SortState(column, true);
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
            case NAME -> Comparator.comparing(this::normalizedName, String.CASE_INSENSITIVE_ORDER);
            case SIZE -> Comparator.comparingLong(this::sizeForSort);
            case MODIFIED -> Comparator.comparingLong(this::modifiedForSort);
        };

        if (!state.ascending) {
            byColumn = byColumn.reversed();
        }

        Comparator<FileItem> byName = Comparator.comparing(this::normalizedName, String.CASE_INSENSITIVE_ORDER);
        return directoriesFirst.thenComparing(byColumn).thenComparing(byName);
    }

    private String normalizedName(FileItem item) {
        return item.getPresentableFilename().toLowerCase(Locale.ROOT);
    }

    private long sizeForSort(FileItem item) {
        return item.isDirectory() ? 0L : item.getFile().length();
    }

    private long modifiedForSort(FileItem item) {
        return item.getFile().lastModified();
    }

    private boolean isParentFolder(FileItem item) {
        return "..".equals(item.getPresentableFilename());
    }

    public String getFocusedPath() {
        VFileSystem fs = fileSystems.get(focusedSide);
        if (fs instanceof ArchiveFileSystem archiveFs) {
            return archiveFs.getSession().getTempFolderPath().toString();
        }
        return filePanes.get(focusedSide).getPath();
    }

    public String getPath(FocusSide focusSide) {
        VFileSystem fs = fileSystems.get(focusSide);
        if (fs instanceof ArchiveFileSystem archiveFs) {
            return archiveFs.getSession().getTempFolderPath().toString();
        }
        return filePanes.get(focusSide).getPath();
    }

    public String getUnfocusedPath() {
        FocusSide unfocusedSide = focusedSide == LEFT ? RIGHT : LEFT;
        VFileSystem fs = fileSystems.get(unfocusedSide);
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
