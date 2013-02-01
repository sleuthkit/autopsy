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
import java.util.List;
import org.sleuthkit.autopsy.datamodel.VolumeNode;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.ArtifactTypeNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.DerivedFileNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.EmailExtracted.EmailExtractedAccountNode;
import org.sleuthkit.autopsy.datamodel.EmailExtracted.EmailExtractedFolderNode;
import org.sleuthkit.autopsy.datamodel.EmailExtracted.EmailExtractedRootNode;
import org.sleuthkit.autopsy.datamodel.ExtractedContentNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.FileSearchFilterNode;
import org.sleuthkit.autopsy.datamodel.HashsetHits.HashsetHitsRootNode;
import org.sleuthkit.autopsy.datamodel.HashsetHits.HashsetHitsSetNode;
import org.sleuthkit.autopsy.datamodel.ImageNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsKeywordNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsListNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsRootNode;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.autopsy.datamodel.LayoutFileNode;
import org.sleuthkit.autopsy.datamodel.RecentFilesFilterNode;
import org.sleuthkit.autopsy.datamodel.RecentFilesNode;
import org.sleuthkit.autopsy.datamodel.SearchFiltersNode;
import org.sleuthkit.autopsy.datamodel.Tags.TagNodeRoot;
import org.sleuthkit.autopsy.datamodel.Tags.TagsNodeRoot;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.TskException;

/**
 * This class wraps nodes as they are passed to the DataResult viewers. It
 * defines the actions that the node should have.
 */
public class DataResultFilterNode extends FilterNode {

    private ExplorerManager sourceEm;
    private final DisplayableItemNodeVisitor<List<Action>> getActionsDIV;
    private final DisplayableItemNodeVisitor<AbstractAction> getPreferredActionsDIV;

    /**
     * the constructor
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

        final DisplayableItemNode originalNode;
        originalNode = (DisplayableItemNode) this.getOriginal();

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

    private static class GetPopupActionsDisplayableItemNodeVisitor extends DisplayableItemNodeVisitor.Default<List<Action>> {

        @Override
        public List<Action> visit(ImageNode img) {
            List<Action> actions = new ArrayList<Action>();
            
            //retain actions from the node if any
            for (Action a : img.getActions(true)) {
                actions.add(a);
            }
            
            actions.add(new NewWindowViewAction("View in New Window", img));
            actions.add(new FileSearchAction("Open File Search by Attributes"));
            actions.addAll(ExplorerNodeActionVisitor.getActions(img.getLookup().lookup(Content.class)));
            return actions;
        }

        @Override
        public List<Action> visit(VolumeNode vol) {
            List<Action> actions = new ArrayList<Action>();
            
            //retain actions from the node if any
            for (Action a : vol.getActions(true)) {
                actions.add(a);
            }
            
            actions.add(new NewWindowViewAction("View in New Window", vol));
            actions.addAll(ExplorerNodeActionVisitor.getActions(vol.getLookup().lookup(Content.class)));
            return actions;
        }

        @Override
        public List<Action> visit(DirectoryNode dir) {
            List<Action> actions = new ArrayList<Action>();
            
            //retain actions from the node if any
            for (Action a : dir.getActions(true)) {
                actions.add(a);
            }
            
            if (!dir.getDirectoryBrowseMode()) {
                actions.add(new ViewContextAction("View File in Directory", dir));
                actions.add(null); // creates a menu separator
            }
            actions.add(new NewWindowViewAction("View in New Window", dir));
            actions.add(null); // creates a menu separator
            actions.add(new ExtractAction("Extract Directory", dir));
            actions.add(null); // creates a menu separator
            actions.add(new TagFileAction(dir));
            return actions;
        }

        @Override
        public List<Action> visit(LayoutFileNode lf) {
            List<Action> actions = new ArrayList<Action>();
            
            //retain actions from the node if any
            for (Action a : lf.getActions(true)) {
                actions.add(a);
            }

            actions.add(new NewWindowViewAction("View in New Window", lf));
            actions.add(new ExternalViewerAction("Open in External Viewer", lf));
            actions.add(null); // creates a menu separator
            actions.add(new ExtractAction("Extract File", lf));
            actions.add(null); // creates a menu separator
            actions.add(new TagFileAction(lf));
            return actions;
        }

        @Override
        public List<Action> visit(VirtualDirectoryNode ld) {
            List<Action> actions = new ArrayList<Action>();
            
            //retain actions from the node if any
            for (Action a : ld.getActions(true)) {
                actions.add(a);
            }

            actions.add(new TagFileAction(ld));
            return actions;
        }
        
         @Override
        public List<Action> visit(DerivedFileNode dfn) {
            List<Action> actions = new ArrayList<Action>();
            
            //retain actions from the node if any
            for (Action a : dfn.getActions(true)) {
                actions.add(a);
            }

            actions.add(new NewWindowViewAction("View in New Window", dfn));
            actions.add(new ExternalViewerAction("Open in External Viewer", dfn));
            actions.add(null); // creates a menu separator
            actions.add(new ExtractAction("Extract", dfn)); //might not need this actions - already local file
            actions.add(new HashSearchAction("Search for files with the same MD5 hash", dfn));
            actions.add(null); // creates a menu separator
            actions.add(new TagFileAction(dfn));
            
            return actions;
        }

        @Override
        public List<Action> visit(FileNode f) {
            List<Action> actions = new ArrayList<Action>();
            
            //retain actions from the node if any
            for (Action a : f.getActions(true)) {
                actions.add(a);
            }
            
            if (!f.getDirectoryBrowseMode()) {
                actions.add(new ViewContextAction("View File in Directory", f));
                actions.add(null); // creates a menu separator
            }
            actions.add(new NewWindowViewAction("View in New Window", f));
            actions.add(new ExternalViewerAction("Open in External Viewer", f));
            actions.add(null); // creates a menu separator
            actions.add(new ExtractAction("Extract File", f));
            actions.add(new HashSearchAction("Search for files with the same MD5 hash", f));
            actions.add(null); // creates a menu separator
            actions.add(new TagFileAction(f));
            return actions;
        }

        @Override
        public List<Action> visit(BlackboardArtifactNode ban) {
            List<Action> actions = new ArrayList<Action>();

            //merge predefined specific node actions if bban subclasses have their own
            for (Action a : ban.getActions(true)) {
                actions.add(a);
            }
            BlackboardArtifact ba = ban.getLookup().lookup(BlackboardArtifact.class);
            final int artifactTypeID = ba.getArtifactTypeID();

            if (artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()
                    || artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                actions.add(new ViewContextAction("View File in Directory", ban));
            } else {
                Content c = findLinked(ban);
                if (c != null) {
                    actions.add(new ViewContextAction("View File in Directory", c));
                }
                actions.add(new ViewContextAction("View Source File in Directory", ban));
            }
            File f = ban.getLookup().lookup(File.class);
            LayoutFile lf = null;
            Directory d = null;
            if (f != null) {
                actions.add(null); // creates a menu separator
                actions.add(new NewWindowViewAction("View in New Window", new FileNode(f)));
                actions.add(new ExternalViewerAction("Open in External Viewer", new FileNode(f)));
                actions.add(null); // creates a menu separator
                actions.add(new ExtractAction("Extract File", new FileNode(f)));
                actions.add(new HashSearchAction("Search for files with the same MD5 hash", new FileNode(f)));

                //add file/result tag if itself is not a tag
                if (artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                        && artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                    actions.add(null); // creates a menu separator
                    actions.add(new TagFileAction(f));
                    actions.add(new TagResultAction(ba));
                }
            }
            if ((d = ban.getLookup().lookup(Directory.class)) != null) {
                actions.add(null); // creates a menu separator
                actions.add(new NewWindowViewAction("View in New Window", new DirectoryNode(d)));
                actions.add(new ExternalViewerAction("Open in External Viewer", new DirectoryNode(d)));
                actions.add(null); // creates a menu separator
                actions.add(new ExtractAction("Extract Directory", new DirectoryNode(d)));

                //add file/result tag if itself is not a tag
                if (artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                        && artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                    actions.add(null); // creates a menu separator
                    actions.add(new TagFileAction(d));
                    actions.add(new TagResultAction(ba));
                }
            } else if ((lf = ban.getLookup().lookup(LayoutFile.class)) != null) {
                actions.add(null); // creates a menu separator
                actions.add(new NewWindowViewAction("View in New Window", new LayoutFileNode(lf)));
                actions.add(new ExternalViewerAction("Open in External Viewer", new LayoutFileNode(lf)));
                actions.add(null); // creates a menu separator
                actions.add(new ExtractAction("Extract File", new LayoutFileNode(lf)));

                //add tag if itself is not a tag
                if (artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                        && artifactTypeID != BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                    actions.add(null); // creates a menu separator
                    actions.add(new TagFileAction(lf));
                    actions.add(new TagResultAction(ba));
                }
            }
            //if (artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
            //actions.add(null); // creates a menu separator
            //actions.add(new ResultDeleteAction("Delete Result", ba));
            //}

            return actions;
        }

        @Override
        public List<Action> visit(KeywordHitsRootNode khrn) {
            //List<Action> actions = new ArrayList<Action>();
            //actions.add(null); // creates a menu separator

            //actions.add(new ResultDeleteAction("Delete Results", BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT));
            //return actions;
            return super.visit(khrn);
        }

        @Override
        public List<Action> visit(KeywordHitsListNode khsn) {
            //TODO delete by list
            return super.visit(khsn);
        }

        @Override
        public List<Action> visit(KeywordHitsKeywordNode khmln) {
            //TODO delete by keyword hit
            return super.visit(khmln);
        }

        @Override
        protected List<Action> defaultVisit(DisplayableItemNode ditem) {
            return new ArrayList<Action>();
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
        public AbstractAction visit(BlackboardArtifactNode ban) {
            return new ViewContextAction("View in Directory", ban);
        }

        @Override
        public AbstractAction visit(ArtifactTypeNode atn) {
            return openChild(atn);
        }

        @Override
        public AbstractAction visit(TagNodeRoot tnr) {
            return openChild(tnr);
        }

        @Override
        public AbstractAction visit(TagsNodeRoot tnr) {
            return openChild(tnr);
        }

        @Override
        public AbstractAction visit(DirectoryNode dn) {
            if (dn.getDisplayName().equals(DirectoryNode.DOTDOTDIR)) {
                return openParent(dn);
            } else if (!dn.getDisplayName().equals(DirectoryNode.DOTDIR)) {
                return openChild(dn);
            } else {
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
            } else {
                return null;
            }
        }

        @Override
        public AbstractAction visit(DerivedFileNode dfn) {
            if (dfn.hasContentChildren()) {
                return openChild(dfn);
            } else {
                return null;
            }
        }

        @Override
        public AbstractAction visit(FileSearchFilterNode fsfn) {
            return openChild(fsfn);
        }

        @Override
        public AbstractAction visit(SearchFiltersNode sfn) {
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

        private AbstractAction openChild(AbstractNode node) {
            final Node[] parentNode = sourceEm.getSelectedNodes();
            final Node parentContext = parentNode[0];
            final Node original = node;

            return new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (parentContext != null) {
                        final int childrenNodesCount = parentContext.getChildren().getNodesCount();
                        for (int i = 0; i < childrenNodesCount; i++) {
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