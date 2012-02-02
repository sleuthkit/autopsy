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

import javax.swing.Action;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.directorytree.ChangeViewAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;

/**
 * This class wraps nodes as they are passed to the DataResult viewers.  It 
 * defines the actions that the node should have. 
 */
public class DataResultFilterNode extends FilterNode {


    /** the constructor */
    public DataResultFilterNode(Node arg) {
        super(arg, new DataResultFilterChildren(arg));
    }

    /**
     * Right click action for the nodes that we want to pass to the directory
     * table and the output view.
     *
     * @param popup
     * @return actions
     */
    @Override
    public Action[] getActions(boolean popup) {
        
       Content content = getOriginal().getLookup().lookup(Content.class);
       return content.accept(new GetActionContentVisitor());
    }
    
    private class GetActionContentVisitor extends ContentVisitor.Default<Action[]> {
        @Override
        public Action[] visit(Directory dir) {
            return new Action[]{
                new ExtractAction("Extract Directory", getOriginal()),
                new ChangeViewAction("View", 0, getOriginal()),
                new OpenParentFolderAction("Open Parent Directory", ContentUtils.getSystemPath(dir))
            };
        }
        
        @Override
        public Action[] visit(File f) {
            return new Action[]{
                new ExternalViewerAction("Open in External Viewer", getOriginal()),
                new ExtractAction("Extract File", getOriginal()),
                new ChangeViewAction("View", 0, getOriginal()),
                new OpenParentFolderAction("Open Parent Directory", ContentUtils.getSystemPath(f))
            };
        }

        @Override
        protected Action[] defaultVisit(Content cntnt) {
            return new Action[]{};
        }
    }
    

    /**
     * Double click action for the nodes that we want to pass to the directory
     * table and the output view.
     *
     * @return action
     */
    @Override
    public Action getPreferredAction() {
        return null;
    }
}
