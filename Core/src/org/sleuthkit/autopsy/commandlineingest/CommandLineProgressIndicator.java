/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commandlineingest;

import org.sleuthkit.autopsy.progress.LoggingProgressIndicator;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * Wrap a LoggingProgressIndicator to support both logging to the log file
 * and the command line.
 */
public class CommandLineProgressIndicator implements ProgressIndicator {

    private final LoggingProgressIndicator loggingIndicator = new LoggingProgressIndicator();
    
    @Override
    public void start(String message, int totalWorkUnits) {
        loggingIndicator.start(message, totalWorkUnits);
        System.out.println(message);
    }

    @Override
    public void start(String message) {
        loggingIndicator.start(message);
        System.out.println(message);
    }

    @Override
    public void switchToIndeterminate(String message) {
        loggingIndicator.switchToIndeterminate(message);
    }

    @Override
    public void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
       loggingIndicator.switchToDeterminate(message, workUnitsCompleted, totalWorkUnits);
    }

    @Override
    public void progress(String message) {
        loggingIndicator.progress(message);
        System.out.println(message);
    }

    @Override
    public void progress(int workUnitsCompleted) {
        loggingIndicator.progress(workUnitsCompleted);
    }

    @Override
    public void progress(String message, int workUnitsCompleted) {
        loggingIndicator.progress(message);
        System.out.println(message);
    }

    @Override
    public void finish() {
       loggingIndicator.finish();
    }  
}
