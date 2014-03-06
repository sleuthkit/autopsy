/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2013 Basis Technology Corp.
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

import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.autopsy.datamodel.VolumeNode;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.ArtifactTypeNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.ContentTagTypeNode;
import org.sleuthkit.autopsy.datamodel.LocalFileNode;
import org.sleuthkit.autopsy.datamodel.DeletedContent.DeletedContentsChildren.DeletedContentNode;
import org.sleuthkit.autopsy.datamodel.DeletedContent.DeletedContentsNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.EmailExtracted.EmailExtractedAccountNode;
import org.sleuthkit.autopsy.datamodel.EmailExtracted.EmailExtractedFolderNode;
import org.sleuthkit.autopsy.datamodel.EmailExtracted.EmailExtractedRootNode;
import org.sleuthkit.autopsy.datamodel.ExtractedContentNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.FileTypeNode;
import org.sleuthkit.autopsy.datamodel.FileSize.FileSizeRootChildren.FileSizeNode;
import org.sleuthkit.autopsy.datamodel.FileSize.FileSizeRootNode;
import org.sleuthkit.autopsy.datamodel.HashsetHits.HashsetHitsRootNode;
import org.sleuthkit.autopsy.datamodel.HashsetHits.HashsetHitsSetNode;
import org.sleuthkit.autopsy.datamodel.InterestingHits.InterestingHitsRootNode;
import org.sleuthkit.autopsy.datamodel.InterestingHits.InterestingHitsSetNode;
import org.sleuthkit.autopsy.datamodel.ImageNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsKeywordNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsListNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsRootNode;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.autopsy.datamodel.LayoutFileNode;
import org.sleuthkit.autopsy.datamodel.RecentFilesFilterNode;
import org.sleuthkit.autopsy.datamodel.RecentFilesNode;
import org.sleuthkit.autopsy.datamodel.FileTypesNode;
import org.sleuthkit.autopsy.datamodel.TagNameNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
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
     * @param em ExplorerManager for component that is creating the node
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
     * @return actions
     */
    @Override
    public Action[] getActions(boolean popup) {

        List<Action> actions = new ArrayList<>();

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
            // TODO UPDATE: There is now a DataModelActionsFactory utility; also tags are no longer artifacts so conditionals
            // can be removed.

            List<Action> actions = new ArrayList<>();

            //merge predefined specific node actions if bban subclasses have their own
            for (Action a : ban.getActions(true)) {
                actions.add(a);
            }
            BlackboardArtifact ba = ban.getLookup().lookup(BlackboardArtifact.class);
            final int artifactTypeID = ba.getArtifactTypeID();

            if (artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()
                    || artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() ) {
                actions.add(new ViewContextAction("View File in Directory", ban));
            } else {
                // if the artifact links to another file, add an action to go to
                // that file
                Content c = findLinked(ban);
                if (c != null) {
                    actions.add(new ViewContextAction("View File in Directory", c));
                }
                // action to go to the source file of the artifact
                actions.add(new ViewContextAction("View Source File in Directory", ban));
            }
            File f = ban.getLookup().lookup(File.class);
            LayoutFile lf = null;
            AbstractFile locF = null;
            Directory d = null;
            VirtualDirectory vd = null;
            if (f != null) {
                final FileNode fn = new FileNode(f);
                actions.add(null); // creates a menu separator
                actions.add(new NewWindowViewAction("View in New Window", fn));
                actions.add(new ExternalViewerAction("Open in External Viewer", fn));
                actions.add(null); // creates a menu separator
                actions.add(ExtractAction.getInstance());
                actions.add(new HashSearchAction("Search for files with the same MD5 hash", fn));

                //add file/result tag if itself is not a tag
                if (artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                        && artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                    actions.add(null); // creates a menu separator
                    actions.add(AddContentTagAction.getInstance());
                    actions.add(AddBlackboardArtifactTagAction.getInstance());
                    actions.addAll(ContextMenuExtensionPoint.getActions());
                }
            }
            if ((d = ban.getLookup().lookup(Directory.class)) != null) {
                DirectoryNode dn = new DirectoryNode(d);
                actions.add(null); // creates a menu separator
                actions.add(new NewWindowViewAction("View in New Window", dn));
                actions.add(new ExternalViewerAction("Open in External Viewer", dn));
                actions.add(null); // creates a menu separator
                actions.add(ExtractAction.getInstance());

                //add file/result tag if itself is not a tag
                if (artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                        && artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                    actions.add(null); // creates a menu separator
                    actions.add(AddContentTagAction.getInstance());
                    actions.add(AddBlackboardArtifactTagAction.getInstance());
                    actions.addAll(ContextMenuExtensionPoint.getActions());
                }
            }
            if ((vd = ban.getLookup().lookup(VirtualDirectory.class)) != null) {
                VirtualDirectoryNode dn = new VirtualDirectoryNode(vd);
                actions.add(null); // creates a menu separator
                actions.add(new NewWindowViewAction("View in New Window", dn));
                actions.add(new ExternalViewerAction("Open in External Viewer", dn));
                actions.add(null); // creates a menu separator
                actions.add(ExtractAction.getInstance());

                //add file/result tag if itself is not a tag
                if (artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                        && artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                    actions.add(null); // creates a menu separator
                    actions.add(AddContentTagAction.getInstance());
                    actions.add(AddBlackboardArtifactTagAction.getInstance());
                    actions.addAll(ContextMenuExtensionPoint.getActions());
                }
            } else if ((lf = ban.getLookup().lookup(LayoutFile.class)) != null) {
                LayoutFileNode lfn = new LayoutFileNode(lf);
                actions.add(null); // creates a menu separator
                actions.add(new NewWindowViewAction("View in New Window", lfn));
                actions.add(new ExternalViewerAction("Open in External Viewer", lfn));
                actions.add(null); // creates a menu separator
                actions.add(ExtractAction.getInstance());

                //add tag if itself is not a tag
                if (artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                        && artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                    actions.add(null); // creates a menu separator
                    actions.add(AddContentTagAction.getInstance());
                    actions.add(AddBlackboardArtifactTagAction.getInstance());
                    actions.addAll(ContextMenuExtensionPoint.getActions());
                }
            } else if ((locF = ban.getLookup().lookup(LocalFile.class)) != null
                    || (locF = ban.getLookup().lookup(DerivedFile.class)) != null) {
                final LocalFileNode locfn = new LocalFileNode(locF);
                actions.add(null); // creates a menu separator
                actions.add(new NewWindowViewAction("View in New Window", locfn));
                actions.add(new ExternalViewerAction("Open in External Viewer", locfn));
                actions.add(null); // creates a menu separator
                actions.add(ExtractAction.getInstance());

                //add tag if itself is not a tag
                if (artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                        && artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                    actions.add(null); // creates a menu separator
                    actions.add(AddContentTagAction.getInstance());
                    actions.add(AddBlackboardArtifactTagAction.getInstance());
                    actions.addAll(ContextMenuExtensionPoint.getActions());
                }
            }

            return actions;
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
                    if (attr.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID.getTypeID()) {
                        switch (attr.getValueType()) {
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
                Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Error getting linked file", ex);
            }
            return c;
        }
    }

    /* 
     * Action for double-click / preferred action on nodes.   
     */
    private class GetPreferredActionsDisplayableItemNodeVisitor extends DisplayableItemNodeVisitor.Default<AbstractAction> {

        @Override
        public AbstractAction visit(ImageNode in) {
            return openChild(in);
        }

        @Override
        public AbstractAction visit(VolumeNode vn) {
            return openChild(vn);
        }

        @Override
        public AbstractAction visit(ExtractedContentNode ecn) {
            return openChild(ecn);
        }

        @Override
        public AbstractAction visit(KeywordHitsRootNode khrn) {
            return openChild(khrn);
        }

        @Override
        public AbstractAction visit(HashsetHitsRootNode hhrn) {
            return openChild(hhrn);
        }

        @Override
        public AbstractAction visit(HashsetHitsSetNode hhsn) {
            return openChild(hhsn);
        }
        
        @Override
        public AbstractAction visit(InterestingHitsRootNode iarn) {
            return openChild(iarn);
        }

        @Override
        public AbstractAction visit(InterestingHitsSetNode iasn) {
            return openChild(iasn);
        }
        
        @Override
        public AbstractAction visit(EmailExtractedRootNode eern) {
            return openChild(eern);
        }

        @Override
        public AbstractAction visit(EmailExtractedAccountNode eean) {
            return openChild(eean);
        }

        @Override
        public AbstractAction visit(EmailExtractedFolderNode eefn) {
            return openChild(eefn);
        }

        @Override
        public AbstractAction visit(RecentFilesNode rfn) {
            return openChild(rfn);
        }

        @Override
        public AbstractAction visit(DeletedContentsNode dcn) {
            return openChild(dcn);
        }

        @Override
        public AbstractAction visit(DeletedContentNode dcn) {
            return openChild(dcn);
        }

        @Override
        public AbstractAction visit(FileSizeRootNode fsrn) {
            return openChild(fsrn);
        }

        @Override
        public AbstractAction visit(FileSizeNode fsn) {
            return openChild(fsn);
        }

        @Override
        public AbstractAction visit(BlackboardArtifactNode ban) {
            return new ViewContextAction("View in Directory", ban);
        }

        @Override
        public AbstractAction visit(ArtifactTypeNode atn) {
            return openChild(atn);
        }

        @Override
        public AbstractAction visit(TagNameNode node) {
            return openChild(node);
        }

        @Override
        public AbstractAction visit(ContentTagTypeNode node) {
            return openChild(node);
        }

        @Override
        public AbstractAction visit(BlackboardArtifactTagTypeNode node) {
            return openChild(node);
        }
                
        @Override
        public AbstractAction visit(DirectoryNode dn) {
            if (dn.getDisplayName().equals(DirectoryNode.DOTDOTDIR)) {
                return openParent(dn);
            } 
            else if (dn.getDisplayName().equals(DirectoryNode.DOTDIR) == false) {
                return openChild(dn);
            } 
            else {
                return null;
            }
        }

        @Override
        public AbstractAction visit(VirtualDirectoryNode ldn) {
            return openChild(ldn);
        }

        @Override
        public AbstractAction visit(FileNode fn) {
            if (fn.hasContentChildren()) {
                return openChild(fn);
            } 
            else {
                return null;
            }
        }

        @Override
        public AbstractAction visit(LocalFileNode dfn) {
            if (dfn.hasContentChildren()) {
                return openChild(dfn);
            } 
            else {
                return null;
            }
        }

        @Override
        public AbstractAction visit(FileTypeNode fsfn) {
            return openChild(fsfn);
        }

        @Override
        public AbstractAction visit(FileTypesNode sfn) {
            return openChild(sfn);
        }

        @Override
        public AbstractAction visit(RecentFilesFilterNode rffn) {
            return openChild(rffn);
        }

        @Override
        public AbstractAction visit(KeywordHitsListNode khsn) {
            return openChild(khsn);
        }

        @Override
        public AbstractAction visit(KeywordHitsKeywordNode khmln) {
            return openChild(khmln);
        }

        @Override
        protected AbstractAction defaultVisit(DisplayableItemNode c) {
            return null;
        }

        /**
         * Tell the originating ExplorerManager to display the given dataModelNode. 
         * @param dataModelNode Original (non-filtered) dataModelNode to open
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
                        try {
                            sourceEm.setExploredContextAndSelection(newSelection, new Node[]{newSelection});
                        } catch (PropertyVetoException ex) {
                            Logger logger = Logger.getLogger(DataResultFilterNode.class.getName());
                            logger.log(Level.WARNING, "Error: can't open the selected directory.", ex);
                        }
                    }
                }
            };
        }

        /**
         * Tell the originating ExplorerManager to display the parent of the given node. 
         * @param node Original (non-filtered) node to open
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
                        logger.log(Level.WARNING, "Error: can't open the parent directory.", ex);
                    }
                }
            };
        }
    }
}
