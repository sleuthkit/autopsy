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
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPanel;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.*;

/**
 * ReportBodyFile generates a report in the body file format specified on The
 * Sleuth Kit wiki as
 * MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime.
 */
class ReportBodyFile implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(ReportBodyFile.class.getName());
    private static ReportBodyFile instance = null;

    private Case currentCase;
    private SleuthkitCase skCase;

    private String reportPath;

    // Hidden constructor for the report
    private ReportBodyFile() {
    }

    // Get the default implementation of this report
    public static synchronized ReportBodyFile getDefault() {
        if (instance == null) {
            instance = new ReportBodyFile();
        }
        return instance;
    }

    /**
     * Generates a body file format report for use with the MAC time tool.
     *
     * @param baseReportDir path to save the report
     * @param progressPanel panel to update the report's progress
     */
    @Override
    @SuppressWarnings("deprecation")
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        // Start the progress bar and setup the report
        try {
            currentCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            return;
        }
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportBodyFile.progress.querying"));
        reportPath = baseReportDir + getRelativeFilePath(); //NON-NLS
        
        skCase = currentCase.getSleuthkitCase();

        // Run query to get all files
        try {
            // exclude non-fs files/dirs and . and .. files
            final String query = "type = " + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() //NON-NLS
                    + " AND name != '.' AND name != '..'"; //NON-NLS

            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportBodyFile.progress.loading"));
            List<AbstractFile> fs = skCase.findAllFilesWhere(query);

            // Check if ingest has finished
            String ingestwarning = "";
            if (IngestManager.getInstance().isIngestRunning()) {
                ingestwarning = NbBundle.getMessage(this.getClass(), "ReportBodyFile.ingestWarning.text");
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
                                NbBundle.getMessage(this.getClass(), "ReportBodyFile.progress.processing",
                                        file.getName()));
                        count = 0;
                    }

                    if (file.getMd5Hash() != null) {
                        out.write(file.getMd5Hash());
                    }
                    out.write("|");
                    if (file.getUniquePath() != null) {
                        out.write(file.getUniquePath());
                    }
                    out.write("|");
                    out.write(Long.toString(file.getMetaAddr()));
                    out.write("|");
                    String modeString = file.getModesAsString();
                    if (modeString != null) {
                        out.write(modeString);
                    }
                    out.write("|");
                    out.write(Long.toString(file.getUid()));
                    out.write("|");
                    out.write(Long.toString(file.getGid()));
                    out.write("|");
                    out.write(Long.toString(file.getSize()));
                    out.write("|");
                    out.write(Long.toString(file.getAtime()));
                    out.write("|");
                    out.write(Long.toString(file.getMtime()));
                    out.write("|");
                    out.write(Long.toString(file.getCtime()));
                    out.write("|");
                    out.write(Long.toString(file.getCrtime()));
                    out.write("\n");
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not write the temp body file report.", ex); //NON-NLS
            } finally {
                try {
                    if (out != null) {
                        out.flush();
                        out.close();
                        Case.getOpenCase().addReport(reportPath,
                                NbBundle.getMessage(this.getClass(),
                                        "ReportBodyFile.generateReport.srcModuleName.text"), "");

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
        String name = NbBundle.getMessage(this.getClass(), "ReportBodyFile.getName.text");
        return name;
    }

    @Override
    public String getRelativeFilePath() {
        return NbBundle.getMessage(this.getClass(), "ReportBodyFile.getFilePath.text");
    }

    @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "ReportBodyFile.getDesc.text");
        return desc;
    }

    @Override
    public JPanel getConfigurationPanel() {
        return null; // No configuration panel
    }
}
