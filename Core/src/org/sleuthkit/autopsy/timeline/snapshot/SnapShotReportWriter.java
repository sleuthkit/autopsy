/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.snapshot;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.awt.image.BufferedImage;
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
import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.format.DateTimeFormat;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.ReportBranding;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;

/**
 * Generate and write the Timeline snapshot report to disk.
 */
public class SnapShotReportWriter {

    /**
     * mustache.java template factory.
     */
    private final static MustacheFactory mf = new DefaultMustacheFactory();

    private final Case currentCase;
    private final Path reportFolderPath;
    private final String reportName;
    private final ReportBranding reportBranding;

    private final ZoomParams zoomParams;
    private final Date generationDate;
    private final BufferedImage image;

    /**
     * Constructor
     *
     * @param currentCase      The Case to write a report for.
     * @param reportFolderPath The Path to the folder that will contain the
     *                         report.
     * @param reportName       The name of the report.
     * @param zoomParams       The ZoomParams in effect when the snapshot was
     *                         taken.
     * @param generationDate   The generation Date of the report.
     * @param snapshot         A snapshot of the view to include in the
     *                         report.
     */
    public SnapShotReportWriter(Case currentCase, Path reportFolderPath, String reportName, ZoomParams zoomParams, Date generationDate, BufferedImage snapshot) {
        this.currentCase = currentCase;
        this.reportFolderPath = reportFolderPath;
        this.reportName = reportName;
        this.zoomParams = zoomParams;
        this.generationDate = generationDate;
        this.image = snapshot;

        this.reportBranding = new ReportBranding();
    }

    /**
     * Generate and write the report to disk.
     *
     * @return The Path to the "main file" of the report. This is the file that
     *         Autopsy shows in the results view when the Reports Node is
     *         selected in the DirectoryTree.
     *
     * @throws IOException If there is a problem writing the report.
     */
    public Path writeReport() throws IOException {
        //ensure directory exists 
        Files.createDirectories(reportFolderPath);

        //save the snapshot in the report directory
        ImageIO.write(image, "png", reportFolderPath.resolve("snapshot.png").toFile()); //NON-NLS

        copyResources();

        writeSummaryHTML();
        writeSnapShotHTMLFile();
        return writeIndexHTML();
    }

    /**
     * Generate and write the html page that shows the snapshot and the state of
     * the ZoomParams
     *
     * @throws IOException If there is a problem writing the html file to disk.
     */
    private void writeSnapShotHTMLFile() throws IOException {
        //make a map of context objects to resolve template paramaters against
        HashMap<String, Object> snapShotContext = new HashMap<>();
        snapShotContext.put("reportTitle", reportName); //NON-NLS
        snapShotContext.put("startTime", zoomParams.getTimeRange().getStart().toString(DateTimeFormat.fullDateTime())); //NON-NLS
        snapShotContext.put("endTime", zoomParams.getTimeRange().getEnd().toString(DateTimeFormat.fullDateTime())); //NON-NLS
        snapShotContext.put("zoomParams", zoomParams); //NON-NLS

        fillTemplateAndWrite("/org/sleuthkit/autopsy/timeline/snapshot/snapshot_template.html", "Snapshot", snapShotContext, reportFolderPath.resolve("snapshot.html")); //NON-NLS
    }

    /**
     * Generate and write the main html page with frames for navigation on the
     * left and content on the right.
     *
     * @return The Path of the written html file.
     *
     * @throws IOException If there is a problem writing the html file to disk.
     */
    private Path writeIndexHTML() throws IOException {
        //make a map of context objects to resolve template paramaters against
        HashMap<String, Object> indexContext = new HashMap<>();
        indexContext.put("reportBranding", reportBranding); //NON-NLS
        indexContext.put("reportName", reportName); //NON-NLS
        Path reportIndexFile = reportFolderPath.resolve("index.html"); //NON-NLS

        fillTemplateAndWrite("/org/sleuthkit/autopsy/timeline/snapshot/index_template.html", "Index", indexContext, reportIndexFile); //NON-NLS
        return reportIndexFile;
    }

    /**
     * * Generate and write the summary of the current case for this report.
     *
     * @throws IOException If there is a problem writing the html file to disk.
     */
    private void writeSummaryHTML() throws IOException {
        //make a map of context objects to resolve template paramaters against
        HashMap<String, Object> summaryContext = new HashMap<>();
        summaryContext.put("reportName", reportName); //NON-NLS
        summaryContext.put("reportBranding", reportBranding); //NON-NLS
        summaryContext.put("generationDateTime", new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(generationDate)); //NON-NLS
        summaryContext.put("ingestRunning", IngestManager.getInstance().isIngestRunning()); //NON-NLS
        summaryContext.put("currentCase", currentCase); //NON-NLS

        fillTemplateAndWrite("/org/sleuthkit/autopsy/timeline/snapshot/summary_template.html", "Summary", summaryContext, reportFolderPath.resolve("summary.html")); //NON-NLS
    }

    /**
     * Fill in the mustache template at the given location using the values from
     * the given context object and save it to the given outPutFile.
     *
     * @param templateLocation The location of the template. suitible for use
     *                         with Class.getResourceAsStream
     * @param templateName     The name of the tempalte. (Used by mustache to
     *                         cache templates?)
     * @param context          The contect to use to fill in the template
     *                         values.
     * @param outPutFile       The filled in tempalte will be saced at this
     *                         Path.
     *
     * @throws IOException If there is a problem saving the filled in template
     *                     to disk.
     */
    private void fillTemplateAndWrite(final String templateLocation, final String templateName, Object context, final Path outPutFile) throws IOException {

        Mustache summaryMustache = mf.compile(new InputStreamReader(SnapShotReportWriter.class.getResourceAsStream(templateLocation)), templateName);
        try (Writer writer = Files.newBufferedWriter(outPutFile, Charset.forName("UTF-8"))) { //NON-NLS
            summaryMustache.execute(writer, context);
        }
    }

    /**
     * Copy static resources (static html, css, images, etc) to the reports
     * folder.
     *
     * @throws IOException If there is a problem copying the resources.
     */
    private void copyResources() throws IOException {

        //pull generator and agency logos from branding
        String generatorLogoPath = reportBranding.getGeneratorLogoPath();
        if (StringUtils.isNotBlank(generatorLogoPath)) {
            Files.copy(Files.newInputStream(Paths.get(generatorLogoPath)), reportFolderPath.resolve("generator_logo.png")); //NON-NLS
        }
        String agencyLogoPath = reportBranding.getAgencyLogoPath();
        if (StringUtils.isNotBlank(agencyLogoPath)) {
            Files.copy(Files.newInputStream(Paths.get(agencyLogoPath)), reportFolderPath.resolve("agency_logo.png")); //NON-NLS
        }

        //copy navigation html
        try (InputStream navStream = SnapShotReportWriter.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/snapshot/navigation.html")) { //NON-NLS
            Files.copy(navStream, reportFolderPath.resolve("nav.html")); //NON-NLS
        }
        //copy favicon
        if (StringUtils.isBlank(agencyLogoPath)) {
            // use default Autopsy icon if custom icon is not set
            try (InputStream faviconStream = SnapShotReportWriter.class.getResourceAsStream("/org/sleuthkit/autopsy/report/images/favicon.ico")) { //NON-NLS
                Files.copy(faviconStream, reportFolderPath.resolve("favicon.ico")); //NON-NLS
            }
        } else {
            Files.copy(Files.newInputStream(Paths.get(agencyLogoPath)), reportFolderPath.resolve("favicon.ico")); //NON-NLS           
        }

        //copy report summary icon
        try (InputStream summaryStream = SnapShotReportWriter.class.getResourceAsStream("/org/sleuthkit/autopsy/report/images/summary.png")) { //NON-NLS
            Files.copy(summaryStream, reportFolderPath.resolve("summary.png")); //NON-NLS
        }
        //copy snapshot icon
        try (InputStream snapshotIconStream = SnapShotReportWriter.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/images/image.png")) { //NON-NLS
            Files.copy(snapshotIconStream, reportFolderPath.resolve("snapshot_icon.png")); //NON-NLS
        }
        //copy main report css
        try (InputStream resource = SnapShotReportWriter.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/snapshot/index.css")) { //NON-NLS
            Files.copy(resource, reportFolderPath.resolve("index.css")); //NON-NLS
        }
        //copy summary css
        try (InputStream resource = SnapShotReportWriter.class.getResourceAsStream("/org/sleuthkit/autopsy/timeline/snapshot/summary.css")) { //NON-NLS
            Files.copy(resource, reportFolderPath.resolve("summary.css")); //NON-NLS
        }
    }
}
