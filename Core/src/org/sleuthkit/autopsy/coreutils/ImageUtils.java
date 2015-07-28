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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
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
 *
 * Utilities for working with Images and creating thumbnails. Reuses thumbnails
 * by storing them in the case's cache directory.
 */
public class ImageUtils {

    private static final Logger LOGGER = Logger.getLogger(ImageUtils.class.getName());

    /** save thumbnails to disk as this format */
    private static final String FORMAT = "png"; //NON-NLS

    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;

    private static final Logger logger = LOGGER;
    private static final BufferedImage DEFAULT_THUMBNAIL;
    private static final TreeSet<String> SUPPORTED_MIME_TYPES = new TreeSet<>();
    private static final List<String> SUPPORTED_EXTENSIONS = new ArrayList<>();
    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS;
    private static final List<String> SUPPORTED_VIDEO_EXTENSIONS
            = Arrays.asList("mov", "m4v", "flv", "mp4", "3gp", "avi", "mpg",
                    "mpeg", "asf", "divx", "rm", "moov", "wmv", "vob", "dat",
                    "m1v", "m2v", "m4v", "mkv", "mpe", "yop", "vqa", "xmv",
                    "mve", "wtv", "webm", "vivo", "vc1", "seq", "thp", "san",
                    "mjpg", "smk", "vmd", "sol", "cpk", "sdp", "sbg", "rtsp",
                    "rpl", "rl2", "r3d", "mlp", "mjpeg", "hevc", "h265", "265",
                    "h264", "h263", "h261", "drc", "avs", "pva", "pmp", "ogg",
                    "nut", "nuv", "nsv", "mxf", "mtv", "mvi", "mxg", "lxf",
                    "lvf", "ivf", "mve", "cin", "hnm", "gxf", "fli", "flc",
                    "flx", "ffm", "wve", "uv2", "dxa", "dv", "cdxl", "cdg",
                    "bfi", "jv", "bik", "vid", "vb", "son", "avs", "paf", "mm",
                    "flm", "tmv", "4xm");  //NON-NLS
    private static final TreeSet<String> SUPPORTED_IMAGE_MIME_TYPES;
    private static final List<String> SUPPORTED_VIDEO_MIME_TYPES
            = Arrays.asList("application/x-shockwave-flash", "video/x-m4v", "video/quicktime", "video/avi", "video/msvideo", "video/x-msvideo",
                    "video/mp4", "video/x-ms-wmv", "video/mpeg", "video/asf"); //NON-NLS
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

        SUPPORTED_EXTENSIONS.addAll(SUPPORTED_IMAGE_EXTENSIONS);
        SUPPORTED_EXTENSIONS.addAll(SUPPORTED_VIDEO_EXTENSIONS);

        SUPPORTED_IMAGE_MIME_TYPES = new TreeSet<>(Arrays.asList(ImageIO.getReaderMIMETypes()));
        /* special cases and variants that we support, but don't get registered
         * with ImageIO automatically */
        SUPPORTED_IMAGE_MIME_TYPES.addAll(Arrays.asList(
                "image/x-rgb",
                "image/x-ms-bmp",
                "application/x-123"));
        SUPPORTED_MIME_TYPES.addAll(SUPPORTED_IMAGE_MIME_TYPES);
        SUPPORTED_MIME_TYPES.addAll(SUPPORTED_VIDEO_MIME_TYPES);

        //this is rarely usefull
        SUPPORTED_MIME_TYPES.removeIf("application/octet-stream"::equals);
    }

    /**
     * Get the default Icon, which is the icon for a file.
     *
     * @return
     *
     *
     *
     *         /** initialized lazily */
    private static FileTypeDetector fileTypeDetector;

    /** thread that saves generated thumbnails to disk in the background */
    private static final Executor imageSaver
            = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder()
                    .namingPattern("icon saver-%d").build());

    private ImageUtils() {
    }

    public static List<String> getSupportedImageExtensions() {
        return Collections.unmodifiableList(SUPPORTED_IMAGE_EXTENSIONS);
    }

    public static List<String> getSupportedVideoExtensions() {
        return SUPPORTED_VIDEO_EXTENSIONS;
    }

    public static SortedSet<String> getSupportedImageMimeTypes() {
        return Collections.unmodifiableSortedSet(SUPPORTED_IMAGE_MIME_TYPES);
    }

    public static List<String> getSupportedVideoMimeTypes() {
        return SUPPORTED_VIDEO_MIME_TYPES;
    }

    public static List<String> getSupportedExtensions() {
        return Collections.unmodifiableList(SUPPORTED_EXTENSIONS);
    }

    public static SortedSet<String> getSupportedMimeTypes() {
        return Collections.unmodifiableSortedSet(SUPPORTED_MIME_TYPES);
    }

    /**
     * Get the default thumbnail, which is the icon for a file. Used when we can
     * not
     * generate content based thumbnail.
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

        try {
            String mimeType = getFileTypeDetector().getFileType(file);
            if (Objects.nonNull(mimeType)) {
                return SUPPORTED_MIME_TYPES.contains(mimeType)
                        || (mimeType.equalsIgnoreCase("audio/x-aiff") && "iff".equalsIgnoreCase(file.getNameExtension()));
            }
        } catch (FileTypeDetector.FileTypeDetectorInitException | TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to look up mimetype for " + file.getName() + " using FileTypeDetector.  Fallingback on AbstractFile.isMimeType", ex);

            AbstractFile.MimeMatchEnum mimeMatch = file.isMimeType(SUPPORTED_MIME_TYPES);
            if (mimeMatch == AbstractFile.MimeMatchEnum.TRUE) {
                return true;
            } else if (mimeMatch == AbstractFile.MimeMatchEnum.FALSE) {
                return false;
            }
        }

        // if we have an extension, check it
        final String extension = file.getNameExtension();
        if (StringUtils.isNotBlank(extension) && SUPPORTED_EXTENSIONS.contains(extension)) {
            return true;
        }

        // if no extension or one that is not for an image, then read the content
        return isJpegFileHeader(file) || isPngFileHeader(file);
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
     * Get a thumbnail of a specified size. Generates the image if it is
     * not already cached.
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
     * Get a thumbnail of a specified size. Generates the image if it is
     * not already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return a thumbnail for the given image or a default one if there was a
     *         problem making a thumbnail.
     */
    public static Image getThumbnail(Content content, int iconSize) {
        // If a thumbnail file is already saved locally
        File cacheFile = getCachedThumbnailLocation(content.getId());
        if (cacheFile.exists()) {
            try {
                BufferedImage thumbnail = ImageIO.read(cacheFile);
                if (isNull(thumbnail) || thumbnail.getWidth() != iconSize) {
                    return generateAndSaveThumbnail(content, iconSize, cacheFile);
                } else {
                    return thumbnail;
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error while reading image: " + content.getName(), ex); //NON-NLS
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
     * Generate an icon and save it to specified location.
     *
     * @param content   File to generate icon for
     * @param iconSize
     * @param cacheFile Location to save thumbnail to
     *
     * @return Generated icon or null on error
     */
    private static Image generateAndSaveThumbnail(Content content, int iconSize, File cacheFile) {
        AbstractFile f = (AbstractFile) content;
        final String extension = f.getNameExtension();
        BufferedImage thumbnail = null;
        try {
            if (SUPPORTED_VIDEO_EXTENSIONS.contains(extension)) {
                if (openCVLoaded) {
                    thumbnail = VideoUtils.generateVideoThumbnail((AbstractFile) content, iconSize);
                } else {
                    return DEFAULT_THUMBNAIL;
                }
            } else {
                thumbnail = generateImageThumbnail(content, iconSize);
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
                        LOGGER.log(Level.WARNING, "Could not write cache thumbnail: " + content, ex1); //NON-NLS
                    }
                });
            }
        } catch (NullPointerException ex) {
            logger.log(Level.WARNING, "Could not write cache thumbnail: " + content, ex); //NON-NLS
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
            LOGGER.log(Level.WARNING, "Could not scale image (too large): " + content.getName(), e); //NON-NLS

            return null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load image: " + content.getName(), e); //NON-NLS
            return null;

        }
    }

}
