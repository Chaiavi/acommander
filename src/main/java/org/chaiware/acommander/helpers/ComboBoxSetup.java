package org.chaiware.acommander.helpers;

import javafx.scene.control.ComboBox;
import org.chaiware.acommander.model.Drive;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.model.WindowsFolder;

import java.io.File;

/** Populating the comboBox dropdown with the drives / Windows folders and favorites */
public class ComboBoxSetup {
    public void setupComboBox(ComboBox<Folder> comboBox) {
        comboBox.setCellFactory(param -> new FolderComboBoxCell());

        comboBox.setButtonCell(new FolderComboBoxCell());
        populateComboBox(comboBox);
        comboBox.getSelectionModel().selectLast();
    }

    private void populateComboBox(ComboBox<Folder> comboBox) {
        // Adding drives first
        // File.listRoots() is a simple detection - Should change it to a better detection which will also find usb and drive names
        File[] roots = File.listRoots();
        for (File root : roots) {
            Drive drive = new Drive();
            drive.setPath(root.getAbsolutePath());
            drive.setLetter(root.getAbsolutePath().substring(0, 1));
            drive.setStoreType(getStoreType(root));
            drive.setTotalSpace(root.getTotalSpace());
            drive.setAvailableSpace(root.getUsableSpace());
            comboBox.getItems().add(drive);
        }

        // Adding Windows folders
        addWindowsFolder(comboBox, "Desktop", System.getProperty("user.home") + "\\Desktop");
        addWindowsFolder(comboBox, "Documents", System.getProperty("user.home") + "\\Documents");
        addWindowsFolder(comboBox, "Downloads", System.getProperty("user.home") + "\\Downloads");

        // Adding bookmarked folders ?
        // addBookmarkFolder(comboBox, name, path);
    }

    private void addWindowsFolder(ComboBox<Folder> comboBox, String name, String path) {
        WindowsFolder folder = new WindowsFolder();
        folder.setName(name);
        folder.setPath(path);
        comboBox.getItems().add(folder);
    }

    private String getStoreType(File root) {
        long totalSpace = root.getTotalSpace();
        if (totalSpace > 0) {
            return "Local Disk";
        }
        return "Unknown";
    }
}
