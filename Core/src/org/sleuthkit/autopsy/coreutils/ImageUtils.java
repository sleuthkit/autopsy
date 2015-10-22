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
import java.io.BufferedInputStream;
import java.io.EOFException;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.opencv.core.Core;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector.FileTypeDetectorInitException;
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

    /**
     * save thumbnails to disk as this format
     */
    private static final String FORMAT = "png"; //NON-NLS

    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;

    private static final Logger logger = LOGGER;
    private static final BufferedImage DEFAULT_THUMBNAIL;

    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS;
    private static final TreeSet<String> SUPPORTED_IMAGE_MIME_TYPES;
    private static final List<String> CONDITIONAL_MIME_TYPES = Arrays.asList("audio/x-aiff", "application/octet-stream");

    private static final boolean openCVLoaded;

    static {
        ImageIO.scanForPlugins();
        BufferedImage tempImage;
        try {
            tempImage = ImageIO.read(ImageUtils.class.getResourceAsStream("/org/sleuthkit/autopsy/images/file-icon.png"));//NON-NLS
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load default icon.", ex);
            tempImage = null;
        }
        DEFAULT_THUMBNAIL = tempImage;

        //load opencv libraries
        boolean openCVLoadedTemp;
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            if (System.getProperty("os.arch").equals("amd64") || System.getProperty("os.arch").equals("x86_64")) {
                System.loadLibrary("opencv_ffmpeg248_64");
            } else {
                System.loadLibrary("opencv_ffmpeg248");
            }

            openCVLoadedTemp = true;
        } catch (UnsatisfiedLinkError e) {
            openCVLoadedTemp = false;
            LOGGER.log(Level.SEVERE, "OpenCV Native code library failed to load", e);
            //TODO: show warning bubble

        }

        openCVLoaded = openCVLoadedTemp;
        SUPPORTED_IMAGE_EXTENSIONS = Arrays.asList(ImageIO.getReaderFileSuffixes());

        SUPPORTED_IMAGE_MIME_TYPES = new TreeSet<>(Arrays.asList(ImageIO.getReaderMIMETypes()));
        /*
         * special cases and variants that we support, but don't get registered
         * with ImageIO automatically
         */
        SUPPORTED_IMAGE_MIME_TYPES.addAll(Arrays.asList(
                "image/x-rgb",
                "image/x-ms-bmp",
                "image/x-portable-graymap",
                "image/x-portable-bitmap",
                "application/x-123"));
        SUPPORTED_IMAGE_MIME_TYPES.removeIf("application/octet-stream"::equals);
    }

    /**
     * initialized lazily
     */
    private static FileTypeDetector fileTypeDetector;

    /**
     * thread that saves generated thumbnails to disk in the background
     */
    private static final Executor imageSaver =
            Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder()
                    .namingPattern("icon saver-%d").build());

    public static List<String> getSupportedImageExtensions() {
        return Collections.unmodifiableList(SUPPORTED_IMAGE_EXTENSIONS);
    }

    public static SortedSet<String> getSupportedImageMimeTypes() {
        return Collections.unmodifiableSortedSet(SUPPORTED_IMAGE_MIME_TYPES);
    }

    /**
     * Get the default thumbnail, which is the icon for a file. Used when we can
     * not generate content based thumbnail.
     *
     * @return
     *
     * @deprecated use {@link  #getDefaultThumbnail() } instead.
     */
    @Deprecated
    public static Image getDefaultIcon() {
        return getDefaultThumbnail();
    }

    /**
     * Get the default thumbnail, which is the icon for a file. Used when we can
     * not generate content based thumbnail.
     *
     * @return the default thumbnail
     */
    public static Image getDefaultThumbnail() {
        return DEFAULT_THUMBNAIL;
    }

    /**
     * Can a thumbnail be generated for the content?
     *
     * @param content
     *
     * @return
     *
     */
    public static boolean thumbnailSupported(Content content) {

        if (content.getSize() == 0) {
            return false;
        }
        if (!(content instanceof AbstractFile)) {
            return false;
        }
        AbstractFile file = (AbstractFile) content;

        return VideoUtils.isVideoThumbnailSupported(file)
                || isImageThumbnailSupported(file);

    }

    public static boolean isImageThumbnailSupported(AbstractFile file) {

        return isMediaThumbnailSupported(file, SUPPORTED_IMAGE_MIME_TYPES, SUPPORTED_IMAGE_EXTENSIONS, CONDITIONAL_MIME_TYPES)
                || hasImageFileHeader(file);
    }

    /**
     * Check if a file is "supported" by checking it mimetype and extension
     *
     * //TODO: this should move to a better place. Should ImageUtils and
     * VideoUtils both implement/extend some base interface/abstract class. That
     * would be the natural place to put this.
     *
     * @param file
     * @param supportedMimeTypes a set of mimetypes that the could have to be
     *                           supported
     * @param supportedExtension a set of extensions a file could have to be
     *                           supported if the mime lookup fails or is
     *                           inconclusive
     * @param conditionalMimes   a set of mimetypes that a file could have to be
     *                           supoprted if it also has a supported extension
     *
     * @return true if a thumbnail can be generated for the given file with the
     *         given lists of supported mimetype and extensions
     */
    static boolean isMediaThumbnailSupported(AbstractFile file, final SortedSet<String> supportedMimeTypes, final List<String> supportedExtension, List<String> conditionalMimes) {
        if (file.getSize() == 0) {
            return false;
        }
        final String extension = file.getNameExtension();
        try {
            String mimeType = getFileTypeDetector().getFileType(file);
            if (Objects.nonNull(mimeType)) {
                return supportedMimeTypes.contains(mimeType)
                        || (conditionalMimes.contains(mimeType.toLowerCase()) && supportedExtension.contains(extension));
            }
        } catch (FileTypeDetector.FileTypeDetectorInitException | TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to look up mimetype for " + file.getName() + " using FileTypeDetector.  Fallingback on AbstractFile.isMimeType", ex);

            AbstractFile.MimeMatchEnum mimeMatch = file.isMimeType(supportedMimeTypes);
            if (mimeMatch == AbstractFile.MimeMatchEnum.TRUE) {
                return true;
            } else if (mimeMatch == AbstractFile.MimeMatchEnum.FALSE) {
                return false;
            }
        }
        // if we have an extension, check it
        return StringUtils.isNotBlank(extension) && supportedExtension.contains(extension);
    }

    /**
     * returns a lazily instatiated FileTypeDetector
     *
     * @return a FileTypeDetector
     *
     * @throws FileTypeDetectorInitException if a initializing the
     *                                       FileTypeDetector failed.
     */
    synchronized private static FileTypeDetector getFileTypeDetector() throws FileTypeDetector.FileTypeDetectorInitException {
        if (fileTypeDetector == null) {
            fileTypeDetector = new FileTypeDetector();
        }
        return fileTypeDetector;
    }

    /**
     * Get a thumbnail of a specified size. Generates the image if it is not
     * already cached.
     *
     * @param content
     * @param iconSize
     *
     *
     * @return a thumbnail for the given image or a default one if there was a
     *         problem making a thumbnail.
     *
     * @deprecated use {@link #getThumbnail(org.sleuthkit.datamodel.Content, int)
     * } instead.
     *
     */
    @Nonnull
    @Deprecated
    public static Image getIcon(Content content, int iconSize) {
        return getThumbnail(content, iconSize);
    }

    /**
     * Get a thumbnail of a specified size. Generates the image if it is not
     * already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return a thumbnail for the given image or a default one if there was a
     *         problem making a thumbnail.
     */
    public static Image getThumbnail(Content content, int iconSize) {
        if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile) content;
            // If a thumbnail file is already saved locally
            File cacheFile = getCachedThumbnailLocation(content.getId());
            if (cacheFile.exists()) {
                try {
                    BufferedImage thumbnail = ImageIO.read(cacheFile);
                    if (isNull(thumbnail) || thumbnail.getWidth() != iconSize) {
                        return generateAndSaveThumbnail(file, iconSize, cacheFile);
                    } else {
                        return thumbnail;
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error while reading image: " + content.getName(), ex); //NON-NLS
                    return generateAndSaveThumbnail(file, iconSize, cacheFile);
                }
            } else {
                return generateAndSaveThumbnail(file, iconSize, cacheFile);
            }
        } else {
            return DEFAULT_THUMBNAIL;
        }
    }

    /**
     * Get a thumbnail of a specified size. Generates the image if it is not
     * already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return File object for cached image. Is guaranteed to exist, as long as
     *         there was not an error generating or saving the thumbnail.
     *
     * @deprecated use {@link #getCachedThumbnailFile(org.sleuthkit.datamodel.Content, int)
     * } instead.
     *
     */
    @Nullable
    @Deprecated
    public static File getIconFile(Content content, int iconSize) {
        return getCachedThumbnailFile(content, iconSize);

    }

    /**
     *
     * Get a thumbnail of a specified size. Generates the image if it is not
     * already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return File object for cached image. Is guaranteed to exist, as long as
     *         there was not an error generating or saving the thumbnail.
     */
    @Nullable
    public static File getCachedThumbnailFile(Content content, int iconSize) {
        getThumbnail(content, iconSize);
        return getCachedThumbnailLocation(content.getId());
    }

    /**
     * Get a file object for where the cached icon should exist. The returned
     * file may not exist.
     *
     * @param id
     *
     * @return
     *
     *
     * @deprecated use {@link #getCachedThumbnailLocation(long) } instead
     */
    @Deprecated

    public static File getFile(long id) {
        return getCachedThumbnailLocation(id);
    }

    /**
     * Get a file object for where the cached icon should exist. The returned
     * file may not exist.
     *
     * @param fileID
     *
     * @return
     *
     */
    private static File getCachedThumbnailLocation(long fileID) {
        return Paths.get(Case.getCurrentCase().getCacheDirectory(), "thumbnails", fileID + ".png").toFile();
    }

    public static boolean hasImageFileHeader(AbstractFile file) {
        return isJpegFileHeader(file) || isPngFileHeader(file);
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
            /*
             * Check for the JPEG header. Since Java bytes are signed, we cast
             * them to an int first.
             */
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
            /*
             * Check for the png header. Since Java bytes are signed, we cast
             * them to an int first.
             */
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
     * Generate an icon and save it to specified location.
     *
     * @param file      File to generate icon for
     * @param iconSize
     * @param cacheFile Location to save thumbnail to
     *
     * @return Generated icon or null on error
     */
    private static Image generateAndSaveThumbnail(AbstractFile file, int iconSize, File cacheFile) {
        BufferedImage thumbnail = null;
        try {
            if (VideoUtils.isVideoThumbnailSupported(file)) {
                if (openCVLoaded) {
                    thumbnail = VideoUtils.generateVideoThumbnail(file, iconSize);
                } else {
                    return DEFAULT_THUMBNAIL;
                }
            } else {
                thumbnail = generateImageThumbnail(file, iconSize);
            }

            if (thumbnail == null) {
                return DEFAULT_THUMBNAIL;

            } else {
                BufferedImage toSave = thumbnail;
                imageSaver.execute(() -> {
                    try {
                        Files.createParentDirs(cacheFile);
                        if (cacheFile.exists()) {
                            cacheFile.delete();
                        }
                        ImageIO.write(toSave, FORMAT, cacheFile);
                    } catch (IllegalArgumentException | IOException ex1) {
                        LOGGER.log(Level.WARNING, "Could not write cache thumbnail: " + file, ex1); //NON-NLS
                    }
                });
            }
        } catch (NullPointerException ex) {
            logger.log(Level.WARNING, "Could not write cache thumbnail: " + file, ex); //NON-NLS
        }
        return thumbnail;
    }

    /**
     *
     * Generate and return a scaled image
     *
     * @param content
     * @param iconSize
     *
     * @return a Thumbnail of the given content at the given size, or null if
     *         there was a problem.
     */
    @Nullable
    private static BufferedImage generateImageThumbnail(Content content, int iconSize) {

        try (InputStream inputStream = new BufferedInputStream(new ReadContentInputStream(content));) {
            BufferedImage bi = ImageIO.read(inputStream);

            if (bi == null) {
                LOGGER.log(Level.WARNING, "No image reader for file: {0}", content.getName()); //NON-NLS
                return null;
            }
            try {
                return ScalrWrapper.resizeFast(bi, iconSize);
            } catch (IllegalArgumentException e) {
                // if resizing does not work due to extreme aspect ratio,
                // crop the image instead.
                return ScalrWrapper.cropImage(bi, Math.min(iconSize, bi.getWidth()), Math.min(iconSize, bi.getHeight()));
            }
        } catch (OutOfMemoryError e) {
            LOGGER.log(Level.WARNING, "Could not scale image (too large) " + content.getName(), e); //NON-NLS
        } catch (EOFException e) {
            LOGGER.log(Level.WARNING, "Could not load image (EOF) {0}", content.getName()); //NON-NLS
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load image " + content.getName(), e); //NON-NLS
        }
        return null;
    }

}
