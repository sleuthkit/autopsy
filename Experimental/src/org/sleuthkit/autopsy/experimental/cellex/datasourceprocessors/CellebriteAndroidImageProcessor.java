/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.cellex.datasourceprocessors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JPanel;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * An Cellebrite UFED output folder data source processor that implements the
 * DataSourceProcessor service provider interface to allow integration with the
 * add data source wizard. It also provides a run method overload to allow it to
 * be used independently of the wizard.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class CellebriteAndroidImageProcessor implements DataSourceProcessor {

    private static final String DATA_SOURCE_TYPE = "Cellebrite Android";
    private final CellebriteAndroidInputPanel configPanel;
    private AddCellebriteAndroidImageTask addImagesTask;

    /**
     * Contructs a Cellebrite UFED output folder data source processor that
     * implements the DataSourceProcessor service provider interface to allow
     * integration with the add data source wizard. It also provides a run
     * method overload to allow it to be used independently of the wizard.
     */
    public CellebriteAndroidImageProcessor() {
        configPanel = CellebriteAndroidInputPanel.createInstance(CellebriteAndroidImageProcessor.class.getName());
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
        run(UUID.randomUUID().toString(), configPanel.getContentPaths(), configPanel.getTimeZone(), progressMonitor, callback);
    }

    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the given settings instead of those provided by the
     * selection and configuration panel. Returns as soon as the background task
     * is started and uses the callback object to signal task completion and
     * return results.
     *
     * This method should not be called unless isPanelValid returns true.
     *
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callback        Callback that will be used by the background task
     *                        to return results.
     */
    public void run(String deviceId, String imageFolderPath, String timeZone, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        List<String> imageFilePaths = getImageFilePaths(imageFolderPath);
        addImagesTask = new AddCellebriteAndroidImageTask(deviceId, imageFilePaths, timeZone, progressMonitor, callback);
        new Thread(addImagesTask).start();
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
        addImagesTask.cancelTask();
    }

    /**
     * Resets the selection and configuration panel for this data source
     * processor.
     */
    @Override
    public void reset() {
        configPanel.reset();
    }

    /**
     * Gets the paths of the image files in a Cellebrite UFED output folder.
     *
     * @param folderPath The path to a Cellebrite UFED output folder
     *
     * @return A list of image file paths.
     */
    private static List<String> getImageFilePaths(String folderPath) {
        List<String> imageFilePaths = new ArrayList<>();
        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles();
        for (File file : listOfFiles) {
            if (file.isFile()) {
                String fName = file.getName().toLowerCase();
                int lastPeriod = fName.lastIndexOf('.');
                if (-1 == lastPeriod) {
                    continue;
                }
                String fNameNoExt = fName.substring(0, lastPeriod);
                String ext = fName.substring(lastPeriod + 1);
                String filePathName = folderPath + File.separator + fName;
                if (ext.equalsIgnoreCase("bin")) {
                    // this needs to identify and handle different Cellebrite scenarios:
                    //  i  single image in a single file
                    // ii. Single image spilt over multiple files - just need to pass the first to TSK and it will combine the split image files.
                    //       Note there may be more than  than one split images in a single dir, 
                    //       e.g. blk0_mmcblk0.bin, blk0_mmcblk0(1).bin......, and blk24_mmcblk1.bin, blk24_mmcblk1(1).bin......
                    //iii. Multiple image files - one per volume - need to handle each one separately
                    //       e.g. blk0_mmcblk0.bin, mtd0_system.bin, mtd1_cache.bin, mtd2_userdata.bin
                    // if the file name ends with something like (001).bin then its part of a split image,
                    if (!fNameNoExt.matches("\\w+\\(\\d+\\)")) {
                        imageFilePaths.add(filePathName);
                    }
                }
            }
        }
        return imageFilePaths;
    }

}
