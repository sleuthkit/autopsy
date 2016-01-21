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

import java.util.Calendar;
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

    /**
     * Constructs an uninitialized local drive data source processor with a
     * configuration panel. The data source processor should not be run until
     * further initialization using the configuration panel has been completed
     * and validated or the setDataSourceOptions method has been called.
     *
     * TODO (AUT-1867): Configuration is not currently enforced.
     */
    public LocalDiskDSProcessor() {
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
     * configuration panel. The data source processor will assign a UUID to the
     * data source and will use the time zone of the machine executing this code
     * when when processing dates and times for the image.
     *
     * @param drivePath            Path to the local drive.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     */
    public void setDataSourceOptions(String drivePath, boolean ignoreFatOrphanFiles) {
        setDataSourceOptions(drivePath, Calendar.getInstance().getTimeZone().getID(), ignoreFatOrphanFiles);
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel. The data source processor will assign a UUID to the
     * data source.
     *
     * @param drivePath            Path to the local drive.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     */
    public void setDataSourceOptions(String drivePath, String timeZone, boolean ignoreFatOrphanFiles) {
        setDataSourceOptions(UUID.randomUUID().toString(), drivePath, timeZone, ignoreFatOrphanFiles);
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel.
     *
     * @param dataSourceId         A identifier for the data source that is
     *                             unique across multiple cases (e.g., a UUID).
     * @param drivePath            Path to the local drive.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     */
    public void setDataSourceOptions(String dataSourceId, String drivePath, String timeZone, boolean ignoreFatOrphanFiles) {
        this.dataSourceId = dataSourceId;
        this.drivePath = drivePath;
        this.timeZone = timeZone;
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        configured = true;
    }

}
