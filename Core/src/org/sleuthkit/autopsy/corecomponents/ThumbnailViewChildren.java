/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-17 Basis Technology Corp.
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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Image;
import java.awt.Toolkit;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.SortOrder;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.corecomponents.ResultViewerPersistence.SortCriterion;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import static org.sleuthkit.autopsy.corecomponents.ResultViewerPersistence.loadSortCriteria;

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

    private static final Logger logger = Logger.getLogger(ThumbnailViewChildren.class.getName());

    static final int IMAGES_PER_PAGE = 200;
    private final Node parent;
    private final HashMap<Integer, List<Node>> pages = new HashMap<>();
    private int totalImages = 0;
    private int totalPages = 0;
    private int iconSize = ImageUtils.ICON_SIZE_MEDIUM;
    private final ThumbnailLoader thumbLoader;

    /**
     * the constructor
     *
     * @param arg
     * @param iconSize
     */
    ThumbnailViewChildren(Node arg, ThumbnailLoader thumbLoader) {
        super(true); //support lazy loading

        this.parent = arg;
        this.thumbLoader = thumbLoader;
    }

    @Override
    protected void addNotify() {
        super.addNotify();

        setupKeys();
    }

    int getTotalPages() {
        return totalPages;
    }

    int getTotalImages() {
        return totalImages;
    }

    private void setupKeys() {
        //divide the supported content into buckets
        totalImages = 0;
        //TODO when lazy loading of original nodes is fixed
        //we should be asking the datamodel for the children instead
        //and not counting the children nodes (which might not be preloaded at this point)
        final List<Node> suppContent = new ArrayList<>();
        for (Node child : parent.getChildren().getNodes()) {
            if (isSupported(child)) {
                ++totalImages;
                suppContent.add(child);
            }
        }
        //sort suppContent!
        Collections.sort(suppContent, getComparator());

        if (totalImages == 0) {
            return;
        }

        totalPages = 0;
        if (totalImages < IMAGES_PER_PAGE) {
            totalPages = 1;
        } else {
            totalPages = totalImages / IMAGES_PER_PAGE;
            if (totalPages % totalImages != 0) {
                ++totalPages;
            }
        }

        int prevImages = 0;
        for (int page = 1; page <= totalPages; ++page) {
            int toAdd = Math.min(IMAGES_PER_PAGE, totalImages - prevImages);
            List<Node> pageContent = suppContent.subList(prevImages, prevImages + toAdd);
            pages.put(page, pageContent);
            prevImages += toAdd;
        }

        Integer[] pageNums = new Integer[totalPages];
        for (int i = 0; i < totalPages; ++i) {
            pageNums[i] = i + 1;
        }
        setKeys(pageNums);
    }

    private synchronized Comparator<Node> getComparator() {
        Comparator<Node> comp = (node1, node2) -> 0;

        if (!(parent instanceof TableFilterNode)) {
            return comp;
        } else {
            List<SortCriterion> sortCriteria = loadSortCriteria((TableFilterNode) parent);

            //make a comparatator that will sort the nodes.
            return sortCriteria.stream()
                    .map(this::getCriterionComparator)
                    .collect(Collectors.reducing(Comparator::thenComparing))
                    .orElse(comp);

        }
    }

    private Comparator<Node> getCriterionComparator(SortCriterion criterion) {
        Comparator<Node> c = Comparator.comparing(node -> getPropertyValue(node, criterion.getProperty()),
                Comparator.nullsFirst(Comparator.naturalOrder()));
        return criterion.getSortOrder() == SortOrder.ASCENDING ? c : c.reversed();
    }

    private Comparable getPropertyValue(Node node, Node.Property<?> prop) {
        for (Node.PropertySet ps : node.getPropertySets()) {
            for (Node.Property<?> p : ps.getProperties()) {
                if (p.equals(prop)) {
                    try {
                        return (Comparable) p.getValue();
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
        return null;
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

    static boolean isSupported(Node node) {
        if (node != null) {
            Content content = node.getLookup().lookup(Content.class);
            if (content != null) {
                return ImageUtils.thumbnailSupported(content);
            }
        }
        return false;
    }

    public void setIconSize(int iconSize) {
        this.iconSize = iconSize;

    }

    /**
     * Node that wraps around original node and adds the bitmap icon
     * representing the picture
     */
    class ThumbnailViewNode extends FilterNode {

        private Logger logger = Logger.getLogger(ThumbnailViewNode.class.getName());

        private final Image waitingIcon = Toolkit.getDefaultToolkit().createImage(ThumbnailViewNode.class.getResource("/org/sleuthkit/autopsy/images/working_spinner.gif"));

        private SoftReference<Image> thumbCache = null;
        private int iconSize = ImageUtils.ICON_SIZE_MEDIUM;

        private ThumbnailLoadTask thumbTask;
        private Timer timer;
        private final ThumbnailLoader thumbLoader;

        /**
         * the constructor
         */
        private ThumbnailViewNode(Node arg, ThumbnailLoader thumbLoader) {
            super(arg, FilterNode.Children.LEAF);
            this.thumbLoader = thumbLoader;
        }

        @Override
        public String getDisplayName() {
            return StringUtils.abbreviate(super.getDisplayName(), 18);
        }

        @Override
        @NbBundle.Messages({"# {0} - file name",
            "ThumbnailViewNode.progressHandle.text=Generating thumbnail for {0}"})
        synchronized public Image getIcon(int type) {
            Image thumbnail = null;

            if (thumbCache != null) {
                thumbnail = thumbCache.get();
            }

            if (thumbnail != null) {
                return thumbnail;
            } else {
                final Content content = this.getLookup().lookup(Content.class);
                if (content == null) {
                    return ImageUtils.getDefaultThumbnail();
                }
                if (thumbTask == null || thumbTask.isDone()) {
                    thumbTask = new ThumbnailLoadTask(content);
                    thumbLoader.load(thumbTask);

                }
                if (timer == null) {
                    timer = new Timer(1, actionEvent -> fireIconChange());
                    timer.start();
                }
                return waitingIcon;
            }
        }

        synchronized public void setIconSize(int iconSize) {
            this.iconSize = iconSize;
            thumbCache = null;
            thumbTask = null;
        }

        private class ThumbnailLoadTask extends SwingWorker<Image, Object> {

            private final Content content;
            private final ProgressHandle progressHandle;

            ThumbnailLoadTask(Content content) {
                this.content = content;
                final String progressText = Bundle.ThumbnailViewNode_progressHandle_text(content.getName());
                progressHandle = ProgressHandle.createHandle(progressText);
            }

            private boolean cancel() {
                return this.cancel(true);
            }

            @Override
            protected Image doInBackground() throws Exception {
                progressHandle.start();
                return ImageUtils.getThumbnail(content, iconSize);
            }

            @Override
            protected void done() {
                super.done();
                try {
                    thumbCache = new SoftReference<>(super.get());
                    fireIconChange();
                } catch (CancellationException ex) {
                    //do nothing, it was cancelled
                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.SEVERE, "Error getting thumbnail icon for " + content.getName(), ex); //NON-NLS
                } finally {
                    progressHandle.finish();
                    if (timer != null) {
                        timer.stop();
                        timer = null;

                    }
                    thumbTask = null;
                }
            }
        }
    }

    static class ThumbnailLoader {

        private final ExecutorService executor = Executors.newFixedThreadPool(4,
                new ThreadFactoryBuilder().setNameFormat("Thumbnail-Loader-%d").build());

        private final List<Future<?>> futures = new ArrayList<>();

        synchronized void cancellAll() {
            futures.forEach(future -> future.cancel(true));
            futures.clear();
        }

        private synchronized void load(ThumbnailViewNode.ThumbnailLoadTask task) {
            futures.add(task);
            executor.submit(task);
        }
    }

    /**
     * Node representing page node, a parent of image nodes, with a name showing
     * children range
     */
    private class ThumbnailPageNode extends AbstractNode {

        ThumbnailPageNode(Integer pageNum) {
            super(new ThumbnailPageNodeChildren(pages.get(pageNum)), Lookups.singleton(pageNum));
            setName(Integer.toString(pageNum));
            int from = 1 + ((pageNum - 1) * IMAGES_PER_PAGE);
            int showImages = Math.min(IMAGES_PER_PAGE, totalImages - (from - 1));
            int to = from + showImages - 1;
            setDisplayName(from + "-" + to);

            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png"); //NON-NLS

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

            setKeys(new ArrayList<>());
        }

        @Override
        protected Node[] createNodes(Node wrapped) {
            if (wrapped != null) {
                final ThumbnailViewNode thumb = new ThumbnailViewNode(wrapped, thumbLoader);
                thumb.setIconSize(iconSize);
                return new Node[]{thumb};
            } else {
                return new Node[]{};
            }
        }
    }

}
