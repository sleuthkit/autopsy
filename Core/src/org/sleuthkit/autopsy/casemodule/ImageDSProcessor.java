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

import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import javax.swing.filechooser.FileFilter;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * An image data source processor with a configuration panel. This data source
 * processor implements the DataSourceProcessor service provider interface to
 * allow integration with the add data source wizard. It also provides a run
 * method overload to allow it to be used independently of the configuration UI.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class ImageDSProcessor implements DataSourceProcessor {

    private final static String dsType = NbBundle.getMessage(ImageDSProcessor.class, "ImageDSProcessor.dsType.text");
    private static final List<String> allExt = new ArrayList<>();
    private static final GeneralFilter rawFilter = new GeneralFilter(GeneralFilter.RAW_IMAGE_EXTS, GeneralFilter.RAW_IMAGE_DESC);
    private static final GeneralFilter encaseFilter = new GeneralFilter(GeneralFilter.ENCASE_IMAGE_EXTS, GeneralFilter.ENCASE_IMAGE_DESC);
    private static final GeneralFilter virtualMachineFilter = new GeneralFilter(GeneralFilter.VIRTUAL_MACHINE_EXTS, GeneralFilter.VIRTUAL_MACHINE_DESC);
    private static final String allDesc = NbBundle.getMessage(ImageDSProcessor.class, "ImageDSProcessor.allDesc.text");
    private static final GeneralFilter allFilter = new GeneralFilter(allExt, allDesc);
    private static final List<FileFilter> filtersList = new ArrayList<>();
    private final ImageFilePanel configPanel;
    private String dataSourceId;
    private String imagePath;
    private String timeZone;
    private boolean ignoreFatOrphanFiles;
    private boolean configured;
    private AddImageTask addImageTask;

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
     * Constructs a local drive data source processor with a configuration
     * panel. This data source processor implements the DataSourceProcessor
     * service provider interface to allow integration with the add data source
     * wizard. It also provides a run method overload to allow it to be used
     * independently of the configuration UI.
     */
    public ImageDSProcessor() {
        configPanel = ImageFilePanel.createInstance(ImageDSProcessor.class.getName(), filtersList);
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
        configPanel.readSettings();
        configPanel.select();
        return configPanel;
    }

    /**
     * Indicates whether or not the inputs to the configuration panel are valid.
     *
     * @return True or false.
     */
    @Override
    public boolean isPanelValid() {
        return configPanel.validatePanel();
    }

    /**
     * Runs the data source processor in a separate thread. Should only be
     * called after further configuration has been completed.
     *
     * @param monitor Progress monitor to report progress during processing.
     * @param cbObj   Callback to call when processing is done.
     */
    @Override
    public void run(DataSourceProcessorProgressMonitor monitor, DataSourceProcessorCallback cbObj) {
        /*
         * TODO (AUT-1867): Configuration is not currently enforced. This code
         * assumes that the ingest panel is providing validated inputs.
         */
        if (!configured) {
            configPanel.storeSettings();
            if (null == dataSourceId) {
                dataSourceId = UUID.randomUUID().toString();
            }
            imagePath = configPanel.getContentPaths();
            timeZone = configPanel.getTimeZone();
            ignoreFatOrphanFiles = configPanel.getNoFatOrphans();
            configured = true;
        }
        addImageTask = new AddImageTask(dataSourceId, imagePath, timeZone, ignoreFatOrphanFiles, monitor, cbObj);
        new Thread(addImageTask).start();
    }

    /**
     * Runs the data source processor in a separate thread without requiring use
     * the configuration panel.
     *
     * @param dataSourceId         An ASCII-printable identifier for the data
     *                             source that is intended to be unique across
     *                             multiple cases (e.g., a UUID).
     * @param imagePath            Path to the image file.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     * @param monitor              Progress monitor to report progress during
     *                             processing.
     * @param cbObj                Callback to call when processing is done.
     */
    public void run(String dataSourceId, String imagePath, String timeZone, boolean ignoreFatOrphanFiles, DataSourceProcessorProgressMonitor monitor, DataSourceProcessorCallback cbObj) {
        this.dataSourceId = dataSourceId;
        this.imagePath = imagePath;
        this.timeZone = timeZone;
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        configured = true;
        run(monitor, cbObj);
    }

    /**
     * Cancels the processing of the data source.
     */
    @Override
    public void cancel() {
        addImageTask.cancelTask();
    }

    /**
     * Resets the configuration of this data source processor, including its
     * configuration panel.
     */
    @Override
    public void reset() {
        dataSourceId = null;
        imagePath = null;
        timeZone = null;
        ignoreFatOrphanFiles = false;
        configPanel.reset();
        configured = false;
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel. The data source processor will assign a UUID to the
     * data source and will use the time zone of the machine executing this code
     * when when processing dates and times for the image.
     *
     * @param imagePath            Path to the image file.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     *
     * @deprecated Use the run method instead.
     */
    @Deprecated
    public void setDataSourceOptions(String imagePath, String timeZone, boolean ignoreFatOrphanFiles) {
        this.dataSourceId = UUID.randomUUID().toString();
        this.imagePath = imagePath;
        this.timeZone = Calendar.getInstance().getTimeZone().getID();
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        this.configured = true;
    }

}
