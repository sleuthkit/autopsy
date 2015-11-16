/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.dialog.ProgressDialog;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;

/**
 *
 */
public class PromptDialogManager {

    private static final Logger LOGGER = Logger.getLogger(PromptDialogManager.class.getName());
    private static final Image LOGO;

    static {
        Image x = null;
        try {
            x = new Image(new URL("nbresloc:/org/netbeans/core/startup/frame.gif").openStream());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to laod branded icon for progress dialog.", ex);
        }
        LOGO = x;
    }
    private Dialog<?> currentDialog;

    private final TimeLineController controller;

    PromptDialogManager(TimeLineController controller) {
        this.controller = controller;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({"Timeline.progressWindow.title=Populating Timeline Data"})
    void showProgressDialog(Worker<Void> task) {
        currentDialog = new ProgressDialog(task);
        currentDialog.setTitle(Bundle.Timeline_progressWindow_title());
        DialogPane dialogPane = currentDialog.getDialogPane();
        dialogPane.getButtonTypes().add(ButtonType.CANCEL);
        Stage dialogStage = (Stage) dialogPane.getScene().getWindow();
        dialogPane.setPrefWidth(400);
        currentDialog.headerTextProperty().bind(task.titleProperty());
        dialogStage.getIcons().setAll(LOGO);
        currentDialog.setOnCloseRequest(closeRequestEvent -> task.cancel());
        currentDialog.setOnHidden(new EventHandler<DialogEvent>() {

            @Override
            public void handle(DialogEvent event) {
                if (currentDialog == progressDialog) {
                    currentDialog = null;
                }
            }
        });
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean showConfirmationDialog(String title, String headerText, String contentText, ButtonType okButton, ButtonType cancelButton) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, contentText, okButton, cancelButton);
        alert.initStyle(StageStyle.UTILITY);
        alert.initModality(Modality.APPLICATION_MODAL);
//        alert.initOwner(mainFrame);

        alert.setHeaderText(headerText);
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.setTitle(title);
//        alert = alert;
        return alert.showAndWait().map(okButton::equals).orElse(false);
    }

    /**
     * prompt the user to rebuild the db because that datasource_ids are missing
     * from the database and that the datasource filter will not work
     *
     * @return true if they agree to rebuild
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({"datasource.missing.header=The Timeline events database was previously populated without datasource information."
        + "\nThe data source filter will be unavailable unless you update the events database."
    })
    synchronized boolean confirmDataSourceIDsMissingRebuild() {
        return showConfirmationDialog(Bundle.Timeline_confirmation_dialogs_title(),
                Bundle.datasource_missing_header(),
                Bundle.TimeLinecontroller_updateNowQuestion(),
                new ButtonType("Update", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Continue without updating", ButtonBar.ButtonData.CANCEL_CLOSE));
    }

    /**
     * prompt the user to rebuild the db because the db was last build during
     * ingest and may be incomplete
     *
     * @return true if they agree to rebuild
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({"Timeline.do_repopulate.msg=The Timeline events database was previously populated while ingest was running."
        + "\nSome events may not have been populated or may have been populated inaccurately."
    })
    synchronized boolean confirmLastBuiltDuringIngestRebuild() {
        return showConfirmationDialog(Bundle.Timeline_confirmation_dialogs_title(),
                Bundle.Timeline_do_repopulate_msg(),
                Bundle.TimeLinecontroller_updateNowQuestion(),
                new ButtonType("Update", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Continue without updating", ButtonBar.ButtonData.CANCEL_CLOSE));
//        return JOptionPane.showConfirmDialog(mainFrame,
//                Bundle.Timeline_do_repopulate_msg(),
//                Bundle.Timeline_confirmation_dialogs_title(),
//                JOptionPane.YES_NO_OPTION,
//                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * prompt the user to rebuild the db because the db is out of date and
     * doesn't include things from subsequent ingests
     *
     * @return true if they agree to rebuild
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({"Timeline.propChg.confDlg.timelineOOD.msg=The event data is out of date.",})
    synchronized boolean confirmOutOfDateRebuild() {
        return showConfirmationDialog(Bundle.Timeline_confirmation_dialogs_title(),
                Bundle.Timeline_propChg_confDlg_timelineOOD_msg(),
                Bundle.TimeLinecontroller_updateNowQuestion(),
                new ButtonType("Update", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Continue without updating", ButtonBar.ButtonData.CANCEL_CLOSE));
//        return JOptionPane.showConfirmDialog(mainFrame,
//                Bundle.Timeline_propChg_confDlg_timelineOOD_msg(),
//                Bundle.Timeline_confirmation_dialogs_title(),
//                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    /**
     * prompt the user that ingest is running and the db may not end up
     * complete.
     *
     * @return true if they want to continue anyways
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({"Timeline.initTimeline.confDlg.genBeforeIngest.msg=You are trying to generate a timeline before ingest has been completed. "
        + "The timeline may be incomplete.",
        "Timeline.initTimeline.confDlg.genBeforeIngest.question=Do you want to continue?"})
    synchronized boolean confirmRebuildDuringIngest() {
        return showConfirmationDialog(Bundle.Timeline_confirmation_dialogs_title(),
                Bundle.Timeline_initTimeline_confDlg_genBeforeIngest_msg(),
                Bundle.Timeline_initTimeline_confDlg_genBeforeIngest_question(),
                new ButtonType("Show Timeline", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE));
//        return JOptionPane.showConfirmDialog(mainFrame,
//                Bundle.Timeline_initTimeline_confDlg_genBeforeIngest_msg(),
//                Bundle.Timeline_confirmation_dialogs_title(),
//                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    boolean bringCurrentDialogToFront() {
        if (currentDialog != null && currentDialog.isShowing()) {
            ((Stage) currentDialog.getDialogPane().getScene().getWindow()).toFront();
            return true;
        }
        return false;
    }
}
