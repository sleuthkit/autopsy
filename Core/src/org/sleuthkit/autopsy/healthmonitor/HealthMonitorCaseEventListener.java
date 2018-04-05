/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.healthmonitor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Listener for case events
 */
final class HealthMonitorCaseEventListener implements PropertyChangeListener {

    private final ExecutorService jobProcessingExecutor;
    private static final String CASE_EVENT_THREAD_NAME = "Health-Monitor-Event-Listener-%d";

    HealthMonitorCaseEventListener() {
        jobProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(CASE_EVENT_THREAD_NAME).build());
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

        switch (Case.Events.valueOf(evt.getPropertyName())) {

            case CURRENT_CASE:
                if ((null == evt.getNewValue()) && (evt.getOldValue() instanceof Case)) {
                    // When a case is closed, write the current metrics to the database
                    jobProcessingExecutor.submit(new ServicesHealthMonitor.DatabaseWriteTask());
                }
                break;
        }
    }
}
