/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.communications.relationships;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode.BlackboardArtifactNodeKey;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *A ChildFactory for CallLog artifacts. 
 */
final class CallLogsChildNodeFactory extends ChildFactory<BlackboardArtifactNodeKey>{
    
    private static final Logger logger = Logger.getLogger(CallLogsChildNodeFactory.class.getName());
    
    private SelectionInfo selectionInfo;
    
    private final Map<String, String> deviceIDMap = new HashMap<>();
    
    CallLogsChildNodeFactory(SelectionInfo selectionInfo) {
        this.selectionInfo = selectionInfo;
    }
    
    void refresh(SelectionInfo selectionInfo) {
        this.selectionInfo = selectionInfo;
        refresh(true);
    }
    
    @Override
    protected boolean createKeys(List<BlackboardArtifactNodeKey> list) {
        
        if(selectionInfo == null) {
            return true;
        }
        
        final Set<Content> relationshipSources;
        try {
            relationshipSources = selectionInfo.getRelationshipSources();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to load relationship sources.", ex); //NON-NLS
            return false;
        }


        for(Content content: relationshipSources) {
            if( !(content instanceof BlackboardArtifact)){
                continue;
            }

            BlackboardArtifact bba = (BlackboardArtifact) content;
            BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(bba.getArtifactTypeID());

            if ( fromID == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG) { 
                try {
                   list.add(BlackboardArtifactNode.createNodeKey(bba));
                } catch ( TskCoreException ex) {
                    logger.log(Level.WARNING, String.format("Unable to create key for artifact: artifactID = %d", bba.getId()), ex);
                }
            }
        }
        
        list.sort(new CallLogComparator(BlackboardArtifactDateComparator.ACCENDING));

        return true;
    }

    @Override
    protected Node createNodeForKey(BlackboardArtifactNodeKey key) {        
        return new CallLogNode(key);
    }
    
    /**
     * A comparator for CallLogNodeKey objects
     */
    final class CallLogComparator implements Comparator<BlackboardArtifactNodeKey>{
        
        final BlackboardArtifactDateComparator comparator;
        
        CallLogComparator(int direction) {
            comparator = new BlackboardArtifactDateComparator(direction);
        }

        @Override
        public int compare(BlackboardArtifactNodeKey key1, BlackboardArtifactNodeKey key2) {
            return comparator.compare(key1.getArtifact(), key2.getArtifact());
        }
    }
}
