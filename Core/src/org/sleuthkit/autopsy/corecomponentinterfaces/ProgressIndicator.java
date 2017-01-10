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

/**
 * An interface for task progress indicators. A progress indicator can run in
 * determinate mode (the total number of work units to be completed is known) or
 * indeterminate mode. Switching back and forth between the two modes is
 * supported.
 */
public interface ProgressIndicator {

    /**
     * Sets the name of the task for which progress is being indicated. This
     * should be set before the task is started and should not be changed.
     *
     * @param taskName The task name.
     */
    void setTaskName(String taskName);

    /**
     * Starts the progress indicator in determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param totalWorkUnits The total number of work units.
     */
    void start(int totalWorkUnits);

    /**
     * Starts the progress indicator in indeterminate mode (the total number of
     * work units to be completed is unknown).
     */
    void start();

    /**
     * Switches the progress indicator to indeterminate mode (the total number
     * of work units to be completed is unknown).
     */
    public void switchToIndeterminate();

    /**
     * Switches the progress indicator to determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param workUnitsCompleted The number of work units completed so far.
     * @param totalWorkUnits     The total number of work units to be completed.
     */
    public void switchToDeterminate(int workUnitsCompleted, int totalWorkUnits);

    /**
     * Updates the progress indicator with a progress message.
     *
     * @param message The progress message.
     */
    public void progress(String message);

    /**
     * Updates the progress indicator with the number of work units completed so
     * far when in determinate mode (the total number of work units to be
     * completed is known).
     *
     * @param workUnitsCompleted Number of work units completed so far.
     */
    public void progress(int workUnitsCompleted);

    /**
     * Updates the progress indicator with a progress message and the number of
     * work units completed so far when in determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message            The progress message.
     * @param workUnitsCompleted Number of work units completed so far.
     */
    public void progress(String message, int workUnitsCompleted);

    /**
     * Finishes the progress indicator when the task is completed.
     */
    void finish();
    
}
