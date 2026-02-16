package org.chaiware.acommander.helpers;

import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import org.chaiware.acommander.model.Drive;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.model.WindowsFolder;

import java.io.File;

/** Populating the comboBox dropdown with the drives / Windows folders and favorites */
public class ComboBoxSetup {
    public void setupComboBox(ComboBox<Folder> comboBox) {
        comboBox.setCellFactory(param -> new FolderComboBoxCell());
        comboBox.setButtonCell(new FolderComboBoxCell(true));
        populateComboBox(comboBox);
        setStringInput(comboBox);
        comboBox.getSelectionModel().selectLast();
    }

    /** Enables user input into the combox as string (it will convert it to Folder object) */
    private void setStringInput(ComboBox<Folder> comboBox) {
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Folder folder) {
                if (folder == null) {
                    return "";
                }

                if (folder instanceof Drive drive) {
                    return drive.getPath() + " (" + formatBytes(drive.getAvailableSpace()) + " / " + formatBytes(drive.getTotalSpace()) + ")";
                }

                File pathFile = new File(folder.getPath());
                if (pathFile.exists() && pathFile.getParentFile() == null) {
                    return folder.getPath() + " (" + formatBytes(pathFile.getUsableSpace()) + " / " + formatBytes(pathFile.getTotalSpace()) + ")";
                }

                return folder.getPath();
            }

            @Override
            public Folder fromString(String string) {
                return new Folder(normalizePath(string));
            }
        });
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private String normalizePath(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        // Strip display suffixes like "(120.3 GB / 476.9 GB)" or "(120.3 GB free)" from editable ComboBox text.
        trimmed = trimmed.replaceFirst("\\s*\\(\\s*[\\d.,]+\\s*[KMGTPE]?B\\s*/\\s*[\\d.,]+\\s*[KMGTPE]?B\\s*\\)\\s*$", "");
        trimmed = trimmed.replaceFirst("\\s*\\([^)]*free\\)\\s*$", "");
        return trimmed.trim();
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
