/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.dsp;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import javax.swing.JOptionPane;
import static javax.swing.JOptionPane.YES_OPTION;
import javax.swing.JPanel;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A Logical Imager data source processor that implements the
 * DataSourceProcessor service provider interface to allow integration with the
 * add data source wizard. It also provides a run method overload to allow it to
 * be used independently of the wizard.
 */
@ServiceProviders(value = {
    @ServiceProvider(service = DataSourceProcessor.class)}
)
public final class LogicalImagerDSProcessor implements DataSourceProcessor {

    private static final String LOGICAL_IMAGER_DIR = "LogicalImager"; //NON-NLS
    private final LogicalImagerPanel configPanel;
    private AddLogicalImageTask addLogicalImageTask;

    /*
     * Constructs a Logical Imager data source processor that implements the
     * DataSourceProcessor service provider interface to allow integration with
     * the add data source wizard. It also provides a run method overload to
     * allow it to be used independently of the wizard.
     */
    public LogicalImagerDSProcessor() {
        configPanel = LogicalImagerPanel.createInstance(LogicalImagerDSProcessor.class.getName());
    }

    /**
     * Gets a string that describes the type of data sources this processor is
     * able to add to the case database. The string is suitable for display in a
     * type selection UI component (e.g., a combo box).
     *
     * @return A data source type display string for this data source processor.
     */
    @Messages({"LogicalImagerDSProcessor.dataSourceType=Autopsy Logical Imager Results"})
    public static String getType() {
        return Bundle.LogicalImagerDSProcessor_dataSourceType();
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
        return Bundle.LogicalImagerDSProcessor_dataSourceType();
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
        configPanel.reset();
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
     * @param host            Host for the data source.
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callback        Callback that will be used by the background task
     *                        to return results.
     */
    @Messages({
        "# {0} - imageDirPath", "LogicalImagerDSProcessor.imageDirPathNotFound={0} not found.\nUSB drive has been ejected.",
        "# {0} - directory", "LogicalImagerDSProcessor.failToCreateDirectory=Failed to create directory {0}",
        "# {0} - directory", "LogicalImagerDSProcessor.directoryAlreadyExists=Directory {0} already exists",
        "LogicalImagerDSProcessor.destinationDirectoryConfirmation=Destination directory confirmation",
        "# {0} - directory", "LogicalImagerDSProcessor.destinationDirectoryConfirmationMsg=The logical imager folder {0} already exists,\ndo you want to add it again using a new folder name?",
        "LogicalImagerDSProcessor.noCurrentCase=No current case",
    })
    @Override
    public void run(Host host, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        configPanel.storeSettings();       
        Path imageDirPath = configPanel.getImageDirPath();
        List<String> errorList = new ArrayList<>();
        List<Content> emptyDataSources = new ArrayList<>();

        if (!imageDirPath.toFile().exists()) {
            // This can happen if the USB drive was selected in the panel, but
            // was ejected before pressing the NEXT button
            // TODO: Better ways to detect ejected USB drive?
            String msg = Bundle.LogicalImagerDSProcessor_imageDirPathNotFound(imageDirPath.toString());
            errorList.add(msg);
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }

        // Create the LogicalImager directory under ModuleDirectory
        String moduleDirectory = Case.getCurrentCase().getModuleDirectory();
        File logicalImagerDir = Paths.get(moduleDirectory, LOGICAL_IMAGER_DIR).toFile();
        if (!logicalImagerDir.exists() && !logicalImagerDir.mkdir()) {
            // create failed
            String msg = Bundle.LogicalImagerDSProcessor_failToCreateDirectory(logicalImagerDir);
            errorList.add(msg);
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }
        File dest = Paths.get(logicalImagerDir.toString(), imageDirPath.getFileName().toString()).toFile();
        if (dest.exists()) {
            // Destination directory already exists
            int showConfirmDialog = JOptionPane.showConfirmDialog(configPanel, 
                    Bundle.LogicalImagerDSProcessor_destinationDirectoryConfirmationMsg(dest.toString()),
                    Bundle.LogicalImagerDSProcessor_destinationDirectoryConfirmation(),
                    JOptionPane.YES_NO_OPTION);
            if (showConfirmDialog == YES_OPTION) {
                // Get unique dest directory
                String uniqueDirectory = imageDirPath.getFileName() + "_" + TimeStampUtils.createTimeStamp();
                dest = Paths.get(logicalImagerDir.toString(), uniqueDirectory).toFile();
            } else {
                String msg = Bundle.LogicalImagerDSProcessor_directoryAlreadyExists(dest.toString());
                errorList.add(msg);
                callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
                return;
            }
        }
        File src = imageDirPath.toFile();

        try {
            String deviceId = UUID.randomUUID().toString();
            String timeZone = Calendar.getInstance().getTimeZone().getID();
            run(deviceId, timeZone, src, dest, host,
                    progressMonitor, callback);
        } catch (NoCurrentCaseException ex) {
            String msg = Bundle.LogicalImagerDSProcessor_noCurrentCase();
            errorList.add(msg);
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
        }
    }

    /**
     * Adds a "Logical Imager" data source to the case database using a
     * background task in a separate thread and the given settings instead of
     * those provided by the selection and configuration panel. Returns as soon
     * as the background task is started and uses the callback object to signal
     * task completion and return results.
     *
     * @param deviceId        An ASCII-printable identifier for the device
     *                        associated with the data source that is intended
     *                        to be unique across multiple cases (e.g., a UUID).
     * @param timeZone        The time zone to use when processing dates and
     *                        times for the image, obtained from
     *                        java.util.TimeZone.getID.
     * @param src             The source directory of image.
     * @param dest            The destination directory to copy the source.
     * @param host            The host for this data source.
     * @param progressMonitor Progress monitor for reporting progress during
     *                        processing.
     * @param callback        Callback to call when processing is done.
     */
    private void run(String deviceId, String timeZone,
            File src, File dest, Host host,
            DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback
    ) throws NoCurrentCaseException {
        addLogicalImageTask = new AddLogicalImageTask(deviceId, timeZone, src, dest, host,
                progressMonitor, callback);
        Thread thread = new Thread(addLogicalImageTask);
        thread.start();
    }

    @Override
    public void cancel() {
        if (addLogicalImageTask != null) {
            addLogicalImageTask.cancelTask();
        }
    }

    /**
     * Resets the selection and configuration panel for this data source
     * processor.
     */
    @Override
    public void reset() {
        configPanel.reset();
    }

}
