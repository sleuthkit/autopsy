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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * A Cellebrite XML report file data source processor that implements the
 * DataSourceProcessor service provider interface to allow integration with the
 * add data source wizard. It also provides a run method overload to allow it to
 * be used independently of the wizard.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class CellebriteXMLProcessor implements DataSourceProcessor {

    private static final String DATA_SOURCE_TYPE = "Cellebrite XML";
    private static final List<String> CELLEBRITE_EXTS = Arrays.asList(new String[]{".xml"});
    private static final String CELLEBRITE_DESC = "Cellebrite XML Files (*.xml)";
    private static final GeneralFilter xmlFilter = new GeneralFilter(CELLEBRITE_EXTS, CELLEBRITE_DESC);
    private static final List<FileFilter> filtersList = new ArrayList<>();
    private final CellebriteXMLFilePanel configPanel;
    private AddCellebriteXMLTask addCellebriteXMLTask;

    static {
        filtersList.add(xmlFilter);
    }

    /**
     * Gets the file extensions supported by this data source processor as a
     * list of file filters.
     *
     * @return List<FileFilter> List of FileFilter objects
     */
    public static final List<FileFilter> getFileFilterList() {
        return filtersList;
    }

    /*
     * Constructs a Cellebrite XML report file data source processor that
     * implements the DataSourceProcessor service provider interface to allow
     * integration with the add data source wizard. It also provides a run
     * method overload to allow it to be used independently of the wizard.
     */
    public CellebriteXMLProcessor() {
        configPanel = CellebriteXMLFilePanel.createInstance(CellebriteXMLProcessor.class.getName(), filtersList);
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
        String deviceId = UUID.randomUUID().toString();
        run(deviceId, deviceId, configPanel.getImageFilePath(), configPanel.isHandsetFile(), progressMonitor, callback);
    }

    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the given settings instead of those provided by the
     * selection and configuration panel. Returns as soon as the background task
     * is started and uses the callback object to signal task completion and
     * return results.
     *
     * @param deviceId                 An ASCII-printable identifier for the
     *                                 device associated with the data source
     *                                 that is intended to be unique across
     *                                 multiple cases (e.g., a UUID).
     * @param rootVirtualDirectoryName The name to give to the virtual directory
     *                                 that will represent the data source. Pass
     *                                 the empty string to get a default name of
     *                                 the form: LogicalFileSet[N]
     * @param cellebriteXmlFilePath    Path to a Cellebrite report XML file.
     * @param isHandsetFile            Indicates whether the XML file is for a
     *                                 handset or a SIM.
     * @param progressMonitor          Progress monitor for reporting progress
     *                                 during processing.
     * @param callback                 Callback to call when processing is done.
     */
    public void run(String deviceId, String rootVirtualDirectoryName, String cellebriteXmlFilePath, boolean isHandsetFile, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        AddCellebriteXMLTask.CellebriteInputType inputType;
        if (isHandsetFile) {
            inputType = AddCellebriteXMLTask.CellebriteInputType.handset;
        } else {
            inputType = AddCellebriteXMLTask.CellebriteInputType.SIM;
        }
        addCellebriteXMLTask = new AddCellebriteXMLTask(deviceId, rootVirtualDirectoryName, cellebriteXmlFilePath, inputType, progressMonitor, callback);
        new Thread(addCellebriteXMLTask).start();
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
        addCellebriteXMLTask.cancelTask();
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
