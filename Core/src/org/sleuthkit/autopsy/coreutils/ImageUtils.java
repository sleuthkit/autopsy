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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for creating and manipulating thumbnail and icon images.
 *
 * @author jwallace
 */
public class ImageUtils {

    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;
    private static final Logger logger = Logger.getLogger(ImageUtils.class.getName());
    private static final Image DEFAULT_ICON = new ImageIcon("/org/sleuthkit/autopsy/images/file-icon.png").getImage(); //NON-NLS
    private static final List<String> SUPP_EXTENSIONS = Arrays.asList(ImageIO.getReaderFileSuffixes());
    private static final List<String> SUPP_MIME_TYPES = new ArrayList<>(Arrays.asList(ImageIO.getReaderMIMETypes()));

    static {
        SUPP_MIME_TYPES.add("image/x-ms-bmp");
    }

    /**
     * Get the default Icon, which is the icon for a file.
     *
     * @return
     */
    public static Image getDefaultIcon() {
        return DEFAULT_ICON;
    }

    /**
     * Can a thumbnail be generated for the content?
     *
     * @param content
     *
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
            ArrayList<BlackboardAttribute> attributes = f.getGenInfoAttributes(ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            for (BlackboardAttribute attribute : attributes) {
                if (SUPP_MIME_TYPES.contains(attribute.getValueString())) {
                    return true;
                }
            }
            // if the file type is known and we don't support it, bail
            if (attributes.size() > 0) {
                return false;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error while getting file signature from blackboard.", ex); //NON-NLS
        }

        // if we have an extension, check it
        final String extension = f.getNameExtension();
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
     * Get a thumbnail of a specified size. Generates the image if it is not
     * already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return
     */
    public static Image getIcon(Content content, int iconSize) {
        Image icon;
        // If a thumbnail file is already saved locally
        // @@@ Bug here in that we do not refer to size in the cache. 
        File file = getFile(content.getId());
        if (file.exists()) {
            try {
                BufferedImage bicon = ImageIO.read(file);
                if (bicon == null) {
                    icon = DEFAULT_ICON;
                } else if (bicon.getWidth() != iconSize) {
                    icon = generateAndSaveIcon(content, iconSize, file);
                } else {
                    icon = bicon;
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error while reading image.", ex); //NON-NLS
                icon = DEFAULT_ICON;
            }
        } else { // Make a new icon
            icon = generateAndSaveIcon(content, iconSize, file);
        }
        return icon;
    }

    /**
     * Get a thumbnail of a specified size. Generates the image if it is not
     * already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return File object for cached image. Is guaranteed to exist.
     */
    public static File getIconFile(Content content, int iconSize) {
        if (getIcon(content, iconSize) != null) {
            return getFile(content.getId());
        }
        return null;
    }

    /**
     * Get a file object for where the cached icon should exist. The returned
     * file may not exist.
     *
     * @param id
     *
     * @return
     */
    // TODO: This should be private and be renamed to something like  getCachedThumbnailLocation().
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

    public static boolean isPngFileHeader(AbstractFile file) {
        if (file.getSize() < 10) {
            return false;
        }

        byte[] fileHeaderBuffer = new byte[8];
        int bytesRead;
        try {
            bytesRead = file.read(fileHeaderBuffer, 0, 8);
        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not an image
            return false;
        }
        if (bytesRead != 8) {
            return false;
        }
        /*
         * Check for the header. Since Java bytes are signed, we cast them to an
         * int first.
         */
        return (((fileHeaderBuffer[1] & 0xff) == 0x50) && ((fileHeaderBuffer[2] & 0xff) == 0x4E)
                && ((fileHeaderBuffer[3] & 0xff) == 0x47) && ((fileHeaderBuffer[4] & 0xff) == 0x0D)
                && ((fileHeaderBuffer[5] & 0xff) == 0x0A) && ((fileHeaderBuffer[6] & 0xff) == 0x1A)
                && ((fileHeaderBuffer[7] & 0xff) == 0x0A));
    }

    /**
     * Generate an icon and save it to specified location.
     *
     * @param content  File to generate icon for
     * @param iconSize
     * @param saveFile Location to save thumbnail to
     *
     * @return Generated icon or null on error
     */
    private static Image generateAndSaveIcon(Content content, int iconSize, File saveFile) {
        Image icon = null;
        try {
            icon = generateIcon(content, iconSize);
            if (icon == null) {
                return DEFAULT_ICON;
            } else {
                if (saveFile.exists()) {
                    saveFile.delete();
                }
                ImageIO.write((BufferedImage) icon, "png", saveFile); //NON-NLS
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not write cache thumbnail: " + content, ex); //NON-NLS
        }
        return icon;
    }

    /*
     * Generate and return a scaled image
     */
    private static BufferedImage generateIcon(Content content, int iconSize) {

        InputStream inputStream = null;
        BufferedImage bi = null;
        try {
            inputStream = new ReadContentInputStream(content);
            bi = ImageIO.read(inputStream);
            if (bi == null) {
                logger.log(Level.WARNING, "No image reader for file: " + content.getName()); //NON-NLS
                return null;
            }
            BufferedImage biScaled = ScalrWrapper.resizeFast(bi, iconSize);

            return biScaled;
        } catch (IllegalArgumentException e) {
            // if resizing does not work due to extremely small height/width ratio,
            // crop the image instead.
            BufferedImage biCropped = ScalrWrapper.cropImage(bi, Math.min(iconSize, bi.getWidth()), Math.min(iconSize, bi.getHeight()));
            return biCropped;
        } catch (OutOfMemoryError e) {
            logger.log(Level.WARNING, "Could not scale image (too large): " + content.getName(), e); //NON-NLS
            return null;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not scale image: " + content.getName(), e); //NON-NLS
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close input stream after resizing thumbnail: " + content.getName(), ex); //NON-NLS
                }
            }

        }
    }
}
