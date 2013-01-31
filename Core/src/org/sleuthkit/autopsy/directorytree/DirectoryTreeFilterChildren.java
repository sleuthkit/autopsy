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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.DerivedFileNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.LayoutFileNode;
import org.sleuthkit.autopsy.datamodel.VolumeNode;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;

/**
 * This class wraps around nodes that are displayed in the directory tree and
 * hides files, '..', and other children that should not be displayed. facility
 * to customize nodes view in dir tree: hide them or set no children
 */
class DirectoryTreeFilterChildren extends FilterNode.Children {

    private final ShowItemVisitor showItemV = new ShowItemVisitor();
    private final IsLeafItemVisitor isLeafItemV = new IsLeafItemVisitor();

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

        if (diNode.accept(showItemV) == false) {
            //do not show
            return new Node[]{};
        }

        // filter out the FileNode and the "." and ".." directories
        // do not set children, if we know the node type is a leaf node type
        final boolean isLeaf = diNode.accept(isLeafItemV);

        return new Node[]{this.copyNode(origNode, !isLeaf)};

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

    private static boolean isLeafVolume(VolumeNode node) {
        Volume vol = node.getLookup().lookup(Volume.class);
        boolean ret = true;

        try {
            for (Content c : vol.getChildren()) {
                if (! (c instanceof LayoutFile) ){
                    ret = false;
                    break;
                }
            }

        } catch (TskException ex) {
            Logger.getLogger(DirectoryTreeFilterChildren.class.getName())
                    .log(Level.WARNING, "Error getting volume children", ex);
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

    private static class IsLeafItemVisitor extends DisplayableItemNodeVisitor.Default<Boolean> {

        @Override
        protected Boolean defaultVisit(DisplayableItemNode c) {
            return c.isLeafTypeNode();
        }

        @Override
        public Boolean visit(DirectoryNode dn) {
            return isLeafDirectory(dn);
        }
        
        @Override
        public Boolean visit(FileNode fn) {
            return true; //return ! fn.hasContentChildren();
        }
        
        @Override
        public Boolean visit(DerivedFileNode dfn) {
            return true; //return ! dfn.hasContentChildren();
        }

        @Override
        public Boolean visit(VolumeNode vn) {
            return isLeafVolume(vn);
        }
    }

    private static class ShowItemVisitor extends DisplayableItemNodeVisitor.Default<Boolean> {

        @Override
        protected Boolean defaultVisit(DisplayableItemNode c) {
            return true;
        }

        @Override
        public Boolean visit(DirectoryNode dn) {
            if (isDotDirectory(dn)) {
                return false;
            }
            return true;
        }

        @Override
        public Boolean visit(FileNode fn) {
            return fn.hasContentChildren();
        }
        
        @Override
        public Boolean visit(DerivedFileNode dfn) {
            return dfn.hasContentChildren();
        }

        @Override
        public Boolean visit(LayoutFileNode ln) {
            return false;
        }
    }
}
