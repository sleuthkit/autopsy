/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import static java.util.Objects.nonNull;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
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

    /**
     * save thumbnails to disk as this format
     */
    private static final String FORMAT = "png"; //NON-NLS

    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;

    private static final BufferedImage DEFAULT_THUMBNAIL;

    private static final List<String> GIF_EXTENSION_LIST = Arrays.asList("gif");
    private static final SortedSet<String> GIF_MIME_SET = ImmutableSortedSet.copyOf(new String[]{"image/gif"});

    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS = new ArrayList<>();
    private static final SortedSet<String> SUPPORTED_IMAGE_MIME_TYPES;

    private static final boolean OPEN_CV_LOADED;

    /**
     * Map from tsk object id to Java File object. Used to get the same File for
     * different tasks related to the same object so we can then synchronize on
     * the File.
     *
     * NOTE: Must be cleared when the case is changed.
     */
    private static final ConcurrentHashMap<Long, File> cacheFileMap = new ConcurrentHashMap<>();

    static {
        ImageIO.scanForPlugins();
        BufferedImage tempImage;
        try {
            tempImage = ImageIO.read(ImageUtils.class.getResourceAsStream("/org/sleuthkit/autopsy/images/file-icon.png"));//NON-NLS
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load default icon.", ex); //NON-NLS
            tempImage = null;
        }
        DEFAULT_THUMBNAIL = tempImage;

        //load opencv libraries
        boolean openCVLoadedTemp;
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            if (System.getProperty("os.arch").equals("amd64") || System.getProperty("os.arch").equals("x86_64")) { //NON-NLS
                System.loadLibrary("opencv_ffmpeg248_64"); //NON-NLS
            } else {
                System.loadLibrary("opencv_ffmpeg248"); //NON-NLS
            }

            openCVLoadedTemp = true;
        } catch (UnsatisfiedLinkError e) {
            openCVLoadedTemp = false;
            LOGGER.log(Level.SEVERE, "OpenCV Native code library failed to load", e); //NON-NLS
            //TODO: show warning bubble

        }

        OPEN_CV_LOADED = openCVLoadedTemp;
        SUPPORTED_IMAGE_EXTENSIONS.addAll(Arrays.asList(ImageIO.getReaderFileSuffixes()));
        SUPPORTED_IMAGE_EXTENSIONS.add("tec"); // Add JFIF .tec files
        SUPPORTED_IMAGE_EXTENSIONS.removeIf("db"::equals); // remove db files

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
                "application/x-123")); //TODO: is this correct? -jm //NON-NLS
        SUPPORTED_IMAGE_MIME_TYPES.removeIf("application/octet-stream"::equals); //NON-NLS

        //Clear the file map when the case changes, so we don't accidentaly get images from the old case.
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), evt -> cacheFileMap.clear());
    }

    /**
     * initialized lazily
     */
    private static FileTypeDetector fileTypeDetector;

    /**
     * Thread/Executor that saves generated thumbnails to disk in the background
     */
    private static final Executor imageSaver =
            Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder()
                    .namingPattern("thumbnail-saver-%d").build()); //NON-NLS

    public static List<String> getSupportedImageExtensions() {
        return Collections.unmodifiableList(SUPPORTED_IMAGE_EXTENSIONS);
    }

    public static SortedSet<String> getSupportedImageMimeTypes() {
        return Collections.unmodifiableSortedSet(SUPPORTED_IMAGE_MIME_TYPES);
    }

    /**
     * Get the default thumbnail, which is the icon for a file. Used when we can
     * not generate a content based thumbnail.
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

        if (!(content instanceof AbstractFile)) {
            return false;
        }
        AbstractFile file = (AbstractFile) content;

        return VideoUtils.isVideoThumbnailSupported(file)
                || isImageThumbnailSupported(file);
    }

    /**
     * Is the file an image that we can read and generate a thumbnail for?
     *
     * @param file the AbstractFile to test
     *
     * @return true if the file is an image we can read and generate thumbnail
     *         for.
     */
    public static boolean isImageThumbnailSupported(AbstractFile file) {
        return isMediaThumbnailSupported(file, "image/", SUPPORTED_IMAGE_MIME_TYPES, SUPPORTED_IMAGE_EXTENSIONS) || hasImageFileHeader(file);//NON-NLS
    }

    /**
     * Checks the MIME type and/or extension of a file to determine whether it
     * is a GIF.
     *
     * @param file the AbstractFile to test
     *
     * @return true if the file is a gif
     */
    public static boolean isGIF(AbstractFile file) {
        return isMediaThumbnailSupported(file, null, GIF_MIME_SET, GIF_EXTENSION_LIST);
    }

    /**
     * Check if making a thumbnail for the given file is supported by checking
     * its extension and/or MIME type against the supplied collections.
     *
     * //TODO: this should move to a better place. Should ImageUtils and
     * VideoUtils both implement/extend some base interface/abstract class. That
     * would be the natural place to put this.
     *
     * @param file               the AbstractFile to test
     * @param mimeTypePrefix     a MIME 'top-level type name' such as "image/",
     *                           including the "/". In addition to the list of
     *                           supported MIME types, any type that starts with
     *                           this prefix will be regarded as supported
     * @param supportedMimeTypes a collection of mimetypes that are supported
     * @param supportedExtension a collection of extensions that are supported
     *
     * @return true if a thumbnail can be generated for the given file based on
     *         the given MIME type prefix and lists of supported MIME types and
     *         extensions
     */
    static boolean isMediaThumbnailSupported(AbstractFile file, String mimeTypePrefix, final Collection<String> supportedMimeTypes, final List<String> supportedExtension) {
        if (false == file.isFile() || file.getSize() <= 0) {
            return false;
        }

        String extension = file.getNameExtension();

        if (StringUtils.isNotBlank(extension) && supportedExtension.contains(extension)) {
            return true;
        } else {
            try {
                String mimeType = getFileTypeDetector().getMIMEType(file);
                if (StringUtils.isNotBlank(mimeTypePrefix) && mimeType.startsWith(mimeTypePrefix)) {
                    return true;
                }
                return supportedMimeTypes.contains(mimeType);
            } catch (FileTypeDetectorInitException ex) {
                LOGGER.log(Level.SEVERE, "Error determining MIME type of " + getContentPathSafe(file), ex);//NON-NLS
                return false;
            }
        }
    }

    /**
     * //TODO: AUT-2057 this FileTypeDetector needs to be recreated when the
     * user adds new user defined file types.
     *
     * get a FileTypeDetector
     *
     * @return a FileTypeDetector
     *
     * @throws FileTypeDetectorInitException if initializing the
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
     * @param content  the content to generate a thumbnail for
     * @param iconSize the size (one side of a square) in pixels to generate
     *
     * @return A thumbnail for the given image or a default one if there was a
     *         problem making a thumbnail.
     */
    public static BufferedImage getThumbnail(Content content, int iconSize) {
        if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile) content;
            if (ImageUtils.isGIF(file)) {
                /*
                 * Intercepting the image reading code for GIFs here allows us
                 * to rescale easily, but we lose animations.
                 */
                try (BufferedInputStream bufferedReadContentStream = getBufferedReadContentStream(file);) {
                    if (Thread.interrupted()) {
                        return DEFAULT_THUMBNAIL;
                    }
                    final BufferedImage image = ImageIO.read(bufferedReadContentStream);
                    if (image != null) {
                        if (Thread.interrupted()) {
                            return DEFAULT_THUMBNAIL;
                        }
                        return ScalrWrapper.resizeHighQuality(image, iconSize, iconSize);
                    }
                } catch (IOException iOException) {
                    LOGGER.log(Level.WARNING, "Failed to get thumbnail for " + getContentPathSafe(content), iOException); //NON-NLS
                }
                return DEFAULT_THUMBNAIL;
            }

            Task<javafx.scene.image.Image> thumbnailTask = newGetThumbnailTask(file, iconSize, true);
            if (Thread.interrupted()) {
                return DEFAULT_THUMBNAIL;
            }
            thumbnailTask.run();
            try {
                return SwingFXUtils.fromFXImage(thumbnailTask.get(), null);
            } catch (InterruptedException | ExecutionException ex) {
                LOGGER.log(Level.WARNING, "Failed to get thumbnail for " + getContentPathSafe(content), ex); //NON-NLS
            }
        }
        return DEFAULT_THUMBNAIL;
    }

    /**
     * Get a BufferedInputStream wrapped around a ReadContentStream for the
     * given AbstractFile.
     *
     * @param file The AbstractFile to get a stream for.
     *
     * @return A BufferedInputStream wrapped around a ReadContentStream for the
     *         given AbstractFile
     */
    private static BufferedInputStream getBufferedReadContentStream(AbstractFile file) {
        return new BufferedInputStream(new ReadContentInputStream(file));
    }

    /**
     * Get a thumbnail of a specified size for the given image. Generates the
     * thumbnail if it is not already cached.
     *
     * @param content  the content to generate a thumbnail for
     * @param iconSize the size (one side of a square) in pixels to generate
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
     * Get the location,as a java File, of the cached thumbnail for an file with
     * the given fileID . The returned File may not exist on disk yet.
     *
     * @param fileID the fileID to get the cached thumbnail location for
     *
     * @return A File object representing the location of the cached thumbnail.
     *         This file may not actually exist(yet). Returns null if there was
     *         any problem getting the file, such as no case was open.
     */
    private static File getCachedThumbnailLocation(long fileID) {
        return cacheFileMap.computeIfAbsent(fileID, id -> {
            try {
                String cacheDirectory = Case.getOpenCase().getCacheDirectory();
                return Paths.get(cacheDirectory, "thumbnails", fileID + ".png").toFile(); //NON-NLS
            } catch (NoCurrentCaseException e) {
                LOGGER.log(Level.WARNING, "Could not get cached thumbnail location.  No case is open."); //NON-NLS
                return null;
            }
        });
    }

    /**
     * Do a direct check to see if the given file has an image file header.
     * NOTE: Currently only jpeg and png are supported.
     *
     * @param file the AbstractFile to check
     *
     * @return true if the given file has one of the supported image headers.
     */
    public static boolean hasImageFileHeader(AbstractFile file) {
        return isJpegFileHeader(file) || isPngFileHeader(file);
    }

    /**
     * Check if the given file is a jpeg based on header.
     *
     * @param file the AbstractFile to check
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
     * Find the offset for the first Start Of Image marker (0xFFD8) in JFIF,
     * allowing for leading End Of Image markers.
     *
     * @param file the AbstractFile to parse
     *
     * @return Offset of first Start Of Image marker, or 0 if none found. This
     *         will let ImageIO try to open it from offset 0.
     */
    private static long getJfifStartOfImageOffset(AbstractFile file) {
        byte[] fileHeaderBuffer;
        long length;
        try {
            length = file.getSize();
            if (length % 2 != 0) {
                length -= 1; // Make it an even number so we can parse two bytes at a time
            }
            if (length >= 1024) {
                length = 1024;
            }
            fileHeaderBuffer = readHeader(file, (int) length); // read up to first 1024 bytes
        } catch (TskCoreException ex) {
            // Couldn't read header. Let ImageIO try it.
            return 0;
        }

        if (fileHeaderBuffer != null) {
            for (int index = 0; index < length; index += 2) {
                // Look for Start Of Image marker and return the index when it's found
                if ((fileHeaderBuffer[index] == (byte) 0xFF) && (fileHeaderBuffer[index + 1] == (byte) 0xD8)) {
                    return index;
                }
            }
        }

        // Didn't match JFIF. Let ImageIO try to open it from offset 0.
        return 0;
    }

    /**
     * Check if the given file is a png based on header.
     *
     * @param file the AbstractFile to check
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
            throw new TskCoreException("Could not read " + buffLength + " bytes from " + file.getName());//NON-NLS
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
                "ImageIO could not determine width of {0}: ", //NON-NLS
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
                "ImageIO could not determine height of {0}: ", //NON-NLS
                imageReader -> imageReader.getHeight(0)
        );

    }

    /**
     * Functional interface for methods that extract a property out of an
     * ImageReader. Initially created to abstract over
     * getImageHeight(org.sleuthkit.datamodel.AbstractFile) and
     * getImageWidth(org.sleuthkit.datamodel.AbstractFile)
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
     * file.
     *
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
        try (InputStream inputStream = getBufferedReadContentStream(file);
                ImageInputStream input = ImageIO.createImageInputStream(inputStream)) {
            if (input == null) {
                IIOException iioException = new IIOException("Could not create ImageInputStream.");
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
                IIOException iioException = new IIOException("No ImageReader found.");
                LOGGER.log(Level.WARNING, errorTemplate + iioException.toString(), getContentPathSafe(file));
                throw iioException;
            }
        }
    }

    /**
     * Create a new Task that will get a thumbnail for the given image of the
     * specified size. If a cached thumbnail is available it will be returned as
     * the result of the task, otherwise a new thumbnail will be created and
     * cached.
     *
     * Note: the returned task is suitable for running in a background thread,
     * but is not started automatically. Clients are responsible for running the
     * task, monitoring its progress, and using its result.
     *
     * @param file             The file to create a thumbnail for.
     * @param iconSize         The size of the thumbnail.
     * @param defaultOnFailure Whether or not to default on failure.
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

        private static final String FAILED_TO_READ_IMAGE_FOR_THUMBNAIL_GENERATION = "Failed to read {0} for thumbnail generation."; //NON-NLS

        private final int iconSize;
        private final File cacheFile;
        private final boolean defaultOnFailure;

        @NbBundle.Messages({"# {0} - file name",
            "GetOrGenerateThumbnailTask.loadingThumbnailFor=Loading thumbnail for {0}",
            "# {0} - file name",
            "GetOrGenerateThumbnailTask.generatingPreviewFor=Generating preview for {0}"})
        private GetThumbnailTask(AbstractFile file, int iconSize, boolean defaultOnFailure) {
            super(file);
            updateMessage(Bundle.GetOrGenerateThumbnailTask_loadingThumbnailFor(file.getName()));
            this.iconSize = iconSize;
            this.defaultOnFailure = defaultOnFailure;
            this.cacheFile = getCachedThumbnailLocation(file.getId());
        }

        @Override
        protected javafx.scene.image.Image call() throws Exception {
            if (isCancelled()) {
                return null;
            }
            if (isGIF(file)) {
                return readImage();
            }

            // If a thumbnail file is already saved locally, just read that.
            if (cacheFile != null) {
                synchronized (cacheFile) {
                    if (cacheFile.exists()) {
                        try {
                            if (isCancelled()) {
                                return null;
                            }
                            BufferedImage cachedThumbnail = ImageIO.read(cacheFile);
                            if (isCancelled()) {
                                return null;
                            }
                            if (nonNull(cachedThumbnail) && cachedThumbnail.getWidth() == iconSize) {
                                return SwingFXUtils.toFXImage(cachedThumbnail, null);
                            }
                        } catch (Exception ex) {
                            LOGGER.log(Level.WARNING, "ImageIO had a problem reading the cached thumbnail for {0}: " + ex.toString(), ImageUtils.getContentPathSafe(file)); //NON-NLS
                            cacheFile.delete();  //since we can't read the file we might as well delete it.
                        }
                    }
                }
            }

            //There was no correctly-sized cached thumbnail so make one.
            BufferedImage thumbnail = null;
            if (VideoUtils.isVideoThumbnailSupported(file)) {
                if (OPEN_CV_LOADED) {
                    updateMessage(Bundle.GetOrGenerateThumbnailTask_generatingPreviewFor(file.getName()));
                    if (isCancelled()) {
                        return null;
                    }
                    thumbnail = VideoUtils.generateVideoThumbnail(file, iconSize);
                }
                if (null == thumbnail) {
                    if (defaultOnFailure) {
                        thumbnail = DEFAULT_THUMBNAIL;
                    } else {
                        throw new IIOException("Failed to generate a thumbnail for " + getContentPathSafe(file));//NON-NLS
                    }
                }

            } else {
                if (isCancelled()) {
                    return null;
                }
                //read the image into a buffered image.
                //TODO: I don't like this, we just converted it from BufferedIamge to fx Image -jm
                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(readImage(), null);
                if (null == bufferedImage) {
                    String msg = MessageFormat.format(FAILED_TO_READ_IMAGE_FOR_THUMBNAIL_GENERATION, getContentPathSafe(file));
                    LOGGER.log(Level.WARNING, msg);
                    throw new IIOException(msg);
                }
                updateProgress(-1, 1);
                if (isCancelled()) {
                    return null;
                }
                //resize, or if that fails, crop it
                try {
                    if (isCancelled()) {
                        return null;
                    }
                    thumbnail = ScalrWrapper.resizeFast(bufferedImage, iconSize);
                } catch (IllegalArgumentException | OutOfMemoryError e) {
                    // if resizing does not work due to extreme aspect ratio or oom, crop the image instead.
                    LOGGER.log(Level.WARNING, "Cropping {0}, because it could not be scaled: " + e.toString(), ImageUtils.getContentPathSafe(file)); //NON-NLS

                    final int height = bufferedImage.getHeight();
                    final int width = bufferedImage.getWidth();
                    if (iconSize < height || iconSize < width) {
                        final int cropHeight = Math.min(iconSize, height);
                        final int cropWidth = Math.min(iconSize, width);
                        try {
                            if (isCancelled()) {
                                return null;
                            }
                            if (isCancelled()) {
                                return null;
                            }
                            thumbnail = ScalrWrapper.cropImage(bufferedImage, cropWidth, cropHeight);
                        } catch (Exception cropException) {
                            LOGGER.log(Level.WARNING, "Could not crop {0}: " + cropException.toString(), ImageUtils.getContentPathSafe(file)); //NON-NLS
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Could not scale {0}: " + e.toString(), ImageUtils.getContentPathSafe(file)); //NON-NLS
                    throw e;
                }
            }

            updateProgress(-1, 1);

            //if we got a valid thumbnail save it
            if ((cacheFile != null) && thumbnail != null && DEFAULT_THUMBNAIL != thumbnail) {
                saveThumbnail(thumbnail);
            }
            if (isCancelled()) {
                return null;
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
                    synchronized (cacheFile) {
                        Files.createParentDirs(cacheFile);
                        if (cacheFile.exists()) {
                            cacheFile.delete();
                        }
                        ImageIO.write(thumbnail, FORMAT, cacheFile);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Could not write thumbnail for {0}: " + ex.toString(), ImageUtils.getContentPathSafe(file)); //NON-NLS
                }
            });
        }
    }

    /**
     * Create a new Task that will read the file into memory as an
     * javafx.scene.image.Image.
     *
     * Note: the returned task is suitable for running in a background thread,
     * but is not started automatically. Clients are responsible for running the
     * task, monitoring its progress, and using its result(including testing for
     * null).
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
    @NbBundle.Messages({
        "# {0} - file name",
        "ReadImageTask.mesageText=Reading image: {0}"})
    static private class ReadImageTask extends ReadImageTaskBase {

        ReadImageTask(AbstractFile file) {
            super(file);
            updateMessage(Bundle.ReadImageTask_mesageText(file.getName()));
        }

        @Override
        protected javafx.scene.image.Image call() throws Exception {
            return readImage();
        }
    }

    /**
     * Base class for tasks that need to read AbstractFiles as Images.
     */
    static private abstract class ReadImageTaskBase extends Task<javafx.scene.image.Image> implements IIOReadProgressListener {

        private static final String IMAGEIO_COULD_NOT_READ_UNSUPPORTED_OR_CORRUPT = "ImageIO could not read {0}.  It may be unsupported or corrupt"; //NON-NLS
        final AbstractFile file;

        ReadImageTaskBase(AbstractFile file) {
            this.file = file;
        }

        protected javafx.scene.image.Image readImage() throws IOException {
            if (isCancelled()) {
                return null;
            }
            if (ImageUtils.isGIF(file)) {
                //use JavaFX to directly read GIF to preserve potential animation
                javafx.scene.image.Image image = new javafx.scene.image.Image(getBufferedReadContentStream(file));
                if (image.isError() == false) {
                    return image;
                }
            } else if (file.getNameExtension().equalsIgnoreCase("tec")) { //NON-NLS
                ReadContentInputStream readContentInputStream = new ReadContentInputStream(file);
                // Find first Start Of Image marker
                readContentInputStream.seek(getJfifStartOfImageOffset(file));
                //use JavaFX to directly read .tec files
                javafx.scene.image.Image image = new javafx.scene.image.Image(new BufferedInputStream(readContentInputStream));
                if (image.isError() == false) {
                    return image;
                }
            }
            //fall through to default image reading code if there was an error
            return getImageProperty(file, "ImageIO could not read {0}: ",
                    imageReader -> {
                        imageReader.addIIOReadProgressListener(ReadImageTaskBase.this);
                        /*
                         * This is the important part, get or create a
                         * ImageReadParam, create a destination image to hold
                         * the decoded result, then pass that image with the
                         * param.
                         */
                        ImageReadParam param = imageReader.getDefaultReadParam();
                        BufferedImage bufferedImage = imageReader.getImageTypes(0).next().createBufferedImage(imageReader.getWidth(0), imageReader.getHeight(0));
                        param.setDestination(bufferedImage);
                        try {
                            if (isCancelled()) {
                                return null;
                            }
                            bufferedImage = imageReader.read(0, param); //should always be same bufferedImage object
                        } catch (IOException iOException) {
                            LOGGER.log(Level.WARNING, IMAGEIO_COULD_NOT_READ_UNSUPPORTED_OR_CORRUPT + ": " + iOException.toString(), ImageUtils.getContentPathSafe(file)); //NON-NLS
                        } finally {
                            imageReader.removeIIOReadProgressListener(ReadImageTaskBase.this);
                        }
                        if (isCancelled()) {
                            return null;
                        }
                        return SwingFXUtils.toFXImage(bufferedImage, null);
                    }
            );
        }

        @Override
        public void imageProgress(ImageReader reader, float percentageDone) {
            //update this task with the progress reported by ImageReader.read
            updateProgress(percentageDone, 100);
            if (isCancelled()) {
                reader.removeIIOReadProgressListener(this);
                reader.abort();
                reader.dispose();
            }
        }

        @Override
        public boolean isCancelled() {
            if (Thread.interrupted()) {
                this.cancel(true);
                return true;
            }
            return super.isCancelled();
        }

        @Override
        protected void succeeded() {
            super.succeeded();
            try {
                javafx.scene.image.Image fxImage = get();
                if (fxImage == null) {
                    LOGGER.log(Level.WARNING, IMAGEIO_COULD_NOT_READ_UNSUPPORTED_OR_CORRUPT, ImageUtils.getContentPathSafe(file));
                } else if (fxImage.isError()) {
                    //if there was somekind of error, log it
                    LOGGER.log(Level.WARNING, IMAGEIO_COULD_NOT_READ_UNSUPPORTED_OR_CORRUPT + ": " + ObjectUtils.toString(fxImage.getException()), ImageUtils.getContentPathSafe(file));
                }
            } catch (InterruptedException | ExecutionException ex) {
                failed();
            }
        }

        @Override
        protected void failed() {
            super.failed();
            LOGGER.log(Level.WARNING, IMAGEIO_COULD_NOT_READ_UNSUPPORTED_OR_CORRUPT + ": " + ObjectUtils.toString(getException()), ImageUtils.getContentPathSafe(file));
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
     * @return the unique path for the content, or if that fails, just the name.
     */
    static String getContentPathSafe(Content content) {
        try {
            return content.getUniquePath();
        } catch (TskCoreException tskCoreException) {
            String contentName = content.getName();
            LOGGER.log(Level.SEVERE, "Failed to get unique path for " + contentName, tskCoreException); //NON-NLS
            return contentName;
        }
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
     * Get a thumbnail of a specified size for the given image. Generates the
     * thumbnail if it is not already cached.
     *
     * @param content
     * @param iconSize
     *
     * @return a thumbnail for the given image or a default one if there was a
     *         problem making a thumbnail.
     *
     * @deprecated use getThumbnail(org.sleuthkit.datamodel.Content, int)
     * instead.
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
     * @return File object for cached image. Is guaranteed to exist, as long as
     *         there was not an error generating or saving the thumbnail.
     *
     * @deprecated use getCachedThumbnailFile(org.sleuthkit.datamodel.Content,
     * int) instead.
     *
     */
    @Nullable
    @Deprecated
    public static File getIconFile(Content content, int iconSize) {
        return getCachedThumbnailFile(content, iconSize);

    }
}
