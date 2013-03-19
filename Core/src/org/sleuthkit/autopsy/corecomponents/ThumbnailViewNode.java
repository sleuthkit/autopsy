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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskException;

/**
 * Node that wraps around original node and adds the bitmap icon representing
 * the picture
 */
class ThumbnailViewNode extends FilterNode {

    private SoftReference<Image> iconCache;
    private static final Image defaultIcon = new ImageIcon("/org/sleuthkit/autopsy/images/file-icon.png").getImage();
    private static final Logger logger = Logger.getLogger(ThumbnailViewNode.class.getName());
    //private final BufferedImage defaultIconBI;

    /**
     * the constructor
     */
    ThumbnailViewNode(Node arg) {
        super(arg, Children.LEAF);
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


        if (icon == null) {
            Content content = this.getLookup().lookup(Content.class);

            if (content != null) {
                if (getFile(content.getId()).exists()) {
                    try {
                        icon = ImageIO.read(getFile(content.getId()));
                    } catch (IOException ex) {
                        icon = ThumbnailViewNode.defaultIcon;
                    }
                } else {
                    try {
                        icon = generateIcon(content);
                        if (icon == null) {
                            icon = ThumbnailViewNode.defaultIcon;
                        }
                        else {
                            ImageIO.write((BufferedImage) icon, "jpg", getFile(content.getId()));
                        }
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Could not write cache thumbnail: " + content, ex);
                    }
                }
            } else {
                icon = ThumbnailViewNode.defaultIcon;
            }

            iconCache = new SoftReference<Image>(icon);
        }

        return icon;
    }

    /*
     * Generate a scaled image
     */
    static private BufferedImage generateIcon(Content content) {

        try {
            final InputStream inputStream = new ReadContentInputStream(content);
            BufferedImage bi = ImageIO.read(inputStream);

            BufferedImage biScaled = ScalrWrapper.resizeFast(bi, 100, 100);
            return biScaled;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not scale image: " + content.getName(), e);
            return null;
        }
    }

    private static File getFile(long id) {
        return new File(Case.getCurrentCase().getCacheDirectory() + File.separator + id + ".jpg");
    }
}
