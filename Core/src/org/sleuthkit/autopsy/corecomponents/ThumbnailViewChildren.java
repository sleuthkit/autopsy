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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Image;
import java.awt.Toolkit;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
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
import static org.sleuthkit.autopsy.corecomponents.ResultViewerPersistence.loadSortCriteria;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;

/**
 * Complementary class to ThumbnailViewNode. Children node factory. Wraps around
 * original data result children nodes of the passed in parent node, and creates
 * filter nodes for the supported children nodes, adding the thumbnail. If
 * original nodes are lazy loaded, this will support lazy loading. We add a page
 * node hierarchy to divide children nodes into "pages".
 *
 * Filter-node like class, but adds additional hierarchy (pages) as parents of
 * the filtered nodes.
 */
class ThumbnailViewChildren extends Children.Keys<Integer> {

    private static final Logger logger = Logger.getLogger(ThumbnailViewChildren.class.getName());

    @NbBundle.Messages("ThumbnailViewChildren.progress.cancelling=(Cancelling)")
    private static final String CANCELLING_POSTIX = Bundle.ThumbnailViewChildren_progress_cancelling();
    static final int IMAGES_PER_PAGE = 200;

    private final ExecutorService executor = Executors.newFixedThreadPool(4,
            new ThreadFactoryBuilder().setNameFormat("Thumbnail-Loader-%d").build());
    private final List<ThumbnailLoadTask> tasks = new ArrayList<>();

    private final Node parent;
    private final List<List<Node>> pages = new ArrayList<>();
    private int thumbSize;

    /**
     * The constructor
     *
     * @param parent    The node which is the parent of this children.
     * @param thumbSize The hight and/or width of the thumbnails in pixels.
     */
    ThumbnailViewChildren(Node parent, int thumbSize) {
        super(true); //support lazy loading

        this.parent = parent;
        this.thumbSize = thumbSize;
    }

    @Override
    protected void addNotify() {
        super.addNotify();

        /*
         * TODO: When lazy loading of original nodes is fixed, we should be
         * asking the datamodel for the children instead and not counting the
         * children nodes (which might not be preloaded at this point).
         */
        // get list of supported children sorted by persisted criteria
        final List<Node> suppContent =
                Stream.of(parent.getChildren().getNodes())
                        .filter(ThumbnailViewChildren::isSupported)
                        .sorted(getComparator())
                        .collect(Collectors.toList());

        if (suppContent.isEmpty()) {
            //if there are no images, there is nothing more to do
            return;
        }

        //divide the supported content into buckets
        pages.addAll(Lists.partition(suppContent, IMAGES_PER_PAGE));

        //the keys are just the indices into the pages list.
        setKeys(IntStream.rangeClosed(0, pages.size()).boxed().collect(Collectors.toList()));
    }

    /**
     * Get a comparator for the child nodes loadeded from the persisted
     * sort criteria. The comparator is a composite one that applies all the sort
     * criteria at once.
     *
     * @return A Coparator used to sort the child nodes.
     */
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
        @SuppressWarnings("unchecked")
        Comparator<Node> c = Comparator.comparing(node -> getPropertyValue(node, criterion.getProperty()),
                Comparator.nullsFirst(Comparator.naturalOrder()));
        return criterion.getSortOrder() == SortOrder.ASCENDING ? c : c.reversed();
    }

    @SuppressWarnings("rawtypes")
    private Comparable getPropertyValue(Node node, Node.Property<?> prop) {
        for (Node.PropertySet ps : node.getPropertySets()) {
            for (Node.Property<?> p : ps.getProperties()) {
                if (p.equals(prop)) {
                    try {
                        if (p.getValue() instanceof Comparable) {
                            return (Comparable) p.getValue();
                        } else {
                            //if the value is not comparable use its string representation
                            return p.getValue().toString();
                        }

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
    }

    @Override
    protected Node[] createNodes(Integer pageNum) {
        return new Node[]{new ThumbnailPageNode(pageNum, pages.get(pageNum))};

    }

    private static boolean isSupported(Node node) {
        if (node != null) {
            Content content = node.getLookup().lookup(Content.class);
            if (content != null) {
                return ImageUtils.thumbnailSupported(content);
            }
        }
        return false;
    }

    public void setThumbsSize(int thumbSize) {
        this.thumbSize = thumbSize;
        for (Node page : getNodes()) {
            for (Node node : page.getChildren().getNodes()) {
                ((ThumbnailViewNode) node).setThumbSize(thumbSize);
            }
        }
    }

    synchronized void cancelLoadingThumbnails() {
        tasks.forEach(ThumbnailLoadTask::cancel);
        tasks.clear();
        executor.shutdownNow();
    }

    private synchronized ThumbnailLoadTask loadThumbnail(ThumbnailViewNode node, Content content) {
        if (executor.isShutdown() == false) {
            ThumbnailLoadTask task = new ThumbnailLoadTask(node, content, node.getThumbSize());
            tasks.add(task);
            executor.submit(task);
            return task;
        } else {
            return null;
        }
    }

    /**
     * Node that wraps around original node and adds the thumbnail representing
     * the image/video.
     */
    private class ThumbnailViewNode extends FilterNode {

        private final Logger logger = Logger.getLogger(ThumbnailViewNode.class.getName());

        private final Image waitingIcon = Toolkit.getDefaultToolkit().createImage(ThumbnailViewNode.class.getResource("/org/sleuthkit/autopsy/images/working_spinner.gif")); //NOI18N

        private SoftReference<Image> thumbCache = null;
        private int thumbSize;
        private final Content content;

        int getThumbSize() {
            return thumbSize;
        }

        private ThumbnailLoadTask thumbTask;
        private Timer timer;

        /**
         * The constructor
         *
         * @param wrappedNode The original node that this Node wraps.
         * @param thumbSize   The hight and/or width of the thumbnail in pixels.
         */
        private ThumbnailViewNode(Node wrappedNode, int thumbSize) {
            super(wrappedNode, FilterNode.Children.LEAF);
            this.thumbSize = thumbSize;
            this.content = this.getLookup().lookup(Content.class);
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

                if (content == null) {
                    return ImageUtils.getDefaultThumbnail();
                }
                if (thumbTask == null || thumbTask.isDone()) {
                    thumbTask = loadThumbnail(ThumbnailViewNode.this, content);

                }
                if (timer == null) {
                    timer = new Timer(1, actionEvent -> fireIconChange());
                    timer.start();
                }
                return waitingIcon;
            }
        }

        synchronized void setThumbSize(int iconSize) {
            this.thumbSize = iconSize;
            thumbCache = null;
            thumbTask = null;
        }

        private void completionCallback(ThumbnailLoadTask task) {
            try {
                thumbCache = new SoftReference<>(task.get());
                fireIconChange();
            } catch (CancellationException ex) {
                //Task was cancelled, do nothing
            } catch (InterruptedException | ExecutionException ex) {
                if (ex.getCause() instanceof CancellationException) {
                } else {
                    logger.log(Level.SEVERE, "Error getting thumbnail icon for " + content.getName(), ex); //NON-NLS
                }
            } finally {

                if (timer != null) {
                    timer.stop();
                    timer = null;
                }
                thumbTask = null;
            }
        }

    }

    private class ThumbnailLoadTask extends SwingWorker<Image, Void> {

        private final ThumbnailViewNode node;

        private final Content content;
        private final ProgressHandle progressHandle;
        private volatile boolean started = false;
        private final String progressText;
        private final int thumbSize;

        ThumbnailLoadTask(ThumbnailViewNode node, Content content, int thumbSize) {
            this.node = node;
            this.content = content;
            progressText = Bundle.ThumbnailViewNode_progressHandle_text(content.getName());
            progressHandle = ProgressHandle.createHandle(progressText);
            this.thumbSize = thumbSize;
        }

        @Override
        protected Image doInBackground() throws Exception {
            synchronized (progressHandle) {
                progressHandle.start();
                started = true;
            }
            return ImageUtils.getThumbnail(content, thumbSize);
        }

        private void cancel() {
            SwingUtilities.invokeLater(() -> progressHandle.setDisplayName(progressText + " " + CANCELLING_POSTIX));
        }

        @Override
        protected void done() {
            super.done();
            synchronized (progressHandle) {
                if (started) {
                    progressHandle.finish();
                }
            }

            node.completionCallback(this);
        }
    }

    /**
     * Node representing a page of thumbnails, a parent of image nodes, with a
     * name showing children range
     */
    private class ThumbnailPageNode extends AbstractNode {

        private ThumbnailPageNode(Integer pageNum, List<Node> childNodes) {

            super(new ThumbnailPageNodeChildren(childNodes), Lookups.singleton(pageNum));
            setName(Integer.toString(pageNum + 1));
            int from = 1 + (pageNum * IMAGES_PER_PAGE);
            int to = from + ((ThumbnailPageNodeChildren) getChildren()).getChildCount() - 1;
            setDisplayName(from + "-" + to);

            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png"); //NON-NLS
        }
    }

    /**
     * Children.Keys implementation which uses nodes as keys, and wraps them in
     * ThumbnailViewNodes as the child nodes.
     *
     */
    private class ThumbnailPageNodeChildren extends Children.Keys<Node> {

        /*
         * wrapped original nodes
         */
        private List<Node> keyNodes = null;

        ThumbnailPageNodeChildren(List<Node> keyNodes) {
            super(true);
            this.keyNodes = keyNodes;
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            setKeys(keyNodes);
        }

        @Override
        protected void removeNotify() {
            super.removeNotify();
            setKeys(Collections.emptyList());
        }

        int getChildCount() {
            return keyNodes.size();
        }

        @Override
        protected Node[] createNodes(Node wrapped) {
            if (wrapped != null) {
                final ThumbnailViewNode thumb = new ThumbnailViewNode(wrapped, thumbSize);
                return new Node[]{thumb};
            } else {
                return new Node[]{};
            }
        }
    }
}
