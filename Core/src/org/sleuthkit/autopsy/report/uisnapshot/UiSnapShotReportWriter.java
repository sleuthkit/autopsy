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
package org.sleuthkit.autopsy.report.uisnapshot;

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
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.ReportBranding;

/**
 * Generate and write the snapshot report to disk.
 */
public abstract class UiSnapShotReportWriter {

    /**
     * mustache.java template factory.
     */
    private final static MustacheFactory mf = new DefaultMustacheFactory();

    private final Case currentCase;
    private final Path reportFolderPath;
    private final String reportName;
    private final ReportBranding reportBranding;

    private Date generationDate;

    /**
     * Constructor
     *
     * @param currentCase      The Case to write a report for.
     * @param reportFolderPath The Path to the folder that will contain the
     *                         report.
     * @param reportName       The name of the report.
     * @param generationDate   The generation Date of the report.
     */
    protected UiSnapShotReportWriter(Case currentCase, Path reportFolderPath, String reportName, Date generationDate) {
        this.currentCase = currentCase;
        this.reportFolderPath = reportFolderPath;
        this.reportName = reportName;
        this.generationDate = generationDate;

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

        copyResources();

        writeSummaryHTML();
        writeSnapShotHTMLFile();
        return writeIndexHTML();
    }
    
    /**
     * Get the name for the report.
     * 
     * @return Returns the reportName
     */
    protected String getReportName() {
        return reportName;
    }
    
    /**
     * Get the folder path for the report.
     * 
     * @return Report folder path
     */
    protected Path getReportFolderPath() {
        return reportFolderPath;
    }
    
    /**
     * Get the case for this report.
     * 
     * @return Current case object 
     */
    protected Case getCurrentCase() {
        return currentCase;
    }

    /**
     * Generate and write the html page that shows the snapshot and the state of
     * any filters.
     *
     * @throws IOException If there is a problem writing the html file to disk.
     */
    protected abstract void writeSnapShotHTMLFile() throws IOException ;

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

        fillTemplateAndWrite("/org/sleuthkit/autopsy/report/uisnapshot/index_template.html", "Index", indexContext, reportIndexFile); //NON-NLS
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
        String agencyLogo = "agency_logo.png"; //default name for agency logo.
        if (StringUtils.isNotBlank(reportBranding.getAgencyLogoPath())){
            agencyLogo = Paths.get(reportBranding.getAgencyLogoPath()).getFileName().toString();
        }
        summaryContext.put("agencyLogoFileName", agencyLogo);
        fillTemplateAndWrite("/org/sleuthkit/autopsy/report/uisnapshot/summary_template.html", "Summary", summaryContext, reportFolderPath.resolve("summary.html")); //NON-NLS
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
    protected void fillTemplateAndWrite(final String templateLocation, final String templateName, Object context, final Path outPutFile) throws IOException {

        Mustache summaryMustache = mf.compile(new InputStreamReader(UiSnapShotReportWriter.class.getResourceAsStream(templateLocation)), templateName);
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
            Files.copy(Files.newInputStream(Paths.get(agencyLogoPath)), reportFolderPath.resolve(Paths.get(reportBranding.getAgencyLogoPath()).getFileName())); //NON-NLS
        }
        
        //copy favicon
        if (StringUtils.isBlank(agencyLogoPath)) {
            copyInternalResource("/org/sleuthkit/autopsy/report/images/favicon.ico", "favicon.ico");
        } else {
            Files.copy(Files.newInputStream(Paths.get(agencyLogoPath)), reportFolderPath.resolve("favicon.ico")); //NON-NLS           
        }

        copyInternalResource("/org/sleuthkit/autopsy/report/uisnapshot/navigation.html", "nav.html");
        copyInternalResource("/org/sleuthkit/autopsy/report/images/summary.png", "summary.png");
        copyInternalResource("/org/sleuthkit/autopsy/report/images/image.png", "snapshot_icon.png");
        copyInternalResource("/org/sleuthkit/autopsy/report/uisnapshot/index.css", "index.css");
        copyInternalResource("/org/sleuthkit/autopsy/report/uisnapshot/summary.css", "summary.css");
    }
    
    /**
     * Copies internal resource to the report folder.
     * 
     * @param internalPath Location in jar of the image
     * @param fileName Name to give resource in new location
     * 
     * @throws IOException 
     */
    private void copyInternalResource(String internalPath, String fileName) throws IOException{
        try (InputStream resource = UiSnapShotReportWriter.class.getResourceAsStream(internalPath)) { //NON-NLS
            Files.copy(resource, reportFolderPath.resolve(fileName)); //NON-NLS
        }
    }
}
