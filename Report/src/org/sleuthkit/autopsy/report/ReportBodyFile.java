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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.*;

/**
 * ReportBodyFile generates a report in the body file format specified on
 * The Sleuth Kit wiki as MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime.
 */
public class ReportBodyFile implements ReportModule {
    //Declare our publically accessible formatted Report, this will change everytime they run a Report
    public static StringBuilder formatted_Report = new StringBuilder();
    private static String bodyFilePath = "";
    private ReportConfiguration config;
    private static ReportBodyFile instance = null;
    private Case currentCase = Case.getCurrentCase(); // get the current case
    private SleuthkitCase skCase = currentCase.getSleuthkitCase();
    private final Logger logger = Logger.getLogger(ReportBodyFile.class.getName());

    ReportBodyFile() {
    }

    public static synchronized ReportBodyFile getDefault() {
        if (instance == null) {
            instance = new ReportBodyFile();
        }
        return instance;
    }

    @Override
    public String generateReport(ReportConfiguration reportconfig) throws ReportModuleException {
        config = reportconfig;
        ReportGen reportobj = new ReportGen();
        reportobj.populateReport(reportconfig);
        HashMap<BlackboardArtifact, ArrayList<BlackboardAttribute>> report = reportobj.Results;
        
        // Clear the StringBuilder
        formatted_Report.setLength(0);
        // Setup timestamp
        DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String datetime = datetimeFormat.format(date);
        String datenotime = dateFormat.format(date);
        
        // Run query to get all files
        ResultSet rs = null;
        try {
            rs = skCase.runQuery("SELECT * FROM tsk_files");
            List<FsContent> fs = skCase.resultSetToFsContents(rs);
            // Check if ingest finished
            if (IngestManager.getDefault().isIngestRunning()) {
                String ingestwarning = "Warning, this report was run before ingest services completed!";
                formatted_Report.append(ingestwarning);
            }
            // Loop files and write info to report
            for (FsContent file : fs) {
                if (ReportFilter.cancel == true) {
                    break;
                }
                // MD5|name|inode|mode_as_string|UID|GID|size|atime|mtime|ctime|crtime
                formatted_Report.append(file.getMd5Hash()).append("|");
                formatted_Report.append(file.getUniquePath()).append("|");
                formatted_Report.append(file.getMeta_addr()).append("|"); // Use instead of inode
                formatted_Report.append(file.getModeAsString()).append("|");
                formatted_Report.append(file.getUid()).append("|");
                formatted_Report.append(file.getGid()).append("|");
                formatted_Report.append(file.getSize()).append("|");
                formatted_Report.append(file.getAtime()).append("|");
                formatted_Report.append(file.getMtime()).append("|");
                formatted_Report.append(file.getCtime()).append("|");
                formatted_Report.append(file.getCrtime()).append("|");
                formatted_Report.append("\n");
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
        
        try {
            bodyFilePath = currentCase.getCaseDirectory() + File.separator + "Reports" +
                    File.separator + currentCase.getName() + "-" + datenotime + ".txt";
            this.save(bodyFilePath);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Could not write out body file report! ", ex);
        }
        return bodyFilePath;
    }

    @Override
    public String getName() {
        String name = "Body File";
        return name;
    }

    @Override
    public void save(String path) {
        try {
            Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), "UTF-8"));
            out.write(formatted_Report.toString());
            out.flush();
            out.close();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not write out body file report!", ex);
        }

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
