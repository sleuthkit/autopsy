/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractContentNode;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModulesAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * A node filter (decorator) that sets the actions for the nodes in the
 * directory tree and wraps the node children with a
 * DirectoryTreeFilterChildren.
 */
class DirectoryTreeFilterNode extends FilterNode {

    private static final Logger logger = Logger.getLogger(DirectoryTreeFilterNode.class.getName());
    private static final Action collapseAllAction = new CollapseAction(NbBundle.getMessage(DirectoryTreeFilterNode.class, "DirectoryTreeFilterNode.action.collapseAll.text"));

    /**
     * A node filter (decorator) that sets the actions for the nodes in the
     * directory tree and wraps the node children with a
     * DirectoryTreeFilterChildren.
     *
     * @param nodeToWrap     The node to wrap.
     * @param createChildren Whether to create the children of the wrapped node
     *                       or treat it a a leaf node.
     */
    DirectoryTreeFilterNode(Node nodeToWrap, boolean createChildren) {
        super(nodeToWrap,
                DirectoryTreeFilterChildren.createInstance(nodeToWrap, createChildren),
                new ProxyLookup(Lookups.singleton(new OriginalNode(nodeToWrap)), nodeToWrap.getLookup()));
    }

    /**
     * Gets the display name for the node, possibly including a child count in
     * parentheses.
     *
     * @return The display name for the node.
     */
    @Override
    public String getDisplayName() {
        final Node orig = getOriginal();
        String name = orig.getDisplayName();
        if (orig instanceof AbstractContentNode) {
            AbstractFile file = getLookup().lookup(AbstractFile.class);
            if (file != null) {
                try {
                    int numVisibleChildren = getVisibleChildCount(file);
                    
                    /*
                     * Left-to-right marks here are necessary to keep the count
                     * and parens together for mixed right-to-left and
                     * left-to-right names.
                     */
                    name = name + " \u200E(\u200E" + numVisibleChildren + ")\u200E";  //NON-NLS

                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting children count to display for file: " + file, ex); //NON-NLS
                }
            }
        }
        return name;
    }
    
    /**
     * This method gets the number of visible children. Depending on the user
     * preferences, slack files will either be included or purged in the count.
     * 
     * @param file The AbstractFile object whose children will be counted.
     *
     * @return The number of visible children.
     */
    private int getVisibleChildCount(AbstractFile file) throws TskCoreException {
        int numVisibleChildren = 0;
        List<Content> childList = file.getChildren();

        if(UserPreferences.hideSlackFilesInDataSourcesTree()) {
            // Purge slack files from the file count
            for(int i=0; i < childList.size(); i++) {
                AbstractFile childFile = (AbstractFile)childList.get(i);
                if(childFile.getType() != TskData.TSK_DB_FILES_TYPE_ENUM.SLACK) {
                    numVisibleChildren++;
                }
            }
        }
        else {
            // Include slack files in the file count
            numVisibleChildren = file.getChildrenCount();
        }
        
        return numVisibleChildren;
    }

    /**
     * Gets the context mneu (right click menu) actions for the node.
     *
     * @param context Whether to find actions for context meaning or for the
     *                node itself.
     *
     * @return
     */
    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = new ArrayList<>();
        final Content content = this.getLookup().lookup(Content.class);
        if (content != null) {
            actions.addAll(ExplorerNodeActionVisitor.getActions(content));

            Directory dir = this.getLookup().lookup(Directory.class);
            if (dir != null) {
                actions.add(ExtractAction.getInstance());
                actions.add(new RunIngestModulesAction(dir));
            }

            final Image img = this.getLookup().lookup(Image.class);
            final VirtualDirectory virtualDirectory = this.getLookup().lookup(VirtualDirectory.class);
            boolean isRootVD = false;
            if (virtualDirectory != null) {
                try {
                    if (virtualDirectory.getParent() == null) {
                        isRootVD = true;
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error determining the parent of the virtual directory", ex); // NON-NLS
                }
            }
            if (img != null || isRootVD) {
                actions.add(new FileSearchAction(NbBundle.getMessage(this.getClass(), "DirectoryTreeFilterNode.action.openFileSrcByAttr.text")));
                actions.add(new RunIngestModulesAction(Collections.<Content>singletonList(content)));
            }
        }
        actions.add(collapseAllAction);
        return actions.toArray(new Action[actions.size()]);
    }

    //FIXME: this seems like a big hack -jm
    public static class OriginalNode {

        private final Node original;

        OriginalNode(Node original) {
            this.original = original;
        }

        Node getNode() {
            return original;
        }
    }
}
