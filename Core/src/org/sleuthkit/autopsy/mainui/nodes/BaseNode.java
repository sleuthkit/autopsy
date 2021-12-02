/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.autopsy.mainui.datamodel.BaseRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;

/**
 * A a simple starting point for nodes.
 */
abstract class BaseNode<S extends SearchResultsDTO, R extends BaseRowDTO> extends AbstractNode implements ActionContext {

    private final S results;
    private final R rowData;

    BaseNode(Children children, Lookup lookup, S results, R rowData) {
        super(children, lookup);
        this.results = results;
        this.rowData = rowData;
    }

    /**
     * Returns the SearchResultDTO object.
     *
     * @return
     */
    S getSearchResultsDTO() {
        return results;
    }

    /**
     * Returns the RowDTO for this node.
     *
     * @return A RowDTO object.
     */
    R getRowDTO() {
        return rowData;
    }

    @Override
    protected Sheet createSheet() {
        return ContentNodeUtil.setSheet(super.createSheet(), results.getColumns(), rowData.getCellValues());
    }

    @Override
    public Action[] getActions(boolean context) {
        return ActionsFactory.getActions(this);
    }
    
    @Override
    public Action getPreferredAction() {
        System.out.println("### getPreferredAction for node of type: " + this.getClass().getSimpleName());
        DirectoryTreeTopComponent treeViewTopComponent = DirectoryTreeTopComponent.findInstance();
        ExplorerManager treeViewExplorerMgr = treeViewTopComponent.getExplorerManager();
        
        // For now, skip .. case
        if (treeViewExplorerMgr == null || treeViewExplorerMgr.getSelectedNodes().length == 0) {
            return null;
        }
        final Node currentSelectionInDirectoryTree = treeViewExplorerMgr.getSelectedNodes()[0];
        final String currentNodeName = this.getDisplayName();

        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentSelectionInDirectoryTree != null) {
                    // Find the filter version of the passed in dataModelNode. 
                    final org.openide.nodes.Children children = currentSelectionInDirectoryTree.getChildren();
                    // This call could break if the DirectoryTree is re-implemented with lazy ChildFactory objects.
                    System.out.println("### Looking for child with name " + currentNodeName);
                    Node newSelection = children.findChild(currentNodeName);
                    if (newSelection == null) {
                        System.out.println("    Did not find it");
                    } else {
                        System.out.println("    Found it!");
                    }

                    /*
                     * We got null here when we were viewing a ZIP file in
                     * the Views -> Archives area and double clicking on it
                     * got to this code. It tried to find the child in the
                     * tree and didn't find it. An exception was then thrown
                     * from setting the selected node to be null.
                     */
                    if (newSelection != null) {
                        try {
                            treeViewExplorerMgr.setExploredContextAndSelection(newSelection, new Node[]{newSelection});
                        } catch (PropertyVetoException ex) {
                            Logger logger = Logger.getLogger(DataResultFilterNode.class.getName());
                            logger.log(Level.WARNING, "Error: can't open the selected directory.", ex); //NON-NLS
                        }
                    }
                }
            }
        };
    }
}
