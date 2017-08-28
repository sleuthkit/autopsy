/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.nodes.Node.Property;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.directorytree.HashSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 *
 */
class KeywordSearchFilterNode extends FilterNode {

    KeywordSearchFilterNode(QueryResults highlights, Node original) {
        super(original, null, new ProxyLookup(Lookups.singleton(highlights), original.getLookup()));
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

                Property<?>[] oldProperties = ps.getProperties();

                int j = 0;
                for (Property<?> p : oldProperties) {
                    newPs.put(p);
                }

                propertySets[i] = newPs;
            }
        }

        return propertySets;
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
        actions.addAll(Arrays.asList(super.getActions(popup)));
        Content content = this.getOriginal().getLookup().lookup(Content.class);
        actions.addAll(content.accept(new GetPopupActionsContentVisitor()));

        return actions.toArray(new Action[actions.size()]);
    }

    private class GetPopupActionsContentVisitor extends ContentVisitor.Default<List<Action>> {

        @Override
        public List<Action> visit(File f) {
            return getFileActions(true);
        }

        @Override
        public List<Action> visit(DerivedFile f) {
            return getFileActions(true);
        }

        @Override
        public List<Action> visit(Directory d) {
            return getFileActions(false);
        }

        @Override
        public List<Action> visit(LayoutFile lf) {
            //we want hashsearch enabled on carved files but not unallocated blocks
            boolean enableHashSearch = (lf.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.CARVED);
            return getFileActions(enableHashSearch);
        }

        @Override
        public List<Action> visit(LocalFile lf) {
            return getFileActions(true);
        }

        @Override
        public List<Action> visit(SlackFile f) {
            return getFileActions(false);
        }

        @Override
        public List<Action> visit(VirtualDirectory dir) {
            return getFileActions(false);
        }

        private List<Action> getFileActions(boolean enableHashSearch) {
            List<Action> actionsList = new ArrayList<>();
            actionsList.add(new NewWindowViewAction(NbBundle.getMessage(this.getClass(), "KeywordSearchFilterNode.getFileActions.viewInNewWinActionLbl"), KeywordSearchFilterNode.this));
            actionsList.add(new ExternalViewerAction(NbBundle.getMessage(this.getClass(), "KeywordSearchFilterNode.getFileActions.openExternViewActLbl"), getOriginal()));
            actionsList.add(null);
            actionsList.add(ExtractAction.getInstance());
            Action hashSearchAction = new HashSearchAction(NbBundle.getMessage(this.getClass(), "KeywordSearchFilterNode.getFileActions.searchSameMd5"), getOriginal());
            hashSearchAction.setEnabled(enableHashSearch);
            actionsList.add(hashSearchAction);
            actionsList.add(null); // creates a menu separator
            actionsList.add(AddContentTagAction.getInstance());

            final Collection<AbstractFile> selectedFilesList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
            if (selectedFilesList.size() == 1) {
                actionsList.add(DeleteFileContentTagAction.getInstance());
            }

            actionsList.addAll(ContextMenuExtensionPoint.getActions());
            return actionsList;
        }

        @Override
        protected List<Action> defaultVisit(Content c) {
            return getFileActions(false);
        }
    }
}
