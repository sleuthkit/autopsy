/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.caseuco;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * CaseUcoReportModule generates a report in CASE-UCO format. This module will 
 * write all files and data sources to the report.
 */
public final class CaseUcoReportModule implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(CaseUcoReportModule.class.getName());
    private static final CaseUcoReportModule SINGLE_INSTANCE = new CaseUcoReportModule();
    
    //Supported types of TSK_FS_FILES
    private static final Set<Short> SUPPORTED_TYPES = new HashSet<Short>() {{
        add(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_UNDEF.getValue());
        add(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue());
        add(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT.getValue());
    }};

    private static final String REPORT_FILE_NAME = "CASE_UCO_output";    
    private static final String EXTENSION = "json-ld";

    // Hidden constructor for the report
    private CaseUcoReportModule() {
    }

    // Get the default implementation of this report
    public static synchronized CaseUcoReportModule getDefault() {
        return SINGLE_INSTANCE;
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "CaseUcoReportModule.getName.text");
    }
    
    @Override
    public JPanel getConfigurationPanel() {
        return null; // No configuration panel
    }

    @Override
    public String getRelativeFilePath() {
        return REPORT_FILE_NAME  + "." + EXTENSION;
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "CaseUcoReportModule.getDesc.text");
    }

    /**
     * Returns CASE-UCO report file name
     *
     * @return the REPORT_FILE_NAME
     */
    public static String getReportFileName() {
        return REPORT_FILE_NAME;
    }

    /**
     * Generates a CASE-UCO format report for all files in the Case.
     *
     * @param baseReportDir caseDirPath to save the report
     * @param progressPanel panel to update the report's progress
     */
    @NbBundle.Messages({
        "CaseUcoReportModule.notInitialized=CASE-UCO settings panel has not been initialized",
        "CaseUcoReportModule.noDataSourceSelected=No data source selected for CASE-UCO report",
        "CaseUcoReportModule.ioError=I/O error encountered while generating report",
        "CaseUcoReportModule.noCaseOpen=No case is currently open",
        "CaseUcoReportModule.tskCoreException=TskCoreException [%s] encountered while generating the report. Please reference the log for more details.",
        "CaseUcoReportModule.processingDataSource=Processing datasource: ",
        "CaseUcoReportModule.ingestWarning=Warning, this report will be created before ingest services completed",
        "CaseUcoReportModule.unableToCreateDirectories=Unable to create directory for CASE-UCO report",
    })
    @Override
    @SuppressWarnings("deprecation")
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        try {
            // Check if ingest has finished
            if (IngestManager.getInstance().isIngestRunning()) {
                progressPanel.updateStatusLabel(Bundle.CaseUcoReportModule_ingestWarning());
            }
            
            //Create report paths if they don't already exist.
            Path reportDirectory = Paths.get(baseReportDir);
            try {
                Files.createDirectories(reportDirectory);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to create directory for CASE-UCO report.", ex);
                progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, 
                    Bundle.CaseUcoReportModule_unableToCreateDirectories());
                return;
            }
            
            CaseUcoReportGenerator caseUco = new CaseUcoReportGenerator(reportDirectory, REPORT_FILE_NAME);
            
            //First write the Case to the report file.
            Case caseObj = Case.getCurrentCaseThrows();
            caseUco.addCase(caseObj);
            
            List<Content> dataSources = caseObj.getDataSources();
            progressPanel.setIndeterminate(false);
            progressPanel.setMaximumProgress(dataSources.size());
            progressPanel.start();
            
            //Then search each data source for file content.
            for(int i = 0; i < dataSources.size(); i++) {
                Content dataSource = dataSources.get(i);
                progressPanel.updateStatusLabel(Bundle.CaseUcoReportModule_processingDataSource() + dataSource.getName());
                caseUco.addDataSource(dataSource, caseObj);
                
                Queue<Content> dataSourceChildrenQueue = new LinkedList<>();
                dataSourceChildrenQueue.addAll(dataSource.getChildren());
                
                //Breadth First Search the data source tree.
                while(!dataSourceChildrenQueue.isEmpty()) {
                    Content currentChild = dataSourceChildrenQueue.poll();
                    if(currentChild instanceof AbstractFile) {
                        AbstractFile f = (AbstractFile) (currentChild);
                        if(SUPPORTED_TYPES.contains(f.getMetaType().getValue())) {
                            caseUco.addFile(f, dataSource);   
                        }
                    }
                    
                    if(currentChild.hasChildren()) {
                        dataSourceChildrenQueue.addAll(currentChild.getChildren());
                    }
                }
                
                progressPanel.setProgress(i+1);
            }
            
            //Complete the report.
            caseUco.generateReport();
            progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "I/O error encountered while generating the report.", ex);
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, 
                    Bundle.CaseUcoReportModule_ioError());
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "No case open.", ex);
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, 
                    Bundle.CaseUcoReportModule_noCaseOpen());
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "TskCoreException encounted while generating the report.", ex);
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, 
                    String.format(Bundle.CaseUcoReportModule_tskCoreException(), ex.toString()));
        }
        
        progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
    }
}