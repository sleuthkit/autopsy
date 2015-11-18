/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.controlsfx.dialog.ProgressDialog;
import org.controlsfx.tools.Borders;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;

/**
 *
 */
public class PromptDialogManager {

    private static final Logger LOGGER = Logger.getLogger(PromptDialogManager.class.getName());

    private static final ButtonType SHOW_TIMELINE = new ButtonType("Show Timeline", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CONTINUE_NO_UPDATE = new ButtonType("Continue Without Updating", ButtonBar.ButtonData.CANCEL_CLOSE);
    private static final ButtonType UPDATE = new ButtonType("Update", ButtonBar.ButtonData.OK_DONE);

    private static final Image LOGO;

    static {
        Image x = null;
        try {
            x = new Image(new URL("nbresloc:/org/netbeans/core/startup/frame.gif").openStream());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to load branded icon for progress dialog.", ex);
        }
        LOGO = x;
    }
    private Dialog<?> currentDialog;

    private final TimeLineController controller;

    PromptDialogManager(TimeLineController controller) {
        this.controller = controller;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean bringCurrentDialogToFront() {
        if (currentDialog != null && currentDialog.isShowing()) {
            ((Stage) currentDialog.getDialogPane().getScene().getWindow()).toFront();
            return true;
        }
        return false;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({"PromptDialogManager.progressDialog.title=Populating Timeline Data"})
    public void showProgressDialog(CancellationProgressTask<?> task) {

        currentDialog = new ProgressDialog(task);
        currentDialog.setTitle(Bundle.PromptDialogManager_progressDialog_title());
        currentDialog.headerTextProperty().bind(task.titleProperty());

        DialogPane dialogPane = currentDialog.getDialogPane();
        dialogPane.setPrefWidth(400);

        dialogPane.setPrefHeight(200);
        task.setOnCancelled(cancelled -> currentDialog.close());
        task.setOnSucceeded(succeeded -> currentDialog.close());

        dialogPane.getButtonTypes().setAll(ButtonType.CANCEL);
        final Node cancelButton = dialogPane.lookupButton(ButtonType.CANCEL);
        cancelButton.disableProperty().bind(task.cancellableProperty().not());
        currentDialog.setOnCloseRequest(closeRequest -> {

            if (task.isRunning()) {
                closeRequest.consume();
            }
            if (task.isCancellable() && task.isCancelRequested() == false) {
                task.requestCancel();
            }
        });

        Stage stage = (Stage) dialogPane.getScene().getWindow();
        stage.getIcons().setAll(LOGO);
        currentDialog.show();
    }

    /**
     * prompt the user that ingest is running and the db may not end up
     * complete.
     *
     * @return true if they want to continue anyways
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({"PromptDialogManager.confirmDuringIngest.headerText=You are trying to generate a timeline before ingest has been completed."
        + "\nThe timeline may be incomplete.",
        "PromptDialogManager.confirmDuringIngest.contentText=Do you want to continue?"})
    synchronized boolean confirmDuringIngest() {
        currentDialog = new Alert(Alert.AlertType.CONFIRMATION, Bundle.PromptDialogManager_confirmDuringIngest_contentText(), SHOW_TIMELINE, ButtonType.CANCEL);
        currentDialog.initModality(Modality.APPLICATION_MODAL);
        currentDialog.setHeaderText(Bundle.PromptDialogManager_confirmDuringIngest_headerText());
        brandDialog();
        return currentDialog.showAndWait().map(SHOW_TIMELINE::equals).orElse(false);
    }

    private void brandDialog() {
        Stage stage = (Stage) currentDialog.getDialogPane().getScene().getWindow();
        stage.setTitle(Bundle.Timeline_confirmation_dialogs_title());
        stage.getIcons().setAll(LOGO);
    }

    @NbBundle.Messages({"PromptDialogManager.rebuildPrompt.headerText=The Timeline database is incomplete and/or out of date."
        + "\nSome events may be missing or inaccurate and some features may be unavailable.",
        "PromptDialogManager.rebuildPrompt.details=Details:"})
    boolean confirmRebuild(ArrayList<String> rebuildReasons) {
        currentDialog = new Alert(Alert.AlertType.CONFIRMATION, Bundle.TimeLinecontroller_updateNowQuestion(), UPDATE, CONTINUE_NO_UPDATE);
        currentDialog.initModality(Modality.APPLICATION_MODAL);
        currentDialog.setHeaderText(Bundle.PromptDialogManager_rebuildPrompt_headerText());
        brandDialog();

        DialogPane dialogPane = currentDialog.getDialogPane();
        ListView<String> listView = new ListView<>(FXCollections.observableArrayList(rebuildReasons));
        listView.setCellFactory(lstView -> new WrappingListCell());
        listView.setMaxHeight(75);
        Node wrappedListView = Borders.wrap(listView).lineBorder().title(Bundle.PromptDialogManager_rebuildPrompt_details()).buildAll();
        dialogPane.setExpandableContent(wrappedListView);

        return currentDialog.showAndWait().map(UPDATE::equals).orElse(false);
    }
}
