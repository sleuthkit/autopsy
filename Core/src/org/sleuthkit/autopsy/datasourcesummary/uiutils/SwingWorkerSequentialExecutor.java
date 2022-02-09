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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.swing.SwingWorker;

/**
 * Runs a list of swing workers in sequential order. Also, provides the ability
 * to reset or cancel a run.
 *
 * Based on:
 * https://stackoverflow.com/questions/31580805/java-swingworker-one-after-another-and-update-gui
 */
public class SwingWorkerSequentialExecutor {

    private final ExecutorService executorService = Executors.newFixedThreadPool(1, new ThreadFactoryBuilder().setNameFormat("SwingWorkerSequentialExecutor-%d").build());
    private List<? extends SwingWorker<?, ?>> workers = Collections.emptyList();
    private List<Future<?>> futures = Collections.emptyList();

    /**
     * Cancels currently running operations and starts running the new list of
     * swing workers.
     *
     * @param submittedWorkers The list of submitted swing workers.
     */
    public synchronized void submit(List<? extends SwingWorker<?, ?>> submittedWorkers) {
        // cancel currently running operations
        cancelRunning();

        // if no workers, there is nothing to run
        if (submittedWorkers == null) {
            return;
        }

        this.workers = new ArrayList<>(submittedWorkers);

        // start running the workers and capture the futures if there is a need to cancel them.
        this.futures = this.workers.stream()
                .map((w) -> executorService.submit(w))
                .collect(Collectors.toList());
    }

    /**
     * Cancels currently running items.
     */
    public synchronized void cancelRunning() {
        futures.forEach((f) -> f.cancel(true));
        workers = Collections.emptyList();
        futures = Collections.emptyList();
    }

    /**
     * Returns whether or not any of the workers provided are still running.
     *
     * @return Whether or not any of the submitted workers are still running.
     */
    public synchronized boolean isRunning() {
        // borrowed from this stack overflow answer: 
        // https://stackoverflow.com/a/33845730

        for (Future<?> future : futures) {
            if (!future.isDone()) {
                return true;
            }
        }

        return false;
    }
}
