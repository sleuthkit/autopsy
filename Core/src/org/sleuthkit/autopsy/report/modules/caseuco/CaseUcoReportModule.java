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
import java.util.Queue;
import java.util.Set;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * CaseUcoReport generates a report in the CASE-UCO format. It saves basic file
 * info like full caseDirPath, name, MIME type, times, and hash.
 */
public final class CaseUcoReportModule implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(CaseUcoReportModule.class.getName());
    private static CaseUcoReportModule instance = null;
    
    private static final Set<Short> SUPPORTED_TYPES = new HashSet<Short>() {{
        add(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_UNDEF.getValue());
        add(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue());
        add(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT.getValue());
    }};

    private static final String REPORT_FILE_NAME = "CASE_UCO_output";

    // Hidden constructor for the report
    private CaseUcoReportModule() {
    }

    // Get the default implementation of this report
    public static synchronized CaseUcoReportModule getDefault() {
        if (instance == null) {
            instance = new CaseUcoReportModule();
        }
        return instance;
    }

    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "CaseUcoReportModule.getName.text");
        return name;
    }
    
    @Override
    public JPanel getConfigurationPanel() {
        return null; // No configuration panel
    }

    @Override
    public String getRelativeFilePath() {
        return REPORT_FILE_NAME;
    }

    @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "CaseUcoReportModule.getDesc.text");
        return desc;
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
     * Generates a CASE-UCO format report.
     *
     * @param baseReportDir caseDirPath to save the report
     * @param progressPanel panel to update the report's progress
     */
    @NbBundle.Messages({
        "CaseUcoReportModule.notInitialized=CASE-UCO settings panel has not been initialized",
        "CaseUcoReportModule.noDataSourceSelected=No data source selected for CASE-UCO report"
    })
    @Override
    @SuppressWarnings("deprecation")
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        try {
            Path reportDirectory = Paths.get(baseReportDir);
            Files.createDirectories(reportDirectory);
            progressPanel.setIndeterminate(false);
            progressPanel.start();
            CaseUcoReportGenerator caseUco = new CaseUcoReportGenerator(reportDirectory, REPORT_FILE_NAME);
            Case caseObj = Case.getCurrentCaseThrows();
            caseUco.addCase(caseObj);
            for(DataSource dataSource : caseObj.getSleuthkitCase().getDataSources()) {
                progressPanel.updateStatusLabel("Processing datasoure: " + dataSource.getName());
                caseUco.addDataSource(dataSource, caseObj);
                
                Queue<Content> contentQueue = new LinkedList<>();
                //Add the dataSource root contents
                contentQueue.addAll(dataSource.getChildren());
                //Breadth First Search the DataSource tree.
                while(!contentQueue.isEmpty()) {
                    Content current = contentQueue.poll();
                    if(current instanceof AbstractFile) {
                        AbstractFile f = (AbstractFile) (current);
                        if(SUPPORTED_TYPES.contains(f.getMetaType().getValue())) {
                            caseUco.addFile(f, dataSource);   
                        }
                    }
                    
                    if(current.hasChildren()) {
                        contentQueue.addAll(current.getChildren());
                    }
                }
            }
            
            //Report is now done.
            caseUco.generateReport();
            //progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
        } catch (IOException ex) {
            //Log
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, "IO");
        } catch (NoCurrentCaseException ex) {
            //Log
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, "NoCase");
        } catch (TskCoreException ex) {
            //Log
            progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR, "TskCore");
        }
        
        CaseUcoFormatExporter.generateReport(baseReportDir + "Case-previous-output.json-ld", progressPanel);
        progressPanel.complete(ReportProgressPanel.ReportStatus.COMPLETE);
    }
}