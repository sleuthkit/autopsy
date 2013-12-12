 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.coreutils;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.contentviewers.Utilities;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Utilities for creating and manipulating thumbnails.
 * @author jwallace
 */
public class ImageUtils {
    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;
    private static final Logger logger = Logger.getLogger(ImageUtils.class.getName());
    private static final Image DEFAULT_ICON = new ImageIcon("/org/sleuthkit/autopsy/images/file-icon.png").getImage();
    private static final List<String> SUPP_EXTENSIONS = Arrays.asList(ImageIO.getReaderFileSuffixes());
    
    public static Image getDefaultIcon() {
        return DEFAULT_ICON;
    }
    
    public static boolean thumbnailSupported(Content content) {
        if (content instanceof AbstractFile == false) {
            return false;
        }
        
        AbstractFile f = (AbstractFile) content;
        final String fName = f.getName();
        final int dotIdx = fName.lastIndexOf('.');
        if (dotIdx == -1 || dotIdx == (fName.length() - 1)) {
            return Utilities.isJpegFileHeader(f);
        }

        final String ext = fName.substring(dotIdx + 1).toLowerCase();

        // Note: thumbnail generator only supports JPG, GIF, and PNG for now
        return (f.getSize() > 0
                && SUPP_EXTENSIONS.contains(ext));
    }
   
    public static Image getIcon(Content content, int iconSize) {
        Image icon;
        // If a thumbnail file is already saved locally
        File file = getFile(content.getId());
        if (file.exists()) {
            try {
                BufferedImage bicon = ImageIO.read(file);
                if (bicon == null) {
                    icon = DEFAULT_ICON;
                } else if (bicon.getWidth() != iconSize) {
                    icon = generateAndSaveIcon(content, iconSize);    
                } else {
                    icon = bicon;    
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error while reading image.", ex);
                icon = DEFAULT_ICON;
            }
        } else { // Make a new icon
            icon = generateAndSaveIcon(content, iconSize);
        }
        return icon;
    }
    
    private static Image generateAndSaveIcon(Content content, int iconSize) { 
        Image icon = null;
        try {
            icon = generateIcon(content, iconSize);
            if (icon == null) {
                return DEFAULT_ICON;
            } else {
                File f = getFile(content.getId());
                if (f.exists()) {
                    f.delete();
                }
                ImageIO.write((BufferedImage) icon, "jpg", getFile(content.getId()));
            }         
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not write cache thumbnail: " + content, ex);
        }   
        return icon;        
    }
    
    /*
     * Generate a scaled image
     */
    private static BufferedImage generateIcon(Content content, int iconSize) {

        InputStream inputStream = null;
        try {
            inputStream = new ReadContentInputStream(content);
            BufferedImage bi = ImageIO.read(inputStream);
            if (bi == null) {
                logger.log(Level.WARNING, "No image reader for file: " + content.getName());
                return null;
            }
            BufferedImage biScaled = ScalrWrapper.resizeFast(bi, iconSize);

            return biScaled;
        } catch (OutOfMemoryError e) {
            logger.log(Level.WARNING, "Could not scale image (too large): " + content.getName(), e);
            return null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not scale image: " + content.getName(), e);
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close input stream after resizing thumbnail: " + content.getName(), ex);
                }
            }

        }
    }
    
    private static File getFile(long id) {
        return new File(Case.getCurrentCase().getCacheDirectory() + File.separator + id + ".jpg");
    }
}
