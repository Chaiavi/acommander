package org.chaiware.acommander.helpers;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import lombok.Data;
import org.chaiware.acommander.commands.ExternalCommandListener;
import org.chaiware.acommander.helpers.PaneSorter.SortColumn;
import org.chaiware.acommander.helpers.PaneSorter.SortState;
import org.chaiware.acommander.model.ArchiveSession;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.vfs.*;
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

    private static final Logger logger = LoggerFactory.getLogger(FilesPanesHelper.class);
    private final VfsManager vfsManager = new VfsManager();

    Map<FocusSide, FilePane> filePanes = new HashMap<>();
    private final Map<FocusSide, SortState> sortStates = new EnumMap<>(FocusSide.class);
    private final Map<FocusSide, VFileSystem> fileSystems = new EnumMap<>(FocusSide.class);
    private final Map<FocusSide, String> currentInternalPaths = new EnumMap<>(FocusSide.class);
    private final Map<FocusSide, Loads> loads = new EnumMap<>(FocusSide.class);
    private FocusSide focusedSide = LEFT;
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

    public VFileSystem getFileSystem(FocusSide side) {
        return fileSystems.get(side);
    }

    public VFileSystem getFocusedFileSystem() {
        return fileSystems.get(focusedSide);
    }

    public VFileSystem getUnfocusedFileSystem() {
        return fileSystems.get(otherSide(focusedSide));
    }

    public void setFileSystem(FocusSide side, VFileSystem fs) throws IOException {
        setFileSystem(side, fs, "/");
    }

    public void setFileSystem(FocusSide side, VFileSystem fs, String initialPath) throws IOException {
        logger.info("Switching {} pane to file system: {}", side, fs.getIdentifier());
        VFileSystem oldFs = fileSystems.put(side, fs);
        fs.setExternalCommandListener(externalCommandListener);

        if (initialPath != null) {
            setFileListPath(side, initialPath);
        } else {
            refreshFileListView(side);
        }
        // Last: closing an archive repacks it and may throw after the pane has already moved on.
        vfsManager.closeFileSystem(oldFs);
    }

    public FilesPanesHelper(ListView<FileItem> leftFileList, ComboBox<Folder> leftPathComboBox, ListView<FileItem> rightFileList, ComboBox<Folder> rightPathComboBox) {
        filePanes.put(LEFT, new FilePane(leftFileList, leftPathComboBox));
        filePanes.put(RIGHT, new FilePane(rightFileList, rightPathComboBox));
        sortStates.put(LEFT, SortState.DEFAULT);
        sortStates.put(RIGHT, SortState.DEFAULT);
        loads.put(LEFT, new Loads());
        loads.put(RIGHT, new Loads());
        
        // Initialize with default local file systems
        fileSystems.put(LEFT, vfsManager.createLocalFileSystem(""));
        fileSystems.put(RIGHT, vfsManager.createLocalFileSystem(""));
        currentInternalPaths.put(LEFT, "");
        currentInternalPaths.put(RIGHT, "");
    }
    
    /** Closes every pane's file system at app exit; throws once with every archive that could not be saved. */
    public void cleanup() throws IOException {
        List<String> failures = new ArrayList<>();
        for (VFileSystem fs : fileSystems.values()) {
            try {
                vfsManager.closeFileSystem(fs);
            } catch (IOException e) {
                failures.add(e.getMessage());
            }
        }
        fileSystems.clear();
        if (!failures.isEmpty()) {
            throw new IOException(String.join("\n", failures));
        }
    }

    public void setFocusedFileList(FocusSide focusSide) {
        this.focusedSide = focusSide;
    }

    public void selectFileItem(boolean isFocused, FileItem fileItem) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> selectFileItem(isFocused, fileItem));
            return;
        }
        FocusSide side = isFocused ? focusedSide : otherSide(focusedSide);
        whenShown(side, () -> {
            ListView<FileItem> list = filePanes.get(side).getFileListView();
            list.getSelectionModel().clearSelection();
            list.getSelectionModel().select(fileItem);
        });
    }

    /** Selects {@code name} in {@code folder} on that pane, also after its next refresh (items match by equality). */
    public void selectFileItem(boolean isFocused, String folder, String name) {
        VFileSystem fs = isFocused ? getFocusedFileSystem() : getUnfocusedFileSystem();
        selectFileItem(isFocused, fs instanceof FtpFileSystem
                ? new FileItem(null, name, 0, 0, false) // FTP items have no path, only a name
                : new FileItem(Path.of(folder, name)));
    }

    /**
     * Selects the items named {@code names} on {@code side}, but only while that pane still shows {@code folder}: an
     * operation that ends after the user moved on must not change their selection.
     */
    public void selectNames(FocusSide side, String folder, Collection<String> names) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> selectNames(side, folder, names));
            return;
        }
        whenShown(side, () -> {
            if (names.isEmpty() || !samePath(getPath(side), folder)) {
                return;
            }
            ListView<FileItem> list = filePanes.get(side).getFileListView();
            Set<String> wanted = new HashSet<>(names);
            int[] indices = java.util.stream.IntStream.range(0, list.getItems().size())
                    .filter(i -> wanted.contains(list.getItems().get(i).getPresentableFilename()))
                    .toArray();
            if (indices.length == 0) {
                return;
            }
            list.getSelectionModel().clearSelection();
            list.getSelectionModel().selectIndices(indices[0], indices);
            list.getFocusModel().focus(indices[0]);
            list.scrollTo(indices[0]);
        });
    }

    /** Selects row {@code index} (kept in range) on {@code side} while that pane still shows {@code folder}. */
    public void selectIndex(FocusSide side, String folder, int index) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> selectIndex(side, folder, index));
            return;
        }
        whenShown(side, () -> {
            ListView<FileItem> list = filePanes.get(side).getFileListView();
            if (list.getItems().isEmpty() || !samePath(getPath(side), folder)) {
                return;
            }
            int bounded = Math.min(Math.max(index, 0), list.getItems().size() - 1);
            list.getSelectionModel().clearAndSelect(bounded);
            list.getFocusModel().focus(bounded);
            list.scrollTo(bounded);
        });
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

        selectNameOrFirst(focusSide, preferredSelectionName);
    }
    /**
     * Enters an archive. For read-write archives, extracts to temp folder.
     * For read-only archives, also extracts but marks as read-only.
     */
    public void enterArchive(FocusSide focusSide, String archivePath) throws IOException {
        VFileSystem fs = vfsManager.openArchive(archivePath);
        currentInternalPaths.put(focusSide, "");
        setFileSystem(focusSide, fs, null);

        Platform.runLater(() -> {
            ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
            pathComboBox.setValue(new ArchiveFolder(fs.getDisplayName()));
            ensureFirstEntrySelected(focusSide);
        });

        logger.info("Entered archive ({} mode): {}", fs.isReadOnly() ? "READ_ONLY" : "READ_WRITE", archivePath);
    }

    /** Leaves the archive, shows its parent folder with the archive selected, then repacks it if it changed. */
    public void exitArchive(FocusSide focusSide) throws IOException {
        if (!(fileSystems.get(focusSide) instanceof ArchiveFileSystem archiveFs)) {
            return;
        }
        File archive = new File(archiveFs.getSession().getArchivePath());
        VFileSystem local = vfsManager.createLocalFileSystem("");
        local.setExternalCommandListener(externalCommandListener);
        fileSystems.put(focusSide, local);
        if (archive.getParentFile() != null) {
            setFileListPath(focusSide, archive.getParent(), archive.getName());
        }
        vfsManager.closeFileSystem(archiveFs);
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
            selectNameOrFirst(focusSide, null);
        });
        
        logger.debug("Entered archive subdirectory: {}", dirName);
    }
    
    /**
     * Navigates up one level in the archive hierarchy.
     * If at root, exits the archive and shows the archive file's parent folder.
     */
    public void goUpInArchive(FocusSide focusSide) throws IOException {
        VFileSystem fs = fileSystems.get(focusSide);
        if (!(fs instanceof ArchiveFileSystem currentArchiveFs)) {
            return;
        }

        ArchiveSession currentSession = currentArchiveFs.getSession();
        ArchiveSession parentSession = currentSession.getParent();
        if (currentSession.isRoot() || parentSession == null) {
            exitArchive(focusSide);
        } else {
            String childDirName = leafName(currentSession.getEntryPath());
            ArchiveFileSystem parentFs = new ArchiveFileSystem(parentSession, vfsManager.getArchiveManager());
            fileSystems.put(focusSide, parentFs);
            currentInternalPaths.put(focusSide, parentSession.getEntryPath());

            Platform.runLater(() -> {
                ComboBox<Folder> pathComboBox = filePanes.get(focusSide).getPathComboBox();
                pathComboBox.setValue(new ArchiveFolder(parentFs.getDisplayName()));

                refreshFileListView(focusSide);
                selectNameOrFirst(focusSide, childDirName);
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

    public ListView<FileItem> getFileList(boolean isFocused) {
        if (isFocused)
            return filePanes.get(focusedSide).getFileListView();
        else
            return filePanes.get(otherSide(focusedSide)).getFileListView();
    }

    /* Refreshes both of the file views; safe to call from any thread */
    public void refreshFileListViews() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshFileListViews);
            return;
        }
        refreshFileListView(LEFT);
        refreshFileListView(RIGHT);
    }

    /**
     * Lists the pane's folder in the background and shows it on the FX thread; for archives, the temp folder. Only
     * the newest request per pane is shown, so a slow listing can never replace a newer one. Moving to another folder
     * empties the pane at once (nothing stale to act on); refreshing the same folder keeps its rows and selection.
     * Safe to call from any thread.
     */
    public void refreshFileListView(FocusSide focusSide) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> refreshFileListView(focusSide));
            return;
        }
        VFileSystem fs = fileSystems.get(focusSide);
        String path = currentInternalPaths.get(focusSide) != null ? currentInternalPaths.get(focusSide)
                : filePanes.get(focusSide).getPath();
        SortState sort = sortState(focusSide);
        Loads paneLoads = loads.get(focusSide);
        ListView<FileItem> listView = filePanes.get(focusSide).getFileListView();
        boolean sameFolder = paneLoads.shows(fs, path);
        if (!sameFolder) {
            listView.getItems().clear();
        }
        long load = paneLoads.start();
        logger.debug("Listing {} on {} (load {})", path, fs.getIdentifier(), load);
        BackgroundTasks.supply(() -> {
            try {
                return PaneSorter.sort(fs.listContents(path), sort);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }).whenComplete((contents, failure) -> Platform.runLater(() -> {
            if (!paneLoads.isLatest(load)) {
                return;
            }
            List<FileItem> shown = List.of();
            if (failure != null) {
                logger.error("Failed to list contents of {} using {}: {}", path, fs.getIdentifier(), failure.getMessage());
                if (sameFolder) {
                    paneLoads.shown(load, fs, path);
                    return;
                }
            } else {
                if (fs instanceof FtpFileSystem ftpFs) {
                    ftpFs.setCurrentPath(path);
                }
                shown = sort.equals(sortState(focusSide)) ? contents : PaneSorter.sort(contents, sortState(focusSide));
            }
            FileItem previouslySelected = listView.getSelectionModel().getSelectedItem();
            listView.getItems().setAll(shown);
            if (previouslySelected != null) {
                listView.getSelectionModel().select(previouslySelected);
            }
            if (listView.getSelectionModel().getSelectedIndex() < 0 && !shown.isEmpty()) {
                listView.getSelectionModel().selectFirst();
                listView.getFocusModel().focus(0);
            }
            paneLoads.shown(load, fs, path);
        }));
    }

    /** Runs {@code action} now, or once the pane shows its newest listing. FX thread only. */
    private void whenShown(FocusSide side, Runnable action) {
        loads.get(side).whenShown(action);
    }

    public void ensureFirstEntrySelected(FocusSide focusSide) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> ensureFirstEntrySelected(focusSide));
            return;
        }
        whenShown(focusSide, () -> {
            ListView<FileItem> listView = filePanes.get(focusSide).getFileListView();
            if (listView.getItems().isEmpty()) {
                return;
            }
            listView.getSelectionModel().selectFirst();
            listView.getFocusModel().focus(0);
        });
    }

    public void toggleSort(FocusSide focusSide, SortColumn column) {
        sortStates.put(focusSide, sortState(focusSide).toggle(column));
        applySort(focusSide);
    }

    public SortColumn getSortColumn(FocusSide focusSide) {
        return sortState(focusSide).column();
    }

    public boolean isSortAscending(FocusSide focusSide) {
        return sortState(focusSide).ascending();
    }

    private SortState sortState(FocusSide focusSide) {
        return sortStates.getOrDefault(focusSide, SortState.DEFAULT);
    }

    /** Re-sorts what the pane shows; a listing still loading sorts itself when it arrives. */
    private void applySort(FocusSide focusSide) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> applySort(focusSide));
            return;
        }
        ObservableList<FileItem> items = filePanes.get(focusSide).getFileListView().getItems();
        items.setAll(PaneSorter.sort(items, sortState(focusSide)));
    }

    /** After the newest listing is shown: selects {@code filename}, else the first row. */
    private void selectNameOrFirst(FocusSide focusSide, String filename) {
        whenShown(focusSide, () -> {
            if (!selectItemByPresentableFilename(focusSide, filename)) {
                ListView<FileItem> listView = filePanes.get(focusSide).getFileListView();
                if (!listView.getItems().isEmpty()) {
                    listView.getSelectionModel().clearAndSelect(0);
                    listView.getFocusModel().focus(0);
                }
            }
        });
    }

    private static FocusSide otherSide(FocusSide side) {
        return side == LEFT ? RIGHT : LEFT;
    }

    /**
     * One pane's listings: a number per request, so only the newest is shown, and the actions waiting for it. FX
     * thread only; no JavaFX types, so it is unit-tested.
     */
    static final class Loads {
        private long started;
        private long shownLoad;
        private VFileSystem shownFs;
        private String shownPath;
        private final List<Runnable> waiting = new ArrayList<>();

        long start() {
            return ++started;
        }

        boolean isLatest(long load) {
            return load == started;
        }

        /** True when the pane already shows {@code path} on {@code fs}, so a refresh can keep its rows meanwhile. */
        boolean shows(VFileSystem fs, String path) {
            return shownFs == fs && Objects.equals(shownPath, path);
        }

        void shown(long load, VFileSystem fs, String path) {
            shownLoad = load;
            shownFs = fs;
            shownPath = path;
            if (isLatest(load)) {
                List<Runnable> ready = new ArrayList<>(waiting);
                waiting.clear();
                ready.forEach(Runnable::run);
            }
        }

        void whenShown(Runnable action) {
            if (shownLoad == started) {
                action.run();
            } else {
                waiting.add(action);
            }
        }
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
        return getPath(focusedSide);
    }

    /** The pane's folder: the internal path on local and FTP panes, the extracted temp folder inside an archive. */
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

    public boolean isAt(FocusSide focusSide, String path) {
        return samePath(getPath(focusSide), path);
    }

    public String getUnfocusedPath() {
        return getPath(otherSide(focusedSide));
    }

    public FileItem getSelectedItem() {
        return getFileList(true).getSelectionModel().getSelectedItem();
    }

    public List<FileItem> getSelectedItems() {
        return getFileList(true).getSelectionModel().getSelectedItems();
    }

    /** The item under the cursor of the focused pane, which may differ from the selection (NC-style F3/F4). */
    public FileItem getCursorItem() {
        FileItem focused = getFileList(true).getFocusModel().getFocusedItem();
        return focused != null ? focused : getSelectedItem();
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
        int[] indices = invertedIndices(listView.getItems(), listView.getSelectionModel().getSelectedIndices());
        listView.getSelectionModel().clearSelection();
        if (indices.length > 0) {
            listView.getSelectionModel().selectIndices(-1, indices);
        }
    }

    /** Indices of {@code items} not in {@code selected}, skipping ".."; one pass over the list. */
    static int[] invertedIndices(List<FileItem> items, Collection<Integer> selected) {
        Set<Integer> selectedSet = new HashSet<>(selected);
        int[] result = new int[items.size()];
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            if (!selectedSet.contains(i) && !"..".equals(items.get(i).getPresentableFilename())) {
                result[count++] = i;
            }
        }
        return Arrays.copyOf(result, count);
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
            return value == null || value.getPath() == null ? "" : withoutDriveSpace(value.getPath());
        }
    }

    /** A path-combo entry without its "(12 GB / 100 GB)" or "(… free)" drive-space suffix. */
    private static String withoutDriveSpace(String path) {
        return path.trim()
                .replaceFirst("\\s*\\(\\s*[\\d.,]+\\s*[KMGTPE]?B\\s*/\\s*[\\d.,]+\\s*[KMGTPE]?B\\s*\\)\\s*$", "")
                .replaceFirst("\\s*\\([^)]*free\\)\\s*$", "")
                .trim();
    }

    private static boolean samePath(String left, String right) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        return withoutDriveSpace(left).equalsIgnoreCase(withoutDriveSpace(right));
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
}
