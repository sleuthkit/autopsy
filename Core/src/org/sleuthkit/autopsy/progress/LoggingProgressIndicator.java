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
package org.sleuthkit.autopsy.progress;

import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A progress indicator that writes progress to the Autopsy application log.
 */
public final class LoggingProgressIndicator implements ProgressIndicator {

    private final Logger LOGGER = Logger.getLogger(LoggingProgressIndicator.class.getName());
    private int totalWorkUnits;

    @Override
    public void start(String message, int totalWorkUnits) {
        this.totalWorkUnits = totalWorkUnits;
        LOGGER.log(Level.INFO, "{0} started, {1} total work units", new Object[]{message, this.totalWorkUnits});
    }

    @Override
    public void start(String message) {
        LOGGER.log(Level.INFO, "{0}", message);
    }

    @Override
    public void switchToIndeterminate(String message) {
        this.totalWorkUnits = 0;
        LOGGER.log(Level.INFO, "{0}", message);
    }

    @Override
    public void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
        this.totalWorkUnits = totalWorkUnits;
        LOGGER.log(Level.INFO, "{0}, {1} of {2} total work units completed", new Object[]{message, workUnitsCompleted, this.totalWorkUnits});
    }

    @Override
    public void progress(String message) {
        LOGGER.log(Level.INFO, "{0}", message);
    }

    @Override
    public void progress(int workUnitsCompleted) {
        LOGGER.log(Level.INFO, "{0} of {1} total work units completed", new Object[]{workUnitsCompleted, this.totalWorkUnits});
    }

    @Override
    public void progress(String message, int workUnitsCompleted) {
        LOGGER.log(Level.INFO, "{0}, {1} of {2} total work units completed", new Object[]{message, workUnitsCompleted, this.totalWorkUnits});
    }

    @Override
    public void finish() {
        LOGGER.log(Level.INFO, "Finished");
    }

}
