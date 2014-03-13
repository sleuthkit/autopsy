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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for creating and manipulating thumbnail and icon images.
 * @author jwallace
 */
public class ImageUtils {
    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;
    private static final Logger logger = Logger.getLogger(ImageUtils.class.getName());
    private static final Image DEFAULT_ICON = new ImageIcon("/org/sleuthkit/autopsy/images/file-icon.png").getImage();
    private static final List<String> SUPP_EXTENSIONS = Arrays.asList(ImageIO.getReaderFileSuffixes());
    private static final List<String> SUPP_MIME_TYPES = Arrays.asList(ImageIO.getReaderMIMETypes());
    /**
     * Get the default Icon, which is the icon for a file.
     * @return 
     */
    public static Image getDefaultIcon() {
        return DEFAULT_ICON;
    }
    
    /**
     * Can a thumbnail be generated for the content?
     * 
     * @param content
     * @return 
     */
    public static boolean thumbnailSupported(Content content) {
        if (content instanceof AbstractFile == false) {
            return false;
        }
        
        AbstractFile f = (AbstractFile) content;
        if (f.getSize() == 0) {
            return false;
        }

        // check the blackboard for a file type attribute
        try {
            ArrayList <BlackboardAttribute> attributes = f.getGenInfoAttributes(ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            for (BlackboardAttribute attribute : attributes) { 
                if (SUPP_MIME_TYPES.contains(attribute.getValueString())) {
                    return true;
                }
            }
        } 
        catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error while getting file signature from blackboard.", ex);
        }
        
        final String extension = f.getNameExtension();
        
        // if we have an extension, check it
        if (extension.equals("") == false) {
            // Note: thumbnail generator only supports JPG, GIF, and PNG for now
            if (SUPP_EXTENSIONS.contains(extension)) {
                return true;
            }
        }
        
        // if no extension or one that is not for an image, then read the content
        return isJpegFileHeader(f);    
    }

   
    /**
     * Get an icon of a specified size.
     * 
     * @param content
     * @param iconSize
     * @return 
     */
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
    
    /**
     * Get the cached file of the icon. Generates the icon and its file if it 
     * doesn't already exist, so this method guarantees to return a file that
     * exists.
     * @param content
     * @param iconSize
     * @return 
     */
    public static File getIconFile(Content content, int iconSize) {
        if (getIcon(content, iconSize) != null) {
            return getFile(content.getId());
        }
        return null;
    }
    
    /**
     * Get the cached file of the content object with the given id. 
     * 
     * The returned file may not exist.
     * 
     * @param id
     * @return 
     */
    public static File getFile(long id) {
        return new File(Case.getCurrentCase().getCacheDirectory() + File.separator + id + ".png");
    }
    
    /**
     * Check if is jpeg file based on header
     *
     * @param file
     *
     * @return true if jpeg file, false otherwise
     */
    public static boolean isJpegFileHeader(AbstractFile file) {
        if (file.getSize() < 100) {
            return false;
        }

        byte[] fileHeaderBuffer = new byte[2];
        int bytesRead;
        try {
            bytesRead = file.read(fileHeaderBuffer, 0, 2);
        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not a JPEG
            return false;
        }
        if (bytesRead != 2) {
            return false;
        }
        /*
         * Check for the JPEG header. Since Java bytes are signed, we cast them
         * to an int first.
         */
        return (((fileHeaderBuffer[0] & 0xff) == 0xff) && ((fileHeaderBuffer[1] & 0xff) == 0xd8));
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
                ImageIO.write((BufferedImage) icon, "png", getFile(content.getId()));
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
}
