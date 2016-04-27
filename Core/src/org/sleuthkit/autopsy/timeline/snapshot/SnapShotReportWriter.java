/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.snapshot;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
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
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.ReportBranding;
import org.sleuthkit.autopsy.timeline.actions.SaveSnapshotAsReport;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;

/**
 *
 */
public class SnapShotReportWriter {

    private final static MustacheFactory mf = new DefaultMustacheFactory();
    private final Case currentCase;
    private final Path reportFolderPath;
    private final String reportName;
    private final ZoomParams zoomParams;
    private final Date generationDate;

    public SnapShotReportWriter(Case currentCase, Path reportFolderPath ,String reportName, ZoomParams zoomParams, Date generationDate, Node node) {
        this.currentCase = currentCase;
        this.reportFolderPath = reportFolderPath;
        this.reportName = reportName;
        this.zoomParams = zoomParams;
        this.generationDate = generationDate;
    }

    public Path writeReport() throws IOException {
        ReportBranding reportBranding = new ReportBranding();

        
        Path reportIndexFilePath = reportFolderPath.resolve("index.html");

        //ensure directory exists and write html files
        Files.createDirectories(reportFolderPath);
        writeSummary(reportFolderPath, generationDate, reportBranding, reportName);
        writeIndexHTML(reportFolderPath);
        writeSnapShotHTMLFile(reportFolderPath, reportName, zoomParams);

        //take snapshot and save in report directory
        ImageIO.write(SwingFXUtils.fromFXImage(node.snapshot(null, null), null), "png",
                reportFolderPath.resolve("snapshot.png").toFile());

        copyResources(reportFolderPath, reportBranding);

        return reportIndexFilePath;
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
    private void writeSummary(Path reportPath, final Date generationDate, ReportBranding reportBranding, String reportName) throws IOException {
        HashMap<String, Object> context = new HashMap<>();

        context.put("reportName", reportName);
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
            Files.copy(Paths.get(generatorLogoPath), reportPath.resolve("generator_logo.png"));
        }

        String agencyLogoPath = reportBranding.getAgencyLogoPath();
        if (StringUtils.isNotBlank(agencyLogoPath)) {
            Files.copy(Paths.get(agencyLogoPath), reportPath.resolve("agency_logo.png"));
        }

        //copy favicon
        try (InputStream faviconStream = SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/report/images/favicon.ico")) {
            Files.copy(faviconStream, reportPath.resolve("favicon.ico"));
        }
        try (InputStream summaryStream = SaveSnapshotAsReport.class.getResourceAsStream("/org/sleuthkit/autopsy/report/images/summary.png")) {
            Files.copy(summaryStream, reportPath.resolve("summary.png"));
        }
        try (InputStream snapshotIconStream = SaveSnapshotAsReport.class.getResourceAsStream("org/sleuthkit/autopsy/timeline/images/image.png")) {
            Files.copy(snapshotIconStream, reportPath.resolve("snapshot_icon.png"));
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
}
