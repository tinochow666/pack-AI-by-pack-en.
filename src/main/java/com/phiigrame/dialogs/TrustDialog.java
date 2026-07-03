package com.phiigrame.dialogs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

import java.nio.file.Path;

/**
 * Modal "Do you trust this folder?" confirmation.  Used the first
 * time a workspace is opened from disk so the user has to explicitly
 * allow the IDE to read and edit files inside it.
 */
public final class TrustDialog {

    private TrustDialog() {}

    /**
     * Show the trust dialog.
     *
     * @return true if the user clicked "Trust", false if they clicked
     *         "Don't trust" or closed the dialog.
     */
    public static boolean showAndWait(Path folder) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Do you trust the authors of this folder?");
        a.setHeaderText("Do you trust the authors of this folder?");
        a.getDialogPane().setMinWidth(560);

        String path = folder == null ? "(unknown)" : folder.toAbsolutePath().normalize().toString();
        String body =
            "Phiigrame is about to open:\n\n" +
            "    " + path + "\n\n" +
            "Code in this folder will be executed by the integrated AI tools " +
            "and by any run configurations you start.  If the folder came from " +
            "an untrusted source, the files may contain code that could harm " +
            "your computer.\n\n" +
            "Do you trust the authors of this folder?";

        Label l = new Label(body);
        l.setWrapText(true);
        l.setPrefWidth(520);
        a.getDialogPane().setContent(new javafx.scene.layout.VBox(l));

        ButtonType trust = new ButtonType("Yes, I trust the authors",
                ButtonBar.ButtonData.OK_DONE);
        ButtonType dont = new ButtonType("No, I don't trust",
                ButtonBar.ButtonData.CANCEL_CLOSE);
        a.getDialogPane().getButtonTypes().setAll(trust, dont);
        ((Region) a.getDialogPane().getContent()).setPrefHeight(Region.USE_COMPUTED_SIZE);
        return a.showAndWait().orElse(dont) == trust;
    }
}
