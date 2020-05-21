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

import java.sql.SQLException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A task that updates one drawable file in the drawable database.
 */
class UpdateDrawableFileTask extends DrawableDbTask {

    final AbstractFile file;
    final DrawableDB taskDB;

    public DrawableDB getTaskDB() {
        return taskDB;
    }

    public AbstractFile getFile() {
        return file;
    }

    UpdateDrawableFileTask(AbstractFile f, DrawableDB taskDB) {
        super();
        this.file = f;
        this.taskDB = taskDB;
    }

    /**
     * Update a file in the database
     */
    @Override
    public void run() {
        try {
            DrawableFile drawableFile = DrawableFile.create(getFile(), true, false);
            getTaskDB().updateFile(drawableFile);
        } catch (TskCoreException | SQLException ex) {
            Logger.getLogger(UpdateDrawableFileTask.class.getName()).log(Level.SEVERE, "Error in update file task", ex); //NON-NLS
        }
    }

}
