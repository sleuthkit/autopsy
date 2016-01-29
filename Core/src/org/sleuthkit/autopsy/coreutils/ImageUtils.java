 /*
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

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.io.Files;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import static java.util.Objects.nonNull;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.opencv.core.Core;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corelibs.ScalrWrapper;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector.FileTypeDetectorInitException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utilities for working with image files and creating thumbnails. Re-uses
 * thumbnails by storing them in the case's cache directory.
 */
public class ImageUtils {

    private static final Logger LOGGER = Logger.getLogger(ImageUtils.class.getName());

    private static final String COULD_NOT_WRITE_CACHE_THUMBNAIL = "Could not write cache thumbnail: "; //NOI18N NON-NLS
    private static final String COULD_NOT_CREATE_IMAGE_INPUT_STREAM = "Could not create ImageInputStream."; //NOI18N NON-NLS
    private static final String NO_IMAGE_READER_FOUND_FOR_ = "No ImageReader found for "; //NOI18N NON-NLS

    /**
     * save thumbnails to disk as this format
     */
    private static final String FORMAT = "png"; //NON-NLS //NOI18N

    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;

    private static final BufferedImage DEFAULT_THUMBNAIL;

    private static final String IMAGE_GIF_MIME = "image/gif"; //NOI18N NON-NLS
    private static final SortedSet<String> GIF_MIME_SET = ImmutableSortedSet.copyOf(new String[]{IMAGE_GIF_MIME});

    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS;
    private static final SortedSet<String> SUPPORTED_IMAGE_MIME_TYPES;
    private static final List<String> CONDITIONAL_MIME_TYPES = Arrays.asList("audio/x-aiff", "application/octet-stream"); //NOI18N NON-NLS

    private static final boolean openCVLoaded;

    static {
        ImageIO.scanForPlugins();
        BufferedImage tempImage;
        try {
            tempImage = ImageIO.read(ImageUtils.class.getResourceAsStream("/org/sleuthkit/autopsy/images/file-icon.png"));//NON-NLS //NOI18N
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load default icon.", ex); //NOI18N NON-NLS
            tempImage = null;
        }
        DEFAULT_THUMBNAIL = tempImage;

        //load opencv libraries
        boolean openCVLoadedTemp;
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            if (System.getProperty("os.arch").equals("amd64") || System.getProperty("os.arch").equals("x86_64")) { //NOI18N NON-NLS
                System.loadLibrary("opencv_ffmpeg248_64"); //NOI18N NON-NLS
            } else {
                System.loadLibrary("opencv_ffmpeg248"); //NOI18N NON-NLS
            }

            openCVLoadedTemp = true;
        } catch (UnsatisfiedLinkError e) {
            openCVLoadedTemp = false;
            LOGGER.log(Level.SEVERE, "OpenCV Native code library failed to load", e); //NOI18N NON-NLS
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
                "image/x-rgb", //NON-NLS
                "image/x-ms-bmp", //NON-NLS
                "image/x-portable-graymap", //NON-NLS
                "image/x-portable-bitmap", //NON-NLS
                "application/x-123")); //TODO: is this correct? -jm //NOI18N NON-NLS
        SUPPORTED_IMAGE_MIME_TYPES.removeIf("application/octet-stream"::equals); //NOI18N NON-NLS
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
                    .namingPattern("icon saver-%d").build()); //NOI18N NON-NLS

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
     * Although this method accepts Content, it always returns false for objects
     * that are not instances of AbstractFile.
     *
     * @param content A content object to test for thumbnail support.
     *
     * @return true if a thumbnail can be generated for the given content.
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

    /**
     * is the file an image that we can read and generate a thumbnail for
     *
     * @param file
     *
     * @return true if the file is an image we can read and generate thumbnail
     *         for.
     */
    public static boolean isImageThumbnailSupported(AbstractFile file) {
        return isMediaThumbnailSupported(file, SUPPORTED_IMAGE_MIME_TYPES, SUPPORTED_IMAGE_EXTENSIONS, CONDITIONAL_MIME_TYPES)
                || hasImageFileHeader(file);
    }

    /**
     * Does the image have a GIF mimetype.
     *
     * @param file
     *
     * @return true if the given file has a GIF mimetype
     */
    public static boolean isGIF(AbstractFile file) {
        try {
            final FileTypeDetector myFileTypeDetector = getFileTypeDetector();
            if (nonNull(myFileTypeDetector)) {
                String fileType = myFileTypeDetector.getFileType(file);
                return IMAGE_GIF_MIME.equalsIgnoreCase(fileType);
            }
        } catch (FileTypeDetectorInitException ex) {
            LOGGER.log(Level.WARNING, "Failed to initialize FileTypeDetector.", ex); //NOI18N NON-NLS
        } catch (TskCoreException ex) {
            if (ex.getMessage().contains("An SQLException was provoked by the following failure: java.lang.InterruptedException")) { //NON-NLS
                LOGGER.log(Level.WARNING, "Mime type look up with FileTypeDetector was interupted."); //NOI18N} NON-NLS
                return "gif".equalsIgnoreCase(file.getNameExtension()); //NOI18N
            } else {
                LOGGER.log(Level.SEVERE, "Failed to get mime type of " + getContentPathSafe(file) + " with FileTypeDetector.", ex); //NOI18N} NON-NLS
            }
        }
        LOGGER.log(Level.WARNING, "Falling back on direct mime type check for {0}.", getContentPathSafe(file)); //NOI18N NON-NLS
        switch (file.isMimeType(GIF_MIME_SET)) {

            case TRUE:
                return true;
            case UNDEFINED:
                LOGGER.log(Level.WARNING, "Falling back on extension check."); //NOI18N NON-NLS
                return "gif".equalsIgnoreCase(file.getNameExtension()); //NOI18N
            case FALSE:
            default:
                return false;
        }
    }

    /**
     * Check if a file is "supported" by checking its mimetype and extension
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
     * @return true if a thumbnail can be generated for the given file based on
     *         the given lists of supported mimetype and extensions
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
            LOGGER.log(Level.WARNING, "Failed to look up mimetype for {0} using FileTypeDetector:{1}", new Object[]{getContentPathSafe(file), ex.toString()}); //NOI18N NON-NLS
            LOGGER.log(Level.INFO, "Falling back on AbstractFile.isMimeType"); //NOI18N NON-NLS
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
     * Get a thumbnail of a specified size for the given image. Generates the
     * thumbnail if it is not already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return a thumbnail for the given image or a default one if there was a
     *         problem making a thumbnail.
     *
     * @deprecated use {@link #getThumbnail(org.sleuthkit.datamodel.Content, int)
     * } instead.
     */
    @Nonnull
    @Deprecated
    public static BufferedImage getIcon(Content content, int iconSize) {
        return getThumbnail(content, iconSize);
    }

    /**
     * Get a thumbnail of a specified size for the given image. Generates the
     * thumbnail if it is not already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return a thumbnail for the given image or a default one if there was a
     *         problem making a thumbnail.
     */
    public static BufferedImage getThumbnail(Content content, int iconSize) {
        if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile) content;

            Task<javafx.scene.image.Image> thumbnailTask = newGetThumbnailTask(file, iconSize, true);
            thumbnailTask.run();
            try {
                return SwingFXUtils.fromFXImage(thumbnailTask.get(), null);
            } catch (InterruptedException | ExecutionException ex) {
                LOGGER.log(Level.WARNING, "Failed to get thumbnail for {0}: " + ex.toString(), getContentPathSafe(content)); //NON-NLS
                return DEFAULT_THUMBNAIL;
            }
        } else {
            return DEFAULT_THUMBNAIL;
        }
    }

    /**
     * Get a thumbnail of a specified size for the given image. Generates the
     * thumbnail if it is not already cached.
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
     * Get a thumbnail of a specified size for the given image. Generates the
     * thumbnail if it is not already cached.
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
     * @deprecated use {@link #getCachedThumbnailLocation(long) } instead
     */
    @Deprecated

    public static File getFile(long id) {
        return getCachedThumbnailLocation(id);
    }

    /**
     * Get a file object for where the cached thumbnail should exist. The
     * returned file may not exist.
     *
     * @param fileID
     *
     * @return a File object representing the location of the cached thumbnail.
     *         This file may not actually exist(yet). Returns null if there was
     *         any problem getting the file, such as no case was open.
     */
    private static File getCachedThumbnailLocation(long fileID) {
        try {
            String cacheDirectory = Case.getCurrentCase().getCacheDirectory();
            return Paths.get(cacheDirectory, "thumbnails", fileID + ".png").toFile(); //NOI18N NON-NLS
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "Could not get cached thumbnail location.  No case is open."); //NON-NLS
            return null;
        }

    }

    /**
     * Do a direct check to see if the given file has an image file header.
     * NOTE: Currently only jpeg and png are supported.
     *
     * @param file
     *
     * @return true if the given file has one of the supported image headers.
     */
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
            throw new TskCoreException("Could not read " + buffLength + " bytes from " + file.getName()); //NOI18N
        }
        return fileHeaderBuffer;
    }

    /**
     * Get the width of the given image, in pixels.
     *
     * @param file
     *
     * @return the width in pixels
     *
     * @throws IOException If the file is not a supported image or the width
     *                     could not be determined.
     */
    static public int getImageWidth(AbstractFile file) throws IOException {
        return getImageProperty(file,
                "ImageIO could not determine width of {0}: ", //NOI18N NON-NLS
                imageReader -> imageReader.getWidth(0)
        );
    }

    /**
     * Get the height of the given image,in pixels.
     *
     * @param file
     *
     * @return the height in pixels
     *
     * @throws IOException If the file is not a supported image or the height
     *                     could not be determined.
     */
    static public int getImageHeight(AbstractFile file) throws IOException {
        return getImageProperty(file,
                "ImageIO could not determine height of {0}: ", //NOI18N NON-NLS
                imageReader -> imageReader.getHeight(0)
        );
    }

    /**
     * Functional interface for methods that extract a property out of an
     * ImageReader. Initially created to abstract over
     * {@link #getImageHeight(org.sleuthkit.datamodel.AbstractFile)} and
     * {@link #getImageWidth(org.sleuthkit.datamodel.AbstractFile)}
     *
     * @param <T> The type of the property.
     */
    @FunctionalInterface
    private static interface PropertyExtractor<T> {

        public T extract(ImageReader reader) throws IOException;
    }

    /**
     * Private template method designed to be used as the implementation of
     * public methods that pull particular (usually meta-)data out of a image
     * file. ./**
     *
     * @param <T>               the type of the property to be retrieved.
     * @param file              the file to extract the data from
     * @param errorTemplate     a message template used to log errors. Should
     *                          take one parameter: the file's unique path or
     *                          name.
     * @param propertyExtractor an implementation of {@link PropertyExtractor}
     *                          used to retrieve the specific property.
     *
     * @return the the value of the property extracted by the given
     *         propertyExtractor
     *
     * @throws IOException if there was a problem reading the property from the
     *                     file.
     *
     * @see PropertyExtractor
     * @see #getImageHeight(org.sleuthkit.datamodel.AbstractFile)
     */
    private static <T> T getImageProperty(AbstractFile file, final String errorTemplate, PropertyExtractor<T> propertyExtractor) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(new ReadContentInputStream(file));) {
            try (ImageInputStream input = ImageIO.createImageInputStream(inputStream)) {
                if (input == null) {
                    IIOException iioException = new IIOException(COULD_NOT_CREATE_IMAGE_INPUT_STREAM);
                    LOGGER.log(Level.WARNING, errorTemplate + iioException.toString(), getContentPathSafe(file));
                    throw iioException;
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    reader.setInput(input);
                    try {

                        return propertyExtractor.extract(reader);
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING, errorTemplate + ex.toString(), getContentPathSafe(file));
                        throw ex;
                    } finally {
                        reader.dispose();
                    }
                } else {
                    IIOException iioException = new IIOException(NO_IMAGE_READER_FOUND_FOR_ + getContentPathSafe(file));
                    LOGGER.log(Level.WARNING, errorTemplate + iioException.toString(), getContentPathSafe(file));

                    throw iioException;
                }
            }
        }
    }

    /**
     * Create a new {@link Task} that will get a thumbnail for the given image
     * of the specified size. If a cached thumbnail is available it will be
     * returned as the result of the task, otherwise a new thumbnail will be
     * created and cached.
     *
     * Note: the returned task is suitable for running in a background thread,
     * but is not started automatically. Clients are responsible for running the
     * task, monitoring its progress, and using its result.
     *
     * @param file     the file to create a thumbnail for
     * @param iconSize the size of the thumbnail
     *
     * @return a new Task that returns a thumbnail as its result.
     */
    public static Task<javafx.scene.image.Image> newGetThumbnailTask(AbstractFile file, int iconSize, boolean defaultOnFailure) {
        return new GetThumbnailTask(file, iconSize, defaultOnFailure);
    }

    /**
     * A Task that gets cached thumbnails and makes new ones as needed.
     */
    static private class GetThumbnailTask extends ReadImageTaskBase {

        private static final String FAILED_TO_READ_IMAGE_FOR_THUMBNAIL_GENERATION = "Failed to read image for thumbnail generation."; //NOI18N NON-NLS

        private final int iconSize;
        private final File cacheFile;
        private final boolean defaultOnFailure;

//        @NbBundle.Messages({"# {0} - file name",
//            "GetOrGenerateThumbnailTask.loadingThumbnailFor=Loading thumbnail for {0}", "# {0} - file name",
//            "GetOrGenerateThumbnailTask.generatingPreviewFor=Generating preview for {0}"})
        private GetThumbnailTask(AbstractFile file, int iconSize, boolean defaultOnFailure) {
            super(file);
            updateMessage(NbBundle.getMessage(this.getClass(), "ImageUtils.GetOrGenerateThumbnailTask.loadingThumbnailFor", file.getName()));
            this.iconSize = iconSize;
            this.defaultOnFailure = defaultOnFailure;
            this.cacheFile = getCachedThumbnailLocation(file.getId());
        }

        @Override
        protected javafx.scene.image.Image call() throws Exception {
            if (isGIF(file)) {
                return readImage();
            }
            if (isCancelled()) {
                return null;
            }
            // If a thumbnail file is already saved locally, just read that.
            if (cacheFile != null && cacheFile.exists()) {
                try {
                    BufferedImage cachedThumbnail = ImageIO.read(cacheFile);
                    if (nonNull(cachedThumbnail) && cachedThumbnail.getWidth() == iconSize) {
                        return SwingFXUtils.toFXImage(cachedThumbnail, null);
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "ImageIO had a problem reading thumbnail for image {0}: " + ex.toString(), ImageUtils.getContentPathSafe(file)); //NOI18N NON-NLS
                }
            }

            if (isCancelled()) {
                return null;
            }
            //There was no correctly-sized cached thumbnail so make one.
            BufferedImage thumbnail = null;

            if (VideoUtils.isVideoThumbnailSupported(file)) {
                if (openCVLoaded) {
                    updateMessage(NbBundle.getMessage(this.getClass(), "ImageUtils.GetOrGenerateThumbnailTask.generatingPreviewFor", file.getName()));
                    thumbnail = VideoUtils.generateVideoThumbnail(file, iconSize);
                }
                if (null == thumbnail) {
                    if (defaultOnFailure) {
                        thumbnail = DEFAULT_THUMBNAIL;
                    } else {
                        throw new IIOException("Failed to generate thumbnail for video file.");
                    }
                }

            } else {
                //read the image into a buffered image.
                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(readImage(), null);
                if (null == bufferedImage) {
                    LOGGER.log(Level.WARNING, FAILED_TO_READ_IMAGE_FOR_THUMBNAIL_GENERATION);
                    throw new IIOException(FAILED_TO_READ_IMAGE_FOR_THUMBNAIL_GENERATION);
                }
                updateProgress(-1, 1);

                //resize, or if that fails, crop it
                try {
                    thumbnail = ScalrWrapper.resizeFast(bufferedImage, iconSize);
                } catch (IllegalArgumentException | OutOfMemoryError e) {
                    // if resizing does not work due to extreme aspect ratio or oom, crop the image instead.
                    LOGGER.log(Level.WARNING, "Could not scale image {0}: " + e.toString() + ".  Attemptying to crop {0} instead", ImageUtils.getContentPathSafe(file)); //NOI18N NON-NLS

                    final int height = bufferedImage.getHeight();
                    final int width = bufferedImage.getWidth();
                    if (iconSize < height || iconSize < width) {
                        final int cropHeight = Math.min(iconSize, height);
                        final int cropWidth = Math.min(iconSize, width);

                        try {
                            thumbnail = ScalrWrapper.cropImage(bufferedImage, cropWidth, cropHeight);
                        } catch (Exception cropException) {
                            LOGGER.log(Level.WARNING, "Could not crop image {0}: " + cropException.toString(), ImageUtils.getContentPathSafe(file)); //NOI18N NON-NLS
                            throw cropException;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Could not scale image {0}: " + e.toString(), ImageUtils.getContentPathSafe(file)); //NOI18N NON-NLS
                    throw e;
                }
            }

            if (isCancelled()) {
                return null;
            }

            updateProgress(-1, 1);

            //if we got a valid thumbnail save it
            if ((cacheFile != null) && nonNull(thumbnail) && DEFAULT_THUMBNAIL != thumbnail) {
                saveThumbnail(thumbnail);
            }

            return SwingFXUtils.toFXImage(thumbnail, null);
        }

        /**
         * submit the thumbnail saving to another background thread.
         *
         * @param thumbnail
         */
        private void saveThumbnail(BufferedImage thumbnail) {
            imageSaver.execute(() -> {
                try {
                    Files.createParentDirs(cacheFile);
                    if (cacheFile.exists()) {
                        cacheFile.delete();
                    }
                    ImageIO.write(thumbnail, FORMAT, cacheFile);
                } catch (IllegalArgumentException | IOException ex) {
                    LOGGER.log(Level.WARNING, "Could not write thumbnail for {0}: " + ex.toString(), ImageUtils.getContentPathSafe(file)); //NOI18N NON-NLS
                }
            });
        }
    }

    /**
     * Create a new {@link Task} that will read the fileinto memory as an
     * {@link javafx.scene.image.Image}
     *
     * Note: the returned task is suitable for running in a background thread,
     * but is not started automatically. Clients are responsible for running the
     * task, monitoring its progress, and using its result.
     *
     * @param file the file to read as an Image
     *
     * @return a new Task that returns an Image as its result
     */
    public static Task<javafx.scene.image.Image> newReadImageTask(AbstractFile file) {
        return new ReadImageTask(file);
    }

    /**
     * A task that reads the content of a AbstractFile as a javafx Image.
     */
    static private class ReadImageTask extends ReadImageTaskBase {

        ReadImageTask(AbstractFile file) {
            super(file);
            updateMessage(NbBundle.getMessage(this.getClass(), "ImageUtils.ReadImageTask.mesage.text", file.getName()));
        }

//        @NbBundle.Messages({
//            "# {0} - file name",
//            "LoadImageTask.mesageText=Reading image: {0}"})
        @Override
        protected javafx.scene.image.Image call() throws Exception {
            return readImage();
        }
    }

    /**
     * Base class for tasks that need to read AbstractFiles as Images.
     */
    static private abstract class ReadImageTaskBase extends Task<javafx.scene.image.Image> implements IIOReadProgressListener {

        private static final String IMAGE_UTILS_COULD_NOT_READ_UNSUPPORTE_OR_CORRUPT = "ImageUtils could not read {0}.  It may be unsupported or corrupt"; //NOI18N NON-NLS
        final AbstractFile file;
        private ImageReader reader;

        ReadImageTaskBase(AbstractFile file) {
            this.file = file;
        }

        protected javafx.scene.image.Image readImage() throws IOException {
            try (InputStream inputStream = new BufferedInputStream(new ReadContentInputStream(file));) {
                if (ImageUtils.isGIF(file)) {
                    //use JavaFX to directly read GIF to preserve potential animation,
                    javafx.scene.image.Image image = new javafx.scene.image.Image(new BufferedInputStream(inputStream));
                    if (image.isError() == false) {
                        return image;
                    }
                    //fall through to default image reading code if there was an error
                }
                if (isCancelled()) {
                    return null;
                }
                try (ImageInputStream input = ImageIO.createImageInputStream(inputStream)) {
                    if (input == null) {
                        throw new IIOException(COULD_NOT_CREATE_IMAGE_INPUT_STREAM);
                    }
                    Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

                    //we use the first ImageReader, is there any point to trying the others?
                    if (readers.hasNext()) {
                        reader = readers.next();
                        reader.addIIOReadProgressListener(this);
                        reader.setInput(input);
                        /*
                         * This is the important part, get or create a
                         * ImageReadParam, create a destination image to hold
                         * the decoded result, then pass that image with the
                         * param.
                         */
                        ImageReadParam param = reader.getDefaultReadParam();

                        BufferedImage bufferedImage = reader.getImageTypes(0).next().createBufferedImage(reader.getWidth(0), reader.getHeight(0));
                        param.setDestination(bufferedImage);
                        try {
                            bufferedImage = reader.read(0, param); //should always be same bufferedImage object
                        } catch (IOException iOException) {
                            // Ignore this exception or display a warning or similar, for exceptions happening during decoding
                            LOGGER.log(Level.WARNING, IMAGE_UTILS_COULD_NOT_READ_UNSUPPORTE_OR_CORRUPT + ": " + iOException.toString(), ImageUtils.getContentPathSafe(file)); //NOI18N
                        } finally {
                            reader.removeIIOReadProgressListener(this);
                            reader.dispose();
                        }
                        if (isCancelled()) {
                            return null;
                        }
                        return SwingFXUtils.toFXImage(bufferedImage, null);
                    } else {
                        throw new IIOException(NO_IMAGE_READER_FOUND_FOR_ + ImageUtils.getContentPathSafe(file));
                    }
                }
            }
        }

        @Override
        public void imageProgress(ImageReader source, float percentageDone) {
            //update this task with the progress reported by ImageReader.read
            updateProgress(percentageDone, 100);
            if (isCancelled()) {
                reader.removeIIOReadProgressListener(this);
                reader.abort();
                reader.dispose();
            }
        }

        @Override
        protected void succeeded() {
            super.succeeded();
            try {
                javafx.scene.image.Image fxImage = get();
                if (fxImage == null) {
                    LOGGER.log(Level.WARNING, IMAGE_UTILS_COULD_NOT_READ_UNSUPPORTE_OR_CORRUPT, ImageUtils.getContentPathSafe(file));
                } else {
                    if (fxImage.isError()) {
                        //if there was somekind of error, log it
                        LOGGER.log(Level.WARNING, IMAGE_UTILS_COULD_NOT_READ_UNSUPPORTE_OR_CORRUPT + ": " + ObjectUtils.toString(fxImage.getException()), ImageUtils.getContentPathSafe(file));
                    }
                }
            } catch (InterruptedException | ExecutionException ex) {
                failed();
            }
        }

        @Override
        protected void failed() {
            super.failed();
            LOGGER.log(Level.WARNING, IMAGE_UTILS_COULD_NOT_READ_UNSUPPORTE_OR_CORRUPT + ": " + ObjectUtils.toString(getException()), ImageUtils.getContentPathSafe(file));
        }

        @Override
        public void imageComplete(ImageReader source) {
            updateProgress(100, 100);
        }

        @Override
        public void imageStarted(ImageReader source, int imageIndex) {
        }

        @Override
        public void sequenceStarted(ImageReader source, int minIndex) {
        }

        @Override
        public void sequenceComplete(ImageReader source) {
        }

        @Override
        public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {
        }

        @Override
        public void thumbnailProgress(ImageReader source, float percentageDone) {
        }

        @Override
        public void thumbnailComplete(ImageReader source) {
        }

        @Override
        public void readAborted(ImageReader source) {
        }
    }

    /**
     * Get the unique path for the content, or if that fails, just return the
     * name.
     *
     * @param content
     *
     * @return
     */
    private static String getContentPathSafe(Content content) {
        try {
            return content.getUniquePath();
        } catch (TskCoreException tskCoreException) {
            String contentName = content.getName();
            LOGGER.log(Level.SEVERE, "Failed to get unique path for " + contentName, tskCoreException); //NOI18N NON-NLS
            return contentName;
        }
    }
}
