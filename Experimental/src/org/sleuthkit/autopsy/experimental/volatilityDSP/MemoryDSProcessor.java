/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.volatilityDSP;

import java.util.UUID;
import java.util.List;
import javax.swing.JPanel;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A memory image data source processor that implements the DataSourceProcessor
 * service provider interface to allow integration with the Add Data Source
 * wizard. It also provides a run method overload to allow it to be used
 * independently of the wizard.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class MemoryDSProcessor implements DataSourceProcessor {

    private final MemoryDSInputPanel configPanel;
    private AddMemoryImageTask addImageTask;

    /*
     * Constructs a memory data source processor that implements the
     * DataSourceProcessor service provider interface to allow integration with
     * the Add Data source wizard. It also provides a run method overload to
     * allow it to be used independently of the wizard.
     */
    public MemoryDSProcessor() {
        configPanel = MemoryDSInputPanel.createInstance(MemoryDSProcessor.class.getName());
    }

    /**
     * Gets a string that describes the type of data sources this processor is
     * able to add to the case database. The string is suitable for display in a
     * type selection UI component (e.g., a combo box).
     *
     * @return A data source type display string for this data source processor.
     */
    @Messages({"MemoryDSProcessor.dataSourceType=Memory Image File (Volatility)"})
    public static String getType() {
        return Bundle.MemoryDSProcessor_dataSourceType();
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
        return Bundle.MemoryDSProcessor_dataSourceType();
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
        run(null, progressMonitor, callback);
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
     * @param host            Host for the data source.
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callback        Callback that will be used by the background task
     *                        to return results.
     */
    @Override
    public void run(Host host, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        configPanel.storeSettings();
        run(UUID.randomUUID().toString(), configPanel.getImageFilePath(), configPanel.getProfile(), configPanel.getPluginsToRun(), configPanel.getTimeZone(), host, progressMonitor, callback);
    }

    /**
     * Adds a memory image data source to the case database using a background
     * task in a separate thread and the given settings instead of those
     * provided by the selection and configuration panel. Returns as soon as the
     * background task is started and uses the callback object to signal task
     * completion and return results.
     *
     * @param deviceId        An ASCII-printable identifier for the device
     *                        associated with the data source that is intended
     *                        to be unique across multiple cases (e.g., a UUID).
     * @param memoryImagePath Path to the memory image file.
     * @param profile         Volatility profile to run or empty string to autodetect
     * @param pluginsToRun    The Volatility plugins to run.
     * @param timeZone        The time zone to use when processing dates and
     *                        times for the image, obtained from
     *                        java.util.TimeZone.getID.
     * @param host            The host for this data source (may be null)
     * @param progressMonitor Progress monitor for reporting progress during
     *                        processing.
     * @param callback        Callback to call when processing is done.
     */
    private void run(String deviceId, String memoryImagePath, String profile, List<String> pluginsToRun, String timeZone, Host host, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        addImageTask = new AddMemoryImageTask(deviceId, memoryImagePath, profile, pluginsToRun, timeZone, host, progressMonitor, callback);
        new Thread(addImageTask).start();
    }

    /**
     * Requests cancellation of the background task that adds a data source to
     * the case database, after the task is started using the run method. This
     * is a "best effort" cancellation, with no guarantees that the case
     * database will be unchanged. If cancellation succeeded, the list of new
     * data sources returned by the background task will be empty.
     */
    @Override
    public void cancel() {
        if (addImageTask != null) {
            addImageTask.cancelTask();
        }
    }

    /**
     * Resets the selection and configuration panel for this data source
     * processor.
     */
    @Override
    public void reset() {
        configPanel.reset();
    }

}
