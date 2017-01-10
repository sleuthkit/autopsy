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
package org.sleuthkit.autopsy.casemodule;

import java.util.logging.Level;
import org.sleuthkit.autopsy.corecomponentinterfaces.ProgressIndicator;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A task progress indicator that writes to the Autopsy application log.
 */
class LoggingProgressIndicator implements ProgressIndicator {
    
    private static Logger LOGGER = Logger.getLogger(LoggingProgressIndicator.class.getName());
    private String taskName;
    private int totalWorkUnits;
    
    LoggingProgressIndicator() {
        LOGGER = Logger.getLogger(LoggingProgressIndicator.class.getName());
    }
    
    LoggingProgressIndicator(Logger logger) {
        LOGGER = logger;
    }
        
    @Override
    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public void start(int totalWorkUnits) {
        this.totalWorkUnits = totalWorkUnits;
        LOGGER.log(Level.INFO, "{0} task started in determinate mode with {1} total work units", new Object[]{this.taskName, this.totalWorkUnits});
    }

    @Override
    public void start() {
        LOGGER.log(Level.INFO, "{0} task started in indeterminate mode", this.taskName);
    }

    @Override
    public void switchToIndeterminate() {
        this.totalWorkUnits = 0;
        LOGGER.log(Level.INFO, "{0} task switched to indeterminate mode", this.taskName);
    }

    @Override
    public void switchToDeterminate(int workUnitsCompleted, int totalWorkUnits) {
        this.totalWorkUnits = totalWorkUnits;
        LOGGER.log(Level.INFO, "{0} task switched to determinate mode, reporting {1} of {2} total work units completed", new Object[]{this.taskName, workUnitsCompleted, this.totalWorkUnits});
    }

    @Override
    public void progress(String message) {
        LOGGER.log(Level.INFO, "{0} task reported message '{0}'", message);
    }

    @Override
    public void progress(int workUnitsCompleted) {
        LOGGER.log(Level.INFO, "{0} task reported {1} of {2} total work units completed", new Object[]{this.taskName, workUnitsCompleted, this.totalWorkUnits});
    }

    @Override
    public void progress(String message, int workUnitsCompleted) {
        LOGGER.log(Level.INFO, "{0} task reported message '{0}' with {1} of {2} total work units completed", new Object[]{this.taskName, message, workUnitsCompleted, this.totalWorkUnits});
    }

    @Override
    public void finish() {
        LOGGER.log(Level.INFO, "{0} task reported finished", this.taskName);
    }

}
