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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.lang.ref.SoftReference;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.ContentNode;
import org.sleuthkit.autopsy.logging.Log;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author jantonius
 */
class ThumbnailViewNode extends FilterNode {

    private ContentNode currentNode;
    // for error handling
    private SoftReference<Image> iconCache;
    private static Image defaultIcon = new ImageIcon("/org/sleuthkit/autopsy/images/file-icon.png").getImage();

    /** the constructor */
    ThumbnailViewNode(ContentNode arg) {
        super((Node) arg, Children.LEAF);
        this.currentNode = arg;
    }

    @Override
    public Node getOriginal() {
        return super.getOriginal();
    }

    @Override
    public Image getIcon(int type) {
        Image icon = null;

        if (iconCache != null) {
            icon = iconCache.get();
        }

        if (icon == null) {
            try {
                System.out.println("generate");
                icon = generateIcon();
                iconCache = new SoftReference<Image>(icon);
            } catch (TskException ex) {
                icon = ThumbnailViewNode.defaultIcon;
            }
        }

        return icon;
    }

    private Image generateIcon() throws TskException {
        ContentNode temp = currentNode;
        byte[] content = temp.read(0, temp.getContent().getSize());
        Image result = Toolkit.getDefaultToolkit().createImage(content);

        // scale the image
        MediaTracker mTracker = new MediaTracker(new JFrame());
        mTracker.addImage(result, 1);
        try {
            mTracker.waitForID(1);
        } catch (InterruptedException ex) {
            // TODO: maybe make bubble instead
            Log.get(ThumbnailViewNode.class).log(Level.WARNING, "Error while trying to scale the icon.", ex);
        }
        int width = result.getWidth(null);
        int height = result.getHeight(null);

        int max = Math.max(width, height);
        double scale = (75 * 100) / max;

        // getScaledInstance can't take have width or height be 0, so round
        // up by adding 1 after truncating to int.
        width = (int) ((width * scale) / 100) + 1;
        height = (int) ((height * scale) / 100) + 1;

        result = result.getScaledInstance(width, height, Image.SCALE_SMOOTH);

        // load the image completely
        mTracker.addImage(result, 1);
        try {
            mTracker.waitForID(1);
        } catch (InterruptedException ex) {
            // TODO: maybe make bubble instead
            Log.get(ThumbnailViewNode.class).log(Level.WARNING, "Error while trying to load the icon.", ex);
        }

        // create 75x75 image for the icon with the icon on the center
        BufferedImage combined = new BufferedImage(75, 75, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) combined.getGraphics();
        g.setColor(Color.WHITE);
        g.setBackground(Color.WHITE);
        g.drawImage(result, (75 - width) / 2, (75 - height) / 2, null);

        return Toolkit.getDefaultToolkit().createImage(combined.getSource());
    }
}
