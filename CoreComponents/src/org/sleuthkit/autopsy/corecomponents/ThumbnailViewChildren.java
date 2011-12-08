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
package org.sleuthkit.autopsy.corecomponents;

import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.ContentNode;

/**
 * Complementary class to ThumbnailViewNode
 */
class ThumbnailViewChildren extends FilterNode.Children {

    private int totalChildren;

    /** the constructor */
    ThumbnailViewChildren(ContentNode arg) {
        super((Node) arg);
        this.totalChildren = 1;
    }

    @Override
    protected Node copyNode(Node arg0) {
        return new ThumbnailViewNode(arg0);
    }

    @Override
    protected Node[] createNodes(Node arg0) {
        // filter out the FileNode and the "." and ".." directories
        if (arg0 != null && //(arg0 instanceof FileNode &&
                isSupported(arg0)) {
            totalChildren++;
            return new Node[]{this.copyNode(arg0)};
        } else {
            return new Node[]{};
        }
    }

    public int childrenCount() {
        return this.totalChildren;
    }

    public static boolean isSupported(Node node) {
        if (node != null) {
            String lowerName = node.getDisplayName().toLowerCase();
            // Note: only supports JPG, GIF, and PNG for now
            // TODO: replace giant OR with check if in list
            return lowerName.endsWith(".jpg")
                    || lowerName.endsWith(".jpeg")
                    || //node.getName().toLowerCase().endsWith(".jpe") ||
                    //node.getName().toLowerCase().endsWith(".jfif") ||
                    lowerName.endsWith(".gif")
                    || //node.getName().toLowerCase().endsWith(".bmp") ||
                    //node.getName().toLowerCase().endsWith(".tif") ||
                    //node.getName().toLowerCase().endsWith(".tiff") ||
                    //node.getName().toLowerCase().endsWith(".tga") ||
                    lowerName.endsWith(".png");
        } else {
            return false;
        }
    }
}
