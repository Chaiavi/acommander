package org.chaiware.acommander.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.chaiware.acommander.helpers.BugReportUrl;

import java.util.function.Consumer;

import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** About popup: name, version, copyright, license, project link and runtime versions. */
public final class AboutDialog {
    private AboutDialog() {
    }

    public static void show(Window owner, String themeClass, String version, Consumer<String> openUrl) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("About A Commander");
        dialog.setHeaderText(null);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeType);
        tip((Button) dialog.getDialogPane().lookupButton(closeType), "Close this window.");

        ImageView icon = new ImageView(new Image(AboutDialog.class.getResourceAsStream("/icon.png"), 48, 48, true, true));
        Label name = new Label("A Commander");
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        HBox title = new HBox(12, icon, new VBox(2, name, new Label("Version " + version)));
        title.setAlignment(Pos.CENTER_LEFT);

        Hyperlink link = tip(new Hyperlink(BugReportUrl.PROJECT_URL), "Open the project page in your browser.");
        link.setOnAction(event -> openUrl.accept(BugReportUrl.PROJECT_URL));
        link.setPadding(Insets.EMPTY);

        VBox content = new VBox(8, title,
                new Label("A dual-pane file manager for Windows with a command palette."),
                new Label("Copyright © Chaiware.org"),
                new Label("Released under the Boost Software License 1.0."),
                link,
                new Label("Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"
                        + ", JavaFX " + System.getProperty("javafx.runtime.version")));
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        DialogTheme.apply(dialog, owner, themeClass);
        dialog.showAndWait();
    }
}
