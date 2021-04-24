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
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.datamodel.Host;

/**
 * Interface implemented by classes that add data sources of a particular type
 * (e.g., images, local disks, virtual directories of local/logical files) to a
 * case database. A data source processor is NOT responsible for analyzing the
 * data source, i.e., running ingest modules on the data source and its
 * contents.
 *
 * Data source processors plug in to the add data source wizard and should
 * provide a UI panel to allow a user to select a data source and do any
 * configuration required by the data source processor. The selection and
 * configuration panel should support addition of the add data source wizard as
 * a property change listener and should fire DSP_PANEL_EVENT property change
 * events to communicate with the wizard.
 *
 * Data source processors should perform all processing in a background task in
 * a separate thread, reporting results using a callback object.
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
         * This event is fired when the user changes something in the selection
         * and configuration panel. It notifies the add data source wizard that
         * it should call isPanelValid.
         */
        UPDATE_UI,
        /**
         * This event is fired to make the add data source wizard move focus to
         * the wizard's next button.
         * @deprecated Use UPDATE_UI.
         */
        @Deprecated
        FOCUS_NEXT
    };

    /**
     * Gets a string that describes the type of data sources this processor is
     * able to add to the case database. The string is suitable for display in a
     * type selection UI component (e.g., a combo box).
     *
     * @return A data source type display string for this data source processor.
     */
    String getDataSourceType();

    /**
     * Gets the panel that allows a user to select a data source and do any
     * configuration required by the data source. The panel is less than 544
     * pixels wide and less than 173 pixels high.
     *
     * @return A selection and configuration panel for this data source
     *         processor.
     */
    JPanel getPanel();

    /**
     * Indicates whether the settings in the selection and configuration panel
     * are valid and complete.
     *
     * @return True if the settings are valid and complete and the processor is
     *         ready to have its run method called, false otherwise.
     */
    boolean isPanelValid();

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
    void run(DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback);
    
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
    default void run(Host host, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        run(progressMonitor, callback);
    }
    
    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the settings provided by the selection and
     * configuration panel. Files found during ingest will be sent directly to
     * the IngestStream provided. Returns as soon as the background task is
     * started. The background task uses a callback object to signal task
     * completion and return results.
     *
     * This method should not be called unless isPanelValid returns true, and
     * should only be called for DSPs that support ingest streams. The ingest
     * settings must be complete before calling this method.
     *
     * @param settings The ingest job settings.
     * @param progress Progress monitor that will be used by the background task
     *                 to report progress.
     * @param callBack Callback that will be used by the background task to
     *                 return results.
     */
    default void runWithIngestStream(IngestJobSettings settings, DataSourceProcessorProgressMonitor progress,
            DataSourceProcessorCallback callBack) {
        throw new UnsupportedOperationException("Streaming ingest not supported for this data source processor");
    }
    
    /**
     * Adds a data source to the case database using a background task in a
     * separate thread and the settings provided by the selection and
     * configuration panel. Files found during ingest will be sent directly to
     * the IngestStream provided. Returns as soon as the background task is
     * started. The background task uses a callback object to signal task
     * completion and return results.
     *
     * This method should not be called unless isPanelValid returns true, and
     * should only be called for DSPs that support ingest streams. The ingest
     * settings must be complete before calling this method.
     *
     * @param host     Host for this data source.
     * @param settings The ingest job settings.
     * @param progress Progress monitor that will be used by the background task
     *                 to report progress.
     * @param callBack Callback that will be used by the background task to
     *                 return results.
     */
    default void runWithIngestStream(Host host, IngestJobSettings settings, DataSourceProcessorProgressMonitor progress,
            DataSourceProcessorCallback callBack) {
        runWithIngestStream(settings, progress, callBack);
    }

    /**
     * Check if this DSP supports ingest streams.
     *
     * @return True if this DSP supports an ingest stream, false otherwise.
     */
    default boolean supportsIngestStream() {
        return false;
    }

    /**
     * Requests cancellation of the background task that adds a data source to
     * the case database, after the task is started using the run method. This
     * is a "best effort" cancellation, with no guarantees that the case
     * database will be unchanged. If cancellation succeeded, the list of new
     * data sources returned by the background task will be empty.
     */
    void cancel();

    /**
     * Resets the selection and configuration panel for this data source
     * processor.
     */
    void reset();
}
