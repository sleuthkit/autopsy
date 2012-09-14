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
import java.lang.ref.SoftReference;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskException;
import sun.rmi.runtime.Log;

/**
 *
 * @author jantonius
 */
class ThumbnailViewNode extends FilterNode {

    private SoftReference<Image> iconCache;
    
    private static final Image defaultIcon = new ImageIcon("/org/sleuthkit/autopsy/images/file-icon.png").getImage();

    /** the constructor */
    ThumbnailViewNode(Node arg) {
        super(arg, Children.LEAF);
    }

    @Override
    public String getDisplayName(){
        if(super.getDisplayName().length() > 15)
            return super.getDisplayName().substring(0, 15).concat("...");
        else
            return super.getDisplayName();
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
                        ImageIO.write(toBufferedImage(icon), "jpg", getFile(content.getId()));
                    } catch (TskException ex) {
                        icon = ThumbnailViewNode.defaultIcon;
                    } catch (IOException ex) {
                    }
                }
            } else {
                icon = ThumbnailViewNode.defaultIcon;
            }
            
            iconCache = new SoftReference<Image>(icon);
        }

        return icon;
    }

    static private Image generateIcon(Content content) throws TskException {
        byte[] data = new byte[(int)content.getSize()];
        int bytesRead = content.read(data, 0, content.getSize());
        
        if (bytesRead < 1)
            return null;
        
        Image result = Toolkit.getDefaultToolkit().createImage(data);

        // scale the image
        MediaTracker mTracker = new MediaTracker(new JFrame());
        mTracker.addImage(result, 1);
        try {
            mTracker.waitForID(1);
        } catch (InterruptedException ex) {
            // TODO: maybe make bubble instead
            Logger.getLogger(ThumbnailViewNode.class.getName()).log(Level.WARNING, "Error while trying to scale the icon.", ex);
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
            Logger.getLogger(ThumbnailViewNode.class.getName()).log(Level.WARNING, "Error while trying to load the icon.", ex);
        }

        // create 75x75 image for the icon with the icon on the center
        BufferedImage combined = new BufferedImage(75, 75, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) combined.getGraphics();
        g.setColor(Color.WHITE);
        g.setBackground(Color.WHITE);
        g.drawImage(result, (75 - width) / 2, (75 - height) / 2, null);

        return Toolkit.getDefaultToolkit().createImage(combined.getSource());
    }

    private static BufferedImage toBufferedImage(Image src) {
        int w = src.getWidth(null);
        int h = src.getHeight(null);
        int type = BufferedImage.TYPE_INT_RGB;  // other options
        BufferedImage dest = new BufferedImage(w, h, type);
        Graphics2D g2 = dest.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return dest;
    }
    
    private static File getFile(long id) {
        return new File(Case.getCurrentCase().getCacheDirectory() + File.separator + id + ".jpg");
    }
    
}
