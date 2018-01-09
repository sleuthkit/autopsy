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
package org.sleuthkit.autopsy.corecomponentinterfaces;

import java.awt.EventQueue;
import java.util.List;
import org.sleuthkit.datamodel.Content;

/**
 * An abstract base class for callback objects to be given to data source
 * processors for use by the background tasks that add data sources to a case
 * database. The callback objects are used to signal task completion and return
 * results.
 *
 * Concrete implementations of DataSourceProcessorCallback should override
 * either the done method or the doneEDT method, but not both.
 */
public abstract class DataSourceProcessorCallback {

    public enum DataSourceProcessorResult {

        /**
         * No errors occurred while ading the data source to the case database.
         */
        NO_ERRORS,
        /**
         * Critical errors occurred while ading the data source to the case
         * database. The data source was not added to the case database.
         */
        CRITICAL_ERRORS,
        /**
         * Non-critical errors occurred while adding the data source to the case
         * database. The data source was added to the database, but the data
         * source may have been corrupted in some way.
         */
        NONCRITICAL_ERRORS
    };

    /**
     * Called by a data source processor when it is done adding a data source to
     * the case database, this method adds a task to call the doneEDT method to
     * the EDT task queue.
     *
     * IMPORTANT: Concrete implementations of DataSourceProcessorCallback should
     * override this method if the callback SHOULD NOT be done in the EDT.
     *
     * @param result         Result code.
     * @param errList        List of error messages, possibly empty.
     * @param newDataSources A list of the data sources added, empty if critical
     *                       errors occurred or processing was successfully
     *                       cancelled.
     */
    public void done(DataSourceProcessorResult result, List<String> errList, List<Content> newDataSources) {
        final DataSourceProcessorResult resultf = result;
        final List<String> errListf = errList;
        final List<Content> newContentsf = newDataSources;
        EventQueue.invokeLater(() -> {
            doneEDT(resultf, errListf, newContentsf);
        });
    }

    /**
     * Called by a data source processor when it is done adding a data source to
     * the case database, if the default done method has not been overridden.
     *
     * IMPORTANT: Concrete implementations of DataSourceProcessorCallback should
     * override the done method and provide an implementation of this method
     * that throws an UnsupportedOperationException if the callback SHOULD NOT
     * be done in the EDT.
     *
     * @param result         Result code.
     * @param errList        List of error messages, possibly empty.
     * @param newDataSources A list of the data sources added, empty if critical
     *                       errors occurred or processing was successfully
     *                       cancelled.
     */
    abstract public void doneEDT(DataSourceProcessorResult result, List<String> errList, List<Content> newDataSources);
};
