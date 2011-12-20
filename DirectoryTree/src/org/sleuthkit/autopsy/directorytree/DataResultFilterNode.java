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
import org.sleuthkit.autopsy.datamodel.VolumeNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Volume;

/**
 * This class wraps nodes as they are passed to the DataResult viewers.  It 
 * defines the actions that the node should have. 
 */
public class DataResultFilterNode extends FilterNode{

    private ExplorerManager sourceEm;
    private final ContentVisitor<List<Action>> getActionsCV;


    /** the constructor */
    public DataResultFilterNode(Node arg, ExplorerManager em) {
        super(arg, new DataResultFilterChildren(arg, em));
        this.sourceEm = em;
        getActionsCV = new GetActionsContentVisitor();
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

        List<Action> actions = new ArrayList<Action>();
        
        actions.add(new NewWindowViewAction("View in New Window", getOriginal()));

        Content nodeContent = this.getOriginal().getLookup().lookup(Content.class);
        actions.addAll(nodeContent.accept(getActionsCV));

        return actions.toArray(new Action[actions.size()]);
    }
        
    private class GetActionsContentVisitor extends ContentVisitor.Default<List<Action>> {
        
        @Override
        public List<Action> visit(Image img) {
            return ShowDetailActionVisitor.getActions(img);
        }
        
        @Override
        public List<Action> visit(Volume vol) {
            List<Action> actions = new ArrayList<Action>();
            actions.addAll(ShowDetailActionVisitor.getActions(vol));
            actions.add(new ChangeViewAction("View", 0, getOriginal()));
            return actions;
        }
        
        @Override
        public List<Action> visit(Directory dir) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new ChangeViewAction("View", 0, getOriginal()));
            actions.add(new ExtractAction("Extract Directory", getOriginal()));
            return actions;
        }
        
        @Override
        public List<Action> visit(File f) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new ExternalViewerAction("Open in External Viewer", getOriginal()));
            actions.add(new ExtractAction("Extract", getOriginal()));
            return actions;
        }

        @Override
        protected List<Action> defaultVisit(Content cntnt) {
            return Collections.EMPTY_LIST;
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
        // double click action(s) for volume node or directory node
        
        final Node originalNode = this.getOriginal();
        
        if (originalNode instanceof VolumeNode || (originalNode instanceof DirectoryNode && !originalNode.getDisplayName().equals("."))) {

            if (originalNode instanceof DirectoryNode && originalNode.getDisplayName().equals("..")) {
                Node[] selectedNode = sourceEm.getSelectedNodes();
                Node selectedContext = selectedNode[0];
                final Node parentNode = selectedContext.getParentNode();

                return new AbstractAction() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        try {
                            sourceEm.setSelectedNodes(new Node[]{parentNode});
                        } catch (PropertyVetoException ex) {
                            Logger logger = Logger.getLogger(DataResultFilterNode.class.getName());
                            logger.log(Level.WARNING, "Error: can't open the parent directory.", ex);
                        }
                    }
                };
            } else {
                final Node[] parentNode = sourceEm.getSelectedNodes();
                final Node parentContext = parentNode[0];

                return new AbstractAction() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (parentContext != null) {
                            for (int i = 0; i < parentContext.getChildren().getNodesCount(); i++) {
                                Node selectedNode = parentContext.getChildren().getNodeAt(i);
                                if (selectedNode != null && selectedNode.getName().equals(originalNode.getName())) {
                                    try {
                                        sourceEm.setExploredContextAndSelection(selectedNode, new Node[]{selectedNode});
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
        } else {
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
}