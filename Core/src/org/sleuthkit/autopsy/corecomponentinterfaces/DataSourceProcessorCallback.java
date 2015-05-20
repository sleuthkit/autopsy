/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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
 * Abstract class for a callback for a DataSourceProcessor.
 *
 * Ensures that DSP invokes the caller overridden method, doneEDT(), in the EDT
 * thread.
 *
 */
public abstract class DataSourceProcessorCallback {

    public enum DataSourceProcessorResult {
        NO_ERRORS,  ///< No errors were encountered while ading the data source
        CRITICAL_ERRORS, ///< No data was added to the database. There were fundamental errors processing the data (such as no data or system failure).  
        NONCRITICAL_ERRORS, ///< There was data added to the database, but there were errors from data corruption or a small number of minor issues. 
    };

    
    /**
     * Called by a DSP implementation when it is done adding a data source
     * to the database. Users of the DSP can override this method if they do
     * not want to be notified on the EDT.  Otherwise, this method will call 
     * doneEDT() with the same arguments. 
     * @param result Code for status
     * @param errList List of error strings
     * @param newContents List of root Content objects that were added to database. Typically only one is given.
     */
    public void done(DataSourceProcessorResult result, List<String> errList, List<Content> newContents) {

        final DataSourceProcessorResult resultf = result;
        final List<String> errListf = errList;
        final List<Content> newContentsf = newContents;

        // Invoke doneEDT() that runs on the EDT .
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                doneEDT(resultf, errListf, newContentsf);
            }
        });
    }

    /**
     * Called by done() if the default implementation is used.  Users of DSPs
     * that have UI updates to do after the DSP is finished adding the DS can
     * implement this method to receive the updates on the EDT.
     *
     * @param result Code for status
     * @param errList List of error strings
     * @param newContents List of root Content objects that were added to database. Typically only one is given.
     */ 
    public abstract void doneEDT(DataSourceProcessorResult result, List<String> errList, List<Content> newContents);
};
