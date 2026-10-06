package org.chaiware.acommander.dialog;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Window;
import org.chaiware.acommander.services.TransferConflicts.Conflict;
import org.chaiware.acommander.services.TransferConflicts.Policy;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Lists the items that already exist in the target folder and asks what to do with all of them. */
public final class OverwriteDialog {
    private OverwriteDialog() {
    }

    public static Optional<Policy> show(Window owner, String themeClass, String operation, String targetFolder,
                                        List<Conflict> conflicts) {
        OptionsDialog<Policy> dialog = new OptionsDialog<>(owner, themeClass,
                "Items Already Exist", operation, operation + " with the choice below.");

        Label subtitle = new Label(conflicts.size() + " item(s) already exist in " + targetFolder);
        subtitle.setWrapText(true);

        TableView<Conflict> table = tip(new TableView<>(), "The items that are in both folders, with their size and date.");
        table.getColumns().add(column("Name", 220, Conflict::name));
        table.getColumns().add(column("Source", 200, c -> Conflict.describe(c.source(), c.sourceIsNewer())));
        table.getColumns().add(column("Target", 200, c -> Conflict.describe(c.target(), c.targetIsNewer())));
        table.getItems().setAll(conflicts);
        table.setPrefHeight(220);

        ToggleGroup group = new ToggleGroup();
        RadioButton overwrite = radio(group, "Overwrite All", Policy.OVERWRITE,
                "Replace every existing item, including the files inside existing folders.");
        RadioButton skip = radio(group, "Skip Existing", Policy.SKIP,
                "Leave the existing items as they are and transfer only the new ones.");
        RadioButton older = radio(group, "Overwrite Older", Policy.OVERWRITE_OLDER,
                "Replace an existing file only when the one you transfer is newer.");
        skip.setSelected(true);

        dialog.add(subtitle, table, overwrite, skip, older);
        dialog.dialog().getDialogPane().setPrefWidth(680);
        return dialog.showAndWait(() -> (Policy) group.getSelectedToggle().getUserData());
    }

    private static TableColumn<Conflict, String> column(String title, double width, Function<Conflict, String> text) {
        TableColumn<Conflict, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        return column;
    }

    private static RadioButton radio(ToggleGroup group, String label, Policy policy, String tooltip) {
        RadioButton radio = tip(new RadioButton(label), tooltip);
        radio.setToggleGroup(group);
        radio.setUserData(policy);
        return radio;
    }
}
