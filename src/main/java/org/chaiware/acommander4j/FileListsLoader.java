package org.chaiware.acommander4j;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.chaiware.acommander4j.FileListsLoader.FocusSide.LEFT;

public class FileListsLoader {
    enum FocusSide {LEFT, RIGHT}

    private final ListView<FileItem> leftFileList;
    private ComboBox<String> leftPathComboBox;
    private final ListView<FileItem> rightFileList;
    private ComboBox<String> rightPathComboBox;
    private String leftPath;
    private String rightPath;
    private ListView<FileItem> focusedFileList;
    Map<FocusSide, FileList> fileLists = new HashMap<>();


    public FileListsLoader(ListView<FileItem> leftFileList, ComboBox<String> leftPathComboBox, ListView<FileItem> rightFileList, ComboBox<String> rightPathComboBox) {
        this.leftFileList = leftFileList;
        this.leftPathComboBox = leftPathComboBox;
        this.rightFileList = rightFileList;
        this.rightPathComboBox = rightPathComboBox;
        setFocusedFileList(LEFT);

        fileLists.put(LEFT, new FileList(leftFileList, leftPathComboBox));
    }

    public void setFocusedFileList(FocusSide focusSide) {
        if (focusSide == LEFT) {
            focusedFileList = leftFileList;
        } else {
            focusedFileList = rightFileList;
        }
    }

    public ListView<FileItem> getFocusedFileList() {
        return focusedFileList;
    }

    /* Refreshes both of the file views */
    public void refreshFileListViews() {
        refreshFileListView(leftFileList);
        refreshFileListView(rightFileList);
    }

    /**
     * Loads the files in the path into the ListView
     */
    public void loadFolder(String path, ListView<FileItem> fileListView) {
        ComboBox<String> folderCombox;
        if (fileListView == leftFileList) {
            leftPath = path;
            folderCombox = leftPathComboBox;
        } else {
            rightPath = path;
            folderCombox = rightPathComboBox;
        }

        folderCombox.getItems().setAll(path);
        folderCombox.getSelectionModel().selectFirst();
        refreshFileListView(fileListView);
    }
//
//    public void loadFolder(FocusSide focusSide) {
//        FileList fileList = fileLists.get(focusSide);
//        fileList.path = fileList.pathComboBox.getValue(); // todo is this needed ?
//
//        ComboBox<String> folderCombox;
//        if (fileListView == leftFileList) {
//            leftPath = path;
//            folderCombox = leftPathComboBox;
//        } else {
//            rightPath = path;
//            folderCombox = rightPathComboBox;
//        }
//
//        folderCombox.getItems().setAll(path);
//        folderCombox.getSelectionModel().selectFirst();
//        refreshFileListView(fileListView);
//    }

    private void refreshFileListView(ListView<FileItem> fileList) {
        File folder = new File(getPath(fileList));
        File[] files = folder.listFiles();

        ObservableList<FileItem> items = fileList.getItems();
        items.clear();
        if (folder.getParentFile() != null)
            items.add(new FileItem(folder, ".."));
        if (files != null)
            for (File f : files)
                items.add(new FileItem(f));
    }

    public String getPath(ListView<FileItem> fileList) {
        if (fileList == leftFileList) return leftPath;
        return rightPath;
    }

    public String getFocusedPath() {
        if (focusedFileList == leftFileList) return leftPath;
        return rightPath;
    }

    public String getUnfocusedPath() {
        if (focusedFileList == leftFileList) return rightPath;
        return leftPath;
    }

    public FileItem getSelectedItem() {
        return getFocusedFileList().getSelectionModel().getSelectedItem();
    }

    public class FileList {
        private ListView<FileItem> fileListView;
        private ComboBox<String> pathComboBox;
        private String path;

        public FileList(ListView<FileItem> fileListView, ComboBox<String> pathComboBox) {
            this.fileListView = fileListView;
            this.pathComboBox = pathComboBox;
            path = pathComboBox.getValue(); // TODO is this needed ?
        }

        String getPath() {
            return pathComboBox.getValue();
        }
    }
}
