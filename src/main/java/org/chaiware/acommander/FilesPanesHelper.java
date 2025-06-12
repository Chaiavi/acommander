package org.chaiware.acommander;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import lombok.Data;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.chaiware.acommander.FilesPanesHelper.FocusSide.LEFT;
import static org.chaiware.acommander.FilesPanesHelper.FocusSide.RIGHT;

public class FilesPanesHelper {
    public enum FocusSide {LEFT, RIGHT}

    Map<FocusSide, FilePane> filePanes = new HashMap<>();
    private FocusSide focusedSide;


    public FilesPanesHelper(ListView<FileItem> leftFileList, ComboBox<String> leftPathComboBox, ListView<FileItem> rightFileList, ComboBox<String> rightPathComboBox) {
        setFocusedFileList(LEFT);

        filePanes.put(LEFT, new FilePane(leftFileList, leftPathComboBox));
        filePanes.put(RIGHT, new FilePane(rightFileList, rightPathComboBox));
    }

    public void setFocusedFileList(FocusSide focusSide) {
        this.focusedSide = focusSide;
    }

    public void setFileListPath(FocusSide focusSide, String path) {
        filePanes.get(focusSide).getPathComboBox().setValue(path);
        refreshFileListView(focusSide);
    }

    public ListView<FileItem> getFocusedFileList() {
        return filePanes.get(focusedSide).getFileListView();
    }

    public ComboBox<String> getFocusedCombox() {
        return filePanes.get(focusedSide).pathComboBox;
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

        filePanes.get(focusSide).getPathComboBox().getSelectionModel().selectFirst();
    }

    public String getFocusedPath() {
        return filePanes.get(focusedSide).getPath();
    }

    public String getUnfocusedPath() {
        if (focusedSide == LEFT) return filePanes.get(RIGHT).getPath();
        return filePanes.get(LEFT).getPath();
    }

    public FileItem getSelectedItem() {
        return getFocusedFileList().getSelectionModel().getSelectedItem();
    }

    public FocusSide getFocusedSide() {
        return focusedSide;
    }

    @Data
    static class FilePane {
        private final ListView<FileItem> fileListView;
        private final ComboBox<String> pathComboBox;

        public FilePane(ListView<FileItem> fileListView, ComboBox<String> pathComboBox) {
            this.fileListView = fileListView;
            this.pathComboBox = pathComboBox;
        }

        String getPath() {
            return pathComboBox.getValue();
        }
    }
}
