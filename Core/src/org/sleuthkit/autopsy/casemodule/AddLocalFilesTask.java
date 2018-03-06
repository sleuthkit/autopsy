/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * A runnable that adds a set of local/logical files and/or directories to the
 * case database, grouped under a virtual directory that serves as the data
 * source.
 */
class AddLocalFilesTask implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(AddLocalFilesTask.class.getName());
    private final String deviceId;
    private final String rootVirtualDirectoryName;
    private final List<String> localFilePaths;
    private final DataSourceProcessorProgressMonitor progress;
    private final DataSourceProcessorCallback callback;

    /**
     * Constructs a runnable that adds a set of local/logical files and/or
     * directories to the case database, grouped under a virtual directory that
     * serves as the data source.
     *
     * @param deviceId                 An ASCII-printable identifier for the
     *                                 device associated with the data source,
     *                                 in this case a gropu of local/logical
     *                                 files, that is intended to be unique
     *                                 across multiple cases (e.g., a UUID).
     * @param rootVirtualDirectoryName The name to give to the virtual directory
     *                                 that will serve as the root for the
     *                                 local/logical files and/or directories
     *                                 that compose the data source. Pass the
     *                                 empty string to get a default name of the
     *                                 form: LogicalFileSet[N]
     * @param localFilePaths           A list of localFilePaths of local/logical
     *                                 files and/or directories.
     * @param progressMonitor          Progress monitor to report progress
     *                                 during processing.
     * @param callback                 Callback to call when processing is done.
     */
    AddLocalFilesTask(String deviceId, String rootVirtualDirectoryName, List<String> localFilePaths, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
        this.deviceId = deviceId;
        this.rootVirtualDirectoryName = rootVirtualDirectoryName;
        this.localFilePaths = localFilePaths;
        this.callback = callback;
        this.progress = progressMonitor;
    }

    /**
     * Adds a set of local/logical files and/or directories to the case
     * database, grouped under a virtual directory that serves as the data
     * source.
     */
    @Override
    public void run() {
        List<Content> newDataSources = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            progress.setIndeterminate(true);
            FileManager fileManager = Case.getOpenCase().getServices().getFileManager();
            LocalFilesDataSource newDataSource = fileManager.addLocalFilesDataSource(deviceId, rootVirtualDirectoryName, "", localFilePaths, new ProgressUpdater());
            newDataSources.add(newDataSource);
        } catch (TskDataException | TskCoreException | NoCurrentCaseException ex) {
            errors.add(ex.getMessage());
            LOGGER.log(Level.SEVERE, String.format("Failed to add datasource: %s", ex.getMessage()), ex);
        } finally {
            DataSourceProcessorCallback.DataSourceProcessorResult result;
            if (!errors.isEmpty()) {
                result = DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
            } else {
                result = DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS;
            }
            callback.done(result, errors, newDataSources);
        }
    }

    /**
     * Updates task progress as the file manager adds the local/logical files
     * and/or directories to the case database.
     */
    private class ProgressUpdater implements FileManager.FileAddProgressUpdater {

        private int count;

        /**
         * Updates task progress (called by the file manager after it adds each
         * local file/directory to the case database).
         */
        @Override
        public void fileAdded(final AbstractFile file) {
            ++count;
            if (count % 10 == 0) {
                progress.setProgressText(NbBundle.getMessage(this.getClass(),
                        "AddLocalFilesTask.localFileAdd.progress.text",
                        file.getParentPath(),
                        file.getName()));
            }
        }
    }
}
