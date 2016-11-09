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
package org.sleuthkit.autopsy.datasourceprocessors;

import java.util.UUID;
import javax.swing.JPanel;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * A Raw data source processor that implements the DataSourceProcessor service
 * provider interface to allow integration with the  add data source wizard.
 * It also provides a run method overload to allow it to be used independently
 * of the wizard.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class RawDSProcessor implements DataSourceProcessor {
    private final RawDSInputPanel configPanel;
    private AddRawImageTask addImageTask;

    /*
     * Constructs a Raw data source processor that implements the
     * DataSourceProcessor service provider interface to allow integration
     * with the add data source wizard. It also provides a run method
     * overload to allow it to be used independently of the wizard.
     */
    public RawDSProcessor() {
        configPanel = RawDSInputPanel.createInstance(RawDSProcessor.class.getName());
    }

/**
     * Gets a string that describes the type of data sources this processor is
     * able to add to the case database. The string is suitable for display in a
     * type selection UI component (e.g., a combo box).
     *
     * @return A data source type display string for this data source processor.
     */
    @Messages({"RawDSProcessor.dataSourceType=Unallocated Space Image File"})
    public static String getType() {
        return Bundle.RawDSProcessor_dataSourceType();
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
        return  Bundle.RawDSProcessor_dataSourceType();
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
        configPanel.storeSettings();
        run(UUID.randomUUID().toString(), configPanel.getImageFilePath(), configPanel.getTimeZone(), configPanel.getChunkSize(), progressMonitor, callback);       
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
     * @param rawDSInputFilePath       Path to a Raw data source file.
     * @param isHandsetFile            Indicates whether the XML file is for a
     *                                 handset or a SIM.
     * @param progressMonitor          Progress monitor for reporting progress
     *                                 during processing.
     */
    private void run(String deviceId, String imageFilePath, String timeZone, long chunkSize, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        addImageTask = new AddRawImageTask(deviceId, imageFilePath, timeZone, chunkSize, progressMonitor, callback);
        new Thread(addImageTask).start();
    }

   
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
    }

}
