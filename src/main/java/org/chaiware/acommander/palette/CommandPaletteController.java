package org.chaiware.acommander.palette;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
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
    private static final int MAX_VISIBLE_ROWS = 8;

    @FXML
    private VBox paletteRoot;
    @FXML
    private TextField queryField;
    @FXML
    private ListView<AppAction> resultsList;

    private final ActionMatcher matcher = new ActionMatcher();
    private final ObservableList<AppAction> filteredActions = FXCollections.observableArrayList();
    private ActionRegistry actionRegistry;
    private ActionContext actionContext;

    @FXML
    public void initialize() {
        resultsList.setItems(filteredActions);
        resultsList.setFixedCellSize(34);
        updateListHeight(0);
        resultsList.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!hasScrollableOverflow() && Math.abs(event.getDeltaY()) > 0) {
                event.consume();
                clampScrollToTop();
            }
        });
        resultsList.setCellFactory(listView -> new ListCell<>() {
            {
                getStyleClass().add("palette-item-cell");
            }

            @Override
            protected void updateItem(AppAction item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Label title = new Label(item.title());
                title.getStyleClass().add("palette-item-title");
                HBox.setHgrow(title, Priority.ALWAYS);
                title.setMaxWidth(Double.MAX_VALUE);

                Label shortcut = new Label(item.shortcut());
                shortcut.getStyleClass().add("palette-item-shortcut");

                HBox row = new HBox(title, shortcut);
                row.setSpacing(12);
                row.getStyleClass().add("palette-item-row");
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
        filteredActions.clear();
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
        if (next != current) {
            resultsList.getSelectionModel().select(next);
            if (hasScrollableOverflow()) {
                resultsList.scrollTo(next);
            }
            return;
        }
        if (current < 0) {
            resultsList.getSelectionModel().select(next);
        }
    }

    public void selectPrevious() {
        int size = resultsList.getItems().size();
        if (size == 0) {
            return;
        }
        int current = resultsList.getSelectionModel().getSelectedIndex();
        int previous = current <= 0 ? 0 : current - 1;
        if (previous != current) {
            resultsList.getSelectionModel().select(previous);
            if (hasScrollableOverflow()) {
                resultsList.scrollTo(previous);
            }
            return;
        }
        if (current < 0) {
            resultsList.getSelectionModel().select(previous);
        }
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
        filteredActions.setAll(matched);
        updateListHeight(matched.size());
        if (!matched.isEmpty()) {
            resultsList.getSelectionModel().selectFirst();
        } else {
            resultsList.getSelectionModel().clearSelection();
        }
        Platform.runLater(this::clampScrollToTop);
    }

    private boolean hasScrollableOverflow() {
        return resultsList.getItems().size() > MAX_VISIBLE_ROWS;
    }

    private void updateListHeight(int itemCount) {
        int visibleRows = Math.min(Math.max(itemCount, 1), MAX_VISIBLE_ROWS);
        double listHeight = (visibleRows * resultsList.getFixedCellSize())
                + resultsList.snappedTopInset()
                + resultsList.snappedBottomInset();
        resultsList.setMinHeight(listHeight);
        resultsList.setPrefHeight(listHeight);
        resultsList.setMaxHeight(listHeight);
    }

    private void clampScrollToTop() {
        if (hasScrollableOverflow()) {
            return;
        }
        resultsList.scrollTo(0);
        ScrollBar verticalBar = (ScrollBar) resultsList.lookup(".scroll-bar:vertical");
        if (verticalBar != null) {
            verticalBar.setValue(verticalBar.getMin());
        }
    }
}
