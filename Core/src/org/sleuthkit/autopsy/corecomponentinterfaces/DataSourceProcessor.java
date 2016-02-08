/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponentinterfaces;

import javax.swing.JPanel;

/**
 * Interface implemented by classes that add data sources of a particular type
 * (e.g., images, local disks, virtual directories of local/logical files, etc.)
 * to a case database. A data source processor is NOT responsible for analyzing
 * the data source (running ingest modules on the data source and its contents).
 *
 * Data source processors plug in to the add data source wizard and should
 * provide a JPanel to allow a user to select a data source and do any
 * configuration the data source processor may require. The panel should support
 * addition of the add data source wizard as a property change listener and
 * should fire DSP_PANEL_EVENT property changes to communicate with the wizard.
 *
 * Data source processors should perform all processing on a separate thread,
 * reporting results using a callback object.
 */
public interface DataSourceProcessor {

    /**
     * Property change events fired to communicate with the add data source
     * wizard.
     *
     * TODO (AUT-1891): What is needed is a single PANEL_CHANGED event so that
     * the wizard can call isPanelValid and set the enabling and focus of the
     * next button based on the result.
     */
    enum DSP_PANEL_EVENT {

        /**
         * Fire this event when the user changes something in the panel to
         * notify the add data source wizard that it should call isPanelValid.
         */
        UPDATE_UI,
        /**
         * Fire this event to make the add data source wizard move focus to the
         * next button.
         */
        FOCUS_NEXT
    };

    /**
     * Gets a string that describes the type of data sources this processor is
     * able to process.
     *
     * @return A string suitable for display in a data source processor
     *         selection UI component (e.g., a combo box).
     */
    String getDataSourceType();

    /**
     * Gets the panel that allows a user to select a data source and do any
     * configuration the data source processor may require.
     *
     * @return A JPanel less than 544 pixels wide and 173 pixels high.
     */
    JPanel getPanel();

    /**
     * Indicates whether the settings in the panel are valid and complete.
     *
     * @return True if the settings are valid and complete and the processor is
     *         ready to have its run method called; false otherwise.
     */
    boolean isPanelValid();

    /**
     * Adds a data source to the case database using a separate thread and the
     * settings provided by the panel. Returns as soon as the background task is
     * started and uses the callback object to signal task completion and return
     * results.
     *
     * NOTE: This method should not be called unless isPanelValid returns true.
     *
     * @param progressMonitor Progress monitor for reporting progress during
     *                        processing.
     * @param callback        Callback to call when processing is done.
     */
    void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback);

    /**
     * Requests cancellation of the data source processing task after it is
     * started using the run method. Cancellation is not guaranteed.
     */
    void cancel();

    /**
     * Resets the panel.
     */
    void reset();
}
