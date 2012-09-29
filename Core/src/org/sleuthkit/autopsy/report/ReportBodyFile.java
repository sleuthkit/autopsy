 /*
 *
 * Autopsy Forensic Browser
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.*;

/**
 * ReportBodyFile generates a report in the body file format specified on
 * The Sleuth Kit wiki as MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime.
 */
public class ReportBodyFile implements ReportModule {
    //Declare our publically accessible formatted Report, this will change everytime they run a Report
    private static String bodyFilePath = "";
    private ReportConfiguration config;
    private static ReportBodyFile instance = null;
    private Case currentCase = Case.getCurrentCase(); // get the current case
    private SleuthkitCase skCase = currentCase.getSleuthkitCase();
    private static final Logger logger = Logger.getLogger(ReportBodyFile.class.getName());

    ReportBodyFile() {
    }

    public static synchronized ReportBodyFile getDefault() {
        if (instance == null) {
            instance = new ReportBodyFile();
        }
        return instance;
    }

    /**
     * Generates a Body File report in the Reports folder of the current case.
     * 
     * @param reportconfig  unused in the body file
     * @return  the path to the generated report
     * @throws ReportModuleException 
     */
    @Override
    public String generateReport(ReportConfiguration reportconfig) throws ReportModuleException {
        config = reportconfig;
        
        // Setup timestamp
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String datenotime = dateFormat.format(date);
        
        // Get report path
        bodyFilePath = currentCase.getCaseDirectory() + File.separator + "Reports" +
                File.separator + currentCase.getName() + "-" + datenotime + ".txt";
        
        // Run query to get all files
        ResultSet rs = null;
        try {
            // exclude non-fs files/dirs and . and .. files
            rs = skCase.runQuery("SELECT * FROM tsk_files "
                               + "WHERE type = '" + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() + "' "
                               + "AND name != '.' "
                               + "AND name != '..'");
            List<FsContent> fs = skCase.resultSetToFsContents(rs);
            // Check if ingest finished
            String ingestwarning = "";
            if (IngestManager.getDefault().isIngestRunning()) {
                ingestwarning = "Warning, this report was run before ingest services completed!\n";
            }
            // Loop files and write info to report
            for (FsContent file : fs) {
                if (ReportFilter.cancel == true) {
                    break;
                }
                
                BufferedWriter out = null;
                try {
                    // MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime
                    out = new BufferedWriter(new FileWriter(bodyFilePath, true));
                    out.write(ingestwarning);
                    
                    if(file.getMd5Hash()!=null) {
                        out.write(file.getMd5Hash());
                    }
                    out.write("|");
                    if(file.getUniquePath()!=null) {
                        out.write(file.getUniquePath());
                    }
                    out.write("|");
                    out.write(Long.toString(file.getMeta_addr()));
                    out.write("|");
                    if(file.getModeAsString()!=null) {
                        out.write(file.getModeAsString());
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
            }
        } catch(SQLException ex) {
            logger.log(Level.WARNING, "Failed to get all file information.", ex);
        } catch(TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get the unique path.", ex);
        } finally {
            try {// Close the query
                if(rs!=null) { skCase.closeRunQuery(rs); }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to close the query.", ex);
            }
        }
        
        return bodyFilePath;
    }

    @Override
    public String getName() {
        String name = "Body File";
        return name;
    }

    /**
     * Save the previously generated report to the given path.
     * If the report was not generated in generateReport, save will attempt
     * to regenerate it, then copy the file. If the regeneration fails, the 
     * incident is logged.
     */
    @Override
    public void save(String path) {
        File caseFile = new File(bodyFilePath);
        if(!caseFile.exists()) {
            logger.log(Level.WARNING, "Body File report does not exist.");
            try {
                // Try to generate it again
                generateReport(config);
                logger.log(Level.INFO, "Body File report has been regenerated.");
            } catch (ReportModuleException ex) {
                logger.log(Level.WARNING, "Failed attempt to regenerate the report.", ex);
            }
        }
        // Check again
        if(caseFile.exists()) {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new FileInputStream(caseFile);
                out = new FileOutputStream(path);
                byte[] b  = new byte[Integer.parseInt(Long.toString(caseFile.length()))];
                int len = b.length;
                int total = 0;
                int result = 0;

                while ((result = in.read(b, total, len-total)) > 0) {
                  out.write(b, total, len);
                  total += result;
                }
            } catch(FileNotFoundException ex) {
                logger.log(Level.WARNING, "Could find the file specified.", ex);
            } catch(IOException ex) {
                logger.log(Level.WARNING, "Could not read from the FileInputStream.", ex);
            } finally {
                try {
                    in.close();
                    out.flush();
                    out.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close and flush the streams.", ex);
                }
            }
        }
        // Otherwise give up
    }

    @Override
    public String getReportType() {
        String type = "BodyFile";
        return type;
    }

    @Override
    public String getExtension() {
        String ext = ".txt";
        return ext;
    }

    @Override
    public ReportConfiguration GetReportConfiguration() {
        return config;
    }

    @Override
    public String getReportTypeDescription() {
        String desc = "This is an body file format report.";
        return desc;
    }

    @Override
    public void getPreview(String path) {
        BrowserControl.openUrl(path);
    }
}
