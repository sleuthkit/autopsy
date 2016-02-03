/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2016 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * A runnable that adds a set of local/logical files and/or directories to the
 * case database, grouped under a virtual directory that serves as the data
 * source.
 */
class AddLocalFilesTask implements Runnable {

    private final Logger logger = Logger.getLogger(AddLocalFilesTask.class.getName());
    private final String deviceId;
    private final List<String> paths;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callback;
    private final List<Content> dataSources;
    private final List<String> errors;
    private volatile boolean cancelRequested;

    /**
     * Constructs a runnable that adds a set of local/logical files and/or
     * directories to the case database, grouped under a virtual directory that
     * serves as the data source.
     *
     * @param deviceId    An ASCII-printable identifier for the device
     *                        associated with the data source, in this case a
     *                        gropu of local/logical files, that is intended to
     *                        be unique across multiple cases (e.g., a UUID).
     * @param paths           A list of paths of local/logical files and/or
     *                        directories.
     * @param progressMonitor Progress monitor to report progress during
     *                        processing.
     * @param callback        Callback to call when processing is done.
     */
    AddLocalFilesTask(String deviceId, List<String> paths, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        this.deviceId = deviceId;
        this.paths = paths;
        this.callback = callback;
        this.progressMonitor = progressMonitor;
        this.errors = new ArrayList<>();
        this.dataSources = Collections.synchronizedList(new ArrayList<Content>());
    }

    /**
     * Adds a set of local/logical files and/or directories to the case
     * database, grouped under a virtual directory that serves as the data
     * source.
     */
    @Override
    public void run() {
        ProgressUpdater progressUpdater = new ProgressUpdater();
        try {
            progressMonitor.setIndeterminate(true);
            progressMonitor.setProgress(0);
            FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
            VirtualDirectory dataSource = fileManager.addLocalFilesDirs(paths, progressUpdater);
            dataSources.add(dataSource);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error adding logical files %s", paths), ex); //NON-NLS
            errors.add(ex.getMessage());
        }

        if (!cancelRequested && errors.isEmpty()) {
            progressMonitor.setProgress(100);
            progressMonitor.setIndeterminate(false);
        }

        if (!cancelRequested) {
            DataSourceProcessorCallback.DataSourceProcessorResult result;
            if (!errors.isEmpty()) {
                result = DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
            } else {
                result = DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS;
            }
            callback.done(result, errors, dataSources);
        }
    }

    /**
     * Sets a cancel requested flag. Does not cancel adding the local/logical
     * files and/or directories, but will cancel the return of the data source
     * via the callback, if the flag is set before the run method completes.
     */
    public void cancelTask() {
        cancelRequested = true;
    }

    /**
     * Updates the progress progressMonitor with progress reported by the files
     * manager.
     */
    private class ProgressUpdater implements FileManager.FileAddProgressUpdater {

        private int count;

        /**
         * @inheritDoc
         */
        @Override
        public void fileAdded(final AbstractFile file) {
            ++count;
            if (count % 10 == 0) {
                progressMonitor.setProgressText(NbBundle.getMessage(this.getClass(),
                        "AddLocalFilesTask.localFileAdd.progress.text",
                        file.getParentPath(),
                        file.getName()));
            }
        }
    }
}
