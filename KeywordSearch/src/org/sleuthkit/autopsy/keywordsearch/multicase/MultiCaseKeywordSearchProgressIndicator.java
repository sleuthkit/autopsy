/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch.multicase;

import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import org.sleuthkit.autopsy.progress.ProgressIndicator;

/**
 * A progress indicator that updates a JProgressBar.
 */
final class MultiCaseKeywordSearchProgressIndicator implements ProgressIndicator {

    private final JProgressBar progress;

    /**
     * Construct a new JProgressIndicator
     *
     * @param progressBar the JProgressBar you want this indicator to update
     */
    MultiCaseKeywordSearchProgressIndicator(JProgressBar progressBar) {
        progress = progressBar;
        progress.setStringPainted(true);
    }

    /**
     * Start showing progress in the progress bar.
     *
     * @param message the message to be displayed on the progress bar, null to
     *                display percent complete
     * @param max     The total number of work units to be completed.
     */
    @Override
    public void start(String message, int max) {
        SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(false);
            progress.setMinimum(0);
            progress.setString(message);  //the message
            progress.setValue(0);
            progress.setMaximum(max);
            progress.setVisible(true);
        });
    }

    /**
     * Start showing progress in the progress bar.
     *
     * @param message the message to be displayed on the progress bar, null to
     *                display percent complete
     */
    @Override
    public void start(String message) {
        SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(true);
            progress.setMinimum(0);
            progress.setString(message);
            progress.setValue(0);
            progress.setVisible(true);
        });
    }

    /**
     * Switches the progress indicator to indeterminate mode (the total number
     * of work units to be completed is unknown).
     *
     * @param message the message to be displayed on the progress bar, null to
     *                display percent complete
     */
    @Override
    public void switchToIndeterminate(String message) {
        SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(true);
            progress.setString(message);
        });
    }

    /**
     * Switches the progress indicator to determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message the message to be displayed on the progress bar, null to
     *                display percent complete
     * @param current The number of work units completed so far.
     * @param max     The total number of work units to be completed.
     */
    @Override
    public void switchToDeterminate(String message, int current, int max) {
        SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(false);
            progress.setMinimum(0);
            progress.setString(message);
            progress.setValue(current);
            progress.setMaximum(max);
        });
    }

    /**
     * Updates the progress indicator with a progress message.
     *
     * @param message the message to be displayed on the progress bar, null to
     *                display percent complete
     */
    @Override
    public void progress(String message) {
        SwingUtilities.invokeLater(() -> {
            progress.setString(message);
        });
    }

    /**
     * Updates the progress indicator with the number of work units completed so
     * far when in determinate mode (the total number of work units to be
     * completed is known).
     *
     * @param current Number of work units completed so far.
     */
    @Override
    public void progress(int current) {
        SwingUtilities.invokeLater(() -> {
            progress.setValue(current);
        });
    }

    /**
     * Updates the progress indicator with a progress message and the number of
     * work units completed so far when in determinate mode (the total number of
     * work units to be completed is known).
     *
     * @param message the message to be displayed on the progress bar, null to
     *                display percent complete
     * @param current Number of work units completed so far.
     */
    @Override
    public void progress(String message, int current) {
        SwingUtilities.invokeLater(() -> {
            progress.setString(message);
            progress.setValue(current);
        });
    }

    /**
     * Finishes the progress indicator when the task is completed.
     */
    @Override
    public void finish() {
        SwingUtilities.invokeLater(() -> {
            progress.setVisible(false);
        });
    }

}
