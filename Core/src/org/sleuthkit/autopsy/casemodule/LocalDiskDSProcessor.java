/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2021 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagewriter.ImageWriterSettings;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A local drive data source processor that implements the DataSourceProcessor
 * service provider interface to allow integration with the add data source
 * wizard. It also provides a run method overload to allow it to be used
 * independently of the wizard.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class LocalDiskDSProcessor implements DataSourceProcessor {

    private final Logger logger = Logger.getLogger(LocalDiskDSProcessor.class.getName());
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
    private Host host;
    private ImageWriterSettings imageWriterSettings;
    private boolean ignoreFatOrphanFiles;

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
        configPanel.resetLocalDiskSelection();
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
     * @param host            Host for this data source.
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callback        Callback that will be used by the background task
     *                        to return results.
     */
    @Override
    public void run(Host host, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        deviceId = UUID.randomUUID().toString();
        drivePath = configPanel.getContentPath();
        sectorSize = configPanel.getSectorSize();
        timeZone = configPanel.getTimeZone();
        ignoreFatOrphanFiles = configPanel.getNoFatOrphans();
        if (configPanel.getImageWriterEnabled()) {
            imageWriterSettings = configPanel.getImageWriterSettings();
        } else {
            imageWriterSettings = null;
        }

        this.host = host;

        Image image;
        try {
            image = SleuthkitJNI.addImageToDatabase(Case.getCurrentCase().getSleuthkitCase(),
                    new String[]{drivePath}, sectorSize,
                    timeZone, null, null, null, deviceId, this.host);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error adding local disk with path " + drivePath + " to database", ex);
            final List<String> errors = new ArrayList<>();
            errors.add(ex.getMessage());
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errors, new ArrayList<>());
            return;
        }

        addDiskTask = new AddImageTask(
                new AddImageTask.ImageDetails(deviceId, image, sectorSize, timeZone, ignoreFatOrphanFiles, null, null, null, imageWriterSettings),
                progressMonitor,
                new StreamingAddDataSourceCallbacks(new DefaultIngestStream()),
                new StreamingAddImageTaskCallback(new DefaultIngestStream(), callback));
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
        Image image;
        try {
            image = SleuthkitJNI.addImageToDatabase(Case.getCurrentCase().getSleuthkitCase(),
                    new String[]{drivePath}, sectorSize,
                    timeZone, null, null, null, deviceId);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error adding local disk with path " + drivePath + " to database", ex);
            final List<String> errors = new ArrayList<>();
            errors.add(ex.getMessage());
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errors, new ArrayList<>());
            return;
        }

        addDiskTask = new AddImageTask(new AddImageTask.ImageDetails(deviceId, image, sectorSize, timeZone, ignoreFatOrphanFiles, null, null, null, imageWriterSettings),
                progressMonitor,
                new StreamingAddDataSourceCallbacks(new DefaultIngestStream()),
                new StreamingAddImageTaskCallback(new DefaultIngestStream(), callback));
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
    }
}
