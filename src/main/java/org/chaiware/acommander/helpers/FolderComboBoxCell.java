package org.chaiware.acommander.helpers;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.chaiware.acommander.model.Drive;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.model.WindowsFolder;

import java.io.File;

/** The ComboBox rows look&feel */
public class FolderComboBoxCell extends ListCell<Folder> {
    private final boolean compact;

    public FolderComboBoxCell() {
        this(false);
    }

    public FolderComboBoxCell(boolean compact) {
        this.compact = compact;
    }

    @Override
    protected void updateItem(Folder item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            setGraphic(compact ? createCompactCellContent(item) : createCellContent(item));
            setText(null);
        }
    }

    private HBox createCompactCellContent(Folder folder) {
        HBox container = new HBox();
        container.setPadding(new Insets(2, 4, 2, 4));

        String text;
        if (folder instanceof Drive drive) {
            text = drive.getLetter() + ": (" + formatBytes(drive.getAvailableSpace()) + " free)";
        } else if (folder instanceof WindowsFolder winFolder) {
            text = winFolder.getName() + " (" + winFolder.getPath() + ") " + formatFreeForPath(winFolder.getPath());
        } else {
            text = folder.getPath() + " " + formatFreeForPath(folder.getPath());
        }

        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: -ac-input-text;");
        container.getChildren().add(label);
        return container;
    }

    private HBox createCellContent(Folder folder) {
        HBox container = new HBox(8);
        container.setPadding(new Insets(4, 8, 4, 8));

        if (folder instanceof Drive) {
            return createDriveContent((Drive) folder, container);
        } else if (folder instanceof WindowsFolder) {
            return createFolderContent((WindowsFolder) folder, container);
        } else {
            return createGenericFolderContent(folder, container);
        }
    }

    /** Drive info in the combox dropdown */
    private HBox createDriveContent(Drive drive, HBox container) {
        VBox driveInfo = new VBox(2);
        Label driveLabel = new Label(drive.getLetter() + ": (" + drive.getStoreType() + ", " + formatBytes(drive.getAvailableSpace()) + " free)");
        driveLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        String spaceInfo = formatBytes(drive.getAvailableSpace()) + " / " + formatBytes(drive.getTotalSpace());
        Label spaceLabel = new Label(spaceInfo);
        spaceLabel.setStyle("-fx-font-size: 11px;");
        spaceLabel.getStyleClass().add("drive-space-label");

        driveInfo.getChildren().addAll(driveLabel, spaceLabel);
        container.getChildren().add(driveInfo);

        return container;
    }

    /** Folder info in the combox dropdown */
    private HBox createFolderContent(WindowsFolder folder, HBox container) {
        VBox folderInfo = new VBox(2);
        Label nameLabel = new Label(folder.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label pathLabel = new Label(folder.getPath());
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        folderInfo.getChildren().addAll(nameLabel, pathLabel);
        container.getChildren().add(folderInfo);

        return container;
    }

    /** Generic Folder info in the combox dropdown */
    private HBox createGenericFolderContent(Folder folder, HBox container) {
        Label icon = new Label("[F]");
        icon.setStyle("-fx-font-size: 12px;");
        Label pathLabel = new Label(folder.getPath());
        pathLabel.setStyle("-fx-font-size: 13px;");
        container.getChildren().addAll(icon, pathLabel);
        return container;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private String formatFreeForPath(String path) {
        if (path == null || path.isBlank()) {
            return "(free: -)";
        }

        File root = new File(path);
        while (root.getParentFile() != null) {
            root = root.getParentFile();
        }

        if (!root.exists()) {
            return "(free: -)";
        }

        return "(" + formatBytes(root.getUsableSpace()) + " free)";
    }
}
