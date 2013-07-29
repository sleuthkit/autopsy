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

import java.awt.event.ActionEvent;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import org.openide.nodes.Node;
import org.openide.util.actions.Presenter;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;

/**
 * Action on a file or artifact that adds a tag and reloads the directory tree. 
 * Supports tagging of AbstractFiles and BlackboardArtifacts.
 */
public abstract class TagAction extends AbstractAction implements Presenter.Popup {
    @Override
    public JMenuItem getPopupPresenter() {
        DataResultViewerTable resultViewer = (DataResultViewerTable)Lookup.getDefault().lookup(DataResultViewer.class);
        if (null == resultViewer) {
            Logger.getLogger(TagAction.class.getName()).log(Level.SEVERE, "Could not get DataResultViewerTable from Lookup");
            return null;
        }
        
        Node[] selectedNodes = resultViewer.getExplorerManager().getSelectedNodes();
        if (selectedNodes.length <= 0) {
            Logger.getLogger(TagAction.class.getName()).log(Level.SEVERE, "Tried to perform tagging of Nodes with no Nodes selected");
            return null;
        }
        
        return getTagMenu(selectedNodes);
    }

    protected abstract TagMenu getTagMenu(Node[] selectedNodes);
            
    @Override
    public void actionPerformed(ActionEvent e) {
        // Do nothing - this action should never be performed
        // Submenu actions are invoked instead
    }
}