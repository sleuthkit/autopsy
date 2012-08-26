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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.TskException;

/**
 * This class wraps around nodes that are displayed in the directory tree and
 * hides files, '..', and other children that should not be displayed.
 *
 * @author jantonius
 */
class DirectoryTreeFilterChildren extends FilterNode.Children {

    /**
     * the constructor
     */
    public DirectoryTreeFilterChildren(Node arg) {
        super(arg);
    }

    @Override
    protected Node copyNode(Node arg0) {
        return new DirectoryTreeFilterNode(arg0, true);
    }

    protected Node copyNode(Node arg0, boolean createChildren) {
        return new DirectoryTreeFilterNode(arg0, createChildren);
    }

    @Override
    protected Node[] createNodes(Node origNode) {


        if (origNode == null || !(origNode instanceof DisplayableItemNode)) {
            return new Node[]{};
        } 

        final DisplayableItemNode diNode = (DisplayableItemNode) origNode;

        // filter out the FileNode and the "." and ".." directories
        // do not set childrens, if we know the node type is a leaf node type
        if (!diNode.isLeafTypeNode()
                || (origNode instanceof DirectoryNode
                && !isDotDirectory((DirectoryNode) origNode)
                && !isLeafDirectory((DirectoryNode) origNode))) {
            return new Node[]{this.copyNode(origNode, true)};
        } else {
            return new Node[]{this.copyNode(origNode, false)};
        }
    }

    /**
     * Don't show expansion button on leaves leaf: all children are (file) or
     * (directory named "." or "..")
     *
     * @param node
     * @return whether node is a leaf
     */
    private static boolean isLeafDirectory(DirectoryNode node) {
        Directory dir = node.getLookup().lookup(Directory.class);
        boolean ret = true;
        try {
            for (Content c : dir.getChildren()) {
                if (c instanceof Directory && (!((Directory) c).getName().equals(".")
                        && !((Directory) c).getName().equals(".."))) {
                    ret = false;
                    break;
                }
            }
        } catch (TskException ex) {
            Logger.getLogger(DirectoryTreeFilterChildren.class.getName())
                    .log(Level.WARNING, "Error getting directory children", ex);
            return false;
        }
        return ret;
    }

    /**
     * Helper to ignore the '.' and '..' directories
     */
    private static boolean isDotDirectory(DirectoryNode dir) {
        String name = dir.getDisplayName();
        return name.equals(DirectoryNode.DOTDIR) || name.equals(DirectoryNode.DOTDOTDIR);
    }

    /**
     * Return the children based on the current node given. If the node doesn't
     * have any directory or volume or image node inside it, it just returns
     * leaf.
     *
     * @param arg the node
     * @return children the children
     */
    public static Children createInstance(Node arg, boolean createChildren) {
        if (createChildren) {
            return new DirectoryTreeFilterChildren(arg);
        } else {
            return Children.LEAF;
        }
    }
}
