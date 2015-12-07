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

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.lang.ref.SoftReference;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;

/**
 * Node that wraps around original node and adds the bitmap icon representing
 * the picture
 */
class ThumbnailViewNode extends FilterNode {

    static private final Image waitingIcon = Toolkit.getDefaultToolkit().createImage(ThumbnailViewNode.class.getResource("/org/sleuthkit/autopsy/images/working_spinner.gif"));

    private SoftReference<Image> iconCache = null;
    private int iconSize = ImageUtils.ICON_SIZE_MEDIUM;

    private SwingWorker<Image, Object> swingWorker;
    private Timer timer;

    /**
     * the constructor
     */
    ThumbnailViewNode(Node arg, int iconSize) {
        super(arg, Children.LEAF);
        this.iconSize = iconSize;
    }

    @Override
    public String getDisplayName() {
        if (super.getDisplayName().length() > 15) {
            return super.getDisplayName().substring(0, 15).concat("...");
        } else {
            return super.getDisplayName();
        }
    }

    @Override
    public Image getIcon(int type) {
        Image icon = null;

        if (iconCache != null) {
            icon = iconCache.get();
        }

        if (icon != null) {
            return icon;
        } else {
            final Content content = this.getLookup().lookup(Content.class);
            if (content == null) {
                return ImageUtils.getDefaultThumbnail();
            }
            if (swingWorker == null || swingWorker.isDone()) {
                swingWorker = new SwingWorker<Image, Object>() {
                    final private ProgressHandle progressHandle = ProgressHandleFactory.createHandle("generating thumbnail for video file " + content.getName());

                    @Override
                    protected Image doInBackground() throws Exception {
                        progressHandle.start();
                        return ImageUtils.getThumbnail(content, iconSize);
                    }

                    @Override
                    protected void done() {
                        super.done();
                        try {
                            iconCache = new SoftReference<>(super.get());
                            progressHandle.finish();
                            fireIconChange();
                            if (timer != null) {
                                timer.stop();
                                timer = null;
                            }
                        } catch (InterruptedException | ExecutionException ex) {
                            Logger.getLogger(ThumbnailViewNode.class.getName()).log(Level.SEVERE, "Error getting thumbnail icon", ex); //NON-NLS
                        }
                        swingWorker = null;
                    }
                };
                swingWorker.execute();
            }
            if (timer == null) {
                timer = new Timer(100, (ActionEvent e) -> {
                    fireIconChange();
                });
                timer.start();
            }
            return waitingIcon;
        }
    }

    public void setIconSize(int iconSize) {
        this.iconSize = iconSize;
        iconCache = null;
        swingWorker = null;
    }
}
