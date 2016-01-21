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
    private static final GeneralFilter virtualMachineFilter = new GeneralFilter(GeneralFilter.VIRTUAL_MACHINE_EXTS, GeneralFilter.VIRTUAL_MACHINE_DESC);
    private static final String allDesc = NbBundle.getMessage(ImageDSProcessor.class, "ImageDSProcessor.allDesc.text");
    private static final GeneralFilter allFilter = new GeneralFilter(allExt, allDesc);
    private static final List<FileFilter> filtersList = new ArrayList<>();
    private final ImageFilePanel imageFilePanel;
    private String imagePath;
    private String timeZone;
    private boolean noFatOrphans;
    private boolean imageOptionsSet = false;
    private AddImageTask addImageTask;
    DataSourceProcessorCallback callbackObj = null;

    static {
        filtersList.add(allFilter);
        filtersList.add(rawFilter);
        filtersList.add(encaseFilter);
        filtersList.add(virtualMachineFilter);
        allExt.addAll(GeneralFilter.RAW_IMAGE_EXTS);
        allExt.addAll(GeneralFilter.ENCASE_IMAGE_EXTS);
	allExt.addAll(GeneralFilter.VIRTUAL_MACHINE_EXTS);
    }

    /*
     * Constructs an uninitialized data source processor with a configuration
     * panel. The data source processor will not run if the configuration panel
     * inputs have not been completed and validated.
     */
    public ImageDSProcessor() {
        imageFilePanel = ImageFilePanel.createInstance(ImageDSProcessor.class.getName(), filtersList);
    }

    /**
     * Gets the display names of the types of data sources this type of data
     * source processor is able to process.
     *
     * @return The data source type display name.
     */
    public static String getType() {
        return dsType;
    }

    /**
     * Gets the display names of the types of data sources this data source
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
     *
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
        callbackObj = cbObj;
        if (!imageOptionsSet) {
            imageFilePanel.storeSettings();
            imagePath = imageFilePanel.getContentPaths();
            timeZone = imageFilePanel.getTimeZone();
            noFatOrphans = imageFilePanel.getNoFatOrphans();
        }
        addImageTask = new AddImageTask(imagePath, timeZone, noFatOrphans, progressMonitor, cbObj);
        new Thread(addImageTask).start();
    }

    /**
     * Cancel the processing of the data source.
     *
     */
    @Override
    public void cancel() {
        addImageTask.cancelTask();
    }

    /**
     * Reset the configuration of this data source processor, including its
     * configuration panel.
     */
    @Override
    public void reset() {
        imageFilePanel.reset();
        imageOptionsSet = false;
        imagePath = null;
        timeZone = null;
        noFatOrphans = false;
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel.
     *
     * @param imgPath Path to the image file.
     * @param tz      The time zone to use when processing dates and times for
     *                the image.
     * @param noFat   Whether to parse orphans if the image has a FAT filesystem.
     */
    public void setDataSourceOptions(String imgPath, String tz, boolean noFat) {
        this.imagePath = imgPath;
        this.timeZone = tz;
        this.noFatOrphans = noFat;
        imageOptionsSet = true;
    }

}
