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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Optional;
import java.util.logging.Level;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
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
    private static final ButtonType OPEN = new ButtonType(Bundle.OpenReportAction_DisplayName(), ButtonBar.ButtonData.NO);
    private static final ButtonType OK = new ButtonType(ButtonType.OK.getText(), ButtonBar.ButtonData.CANCEL_CLOSE);

    private final static MustacheFactory mf = new DefaultMustacheFactory();

    private final TimeLineController controller;
    private final Case currentCase;

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

        setEventHandler(actionEvent -> {
            ReportBranding reportBranding = new ReportBranding();
            Date generationDate = new Date();

            TextInputDialog textInputDialog = new TextInputDialog();

            textInputDialog.setTitle("Timeline");
            textInputDialog.setHeaderText("Enter a report name.");
            Optional<String> showAndWait = textInputDialog.showAndWait();

            String reportName = showAndWait.orElseGet(() -> FileUtil.escapeFileName(currentCase.getName() + " " + new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss").format(generationDate)));
            Path reportFolderPath = Paths.get(currentCase.getReportDirectory(), reportName, "Timeline Snapshot");
            Path reportIndexFilePath = reportFolderPath.resolve("index.html");

            ZoomParams zoomParams = controller.getEventsModel().zoomParametersProperty().get();

            try {
                //ensure directory exists and write html files
                Files.createDirectories(reportFolderPath);
                writeSummary(reportFolderPath, generationDate, reportBranding);
                writeIndexHTML(reportFolderPath);
                writeSnapShotHTMLFile(reportFolderPath, reportName, zoomParams);

                //take snapshot and save in report directory
                ImageIO.write(SwingFXUtils.fromFXImage(node.snapshot(null, null), null), "png",
                        reportFolderPath.resolve("snapshot.png").toFile());

                copyResources(reportFolderPath, reportBranding);

                //add html file as report to case
                try {
                    Case.getCurrentCase().addReport(reportIndexFilePath.toString(), Bundle.Timeline_ModuleName(), reportName); //
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
                final OpenReportAction openReportAction = new OpenReportAction(reportIndexFilePath);
                HyperlinkLabel hyperlinkLabel = new HyperlinkLabel(Bundle.SaveSnapShotAsReport_ReportSavedAt(reportIndexFilePath.toString()));
                hyperlinkLabel.setOnAction(openReportAction);
                alert.getDialogPane().setContent(hyperlinkLabel);

                alert.showAndWait()
                        .filter(OPEN::equals)
                        .ifPresent(buttonType -> openReportAction.handle(null));

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error writing report " + reportFolderPath + " to disk", e); //
                new Alert(Alert.AlertType.ERROR, Bundle.SaveSnapShotAsReport_ErrorWritingReport(reportFolderPath)).showAndWait();
            }
        });
    }

    private static void writeSnapShotHTMLFile(Path reportPath, String reportTitle, ZoomParams zoomParams) throws IOException {

        HashMap<String, Object> context = new HashMap<>();

        context.put("reportTitle", reportTitle);
        Interval timeRange = zoomParams.getTimeRange();
        context.put("startTime", timeRange.getStart().toString(DateTimeFormat.fullDateTime()));
        context.put("endTime", timeRange.getEnd().toString(DateTimeFormat.fullDateTime()));
        context.put("zoomParams", zoomParams);

        try (Writer writer = Files.newBufferedWriter(reportPath.resolve("snapshot.html"), Charset.forName("UTF-8"))) {
            Mustache summaryMustache = mf.compile(new InputStreamReader(SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/actions/snapshot_template.html")), "Snapshot");
            summaryMustache.execute(writer, context);
            writer.flush();
        }
    }

    private void writeIndexHTML(Path reportPath) throws IOException {

        HashMap<String, Object> context = new HashMap<>();

        context.put("currentCase", currentCase);

        try (Writer writer = Files.newBufferedWriter(reportPath.resolve("index.html"), Charset.forName("UTF-8"))) {
            Mustache summaryMustache = mf.compile(new InputStreamReader(SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/actions/index_template.html")), "Index");
            summaryMustache.execute(writer, context);
            writer.flush();
        }
    }

    /**
     * Write the summary of the current case for this report.
     *
     * @param reportPath     the value of reportPath
     * @param generationDate the value of generationDate
     */
    @NbBundle.Messages({
        "ReportHTML.writeSum.caseName=Case:",
        "ReportHTML.writeSum.caseNum=Case Number:",
        "ReportHTML.writeSum.noCaseNum=<i>No case number</i>",
        "ReportHTML.writeSum.noExaminer=<i>No examiner</i>",
        "ReportHTML.writeSum.examiner=Examiner:",
        "ReportHTML.writeSum.timezone=Timezone:",
        "ReportHTML.writeSum.path=Path:"})
    private void writeSummary(Path reportPath, final Date generationDate, ReportBranding reportBranding) throws IOException {
        HashMap<String, Object> context = new HashMap<>();

        context.put("reportBranding", reportBranding);
        context.put("generationDateTime", new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(generationDate));
        context.put("ingestRunning", IngestManager.getInstance().isIngestRunning());
        context.put("currentCase", currentCase);

        try (Writer writer = Files.newBufferedWriter(reportPath.resolve("summary.html"), Charset.forName("UTF-8"))) {

            Mustache summaryMustache = mf.compile(new InputStreamReader(SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/actions/summary_template.html")), "Summary");
            summaryMustache.execute(writer, context);
            writer.flush();
        }
    }

    private void copyResources(Path reportPath, ReportBranding reportBranding) throws IOException {
        //copy navigation html
        try (InputStream navStream = SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/actions/navigation_template.html")) {
            Files.copy(navStream, reportPath.resolve("nav.html"));
        }

        //pull generator and agency logo from branding
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
        try (InputStream faviconStream = getClass().getResourceAsStream("/org/sleuthkit/autopsy/report/images/summary.png")) {
            Files.copy(faviconStream, reportPath.resolve("summary.png"));
        }

        //copy report css
        try (InputStream resource = SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/index.css")) { //
            Files.copy(resource, reportPath.resolve("index.css")); //
        }
        //copy summary css
        try (InputStream resource = SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/actions/summary.css")) { //
            Files.copy(resource, reportPath.resolve("summary.css")); //
        }
    }

    @NbBundle.Messages({"OpenReportAction.DisplayName=Open Report",
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
