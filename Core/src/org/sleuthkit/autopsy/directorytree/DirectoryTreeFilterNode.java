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
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractContentNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A node filter (decorator) that sets the actions for a node in the tree view
 * and wraps the Children object of the wrapped node with a
 * DirectoryTreeFilterChildren.
 */
class DirectoryTreeFilterNode extends FilterNode {

    private static final Logger logger = Logger.getLogger(DirectoryTreeFilterNode.class.getName());
    private static final Action collapseAllAction = new CollapseAction(NbBundle.getMessage(DirectoryTreeFilterNode.class, "DirectoryTreeFilterNode.action.collapseAll.text"));

    /**
     * Constructs node filter (decorator) that sets the actions for a node in
     * the tree view and wraps the Children object of the wrapped node with a
     * DirectoryTreeFilterChildren.
     *
     * @param nodeToWrap The node to wrap.
     * @param createChildren Whether to create the children of the wrapped node
     * or treat it a a leaf node.
     */
    DirectoryTreeFilterNode(Node nodeToWrap, boolean createChildren) {
        super(nodeToWrap,
                DirectoryTreeFilterChildren.createInstance(nodeToWrap, createChildren),
                new ProxyLookup(Lookups.singleton(nodeToWrap), nodeToWrap.getLookup()));
    }

    /**
     * Gets the display name for the wrapped node, possibly including a child
     * count in parentheses.
     *
     * @return The display name for the node.
     */
    @Override
    public String getDisplayName() {
        final Node orig = getOriginal();
        String name = orig.getDisplayName();

        if (orig instanceof AbstractContentNode) {
            AbstractFile file = getLookup().lookup(AbstractFile.class);
            if ((file != null) && (false == (orig instanceof BlackboardArtifactNode))) {
                try {
                    int numVisibleChildren = getVisibleChildCount(file);

                    name = name + " (" + numVisibleChildren + ")";  //NON-NLS

                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting children count to display for file: " + file, ex); //NON-NLS
                }
            } else if (orig instanceof BlackboardArtifactNode) {
                BlackboardArtifact artifact = ((BlackboardArtifactNode) orig).getArtifact();
                try {
                    int numAttachments = artifact.getChildrenCount();
                    name = name + " (" + numAttachments + ")";  //NON-NLS
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting chidlren count for atifact: " + artifact, ex); //NON-NLS
                }
            }
        }
        return name;
    }

    /**
     * Gets the number of visible children for a tree view node representing an
     * AbstractFile. Depending on the user preferences, known and/or slack files
     * will either be included or purged in the count.
     *
     * @param file The AbstractFile object whose children will be counted.
     *
     * @return The number of visible children.
     */
    private int getVisibleChildCount(AbstractFile file) throws TskCoreException {
        List<Content> childList = file.getChildren();

        int numVisibleChildren = childList.size();
        boolean purgeKnownFiles = UserPreferences.hideKnownFilesInDataSourcesTree();
        boolean purgeSlackFiles = UserPreferences.hideSlackFilesInDataSourcesTree();

        if (purgeKnownFiles || purgeSlackFiles) {
            // Purge known and/or slack files from the file count
            for (int i = 0; i < childList.size(); i++) {
                Content child = childList.get(i);
                if (child instanceof AbstractFile) {
                    AbstractFile childFile = (AbstractFile) child;
                    if ((purgeKnownFiles && childFile.getKnown() == TskData.FileKnown.KNOWN)
                            || (purgeSlackFiles && childFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)) {
                        numVisibleChildren--;
                    }
                } else if (child instanceof BlackboardArtifact) {

                    if (FilterNodeUtils.showMessagesInDatasourceTree()) {
                        // In older versions of Autopsy,  attachments were children of email/message artifacts
                        // and hence email/messages with attachments are shown in the directory tree.
                        BlackboardArtifact bba = (BlackboardArtifact) child;
                        // Only message type artifacts are displayed in the tree
                        if ((bba.getArtifactTypeID() != ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID())
                                && (bba.getArtifactTypeID() != ARTIFACT_TYPE.TSK_MESSAGE.getTypeID())) {
                            numVisibleChildren--;
                        }
                    } else {
                        numVisibleChildren--;
                    }
                }
            }
        }

        return numVisibleChildren;
    }

    /**
     * Gets the context mneu (right click menu) actions for the wrapped node.
     *
     * @param context Whether to find actions for context meaning or for the
     * node itself.
     *
     * @return
     */
    @Override
    public Action[] getActions(boolean context) {
        List<Action> actions = new ArrayList<>();
        actions.addAll(Arrays.asList(getNodeActions()));
        actions.add(collapseAllAction);
        return actions.toArray(new Action[actions.size()]);
    }

    /**
     * @return If lookup node is content, host, or person, returns super
     * actions. Otherwise, returns empty array.
     */
    private Action[] getNodeActions() {
        Lookup lookup = this.getLookup();
        if (lookup.lookup(Content.class) != null
                || lookup.lookup(Host.class) != null
                || lookup.lookup(Person.class) != null) {
            return super.getActions(true);
        } else {
            return new Action[0];
        }
    }

    /**
     * Get the wrapped node.
     *
     * @return
     */
    @Override
    public Node getOriginal() {
        return super.getOriginal();
    }

}
