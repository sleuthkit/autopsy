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
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.RootContentChildren;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;
/**
 * View the directory content associated with the given Artifact
 */
class ViewContextAction extends AbstractAction {

    private BlackboardArtifactNode node;
    private static final Logger logger = Logger.getLogger(ViewContextAction.class.getName());

    public ViewContextAction(String title, Node node) {
        super(title);
        this.node = (BlackboardArtifactNode) node;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        List<Content> hierarchy = new ArrayList<Content>();
        FsContent associated = node.getAssociatedFile();
        while(!associated.isRoot()){
            hierarchy.add(associated);
            try {
                associated = associated.getParentDirectory();
            } catch (TskException ex) {
                Logger.getLogger(ViewContextAction.class.getName())
                        .log(Level.INFO, "Couldn't get parent directory", ex);
                return;
            }
        }
        FileSystem fs = associated.getFileSystem();
        //hierarchy.add(fs);
        Volume v = (Volume) fs.getParent();
        hierarchy.add(v);
        VolumeSystem vs = v.getParent();
        //hierarchy.add(vs);
        Image img = vs.getParent();
        hierarchy.add(img);
        Collections.reverse(hierarchy);
        Node generated = new AbstractNode(new RootContentChildren(hierarchy));
        Children genChilds = generated.getChildren();

        ExplorerManager man = DirectoryTreeTopComponent.findInstance().getExplorerManager();
        Node root = man.getRootContext();
        Children dirChilds = root.getChildren();

        Node dirExplored = null;

        for(int i = 0; i < genChilds.getNodesCount()-1; i++){
            Node currentGeneratedNode = genChilds.getNodeAt(i);
            for(int j = 0; j < dirChilds.getNodesCount(); j++){
                Node currentDirectoryTreeNode = dirChilds.getNodeAt(j);
                if(currentGeneratedNode.getDisplayName().equals(currentDirectoryTreeNode.getDisplayName())){
                    dirExplored = currentDirectoryTreeNode;
                    dirChilds = currentDirectoryTreeNode.getChildren();
                    break;
                }
            }
        }

        try {
            if(dirExplored != null)
                man.setExploredContextAndSelection(dirExplored, new Node[]{dirExplored});
        } catch (PropertyVetoException ex) {
            logger.log(Level.WARNING, "Couldn't set selected node", ex);
        }
    }
}
