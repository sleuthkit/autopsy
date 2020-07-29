/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2018-2020 Basis Technology Corp.
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.GeneralReportSettings;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.caseuco.CaseUcoExporter;
import org.sleuthkit.caseuco.ContentNotExportableException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.blackboardutils.attributes.BlackboardJsonAttrUtil;

/**
 * Exports an Autopsy case to a CASE-UCO report file. This module will write all
 * files and artifacts from the selected data sources.
 */
public final class CaseUcoReportModule implements GeneralReportModule {

    private static final Logger logger = Logger.getLogger(CaseUcoReportModule.class.getName());
    private static final CaseUcoReportModule SINGLE_INSTANCE = new CaseUcoReportModule();

    //Supported types of TSK_FS_FILES
    private static final Set<Short> SUPPORTED_TYPES = new HashSet<Short>() {
        {
            add(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_UNDEF.getValue());
            add(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue());
            add(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_VIRT.getValue());
        }
    };

    private static final String REPORT_FILE_NAME = "CASE_UCO_output";
    private static final String EXTENSION = "jsonld";

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
        return REPORT_FILE_NAME + "." + EXTENSION;
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

    @Override
    public boolean supportsDataSourceSelection() {
        return true;
    }

    /**
     * Generates a CASE-UCO format report for all files in the Case.
     *
     * @param settings Report settings.
     * @param progressPanel panel to update the report's progress
     */
    @NbBundle.Messages({
        "CaseUcoReportModule.notInitialized=CASE-UCO settings panel has not been initialized",
        "CaseUcoReportModule.noDataSourceSelected=No data source selected for CASE-UCO report",
        "CaseUcoReportModule.ioError=I/O error encountered while generating report",
        "CaseUcoReportModule.noCaseOpen=No case is currently open",
        "CaseUcoReportModule.tskCoreException=TskCoreException [%s] encountered while generating the report. Please reference the log for more details.",
        "CaseUcoReportModule.processingDataSource=Processing datasource: %s",
        "CaseUcoReportModule.ingestWarning=Warning, this report will be created before ingest services completed",
        "CaseUcoReportModule.unableToCreateDirectories=Unable to create directory for CASE-UCO report",
        "CaseUcoReportModule.srcModuleName=CASE-UCO Report"
    })
    @Override
    public void generateReport(GeneralReportSettings settings, ReportProgressPanel progressPanel) {
        try {
            // Check if ingest has finished
            warnIngest(progressPanel);

            //Create report paths if they don't already exist.
            Path reportDirectory = Paths.get(settings.getReportDirectoryPath());
            try {
                Files.createDirectories(reportDirectory);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to create directory for CASE-UCO report.", ex);
                progressPanel.complete(ReportProgressPanel.ReportStatus.ERROR,
                        Bundle.CaseUcoReportModule_unableToCreateDirectories());
                return;
            }

            Case currentCase = Case.getCurrentCaseThrows();

            Path caseJsonReportFile = reportDirectory.resolve(REPORT_FILE_NAME + "." + EXTENSION);

            try (OutputStream stream = new FileOutputStream(caseJsonReportFile.toFile());
                    JsonWriter reportWriter = new JsonWriter(new OutputStreamWriter(stream, "UTF-8"))) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                reportWriter.setIndent("    ");
                reportWriter.beginObject();
                reportWriter.name("@graph");
                reportWriter.beginArray();

                CaseUcoExporter exporter = new CaseUcoExporter(currentCase.getSleuthkitCase());
                for (JsonElement element : exporter.exportSleuthkitCase()) {
                    gson.toJson(element, reportWriter);
                }

                // Get a list of selected data sources to process.
                List<DataSource> dataSources = getSelectedDataSources(currentCase, settings);

                progressPanel.setIndeterminate(false);
                progressPanel.setMaximumProgress(dataSources.size());
                progressPanel.start();

                // First stage of reporting is for files and data sources.
                // Iterate through each data source and dump all files contained
                // in that data source.
                for (int i = 0; i < dataSources.size(); i++) {
                    DataSource dataSource = dataSources.get(i);
                    progressPanel.updateStatusLabel(String.format(
                            Bundle.CaseUcoReportModule_processingDataSource(),
                            dataSource.getName()));
                    // Add the data source export.
                    for (JsonElement element : exporter.exportDataSource(dataSource)) {
                        gson.toJson(element, reportWriter);
                    }
                    // Search all children of the data source.
                    performDepthFirstSearch(dataSource, gson, exporter, reportWriter);
                    progressPanel.setProgress(i + 1);
                }

                // Second stage of reporting handles artifacts.
                Set<Long> dataSourceIds = dataSources.stream()
                        .map((datasource) -> datasource.getId())
                        .collect(Collectors.toSet());
                
                logger.log(Level.INFO, "Writing all artifacts to the CASE-UCO report. "
                        + "Keyword hits will be skipped as they can't be represented"
                        + " in CASE format.");

                // Write all standard artifacts that are contained within the 
                // selected data sources.
                for (ARTIFACT_TYPE artType : currentCase.getSleuthkitCase().getBlackboardArtifactTypesInUse()) {
                    if(artType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT)) {
                        // Keyword hits cannot be represented in CASE.
                        continue;
                    }
                    
                    for (BlackboardArtifact artifact : currentCase.getSleuthkitCase().getBlackboardArtifacts(artType)) {
                        if (dataSourceIds.contains(artifact.getDataSource().getId())) {
                            try {
                                for (JsonElement element : exporter.exportBlackboardArtifact(artifact)) {
                                    gson.toJson(element, reportWriter);
                                }
                            } catch (ContentNotExportableException ex) {
                                logger.log(Level.INFO, String.format("Unable to export blackboard artifact (id: %d, type: %d) to CASE/UCO. "
                                        + "The artifact type is either not supported or the artifact instance does not have any "
                                        + "exportable attributes.", artifact.getId(), artType.getTypeID()));
                            } catch (BlackboardJsonAttrUtil.InvalidJsonException ex) {
                                logger.log(Level.WARNING, String.format("Artifact instance (id: %d, type: %d) contained a "
                                        + "malformed json attribute.", artifact.getId(), artType.getTypeID()), ex);
                            }
                        }
                    }
                }

                reportWriter.endArray();
                reportWriter.endObject();
            }

            currentCase.addReport(caseJsonReportFile.toString(),
                    Bundle.CaseUcoReportModule_srcModuleName(),
                    REPORT_FILE_NAME);
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

    /**
     * Get the selected data sources from the settings instance.
     */
    private List<DataSource> getSelectedDataSources(Case currentCase, GeneralReportSettings settings) throws TskCoreException {
        return currentCase.getSleuthkitCase().getDataSources().stream()
                .filter((dataSource) -> {
                    if (settings.getSelectedDataSources() == null) {
                        // Assume all data sources if list is null.
                        return true;
                    }
                    return settings.getSelectedDataSources().contains(dataSource.getId());
                })
                .collect(Collectors.toList());
    }

    /**
     * Warn the user if ingest is still ongoing.
     */
    private void warnIngest(ReportProgressPanel progressPanel) {
        if (IngestManager.getInstance().isIngestRunning()) {
            progressPanel.updateStatusLabel(Bundle.CaseUcoReportModule_ingestWarning());
        }
    }

    /**
     * Perform DFS on the data sources tree, which will search it in entirety.
     */
    private void performDepthFirstSearch(DataSource dataSource,
            Gson gson, CaseUcoExporter exporter, JsonWriter reportWriter) throws IOException, TskCoreException {

        Deque<Content> stack = new ArrayDeque<>();
        stack.addAll(dataSource.getChildren());

        //Depth First Search the data source tree.
        while (!stack.isEmpty()) {
            Content current = stack.pop();
            if (current instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) (current);
                if (SUPPORTED_TYPES.contains(file.getMetaType().getValue())) {

                    for (JsonElement element : exporter.exportAbstractFile(file)) {
                        gson.toJson(element, reportWriter);
                    }
                }
            }

            for (Content child : current.getChildren()) {
                stack.push(child);
            }
        }
    }
}
