/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.datamodel.FileTypes.FileTypesNode;
import org.sleuthkit.autopsy.commonpropertiessearch.InstanceCountNode;
import org.sleuthkit.autopsy.commonpropertiessearch.InstanceCaseNode;
import org.sleuthkit.autopsy.commonpropertiessearch.InstanceDataSourceNode;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeValueNode;
import org.sleuthkit.autopsy.commonpropertiessearch.CentralRepoCommonAttributeInstanceNode;
import org.sleuthkit.autopsy.datamodel.LayoutFileNode;
import org.sleuthkit.autopsy.datamodel.LocalFileNode;
import org.sleuthkit.autopsy.datamodel.LocalDirectoryNode;
import org.sleuthkit.autopsy.datamodel.NodeSelectionInfo;
import org.sleuthkit.autopsy.datamodel.Reports;
import org.sleuthkit.autopsy.datamodel.SlackFileNode;
import org.sleuthkit.autopsy.commonpropertiessearch.CaseDBCommonAttributeInstanceNode;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import static org.sleuthkit.autopsy.directorytree.Bundle.DataResultFilterNode_viewSourceArtifact_text;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.ExtractArchiveWithPasswordAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A node used to wrap another node before passing it to the result viewers. The
 * wrapper node defines the actions associated with the wrapped node and may
 * filter out some of its children.
 */
public class DataResultFilterNode extends FilterNode {

    private static final Logger LOGGER = Logger.getLogger(DataResultFilterNode.class.getName());

    private static boolean filterKnownFromDataSources = UserPreferences.hideKnownFilesInDataSourcesTree();
    private static boolean filterKnownFromViews = UserPreferences.hideKnownFilesInViewsTree();
    private static boolean filterSlackFromDataSources = UserPreferences.hideSlackFilesInDataSourcesTree();
    private static boolean filterSlackFromViews = UserPreferences.hideSlackFilesInViewsTree();

    static {
        UserPreferences.addChangeListener(new PreferenceChangeListener() {
            @Override
            public void preferenceChange(PreferenceChangeEvent evt) {
                switch (evt.getKey()) {
                    case UserPreferences.HIDE_KNOWN_FILES_IN_DATA_SRCS_TREE:
                        filterKnownFromDataSources = UserPreferences.hideKnownFilesInDataSourcesTree();
                        break;
                    case UserPreferences.HIDE_KNOWN_FILES_IN_VIEWS_TREE:
                        filterKnownFromViews = UserPreferences.hideKnownFilesInViewsTree();
                        break;
                    case UserPreferences.HIDE_SLACK_FILES_IN_DATA_SRCS_TREE:
                        filterSlackFromDataSources = UserPreferences.hideSlackFilesInDataSourcesTree();
                        break;
                    case UserPreferences.HIDE_SLACK_FILES_IN_VIEWS_TREE:
                        filterSlackFromViews = UserPreferences.hideSlackFilesInViewsTree();
                        break;
                }
            }
        });
    }

    static private final DisplayableItemNodeVisitor<List<Action>> getActionsDIV = new GetPopupActionsDisplayableItemNodeVisitor();
    private final DisplayableItemNodeVisitor<AbstractAction> getPreferredActionsDIV = new GetPreferredActionsDisplayableItemNodeVisitor();

    private final ExplorerManager sourceEm;

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
     * Constructs a node used to wrap another node before passing it to the
     * result viewers. The wrapper node defines the actions associated with the
     * wrapped node and may filter out some of its children.
     *
     * @param node        The node to wrap.
     * @param em          The ExplorerManager for the component that is creating
     *                    the node.
     * @param filterKnown Whether or not to filter out children that represent
     *                    known files.
     * @param filterSlack Whether or not to filter out children that represent
     *                    virtual slack space files.
     */
    private DataResultFilterNode(Node node, ExplorerManager em, boolean filterKnown, boolean filterSlack) {
        super(node, new DataResultFilterChildren(node, em, filterKnown, filterSlack));
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
     * Gets the display name for the wrapped node.
     *
     * OutlineView used in the DataResult table uses getDisplayName() to
     * populate the first column, which is Source File.
     *
     * Hence this override to return the 'correct' displayName for the wrapped
     * node.
     *
     * @return The display name for the node.
     */
    @Override
    public String getDisplayName() {
        final Node orig = getOriginal();
        String name = orig.getDisplayName();
        if ((orig instanceof BlackboardArtifactNode)) {
            name = ((BlackboardArtifactNode) orig).getSourceName();
        }
        return name;
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

        private boolean filterKnown;
        private boolean filterSlack;
        private boolean filterArtifacts;    // display message artifacts in the DataSource subtree

        /**
         * the constructor
         */
        private DataResultFilterChildren(Node arg, ExplorerManager sourceEm) {
            super(arg);

            this.filterArtifacts = false;
            switch (SelectionContext.getSelectionContext(arg)) {
                case DATA_SOURCES:
                    filterSlack = filterSlackFromDataSources;
                    filterKnown = filterKnownFromDataSources;
                    filterArtifacts = true;
                    break;
                case VIEWS:
                    filterSlack = filterSlackFromViews;
                    filterKnown = filterKnownFromViews;
                    break;
                default:
                    filterSlack = false;
                    filterKnown = false;
                    break;
            }
            this.sourceEm = sourceEm;
        }

        private DataResultFilterChildren(Node arg, ExplorerManager sourceEm, boolean filterKnown, boolean filterSlack) {
            super(arg);
            this.filterKnown = filterKnown;
            this.filterSlack = filterSlack;
            this.sourceEm = sourceEm;
        }

        @Override
        protected Node[] createNodes(Node key) {
            AbstractFile file = key.getLookup().lookup(AbstractFile.class);
            if (file != null) {
                if (filterKnown && (file.getKnown() == TskData.FileKnown.KNOWN)) {
                    // Filter out child nodes that represent known files
                    return new Node[]{};
                }
                if (filterSlack && file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)) {
                    // Filter out child nodes that represent slack files
                    return new Node[]{};
                }
            }

            // filter out all non-message artifacts, if displaying the results from the Data Source tree
            BlackboardArtifact art = key.getLookup().lookup(BlackboardArtifact.class);
            if (art != null
                    && filterArtifacts
                    && art.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()
                    && art.getArtifactTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()) {
                return new Node[]{};
            }

            return new Node[]{new DataResultFilterNode(key, sourceEm, filterKnown, filterSlack)};
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
            //set up actions for artifact node based on its Content object
            //TODO all actions need to be consolidated in single place!
            //they should be set in individual Node subclass and using a utility to get Actions per Content sub-type
            // TODO UPDATE: There is now a DataModelActionsFactory utility;

            List<Action> actionsList = new ArrayList<>();

            //merge predefined specific node actions if bban subclasses have their own
            for (Action a : ban.getActions(true)) {
                actionsList.add(a);
            }
            BlackboardArtifact ba = ban.getLookup().lookup(BlackboardArtifact.class);
            final int artifactTypeID = ba.getArtifactTypeID();

            if (artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()
                    || artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                if (ban.getLookup().lookup(AbstractFile.class) != null) {
                    // We only want the "View File in Directory" actions if we have a file...it is
                    // possible that we have a keyword hit on a Report.
                    actionsList.add(new ViewContextAction(
                            NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewFileInDir.text"), ban));
                }
            } else if (artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()) {
                //action to go to the source artifact
                actionsList.add(new ViewSourceArtifactAction(DataResultFilterNode_viewSourceArtifact_text(), ba));
                // action to go to the source file of the artifact
                actionsList.add(new ViewContextAction(
                        NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewSrcFileInDir.text"), ban));
            } else {
                // if the artifact links to another file, add an action to go to
                // that file
                Content c = findLinked(ban);
                if (c != null) {
                    actionsList.add(new ViewContextAction(
                            NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewFileInDir.text"), c));
                }
                // action to go to the source file of the artifact
                // action to go to the source file of the artifact
                Content fileContent = ban.getLookup().lookup(AbstractFile.class);
                if (fileContent == null) {
                    Content content = ban.getLookup().lookup(Content.class);
                    actionsList.add(new ViewContextAction("View Source Content in Directory", content));
                } else {
                    actionsList.add(new ViewContextAction(
                            NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewSrcFileInDir.text"), ban));
                }
            }
            Content c = ban.getLookup().lookup(File.class);
            Node n = null;
            if (c != null) {
                n = new FileNode((AbstractFile) c);
            } else if ((c = ban.getLookup().lookup(Directory.class)) != null) {
                n = new DirectoryNode((Directory) c);
            } else if ((c = ban.getLookup().lookup(VirtualDirectory.class)) != null) {
                n = new VirtualDirectoryNode((VirtualDirectory) c);
            } else if ((c = ban.getLookup().lookup(LocalDirectory.class)) != null) {
                n = new LocalDirectoryNode((LocalDirectory) c);
            } else if ((c = ban.getLookup().lookup(LayoutFile.class)) != null) {
                n = new LayoutFileNode((LayoutFile) c);
            } else if ((c = ban.getLookup().lookup(LocalFile.class)) != null
                    || (c = ban.getLookup().lookup(DerivedFile.class)) != null) {
                n = new LocalFileNode((AbstractFile) c);
                if (FileTypeExtensions.getArchiveExtensions().contains("." + ((AbstractFile) c).getNameExtension().toLowerCase())) {
                    try {
                        if (c.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED).size() > 0) {
                            actionsList.add(new ExtractArchiveWithPasswordAction((AbstractFile) c));
                        }
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.WARNING, "Unable to add unzip with password action to context menus", ex);
                    }
                }
            } else if ((c = ban.getLookup().lookup(SlackFile.class)) != null) {
                n = new SlackFileNode((SlackFile) c);
            } else if ((c = ban.getLookup().lookup(Report.class)) != null) {
                actionsList.addAll(DataModelActionsFactory.getActions(c, false));
            }
            if (n != null) {
                final Collection<AbstractFile> selectedFilesList
                        = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
                actionsList.add(null); // creates a menu separator
                actionsList.add(new NewWindowViewAction(
                        NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.viewInNewWin.text"), n));
                if (selectedFilesList.size() == 1) {
                    actionsList.add(new ExternalViewerAction(
                            NbBundle.getMessage(this.getClass(), "DataResultFilterNode.action.openInExtViewer.text"), n));
                } else {
                    actionsList.add(ExternalViewerShortcutAction.getInstance());
                }
                actionsList.add(null); // creates a menu separator
                actionsList.add(ExtractAction.getInstance());
                actionsList.add(null); // creates a menu separator
                actionsList.add(AddContentTagAction.getInstance());
                actionsList.add(AddBlackboardArtifactTagAction.getInstance());

                if (selectedFilesList.size() == 1) {
                    actionsList.add(DeleteFileContentTagAction.getInstance());
                }
            } else {
                // There's no specific file associated with the artifact, but
                // we can still tag the artifact itself
                actionsList.add(null);
                actionsList.add(AddBlackboardArtifactTagAction.getInstance());
            }

            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }

            if (n != null) {
                actionsList.addAll(ContextMenuExtensionPoint.getActions());
            }

            return actionsList;
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
