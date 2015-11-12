/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.logging.Level;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import org.controlsfx.control.HyperlinkLabel;
import org.controlsfx.control.action.Action;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Save a snapshot of the given node as an autopsy report.
 */
public class SaveSnapshotAsReport extends Action {

    private static final Logger LOGGER = Logger.getLogger(SaveSnapshotAsReport.class.getName());
    private static final Image SNAP_SHOT = new Image("org/sleuthkit/autopsy/timeline/images/image.png", 16, 16, true, true); // NON-NLS
    private static final String HTML_EXT = ".html"; // NON-NLS
    private static final String REPORT_IMAGE_EXTENSION = ".png"; // NON-NLS
    private static final ButtonType open = new ButtonType(Bundle.OpenReportAction_DisplayName(), ButtonBar.ButtonData.NO);
    private static final ButtonType ok = new ButtonType(ButtonType.OK.getText(), ButtonBar.ButtonData.CANCEL_CLOSE);

    @NbBundle.Messages({"SaveSnapshot.action.name.text=Snapshot Report",
        "SaveSnapshot.action.longText=Save a screen capture of the visualization as a report.",
        "SaveSnapshot.fileChoose.title.text=Save snapshot to",
        "# {0} - report file path",
        "SaveSnapShotAsReport.ReportSavedAt=Report saved at [{0}]",
        "Timeline.ModuleName=Timeline", "SaveSnapShotAsReport.Success=Success",
        "# {0} - uniqueness identifier, local date time at report creation time",
        "SaveSnapsHotAsReport.ReportName=timeline-report-{0}",
        "SaveSnapShotAsReport.FailedToAddReport=Failed to add snaphot as a report. See log for details",
        "# {0} - report name",
        "SaveSnapShotAsReport.ErrorWritingReport=Error writing report {0} to disk. See log for details",})
    public SaveSnapshotAsReport(TimeLineController controller, Node node) {
        super(Bundle.SaveSnapshot_action_name_text());
        setLongText(Bundle.SaveSnapshot_action_longText());
        setGraphic(new ImageView(SNAP_SHOT));
        setEventHandler(new Consumer<ActionEvent>() {

            @Override
            public void accept(ActionEvent actioneEvent) {
                String escapedLocalDateTime = FileUtil.escapeFileName(LocalDateTime.now().toString());
                String reportName = Bundle.SaveSnapsHotAsReport_ReportName(escapedLocalDateTime);
                Path reportPath = Paths.get(Case.getCurrentCase().getReportDirectory(), reportName).toAbsolutePath();
                File reportHTMLFIle = reportPath.resolve(reportName + HTML_EXT).toFile();

                ZoomParams zoomParams = controller.getEventsModel().zoomParametersProperty().get();

                try {
                    //ensure directory exists and write html file
                    Files.createDirectories(reportPath);
                    try (Writer htmlWriter = new FileWriter(reportHTMLFIle)) {
                        writeHTMLFile(reportName, htmlWriter, zoomParams);
                    }

                    //take snapshot and save in report directory
                    ImageIO.write(SwingFXUtils.fromFXImage(node.snapshot(null, null), null), "png", // NON-NLS
                            reportPath.resolve(reportName + REPORT_IMAGE_EXTENSION).toFile()); // NON-NLS

                    //copy report css
                    try (InputStream resource = this.getClass().getResourceAsStream("/org/sleuthkit/autopsy/timeline/index.css")) { // NON-NLS
                        Files.copy(resource, reportPath.resolve("index.css")); // NON-NLS
                    }

                    //add html file as report to case
                    try {
                        Case.getCurrentCase().addReport(reportHTMLFIle.getPath(), Bundle.Timeline_ModuleName(), reportName + HTML_EXT); // NON-NLS
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.WARNING, "failed to add html wrapper as a report", ex); // NON-NLS
                        new Alert(Alert.AlertType.ERROR, Bundle.SaveSnapShotAsReport_FailedToAddReport()).showAndWait();
                    }

                    //create alert to notify user of report location
                    final Alert alert = new Alert(Alert.AlertType.INFORMATION, null, open, ok);
                    alert.setTitle(Bundle.SaveSnapshot_action_name_text());
                    alert.setHeaderText(Bundle.SaveSnapShotAsReport_Success());
                    alert.initStyle(StageStyle.UTILITY);
                    alert.initOwner(node.getScene().getWindow());
                    alert.initModality(Modality.APPLICATION_MODAL);

                    //make action to open report, and hyperlinklable to invoke action
                    final OpenReportAction openReportAction = new OpenReportAction(reportHTMLFIle);
                    HyperlinkLabel hyperlinkLabel = new HyperlinkLabel(Bundle.SaveSnapShotAsReport_ReportSavedAt(reportHTMLFIle.getPath()));
                    hyperlinkLabel.setOnAction(openReportAction);
                    alert.getDialogPane().setContent(hyperlinkLabel);

                    alert.showAndWait().ifPresent(buttonType -> {
                        if (buttonType == open) {
                            openReportAction.handle(null);
                        }
                    });

                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error writing report " + reportPath + " to disk", e); // NON-NLS
                    new Alert(Alert.AlertType.ERROR, Bundle.SaveSnapShotAsReport_ErrorWritingReport(reportPath)).showAndWait();
                }
            }
        });
    }

    private static void writeHTMLFile(String reportName, final Writer htmlWriter, ZoomParams zoomParams) throws IOException {

        //write html wrapper file
        htmlWriter.write("<html>\n<head>\n\t<title>timeline snapshot</title>\n\t<link rel=\"stylesheet\" type=\"text/css\" href=\"index.css\" />\n</head>\n<body>\n"); // NON-NLS
        htmlWriter.write("<div id=\"content\">\n<h1>" + reportName + "</h1>\n"); // NON-NLS
        //embed snapshot
        htmlWriter.write("<img src = \"" + reportName + REPORT_IMAGE_EXTENSION + "\" alt = \"snaphot\">"); // NON-NLS
        //write view paramaters
        htmlWriter.write("<table>\n"); // NON-NLS
        writeTableRow(htmlWriter, "Case", Case.getCurrentCase().getName()); // NON-NLS
        writeTableRow(htmlWriter, "Time Range", zoomParams.getTimeRange().toString()); // NON-NLS
        writeTableRow(htmlWriter, "Description Level of Detail", zoomParams.getDescriptionLOD().getDisplayName()); // NON-NLS
        writeTableRow(htmlWriter, "Event Type Zoom Level", zoomParams.getTypeZoomLevel().getDisplayName()); // NON-NLS
        writeTableRow(htmlWriter, "Filters", zoomParams.getFilter().getHTMLReportString()); // NON-NLS
        //end table and html
        htmlWriter.write("</table>\n"); // NON-NLS
        htmlWriter.write("</div>\n</body>\n</html>"); // NON-NLS
    }

    /**
     *
     * @param htmlWriter the value of htmlWriter
     * @param key        the value of Key
     * @param value      the value of value
     *
     * @throws IOException
     */
    private static void writeTableRow(final Writer htmlWriter, final String key, final String value) throws IOException {
        htmlWriter.write("<tr><td>" + key + ": </td><td>" + value + "</td></tr>\n"); // NON-NLS
    }

    @NbBundle.Messages({"OpenReportAction.DisplayName=Open Report",
        "OpenReportAction.NoAssociatedEditorMessage=There is no associated editor for reports of this type or the associated application failed to launch.",
        "OpenReportAction.MessageBoxTitle=Open Report Failure",
        "OpenReportAction.NoOpenInEditorSupportMessage=This platform (operating system) does not support opening a file in an editor this way.",
        "OpenReportAction.MissingReportFileMessage=The report file no longer exists.",
        "OpenReportAction.ReportFileOpenPermissionDeniedMessage=Permission to open the report file was denied."})
    private class OpenReportAction extends Action {

        OpenReportAction(File reportHTMLFIle) {
            super(Bundle.OpenReportAction_DisplayName());
            setEventHandler(actionEvent -> {
                try {
                    Desktop.getDesktop().open(reportHTMLFIle);
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
