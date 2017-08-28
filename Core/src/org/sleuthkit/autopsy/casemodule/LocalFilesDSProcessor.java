/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;

/**
 * A local/logical files and/or directories data source processor that
 * implements the DataSourceProcessor service provider interface to allow
 * integration with the add data source wizard. It also provides a run method
 * overload to allow it to be used independently of the wizard.
 */
@ServiceProviders(value={
    @ServiceProvider(service=DataSourceProcessor.class),
    @ServiceProvider(service=AutoIngestDataSourceProcessor.class)}
)
public class LocalFilesDSProcessor implements DataSourceProcessor, AutoIngestDataSourceProcessor {

    private static final String DATA_SOURCE_TYPE = NbBundle.getMessage(LocalFilesDSProcessor.class, "LocalFilesDSProcessor.dsType");
    private final LocalFilesPanel configPanel;
    /*
     * TODO: Remove the setDataSourceOptionsCalled flag and the settings fields
     * when the deprecated method setDataSourceOptions is removed.
     */
    private List<String> localFilePaths;
    private boolean setDataSourceOptionsCalled;

    /**
     * Constructs a local/logical files and/or directories data source processor
     * that implements the DataSourceProcessor service provider interface to
     * allow integration with the add data source wizard. It also provides a run
     * method overload to allow it to be used independently of the wizard.
     */
    public LocalFilesDSProcessor() {
        configPanel = LocalFilesPanel.getDefault();
    }

    /**
     * Gets a string that describes the type of data sources this processor is
     * able to add to the case database. The string is suitable for display in a
     * type selection UI component (e.g., a combo box).
     *
     * @return A data source type display string for this data source processor.
     */
    public static String getType() {
        return DATA_SOURCE_TYPE;
    }

    /**
     * Gets a string that describes the type of data sources this processor is
     * able to add to the case database. The string is suitable for display in a
     * type selection UI component (e.g., a combo box).
     *
     * @return A data source type display string for this data source processor.
     */
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
            localFilePaths = configPanel.getContentPaths();
        }
        run(UUID.randomUUID().toString(), configPanel.getFileSetName(), localFilePaths, progressMonitor, callback);
    }

    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the given settings instead of those provided by the
     * selection and configuration panel. Returns as soon as the background task
     * is started and uses the callback object to signal task completion and
     * return results.
     *
     * @param deviceId                 An ASCII-printable identifier for the
     *                                 device associated with the data source
     *                                 that is intended to be unique across
     *                                 multiple cases (e.g., a UUID).
     * @param rootVirtualDirectoryName The name to give to the virtual directory
     *                                 that will serve as the root for the
     *                                 local/logical files and/or directories
     *                                 that compose the data source. Pass the
     *                                 empty string to get a default name of the
     *                                 form: LogicalFileSet[N]
     * @param localFilePaths           A list of local/logical file and/or
     *                                 directory localFilePaths.
     * @param progressMonitor          Progress monitor for reporting progress
     *                                 during processing.
     * @param callback                 Callback to call when processing is done.
     */
    public void run(String deviceId, String rootVirtualDirectoryName, List<String> localFilePaths, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        new Thread(new AddLocalFilesTask(deviceId, rootVirtualDirectoryName, localFilePaths, progressMonitor, callback)).start();
    }

    /**
     * Requests cancellation of the background task that adds a data source to
     * the case database, after the task is started using the run method. This
     * is a "best effort" cancellation, with no guarantees that the case
     * database will be unchanged. If cancellation succeeded, the list of new
     * data sources returned by the background task will be empty.
     *
     * TODO (AUT-1907): Implement cancellation by deleting rows added to the
     * case database.
     */
    @Override
    public void cancel() {
    }

    /**
     * Resets the selection and configuration panel for this data source
     * processor.
     */
    @Override
    public void reset() {
        configPanel.reset();
        localFilePaths = null;
        setDataSourceOptionsCalled = false;
    }

    @Override
    public int canProcess(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {
        // Local files DSP can process any file by simply adding it as a logical file.
        // It should return lowest possible non-zero confidence level and be treated 
        // as the "option of last resort" for auto ingest purposes
        return 1;
    }

    @Override
    public void process(String deviceId, Path dataSourcePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) throws AutoIngestDataSourceProcessorException {
        this.localFilePaths = Arrays.asList(new String[]{dataSourcePath.toString()});
        run(deviceId, deviceId, this.localFilePaths, progressMonitor, callBack);
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel. The data source processor will assign a UUID to the
     * data source and will use the time zone of the machine executing this code
     * when when processing dates and times for the image.
     *
     * @param paths A list of local/logical file and/or directory
     *              localFilePaths.
     *
     * @deprecated Use the provided overload of the run method instead.
     */
    @Deprecated
    public void setDataSourceOptions(String paths) {
        // The LocalFilesPanel used to separate file paths with a comma and pass
        // them as a string, but because file names are allowed to contain
        // commas, this approach was buggy and replaced. We now pass a list of
        // String paths.
        this.localFilePaths = Arrays.asList(paths.split(","));
        setDataSourceOptionsCalled = true;
    }
    
}
