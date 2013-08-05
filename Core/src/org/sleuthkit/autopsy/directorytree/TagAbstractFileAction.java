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
import org.sleuthkit.datamodel.AbstractFile;

public class TagAbstractFileAction extends TagAction { 
    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static TagAbstractFileAction instance;

    public static synchronized TagAbstractFileAction getInstance() {
        if (null == instance) {
            instance = new TagAbstractFileAction();
        }

        return instance;
    }

    private TagAbstractFileAction() {
    }
    
    @Override
    protected TagMenu getTagMenu(Node[] selectedNodes) {
        return new TagAbstractFileMenu(selectedNodes);        
    }
    
    private static class TagAbstractFileMenu extends TagMenu {
        public TagAbstractFileMenu(Node[] nodes) {
            super((nodes.length > 1 ? "Tag Files" : "Tag File"), nodes);
        }

        @Override
        protected void tagNodes(String tagName, String comment) {
            for (Node node : getNodes()) {
                AbstractFile file = node.getLookup().lookup(AbstractFile.class);
                if (null != file) {
                    Tags.createTag(file, tagName, comment);
                }
                else {
                    Logger.getLogger(org.sleuthkit.autopsy.directorytree.TagAbstractFileAction.TagAbstractFileMenu.class.getName()).log(Level.SEVERE, "Node not associated with an AbstractFile object");                
                }
            }
        }
    }        
}
