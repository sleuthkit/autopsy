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
package org.sleuthkit.autopsy.directorytree;

import java.util.logging.Level;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.Tags;
import org.sleuthkit.datamodel.BlackboardArtifact;

public class TagBlackboardArtifactAction extends TagAction {
    @Override
    protected TagMenu getTagMenu(Node[] selectedNodes) {
        return new TagBlackboardArtifactMenu(selectedNodes);        
    }    
    
    private static class TagBlackboardArtifactMenu extends TagMenu {
        public TagBlackboardArtifactMenu(Node[] nodes) {
            super((nodes.length > 1 ? "Tag Results" : "Tag Result"), nodes);
        }

        @Override
        protected void tagNodes(String tagName, String comment) {
            for (Node node : getNodes()) {
                BlackboardArtifact artifact = node.getLookup().lookup(BlackboardArtifact.class);
                if (null != artifact) {
                    Tags.createTag(artifact, tagName, comment);
                }
                else {
                    Logger.getLogger(org.sleuthkit.autopsy.directorytree.TagBlackboardArtifactAction.TagBlackboardArtifactMenu.class.getName()).log(Level.SEVERE, "Node not associated with a BlackboardArtifact object");                
                }
            }
        }
    }    
}
