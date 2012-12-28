 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.*;

/**
 * ReportBodyFile generates a report in the body file format specified on
 * The Sleuth Kit wiki as MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime.
 */
public class ReportBodyFile implements GeneralReportModule {
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
     * @param path path to save the report
     * @param progressPanel panel to update the report's progress
     */
    @Override
    @SuppressWarnings("deprecation")
    public void generateReport(String path, ReportProgressPanel progressPanel) {
        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.updateStatusLabel("Querying files...");  
        reportPath = path + "BodyFile.txt";
        currentCase = Case.getCurrentCase();
        skCase = currentCase.getSleuthkitCase();
        
        // Run query to get all files
        ResultSet rs = null;
        try {
            // exclude non-fs files/dirs and . and .. files
            rs = skCase.runQuery("SELECT * FROM tsk_files "
                               + "WHERE type = '" + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() + "' "
                               + "AND name != '.' "
                               + "AND name != '..'");
            
            progressPanel.updateStatusLabel("Loading files...");  
            List<FsContent> fs = skCase.resultSetToFsContents(rs);
            
            // Check if ingest has finished
            String ingestwarning = "";
            if (IngestManager.getDefault().isIngestRunning()) {
                ingestwarning = "Warning, this report was run before ingest services completed!\n";
            }
            
            int size = fs.size();
            progressPanel.setMaximumProgress(size/100);
                
            BufferedWriter out = null;
            try {
                // MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime
                out = new BufferedWriter(new FileWriter(reportPath, true));
                out.write(ingestwarning);
                // Loop files and write info to report
                int count = 0;
                for (FsContent file : fs) {
                    if (progressPanel.getStatus() == ReportStatus.CANCELED) {
                        break;
                    }
                    if(count++ == 100) {
                        progressPanel.increment();
                        progressPanel.updateStatusLabel("Now processing " + file.getName() + "...");
                        count = 0;
                    }

                    if(file.getMd5Hash()!=null) {
                        out.write(file.getMd5Hash());
                    }
                    out.write("|");
                    if(file.getUniquePath()!=null) {
                        out.write(file.getUniquePath());
                    }
                    out.write("|");
                    out.write(Long.toString(file.getMetaAddr()));
                    out.write("|");
                    String modeString = file.getModesAsString();
                    if(modeString != null) {
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
                logger.log(Level.WARNING, "Could not write the temp body file report.", ex);
            } finally {
                try {
                    out.flush();
                    out.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not flush and close the BufferedWriter.", ex);
                }
            }
            progressPanel.complete();
        } catch(SQLException ex) {
            logger.log(Level.WARNING, "Failed to get all file information.", ex);
        } catch(TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get the unique path.", ex);
        } finally {
            try {// Close the query
                if(rs!=null) {
                    skCase.closeRunQuery(rs);
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to close the query.", ex);
            }
        }
    }

    @Override
    public String getName() {
        String name = "Body File";
        return name;
    }

    @Override
    public String getFilePath() {
        return "BodyFile.txt";
    }

    @Override
    public String getExtension() {
        String ext = ".txt";
        return ext;
    }

    @Override
    public String getDescription() {
        String desc = "Body file format report with MAC times for every file. This format can be used for a timeline view.";
        return desc;
    }

    @Override
    public JPanel getConfigurationPanel() {
        return null; // No configuration panel
    }
}
