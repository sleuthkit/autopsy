/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.FileTypes.FileTypesNode;
import org.sleuthkit.autopsy.commonpropertiessearch.InstanceCountNode;
import org.sleuthkit.autopsy.commonpropertiessearch.InstanceCaseNode;
import org.sleuthkit.autopsy.commonpropertiessearch.InstanceDataSourceNode;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeValueNode;
import org.sleuthkit.autopsy.commonpropertiessearch.CentralRepoCommonAttributeInstanceNode;
import org.sleuthkit.autopsy.datamodel.LocalFileNode;
import org.sleuthkit.autopsy.datamodel.NodeSelectionInfo;
import org.sleuthkit.autopsy.datamodel.Reports;
import org.sleuthkit.autopsy.commonpropertiessearch.CaseDBCommonAttributeInstanceNode;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A node used to wrap another node before passing it to the result viewers. The
 * wrapper node defines the actions associated with the wrapped node and may
 * filter out some of its children.
 */
public class DataResultFilterNode extends FilterNode {

    private static final Logger LOGGER = Logger.getLogger(DataResultFilterNode.class.getName());

    static private final DisplayableItemNodeVisitor<List<Action>> getActionsDIV = new GetPopupActionsDisplayableItemNodeVisitor();
    private final DisplayableItemNodeVisitor<AbstractAction> getPreferredActionsDIV = new GetPreferredActionsDisplayableItemNodeVisitor();

    // Assumptions are made in GetPreferredActionsDisplayableItemNodeVisitor that
    // sourceEm is the directory tree explorer manager.
    private final ExplorerManager sourceEm;

    /**
     * Constructs a node used to wrap another node before passing it to the
     * result viewers. The wrapper node defines the actions associated with the
     * wrapped node and may filter out some of its children.
     *
     * @param node The node to wrap.
     */
    public DataResultFilterNode(Node node) {
        this(node, null);
    }

    /**
     * Constructs a node used to wrap another node before passing it to the
     * result viewers. The wrapper node defines the actions associated with the
     * wrapped node and may filter out some of its children.
     *
     * @param node The node to wrap.
     * @param em   The ExplorerManager for the component that is creating the
     *             node.
     */
    public DataResultFilterNode(Node node, ExplorerManager em) {
        super(node, new DataResultFilterChildren(node, em));
        this.sourceEm = em;
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
        if (this.getOriginal() instanceof DisplayableItemNode) {
            final DisplayableItemNode originalNode = (DisplayableItemNode) this.getOriginal();
            List<Action> accept = originalNode.accept(getActionsDIV);
            if (accept != null) {
                actions.addAll(accept);
            }
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
                newPs.remove(AbstractFsContentNode.HIDE_PARENT);
                propertySets[i] = newPs;
            }
        }

        return propertySets;
    }

    /**
     * Adds information about which child node of this node, if any, should be
     * selected. Can be null.
     *
     * @param selectedChildNodeInfo The child node selection information.
     */
    public void setChildNodeSelectionInfo(NodeSelectionInfo selectedChildNodeInfo) {
        if (getOriginal() instanceof DisplayableItemNode) {
            ((DisplayableItemNode) getOriginal()).setChildNodeSelectionInfo(selectedChildNodeInfo);
        }
    }

    /**
     * Gets information about which child node of this node, if any, should be
     * selected.
     *
     * @return The child node selection information, or null if no child should
     *         be selected.
     */
    public NodeSelectionInfo getChildNodeSelectionInfo() {
        if (getOriginal() instanceof DisplayableItemNode) {
            return ((DisplayableItemNode) getOriginal()).getChildNodeSelectionInfo();
        } else {
            return null;
        }
    }

    /**
     * This class is used for the creation of all the children for the
     * DataResultFilterNode that created in the DataResultFilterNode.java.
     *
     */
    private static class DataResultFilterChildren extends FilterNode.Children {

        private final ExplorerManager sourceEm;
        private final boolean filterArtifacts;    // display message artifacts in the DataSource subtree

        /**
         * the constructor
         */
        private DataResultFilterChildren(Node arg, ExplorerManager sourceEm) {
            super(arg);

            filterArtifacts = SelectionContext.getSelectionContext(arg).equals(SelectionContext.DATA_SOURCES);

            this.sourceEm = sourceEm;
        }

        @Override
        protected Node[] createNodes(Node key) {
            // if displaying the results from the Data Source tree
            // filter out artifacts

            // In older versions of Autopsy,  attachments were children of email/message artifacts
            // and hence email/messages with attachments are shown in the tree data source tree,
            BlackboardArtifact art = key.getLookup().lookup(BlackboardArtifact.class);
            if (art != null && filterArtifacts
                    && ((FilterNodeUtils.showMessagesInDatasourceTree() == false)
                    || (FilterNodeUtils.showMessagesInDatasourceTree()
                    && art.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()
                    && art.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()))) {
                return new Node[]{};
            }

            return new Node[]{new DataResultFilterNode(key, sourceEm)};
        }
    }

    @NbBundle.Messages("DataResultFilterNode.viewSourceArtifact.text=View Source Result")
    /**
     * Uses the default nodes actions per node, adds some custom ones and
     * returns them per visited node type
     */
    private static class GetPopupActionsDisplayableItemNodeVisitor extends DisplayableItemNodeVisitor.Default<List<Action>> {

        @Override
        public List<Action> visit(BlackboardArtifactNode ban) {
            return Arrays.asList(ban.getActions(true));
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
            return Arrays.asList(ditem.getActions(true));
        }
    }

    /*
     * Action for double-click / preferred action on nodes.
     */
    private class GetPreferredActionsDisplayableItemNodeVisitor extends DisplayableItemNodeVisitor.Default<AbstractAction> {

        @Override
        public AbstractAction visit(InstanceCountNode icn) {
            return null;
        }

        @Override
        public AbstractAction visit(InstanceCaseNode icn) {
            return null;
        }

        @Override
        public AbstractAction visit(InstanceDataSourceNode icn) {
            return null;
        }

        @Override
        public AbstractAction visit(CommonAttributeValueNode md5n) {
            return null;
        }

        @Override
        public AbstractAction visit(CaseDBCommonAttributeInstanceNode fin) {
            return null;
        }

        @Override
        public AbstractAction visit(CentralRepoCommonAttributeInstanceNode iccan) {
            return null;
        }

        @Override
        public AbstractAction visit(BlackboardArtifactNode ban) {

            Action preferredAction = ban.getPreferredAction();
            if (preferredAction instanceof AbstractAction) {
                return (AbstractAction) preferredAction;
            }

            BlackboardArtifact artifact = ban.getArtifact();
            try {
                if ((artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID())
                        || (artifact.getArtifactTypeID() == ARTIFACT_TYPE.TSK_MESSAGE.getTypeID())) {
                    if (artifact.hasChildren()) {
                        return openChild(ban);
                    }
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, MessageFormat.format("Error getting children from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
            }
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
            if (sourceEm == null || sourceEm.getSelectedNodes().length == 0) {
                return null;
            }
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
            if (sourceEm == null) {
                return null;
            }
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
