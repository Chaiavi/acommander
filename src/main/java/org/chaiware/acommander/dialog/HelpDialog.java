package org.chaiware.acommander.dialog;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.chaiware.acommander.helpers.HelpTopics;
import org.chaiware.acommander.helpers.HelpTopics.Entry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** F1: every key and action in collapsible sections per category, a filter that opens the matching ones, and tips. */
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

        Map<String, List<Entry>> categories = HelpTopics.byCategory(entries);
        Map<String, TitledPane> sections = new LinkedHashMap<>();
        VBox sectionBox = new VBox(4);
        categories.keySet().forEach(category -> {
            TitledPane section = tip(new TitledPane(), "Click to show or hide this section's keys and actions.");
            section.setAnimated(false);
            sections.put(category, section);
            sectionBox.getChildren().add(section);
        });
        Label noMatch = new Label("No key or action matches the filter.");
        sectionBox.getChildren().add(noMatch);
        ScrollPane scroll = new ScrollPane(sectionBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Runnable applyFilter = () -> {
            String query = filter.getText();
            boolean anyMatch = false;
            for (Map.Entry<String, TitledPane> section : sections.entrySet()) {
                List<Entry> rows = categories.get(section.getKey()).stream().filter(entry -> HelpTopics.matches(entry, query)).toList();
                TitledPane pane = section.getValue();
                pane.setText(section.getKey() + "  (" + rows.size() + ")");
                pane.setContent(grid(rows));
                pane.setVisible(!rows.isEmpty());
                pane.setManaged(!rows.isEmpty());
                pane.setExpanded(query != null && !query.isBlank());
                anyMatch |= !rows.isEmpty();
            }
            noMatch.setVisible(!anyMatch);
            noMatch.setManaged(!anyMatch);
        };
        filter.textProperty().addListener((obs, old, query) -> applyFilter.run());
        applyFilter.run();

        VBox tips = new VBox(2);
        HelpTopics.TIPS.forEach(text -> tips.getChildren().add(new Label("•  " + text)));

        HBox filterRow = new HBox(8, new Label("Filter:"), filter, fullHelp);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, heading, filterRow, scroll, tips);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(960, 680);
        dialog.setResizable(true);
        dialog.setOnShown(event -> Platform.runLater(filter::requestFocus));
        DialogTheme.apply(dialog, owner, themeClass);
        dialog.showAndWait();
    }

    private static GridPane grid(List<Entry> rows) {
        GridPane grid = new GridPane(16, 6);
        ColumnConstraints keys = new ColumnConstraints(170);
        ColumnConstraints action = new ColumnConstraints(190);
        ColumnConstraints description = new ColumnConstraints();
        description.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keys, action, description);
        for (int row = 0; row < rows.size(); row++) {
            Entry entry = rows.get(row);
            Label keyLabel = new Label(entry.keys());
            keyLabel.setStyle("-fx-font-weight: bold;");
            keyLabel.setWrapText(true);
            Label actionLabel = new Label(entry.action());
            actionLabel.setWrapText(true);
            Label descriptionLabel = new Label(entry.description());
            descriptionLabel.setWrapText(true);
            grid.addRow(row, keyLabel, actionLabel, descriptionLabel);
        }
        return grid;
    }
}
