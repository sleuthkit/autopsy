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

package org.sleuthkit.autopsy.directorytree;

import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.CaseDbSchemaVersionNumber;

/**
 * Utility class for Directory tree.
 * 
 */
final class DirectoryTreeUtils {
    
    private static final int ATTACHMENT_CHILDOF_MSG_MAX_DB_MAJOR_VER = 8;
    private static final int ATTACHMENT_CHILDOF_MSG_MAX_DB_MINOR_VER = 4;
     
    
     /**
     * Prior to schema version 8.4, attachments were children of messages and
     * hence messages with any attachment children are shown in the directory
     * tree.
     *
     * At 8.4, attachments are tracked as an attribute, and the message artifact
     * don't need to be shown in the directory tree.
     *
     * This method may be used to check the schema version and behave
     * accordingly, in order to maintain backward compatibility.
     *
     * @return True if messages with attachment children should be shown in
     * directory tree.
     */
    static boolean showMessagesInDirTree() {
        boolean showMessagesInDirTree = true;
        if (Case.isCaseOpen()) {
            CaseDbSchemaVersionNumber version = Case.getCurrentCase().getSleuthkitCase().getDBSchemaCreationVersion();
            showMessagesInDirTree
                    = ((version.getMajor() < ATTACHMENT_CHILDOF_MSG_MAX_DB_MAJOR_VER)
                    || (version.getMajor() == ATTACHMENT_CHILDOF_MSG_MAX_DB_MAJOR_VER && version.getMinor() < ATTACHMENT_CHILDOF_MSG_MAX_DB_MINOR_VER));
        }
        return showMessagesInDirTree;
    }
    
}
