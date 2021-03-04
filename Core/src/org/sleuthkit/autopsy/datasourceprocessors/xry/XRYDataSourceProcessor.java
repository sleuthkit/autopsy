/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor.AutoIngestDataSourceProcessorException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * An XRY Report data source processor.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = DataSourceProcessor.class),
    @ServiceProvider(service = AutoIngestDataSourceProcessor.class)
})
public class XRYDataSourceProcessor implements DataSourceProcessor, AutoIngestDataSourceProcessor {

    private final XRYDataSourceProcessorConfigPanel configPanel;

    private static final int XRY_FILES_DEPTH = 1;

    //Background processor to relieve the EDT from adding files to the case
    //database and parsing the report files.
    private XRYReportProcessorSwingWorker swingWorker;

    private static final Logger logger = Logger.getLogger(XRYDataSourceProcessor.class.getName());

    public XRYDataSourceProcessor() {
        configPanel = XRYDataSourceProcessorConfigPanel.getInstance();
    }

    @Override
    @NbBundle.Messages({
        "XRYDataSourceProcessor.dataSourceType=XRY Text Export"
    })
    public String getDataSourceType() {
        return Bundle.XRYDataSourceProcessor_dataSourceType();
    }

    @Override
    public JPanel getPanel() {
        return configPanel;
    }

    /**
     * Tests the selected path.
     *
     * This functions checks permissions to the path directly and then to each
     * of its top most children, if it is a folder.
     */
    @Override
    @NbBundle.Messages({
        "XRYDataSourceProcessor.noPathSelected=Please select a folder containing exported XRY text files",
        "XRYDataSourceProcessor.notReadable=Selected path is not readable",
        "XRYDataSourceProcessor.notXRYFolder=Selected folder did not contain any XRY text files",
        "XRYDataSourceProcessor.ioError=I/O error occured trying to test the selected folder",
        "XRYDataSourceProcessor.childNotReadable=Top level path [ %s ] is not readable",
        "XRYDataSourceProcessor.notAFolder=The selected path is not a folder"
    })
    public boolean isPanelValid() {
        configPanel.clearErrorText();
        String selectedFilePath = configPanel.getSelectedFilePath();
        if (selectedFilePath.isEmpty()) {
            configPanel.setErrorText(Bundle.XRYDataSourceProcessor_noPathSelected());
            return false;
        }

        File selectedFile = new File(selectedFilePath);
        Path selectedPath = selectedFile.toPath();

        //Test permissions
        if (!Files.isReadable(selectedPath)) {
            configPanel.setErrorText(Bundle.XRYDataSourceProcessor_notReadable());
            return false;
        }

        try {
            BasicFileAttributes attr = Files.readAttributes(selectedPath,
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            if (!attr.isDirectory()) {
                configPanel.setErrorText(Bundle.XRYDataSourceProcessor_notAFolder());
                return false;
            }

            //Ensure all of the XRY_FILES_DEPTH paths are readable.
            try (Stream<Path> allFiles = Files.walk(selectedPath, XRY_FILES_DEPTH)) {
                Iterator<Path> allFilesIterator = allFiles.iterator();
                while (allFilesIterator.hasNext()) {
                    Path currentPath = allFilesIterator.next();
                    if (!Files.isReadable(currentPath)) {
                        Path fileName = currentPath.subpath(currentPath.getNameCount() - 2, 
                                currentPath.getNameCount());
                        configPanel.setErrorText(String.format(
                                Bundle.XRYDataSourceProcessor_childNotReadable(),
                                fileName.toString()));
                        return false;
                    }
                }
            }

            //Validate the folder.
            if (!XRYFolder.isXRYFolder(selectedPath)) {
                configPanel.setErrorText(Bundle.XRYDataSourceProcessor_notXRYFolder());
                return false;
            }
        } catch (IOException | UncheckedIOException ex) {
            configPanel.setErrorText(Bundle.XRYDataSourceProcessor_ioError());
            logger.log(Level.WARNING, "[XRY DSP] I/O exception encountered trying to test the XRY folder.", ex);
            return false;
        }

        return true;
    }

    /**
     * Tests if the given path is an XRY Folder.
     *
     * This function assumes the calling thread has sufficient privileges to
     * read the folder and its child content.
     *
     * @param dataSourcePath Path to test
     * @return 100 if the folder passes the XRY Folder check, 0 otherwise.
     * @throws AutoIngestDataSourceProcessorException if an I/O error occurs
     * during disk reads.
     */
    @Override
    public int canProcess(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {
        try {
            if (XRYFolder.isXRYFolder(dataSourcePath)) {
                return 100;
            }
        } catch (IOException ex) {
            throw new AutoIngestDataSourceProcessorException("[XRY DSP] encountered I/O error " + ex.getMessage(), ex);
        }
        return 0;
    }

    /**
     * Processes the XRY folder that the examiner selected. The heavy lifting is
     * done off of the EDT, so this function will return while the 
     * path is still being processed.
     * 
     * This function assumes the calling thread has sufficient privileges to
     * read the folder and its child content, which should have been validated 
     * in isPanelValid().
     */
    @Override
    public void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        run(null, progressMonitor, callback);
    }    
    
    /**
     * Processes the XRY folder that the examiner selected. The heavy lifting is
     * done off of the EDT, so this function will return while the 
     * path is still being processed.
     * 
     * This function assumes the calling thread has sufficient privileges to
     * read the folder and its child content, which should have been validated 
     * in isPanelValid().
     * 
     * @param host            Host for the data source.
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callback        Callback that will be used by the background task
     *                        to return results.
     */
    @Override
    @NbBundle.Messages({
        "XRYDataSourceProcessor.noCurrentCase=No case is open."
    })
    public void run(Host host, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        progressMonitor.setIndeterminate(true);      
        String selectedFilePath = configPanel.getSelectedFilePath();
        File selectedFile = new File(selectedFilePath);
        Path selectedPath = selectedFile.toPath();

        try {
            XRYFolder xryFolder = new XRYFolder(selectedPath);
            Case currentCase = Case.getCurrentCaseThrows();
            String uniqueUUID = UUID.randomUUID().toString();
            //Move heavy lifting to a background task.
            swingWorker = new XRYReportProcessorSwingWorker(xryFolder, progressMonitor,
                    callback, currentCase, uniqueUUID, host);
            swingWorker.execute();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "[XRY DSP] No case is currently open.", ex);
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS,
                    Lists.newArrayList(Bundle.XRYDataSourceProcessor_noCurrentCase(),
                            ex.getMessage()), Lists.newArrayList());
        }
    }

    @Override
    public void process(String deviceId, Path dataSourcePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) {
        process(deviceId, dataSourcePath, null, progressMonitor, callBack);
    }
    
    /**
     * Processes the XRY Folder encountered in an auto-ingest context. The heavy
     * lifting is done off of the EDT, so this function will return while the 
     * path is still being processed.
     * 
     * This function assumes the calling thread has sufficient privileges to
     * read the folder and its child content.
     * 
     * @param deviceId
     * @param dataSourcePath
     * @param host
     * @param progressMonitor
     * @param callBack
     */
    @Override
    public void process(String deviceId, Path dataSourcePath, Host host, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) {
        progressMonitor.setIndeterminate(true);

        try {
            XRYFolder xryFolder = new XRYFolder(dataSourcePath);
            Case currentCase = Case.getCurrentCaseThrows();
            //Move heavy lifting to a background task.
            swingWorker = new XRYReportProcessorSwingWorker(xryFolder, progressMonitor,
                    callBack, currentCase, deviceId, host);
            swingWorker.execute();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "[XRY DSP] No case is currently open.", ex);
            callBack.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS,
                    Lists.newArrayList(Bundle.XRYDataSourceProcessor_noCurrentCase(),
                            ex.getMessage()), Lists.newArrayList());
        }
    }

    @Override
    public void cancel() {
        if (swingWorker != null) {
            swingWorker.cancel(true);
        }
    }

    @Override
    public void reset() {
        //Clear the current selected file path.
        configPanel.clearSelectedFilePath();
    }

    /**
     * Relieves the EDT from having to process the XRY report and write to the
     * case database.
     */
    private class XRYReportProcessorSwingWorker extends SwingWorker<LocalFilesDataSource, Void> {

        private final DataSourceProcessorProgressMonitor progressMonitor;
        private final DataSourceProcessorCallback callback;
        private final Case currentCase;
        private final XRYFolder xryFolder;
        private final String uniqueUUID;
        private final Host host;

        public XRYReportProcessorSwingWorker(XRYFolder folder,
                DataSourceProcessorProgressMonitor progressMonitor,
                DataSourceProcessorCallback callback,
                Case currentCase, String uniqueUUID, Host host) {

            this.xryFolder = folder;
            this.progressMonitor = progressMonitor;
            this.callback = callback;
            this.currentCase = currentCase;
            this.uniqueUUID = uniqueUUID;
            this.host = host;
        }

        @Override
        @NbBundle.Messages({
            "XRYDataSourceProcessor.preppingFiles=Preparing to add files to the case database",
            "XRYDataSourceProcessor.processingFiles=Processing all XRY files..."
        })
        protected LocalFilesDataSource doInBackground() throws TskCoreException,
                TskDataException, IOException, BlackboardException {
            progressMonitor.setProgressText(Bundle.XRYDataSourceProcessor_preppingFiles());

            List<Path> nonXRYFiles = xryFolder.getNonXRYFiles();
            List<String> filePaths = nonXRYFiles.stream()
                    //Map paths to string representations.
                    .map(Path::toString)
                    .collect(Collectors.toList());
            LocalFilesDataSource dataSource = currentCase.getServices().getFileManager().addLocalFilesDataSource(
                    uniqueUUID,
                    "XRY Text Export", //Name
                    "", //Timezone
                    host,
                    filePaths,
                    new ProgressMonitorAdapter(progressMonitor));

            //Process the report files.
            progressMonitor.setProgressText(Bundle.XRYDataSourceProcessor_processingFiles());
            XRYReportProcessor.process(xryFolder, dataSource, currentCase.getSleuthkitCase());
            return dataSource;
        }

        @Override
        @NbBundle.Messages({
            "XRYDataSourceProcessor.unexpectedError=Internal error occurred while processing XRY report"
        })
        public void done() {
            try {
                LocalFilesDataSource newDataSource = get();
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS,
                        Lists.newArrayList(), Lists.newArrayList(newDataSource));
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, "[XRY DSP] Thread was interrupted while processing the XRY report."
                        + " The case may or may not have the complete XRY report.", ex);
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS, 
                        Lists.newArrayList(), Lists.newArrayList());
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "[XRY DSP] Unexpected internal error while processing XRY report.", ex);
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS,
                        Lists.newArrayList(Bundle.XRYDataSourceProcessor_unexpectedError(),
                                ex.toString()), Lists.newArrayList());
            }
        }

        /**
         * Makes the DSP progress monitor compatible with the File Manager
         * progress updater.
         */
        private class ProgressMonitorAdapter implements FileManager.FileAddProgressUpdater {

            private final DataSourceProcessorProgressMonitor progressMonitor;

            ProgressMonitorAdapter(DataSourceProcessorProgressMonitor progressMonitor) {
                this.progressMonitor = progressMonitor;
            }

            @Override
            @NbBundle.Messages({
                "XRYDataSourceProcessor.fileAdded=Added %s to the case database"
            })
            public void fileAdded(AbstractFile newFile) {
                progressMonitor.setProgressText(String.format(Bundle.XRYDataSourceProcessor_fileAdded(), newFile.getName()));
            }
        }
    }
}