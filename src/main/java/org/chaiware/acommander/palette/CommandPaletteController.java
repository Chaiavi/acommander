package org.chaiware.acommander.palette;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.chaiware.acommander.actions.ActionContext;
import org.chaiware.acommander.actions.ActionMatcher;
import org.chaiware.acommander.actions.ActionRegistry;
import org.chaiware.acommander.actions.AppAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CommandPaletteController {
    private static final Logger logger = LoggerFactory.getLogger(CommandPaletteController.class);

    @FXML
    private VBox paletteRoot;
    @FXML
    private TextField queryField;
    @FXML
    private ListView<AppAction> resultsList;

    private final ActionMatcher matcher = new ActionMatcher();
    private ActionRegistry actionRegistry;
    private ActionContext actionContext;

    @FXML
    public void initialize() {
        resultsList.setFixedCellSize(34);
        resultsList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(AppAction item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Label title = new Label(item.title());
                title.setStyle("-fx-text-fill: #F8F8F8; -fx-font-size: 14px;");
                HBox.setHgrow(title, Priority.ALWAYS);
                title.setMaxWidth(Double.MAX_VALUE);

                Label shortcut = new Label(item.shortcut());
                shortcut.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 12px;");

                HBox row = new HBox(title, shortcut);
                row.setSpacing(12);
                setGraphic(row);
            }
        });

        queryField.textProperty().addListener((obs, oldValue, newValue) -> refreshResults());
    }

    public void configure(ActionRegistry actionRegistry, ActionContext actionContext) {
        this.actionRegistry = actionRegistry;
        this.actionContext = actionContext;
        refreshResults();
    }

    public void open() {
        paletteRoot.setManaged(true);
        paletteRoot.setVisible(true);
        queryField.clear();
        refreshResults();
        Platform.runLater(queryField::requestFocus);
    }

    public void close() {
        paletteRoot.setVisible(false);
        paletteRoot.setManaged(false);
        queryField.clear();
        resultsList.getItems().clear();
    }

    public boolean isOpen() {
        return paletteRoot.isVisible();
    }

    public void selectNext() {
        int size = resultsList.getItems().size();
        if (size == 0) {
            return;
        }
        int current = resultsList.getSelectionModel().getSelectedIndex();
        int next = current < 0 ? 0 : Math.min(current + 1, size - 1);
        resultsList.getSelectionModel().select(next);
        resultsList.scrollTo(next);
    }

    public void selectPrevious() {
        int size = resultsList.getItems().size();
        if (size == 0) {
            return;
        }
        int current = resultsList.getSelectionModel().getSelectedIndex();
        int previous = current <= 0 ? 0 : current - 1;
        resultsList.getSelectionModel().select(previous);
        resultsList.scrollTo(previous);
    }

    public void executeSelected() {
        AppAction action = resultsList.getSelectionModel().getSelectedItem();
        if (action == null) {
            return;
        }
        logger.info("Executing action from command palette: {}", action.id());
        close();
        action.run(actionContext);
    }

    private void refreshResults() {
        if (actionRegistry == null || actionContext == null) {
            return;
        }
        List<AppAction> matched = matcher.rank(queryField.getText(), actionRegistry.all(), actionContext);
        resultsList.setItems(FXCollections.observableArrayList(matched));
        int visibleRows = Math.min(Math.max(matched.size(), 1), 8);
        resultsList.setPrefHeight((visibleRows * resultsList.getFixedCellSize()) + 2);
        if (!matched.isEmpty()) {
            resultsList.getSelectionModel().selectFirst();
        }
    }
}
