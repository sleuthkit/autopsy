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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * Image data source processor.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class ImageDSProcessor implements DataSourceProcessor {

    private static final Logger logger = Logger.getLogger(ImageDSProcessor.class.getName());
    private final static String dsType = NbBundle.getMessage(ImageDSProcessor.class, "ImageDSProcessor.dsType.text");
    private static final List<String> allExt = new ArrayList<>();
    private static final GeneralFilter rawFilter = new GeneralFilter(GeneralFilter.RAW_IMAGE_EXTS, GeneralFilter.RAW_IMAGE_DESC);
    private static final GeneralFilter encaseFilter = new GeneralFilter(GeneralFilter.ENCASE_IMAGE_EXTS, GeneralFilter.ENCASE_IMAGE_DESC);
    private static final String allDesc = NbBundle.getMessage(ImageDSProcessor.class, "ImageDSProcessor.allDesc.text");
    private static final GeneralFilter allFilter = new GeneralFilter(allExt, allDesc);
    private static final List<FileFilter> filtersList = new ArrayList<>();

    static {
        filtersList.add(allFilter);
        filtersList.add(rawFilter);
        filtersList.add(encaseFilter);
        allExt.addAll(GeneralFilter.RAW_IMAGE_EXTS);
        allExt.addAll(GeneralFilter.ENCASE_IMAGE_EXTS);
    }
    private final ImageFilePanel imageFilePanel;
    private String imagePath;
    private String timeZone;
    private boolean noFatOrphans;
    private boolean imageOptionsSet = false;
    private AddImageTask addImageTask;
    DataSourceProcessorCallback callbackObj = null;

    /*
     * A no argument constructor is required for the NM lookup() method to
     * create an object
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
     * Returns the JPanel for collecting the Data source information
     *
     * @return JPanel the config panel
     *
     */
    @Override
    public JPanel getPanel() {
        imageFilePanel.readSettings();
        imageFilePanel.select();
        return imageFilePanel;
    }

    /**
     * Validates the data collected by the JPanel
     *
     * @return String returns NULL if success, error string if there is any
     *         errors
     *
     */
    @Override
    public boolean isPanelValid() {
        return imageFilePanel.validatePanel();
    }

    /**
     * Runs the data source processor. This must kick off processing the data
     * source in background
     *
     * @param progressMonitor Progress monitor to report progress during
     *                        processing
     * @param cbObj           callback to call when processing is done.
     *
     */
    @Override
    public void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback cbObj) {
        callbackObj = cbObj;
        if (!imageOptionsSet) {
            //tell the panel to save the current settings
            imageFilePanel.storeSettings();

            // get the image options from the panel
            imagePath = imageFilePanel.getContentPaths();
            timeZone = imageFilePanel.getTimeZone();
            noFatOrphans = imageFilePanel.getNoFatOrphans();
        }

        addImageTask = new AddImageTask(imagePath, timeZone, noFatOrphans, progressMonitor, cbObj);
        new Thread(addImageTask).start();
    }

    /**
     * Cancel the data source processing
     *
     */
    @Override
    public void cancel() {
        addImageTask.cancelTask();
    }

    /**
     * Reset the data source processor
     *
     */
    @Override
    public void reset() {
        // reset the config panel
        imageFilePanel.reset();

        // reset state 
        imageOptionsSet = false;
        imagePath = null;
        timeZone = null;
        noFatOrphans = false;
    }

    /**
     * Sets the data source options externally. To be used by a client that does
     * not have a UI and does not use the JPanel to collect this information
     * from a user.
     *
     * @param imgPath path to thew image or first image
     * @param tz      timeZone
     * @param noFat   whether to parse FAT orphans
     */
    public void setDataSourceOptions(String imgPath, String tz, boolean noFat) {
        this.imagePath = imgPath;
        this.timeZone = tz;
        this.noFatOrphans = noFat;
        imageOptionsSet = true;
    }

}
