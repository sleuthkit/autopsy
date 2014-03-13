/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2013 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Creates the children for the ExtractedContent area of the results tree.  This area
 * has all of the blackboard artifacts that are not displayed in a more specific form elsewhere
 * in the tree.
 */
class ExtractedContentChildren extends ChildFactory<BlackboardArtifact.ARTIFACT_TYPE> {
    private SleuthkitCase skCase;
    private final ArrayList<BlackboardArtifact.ARTIFACT_TYPE> doNotShow;
    
    public ExtractedContentChildren(SleuthkitCase skCase) {
        super();
        this.skCase = skCase;
        
        // these are shown in other parts of the UI tree
        doNotShow = new ArrayList<>();
        doNotShow.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_GEN_INFO);
        doNotShow.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG);
        doNotShow.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT);
        doNotShow.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT);
        doNotShow.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
        doNotShow.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
        doNotShow.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        doNotShow.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
    }

    @Override
    protected boolean createKeys(List<BlackboardArtifact.ARTIFACT_TYPE> list) {
        try {
            List<BlackboardArtifact.ARTIFACT_TYPE> inUse = skCase.getBlackboardArtifactTypesInUse();
            inUse.removeAll(doNotShow);
            Collections.sort(inUse,
                    new Comparator<BlackboardArtifact.ARTIFACT_TYPE>() {
                        @Override
                        public int compare(BlackboardArtifact.ARTIFACT_TYPE a, BlackboardArtifact.ARTIFACT_TYPE b) {
                            return a.getDisplayName().compareTo(b.getDisplayName());
                        }
                    });
            list.addAll(inUse);
        } catch (TskCoreException ex) {
            Logger.getLogger(ExtractedContentChildren.class.getName()).log(Level.SEVERE, "Error getting list of artifacts in use: " + ex.getLocalizedMessage());
            return false;
        }
        return true;
    }

    
    @Override
    protected Node createNodeForKey(BlackboardArtifact.ARTIFACT_TYPE key){
        return new ArtifactTypeNode(key, skCase);
    }
    
}
