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
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.datamodel.ImageNode;
import org.sleuthkit.autopsy.datamodel.VolumeNode;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.Log;
import org.sleuthkit.autopsy.datamodel.ArtifactTypeNode;
import org.sleuthkit.autopsy.datamodel.ExtractedContentNode;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.datamodel.FileSearchFilterNode;
import org.sleuthkit.autopsy.datamodel.HashsetHits.HashsetHitsRootNode;
import org.sleuthkit.autopsy.datamodel.ImagesNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsKeywordNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsListNode;
import org.sleuthkit.autopsy.datamodel.KeywordHits.KeywordHitsRootNode;
import org.sleuthkit.autopsy.datamodel.RecentFilesFilterNode;
import org.sleuthkit.autopsy.datamodel.RecentFilesNode;
import org.sleuthkit.autopsy.datamodel.ResultsNode;
import org.sleuthkit.autopsy.datamodel.SearchFiltersNode;
import org.sleuthkit.autopsy.datamodel.ViewsNode;
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

    /** the constructor */
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
    protected Node[] createNodes(Node arg0) {

        //TODO: ContentNode fix - replace with ContentVisitor

        // filter out the FileNode and the "." and ".." directories
        if (arg0 != null
                && (arg0 instanceof ImageNode
                || arg0 instanceof VolumeNode
                || (arg0 instanceof DirectoryNode
                && !isDotDirectory((DirectoryNode) arg0)
                && !isLeafDirectory((DirectoryNode) arg0))
                || arg0 instanceof ExtractedContentNode
                || arg0 instanceof SearchFiltersNode
                || arg0 instanceof RecentFilesNode
                || arg0 instanceof KeywordHitsRootNode
                || arg0 instanceof KeywordHitsListNode
                || arg0 instanceof ImagesNode
                || arg0 instanceof ViewsNode
                || arg0 instanceof ResultsNode)) {
            return new Node[]{this.copyNode(arg0)};
        } else if (arg0 != null
                && (arg0 instanceof KeywordHitsKeywordNode
                || (arg0 instanceof DirectoryNode
                && !isDotDirectory((DirectoryNode) arg0))
                || arg0 instanceof ArtifactTypeNode 
                || arg0 instanceof RecentFilesFilterNode
                || arg0 instanceof FileSearchFilterNode
                || arg0 instanceof HashsetHitsRootNode
                )) {
            return new Node[]{this.copyNode(arg0, false)};
        } else {
            return new Node[]{};
        }
    }

    /**
     * Don't show expansion button on leaves
     * leaf: all children are (file) or (directory named "." or "..")
     * @param node
     * @return whether node is a leaf
     */
    private static boolean isLeafDirectory(DirectoryNode node) {
        Directory dir = node.getLookup().lookup(Directory.class);
        boolean ret = true;
        try {
            for (Content c : dir.getChildren()) {
                if (c instanceof Directory && (!((Directory)c).getName().equals(".")
                        && !((Directory)c).getName().equals(".."))) {
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
        return name.equals(".") || name.equals("..");
    }

    /**
     * Return the children based on the current node given. If the node doesn't
     * have any directory or volume or image node inside it, it just returns leaf.
     * 
     * @param arg        the node
     * @return children  the children
     */
    public static Children createInstance(Node arg, boolean createChildren) {
        if (createChildren) {
            return new DirectoryTreeFilterChildren(arg);
        } else {
            return Children.LEAF;
        }
    }
}
