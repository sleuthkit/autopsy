/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.cellex.datasourceprocessors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.autopsy.experimental.cellex.cellxml.CellXMLParser;

/*
 * A runnable that adds a Cellebrite XML report a case as a local files data
 * source. The Cellebrite XML report is converted to a DFXML/CellXML report and
 * the CellXML report is parsed to generate artifacts.
 */
class AddCellebriteXMLTask implements Runnable {

    public enum CellebriteInputType {

        handset,
        SIM,
    };

    private static final Logger logger = Logger.getLogger(CellebriteLogicalReportProcessor.class.getName());
    private static final String MODULE_NAME = "Cellebrite XML Processor";
    private static final String CONVERTOR_EXE = "CellebriteToDFXMLConv.exe";
    private final String deviceId;
    private final String rootVirtualDirectoryName;
    private final String cellebriteXmlFilePath;
    private final CellebriteInputType cellebriteXmlFileType;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callback;
    private volatile boolean cancelled = false;

    /**
     * Constructs a runnable that adds a Cellebrite XML report a case as a local
     * files data source. The Cellebrite XML report is converted to a
     * DFXML/CellXML report and the CellXML report is parsed to generate
     * artifacts.
     *
     * @param deviceId                 An ASCII-printable identifier for the
     *                                 device associated with the data source
     *                                 that is intended to be unique across
     *                                 multiple cases (e.g., a UUID).
     * @param rootVirtualDirectoryName The name to give to the virtual directory
     *                                 that will represent the data source. Pass
     *                                 the empty string to get a default name of
     *                                 the form: LogicalFileSet[N]
     * @param cellebriteXmlFilePath    Path to a Cellebrite report XML file.
     * @param cellebriteXmlFileType    Handset or SIM.
     * @param progressMonitor          Progress monitor for reporting
     *                                 progressMonitor during processing.
     * @param callback                 Callback to call when processing is done.
     */
    AddCellebriteXMLTask(String deviceId, String rootVirtualDirectoryName, String cellebriteXmlFilePath, CellebriteInputType cellebriteXmlFileType, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        this.deviceId = deviceId;
        this.rootVirtualDirectoryName = rootVirtualDirectoryName;
        this.cellebriteXmlFilePath = cellebriteXmlFilePath;
        this.cellebriteXmlFileType = cellebriteXmlFileType;
        this.callback = callback;
        this.progressMonitor = progressMonitor;
    }

    /**
     * Adds a Cellebrite XML report a case as a local files data source. The
     * Cellebrite XML report is converted to a DFXML/CellXML report and the
     * CellXML report is parsed to generate artifacts.
     */
    @Override
    public void run() {
        List<Content> newDataSources = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        try {
            progressMonitor.setIndeterminate(true);
            progressMonitor.setProgressText("Processing: " + cellebriteXmlFilePath);

            /*
             * Locate the Cellebrite XML to DFXML/CellXML converter.
             */
            final File converterHome = InstalledFileLocator.getDefault().locate(FilenameUtils.removeExtension(CONVERTOR_EXE), AddCellebriteXMLTask.class.getPackage().getName(), false);
            if (null == converterHome) {
                errorMessages.add(String.format("Critical error adding %s for device %s: %s not found", cellebriteXmlFilePath, deviceId, CONVERTOR_EXE));
                return;
            }
            String converterExePath = Paths.get(converterHome.getAbsolutePath(), CONVERTOR_EXE).toString();

            /*
             * Get the file name of the image file sans extension and use it to
             * create an converter output folder in the module output directory
             * of the case, adding a time stamp suffix for uniqueness.
             *
             * NOTE: The input file name may not have a .xml extension.
             */
            String cellebriteXmlFileNameWithoutExt = FilenameUtils.removeExtension(Paths.get(cellebriteXmlFilePath).getFileName().toString());
            Path converterOutputDirPath = Paths.get(Case.getCurrentCase().getModuleDirectory(),
                    MODULE_NAME,
                    cellebriteXmlFileNameWithoutExt + "_" + new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss-SSSS").format(new Date()));
            try {
                Files.createDirectories(converterOutputDirPath);
            } catch (IOException ex) {
                errorMessages.add(String.format("Critical error adding %s for device %s, cannot create converter output directory %s : %s", cellebriteXmlFilePath, deviceId, converterOutputDirPath, ex.getLocalizedMessage()));
                return;
            }
            String cellXmlFilePath = Paths.get(converterOutputDirPath.toString(), cellebriteXmlFileNameWithoutExt + ".xml").toString();

            /*
             * Run the converter.
             */
            ProcessBuilder processBuilder = new ProcessBuilder(
                    converterExePath,
                    "-t",
                    cellebriteXmlFileType.toString(),
                    "-i",
                    cellebriteXmlFilePath,
                    "-o",
                    cellXmlFilePath);
            String logFileName = Paths.get(converterOutputDirPath.toString(), "c2c_stdout.txt").toString();
            File logFile = new File(logFileName);
            Path errFileName = Paths.get(converterOutputDirPath.toString(), "c2c_errors.txt");
            File errFile = new File(errFileName.toString());
            processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            try {
                int exitValue = ExecUtil.execute(processBuilder);
                if (exitValue != 0) {
                    errorMessages.add(String.format("Critical error adding %s for device %s: %s returned failure code", cellebriteXmlFilePath, deviceId, CONVERTOR_EXE));
                    return;
                }
            } catch (IOException | SecurityException ex) {
                errorMessages.add(String.format("Critical error adding %s for device %s, %s execution exception: %s", cellebriteXmlFilePath, deviceId, CONVERTOR_EXE, ex.getMessage()));
                return;
            }

            if (cancelled) {
                return;
            }

            /*
             * Add the Cellebrite XML file to the case as a local file data
             * source.
             */
            FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
            List<String> localFilePaths = new ArrayList<>();
            localFilePaths.add(cellebriteXmlFilePath);
            LocalFilesDataSource newDataSource = fileManager.addLocalFilesDataSource(deviceId, rootVirtualDirectoryName, "", localFilePaths, (final AbstractFile newFile) -> {
            });
            newDataSources.add(newDataSource.getRootDirectory());

            if (cancelled) {
                return;
            }

            /*
             * Generate artifacts from the DFXML/CellXML file.
             */
            java.io.File cellxmlFile = new java.io.File(cellXmlFilePath);
            if (cellxmlFile.exists()) {
                CellXMLParser.getDefault().Process(cellXmlFilePath, newDataSource.getRootDirectory(), MODULE_NAME);
            } else {
                errorMessages.add(String.format("Critical error adding %s for device %s: missing CellXML file", cellebriteXmlFilePath, deviceId));
            }
        } catch (TskDataException | TskCoreException ex) {
            errorMessages.add(String.format("Critical error adding %s for device %s: %s", cellebriteXmlFilePath, deviceId, ex.getLocalizedMessage()));
        } finally {
            /*
             * This appears to be the best that can be done to indicate
             * completion with the DataSourceProcessorProgressMonitor in its
             * current form.
             */
            progressMonitor.setProgress(0);
            progressMonitor.setProgress(100);

            /*
             * Pass the results back via the callback.
             */
            DataSourceProcessorCallback.DataSourceProcessorResult result;
            if (!errorMessages.isEmpty()) {
                result = DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
            } else {
                result = DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS;
            }
            callback.done(result, errorMessages, newDataSources);
        }
    }

    /**
     * Attempts to cancel the processing of the input image file. May result in
     * partial processing of the input.
     */
    public void cancelTask() {
        logger.log(Level.WARNING, "AddMPFImageTask cancelled, processing may be incomplete");
        cancelled = true;
    }

}
