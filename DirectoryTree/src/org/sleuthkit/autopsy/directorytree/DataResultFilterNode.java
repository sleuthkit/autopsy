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
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode.FsContentPropertyType;
import org.sleuthkit.autopsy.datamodel.ArtifactTypeNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.FileSearchFilterNode;
import org.sleuthkit.autopsy.datamodel.ImageNode;
import org.sleuthkit.autopsy.datamodel.RecentFilesFilterNode;
import org.sleuthkit.datamodel.Content;


/**
 * This class wraps nodes as they are passed to the DataResult viewers.  It 
 * defines the actions that the node should have. 
 */
public class DataResultFilterNode extends FilterNode{

    private ExplorerManager sourceEm;
    private final DisplayableItemNodeVisitor<List<Action>> getActionsDIV;
    private final DisplayableItemNodeVisitor<AbstractAction> getPreferredActionsDIV;


    /** the constructor */
    public DataResultFilterNode(Node node, ExplorerManager em) {
        super(node, new DataResultFilterChildren(node, em));
        this.sourceEm = em;
        getActionsDIV = new GetPopupActionsDisplayableItemNodeVisitor();
        getPreferredActionsDIV = new GetPreferredActionsDisplayableItemNodeVisitor();
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
        
        final DisplayableItemNode originalNode = (DisplayableItemNode) this.getOriginal();
        actions.addAll(originalNode.accept(getActionsDIV));
        
        //actions.add(new IndexContentFilesAction(nodeContent, "Index"));

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
        
        final DisplayableItemNode originalNode = (DisplayableItemNode) this.getOriginal();
        
        return originalNode.accept(getPreferredActionsDIV);
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
                newPs.remove(FsContentPropertyType.LOCATION.toString() );
                propertySets[i] = newPs;
            }
        }

        return propertySets;
    }
    
    private class GetPopupActionsDisplayableItemNodeVisitor extends DisplayableItemNodeVisitor.Default<List<Action>> {
        
        @Override
        public List<Action> visit(ImageNode img) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new NewWindowViewAction("View in New Window", getOriginal()));
            actions.addAll(ShowDetailActionVisitor.getActions(img.getLookup().lookup(Content.class)));
            return actions;
        }
        
        @Override
        public List<Action> visit(VolumeNode vol) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new NewWindowViewAction("View in New Window", getOriginal()));
            actions.addAll(ShowDetailActionVisitor.getActions(vol.getLookup().lookup(Content.class)));
            actions.add(new ChangeViewAction("View", 0, getOriginal()));
            
            return actions;
        }
        
        @Override
        public List<Action> visit(DirectoryNode dir) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new NewWindowViewAction("View in New Window", getOriginal()));
            actions.add(new ChangeViewAction("View", 0, getOriginal()));
            actions.add(new ExtractAction("Extract Directory", getOriginal()));
            return actions;
        }
        
        @Override
        public List<Action> visit(FileNode f) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new NewWindowViewAction("View in New Window", getOriginal()));
            actions.add(new ExternalViewerAction("Open in External Viewer", getOriginal()));
            actions.add(new ExtractAction("Extract File", getOriginal()));
            return actions;
        }
        
        @Override
        public List<Action> visit(BlackboardArtifactNode ba) {
            List<Action> actions = new ArrayList<Action>();
            actions.add(new ViewAssociatedContentAction("View Associated Content", getOriginal()));
            actions.add(new ViewContextAction("View in Directory", getOriginal()));
            return actions;
        }
        
        @Override
        protected List<Action> defaultVisit(DisplayableItemNode ditem) {
            return Collections.EMPTY_LIST;
        }
        
    }
    
    private class GetPreferredActionsDisplayableItemNodeVisitor extends DisplayableItemNodeVisitor.Default<AbstractAction>{
        
        @Override
        public AbstractAction visit(VolumeNode vn){
            return openChild(vn);
        }
        
        @Override
        public AbstractAction visit(BlackboardArtifactNode ban){
            return new ViewContextAction("View in Directory", getOriginal());
        }
        
        @Override
        public AbstractAction visit(ArtifactTypeNode atn){
            return openChild(atn);
        }
        
        @Override
        public AbstractAction visit(DirectoryNode dn){
            if(dn.getDisplayName().equals(".."))
                return openParent(dn);
            else if(!dn.getDisplayName().equals("."))
                return openChild(dn);
            else
                return null;
        }
        
        @Override
        public AbstractAction visit(FileSearchFilterNode fsfn){
            return openChild(fsfn);
        }
        
        @Override
        public AbstractAction visit(RecentFilesFilterNode rffn) {
            return openChild(rffn);
        }
        
        @Override
        protected AbstractAction defaultVisit(DisplayableItemNode c) {
            return null;
        }

        private AbstractAction openChild(AbstractNode node) {
            final Node[] parentNode = sourceEm.getSelectedNodes();
            final Node parentContext = parentNode[0];
            final Node original = node;

            return new AbstractAction() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (parentContext != null) {
                        for (int i = 0; i < parentContext.getChildren().getNodesCount(); i++) {
                            Node selectedNode = parentContext.getChildren().getNodeAt(i);
                            if (selectedNode != null && selectedNode.getName().equals(original.getName())) {
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
        
        private AbstractAction openParent(AbstractNode node) {
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
        }
    }
}