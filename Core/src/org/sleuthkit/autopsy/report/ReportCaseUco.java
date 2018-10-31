 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2018 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.report;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPanel;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.*;

/**
 * ReportCaseUco generates a report in the CASE/UCO format. It saves basic
 * file info like full path, name, MIME type, times, and hash.
 */
class ReportCaseUco implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(ReportCaseUco.class.getName());
    private static ReportCaseUco instance = null;

    private Case currentCase;
    private SleuthkitCase skCase;

    private String reportPath;
    
    private JsonFactory jsonGeneratorFactory;
    private JsonGenerator masterCatalog;
    
    // Hidden constructor for the report
    private ReportCaseUco() {
    }

    // Get the default implementation of this report
    public static synchronized ReportCaseUco getDefault() {
        if (instance == null) {
            instance = new ReportCaseUco();
        }
        return instance;
    }

    /**
     * Generates a CASE/UCO format report.
     *
     * @param baseReportDir path to save the report
     * @param progressPanel panel to update the report's progress
     */
    @Override
    @SuppressWarnings("deprecation")
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        // Start the progress bar and setup the report
        try {
            currentCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            return;
        }
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportCaseUco.progress.querying"));
        
        // Create the JSON generator
        jsonGeneratorFactory = new JsonFactory();
        jsonGeneratorFactory.setRootValueSeparator("\r\n");
        reportPath = baseReportDir + getRelativeFilePath(); //NON-NLS
        Path catalogPath = Paths.get(reportPath);
        try {
            Files.createDirectories(catalogPath.getParent());
            java.io.File catalogFile = catalogPath.toFile();
            masterCatalog = jsonGeneratorFactory.createGenerator(catalogFile, JsonEncoding.UTF8);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error while initializing CASE/UCO report", ex); //NON-NLS
            // ELTODO what else needs to be done here?
            return;
        }
        
        skCase = currentCase.getSleuthkitCase();

        // Run query to get all files
        try {
            // exclude non-fs files/dirs and . and .. files
            final String query = "type = " + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() //NON-NLS
                    + " AND name != '.' AND name != '..'"; //NON-NLS

            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportCaseUco.progress.loading"));
            List<AbstractFile> fs = skCase.findAllFilesWhere(query);

            // Check if ingest has finished
            String ingestwarning = "";
            if (IngestManager.getInstance().isIngestRunning()) {
                ingestwarning = NbBundle.getMessage(this.getClass(), "ReportCaseUco.ingestWarning.text");
            }

            int size = fs.size();
            progressPanel.setMaximumProgress(size / 100);
            
            BufferedWriter out = null;
            try {
                // MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime
                out = new BufferedWriter(new FileWriter(reportPath, true));
                out.write(ingestwarning);
                // Loop files and write info to report
                int count = 0;
                for (AbstractFile file : fs) {
                    if (progressPanel.getStatus() == ReportStatus.CANCELED) {
                        break;
                    }
                    if (count++ == 100) {
                        progressPanel.increment();
                        progressPanel.updateStatusLabel(
                                NbBundle.getMessage(this.getClass(), "ReportCaseUco.progress.processing",
                                        file.getName()));
                        count = 0;
                    }


                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not write the temp CASE/UCO report.", ex); //NON-NLS
            } finally {
                try {
                    if (out != null) {
                        out.flush();
                        out.close();
                        Case.getCurrentCaseThrows().addReport(reportPath,
                                NbBundle.getMessage(this.getClass(),
                                        "ReportCaseUco.generateReport.srcModuleName.text"), "");

                    }
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not flush and close the BufferedWriter.", ex); //NON-NLS
                } catch (TskCoreException | NoCurrentCaseException ex) {
                    String errorMessage = String.format("Error adding %s to case as a report", reportPath); //NON-NLS
                    logger.log(Level.SEVERE, errorMessage, ex);
                }
            }
            progressPanel.complete(ReportStatus.COMPLETE);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get the unique path.", ex); //NON-NLS
        }
    }

    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "ReportCaseUco.getName.text");
        return name;
    }

    @Override
    public String getRelativeFilePath() {
        return NbBundle.getMessage(this.getClass(), "ReportCaseUco.getFilePath.text");
    }

    @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "ReportCaseUco.getDesc.text");
        return desc;
    }

    @Override
    public JPanel getConfigurationPanel() {
        return null; // No configuration panel
    }
}
