package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Map;
import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Picks one bookmark by name; Up / Down move the selection from anywhere in the dialog. */
public final class BookmarkPickerDialog {
    private BookmarkPickerDialog() {
    }

    /** @param bookmarks name → path; must not be empty */
    public static Optional<String> show(Window owner, String themeClass, String title, String hint,
                                        String actionLabel, String actionTooltip, Map<String, String> bookmarks) {
        OptionsDialog<String> dialog = new OptionsDialog<>(owner, themeClass, title, actionLabel, actionTooltip);

        ListView<String> listView = tip(new ListView<>(), "Your bookmarks; double-click one to choose it.");
        listView.getItems().setAll(bookmarks.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        listView.getSelectionModel().selectFirst();
        listView.setPrefWidth(460);
        listView.setPrefHeight(Math.min(320, Math.max(140, bookmarks.size() * 34)));
        listView.setCellFactory(unused -> new ListCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label nameLabel = new Label(name);
                nameLabel.setStyle("-fx-font-weight: bold;");
                Label pathLabel = new Label(bookmarks.getOrDefault(name, ""));
                pathLabel.setStyle("-fx-opacity: 0.8;");
                setGraphic(new VBox(2, nameLabel, pathLabel));
            }
        });
        listView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                dialog.okButton().fire();
            }
        });
        dialog.okButton().disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

        dialog.dialog().getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.UP && event.getCode() != KeyCode.DOWN) {
                return;
            }
            int index = listView.getSelectionModel().getSelectedIndex();
            index = index < 0 ? 0 : event.getCode() == KeyCode.DOWN
                    ? Math.min(index + 1, listView.getItems().size() - 1) : Math.max(index - 1, 0);
            listView.getSelectionModel().select(index);
            listView.scrollTo(index);
            listView.requestFocus();
            event.consume();
        });

        dialog.add(new VBox(10, new Label(hint), listView));
        dialog.dialog().setOnShown(event -> Platform.runLater(listView::requestFocus));
        return dialog.showAndWait(() -> listView.getSelectionModel().getSelectedItem());
    }
}
