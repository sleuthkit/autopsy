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

/**
 * An GUI agnostic DataSourceProcessorProgressMonitor interface for
 * DataSourceProcesssors to indicate progress. It models after a JProgressbar
 * though it could use any underlying implementation (or NoOps)
 */
public interface DataSourceProcessorProgressMonitor {

    /**
     * Identify if progress will be indeterminate or not
     * 
     * @param indeterminate true if progress bar should not show steps
     */
    void setIndeterminate(boolean indeterminate);

    /**
     * Increment the progress bar if it is determinate
     * @param progress How much progress has happened. Must be smaller than value passed to setProgressMax()
     */
    void setProgress(int progress);
    
    /**
     * Maximum value for a determinate progress bar. 
     * @param max Max value that will be used 
     */
    default void setProgressMax(final int max) { }

    /**
     * Set the text to be displayed to the user.
     * @param text Text to display
     */
    void setProgressText(String text);
}
