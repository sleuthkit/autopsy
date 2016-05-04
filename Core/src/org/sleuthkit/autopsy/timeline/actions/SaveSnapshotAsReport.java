/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.actions;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javax.swing.JOptionPane;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.HyperlinkLabel;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.snapshot.SnapShotReportWriter;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Action that saves a snapshot of the given node as an autopsy report.
 * Delegates to SnapsHotReportWrite to actually generate and write the report.
 */
public class SaveSnapshotAsReport extends Action {

    private static final Logger LOGGER = Logger.getLogger(SaveSnapshotAsReport.class.getName());
    private static final Image SNAP_SHOT = new Image("org/sleuthkit/autopsy/timeline/images/image.png", 16, 16, true, true); //NON_NLS
    private static final ButtonType OPEN = new ButtonType(Bundle.OpenReportAction_DisplayName(), ButtonBar.ButtonData.NO);
    private static final ButtonType OK = new ButtonType(ButtonType.OK.getText(), ButtonBar.ButtonData.CANCEL_CLOSE);

    private final TimeLineController controller;
    private final Case currentCase;

    /**
     * Constructor
     *
     * @param controller   The controller for this timeline action
     * @param nodeSupplier The Supplier of the node to snapshot.
     */
    @NbBundle.Messages({
        "Timeline.ModuleName=Timeline",
        "SaveSnapShotAsReport.action.dialogs.title=Timeline",
        "SaveSnapShotAsReport.action.name.text=Snapshot Report",
        "SaveSnapShotAsReport.action.longText=Save a screen capture of the visualization as a report.",
        "# {0} - report file path",
        "SaveSnapShotAsReport.ReportSavedAt=Report saved at [{0}]",
        "SaveSnapShotAsReport.Success=Success",
        "SaveSnapShotAsReport.FailedToAddReport=Failed to add snaphot to case as a report.",
        "# {0} - report path",
        "SaveSnapShotAsReport.ErrorWritingReport=Error writing report to disk at {0}.",
        "# {0} - generated default report name",
        "SaveSnapShotAsReport.reportName.prompt=leave empty for default report name: {0}.",
        "SaveSnapShotAsReport.reportName.header=Enter a report name for the Timeline Snapshot Report."
    })
    public SaveSnapshotAsReport(TimeLineController controller, Node node) {
        super(Bundle.SaveSnapShotAsReport_action_name_text());
        setLongText(Bundle.SaveSnapShotAsReport_action_longText());
        setGraphic(new ImageView(SNAP_SHOT));

        this.controller = controller;
        this.currentCase = controller.getAutopsyCase();

        setEventHandler(actionEvent -> {
            //capture generation date and use to make default report name
            Date generationDate = new Date();
            final String defaultReportName = FileUtil.escapeFileName(currentCase.getName() + " " + new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss").format(generationDate)); //NON_NLS

            BufferedImage snapshot = SwingFXUtils.fromFXImage(node.snapshot(null, null), null);

            //prompt user to pick report name
            TextInputDialog textInputDialog = new TextInputDialog();
            textInputDialog.setTitle(Bundle.SaveSnapShotAsReport_action_dialogs_title());
            textInputDialog.getEditor().setPromptText(Bundle.SaveSnapShotAsReport_reportName_prompt(defaultReportName));
            //keep prompt even if text field has focus, until user starts typing.
            textInputDialog.getEditor().setStyle("-fx-prompt-text-fill: derive(-fx-control-inner-background, -30%);");//NON_NLS 
            textInputDialog.setHeaderText(Bundle.SaveSnapShotAsReport_reportName_header());

            textInputDialog.showAndWait().ifPresent(enteredReportName -> {
                //reportName defaults to case name + timestamp if left blank
                String reportName = StringUtils.defaultIfBlank(enteredReportName, defaultReportName);
                Path reportFolderPath = Paths.get(currentCase.getReportDirectory(), reportName, "Timeline Snapshot"); //NON_NLS
                Path reportMainFilePath;

                try {
                    //generate and write report
                    reportMainFilePath = new SnapShotReportWriter(currentCase,
                            reportFolderPath,
                            reportName,
                            controller.getEventsModel().getZoomParamaters(),
                            generationDate, snapshot).writeReport();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Error writing report to disk at " + reportFolderPath, ex); //NON_NLS
                    new Alert(Alert.AlertType.ERROR, Bundle.SaveSnapShotAsReport_ErrorWritingReport(reportFolderPath)).show();
                    return;
                }

                try {
                    //add main file as report to case
                    Case.getCurrentCase().addReport(reportMainFilePath.toString(), Bundle.Timeline_ModuleName(), reportName);
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.WARNING, "Failed to add " + reportMainFilePath.toString() + " to case as a report", ex); //NON_NLS
                    new Alert(Alert.AlertType.ERROR, Bundle.SaveSnapShotAsReport_FailedToAddReport()).show();
                    return;
                }

                //notify user of report location
                final Alert alert = new Alert(Alert.AlertType.INFORMATION, null, OPEN, OK);
                alert.setTitle(Bundle.SaveSnapShotAsReport_action_dialogs_title());
                alert.setHeaderText(Bundle.SaveSnapShotAsReport_Success());

                //make action to open report, and hyperlinklable to invoke action
                final OpenReportAction openReportAction = new OpenReportAction(reportMainFilePath);
                HyperlinkLabel hyperlinkLabel = new HyperlinkLabel(Bundle.SaveSnapShotAsReport_ReportSavedAt(reportMainFilePath.toString()));
                hyperlinkLabel.setOnAction(openReportAction);
                alert.getDialogPane().setContent(hyperlinkLabel);

                alert.showAndWait().filter(OPEN::equals).ifPresent(buttonType -> openReportAction.handle(null));
            });
        });
    }

    /**
     * Action that opens the given Path in the system default application.
     */
    @NbBundle.Messages({
        "OpenReportAction.DisplayName=Open Report",
        "OpenReportAction.NoAssociatedEditorMessage=There is no associated editor for reports of this type or the associated application failed to launch.",
        "OpenReportAction.MessageBoxTitle=Open Report Failure",
        "OpenReportAction.NoOpenInEditorSupportMessage=This platform (operating system) does not support opening a file in an editor this way.",
        "OpenReportAction.MissingReportFileMessage=The report file no longer exists.",
        "OpenReportAction.ReportFileOpenPermissionDeniedMessage=Permission to open the report file was denied."})
    private class OpenReportAction extends Action {

        OpenReportAction(Path reportHTMLFIle) {
            super(Bundle.OpenReportAction_DisplayName());
            setEventHandler(actionEvent -> {
                try {
                    Desktop.getDesktop().open(reportHTMLFIle.toFile());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(null,
                            Bundle.OpenReportAction_NoAssociatedEditorMessage(),
                            Bundle.OpenReportAction_MessageBoxTitle(),
                            JOptionPane.ERROR_MESSAGE);
                } catch (UnsupportedOperationException ex) {
                    JOptionPane.showMessageDialog(null,
                            Bundle.OpenReportAction_NoOpenInEditorSupportMessage(),
                            Bundle.OpenReportAction_MessageBoxTitle(),
                            JOptionPane.ERROR_MESSAGE);
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(null,
                            Bundle.OpenReportAction_MissingReportFileMessage(),
                            Bundle.OpenReportAction_MessageBoxTitle(),
                            JOptionPane.ERROR_MESSAGE);
                } catch (SecurityException ex) {
                    JOptionPane.showMessageDialog(null,
                            Bundle.OpenReportAction_ReportFileOpenPermissionDeniedMessage(),
                            Bundle.OpenReportAction_MessageBoxTitle(),
                            JOptionPane.ERROR_MESSAGE);
                }
            });
        }
    }
}
