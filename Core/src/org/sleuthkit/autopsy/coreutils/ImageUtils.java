 /*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-15 Basis Technology Corp.
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

import com.google.common.io.Files;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import static java.util.Objects.isNull;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for working with Images and creating thumbnails. Reuses thumbnails
 * by storing them in the case's cache directory.
 */
public class ImageUtils {

    private static final Logger LOGGER = Logger.getLogger(ImageUtils.class.getName());

    /** save thumbnails to disk as this format */
    private static final String FORMAT = "png";

    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;
    private static final Image DEFAULT_ICON = new ImageIcon("/org/sleuthkit/autopsy/images/file-icon.png").getImage(); //NON-NLS

    public static List<String> getSupportedExtensions() {
        return Collections.unmodifiableList(SUPP_EXTENSIONS);
    }

    public static SortedSet<String> getSupportedMimeTypes() {
        return Collections.unmodifiableSortedSet(SUPP_MIME_TYPES);
    }

    private static final List<String> SUPP_EXTENSIONS;
    private static final TreeSet<String> SUPP_MIME_TYPES;

    /** thread that saves generated thumbnails to disk for use later */
    private static final Executor imageSaver = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().namingPattern("icon saver-%d").build());

    static {
        ImageIO.scanForPlugins();
        SUPP_EXTENSIONS = Arrays.asList(ImageIO.getReaderFileSuffixes());

        SUPP_MIME_TYPES = new TreeSet<>(Arrays.asList(ImageIO.getReaderMIMETypes()));
        SUPP_MIME_TYPES.addAll(Arrays.asList("image/x-ms-bmp", "application/x-123"));
    }

    private ImageUtils() {
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

        AbstractFile file = (AbstractFile) content;
        if (file.getSize() == 0) {
            return false;
        }

        try {
            String mimeType = new FileTypeDetector().getFileType(file);
            if (Objects.nonNull(mimeType)) {
                return SUPP_MIME_TYPES.contains(mimeType);
            }
        } catch (FileTypeDetector.FileTypeDetectorInitException | TskCoreException ex) {

            LOGGER.log(Level.WARNING, "Failed to look up mimetype for " + file.getName() + " using FileTypeDetector.  Fallingback on AbstractFile.isMimeType", ex);
            if (!SUPP_MIME_TYPES.isEmpty()) {
                AbstractFile.MimeMatchEnum mimeMatch = file.isMimeType(SUPP_MIME_TYPES);
                if (mimeMatch == AbstractFile.MimeMatchEnum.TRUE) {
                    return true;
                } else if (mimeMatch == AbstractFile.MimeMatchEnum.FALSE) {
                    return false;
                }
            }
        }

        // if we have an extension, check it
        final String extension = file.getNameExtension();
        if (StringUtils.isNotBlank(extension)) {
            if (SUPP_EXTENSIONS.contains(extension)) {
                return true;
            }
        }
        // if no extension or one that is not for an image, then read the content
        return isJpegFileHeader(file) || isPngFileHeader(file);
    }

    /**
     * Get a thumbnail of a specified size. Generates the image if it is
     * not already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return a thumbnail for the given image or a default one if there was a
     *         problem making a thumbnail.
     */
    @Nonnull
    public static Image getIcon(Content content, int iconSize) {
        // If a thumbnail file is already saved locally
        File cacheFile = getCachedThumnailLocation(content.getId());
        if (cacheFile.exists()) {
            try {
                BufferedImage thumbnail = ImageIO.read(cacheFile);
                if (isNull(thumbnail) || thumbnail.getWidth() != iconSize) {
                    return generateAndSaveThumbnail(content, iconSize, cacheFile);
                } else {
                    return thumbnail;
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Error while reading image.", ex); //NON-NLS
                return generateAndSaveThumbnail(content, iconSize, cacheFile);
            }
        } else {
            return generateAndSaveThumbnail(content, iconSize, cacheFile);
        }
    }

    /**
     * Get a thumbnail of a specified size. Generates the image if it is
     * not already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return File object for cached image. Is guaranteed to exist, as long as
     *         there was not an error generating or saving the thumbnail.
     */
    @Nullable
    public static File getIconFile(Content content, int iconSize) {
        getIcon(content, iconSize);
        return getCachedThumnailLocation(content.getId());

    }

    /**
     * Get a file object for where the cached icon should exist. The returned
     * file may not exist.
     *
     * @param id
     *
     * @return
     *
     * @deprecated this should never have been public
     */
    @Deprecated
    public static File getFile(long id) {
        return getCachedThumnailLocation(id);
    }

    private static File getCachedThumnailLocation(long id) {
        return Paths.get(Case.getCurrentCase().getCacheDirectory(), "thumbnails", id + ".png").toFile();
    }

    /**
     * Check if the given file is a jpeg based on header.
     *
     * @param file
     *
     * @return true if jpeg file, false otherwise
     */
    public static boolean isJpegFileHeader(AbstractFile file) {
        if (file.getSize() < 100) {
            return false;
        }

        try {
            byte[] fileHeaderBuffer = readHeader(file, 2);
            /* Check for the JPEG header. Since Java bytes are signed, we cast
             * them to an int first. */
            return (((fileHeaderBuffer[0] & 0xff) == 0xff) && ((fileHeaderBuffer[1] & 0xff) == 0xd8));
        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not a JPEG
            return false;
        }
    }

    /**
     * Check if the given file is a png based on header.
     *
     * @param file
     *
     * @return true if png file, false otherwise
     */
    public static boolean isPngFileHeader(AbstractFile file) {
        if (file.getSize() < 10) {
            return false;
        }

        try {
            byte[] fileHeaderBuffer = readHeader(file, 8);
            /* Check for the png header. Since Java bytes are signed, we cast
             * them to an int first. */
            return (((fileHeaderBuffer[1] & 0xff) == 0x50) && ((fileHeaderBuffer[2] & 0xff) == 0x4E)
                    && ((fileHeaderBuffer[3] & 0xff) == 0x47) && ((fileHeaderBuffer[4] & 0xff) == 0x0D)
                    && ((fileHeaderBuffer[5] & 0xff) == 0x0A) && ((fileHeaderBuffer[6] & 0xff) == 0x1A)
                    && ((fileHeaderBuffer[7] & 0xff) == 0x0A));

        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not an png
            return false;
        }
    }

    private static byte[] readHeader(AbstractFile file, int buffLength) throws TskCoreException {
        byte[] fileHeaderBuffer = new byte[buffLength];
        int bytesRead = file.read(fileHeaderBuffer, 0, buffLength);

        if (bytesRead != buffLength) {
            //ignore if can't read the first few bytes, not an image
            throw new TskCoreException("Could not read " + buffLength + " bytes from " + file.getName());
        }
        return fileHeaderBuffer;
    }

    /**
     * Generate a thumbnail and save it to specified location.
     *
     * @param content  File to generate icon for
     * @param size     the size of thumbnail to generate in pixels
     * @param saveFile Location to save thumbnail to
     *
     * @return Generated icon or a default icon if a thumbnail could not be
     *         made.
     */
    private static Image generateAndSaveThumbnail(Content content, int size, File saveFile) {
        BufferedImage thumbNail = generateThumbnail(content, size);
        if (Objects.nonNull(thumbNail)) {
            imageSaver.execute(() -> {

                saveThumbnail(content, thumbNail, saveFile);
            });
            return thumbNail;
        } else {
            return getDefaultIcon();
        }
    }

    /**
     * Generate and return a scaled image
     *
     * @param content
     * @param iconSize
     *
     * @return a Thumbnail of the given content at the given size, or null if
     *         there was a problem.
     */
    @Nullable
    private static BufferedImage generateThumbnail(Content content, int iconSize) {

        try (InputStream inputStream = new ReadContentInputStream(content);) {

            BufferedImage bi = ImageIO.read(inputStream);
            if (bi == null) {
                LOGGER.log(Level.WARNING, "No image reader for file: {0}", content.getName()); //NON-NLS
                return null;
            }
            try {
                return ScalrWrapper.resizeFast(bi, iconSize);
            } catch (IllegalArgumentException e) {
                // if resizing does not work due to extremely small height/width ratio,
                // crop the image instead.
                return ScalrWrapper.cropImage(bi, Math.min(iconSize, bi.getWidth()), Math.min(iconSize, bi.getHeight()));
            }
        } catch (OutOfMemoryError e) {
            LOGGER.log(Level.WARNING, "Could not scale image (too large): " + content.getName(), e); //NON-NLS
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not scale image: " + content.getName(), e); //NON-NLS
            return null;
        }
    }

    /**
     * save the generated thumbnail to disk in the cache folder with
     * the obj_id as the name.
     *
     * @param file      the file the given image is a thumbnail for
     * @param thumbnail the thumbnail to save for the given DrawableFile
     */
    static private void saveThumbnail(Content content, final RenderedImage thumbnail, File cacheFile) {
        try {
            Files.createParentDirs(cacheFile);
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            //convert back to swing to save
            ImageIO.write(thumbnail, FORMAT, cacheFile);
        } catch (IllegalArgumentException | IOException ex) {
            LOGGER.log(Level.WARNING, "Could not write cache thumbnail: " + content, ex); //NON-NLS
        }
    }
}
