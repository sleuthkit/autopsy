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
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import javax.swing.filechooser.FileFilter;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.DataSourceUtils;
import org.sleuthkit.autopsy.datasourceprocessors.AutoIngestDataSourceProcessor;

/**
 * A image file data source processor that implements the DataSourceProcessor
 * service provider interface to allow integration with the add data source
 * wizard. It also provides a run method overload to allow it to be used
 * independently of the wizard.
 */
@ServiceProviders(value={
    @ServiceProvider(service=DataSourceProcessor.class),
    @ServiceProvider(service=AutoIngestDataSourceProcessor.class)}
)
public class ImageDSProcessor implements DataSourceProcessor, AutoIngestDataSourceProcessor {

    private final static String DATA_SOURCE_TYPE = NbBundle.getMessage(ImageDSProcessor.class, "ImageDSProcessor.dsType.text");
    private static final List<String> allExt = new ArrayList<>();
    private static final GeneralFilter rawFilter = new GeneralFilter(GeneralFilter.RAW_IMAGE_EXTS, GeneralFilter.RAW_IMAGE_DESC);
    private static final GeneralFilter encaseFilter = new GeneralFilter(GeneralFilter.ENCASE_IMAGE_EXTS, GeneralFilter.ENCASE_IMAGE_DESC);
    private static final GeneralFilter virtualMachineFilter = new GeneralFilter(GeneralFilter.VIRTUAL_MACHINE_EXTS, GeneralFilter.VIRTUAL_MACHINE_DESC);
    private static final String ALL_DESC = NbBundle.getMessage(ImageDSProcessor.class, "ImageDSProcessor.allDesc.text");
    private static final GeneralFilter allFilter = new GeneralFilter(allExt, ALL_DESC);
    private static final List<FileFilter> filtersList = new ArrayList<>();
    private final ImageFilePanel configPanel;
    private AddImageTask addImageTask;
    /*
     * TODO: Remove the setDataSourceOptionsCalled flag and the settings fields
     * when the deprecated method setDataSourceOptions is removed.
     */
    private String deviceId;
    private String imagePath;
    private int sectorSize;
    private String timeZone;
    private boolean ignoreFatOrphanFiles;
    private boolean setDataSourceOptionsCalled;

    static {
        filtersList.add(allFilter);
        filtersList.add(rawFilter);
        filtersList.add(encaseFilter);
        filtersList.add(virtualMachineFilter);
        allExt.addAll(GeneralFilter.RAW_IMAGE_EXTS);
        allExt.addAll(GeneralFilter.ENCASE_IMAGE_EXTS);
        allExt.addAll(GeneralFilter.VIRTUAL_MACHINE_EXTS);
    }

    /**
     * Constructs an image file data source processor that implements the
     * DataSourceProcessor service provider interface to allow integration with
     * the add data source wizard. It also provides a run method overload to
     * allow it to be used independently of the wizard.
     */
    public ImageDSProcessor() {
        configPanel = ImageFilePanel.createInstance(ImageDSProcessor.class.getName(), filtersList);
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
        return getType();
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
            imagePath = configPanel.getContentPaths();
            sectorSize = configPanel.getSectorSize();
            timeZone = configPanel.getTimeZone();
            ignoreFatOrphanFiles = configPanel.getNoFatOrphans();
        }
        run(deviceId, imagePath, sectorSize, timeZone, ignoreFatOrphanFiles, progressMonitor, callback);
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
     * @param imagePath            Path to the image file.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     * @param progressMonitor      Progress monitor for reporting progress
     *                             during processing.
     * @param callback             Callback to call when processing is done.
     */
    public void run(String deviceId, String imagePath, String timeZone, boolean ignoreFatOrphanFiles, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        run(deviceId, imagePath, 0, timeZone, ignoreFatOrphanFiles, progressMonitor, callback);
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
     * @param imagePath            Path to the image file.
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
    private void run(String deviceId, String imagePath, int sectorSize, String timeZone, boolean ignoreFatOrphanFiles, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        addImageTask = new AddImageTask(deviceId, imagePath, sectorSize, timeZone, ignoreFatOrphanFiles, null, progressMonitor, callback);
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
        if (null != addImageTask) {
            addImageTask.cancelTask();
        }
    }

    /**
     * Resets the selection and configuration panel for this data source
     * processor.
     */
    @Override
    public void reset() {
        deviceId = null;
        imagePath = null;
        timeZone = null;
        ignoreFatOrphanFiles = false;
        configPanel.reset();
        setDataSourceOptionsCalled = false;
    }

    private static boolean isAcceptedByFiler(File file, List<FileFilter> filters) {
        for (FileFilter filter : filters) {
            if (filter.accept(file)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int canProcess(Path dataSourcePath) throws AutoIngestDataSourceProcessorException {
        
        // check file extension for supported types
        if (!isAcceptedByFiler(dataSourcePath.toFile(), filtersList)) {
            return 0;
        }
        
        try {
            // verify that the image has a file system that TSK can process
            Case currentCase = Case.getOpenCase();
            if (!DataSourceUtils.imageHasFileSystem(dataSourcePath)) {
                // image does not have a file system that TSK can process
                return 0;
            }
        } catch (Exception ex) {
            throw new AutoIngestDataSourceProcessorException("Exception inside canProcess() method", ex);
        }
        
        // able to process the data source
        return 100;
    }

    @Override
    public void process(String deviceId, Path dataSourcePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) throws AutoIngestDataSourceProcessorException {
        this.deviceId = deviceId;
        this.imagePath = dataSourcePath.toString();
        this.sectorSize = 0;
        this.timeZone = Calendar.getInstance().getTimeZone().getID();
        this.ignoreFatOrphanFiles = false;
        setDataSourceOptionsCalled = true;
        run(deviceId, dataSourcePath.toString(), sectorSize, timeZone, ignoreFatOrphanFiles, progressMonitor, callBack);
    }
    
    /**
     * Sets the configuration of the data source processor without using the
     * selection and configuration panel.
     *
     * @param imagePath            Path to the image file.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     *
     * @deprecated Use the provided overload of the run method instead.
     */
    @Deprecated
    public void setDataSourceOptions(String imagePath, String timeZone, boolean ignoreFatOrphanFiles) {
        this.deviceId = UUID.randomUUID().toString();
        this.imagePath = imagePath;
        this.sectorSize = 0;
        this.timeZone = Calendar.getInstance().getTimeZone().getID();
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        setDataSourceOptionsCalled = true;
    }
    
}
