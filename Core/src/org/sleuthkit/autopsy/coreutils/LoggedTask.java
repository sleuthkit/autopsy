/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-14 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.concurrent.Task;
import org.openide.util.Cancellable;

/**
 * extension of Task that logs state changes
 */
public abstract class LoggedTask<T> extends Task<T> implements Cancellable {

    private static final Logger LOGGER = Logger.getLogger(LoggedTask.class.getName());

    private final boolean logStateChanges;

    public LoggedTask(String taskName, boolean logStateChanges) {
        updateTitle(taskName);
        this.logStateChanges = logStateChanges;
    }

    @Override
    protected void cancelled() {
        super.cancelled();
        if (logStateChanges) {
            // LOGGER.log(Level.WARNING, "{0} cancelled!", getTitle());
        }
    }

    @Override
    protected void failed() {
        super.failed();
        LOGGER.log(Level.SEVERE, getTitle() + " failed!", getException()); //NON-NLS

    }

    @Override
    protected void scheduled() {
        super.scheduled();
        if (logStateChanges) {
            // LOGGER.log(Level.INFO, "{0} scheduled", getTitle());
        }
    }

    @Override
    protected void succeeded() {
        super.succeeded();
        try {
            get();
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.log(Level.SEVERE, getTitle() + " threw unexpected exception: ", ex); // NON-NLS
        }
        if (logStateChanges) {
            // LOGGER.log(Level.INFO, "{0} succeeded", getTitle());
        }
    }
}
