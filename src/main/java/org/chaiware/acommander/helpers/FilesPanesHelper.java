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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.helpers.FilesPanesHelper.FocusSide.RIGHT;

public class FilesPanesHelper {
    public enum FocusSide {LEFT, RIGHT}

    private static final Logger logger = LoggerFactory.getLogger(FilesPanesHelper.class);

    Map<FocusSide, FilePane> filePanes = new HashMap<>();
    @Getter
    private FocusSide focusedSide;


    public FilesPanesHelper(ListView<FileItem> leftFileList, ComboBox<Folder> leftPathComboBox, ListView<FileItem> rightFileList, ComboBox<Folder> rightPathComboBox) {
        setFocusedFileList(LEFT);

        filePanes.put(LEFT, new FilePane(leftFileList, leftPathComboBox));
        filePanes.put(RIGHT, new FilePane(rightFileList, rightPathComboBox));
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
        refreshFileListView(LEFT);
        refreshFileListView(RIGHT);
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
    }

    public String getFocusedPath() {
        return filePanes.get(focusedSide).getPath();
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
            return String.valueOf(pathComboBox.getValue());
        }
    }
}
