/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2016 Basis Technology Corp.
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

import java.util.UUID;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * Local drive data source processor.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class LocalDiskDSProcessor implements DataSourceProcessor {

    private static final String dsType = NbBundle.getMessage(LocalDiskDSProcessor.class, "LocalDiskDSProcessor.dsType.text");
    private final LocalDiskPanel configPanel;
    private String dataSourceId;
    private String drivePath;
    private String timeZone;
    private boolean ignoreFatOrphanFiles;
    private boolean configured;
    private AddImageTask addDiskTask;

    /*
     * Constructs an uninitialized local drive data source processor with a
     * configuration panel. The data source processor should not be run if the
     * configuration panel inputs have not been completed and validated (TODO
     * (AUT-1867) not currently enforced).
     */
    public LocalDiskDSProcessor() {
        configPanel = LocalDiskPanel.getDefault();
    }

    /*
     * Constructs a local drive data source processor. @param dataSourceId A
     * identifier for the data source that is unique across multiple cases
     * (e.g., a UUID). @param imagePath Path to the image file. @param timeZone
     * The time zone to use when processing dates and times for the image.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     * FAT filesystem.
     */
    public LocalDiskDSProcessor(String dataSourceId, String drivePath, String timeZone, boolean ignoreFatOrphanFiles) {
        this.dataSourceId = dataSourceId;
        this.drivePath = drivePath;
        this.timeZone = timeZone;
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        configured = true;
        configPanel = LocalDiskPanel.getDefault();
    }

    /**
     * Gets the display name of the type of data source this type of data source
     * processor is able to process.
     *
     * @return The data source type display name.
     */
    public static String getType() {
        return dsType;
    }

    /**
     * Gets the display name of the type of data source this data source
     * processor is able to process.
     *
     * @return The data source type display name.
     */
    @Override
    public String getDataSourceType() {
        return dsType;
    }

    /**
     * Gets the a configuration panel for this data source processor.
     *
     * @return JPanel The configuration panel.
     */
    @Override
    public JPanel getPanel() {
        configPanel.select();
        return configPanel;
    }

    /**
     * Indicates whether or not the inputs to the configuration panel are valid.
     *
     * @return True or false.
     *
     */
    @Override
    public boolean isPanelValid() {
        return configPanel.validatePanel();
    }

    /**
     * Runs the data source processor in a separate thread.
     *
     * @param progressMonitor Progress monitor to report progress during
     *                        processing.
     * @param cbObj           Callback to call when processing is done.
     *
     */
    @Override
    public void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback cbObj) {
        if (!configured) {
            if (null == dataSourceId) {
                dataSourceId = UUID.randomUUID().toString();
            }            
            drivePath = configPanel.getContentPaths();
            timeZone = configPanel.getTimeZone();
            ignoreFatOrphanFiles = configPanel.getNoFatOrphans();
            configured = true;
        }
        addDiskTask = new AddImageTask(drivePath, timeZone, ignoreFatOrphanFiles, progressMonitor, cbObj);
        new Thread(addDiskTask).start();
    }

    /**
     * Cancels the processing of the data source.
     */
    @Override
    public void cancel() {
        addDiskTask.cancelTask();
    }

    /**
     * Resets the configuration of this data source processor, including its
     * configuration panel.
     */
    @Override
    public void reset() {
        configPanel.reset();
        dataSourceId = null;        
        drivePath = null;
        timeZone = null;
        ignoreFatOrphanFiles = false;
        configured = false;
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel.
     *
     * @param drivePath            Path to the local drive.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     *
     * @deprecated Use the constructor that takes arguments instead.
     */
    @Deprecated
    public void setDataSourceOptions(String drivePath, String timeZone, boolean ignoreFatOrphanFiles) {
        this.drivePath = drivePath;
        this.timeZone = timeZone;
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        configured = true;
    }
}
