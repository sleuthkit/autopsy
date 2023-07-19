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

import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.AbstractContentNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.FileTypes.FileTypesNode;
import org.sleuthkit.autopsy.datamodel.LayoutFileNode;
import org.sleuthkit.autopsy.datamodel.LocalFileNode;
import org.sleuthkit.autopsy.datamodel.LocalDirectoryNode;
import org.sleuthkit.autopsy.datamodel.SlackFileNode;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.autopsy.datamodel.VolumeNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
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
    private final static Logger logger = Logger.getLogger(DirectoryTreeFilterChildren.class.getName());

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

    /*
     * This method takes in a node as an argument and will create a new one if
     * it should be displayed in the tree. If it is to be displayed, it also
     * figures out if it is a leaf or not (i.e. should it have a + sign in the
     * tree).
     *
     * It does NOT create children nodes
     */
    @Override
    protected Node[] createNodes(Node origNode) {
        if (origNode == null || !(origNode instanceof DisplayableItemNode)) {
            return new Node[]{};
        }

        // Shoudl this node be displayed in the tree or not 
        final DisplayableItemNode diNode = (DisplayableItemNode) origNode;
        if (diNode.accept(showItemV) == false) {
            //do not show
            return new Node[]{};
        }

        // If it is going to be displayed, then determine if it should
        // have a '+' next to it based on if it has children of itself.
        // We will filter out the "." and ".." directories
        final boolean isLeaf = diNode.accept(isLeafItemV);

        return new Node[]{this.copyNode(origNode, !isLeaf)};
    }

    /**
     * Don't show expansion button on leaves leaf: all children are (file) or
     * (directory named "." or "..")
     *
     * @param node
     *
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
                } else if(AbstractContentNode.contentHasVisibleContentChildren(c)){
                    //fie has children, such as derived files
                    ret = false;
                    break;
                }
            }
        } catch (TskException ex) {
            Logger.getLogger(DirectoryTreeFilterChildren.class.getName())
                    .log(Level.WARNING, "Error getting directory children", ex); //NON-NLS
            return false;
        }
        return ret;
    }

    private static boolean isLeafVolume(VolumeNode node) {
        Volume vol = node.getLookup().lookup(Volume.class);
        boolean ret = true;

        try {
            for (Content c : vol.getChildren()) {
                if (!(c instanceof LayoutFile)) {
                    ret = false;
                    break;
                }
            }

        } catch (TskException ex) {
            Logger.getLogger(DirectoryTreeFilterChildren.class.getName())
                    .log(Level.WARNING, "Error getting volume children", ex); //NON-NLS
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
     *
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

        private Boolean visitDeep(AbstractAbstractFileNode<? extends AbstractFile> node) {
            //is a leaf if has no children, or children are files not dirs
            boolean hasChildren = node.hasContentChildren();
            if (!hasChildren) {
                return true;
            }
            List<Content> derivedChildren = node.getContentChildren();
            //child of a file, must be a (derived) file too
            for (Content childContent : derivedChildren) {
                if ((childContent instanceof AbstractFile) && ((AbstractFile) childContent).isDir()) {
                    return false;
                } else {
                    if(AbstractContentNode.contentHasVisibleContentChildren(childContent)){
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public Boolean visit(FileNode fn) {
            return visitDeep(fn);
        }

        @Override
        public Boolean visit(LocalFileNode lfn) {
            return visitDeep(lfn);
        }

        @Override
        public Boolean visit(LayoutFileNode fn) {
            return visitDeep(fn);
        }
        
        @Override
        public Boolean visit(SlackFileNode sfn) {
            return visitDeep(sfn);
        }

        @Override
        public Boolean visit(VolumeNode vn) {
            return isLeafVolume(vn);
        }

        @Override
        public Boolean visit(VirtualDirectoryNode vdn) {
            return visitDeep(vdn);
        }

        @Override
        public Boolean visit(LocalDirectoryNode ldn) {
            return visitDeep(ldn);
        }

        @Override
        public Boolean visit(FileTypesNode ft) {
            return defaultVisit(ft);
        }

        @Override
        public Boolean visit(BlackboardArtifactNode bbafn) {
            // Only show Message arttifacts with children
            if ( (bbafn.getArtifact().getArtifactTypeID() == ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) ||             
                 (bbafn.getArtifact().getArtifactTypeID() == ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()) ) {
                 return bbafn.hasContentChildren();
            }
            
            return false;
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
            return fn.hasVisibleContentChildren();
        }

        @Override
        public Boolean visit(LocalFileNode lfn) {
            return lfn.hasVisibleContentChildren();
        }

        @Override
        public Boolean visit(LayoutFileNode ln) {
            return ln.hasVisibleContentChildren();
        }
        
        @Override
        public Boolean visit(SlackFileNode sfn) {
            return sfn.hasVisibleContentChildren();
        }

        @Override
        public Boolean visit(VirtualDirectoryNode vdn) {
            return true;
            //return vdn.hasContentChildren();
        }

        
        @Override
        public Boolean visit(LocalDirectoryNode ldn) {
            return true;
        }

        @Override
        public Boolean visit(FileTypesNode fileTypes) {
           return defaultVisit(fileTypes);
        }
        
        @Override
        public Boolean visit(BlackboardArtifactNode bbafn) {
            
            // Only show Message arttifacts with children
            if ( (bbafn.getArtifact().getArtifactTypeID() == ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()) ||             
                 (bbafn.getArtifact().getArtifactTypeID() == ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()) ) {
                 return bbafn.hasContentChildren();
            }
            
            return false;
        }

    }
}
