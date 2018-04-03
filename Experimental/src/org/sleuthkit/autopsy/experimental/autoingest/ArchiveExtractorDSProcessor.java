/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;

/**
 * A data source processor that handles archive files. Implements the
 * DataSourceProcessor service provider interface to allow integration with the
 * add data source wizard. It also provides a run method overload to allow it to
 * be used independently of the wizard.
 */
@ServiceProviders(value={
    @ServiceProvider(service=AutoIngestDataSourceProcessor.class)}
)
@NbBundle.Messages({
    "ArchiveDSP.dsType.text=Archive file"})
public class ArchiveExtractorDSProcessor implements AutoIngestDataSourceProcessor {

    private final static String DATA_SOURCE_TYPE = Bundle.ArchiveDSP_dsType_text();
   
    private final ArchiveFilePanel configPanel;
    private String deviceId;
    private String archivePath;
    private boolean setDataSourceOptionsCalled;
    
    private final ExecutorService jobProcessingExecutor;
    private static final String ARCHIVE_DSP_THREAD_NAME = "Archive-DSP-%d";    
    private AddArchiveTask addArchiveTask;    
    
    /**
     * Constructs an archive data source processor that
     * implements the DataSourceProcessor service provider interface to allow
     * integration with the add data source wizard. It also provides a run
     * method overload to allow it to be used independently of the wizard.
     */
    public ArchiveExtractorDSProcessor() {
        configPanel = ArchiveFilePanel.createInstance(ArchiveExtractorDSProcessor.class.getName(), ArchiveUtil.getArchiveFilters());
        jobProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(ARCHIVE_DSP_THREAD_NAME).build());
    }
    
    @Override
    public int canProcess(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {
        // check whether this is an archive
        if (ArchiveUtil.isArchive(dataSourcePath)){
            // return "high confidence" value
            return 100;
        }
        return 0;
    }

    @Override
    public void process(String deviceId, Path dataSourcePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) {
        run(deviceId, dataSourcePath.toString(), progressMonitor, callBack);
    }

    @Override
    public String getDataSourceType() {
        return DATA_SOURCE_TYPE;
    }

    /**
     * Gets the panel that allows a user to select a data source and do any
     * configuration required by the data source. The panel is less than 544
     * pixels wide and less than 173 pixels high.
     *
     * @return A selection and configuration panel for this data source
     *         processor.
     */
    @Override
    public JPanel getPanel() {
        configPanel.readSettings();
        configPanel.select();
        return configPanel;
    }

    /**
     * Indicates whether the settings in the selection and configuration panel
     * are valid and complete.
     *
     * @return True if the settings are valid and complete and the processor is
     *         ready to have its run method called, false otherwise.
     */
    @Override
    public boolean isPanelValid() {
        return configPanel.validatePanel();
    }

    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the settings provided by the selection and
     * configuration panel. Returns as soon as the background task is started.
     * The background task uses a callback object to signal task completion and
     * return results.
     *
     * This method should not be called unless isPanelValid returns true.
     *
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callback        Callback that will be used by the background task
     *                        to return results.
     */
    @Override
    public void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        if (!setDataSourceOptionsCalled) {
            configPanel.storeSettings();
            deviceId = UUID.randomUUID().toString();
            archivePath = configPanel.getContentPaths();
        }
        run(deviceId, archivePath, progressMonitor, callback);
    }
    
    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the given settings instead of those provided by the
     * selection and configuration panel. Returns as soon as the background task
     * is started and uses the callback object to signal task completion and
     * return results.
     *
     * @param deviceId             An ASCII-printable identifier for the device
     *                             associated with the data source that is
     *                             intended to be unique across multiple cases
     *                             (e.g., a UUID).
     * @param archivePath          Path to the archive file.
     * @param progressMonitor      Progress monitor for reporting progress
     *                             during processing.
     * @param callback             Callback to call when processing is done.
     */
    public void run(String deviceId, String archivePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        addArchiveTask = new AddArchiveTask(deviceId, archivePath, progressMonitor, callback);
        jobProcessingExecutor.submit(addArchiveTask);
    }  

    /**
     * This DSP is a service to AutoIngestDataSourceProcessor only. Hence it is
     * only used by AIM. AIM currently doesn't support DSP cancellation.
     */
    @Override
    public void cancel() {
    }

    @Override
    public void reset() {
        deviceId = null;
        archivePath = null;
        configPanel.reset();
        setDataSourceOptionsCalled = false;
    }
}
