/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.FileTypes.FileTypesNode;
import org.sleuthkit.autopsy.datamodel.LayoutFileNode;
import org.sleuthkit.autopsy.datamodel.LocalFileNode;
import org.sleuthkit.autopsy.datamodel.Reports;
import org.sleuthkit.autopsy.datamodel.SlackFileNode;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * This class wraps nodes as they are passed to the DataResult viewers. It
 * defines the actions that the node should have.
 */
public class DataResultFilterNode extends FilterNode {

    private ExplorerManager sourceEm;

    private final DisplayableItemNodeVisitor<List<Action>> getActionsDIV;

    private final DisplayableItemNodeVisitor<AbstractAction> getPreferredActionsDIV;

    /**
     *
     * @param node Root node to be passed to DataResult viewers
     * @param em   ExplorerManager for component that is creating the node
     */
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
     *
     * @return actions
     */
    @Override
    public Action[] getActions(boolean popup) {

        List<Action> actions = new ArrayList<>();

        final DisplayableItemNode originalNode = (DisplayableItemNode) this.getOriginal();
        List<Action> accept = originalNode.accept(getActionsDIV);
        if (accept != null) {
            actions.addAll(accept);
        }

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
        final Node original = this.getOriginal();
        // Once had a org.openide.nodes.ChildFactory$WaitFilterNode passed in
        if ((original instanceof DisplayableItemNode) == false) {
            return null;
        }

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
                if (newPs.remove(AbstractFsContentNode.HIDE_PARENT) != null) {
                    newPs.remove(AbstractFilePropertyType.LOCATION.toString());
                }
                propertySets[i] = newPs;
            }
        }

        return propertySets;
    }

    /**
     * Uses the default nodes actions per node, adds some custom ones and
     * returns them per visited node type
     */
    private static class GetPopupActionsDisplayableItemNodeVisitor extends DisplayableItemNodeVisitor.Default<List<Action>> {

        @Override
        public List<Action> visit(BlackboardArtifactNode ban) {
            //set up actions for artifact node based on its Content object
            //TODO all actions need to be consolidated in single place!
            //they should be set in individual Node subclass and using a utility to get Actions per Content sub-type
            // TODO UPDATE: There is now a DataModelActionsFactory utility;

            List<Action> actions = new ArrayList<>();

            //merge predefined specific node actions if bban subclasses have their own
            for (Action a : ban.getActions(true)) {
                actions.add(a);
            }
            BlackboardArtifact ba = ban.getLookup().lookup(BlackboardArtifact.class);
            final int artifactTypeID = ba.getArtifactTypeID();

            if (artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()
                    || artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                actions.add(new ViewContextAction(
                        NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewFileInDir.text"), ban));
            } else {
                // if the artifact links to another file, add an action to go to
                // that file
                Content c = findLinked(ban);
                if (c != null) {
                    actions.add(new ViewContextAction(
                            NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewFileInDir.text"), c));
                }
                // action to go to the source file of the artifact
                actions.add(new ViewContextAction(
                        NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewSrcFileInDir.text"), ban));
            }
            Content c = ban.getLookup().lookup(File.class);
            Node n = null;
            boolean md5Action = false;
            if (c != null) {
                n = new FileNode((AbstractFile) c);
                md5Action = true;
            } else if ((c = ban.getLookup().lookup(Directory.class)) != null) {
                n = new DirectoryNode((Directory) c);
            } else if ((c = ban.getLookup().lookup(VirtualDirectory.class)) != null) {
                n = new VirtualDirectoryNode((VirtualDirectory) c);
            } else if ((c = ban.getLookup().lookup(LayoutFile.class)) != null) {
                n = new LayoutFileNode((LayoutFile) c);
            } else if ((c = ban.getLookup().lookup(LocalFile.class)) != null
                    || (c = ban.getLookup().lookup(DerivedFile.class)) != null) {
                n = new LocalFileNode((AbstractFile) c);
            } else if ((c = ban.getLookup().lookup(SlackFile.class)) != null) {
                n = new SlackFileNode((SlackFile) c);
            }
            if (n != null) {
                actions.add(null); // creates a menu separator
                actions.add(new NewWindowViewAction(
                        NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewInNewWin.text"), n));
                actions.add(new ExternalViewerAction(
                        NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.openInExtViewer.text"), n));
                actions.add(null); // creates a menu separator
                actions.add(ExtractAction.getInstance());
                if (md5Action) {
                    actions.add(new HashSearchAction(
                            NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.searchFilesSameMd5.text"), n));
                }
                actions.add(null); // creates a menu separator
                actions.add(AddContentTagAction.getInstance());
                actions.add(AddBlackboardArtifactTagAction.getInstance());
                actions.addAll(ContextMenuExtensionPoint.getActions());
            } else {
                // There's no specific file associated with the artifact, but
                // we can still tag the artifact itself
                actions.add(null);
                actions.add(AddBlackboardArtifactTagAction.getInstance());
            }
            return actions;
        }

        @Override
        public List<Action> visit(Reports.ReportsListNode ditem) {
            // The base class Action is "Collapse All", inappropriate.
            return null;
        }
        
        @Override
        public List<Action> visit(FileTypesNode fileTypes) {
          return defaultVisit(fileTypes);
        }
        
        
        @Override
        protected List<Action> defaultVisit(DisplayableItemNode ditem) {
            //preserve the default node's actions
            List<Action> actions = new ArrayList<>();

            for (Action action : ditem.getActions(true)) {
                actions.add(action);
            }

            return actions;
        }

        private Content findLinked(BlackboardArtifactNode ba) {
            BlackboardArtifact art = ba.getLookup().lookup(BlackboardArtifact.class);
            Content c = null;
            try {
                for (BlackboardAttribute attr : art.getAttributes()) {
                    if (attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()) {
                        switch (attr.getAttributeType().getValueType()) {
                            case INTEGER:
                                int i = attr.getValueInt();
                                if (i != -1) {
                                    c = art.getSleuthkitCase().getContentById(i);
                                }
                                break;
                            case LONG:
                                long l = attr.getValueLong();
                                if (l != -1) {
                                    c = art.getSleuthkitCase().getContentById(l);
                                }
                                break;
                        }
                    }
                }
            } catch (TskException ex) {
                Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Error getting linked file", ex); //NON-NLS
            }
            return c;
        }

    }

    /*
     * Action for double-click / preferred action on nodes.
     */
    private class GetPreferredActionsDisplayableItemNodeVisitor extends DisplayableItemNodeVisitor.Default<AbstractAction> {

        @Override
        public AbstractAction visit(BlackboardArtifactNode ban) {
            return new ViewContextAction(
                    NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewInDir.text"), ban);
        }

        @Override
        public AbstractAction visit(DirectoryNode dn) {
            if (dn.getDisplayName().equals(DirectoryNode.DOTDOTDIR)) {
                return openParent(dn);
            } else if (dn.getDisplayName().equals(DirectoryNode.DOTDIR) == false) {
                return openChild(dn);
            } else {
                return null;
            }
        }

        @Override
        public AbstractAction visit(FileNode fn) {
            if (fn.hasContentChildren()) {
                return openChild(fn);
            } else {
                return null;
            }
        }

        @Override
        public AbstractAction visit(LocalFileNode dfn) {
            if (dfn.hasContentChildren()) {
                return openChild(dfn);
            } else {
                return null;
            }
        }

        @Override
        public AbstractAction visit(Reports.ReportNode reportNode) {
            return reportNode.getPreferredAction();
        }

        @Override
        protected AbstractAction defaultVisit(DisplayableItemNode c) {
            return openChild(c);
        }
        
        @Override
        public AbstractAction visit(FileTypesNode fileTypes) {
            return openChild(fileTypes);
        }
        

        
        /**
         * Tell the originating ExplorerManager to display the given
         * dataModelNode.
         *
         * @param dataModelNode Original (non-filtered) dataModelNode to open
         *
         * @return
         */
        private AbstractAction openChild(final AbstractNode dataModelNode) {
            // get the current selection from the directory tree explorer manager,
            // which is a DirectoryTreeFilterNode. One of that node's children
            // is a DirectoryTreeFilterNode that wraps the dataModelNode. We need
            // to set that wrapped node as the selection and root context of the 
            // directory tree explorer manager (sourceEm)
            final Node currentSelectionInDirectoryTree = sourceEm.getSelectedNodes()[0];

            return new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (currentSelectionInDirectoryTree != null) {
                        // Find the filter version of the passed in dataModelNode. 
                        final org.openide.nodes.Children children = currentSelectionInDirectoryTree.getChildren();
                        // This call could break if the DirectoryTree is re-implemented with lazy ChildFactory objects.
                        Node newSelection = children.findChild(dataModelNode.getName());

                        /*
                         * We got null here when we were viewing a ZIP file in
                         * the Views -> Archives area and double clicking on it
                         * got to this code. It tried to find the child in the
                         * tree and didn't find it. An exception was then thrown
                         * from setting the selected node to be null.
                         */
                        if (newSelection != null) {
                            try {
                                sourceEm.setExploredContextAndSelection(newSelection, new Node[]{newSelection});
                            } catch (PropertyVetoException ex) {
                                Logger logger = Logger.getLogger(DataResultFilterNode.class.getName());
                                logger.log(Level.WARNING, "Error: can't open the selected directory.", ex); //NON-NLS
                            }
                        }
                    }
                }
            };
        }

        /**
         * Tell the originating ExplorerManager to display the parent of the
         * given node.
         *
         * @param node Original (non-filtered) node to open
         *
         * @return
         */
        private AbstractAction openParent(AbstractNode node) {
            // @@@ Why do we ignore node?
            Node[] selectedFilterNodes = sourceEm.getSelectedNodes();
            Node selectedFilterNode = selectedFilterNodes[0];
            final Node parentNode = selectedFilterNode.getParentNode();

            return new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        sourceEm.setSelectedNodes(new Node[]{parentNode});
                    } catch (PropertyVetoException ex) {
                        Logger logger = Logger.getLogger(DataResultFilterNode.class.getName());
                        logger.log(Level.WARNING, "Error: can't open the parent directory.", ex); //NON-NLS
                    }
                }
            };
        }
    }
}
