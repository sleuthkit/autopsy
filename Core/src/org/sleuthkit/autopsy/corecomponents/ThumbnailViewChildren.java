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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.File;

/**
 * Complementary class to ThumbnailViewNode
 */
class ThumbnailViewChildren extends FilterNode.Children {
    
    private static final IsSupportedContentVisitor isSupportedVisitor = new IsSupportedContentVisitor();

    private int totalChildren;

    /** the constructor */
    ThumbnailViewChildren(Node arg) {
        super(arg);
        this.totalChildren = 1;
    }

    @Override
    protected Node copyNode(Node arg0) {
        return new ThumbnailViewNode(arg0);
    }

    @Override
    protected Node[] createNodes(Node arg0) {
        if (arg0 != null && isSupported(arg0)) {
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
            Content content = node.getLookup().lookup(Content.class);
            if (content != null) {
                return content.accept(isSupportedVisitor);
            }
        }
        return false;
    }
    
    
    private static class IsSupportedContentVisitor extends ContentVisitor.Default<Boolean> {

        @Override
        public Boolean visit(File f) {
            String lowerName = f.getName().toLowerCase();
            // Note: only supports JPG, GIF, and PNG for now
            // TODO: replace giant OR with check if in list
            return f.getSize() > 0 && 
                    (lowerName.endsWith(".jpg")
                    || lowerName.endsWith(".jpeg")
                    || //node.getName().toLowerCase().endsWith(".jpe") ||
                    //node.getName().toLowerCase().endsWith(".jfif") ||
                    lowerName.endsWith(".gif")
                    || //node.getName().toLowerCase().endsWith(".bmp") ||
                    //node.getName().toLowerCase().endsWith(".tif") ||
                    //node.getName().toLowerCase().endsWith(".tiff") ||
                    //node.getName().toLowerCase().endsWith(".tga") ||
                    lowerName.endsWith(".png"));
        }
        
        @Override
        protected Boolean defaultVisit(Content cntnt) {
            return false;
        }
    } 
}
