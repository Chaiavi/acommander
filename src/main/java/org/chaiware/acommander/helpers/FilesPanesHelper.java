package org.chaiware.acommander.helpers;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import lombok.Data;
import lombok.Getter;
import org.chaiware.acommander.model.FileItem;
import org.chaiware.acommander.model.Folder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.RIGHT;

public class FilesPanesHelper {
    public enum FocusSide {LEFT, RIGHT}
    public enum SortColumn {NAME, SIZE, MODIFIED}

    private static final Logger logger = LoggerFactory.getLogger(FilesPanesHelper.class);

    Map<FocusSide, FilePane> filePanes = new HashMap<>();
    private final Map<FocusSide, SortState> sortStates = new HashMap<>();
    @Getter
    private FocusSide focusedSide;


    public FilesPanesHelper(ListView<FileItem> leftFileList, ComboBox<Folder> leftPathComboBox, ListView<FileItem> rightFileList, ComboBox<Folder> rightPathComboBox) {
        setFocusedFileList(LEFT);

        filePanes.put(LEFT, new FilePane(leftFileList, leftPathComboBox));
        filePanes.put(RIGHT, new FilePane(rightFileList, rightPathComboBox));
        sortStates.put(LEFT, new SortState(SortColumn.NAME, true));
        sortStates.put(RIGHT, new SortState(SortColumn.NAME, true));
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
        pathComboBox.setValue(new Folder(path));
        refreshFileListView(focusSide);
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
     * Loads the files in the path into the ListView
     */
    public void refreshFileListView(FocusSide focusSide) {
        File folder = new File(filePanes.get(focusSide).getPath());
        File[] files = folder.listFiles();

        ObservableList<FileItem> items = filePanes.get(focusSide).getFileListView().getItems();
        items.clear();
        if (folder.getParentFile() != null)
            items.add(new FileItem(folder, ".."));
        if (files != null)
            for (File f : files)
                items.add(new FileItem(f));

        applySort(focusSide);
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
        return filePanes.get(focusedSide).getPath();
    }

    public String getPath(FocusSide focusSide) {
        return filePanes.get(focusSide).getPath();
    }

    public String getUnfocusedPath() {
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

    private record SortState(SortColumn column, boolean ascending) {}
}
