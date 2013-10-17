/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.TskCoreException;

public class Tags {

    private static final Logger logger = Logger.getLogger(Tags.class.getName());
    public static final String BOOKMARK_TAG_NAME = "Bookmark";
    
    /**
     * Looks up the tag names associated with either a tagged artifact or a tag artifact.
     * 
     * @param artifact The artifact
     * @return A set of unique tag names
     */
    public static HashSet<String> getUniqueTagNamesForArtifact(BlackboardArtifact artifact) {
        return getUniqueTagNamesForArtifact(artifact.getArtifactID(), artifact.getArtifactTypeID());
    }    

    /**
     * Looks up the tag names associated with either a tagged artifact or a tag artifact.
     * 
     * @param artifactID The ID of the artifact
     * @param artifactTypeID The ID of the artifact type
     * @return A set of unique tag names
     */
    public static HashSet<String> getUniqueTagNamesForArtifact(long artifactID, int artifactTypeID) {
        HashSet<String> tagNames = new HashSet<>();
        
        try {
            ArrayList<Long> tagArtifactIDs = new ArrayList<>();
            if (artifactTypeID == ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID() ||
                artifactTypeID == ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                tagArtifactIDs.add(artifactID);
            } else {
                List<BlackboardArtifact> tags = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifacts(ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT, artifactID);
                for (BlackboardArtifact tag : tags) {
                    tagArtifactIDs.add(tag.getArtifactID());
                }
            }

            for (Long tagArtifactID : tagArtifactIDs) {
                String whereClause = "WHERE artifact_id = " + tagArtifactID + " AND attribute_type_id = " + ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID();
                List<BlackboardAttribute> attributes = Case.getCurrentCase().getSleuthkitCase().getMatchingAttributes(whereClause);
                for (BlackboardAttribute attr : attributes) {
                    tagNames.add(attr.getValueString());
                }
            }
        } 
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for artifact " + artifactID, ex);
        }
        
        return tagNames;
    }    
}
