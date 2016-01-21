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
import java.util.List;
import java.util.UUID;
import javax.swing.filechooser.FileFilter;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * Image data source processor.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class ImageDSProcessor implements DataSourceProcessor {

    private final static String dsType = NbBundle.getMessage(ImageDSProcessor.class, "ImageDSProcessor.dsType.text");
    private static final List<String> allExt = new ArrayList<>();
    private static final GeneralFilter rawFilter = new GeneralFilter(GeneralFilter.RAW_IMAGE_EXTS, GeneralFilter.RAW_IMAGE_DESC);
    private static final GeneralFilter encaseFilter = new GeneralFilter(GeneralFilter.ENCASE_IMAGE_EXTS, GeneralFilter.ENCASE_IMAGE_DESC);
    private static final String allDesc = NbBundle.getMessage(ImageDSProcessor.class, "ImageDSProcessor.allDesc.text");
    private static final GeneralFilter allFilter = new GeneralFilter(allExt, allDesc);
    private static final List<FileFilter> filtersList = new ArrayList<>();
    private final ImageFilePanel imageFilePanel;
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
        allExt.addAll(GeneralFilter.RAW_IMAGE_EXTS);
        allExt.addAll(GeneralFilter.ENCASE_IMAGE_EXTS);
    }

    /**
     * Constructs an uninitialized image data source processor with a
     * configuration panel. The data source processor should not be run if the
     * configuration panel inputs have not been completed and validated (TODO
     * (AUT-1867) not currently enforced).
     */
    public ImageDSProcessor() {
        this.dataSourceId = UUID.randomUUID().toString();
        imageFilePanel = ImageFilePanel.createInstance(ImageDSProcessor.class.getName(), filtersList);
    }

    /**
     * Constructs an image data source processor.
     *
     * @param dataSourceId
     * @param imagePath
     * @param timeZone
     * @param ignoreFatOrphanFiles
     */
    public ImageDSProcessor(String dataSourceId, String imagePath, String timeZone, boolean ignoreFatOrphanFiles) {
        this.dataSourceId = dataSourceId;
        this.imagePath = imagePath;
        this.timeZone = timeZone;
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        imageFilePanel = ImageFilePanel.createInstance(ImageDSProcessor.class.getName(), filtersList);
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
        imageFilePanel.readSettings();
        imageFilePanel.select();
        return imageFilePanel;
    }

    /**
     * Indicates whether or not the inputs to the configuration panel are valid.
     *
     * @return True or false.
     *
     */
    @Override
    public boolean isPanelValid() {
        return imageFilePanel.validatePanel();
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
            imageFilePanel.storeSettings();
            imagePath = imageFilePanel.getContentPaths();
            timeZone = imageFilePanel.getTimeZone();
            ignoreFatOrphanFiles = imageFilePanel.getNoFatOrphans();
        }
        addImageTask = new AddImageTask(imagePath, timeZone, ignoreFatOrphanFiles, progressMonitor, cbObj);
        new Thread(addImageTask).start();
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
        imageFilePanel.reset();
        configured = false;
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel.
     *
     * @param imagePath            Path to the image file.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     *
     * @deprecated Use the constructor that takes arguments instead.
     */
    @Deprecated
    public void setDataSourceOptions(String imagePath, String timeZone, boolean ignoreFatOrphanFiles) {
        dataSourceId = UUID.randomUUID().toString();
        this.imagePath = imagePath;
        this.timeZone = timeZone;
        this.ignoreFatOrphanFiles = ignoreFatOrphanFiles;
        configured = true;
    }

}
