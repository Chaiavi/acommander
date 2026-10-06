package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.chaiware.acommander.helpers.HelpTopics;
import org.chaiware.acommander.helpers.HelpTopics.Entry;

import java.util.List;
import java.util.function.Function;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** F1: every key and action in one filterable table, plus a few tips. */
public final class HelpDialog {
    private HelpDialog() {
    }

    public static void show(Window owner, String themeClass, String title, List<Entry> entries, Runnable openFullHelp) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeType);
        tip((Button) dialog.getDialogPane().lookupButton(closeType), "Close the help.");

        Label heading = new Label(title);
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        TextField filter = tip(new TextField(), "Type words to show only the matching keys and actions.");
        filter.setPromptText("Filter, e.g. copy, F5, pdf");
        HBox.setHgrow(filter, Priority.ALWAYS);
        Button fullHelp = tip(new Button("Open Full Help in Browser"), "Open the full help page in your web browser.");
        fullHelp.setOnAction(event -> openFullHelp.run());

        FilteredList<Entry> rows = new FilteredList<>(FXCollections.observableArrayList(entries));
        filter.textProperty().addListener((obs, old, query) -> rows.setPredicate(entry -> HelpTopics.matches(entry, query)));
        TableView<Entry> table = tip(new TableView<>(rows), "Every key and action; actions without a key are in the Command Palette (Ctrl+Shift+P).");
        table.getColumns().add(column("Keys", 190, Entry::keys));
        table.getColumns().add(column("Action", 200, Entry::action));
        table.getColumns().add(column("What It Does", 520, Entry::description));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No key or action matches the filter."));
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox tips = new VBox(2);
        HelpTopics.TIPS.forEach(text -> tips.getChildren().add(new Label("•  " + text)));

        HBox filterRow = new HBox(8, new Label("Filter:"), filter, fullHelp);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, heading, filterRow, table, tips);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(960, 680);
        dialog.setResizable(true);
        dialog.setOnShown(event -> Platform.runLater(filter::requestFocus));
        DialogTheme.apply(dialog, owner, themeClass);
        dialog.showAndWait();
    }

    private static TableColumn<Entry, String> column(String title, double width, Function<Entry, String> value) {
        TableColumn<Entry, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }
}
