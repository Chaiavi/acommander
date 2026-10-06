package org.chaiware.acommander.helpers;

import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import org.chaiware.acommander.model.Drive;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.model.WindowsFolder;
import org.chaiware.acommander.tools.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Populating the comboBox dropdown with the drives / Windows folders and favorites */
public class ComboBoxSetup {
    private static final Logger logger = LoggerFactory.getLogger(ComboBoxSetup.class);
    private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private final Map<String, String> driveTypesByLetter = detectDriveTypesByLetter();

    public void setupComboBox(ComboBox<Folder> comboBox) {
        comboBox.setCellFactory(param -> new FolderComboBoxCell());
        comboBox.setButtonCell(new FolderComboBoxCell(true));
        comboBox.getItems().setAll(buildItems());
        setStringInput(comboBox);
        comboBox.getSelectionModel().selectLast();
        comboBox.setOnShowing(e -> refreshDrives(comboBox));
    }

    /** Re-reads the drives (a USB disk plugged in or removed since) and keeps the combo's current path. */
    public void refreshDrives(ComboBox<Folder> comboBox) {
        Folder current = comboBox.getValue();
        comboBox.getItems().setAll(buildItems());
        if (comboBox.getValue() != current) {
            comboBox.setValue(current);
        }
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

    /** The drives, then Desktop / Documents / Downloads. */
    List<Folder> buildItems() {
        List<Folder> items = new ArrayList<>();
        for (File root : File.listRoots()) {
            Drive drive = new Drive();
            drive.setPath(root.getAbsolutePath());
            drive.setLetter(root.getAbsolutePath().substring(0, 1));
            // A drive added after startup isn't in the PowerShell cache; FileSystemView types it instead
            drive.setStoreType(getStoreType(root));
            drive.setTotalSpace(root.getTotalSpace());
            drive.setAvailableSpace(root.getUsableSpace());
            items.add(drive);
        }

        String home = System.getProperty("user.home");
        items.add(windowsFolder("Desktop", home + "\\Desktop"));
        items.add(windowsFolder("Documents", home + "\\Documents"));
        items.add(windowsFolder("Downloads", home + "\\Downloads"));
        return items;
    }

    private static WindowsFolder windowsFolder(String name, String path) {
        WindowsFolder folder = new WindowsFolder();
        folder.setName(name);
        folder.setPath(path);
        return folder;
    }

    private String getStoreType(File root) {
        String driveLetter = extractDriveLetter(root);
        if (driveLetter != null) {
            String detectedType = driveTypesByLetter.get(driveLetter);
            if (detectedType != null && !detectedType.isBlank()) {
                return detectedType;
            }
        }

        try {
            if (fileSystemView.isFloppyDrive(root)) {
                return "Removable";
            }

            String systemType = fileSystemView.getSystemTypeDescription(root);
            if (systemType != null && !systemType.isBlank()) {
                return normalizeStoreType(systemType);
            }
        } catch (Exception ignored) {
            // Fall back to simple heuristics below.
        }

        long totalSpace = root.getTotalSpace();
        if (totalSpace > 0) {
            return "Local Disk";
        }
        return "Unknown";
    }

    private String extractDriveLetter(File root) {
        String path = root.getAbsolutePath();
        if (path.length() >= 2 && path.charAt(1) == ':') {
            return String.valueOf(Character.toUpperCase(path.charAt(0)));
        }
        return null;
    }

    private Map<String, String> detectDriveTypesByLetter() {
        Map<String, String> result = new HashMap<>();
        if (!isWindows()) {
            return result;
        }

        String psScript = "$usbLetters = @(); " +
                "try { " +
                "$usbLetters = Get-Partition | Where-Object { $_.DriveLetter } | ForEach-Object { " +
                "$d = Get-Disk -Number $_.DiskNumber -ErrorAction SilentlyContinue; " +
                "if ($d -and $d.BusType -eq 'USB') { $_.DriveLetter.ToString().ToUpperInvariant() } " +
                "} " +
                "} catch {} ; " +
                "Get-CimInstance Win32_LogicalDisk | Where-Object { $_.DeviceID -match '^[A-Z]:$' } | ForEach-Object { " +
                "$letter=$_.DeviceID.Substring(0,1).ToUpperInvariant(); " +
                "$volume=$_.VolumeName; " +
                "$dtype=[int]$_.DriveType; " +
                "$type = switch ($dtype) { " +
                "2 { 'Removable' } " +
                "3 { 'Local Disk' } " +
                "4 { 'Network' } " +
                "5 { 'Optical' } " +
                "6 { 'RAM Disk' } " +
                "Default { 'Unknown' } " +
                "}; " +
                "if ($usbLetters -contains $letter) { $type = 'External/USB' } " +
                "elseif ($volume -and $volume -match 'Google Drive') { $type = 'Virtual (Google Drive)' }; " +
                "('{0}|{1}' -f $letter, $type) " +
                "}";

        try {
            ProcessRunner.Result run = ProcessRunner.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript)
                    .mergeStderr()
                    .run();
            for (String line : run.stdout()) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\\|", 2);
                if (parts.length == 2) {
                    String letter = parts[0].trim().toUpperCase(Locale.ROOT);
                    String type = parts[1].trim();
                    if (!letter.isEmpty() && !type.isEmpty()) {
                        result.put(letter, type);
                    }
                }
            }
            if (run.exitCode() != 0) {
                logger.debug("PowerShell drive type detection exited with code {}", run.exitCode());
            }
        } catch (Exception ex) {
            logger.debug("PowerShell drive type detection failed", ex);
        }

        return result;
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("win");
    }

    private String normalizeStoreType(String rawType) {
        String value = rawType.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        if (lower.contains("remov") || lower.contains("usb")) {
            return "External/Removable";
        }
        if (lower.contains("network")) {
            return "Network";
        }
        if (lower.contains("cd") || lower.contains("dvd") || lower.contains("optical")) {
            return "Optical";
        }
        if (lower.contains("local") || lower.contains("fixed")) {
            return "Local Disk";
        }

        return value;
    }
}
