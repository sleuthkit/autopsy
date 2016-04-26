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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import javafx.embed.swing.SwingFXUtils;
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
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.HyperlinkLabel;
import org.controlsfx.control.action.Action;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.ReportBranding;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Save a snapshot of the given node as an autopsy report.
 */
public class SaveSnapshotAsReport extends Action {

    private static final Logger LOGGER = Logger.getLogger(SaveSnapshotAsReport.class.getName());
    private static final Image SNAP_SHOT = new Image("org/sleuthkit/autopsy/timeline/images/image.png", 16, 16, true, true); //
    private static final String HTML_EXT = ".html"; //
    private static final String REPORT_IMAGE_EXTENSION = ".png"; //
    private static final ButtonType OPEN = new ButtonType(Bundle.OpenReportAction_DisplayName(), ButtonBar.ButtonData.NO);
    private static final ButtonType OK = new ButtonType(ButtonType.OK.getText(), ButtonBar.ButtonData.CANCEL_CLOSE);

    private final static MustacheFactory mf = new DefaultMustacheFactory();

    private final TimeLineController controller;
    private final Case currentCase;
    private final ReportBranding reportBranding;

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

        this.controller = controller;
        this.currentCase = controller.getAutopsyCase();
        this.reportBranding = new ReportBranding();

        setEventHandler(actionEvent -> {
            String escapedLocalDateTime = FileUtil.escapeFileName(LocalDateTime.now().toString());
            String reportName = Bundle.SaveSnapsHotAsReport_ReportName(escapedLocalDateTime);
            Path reportPath = Paths.get(Case.getCurrentCase().getReportDirectory(), reportName).toAbsolutePath();
            File reportHTMLFIle = reportPath.resolve(reportName + HTML_EXT).toFile();

            ZoomParams zoomParams = controller.getEventsModel().zoomParametersProperty().get();

            try {
                Files.createDirectories(reportPath);

                writeSummary(reportPath);
                //ensure directory exists and write html file

                try (Writer htmlWriter = new FileWriter(reportHTMLFIle)) {
                    writeSnapShotHTMLFile(reportPath, reportName, zoomParams);
                }

                //pull generator and agency logo from branding, and the remaining resources from the core jar
                String generatorLogoPath = reportBranding.getGeneratorLogoPath();
                if (StringUtils.isNotBlank(generatorLogoPath)) {
                    Files.copy(Paths.get(generatorLogoPath), Files.newOutputStream(reportPath.resolve("generator_logo.png")));
                }

                String agencyLogoPath = reportBranding.getAgencyLogoPath();
                if (StringUtils.isNotBlank(agencyLogoPath)) {
                    Files.copy(Paths.get(agencyLogoPath), Files.newOutputStream(reportPath.resolve("agency_logo.png")));
                }

                //copy favicon
                try (InputStream faviconStream = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/favicon.ico")) {
                    Files.copy(faviconStream, reportPath.resolve("favicon.ico"));
                }

                //take snapshot and save in report directory
                ImageIO.write(SwingFXUtils.fromFXImage(node.snapshot(null, null), null), "png", //
                        reportPath.resolve(reportName + REPORT_IMAGE_EXTENSION).toFile()); //

                //copy report css
                try (InputStream resource = SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/index.css")) { //
                    Files.copy(resource, reportPath.resolve("index.css")); //
                }
                //copy report css
                try (InputStream resource = SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/actions/summary.css")) { //
                    Files.copy(resource, reportPath.resolve("summary.css")); //
                }

                //add html file as report to case
                try {
                    Case.getCurrentCase().addReport(reportHTMLFIle.getPath(), Bundle.Timeline_ModuleName(), reportName + HTML_EXT); //
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.WARNING, "failed to add html wrapper as a report", ex); //
                    new Alert(Alert.AlertType.ERROR, Bundle.SaveSnapShotAsReport_FailedToAddReport()).showAndWait();
                }

                //create alert to notify user of report location
                final Alert alert = new Alert(Alert.AlertType.INFORMATION, null, OPEN, OK);
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

                alert.showAndWait()
                        .filter(OPEN::equals)
                        .ifPresent(buttonType -> openReportAction.handle(null));

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error writing report " + reportPath + " to disk", e); //
                new Alert(Alert.AlertType.ERROR, Bundle.SaveSnapShotAsReport_ErrorWritingReport(reportPath)).showAndWait();
            }
        });
    }

    private static void writeSnapShotHTMLFile(Path reportPath, String reportTitle, ZoomParams zoomParams) throws IOException {

        HashMap<String, Object> scopes = new HashMap<>();

        scopes.put("reportTitle", reportTitle);
        scopes.put("snapshotFile", reportTitle + REPORT_IMAGE_EXTENSION);

        Interval timeRange = zoomParams.getTimeRange();

        scopes.put("startTime", timeRange.getStart().toString(DateTimeFormat.fullDateTime()));
        scopes.put("endTime", timeRange.getEnd().toString(DateTimeFormat.fullDateTime()));

        scopes.put("zoomParams", zoomParams);

        try (Writer writer = Files.newBufferedWriter(reportPath.resolve("snapshot.html"), Charset.forName("UTF-8"))) {
            Mustache summaryMustache = mf.compile(new InputStreamReader(SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/actions/snapshot_template.html")), "Snapshot");
            summaryMustache.execute(writer, scopes);
            writer.flush();
        }
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
        htmlWriter.write("<tr><td>" + key + ": </td><td>" + value + "</td></tr>\n"); //
    }

    /**
     * Write the summary of the current case for this report.
     */
    @NbBundle.Messages({
        "ReportHTML.writeSum.caseName=Case:",
        "ReportHTML.writeSum.caseNum=Case Number:",
        "ReportHTML.writeSum.noCaseNum=<i>No case number</i>",
        "ReportHTML.writeSum.noExaminer=<i>No examiner</i>",
        "ReportHTML.writeSum.examiner=Examiner:",
        "ReportHTML.writeSum.timezone=Timezone:",
        "ReportHTML.writeSum.path=Path:"})
    private void writeSummary(Path reportPath) throws IOException {
        HashMap<String, Object> scopes = new HashMap<>();

        scopes.put("reportBranding", reportBranding);

        scopes.put("generationDateTime", new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
        scopes.put("ingestRunning", IngestManager.getInstance().isIngestRunning());

        scopes.put("currentCase", currentCase);

        try (Writer writer = Files.newBufferedWriter(reportPath.resolve("summary.html"), Charset.forName("UTF-8"))) {

            Mustache summaryMustache = mf.compile(new InputStreamReader(SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/actions/summary_template.html")), "Summary");
            summaryMustache.execute(writer, scopes);
            writer.flush();
        }
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
