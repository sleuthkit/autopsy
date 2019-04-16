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

/**
 * An interface for progress indicators. A progress indicator can run in
 * determinate mode (the total number of work units to be completed is known) or
 * indeterminate mode. Switching back and forth between the two modes is
 * supported. Starting, finishing, and starting again is supported.
 */
public interface ProgressIndicator {

    /**
     * Starts the progress indicator in determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message        The initial progress message.
     * @param totalWorkUnits The total number of work units.
     */
    void start(String message, int totalWorkUnits);

    /**
     * Starts the progress indicator in indeterminate mode (the total number of
     * work units to be completed is unknown).
     *
     * @param message The initial progress message.
     */
    void start(String message);

    /**
     * Switches the progress indicator to indeterminate mode (the total number
     * of work units to be completed is unknown).
     *
     * @param message The initial progress message.
     */
    void switchToIndeterminate(String message);

    /**
     * Switches the progress indicator to determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message            The initial progress message.
     * @param workUnitsCompleted The number of work units completed so far.
     * @param totalWorkUnits     The total number of work units to be completed.
     */
    void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits);

    /**
     * Updates the progress indicator with a progress message.
     *
     * @param message The progress message.
     */
    void progress(String message);

    /**
     * Updates the progress indicator with the number of work units completed so
     * far when in determinate mode (the total number of work units to be
     * completed is known).
     *
     * @param workUnitsCompleted Number of work units completed so far.
     */
    void progress(int workUnitsCompleted);

    /**
     * Updates the progress indicator with a progress message and the number of
     * work units completed so far when in determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message            The progress message.
     * @param workUnitsCompleted Number of work units completed so far.
     */
    void progress(String message, int workUnitsCompleted);

    /**
     * If the progress indicator supports cancelling the underlying task, sets a
     * cancelling message and causes the progress indicator to no longer accept
     * updates unless start is called again.
     *
     * The default implementation assumes that cancelling the underlying task is
     * not supported.
     *
     * @param cancellingMessage The cancelling messages.
     */
    default void setCancelling(String cancellingMessage) {
        /*
         * The default implementation assumes that cancelling the underlying
         * task is not supported.
         */
    }

    /**
     * Finishes the progress indicator when the task is completed.
     */
    void finish();

}
