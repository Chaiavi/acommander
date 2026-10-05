package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;
import org.chaiware.acommander.model.FileItem;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Lists the files Find in Files found; Enter or a double-click goes to the selected one. */
public final class FoundFilesDialog {
    private FoundFilesDialog() {
    }

    public static Optional<FileItem> show(Window owner, String themeClass, List<String> files) {
        OptionsDialog<FileItem> dialog = new OptionsDialog<>(owner, themeClass,
                "Files Found", "Go to File", "Show the selected file in the focused pane.");

        ListView<FileItem> fileList = tip(new ListView<>(), "Files that contain the text; double-click one to go to it.");
        fileList.getItems().setAll(files.stream().map(filename -> new FileItem(Path.of(filename))).toList());
        fileList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(FileItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getFullPath());
            }
        });
        fileList.getSelectionModel().selectFirst();
        fileList.setPrefSize(980, 420);
        fileList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                dialog.okButton().fire();
            }
        });
        dialog.okButton().disableProperty().bind(fileList.getSelectionModel().selectedItemProperty().isNull());

        dialog.add(fileList);
        dialog.dialog().setResizable(true);
        dialog.dialog().setOnShown(event -> Platform.runLater(fileList::requestFocus));
        return dialog.showAndWait(() -> fileList.getSelectionModel().getSelectedItem());
    }
}
