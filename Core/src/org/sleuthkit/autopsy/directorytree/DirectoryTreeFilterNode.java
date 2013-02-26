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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractContentNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.ingest.IngestDialog;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This class sets the actions for the nodes in the directory tree and creates
 * the children filter so that files and such are hidden from the tree.
 *
 */
class DirectoryTreeFilterNode extends FilterNode {

    private static final Action collapseAll = new CollapseAction("Collapse All");
    private static final Logger logger = Logger.getLogger(DirectoryTreeFilterNode.class.getName());

    /**
     * the constructor
     */
    DirectoryTreeFilterNode(Node arg, boolean createChildren) {
        super(arg, DirectoryTreeFilterChildren.createInstance(arg, createChildren),
                new ProxyLookup(Lookups.singleton(new OriginalNode(arg)),
                arg.getLookup()));
    }

    @Override
    public String getDisplayName() {
        final Node orig = getOriginal();

        String name = orig.getDisplayName();

        //do not show children counts for non content nodes
        if (orig instanceof AbstractContentNode) {
            //show only for file content nodes
            AbstractFile file = getLookup().lookup(AbstractFile.class);
            if (file != null) {
                try {
                    final int numChildren = file.getChildrenCount();
                    name = name + " (" + numChildren + ")";
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error getting children count to display for file: " + file, ex);
                }

            }
        }

        return name;
    }

    /**
     * Right click action for the nodes in the directory tree.
     *
     * @param popup
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        List<Action> actions = new ArrayList<Action>();

        Content c = this.getLookup().lookup(Content.class);
        if (c != null) {
            actions.addAll(DirectoryTreeFilterNode.getDetailActions(c));

            Directory dir = this.getLookup().lookup(Directory.class);
            if (dir != null) {
                actions.add(new ExtractAction("Extract Directory",
                        getOriginal()));
            }
            final Image img = this.getLookup().lookup(Image.class);
            if (img != null) {
                actions.add(new FileSearchAction("Open File Search by Attributes"));
                actions.add(new AbstractAction("Restart Ingest Modules") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        final IngestDialog ingestDialog = new IngestDialog();
                        ingestDialog.setImage(img);
                        ingestDialog.display();
                    }
                });
            }
        }

        //check if delete actions should be added
        final Node orig = getOriginal();
        //TODO add a mechanism to determine if DisplayableItemNode
        if (orig instanceof DisplayableItemNode) {
            actions.addAll(getDeleteActions((DisplayableItemNode) orig));
        }

        actions.add(collapseAll);
        return actions.toArray(new Action[actions.size()]);
    }

    private static List<Action> getDeleteActions(DisplayableItemNode original) {
        List<Action> actions = new ArrayList<Action>();
        //actions.addAll(original.accept(getDeleteActionVisitor));
        return actions;
    }

    private static List<Action> getDetailActions(Content c) {
        List<Action> actions = new ArrayList<Action>();

        actions.addAll(ExplorerNodeActionVisitor.getActions(c));

        return actions;
    }
}

class OriginalNode {

    private Node original;

    OriginalNode(Node original) {
        this.original = original;
    }

    Node getNode() {
        return original;
    }
}