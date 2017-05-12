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

import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * A "silent" or "null" progress indicator.
 */
public class SilentProgressIndicator implements ProgressIndicator {

    @Override
    public void start(String message, int totalWorkUnits) {
    }

    @Override
    public void start(String message) {
    }

    @Override
    public void switchToIndeterminate(String message) {
    }

    @Override
    public void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
    }

    @Override
    public void progress(String message) {
    }

    @Override
    public void progress(int workUnitsCompleted) {
    }

    @Override
    public void progress(String message, int workUnitsCompleted) {
    }

    @Override
    public void finish() {
    }
    
}
