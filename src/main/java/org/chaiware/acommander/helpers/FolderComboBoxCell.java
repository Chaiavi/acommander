package org.chaiware.acommander.helpers;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.chaiware.acommander.model.Drive;
import org.chaiware.acommander.model.Folder;
import org.chaiware.acommander.model.WindowsFolder;

/** The ComboBox rows look&feel */
public class FolderComboBoxCell extends ListCell<Folder> {
    @Override
    protected void updateItem(Folder item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            setGraphic(createCellContent(item));
            setText(null); // We use graphic instead of text for better layout
        }
    }

    private HBox createCellContent(Folder folder) {
        HBox container = new HBox(8); // 8px spacing
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
        Label icon = new Label("💾");
        icon.setStyle("-fx-font-size: 16px;");
        VBox driveInfo = new VBox(2);
        Label driveLabel = new Label(drive.getLetter() + ": (" + drive.getStoreType() + ")");
        driveLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        String spaceInfo = formatBytes(drive.getAvailableSpace()) + " / " + formatBytes(drive.getTotalSpace());
        Label spaceLabel = new Label(spaceInfo);
        spaceLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        driveInfo.getChildren().addAll(driveLabel, spaceLabel);
        container.getChildren().addAll(icon, driveInfo);

        return container;
    }

    /** Folder info in the combox dropdown */
    private HBox createFolderContent(WindowsFolder folder, HBox container) {
        Label icon = new Label("📁");
        icon.setStyle("-fx-font-size: 16px;");
        VBox folderInfo = new VBox(2);
        Label nameLabel = new Label(folder.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label pathLabel = new Label(folder.getPath());
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        folderInfo.getChildren().addAll(nameLabel, pathLabel);
        container.getChildren().addAll(icon, folderInfo);

        return container;
    }

    /** Generic Folder info in the combox dropdown */
    private HBox createGenericFolderContent(Folder folder, HBox container) {
        Label icon = new Label("📂");
        icon.setStyle("-fx-font-size: 16px;");
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
}