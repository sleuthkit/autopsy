/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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

import java.io.File;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.UUID;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.DriveUtils;
import org.sleuthkit.autopsy.imagewriter.ImageWriterSettings;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;

/**
 * A local drive data source processor that implements the DataSourceProcessor
 * service provider interface to allow integration with the add data source
 * wizard. It also provides a run method overload to allow it to be used
 * independently of the wizard.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = DataSourceProcessor.class),
    @ServiceProvider(service = AutoIngestDataSourceProcessor.class)}
)
public class LocalDiskDSProcessor implements DataSourceProcessor, AutoIngestDataSourceProcessor {

    private static final String DATA_SOURCE_TYPE = NbBundle.getMessage(LocalDiskDSProcessor.class, "LocalDiskDSProcessor.dsType.text");
    private final LocalDiskPanel configPanel;
    private AddImageTask addDiskTask;
    /*
     * TODO: Remove the setDataSourceOptionsCalled flag and the settings fields
     * when the deprecated method setDataSourceOptions is removed.
     */
    private String deviceId;
    private String drivePath;
    private int sectorSize;
    private String timeZone;
    private ImageWriterSettings imageWriterSettings;
    private boolean ignoreFatOrphanFiles;
    private boolean setDataSourceOptionsCalled;

    /**
     * Constructs a local drive data source processor that implements the
     * DataSourceProcessor service provider interface to allow integration with
     * the add data source wizard. It also provides a run method overload to
     * allow it to be used independently of the wizard.
     */
    public LocalDiskDSProcessor() {
        configPanel = LocalDiskPanel.getDefault();
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
        configPanel.refreshTable();
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
            deviceId = UUID.randomUUID().toString();
            drivePath = configPanel.getContentPaths();
            sectorSize = configPanel.getSectorSize();
            timeZone = configPanel.getTimeZone();
            ignoreFatOrphanFiles = configPanel.getNoFatOrphans();
            if (configPanel.getImageWriterEnabled()) {
                imageWriterSettings = configPanel.getImageWriterSettings();
            } else {
                imageWriterSettings = null;
            }
        }
        addDiskTask = new AddImageTask(deviceId, drivePath, sectorSize, timeZone, ignoreFatOrphanFiles, imageWriterSettings, progressMonitor, callback);
        new Thread(addDiskTask).start();
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
     * @param drivePath            Path to the local drive.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     * @param progressMonitor      Progress monitor for reporting progress
     *                             during processing.
     * @param callback             Callback to call when processing is done.
     */
    public void run(String deviceId, String drivePath, String timeZone, boolean ignoreFatOrphanFiles, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        run(deviceId, drivePath, 0, timeZone, ignoreFatOrphanFiles, progressMonitor, callback);
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
     * @param drivePath            Path to the local drive.
     * @param sectorSize           The sector size (use '0' for autodetect).
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     * @param progressMonitor      Progress monitor for reporting progress
     *                             during processing.
     * @param callback             Callback to call when processing is done.
     */
    private void run(String deviceId, String drivePath, int sectorSize, String timeZone, boolean ignoreFatOrphanFiles, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        addDiskTask = new AddImageTask(deviceId, drivePath, sectorSize, timeZone, ignoreFatOrphanFiles, imageWriterSettings, progressMonitor, callback);
        new Thread(addDiskTask).start();
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
        if (null != addDiskTask) {
            addDiskTask.cancelTask();
        }
    }

    /**
     * Resets the selection and configuration panel for this data source
     * processor.
     */
    @Override
    public void reset() {
        deviceId = null;
        drivePath = null;
        timeZone = null;
        ignoreFatOrphanFiles = false;
        setDataSourceOptionsCalled = false;
    }

    @Override
    public int canProcess(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {

        // verify that the data source is not a file or a directory
        File file = dataSourcePath.toFile();
        // ELTODO this needs to be tested more. should I keep isDirectory or just test for isFile?
        if (file.isFile() || file.isDirectory()) {
            return 0;
        }

        // check whether data source is an existing disk or partition
        // ELTODO this needs to be tested more. do these methods actually work correctly? 
        // or should I use PlatformUtil.getPhysicalDrives() and PlatformUtil.getPartitions() instead?
        String path = dataSourcePath.toString();
        if ((DriveUtils.isPhysicalDrive(path) || DriveUtils.isPartition(path)) && DriveUtils.driveExists(path)) {
            return 90;
        }

        return 0;
    }

    @Override
    public void process(String deviceId, Path dataSourcePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) throws AutoIngestDataSourceProcessorException {
        this.deviceId = deviceId;
        this.drivePath = dataSourcePath.toString();
        this.sectorSize = 0;
        this.timeZone = Calendar.getInstance().getTimeZone().getID();
        this.ignoreFatOrphanFiles = false;
        setDataSourceOptionsCalled = true;
        run(deviceId, drivePath, sectorSize, timeZone, ignoreFatOrphanFiles, progressMonitor, callBack);
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel.
     *
     * @param drivePath            Path to the local drive.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the local drive, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     *
     * @deprecated Use the provided overload of the run method instead.
     */
    @Deprecated
    public void setDataSourceOptions(String drivePath, String timeZone, boolean ignoreFatOrphanFiles) {
        this.deviceId = UUID.randomUUID().toString();
        this.drivePath = drivePath;
        this.sectorSize = 0;
        this.timeZone = Calendar.getInstance().getTimeZone().getID();
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        setDataSourceOptionsCalled = true;
    }

}
