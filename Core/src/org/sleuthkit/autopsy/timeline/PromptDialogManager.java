/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * Manager for the various prompts Timeline shows the user related to rebuilding
 * the database.
 */
public class PromptDialogManager {

    private static final Logger LOGGER = Logger.getLogger(PromptDialogManager.class.getName());

    @NbBundle.Messages("PrompDialogManager.buttonType.showTimeline=Show Timeline")
    private static final ButtonType SHOW_TIMELINE = new ButtonType(Bundle.PrompDialogManager_buttonType_showTimeline(), ButtonBar.ButtonData.OK_DONE);

    @NbBundle.Messages("PrompDialogManager.buttonType.continueNoUpdate=Continue Without Updating")
    private static final ButtonType CONTINUE_NO_UPDATE = new ButtonType(Bundle.PrompDialogManager_buttonType_continueNoUpdate(), ButtonBar.ButtonData.CANCEL_CLOSE);

    @NbBundle.Messages("PrompDialogManager.buttonType.update=Update")
    private static final ButtonType UPDATE = new ButtonType(Bundle.PrompDialogManager_buttonType_update(), ButtonBar.ButtonData.OK_DONE);

    private static final Image LOGO;

    static {
        Image x = null;
        try {
            x = new Image(new URL("nbresloc:/org/netbeans/core/startup/frame.gif").openStream()); //NOI18N
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to load branded icon for progress dialog.", ex); //NOI18N NON-NLS
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

    @NbBundle.Messages({"PromptDialogManager.progressDialog.title=Populating Timeline Data"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public void showProgressDialog(CancellationProgressTask<?> task) {
        currentDialog = new ProgressDialog(task);
        currentDialog.headerTextProperty().bind(task.titleProperty());
        setDialogIcons(currentDialog);
        currentDialog.setTitle(Bundle.PromptDialogManager_progressDialog_title());

        DialogPane dialogPane = currentDialog.getDialogPane();
        dialogPane.setPrefSize(400, 200); //override autosizing which fails for some reason

        //co-ordinate task cancelation and dialog hiding.
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

        currentDialog.show();
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    static private void setDialogIcons(Dialog<?> dialog) {
        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.getIcons().setAll(LOGO);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    static private void setDialogTitle(Dialog<?> dialog) {
        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.setTitle(Bundle.Timeline_confirmation_dialogs_title());
    }

    /**
     * prompt the user that ingest is running and the db may not end up
     * complete.
     *
     * @return true if they want to continue anyways
     */
    @NbBundle.Messages({"PromptDialogManager.confirmDuringIngest.headerText=You are trying to show a timeline before ingest has been completed.\nThe timeline may be incomplete.",
        "PromptDialogManager.confirmDuringIngest.contentText=Do you want to continue?"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean confirmDuringIngest() {
        currentDialog = new Alert(Alert.AlertType.CONFIRMATION, Bundle.PromptDialogManager_confirmDuringIngest_contentText(), SHOW_TIMELINE, ButtonType.CANCEL);
        currentDialog.initModality(Modality.APPLICATION_MODAL);
        currentDialog.setHeaderText(Bundle.PromptDialogManager_confirmDuringIngest_headerText());
        setDialogIcons(currentDialog);
        setDialogTitle(currentDialog);

        return currentDialog.showAndWait().map(SHOW_TIMELINE::equals).orElse(false);
    }

    @NbBundle.Messages({"PromptDialogManager.rebuildPrompt.headerText=The Timeline database is incomplete and/or out of date.\nSome events may be missing or inaccurate and some features may be unavailable.",
        "PromptDialogManager.rebuildPrompt.details=Details:"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean confirmRebuild(ArrayList<String> rebuildReasons) {
        currentDialog = new Alert(Alert.AlertType.CONFIRMATION, Bundle.TimeLinecontroller_updateNowQuestion(), UPDATE, CONTINUE_NO_UPDATE);
        currentDialog.initModality(Modality.APPLICATION_MODAL);
        currentDialog.setHeaderText(Bundle.PromptDialogManager_rebuildPrompt_headerText());
        setDialogIcons(currentDialog);
        setDialogTitle(currentDialog);

        DialogPane dialogPane = currentDialog.getDialogPane();
        ListView<String> listView = new ListView<>(FXCollections.observableArrayList(rebuildReasons));
        listView.setCellFactory(lstView -> new WrappingListCell());
        listView.setMaxHeight(75);
        Node wrappedListView = Borders.wrap(listView).lineBorder().title(Bundle.PromptDialogManager_rebuildPrompt_details()).buildAll();
        dialogPane.setExpandableContent(wrappedListView);

        return currentDialog.showAndWait().map(UPDATE::equals).orElse(false);
    }
}
