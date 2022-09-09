/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Task to run when data source analysis is complete.
 */
class HandleDataSourceAnalysisCompleteTask extends DrawableDbTask {

    private final ImageGalleryController controller;
    private final long dataSourceObjId;
    
    private static final Logger logger = Logger.getLogger(HandleDataSourceAnalysisCompleteTask.class.getName());
    
    HandleDataSourceAnalysisCompleteTask(long dataSourceObjId, ImageGalleryController controller) {
        this.controller = controller;
        this.dataSourceObjId = dataSourceObjId;
    }
    
    @Override
    public void run() {
        controller.getGroupManager().resetCurrentPathGroup();
        try {
            DrawableDB drawableDB = controller.getDrawablesDatabase();
            if (drawableDB.getDataSourceDbBuildStatus(dataSourceObjId) == DrawableDB.DrawableDbBuildStatusEnum.IN_PROGRESS) {

                // If at least one file in CaseDB has mime type, then set to COMPLETE
                // Otherwise, back to UNKNOWN since we assume file type module was not run        
                DrawableDB.DrawableDbBuildStatusEnum datasourceDrawableDBStatus
                        = controller.hasFilesWithMimeType(dataSourceObjId)
                        ? DrawableDB.DrawableDbBuildStatusEnum.COMPLETE
                        : DrawableDB.DrawableDbBuildStatusEnum.UNKNOWN;

                drawableDB.insertOrUpdateDataSource(dataSourceObjId, datasourceDrawableDBStatus);
            }
            drawableDB.freeFileMetaDataCache();
        } catch (TskCoreException | SQLException ex) {
            logger.log(Level.WARNING, "Error handling data source analysis completed event", ex);
        }
    }
    
}
