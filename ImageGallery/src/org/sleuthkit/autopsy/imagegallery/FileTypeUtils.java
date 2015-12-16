/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Enum style singleton to provide utilities related to questions about a files
 * type, and wheather it should be supported in Image Gallery.
 *
 * TODO: refactor this to remove code that duplicates
 * org.sleuthkit.autopsy.coreutils.ImageUtils
 */
public enum FileTypeUtils {

    instance;

    private static final Logger LOGGER = Logger.getLogger(FileTypeUtils.class.getName());

    /**
     * Set of specific mimetypes (as strings) that we should support(ie, include
     * in db and show to user). These are in addition to all image/* or video/*
     * types
     */
    private static final Set<String> supportedMimeTypes = new HashSet<>();
    /**
     * set of specific mimetypes to support as videos, in addition to any type
     * prefixed by video/
     */
    private static final Set<String> videoMimeTypes = new HashSet<>();
    /**
     * set of extensions to support as images. lowercase without the period.
     */
    private static final Set<String> imageExtensions = new HashSet<>();
    /**
     * set of extensions to support as videos. lowercase without the period.
     */
    private static final Set<String> videoExtensions = new HashSet<>();
    /**
     * set of all extensions that we should support(ie, include in db and show
     * to user). Initialized to be the concatenation of imageExtensions and
     * videoExtensions sets.
     */
    private static final Set<String> supportedExtensions;
    /**
     * Lazily instantiated FileTypeDetector to use when the mimetype of a file
     * is needed
     */
    private static FileTypeDetector FILE_TYPE_DETECTOR;

    /**
     * static initalizer block to initialize sets of extensions and mimetypes to
     * be supported
     */
    static {
        ImageIO.scanForPlugins();
        //add all extension ImageIO claims to support
        imageExtensions.addAll(Stream.of(ImageIO.getReaderFileSuffixes())
                .map(String::toLowerCase)
                .collect(Collectors.toList()));
        //add list of known image extensions
        imageExtensions.addAll(Arrays.asList(
                "bmp" //Bitmap
                , "gif" //gif
                , "jpg", "jpeg", "jpe", "jp2", "jpx" //jpeg variants
                , "pbm", "pgm", "ppm" // Portable image format variants
                , "png" //portable network graphic
                , "tga" //targa
                , "psd" //photoshop
                , "tif", "tiff" //tiff variants
                , "yuv", "ico" //icons
                , "ai" //illustrator
                , "svg" //scalable vector graphics
                , "sn", "ras" //sun raster
                , "ico" //windows icons
                , "tga" //targa
        ));

        //add list of known video extensions
        videoExtensions.addAll(Arrays.asList("fxm", "aaf", "3gp", "asf", "avi",
                "m1v", "m2v", "m4v", "mp4", "mov", "mpeg", "mpg", "mpe", "mp4",
                "rm", "wmv", "mpv", "flv", "swf"));

        supportedExtensions = Sets.union(imageExtensions, videoExtensions);

        //add list of mimetypes to count as videos even though they aren't prefixed by video/
        videoMimeTypes.addAll(Arrays.asList("application/x-shockwave-flash"));

        supportedMimeTypes.addAll(videoMimeTypes);
        supportedMimeTypes.addAll(Arrays.asList("application/x-123"));

        //add list of mimetypes ImageIO claims to support
        supportedMimeTypes.addAll(Stream.of(ImageIO.getReaderMIMETypes())
                .map(String::toLowerCase)
                .collect(Collectors.toList()));

        supportedMimeTypes.removeIf("application/octet-stream"::equals); //this is rearely usefull
    }

    /**
     *
     * @return
     */
    public static Set<String> getAllSupportedMimeTypes() {
        return Collections.unmodifiableSet(supportedMimeTypes);
    }

    /**
     *
     * @return
     */
    static Set<String> getAllSupportedExtensions() {
        return Collections.unmodifiableSet(supportedExtensions);
    }

    static synchronized FileTypeDetector getFileTypeDetector() {
        if (isNull(FILE_TYPE_DETECTOR)) {
            try {
                FILE_TYPE_DETECTOR = new FileTypeDetector();
            } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
                LOGGER.log(Level.SEVERE, "Failed to initialize File Type Detector, will fall back on extensions in some situations.", ex);
            }
        }
        return FILE_TYPE_DETECTOR;
    }

    /**
     * is the given file supported by image analyzer? ie, does it have a
     * supported mime type (image/*, or video/*). if no mime type is found, does
     * it have a supported extension or a jpeg/png header?
     *
     * @param file
     *
     * @return true if this file is supported or false if not
     */
    public static boolean isDrawable(AbstractFile file) throws TskCoreException {
        return hasDrawableMimeType(file).orElseGet(() -> {
            final boolean contains = FileTypeUtils.supportedExtensions.contains(file.getNameExtension());
            final boolean jpegFileHeader = ImageUtils.isJpegFileHeader(file);
            final boolean pngFileHeader = ImageUtils.isPngFileHeader(file);
            return contains
                    || jpegFileHeader
                    || pngFileHeader;
        });
    }

    public static boolean isGIF(AbstractFile file) {
        return ImageUtils.isGIF(file);
    }

    /**
     * does the given file have drawable/supported mime type
     *
     * @param file
     *
     * @return an Optional containg: True if the file has an image or video mime
     *         type. False if a non image/video mimetype. empty Optional if a
     *         mimetype could not be detected.
     */
    static Optional<Boolean> hasDrawableMimeType(AbstractFile file) throws TskCoreException {

        final FileTypeDetector fileTypeDetector = getFileTypeDetector();
        if (nonNull(fileTypeDetector)) {
            String mimeType = fileTypeDetector.getFileType(file);
            if (isNull(mimeType)) {
                return Optional.empty();
            } else {
                mimeType = mimeType.toLowerCase();
                return Optional.of(mimeType.startsWith("image/")
                        || mimeType.startsWith("video/")
                        || supportedMimeTypes.contains(mimeType));
            }
        }

        return Optional.empty();
    }

    /**
     * is the given file a video
     *
     * @param file
     *
     * @return true if the given file has a video mime type (video/*,
     *         application/x-shockwave-flash, etc) or, if no mimetype is
     *         available, a video extension.
     */
    public static boolean isVideoFile(AbstractFile file) {
        try {
            final FileTypeDetector fileTypeDetector = getFileTypeDetector();
            if (nonNull(fileTypeDetector)) {
                String mimeType = fileTypeDetector.getFileType(file);
                if (nonNull(mimeType)) {
                    mimeType = mimeType.toLowerCase();
                    return mimeType.startsWith("video/") || videoMimeTypes.contains(mimeType);
                }
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.INFO, "failed to get mime type for " + file.getName(), ex);
        }
        return FileTypeUtils.videoExtensions.contains(file.getNameExtension());
    }
}
