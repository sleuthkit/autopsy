/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author dfickling
 */
class ArtifactTypeChildren extends ChildFactory<BlackboardArtifact>{
    
    private SleuthkitCase skCase;
    private BlackboardArtifact.ARTIFACT_TYPE type;

    public ArtifactTypeChildren(BlackboardArtifact.ARTIFACT_TYPE type, SleuthkitCase skCase) {
        this.skCase = skCase;
        this.type = type;
    }

    @Override
    protected boolean createKeys(List<BlackboardArtifact> list) {
        try {
            list.addAll(skCase.getBlackboardArtifacts(type.getTypeID()));
        } catch (TskException ex) {
            Logger.getLogger(ArtifactTypeChildren.class.getName())
                    .log(Level.SEVERE, "Couldn't get blackboard artifacts from database", ex);
        }
        return true;
    }
    
    @Override
    protected Node createNodeForKey(BlackboardArtifact key){
        return new BlackboardArtifactNode(key);
    }
    
}
