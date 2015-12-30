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
import static java.util.Objects.isNull;
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
 * Utilities for working with Images and creating thumbnails. Reuses thumbnails
 * by storing them in the case's cache directory.
 */
public class ImageUtils {

    private static final Logger LOGGER = Logger.getLogger(ImageUtils.class.getName());

    private static final String NO_IMAGE_READER_FOUND_FOR = "No ImageReader found for ";
    private static final String IMAGE_IO_COULD_NOT_DETERMINE_WIDTH = "ImageIO could not determine width of {0}: ";
    private static final String IMAGE_IO_COULD_NOT_DETERMINE_HEIGHT = "ImageIO could not determine height of {0}: ";
    private static final String COULD_NOT_CREATE_IMAGE_INPUT_STREAM = "Could not create ImageInputStream.";

    /**
     * save thumbnails to disk as this format
     */
    private static final String FORMAT = "png"; //NON-NLS

    public static final int ICON_SIZE_SMALL = 50;
    public static final int ICON_SIZE_MEDIUM = 100;
    public static final int ICON_SIZE_LARGE = 200;

    private static final Logger logger = LOGGER;
    private static final BufferedImage DEFAULT_THUMBNAIL;

    private static final String IMAGE_GIF_MIME = "image/gif";
    private static final SortedSet<String> GIF_MIME_SET = ImmutableSortedSet.copyOf(new String[]{IMAGE_GIF_MIME});

    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS;
    private static final SortedSet<String> SUPPORTED_IMAGE_MIME_TYPES;
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
                "application/x-123")); //TODO: is this correct? -jm
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

    public static boolean isGIF(AbstractFile file) {
        try {
            final FileTypeDetector fileTypeDetector = getFileTypeDetector();
            if (nonNull(fileTypeDetector)) {
                String fileType = fileTypeDetector.getFileType(file);
                return IMAGE_GIF_MIME.equalsIgnoreCase(fileType);
            }
        } catch (TskCoreException | FileTypeDetectorInitException ex) {
            LOGGER.log(Level.WARNING, "Failed to get mime type with FileTypeDetector.", ex);
        }
        LOGGER.log(Level.WARNING, "Falling back on direct mime type check.");
        switch (file.isMimeType(GIF_MIME_SET)) {

            case TRUE:
                return true;
            case UNDEFINED:
                LOGGER.log(Level.WARNING, "Falling back on extension check.");
                return "gif".equals(file.getNameExtension());
            case FALSE:
            default:
                return false;
        }
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
            logContentError(logger, Level.WARNING, "Failed to look up mimetype for {0} using FileTypeDetector: " + ex.toString() + "\nFallingback on AbstractFile.isMimeType", file);

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
    public static BufferedImage getThumbnail(Content content, int iconSize) {
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
                    LOGGER.log(Level.WARNING, "ImageIO had a problem reading thumbnail for image {0}: {1}", new Object[]{content.getName(), ex.getLocalizedMessage()}); //NON-NLS
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
     *
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
    private static BufferedImage generateAndSaveThumbnail(AbstractFile file, int iconSize, File cacheFile) {
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
    private static BufferedImage generateImageThumbnail(AbstractFile content, int iconSize) {

        try {
            final ReadImageTask readImageTask = new ReadImageTask(content);

            readImageTask.run();
            BufferedImage bi = SwingFXUtils.fromFXImage(readImageTask.get(), null);

            if (bi == null) {
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
            LOGGER.log(Level.WARNING, "Could not scale image (too large) " + content.getName() + ": " + e.toString()); //NON-NLS
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "ImageIO could not load image " + content.getName() + ": " + e.toString()); //NON-NLS
        }
        return null;
    }

    static public int getWidth(AbstractFile file) throws IIOException, IOException {
        return getIntProperty(file,
                IMAGE_IO_COULD_NOT_DETERMINE_WIDTH,
                imageReader -> imageReader.getWidth(0)
        );
    }

    static public int getHeight(AbstractFile file) throws IIOException, IOException {
        return getIntProperty(file,
                IMAGE_IO_COULD_NOT_DETERMINE_HEIGHT,
                imageReader -> imageReader.getHeight(0)
        );
    }

    private interface PropertyExctractor<T> {

        public T extract(ImageReader reader) throws IOException;
    }

    /**
     *
     * @param file          the value of file
     * @param errorTemplate the value of errorTemplate
     *
     * @return the int
     *
     * @throws IIOException
     * @throws IOException
     */
    private static <T> T getIntProperty(AbstractFile file, final String errorTemplate, PropertyExctractor<T> propertyExtractor) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(new ReadContentInputStream(file));) {

            try (ImageInputStream input = ImageIO.createImageInputStream(inputStream)) {
                if (input == null) {
                    IIOException iioException = new IIOException(COULD_NOT_CREATE_IMAGE_INPUT_STREAM);
                    ImageUtils.logContentError(LOGGER, Level.WARNING, errorTemplate + iioException.toString(), file);
                    throw iioException;
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    reader.setInput(input);
                    try {
                        return propertyExtractor.extract(reader);
                    } catch (IOException ex) {
                        ImageUtils.logContentError(LOGGER, Level.WARNING, errorTemplate + ex.toString(), file);
                        throw ex;
                    }
                } else {
                    IIOException iioException = newImageReaderException(file);
                    ImageUtils.logContentError(LOGGER, Level.WARNING, errorTemplate + iioException.toString(), file);

                    throw iioException;
                }
            }
        }
    }

    private static IIOException newImageReaderException(AbstractFile file) {
        try {
            return new IIOException(NO_IMAGE_READER_FOUND_FOR + file.getUniquePath());
        } catch (TskCoreException ex) {
            return new IIOException(NO_IMAGE_READER_FOUND_FOR + file.getName()); //NOI18N
        }
    }

    public static Task<javafx.scene.image.Image> newGetThumbnailTask(AbstractFile file, int iconSize) {
        return new GetOrGenerateThumbnailTask(file, iconSize);
    }

    static private class GetOrGenerateThumbnailTask extends ReadImageTaskBase {

        private final int iconSize;
        private final File cacheFile;

        private GetOrGenerateThumbnailTask(AbstractFile file, int iconSize) {
            super(file);
            updateMessage("Loading thumbnail for " + file.getName());
            this.iconSize = iconSize;
            cacheFile = getCachedThumbnailLocation(file.getId());
        }

        @Override
        protected javafx.scene.image.Image call() throws Exception {

            // If a thumbnail file is already saved locally, just read that.
            if (cacheFile.exists()) {
                try {
                    BufferedImage cachedThumbnail = ImageIO.read(cacheFile);
                    if (nonNull(cachedThumbnail) && cachedThumbnail.getWidth() == iconSize) {
                        return SwingFXUtils.toFXImage(cachedThumbnail, null);
                    }
                } catch (Exception ex) {
                    logContentError(logger, Level.WARNING, "ImageIO had a problem reading thumbnail for image {0}: " + ex.toString(), file);
                }
            }

            BufferedImage thumbnail = null;
            if (VideoUtils.isVideoThumbnailSupported(file)) {
                if (openCVLoaded) {
                    updateMessage("Generating preview for " + file.getName());
                    thumbnail = VideoUtils.generateVideoThumbnail(file, iconSize);
                } else {
                    thumbnail = DEFAULT_THUMBNAIL;
                }
            } else {
                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(readImage(), null);
                if (isNull(bufferedImage)) {
                    LOGGER.log(Level.WARNING, "Failed to read image for thumbnail generation.");
                    throw new IIOException("Failed to read image for thumbnail generation.");
                }
                updateProgress(-1, 1);

                try {
                    thumbnail = ScalrWrapper.resizeFast(bufferedImage, iconSize);
                } catch (IllegalArgumentException | OutOfMemoryError e) {
                    // if resizing does not work due to extreme aspect ratio, crop the image instead.
                    logContentError(logger, Level.WARNING, "Could not scale image {0}: " + e.toString() + ".  Attemptying to crop {0} instead", file);

                    final int height = bufferedImage.getHeight();
                    final int width = bufferedImage.getWidth();
                    if (iconSize < height || iconSize < width) {
                        final int cropHeight = Math.min(iconSize, height);
                        final int cropWidth = Math.min(iconSize, width);

                        try {
                            thumbnail = ScalrWrapper.cropImage(bufferedImage, cropWidth, cropHeight);
                        } catch (Exception cropException) {
                            logContentError(logger, Level.WARNING, "Could not crop image {0}: " + cropException.toString(), file);
                            throw cropException;
                        }
                    }
                } catch (Exception e) {
                    logContentError(logger, Level.WARNING, "Could not scale image {0}: " + e.toString(), file);
                    throw e;
                }
            }
            updateProgress(-1, 1);
            if (nonNull(thumbnail) && DEFAULT_THUMBNAIL != thumbnail) {
                saveThumbnail(thumbnail);
            }
            return SwingFXUtils.toFXImage(thumbnail, null);
        }

        private void saveThumbnail(BufferedImage thumbnail) {
            imageSaver.execute(() -> {
                try {
                    Files.createParentDirs(cacheFile);
                    if (cacheFile.exists()) {
                        cacheFile.delete();
                    }
                    ImageIO.write(thumbnail, FORMAT, cacheFile);
                } catch (IllegalArgumentException | IOException ex) {
                    logContentError(logger, Level.WARNING, "Could not write thumbnail for {0}: " + ex.toString(), file);
                }
            });
        }
    }

    public static Task<javafx.scene.image.Image> newReadImageTask(AbstractFile file) {
        return new ReadImageTask(file);
    }

    static private class ReadImageTask extends ReadImageTaskBase {

        ReadImageTask(AbstractFile file) {
            super(file);
        }

        @Override
        @NbBundle.Messages({
            "# {0} - file name",
            "LoadImageTask.mesageText=Reading image: {0}"})
        protected javafx.scene.image.Image call() throws Exception {
            updateMessage(Bundle.LoadImageTask_mesageText(file.getName()));
            return readImage();
        }
    }

    static private abstract class ReadImageTaskBase extends Task<javafx.scene.image.Image> implements IIOReadProgressListener {

        final AbstractFile file;
        private volatile BufferedImage bufferedImage = null;
        private ImageReader reader;

        ReadImageTaskBase(AbstractFile file) {
            this.file = file;
        }

        public BufferedImage getBufferedImage() throws InterruptedException, ExecutionException {
            get();
            return bufferedImage;
        }

        protected javafx.scene.image.Image readImage() throws IOException {
            try (InputStream inputStream = new BufferedInputStream(new ReadContentInputStream(file));) {
                if (ImageUtils.isGIF(file)) {
                    //directly read GIF to preserve potential animation,
                    javafx.scene.image.Image image = new javafx.scene.image.Image(new BufferedInputStream(inputStream));
                    if (image.isError() == false) {
                        return image;
                    }
                    //fall through to default iamge reading code if there was an error
                }

                try (ImageInputStream input = ImageIO.createImageInputStream(inputStream)) {
                    if (input == null) {
                        throw new IIOException(COULD_NOT_CREATE_IMAGE_INPUT_STREAM);
                    }
                    Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

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

                        bufferedImage = reader.getImageTypes(0).next().createBufferedImage(reader.getWidth(0), reader.getHeight(0));
                        param.setDestination(bufferedImage);
                        try {
                            reader.read(0, param);
                            if (isCancelled()) {
                                return null;
                            }
                        } catch (IOException iOException) {
                            // Ignore this exception or display a warning or similar, for exceptions happening during decoding
                            logContentError(logger, Level.WARNING, "ImageIO could not read {0}.  It may be unsupported or corrupt: " + iOException.toString(), file);
                        }
                        reader.removeIIOReadProgressListener(this);
                        reader.dispose();
                        return SwingFXUtils.toFXImage(bufferedImage, null);
                    } else {
                        throw newImageReaderException(file);

                    }
                }
            }
        }

        @Override
        public void imageProgress(ImageReader source, float percentageDone) {
            //update this task with the progress reported by ImageReader.read
            updateProgress(percentageDone, 100);
            if (isCancelled()) {
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
                    logContentError(logger, Level.WARNING, "ImageIO could not read {0}.  It may be unsupported or corrupt", file);
                } else {
                    if (fxImage.isError()) {
                        //if there was somekind of error, log it
                        logContentError(logger, Level.WARNING, "ImageIO could not read {0}.  It may be unsupported or corrupt:" + ObjectUtils.toString(fxImage.getException()), file);
                    }
                }
            } catch (InterruptedException | ExecutionException ex) {
                failed();
            }
        }

        @Override
        protected void failed() {
            super.failed();
            logContentError(logger, Level.WARNING, "ImageIO could not read {0}.  It may be unsupported or corrupt: " + ObjectUtils.toString(getException()), file);
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
     * @param logger   the value of logger
     * @param template the value of template
     * @param file     the value of file
     * @param level    the value of level
     */
    public static void logContentError(Logger logger, final Level level, final String template, Content file) {
        try {
            logger.log(level, template, file.getUniquePath());
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "Failed to get unique path for " + file.getName(), tskCoreException);
            logger.log(level, template, file.getName());
        }
    }
}
