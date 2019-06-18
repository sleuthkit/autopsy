/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A task that queries the case database for all files with supported
 * image/video mime types or extensions and adds them to the drawables database.
 */
class AddDrawableFilesTask extends BulkDrawableFilesTask {

    private final ImageGalleryController controller;
    private final DrawableDB taskDB;

    AddDrawableFilesTask(long dataSourceObjId, ImageGalleryController controller) {
        super(dataSourceObjId, controller);
        this.controller = controller;
        this.taskDB = controller.getDrawablesDatabase();
        taskDB.buildFileMetaDataCache();
    }

    @Override
    protected void cleanup() {
        taskDB.freeFileMetaDataCache();
        // at the end of the task, set the stale status based on the
        // cumulative status of all data sources
        controller.setModelIsStale(controller.isDataSourcesTableStale());
    }

    @Override
    void processFile(AbstractFile f, DrawableDB.DrawableTransaction tr, SleuthkitCase.CaseDbTransaction caseDbTransaction) throws TskCoreException {
        final boolean known = f.getKnown() == TskData.FileKnown.KNOWN;
        if (known) {
            taskDB.removeFile(f.getId(), tr); //remove known files
        } else {
            // NOTE: Files are being processed because they have the right MIME type,
            // so we do not need to worry about this calculating them
            if (FileTypeUtils.hasDrawableMIMEType(f)) {
                taskDB.updateFile(DrawableFile.create(f, true, false), tr, caseDbTransaction);
            } //unsupported mimtype => analyzed but shouldn't include
            else {
                taskDB.removeFile(f.getId(), tr);
            }
        }
    }

    @Override
    @NbBundle.Messages({
        "AddDrawableFilesTask.populatingDb.status=populating analyzed image/video database"
    })
    ProgressHandle getInitialProgressHandle() {
        return ProgressHandle.createHandle(Bundle.AddDrawableFilesTask_populatingDb_status(), this);
    }
}
