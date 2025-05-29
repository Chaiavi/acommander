package org.chaiware.acommander4j;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;

import java.io.File;

public class FileListsLoader {
    private final ListView<FileItem> leftFileList;
    private final ListView<FileItem> rightFileList;

    public FileListsLoader(ListView<FileItem> leftFileList, ListView<FileItem> rightFileList) {
        this.leftFileList = leftFileList;
        this.rightFileList = rightFileList;
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
        ComboBox<String> folderCombox = (ComboBox<String>) fileListView.getProperties().get("PathCombox");
        folderCombox.getItems().setAll(path);
        folderCombox.getSelectionModel().selectFirst();
        refreshFileListView(fileListView);
    }

    private void refreshFileListView(ListView<FileItem> fileList) {
        ComboBox<String> pathComboBox = (ComboBox<String>) fileList.getProperties().get("PathCombox");
        File folder = new File(pathComboBox.getItems().get(0));
        File[] files = folder.listFiles();

        ObservableList<FileItem> items = fileList.getItems();
        items.clear();
        if (folder.getParentFile() != null)
            items.add(new FileItem(folder, ".."));
        if (files != null)
            for (File f : files)
                items.add(new FileItem(f));
    }
}
