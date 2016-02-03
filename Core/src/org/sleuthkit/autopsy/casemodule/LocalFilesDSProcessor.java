/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2016  Basis Technology Corp.
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;

/**
 * A local/logical files and/or directories data source processor with a
 * configuration panel. This data source processor implements the
 * DataSourceProcessor service provider interface to allow integration with the
 * add data source wizard. It also provides a run method overload to allow it to
 * be used independently of the configuration UI.
 */
@ServiceProvider(service = DataSourceProcessor.class)
public class LocalFilesDSProcessor implements DataSourceProcessor {

    private static final String dsType = NbBundle.getMessage(LocalFilesDSProcessor.class, "LocalFilesDSProcessor.dsType");
    private final LocalFilesPanel configPanel;
    private String deviceId;
    private List<String> paths;
    private boolean configured;
    private AddLocalFilesTask addFilesTask;

    /**
     * Constructs a local/logical files and/or directories data source processor
     * with a configuration panel. This data source processor implements the
     * DataSourceProcessor service provider interface to allow integration with
     * the add data source wizard. It also provides a run method overload to
     * allow it to be used independently of the configuration UI.
     */
    public LocalFilesDSProcessor() {
        configPanel = LocalFilesPanel.getDefault();
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
        configPanel.select();
        return configPanel;
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
        if (!configured) {
            if (null == deviceId) {
                deviceId = UUID.randomUUID().toString();
            }
            paths = Arrays.asList(configPanel.getContentPaths().split(LocalFilesPanel.FILES_SEP));
            configured = true;
        }
        addFilesTask = new AddLocalFilesTask(deviceId, paths, monitor, cbObj);
        new Thread(addFilesTask).start();
    }

    /**
     * Runs the data source processor in a separate thread without requiring use
     * the configuration panel.
     *
     * @param dataSourceId An ASCII-printable identifier for the device
     *                     associated with the data source, in this case a group
     *                     of local/logical files, that is intended to be unique
     *                     across multiple cases (e.g., a UUID).
     * @param paths        A list of local/logical file and/or directory paths.
     * @param monitor      Progress monitor to report progress during
     *                     processing.
     * @param cbObj        Callback to call when processing is done.
     */
    public void run(String deviceId, List<String> paths, DataSourceProcessorProgressMonitor monitor, DataSourceProcessorCallback cbObj) {
        this.deviceId = deviceId;
        this.paths = new ArrayList<>(paths);
        configured = true;
        run(monitor, cbObj);
    }

    /**
     * Cancels the processing of the data source.
     */
    @Override
    public void cancel() {
        addFilesTask.cancelTask();
    }

    /**
     * Resets the configuration of this data source processor, including its
     * configuration panel.
     */
    @Override
    public void reset() {
        configPanel.reset();
        paths = null;
        configured = false;
    }

    /**
     * Sets the configuration of the data source processor without using the
     * configuration panel. The data source processor will assign a UUID to the
     * data source and will use the time zone of the machine executing this code
     * when when processing dates and times for the image.
     *
     * @param paths A list of local/logical file and/or directory paths.
     *
     * @deprecated Use the run method instead.
     */
    @Deprecated
    public void setDataSourceOptions(String paths) {
        this.paths = Arrays.asList(configPanel.getContentPaths().split(LocalFilesPanel.FILES_SEP));
        configured = true;
    }

}
