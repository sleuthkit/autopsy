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

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.NodeEvent;
import org.openide.nodes.NodeListener;
import org.openide.nodes.NodeMemberEvent;
import org.openide.nodes.NodeReorderEvent;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.File;

/**
 * Complementary class to ThumbnailViewNode. Children node factory. Wraps around
 * original data result children nodes of the passed in parent node, and creates
 * filter nodes for the supported children nodes, adding the bitmap data. If
 * original nodes are lazy loaded, this will support lazy loading. Currently, we
 * add a page node hierarchy to divide children nodes into "pages".
 *
 * Filter-node like class, but adds additional hierarchy (pages) as parents of
 * the filtered nodes.
 */
class ThumbnailViewChildren extends Children.Keys<Integer> {

    private static final IsSupportedContentVisitor isSupportedVisitor = new IsSupportedContentVisitor();
    private static final int IMAGES_PER_PAGE = 1000;
    private Node parent;
    private final HashMap<Integer, List<Node>> pages = new HashMap<Integer, List<Node>>();
    private int totalImages = 0;
    private static final Logger logger = Logger.getLogger(ThumbnailViewChildren.class.getName());

    /**
     * the constructor
     */
    ThumbnailViewChildren(Node arg) {
        super(true); //support lazy loading

        this.parent = arg;
        //
    }

    //   @Override
//    protected Node copyNode(Node arg0) {
    //      return new ThumbnailViewNode(arg0);
//    }
    @Override
    protected void addNotify() {
        super.addNotify();

        setupKeys();
    }

    private void setupKeys() {
        //divide the supported content into buckets
        totalImages = 0;
        //TODO when lazy loading of original nodes is fixed
        //we should be asking the datamodel for the children instead
        //and not counting the children nodes (which might not be preloaded at this point)
        final List<Node> suppContent = new ArrayList<Node>();
        for (Node child : parent.getChildren().getNodes()) {
            if (isSupported(child)) {
                ++totalImages;
                //Content content = child.getLookup().lookup(Content.class);
                //suppContent.add(content);
                suppContent.add(child);
            }
        }

        if (totalImages == 0) {
            return;
        }

        int totalPages = totalImages / IMAGES_PER_PAGE;
        if (totalPages % totalImages != 0) {
            ++totalPages;
        }

        int prevImages = 0;
        for (int page = 1; page <= totalPages; ++page) {
            int toAdd = Math.min(IMAGES_PER_PAGE, totalImages - prevImages);
            List<Node> pageContent = suppContent.subList(prevImages, prevImages + toAdd);
            pages.put(page, pageContent);
            prevImages += toAdd;
        }
        
        Integer [] pageNums = new Integer[totalPages];
        for (int i = 0; i< totalPages; ++i) {
            pageNums[i] = i+1;
        }
        setKeys(pageNums);


    }

    @Override
    protected void removeNotify() {
        super.removeNotify();
        pages.clear();
        totalImages = 0;
    }

    @Override
    protected Node[] createNodes(Integer pageNum) {
        final ThumbnailPageNode pageNode = new ThumbnailPageNode(pageNum);
        return new Node[]{pageNode};
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
            return f.getSize() > 0
                    && (lowerName.endsWith(".jpg")
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

    /**
     * Node representing page node, a parent of image nodes, with a name showing children range
     */
    private class ThumbnailPageNode extends AbstractNode {

        ThumbnailPageNode(Integer pageNum) {
            super(new ThumbnailPageNodeChildren(pages.get(pageNum)), Lookups.singleton(pageNum));
            setName(Integer.toString(pageNum));
            int from = 1 + ((pageNum-1) * IMAGES_PER_PAGE);
            int showImages = Math.min(IMAGES_PER_PAGE, totalImages - (from-1));
            int to = from + showImages - 1;
            setDisplayName(from + "-" + to);

            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png");
            
            /**
            this.addNodeListener(new NodeListener() {

                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    logger.log(Level.INFO, "evt: " + evt.getPropertyName() + " " + evt.getOldValue() + " " + evt.getNewValue());
                }

                
                @Override
                public void childrenRemoved(NodeMemberEvent nme) {
logger.log(Level.INFO, "CHILDREN REMOVED");
                }

                
                @Override
                public void childrenReordered(NodeReorderEvent nre) {
      
                }

                @Override
                public void nodeDestroyed(NodeEvent ne) {
       logger.log(Level.INFO, "NODE DESTROYED");
                }

                
                @Override
                public void childrenAdded(NodeMemberEvent nme) {

            logger.log(Level.INFO, "CHILDREN ADDED, delta; " + nme.getDeltaIndices().length);
            
                }
                
            });
            */

        }
        
        
    
    }
    
    //TODO insert node at beginning pressing which goes back to page view
    private class ThumbnailPageNodeChildren extends Children.Keys<Node> { 

        //wrapped original nodes
        private List<Node> contentImages = null;

        ThumbnailPageNodeChildren(List<Node> contentImages) {
            super(true);

            this.contentImages = contentImages;
        }

     
        @Override
        protected void addNotify() {
            super.addNotify();
            
            setKeys(contentImages);
        }

        @Override
        protected void removeNotify() {
            super.removeNotify();
        }
        
        


        @Override
        protected Node[] createNodes(Node wrapped) {
            if (wrapped != null) {
                final ThumbnailViewNode thumb = new ThumbnailViewNode(wrapped);
                return new Node[]{thumb};
            } else {
                return new Node[]{};
            }
        }

        
    }
    
}
