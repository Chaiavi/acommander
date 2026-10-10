package org.chaiware.acommander.dialog;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import org.chaiware.acommander.services.LockingPrograms.Locker;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Lists the programs that hold the items being deleted open and asks whether to end them and delete again. */
public final class LockedItemsDialog {
    private LockedItemsDialog() {
    }

    /** True when the user chose to end the programs and delete. */
    public static Optional<Boolean> show(Window owner, String themeClass, List<String> itemNames, List<Locker> lockers) {
        OptionsDialog<Boolean> dialog = new OptionsDialog<>(owner, themeClass, "Items in Use", "End Programs and Delete",
                "Ends the programs below, losing any unsaved work in them, then deletes the items again.");

        Label subtitle = new Label("These programs have " + String.join(", ", itemNames) + " open:");
        subtitle.setWrapText(true);

        TableView<Locker> table = tip(new TableView<>(), "The programs that hold the items open, with their process id and user.");
        table.getColumns().add(column("Program", 260, Locker::name));
        table.getColumns().add(column("PID", 80, locker -> String.valueOf(locker.pid())));
        table.getColumns().add(column("User", 160, Locker::user));
        table.getItems().setAll(lockers);
        table.setPrefHeight(180);

        Label warning = new Label("Unsaved work in these programs will be lost.");
        warning.setWrapText(true);

        dialog.add(subtitle, table, warning);
        dialog.dialog().getDialogPane().setPrefWidth(560);
        return dialog.showAndWait(() -> true);
    }

    private static TableColumn<Locker, String> column(String title, double width, Function<Locker, String> text) {
        TableColumn<Locker, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        return column;
    }
}
