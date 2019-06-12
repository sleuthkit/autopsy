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
package org.sleuthkit.autopsy.datamodel;

import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;

/**
 * Container to bag the S C & O data for an abstract file node.
 * 
 */
class SCOData {

    private Pair<DataResultViewerTable.Score, String> scoreAndDescription = null;  
    private DataResultViewerTable.HasCommentStatus comment = null;
    private Pair<Long, String> countAndDescription = null;

    Pair<DataResultViewerTable.Score, String> getScoreAndDescription() {
        return scoreAndDescription;
    }

    DataResultViewerTable.HasCommentStatus getComment() {
        return comment;
    }

    Pair<Long, String> getCountAndDescription() {
        return countAndDescription;
    }
    
    void setScoreAndDescription(Pair<DataResultViewerTable.Score, String> scoreAndDescription) {
        this.scoreAndDescription = scoreAndDescription;
    }
    void setComment(DataResultViewerTable.HasCommentStatus comment) {
        this.comment = comment;
    }
    void setCountAndDescription(Pair<Long, String> countAndDescription) {
        this.countAndDescription = countAndDescription;
    }
    
}
