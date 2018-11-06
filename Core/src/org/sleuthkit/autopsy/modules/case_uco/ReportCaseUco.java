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
package org.sleuthkit.autopsy.modules.case_uco;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import javax.swing.JPanel;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.SimpleTimeZone;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.*;

/**
 * ReportCaseUco generates a report in the CASE/UCO format. It saves basic
 * file info like full path, name, MIME type, times, and hash.
 */
class ReportCaseUco implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(ReportCaseUco.class.getName());
    private static ReportCaseUco instance = null;
    private ReportCaseUcoConfigPanel configPanel;

    private Case currentCase;
    private SleuthkitCase skCase;
    
    private static final String REPORT_FILE_NAME = "CaseUco.txt";
    
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
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportCaseUco.progress.initializing"));
        
        Long selectedDataSourceId = configPanel.getSelectedDataSourceId();
        
        // Create the JSON generator
        JsonFactory jsonGeneratorFactory = new JsonFactory();
        String reportPath = baseReportDir + getRelativeFilePath(); //NON-NLS
        java.io.File reportFile = Paths.get(reportPath).toFile();
        try {
            Files.createDirectories(Paths.get(reportFile.getParent()));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Unable to create directory for CASE/UCO report", ex); //NON-NLS
            // ELTODO what else needs to be done here?
            return;
        }
        
        skCase = currentCase.getSleuthkitCase();

        // Run query to get all files
        JsonGenerator jsonGenerator = null;
        SimpleTimeZone timeZone = new SimpleTimeZone(0, "GMT");
        try {
            jsonGenerator = jsonGeneratorFactory.createGenerator(reportFile, JsonEncoding.UTF8);
            // instert \n after each field for more readable formatting
            jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")));
            
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportCaseUco.progress.querying"));
            // exclude non-fs files/dirs and . and .. files
            final String query = "select obj_id, name, size, crtime, atime, mtime, md5, parent_path, mime_type, extension from tsk_files where "
                    + "data_source_obj_id = " + Long.toString(selectedDataSourceId)
                    + " AND ((meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_UNDEF.getValue()
                    + ") OR (meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue()
                    + ") OR (meta_type = " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT.getValue() + "))"; //NON-NLS

            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "ReportCaseUco.progress.loading"));

            SleuthkitCase.CaseDbQuery queryResult = skCase.executeQuery(query);
            ResultSet resultSet = queryResult.getResultSet();
            // Check if ingest has finished
            String ingestwarning = "";
            if (IngestManager.getInstance().isIngestRunning()) {
                ingestwarning = NbBundle.getMessage(this.getClass(), "ReportCaseUco.ingestWarning.text");
            }
            // ELTODO what to do with this warning?

            int numFiles = 1000; // ELTODO resultSet.size();
            progressPanel.setMaximumProgress(numFiles / 100);
            
            // Loop files and write info to report
            int count = 0;
            while (resultSet.next()) {

                if (progressPanel.getStatus() == ReportStatus.CANCELED) {
                    break;
                }
                
                Long objectId = resultSet.getLong(1);
                String fileName = resultSet.getString(2);
                long size = resultSet.getLong("size");
                String crtime = ContentUtils.getStringTimeISO8601(resultSet.getLong("crtime"), timeZone);
                String atime = ContentUtils.getStringTimeISO8601(resultSet.getLong("atime"), timeZone);
                String mtime = ContentUtils.getStringTimeISO8601(resultSet.getLong("mtime"), timeZone);
                String md5Hash = resultSet.getString("md5"); 
                String parent_path = resultSet.getString("parent_path"); 
                String mime_type = resultSet.getString("mime_type"); 
                String extension = resultSet.getString("extension");
                
                addFile(objectId, fileName, parent_path, md5Hash, mime_type, size, crtime, atime, mtime, extension, jsonGenerator);
                
                /* ELTODO if (count++ == 100) {
                    progressPanel.increment();
                    progressPanel.updateStatusLabel(
                            NbBundle.getMessage(this.getClass(), "ReportCaseUco.progress.processing",
                                    file.getName()));
                    count = 0;
                }*/

            }
            progressPanel.complete(ReportStatus.COMPLETE);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get the unique path.", ex); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to create JSON output for the CASE/UCO report", ex); //NON-NLS
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Unable to read result set", ex); //NON-NLS
        } finally {
            if (jsonGenerator != null) {
                try {
                    jsonGenerator.close();
                    // ELTODO investigate database closing issue
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Failed to close JSON output file", ex); //NON-NLS
                }
            }
        }
    }

    private void addFile(Long objectId, String fileName, String parent_path, String md5Hash, String mime_type, long size, String ctime, 
            String atime, String mtime, String extension, JsonGenerator catalog) throws IOException {
        
        catalog.writeStartObject();
        catalog.writeStringField("@id", "file-"+objectId);
        catalog.writeStringField("@type", "Trace");
        
        catalog.writeFieldName("propertyBundle");
        catalog.writeStartArray();
        
        catalog.writeStartObject();
        catalog.writeStringField("@type", "File");
        catalog.writeStringField("createdTime", ctime);
        catalog.writeStringField("accessedTime", atime);
        catalog.writeStringField("modifiedTime", mtime);
        catalog.writeStringField("extension", extension);
        catalog.writeStringField("fileName", fileName);
        catalog.writeStringField("filePath", parent_path);
        catalog.writeStringField("sizeInBytes", Long.toString(size));        
        catalog.writeEndObject();
        
        catalog.writeStartObject();
        catalog.writeStringField("@type", "ContentData");
        catalog.writeStringField("sizeInBytes", Long.toString(size));
        catalog.writeStringField("mimeType", mime_type);
        catalog.writeFieldName("hash");
        catalog.writeStartArray();
        catalog.writeStartObject();
        catalog.writeStringField("@type", "Hash");
        catalog.writeStringField("hashMethod", "SHA256");
        catalog.writeStringField("hashValue", md5Hash);
        catalog.writeEndObject();
        catalog.writeEndArray();

        catalog.writeEndObject();
        
        catalog.writeEndArray();
        catalog.writeEndObject();
    }

    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "ReportCaseUco.getName.text");
        return name;
    }

    @Override
    public String getRelativeFilePath() {
        return REPORT_FILE_NAME;
    }

    @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "ReportCaseUco.getDesc.text");
        return desc;
    }

    @Override
    public JPanel getConfigurationPanel() {
        configPanel = new ReportCaseUcoConfigPanel();
        return configPanel;
    }
}
