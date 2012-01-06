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


package org.sleuthkit.autopsy.filesearch;


import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.nodes.NodeOp;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.coreutils.Log;


/**
 * The action to open the parent folder of the given Node
 */
public class OpenParentFolderAction extends AbstractAction{

    private String[] paths;

    // for error handling
    private String className = this.getClass().toString();

    public OpenParentFolderAction(String title, String[] paths){
        super(title);
        this.paths = paths;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Log.noteAction(this.getClass());
        
        try {

            ExplorerManager em = DirectoryTreeTopComponent.findInstance().getExplorerManager();
            Node root = em.getRootContext();

            if(paths.length > 1 && root != null) {
                String[] parentPath = Arrays.copyOf(paths, paths.length - 1);
                
                Node parentNode = NodeOp.findPath(root, parentPath);
                
                em.setExploredContextAndSelection(parentNode, new Node[]{parentNode});
                TopComponent dirTree = DirectoryTreeTopComponent.findInstance();
                if(!dirTree.isOpened()){ dirTree.open(); }
                dirTree.requestActive(); // make the directory tree the active top component

//                TopComponent resultTable = new DataResultTopComponent();
//                if(!resultTable.isOpened()){ resultTable.open(); }
//                resultTable.requestActive(); // make the directory tree the active top component
                ((DirectoryTreeTopComponent)dirTree).setDirectoryListingActive();

                // make the node table the active top component
                // @@@ Make the node table the active top component


                }
        
        } catch (Exception ex) {
            // throw an error here
           Logger.getLogger(this.className).log(Level.WARNING, "Error: error while trying to open the parent directory.", ex);
        }
    }

}
