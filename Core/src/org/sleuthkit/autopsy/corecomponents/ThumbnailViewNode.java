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

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.lang.ref.SoftReference;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerThumbnail.ThumbnailLoader;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;

/**
 * Node that wraps around original node and adds the bitmap icon representing
 * the picture
 */
class ThumbnailViewNode extends FilterNode {

    private Logger logger = Logger.getLogger(ThumbnailViewNode.class.getName());

    static private final Image waitingIcon = Toolkit.getDefaultToolkit().createImage(ThumbnailViewNode.class.getResource("/org/sleuthkit/autopsy/images/working_spinner.gif"));

    private SoftReference<Image> thumbCache = null;
    private int iconSize = ImageUtils.ICON_SIZE_MEDIUM;

    private ThumbnailLoadTask thumbTask;
    private Timer timer;
    private final ThumbnailLoader thumbLoader;

    /**
     * the constructor
     */
    ThumbnailViewNode(Node arg, ThumbnailLoader thumbLoader) {
        super(arg, Children.LEAF);
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

    class ThumbnailLoadTask extends SwingWorker<Image, Object> {

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
