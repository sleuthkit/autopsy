/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-18 Basis Technology Corp.
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
import java.util.List;
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
 * Manager for the various prompts and dialogs Timeline shows the user related
 * to rebuilding the database. Methods must only be called on the JFX thread.
 */
public final class PromptDialogManager {

    private final static Logger logger = Logger.getLogger(PromptDialogManager.class.getName());

    @NbBundle.Messages("PrompDialogManager.buttonType.showTimeline=Continue")
    private static final ButtonType CONTINUE = new ButtonType(Bundle.PrompDialogManager_buttonType_showTimeline(), ButtonBar.ButtonData.OK_DONE);

    @NbBundle.Messages("PrompDialogManager.buttonType.continueNoUpdate=Continue Without Updating")
    private static final ButtonType CONTINUE_NO_UPDATE = new ButtonType(Bundle.PrompDialogManager_buttonType_continueNoUpdate(), ButtonBar.ButtonData.CANCEL_CLOSE);

    @NbBundle.Messages("PrompDialogManager.buttonType.update=Update DB")
    private static final ButtonType UPDATE = new ButtonType(Bundle.PrompDialogManager_buttonType_update(), ButtonBar.ButtonData.OK_DONE);

    /** Image to use as title bar icon in dialogs */
    private static final Image AUTOPSY_ICON;

    static {
        Image tempImg = null;
        try {
            tempImg = new Image(new URL("nbresloc:/org/netbeans/core/startup/frame.gif").openStream()); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Failed to load branded icon for progress dialog.", ex); //NON-NLS
        }
        AUTOPSY_ICON = tempImg;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private Dialog<?> currentDialog;

    private final TimeLineController controller;

    /**
     * Constructor
     *
     * @param controller The TimeLineController this manager belongs to.
     */
    PromptDialogManager(TimeLineController controller) {
        this.controller = controller;
    }

    /**
     * Bring the currently managed dialog (if there is one) to the front.
     *
     * @return True if a dialog was brought to the front, or false of there is
     *         no currently managed open dialog
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean bringCurrentDialogToFront() {
        if (currentDialog != null && currentDialog.isShowing()) {
            ((Stage) currentDialog.getDialogPane().getScene().getWindow()).toFront();
            return true;
        }
        return false;
    }

    /**
     * Show a progress dialog for the given db population task
     *
     * @param task The task to show progress for.
     */
    @NbBundle.Messages({"PromptDialogManager.progressDialog.title=Populating Timeline Data"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void showDBPopulationProgressDialog(CancellationProgressTask<?> task) {
        currentDialog = new ProgressDialog(task);
        currentDialog.initModality(Modality.NONE);
        currentDialog.setTitle(Bundle.PromptDialogManager_progressDialog_title());
        setDialogIcons(currentDialog);
        currentDialog.headerTextProperty().bind(task.titleProperty());

        DialogPane dialogPane = currentDialog.getDialogPane();
        dialogPane.setPrefSize(400, 200); //override autosizing which fails for some reason

        //co-ordinate task cancelation and dialog hiding.
        task.setOnCancelled(cancelled -> currentDialog.close());
        task.setOnSucceeded(succeeded -> currentDialog.close());
        task.setOnFailed(failed -> currentDialog.close());

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

    /**
     * Set the title bar icon for the given Dialog to be the Autopsy logo icon.
     *
     * @param dialog The dialog to set the title bar icon for.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public static void setDialogIcons(Dialog<?> dialog) {
        ((Stage) dialog.getDialogPane().getScene().getWindow()).getIcons().setAll(AUTOPSY_ICON);
    }

    /**
     * Prompt the user that ingest is running and the DB may not end up
     * complete.
     *
     * @return True if they want to continue anyways.
     */
    @NbBundle.Messages({
        "PromptDialogManager.confirmDuringIngest.headerText=Ingest is still going, and the Timeline may be incomplete.",
        "PromptDialogManager.confirmDuringIngest.contentText=Do you want to continue?"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean confirmDuringIngest() {
        currentDialog = new Alert(Alert.AlertType.CONFIRMATION, Bundle.PromptDialogManager_confirmDuringIngest_contentText(), CONTINUE, ButtonType.CANCEL);
        currentDialog.initModality(Modality.APPLICATION_MODAL);
        currentDialog.setTitle(Bundle.Timeline_dialogs_title());
        setDialogIcons(currentDialog);
        currentDialog.setHeaderText(Bundle.PromptDialogManager_confirmDuringIngest_headerText());

        //show dialog and map all results except "continue" to false.
        return currentDialog.showAndWait().map(CONTINUE::equals).orElse(false);
    }

    /**
     * Prompt the user to confirm rebuilding the database for the given list of
     * reasons.
     *
     * @param rebuildReasons A List of reasons why the database is out of date.
     *
     * @return True if the user a confirms rebuilding the database.
     */
    @NbBundle.Messages({
        "PromptDialogManager.rebuildPrompt.headerText=The Timeline DB is incomplete and/or out of date."
        + "  Some events may be missing or inaccurate and some features may be unavailable.",
        "PromptDialogManager.rebuildPrompt.details=Details"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean confirmRebuild(List<String> rebuildReasons) {
        currentDialog = new Alert(Alert.AlertType.CONFIRMATION, Bundle.TimeLinecontroller_updateNowQuestion(), UPDATE, CONTINUE_NO_UPDATE);
        currentDialog.initModality(Modality.APPLICATION_MODAL);
        currentDialog.setTitle(Bundle.Timeline_dialogs_title());
        setDialogIcons(currentDialog);

        currentDialog.setHeaderText(Bundle.PromptDialogManager_rebuildPrompt_headerText());

        //set up listview of reasons to rebuild
        ListView<String> listView = new ListView<>(FXCollections.observableArrayList(rebuildReasons));
        listView.setCellFactory(lstView -> new WrappingListCell());
        listView.setMaxHeight(75);

        //wrap listview in title border.
        Node wrappedListView = Borders.wrap(listView)
                .lineBorder()
                .title(Bundle.PromptDialogManager_rebuildPrompt_details())
                .buildAll();

        DialogPane dialogPane = currentDialog.getDialogPane();
        dialogPane.setExpandableContent(wrappedListView);
        dialogPane.setMaxWidth(500);

        //show dialog and map all results except "update" to false.
        return currentDialog.showAndWait().map(UPDATE::equals).orElse(false);
    }

    @NbBundle.Messages({
        "PromptDialogManager.showTooManyFiles.contentText="
        + "There are too many files in the DB to ensure reasonable performance."
        + "  Timeline will be disabled. ",
        "PromptDialogManager.showTooManyFiles.headerText="})
    static void showTooManyFiles() {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION,
                Bundle.PromptDialogManager_showTooManyFiles_contentText(), ButtonType.OK);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(Bundle.Timeline_dialogs_title());
        setDialogIcons(dialog);
        dialog.setHeaderText(Bundle.PromptDialogManager_showTooManyFiles_headerText());
        dialog.showAndWait();
    }

    @NbBundle.Messages({
        "PromptDialogManager.showTimeLineDisabledMessage.contentText="
        + "Timeline functionality is not available yet."
        + "  Timeline will be disabled. ",
        "PromptDialogManager.showTimeLineDisabledMessage.headerText="})
    static void showTimeLineDisabledMessage() {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION,
                Bundle.PromptDialogManager_showTimeLineDisabledMessage_contentText(), ButtonType.OK);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(Bundle.Timeline_dialogs_title());
        setDialogIcons(dialog);
        dialog.setHeaderText(Bundle.PromptDialogManager_showTimeLineDisabledMessage_headerText());
        dialog.showAndWait();
    }
}
