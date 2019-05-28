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
package org.sleuthkit.autopsy.casemodule.services.applicationtags;

import java.util.EnumSet;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A per case Autopsy service that manages the addition of application content
 * tags to the case database.
 */
public final class ApplicationTagsManager {

    private static CaseDbAccessManager dbManager;

    private static final String TABLE_NAME = "beta_tag_app_data";
    private static final String TABLE_SCHEMA = "(app_data_id INTEGER PRIMARY KEY, "
            + "content_tag_id INTEGER NOT NULL, app_data TEXT NOT NULL,"
            + "FOREIGN KEY(content_tag_id) REFERENCES content_tags(tag_id))";
    
    private final String INSERT_OR_REPLACE_TAG_DATA = "(content_tag_id, app_data) VALUES (?, ?)";
    private final String SELECT_TAG_DATA = "* FROM " + TABLE_NAME + " WHERE content_tag_id = ?";
    private final String DELETE_TAG_DATA = "WHERE app_data_id = ?";
    
    static {
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), evt -> {
            if(evt.getNewValue() != null) {
                Case currentCase = (Case) evt.getNewValue();
                try {
                    CaseDbAccessManager caseDb = currentCase.getSleuthkitCase().getCaseDbAccessManager();
                    //Create our custom application tags table, if need be.
                    if (!caseDb.tableExists(TABLE_NAME)) {
                        caseDb.createTable(TABLE_NAME, TABLE_SCHEMA);
                    }
                    
                    dbManager = caseDb;
                } catch (TskCoreException ex) {
                    //Log
                }
            }
        });
    }
    
    //public createTag
    //public updateTag
    //public getTag
    //public deleteTag
    
}
