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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.datamodel.ImageNode;
import org.sleuthkit.autopsy.datamodel.ContentNode;
import org.sleuthkit.autopsy.datamodel.VolumeNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPanel;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.datamodel.ContentNodeVisitor;
import org.sleuthkit.datamodel.Content;

/**
 * This class wraps nodes as they are passed to the DataResult viewers.  It 
 * defines the actions that the node should have. 
 */
public class DataResultFilterNode extends FilterNode implements ContentNode {

    private Node currentNode;
    // for error handling
    private JPanel caller;
    private String className = this.getClass().toString();

    /** the constructor */
    public DataResultFilterNode(Node arg) {
        super(arg, new DataResultFilterChildren(arg));
        this.currentNode = arg;
    }

    @Override
    public Node getOriginal() {
        return super.getOriginal();
    }

    /**
     * Right click action for the nodes that we want to pass to the directory
     * table and the output view.
     *
     * @param popup
     * @return actionss
     */
    @Override
    public Action[] getActions(boolean popup) {

        List<Action> actions = new ArrayList<Action>();


        // right click action(s) for image node
        if (this.currentNode instanceof ImageNode) {
            actions.add(new NewWindowViewAction("View in New Window", (ImageNode) this.currentNode));
            actions.addAll(ShowDetailActionVisitor.getActions(((ImageNode) this.currentNode).getContent()));
        } // right click action(s) for volume node
        else if (this.currentNode instanceof VolumeNode) {
            actions.add(new NewWindowViewAction("View in New Window", (VolumeNode) this.currentNode));
            //new ShowDetailActionVisitor("Volume Details", this.currentNode.getName(), (VolumeNode) this.currentNode),
            actions.addAll(ShowDetailActionVisitor.getActions(((VolumeNode) this.currentNode).getContent()));
            actions.add(new ChangeViewAction("View", 0, (ContentNode) currentNode));
        } // right click action(s) for directory node
        else if (this.currentNode instanceof DirectoryNode) {
            actions.add(new NewWindowViewAction("View in New Window", (DirectoryNode) this.currentNode));
            actions.add(new ChangeViewAction("View", 0, (ContentNode) currentNode));
            actions.add(new ExtractAction("Extract Directory", (DirectoryNode) this.currentNode));
        } // right click action(s) for the file node
        else if (this.currentNode instanceof FileNode) {
            actions.add(new ExternalViewerAction("Open File in External Viewer", (FileNode) this.currentNode));
            actions.add(new NewWindowViewAction("View in New Window", (FileNode) this.currentNode));
            actions.add(new ExtractAction("Extract", (FileNode) this.currentNode));
            actions.add(new ChangeViewAction("View", 0, (ContentNode) currentNode));
        }

        return actions.toArray(new Action[actions.size()]);
    }

    /**
     * Double click action for the nodes that we want to pass to the directory
     * table and the output view.
     *
     * @return action
     */
    @Override
    public Action getPreferredAction() {
        // double click action(s) for volume node or directory node
        if (this.currentNode instanceof VolumeNode || (this.currentNode instanceof DirectoryNode && !this.currentNode.getDisplayName().equals("."))) {

            if (this.currentNode instanceof DirectoryNode && this.currentNode.getDisplayName().equals("..")) {
                ExplorerManager em = DirectoryTreeTopComponent.findInstance().getExplorerManager();
                Node[] selectedNode = em.getSelectedNodes();
                Node selectedContext = selectedNode[0];
                final Node parentNode = selectedContext.getParentNode();

                return new AbstractAction() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        try {
                            DirectoryTreeTopComponent.findInstance().getExplorerManager().setSelectedNodes(new Node[]{parentNode});
                        } catch (PropertyVetoException ex) {
                            Logger logger = Logger.getLogger(DataResultFilterNode.class.getName());
                            logger.log(Level.WARNING, "Error: can't open the parent directory.", ex);
                        }
                    }
                };
            } else {
                ExplorerManager em = DirectoryTreeTopComponent.findInstance().getExplorerManager();
                final Node[] parentNode = em.getSelectedNodes();
                final Node parentContext = parentNode[0];

                return new AbstractAction() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (parentContext != null) {
                            ExplorerManager em = DirectoryTreeTopComponent.findInstance().getExplorerManager();
                            for (int i = 0; i < parentContext.getChildren().getNodesCount(); i++) {
                                Node selectedNode = parentContext.getChildren().getNodeAt(i);
                                if (selectedNode != null && selectedNode.getName().equals(currentNode.getName())) {
                                    try {
                                        em.setExploredContextAndSelection(selectedNode, new Node[]{selectedNode});
                                    } catch (PropertyVetoException ex) {
                                        // throw an error here
                                        Logger logger = Logger.getLogger(DataResultFilterNode.class.getName());
                                        logger.log(Level.WARNING, "Error: can't open the selected directory.", ex);
                                    }
                                }
                            }
                        }
                    }
                };
            }

        } //        // right click action(s) for the file node
        //        if(this.currentNode instanceof FileNode){
        //            // .. put the code here
        //        }
        else {
            return null;
        }
    }

    @Override
    public Node.PropertySet[] getPropertySets() {
        Node.PropertySet[] propertySets = super.getPropertySets();

        for (int i = 0; i < propertySets.length; i++) {
            Node.PropertySet ps = propertySets[i];

            if (ps.getName().equals(Sheet.PROPERTIES)) {
                Sheet.Set newPs = new Sheet.Set();
                newPs.setName(ps.getName());
                newPs.setDisplayName(ps.getDisplayName());
                newPs.setShortDescription(ps.getShortDescription());

                newPs.put(ps.getProperties());
                newPs.remove(FileNode.PROPERTY_LOCATION);
                propertySets[i] = newPs;
            }
        }

        return propertySets;
    }

    @Override
    public Object[][] getRowValues(int rows) throws SQLException {
        return ((ContentNode) currentNode).getRowValues(rows);
    }

    @Override
    public Content getContent() {
        return ((ContentNode) currentNode).getContent();
    }

    @Override
    public String[] getDisplayPath() {
        return ((ContentNode) currentNode).getDisplayPath();
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        // TODO: Figure out how visitors should be delegated
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String[] getSystemPath() {
        return ((ContentNode) currentNode).getSystemPath();
    }
}
