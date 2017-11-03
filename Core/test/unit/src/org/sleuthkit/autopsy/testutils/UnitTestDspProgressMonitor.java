/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.testutils;

import javax.annotation.concurrent.Immutable;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;

/**
 * A data source processor progress monitor for unit testing.
 */
@Immutable
public class UnitTestDspProgressMonitor implements DataSourceProcessorProgressMonitor {

    /**
     * Switches the progress indicator to indeterminate mode (the total number
     * of work units to be completed is unknown) or determinate mode (the total
     * number of work units to be completed is unknown).
     *
     * @param indeterminate True for indeterminate mode, false for determinate
     *                      mode.
     */
    @Override
    public void setIndeterminate(final boolean indeterminate) {
    }

    /**
     * Updates the progress indicator with the number of work units completed so
     * far when in determinate mode (the total number of work units to be
     * completed is known).
     *
     * @param workUnitsCompleted Number of work units completed so far.
     */
    @Override
    public void setProgress(final int workUnitsCompleted) {
    }

    /**
     * Updates the progress indicator with a progress message.
     *
     * @param message The progress message.
     */
    @Override
    public void setProgressText(final String message) {
    }

}
