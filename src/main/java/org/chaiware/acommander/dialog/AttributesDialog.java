package org.chaiware.acommander.dialog;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.chaiware.acommander.helpers.FileAttributesHelper.AttributeChangeRequest;
import org.chaiware.acommander.helpers.FileAttributesHelper.ExistingAttributes;

import java.util.Optional;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks which Windows attributes the selected items should have; starts from the first item's attributes. */
public final class AttributesDialog {
    private AttributesDialog() {
    }

    public static Optional<AttributeChangeRequest> show(Window owner, String themeClass, int selectedCount,
                                                        ExistingAttributes current) {
        OptionsDialog<AttributeChangeRequest> dialog = new OptionsDialog<>(owner, themeClass,
                "Change Attributes", "Apply", "Set the checked attributes and clear the unchecked ones.");

        CheckBox readOnly = tip(new CheckBox("Read-Only"), "Windows and programs may not change or delete the item.");
        CheckBox hidden = tip(new CheckBox("Hidden"), "Hide the item from normal folder listings.");
        CheckBox system = tip(new CheckBox("System"), "Mark the item as an operating system file.");
        CheckBox archive = tip(new CheckBox("Archive"), "Mark the item as changed since the last backup.");
        readOnly.setSelected(current.readOnly());
        hidden.setSelected(current.hidden());
        system.setSelected(current.system());
        archive.setSelected(current.archive());

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.add(readOnly, 0, 0);
        grid.add(hidden, 1, 0);
        grid.add(system, 0, 1);
        grid.add(archive, 1, 1);

        dialog.add(new Label("Selected items: " + selectedCount + " | Checked = set, unchecked = clear"),
                new Separator(), grid);
        return dialog.showAndWait(() -> new AttributeChangeRequest(
                readOnly.isSelected(), hidden.isSelected(), system.isSelected(), archive.isSelected()));
    }
}
