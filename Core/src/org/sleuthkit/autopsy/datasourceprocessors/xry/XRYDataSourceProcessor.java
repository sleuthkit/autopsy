/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * An XRY Report data source processor.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = DataSourceProcessor.class)}
)
public class XRYDataSourceProcessor implements DataSourceProcessor {

    private final XRYDataSourceProcessorConfigPanel configPanel;
    
    //Background processor to relieve the EDT from adding files to the case
    //database and parsing the report files.
    private XRYReportProcessorSwingWorker swingWorker;

    private static final Logger logger = Logger.getLogger(XRYDataSourceProcessor.class.getName());

    public XRYDataSourceProcessor() {
        configPanel = XRYDataSourceProcessorConfigPanel.getInstance();
    }

    @Override
    @NbBundle.Messages({
        "XRYDataSourceProcessor.dataSourceType=Import Tool Report"
    })
    public String getDataSourceType() {
        return Bundle.XRYDataSourceProcessor_dataSourceType();
    }

    @Override
    public JPanel getPanel() {
        return configPanel;
    }

    @Override
    public boolean isPanelValid() {
        return true;
    }

    /**
     * Processes the XRY folder the examiner selected. The heavy lifting 
     * is handed off to a dedicated thread. This function will
     * test the minimum requirements needed to successfully process the input.
     */
    @Override
    @NbBundle.Messages({
        "XRYDataSourceProcessor.testingFolder=Testing input folder...",
        "XRYDataSourceProcessor.notReadable=Could not read from the selected folder",
        "XRYDataSourceProcessor.notXRYFolder=Selected folder did not contain any XRY files",
        "XRYDataSourceProcessor.ioError=I/O error occured trying to test the XRY report folder"
    })
    public void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        progressMonitor.setIndeterminate(true);
        progressMonitor.setProgressText(Bundle.XRYDataSourceProcessor_testingFolder());
        
        String selectedFilePath = configPanel.getSelectedFilePath();
        File selectedFile = new File(selectedFilePath);
        Path selectedPath = selectedFile.toPath();

        //Test permissions
        if (!Files.isReadable(selectedPath)) {
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, 
                    Lists.newArrayList(Bundle.XRYDataSourceProcessor_notReadable()), 
                    Lists.newArrayList());
            return;
        }

        try {
            //Validate the folder.
            if (!XRYFolder.isXRYFolder(selectedPath)) {
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, 
                        Lists.newArrayList(Bundle.XRYDataSourceProcessor_notXRYFolder()), 
                        Lists.newArrayList());
                return;
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "[XRY DSP] I/O exception encountered trying to test the XRY folder.", ex);
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, 
                    Lists.newArrayList(Bundle.XRYDataSourceProcessor_ioError(), ex.toString()), Lists.newArrayList());
            return;
        }

        try {
            XRYFolder xryFolder = new XRYFolder(selectedPath);
            FileManager fileManager = Case.getCurrentCaseThrows()
                    .getServices().getFileManager();
            
            //Move heavy lifting to a dedicated thread.
            swingWorker = new XRYReportProcessorSwingWorker(xryFolder, progressMonitor,
                    callback, fileManager);
            swingWorker.execute();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "[XRY DSP] No case is currently open.", ex);
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
     * Relieves the EDT from add images to the case database and processing the
     * XRY report files.
     */
    private class XRYReportProcessorSwingWorker extends SwingWorker<LocalFilesDataSource, Void> {

        private final DataSourceProcessorProgressMonitor progressMonitor;
        private final DataSourceProcessorCallback callback;
        private final FileManager fileManager;
        private final XRYFolder xryFolder;

        public XRYReportProcessorSwingWorker(XRYFolder folder, DataSourceProcessorProgressMonitor progressMonitor,
                DataSourceProcessorCallback callback, FileManager fileManager) {
            this.xryFolder = folder;
            this.progressMonitor = progressMonitor;
            this.callback = callback;
            this.fileManager = fileManager;
        }

        @Override
        @NbBundle.Messages({
            "XRYDataSourceProcessor.preppingFiles=Preparing to add files to the case database",
            "XRYDataSourceProcessor.processingFiles=Processing all XRY files..."
        })
        protected LocalFilesDataSource doInBackground() throws TskCoreException, 
                TskDataException, IOException {
            progressMonitor.setProgressText(Bundle.XRYDataSourceProcessor_preppingFiles());
            
            List<Path> nonXRYFiles = xryFolder.getNonXRYFiles();
            List<String> filePaths = nonXRYFiles.stream()
                    //Map paths to string representations.
                    .map(Path::toString)
                    .collect(Collectors.toList());
            String uniqueUUID = UUID.randomUUID().toString();
            LocalFilesDataSource dataSource = fileManager.addLocalFilesDataSource(
                    uniqueUUID, 
                    "XRY Report", //Name
                    "", //Timezone
                    filePaths, 
                    new ProgressMonitorAdapter(progressMonitor));

            //Process the report files.
            progressMonitor.setProgressText(Bundle.XRYDataSourceProcessor_processingFiles());
            XRYReportProcessor.process(xryFolder, dataSource);
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
                //DSP was cancelled. Not an error.
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
