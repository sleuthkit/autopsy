/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-16 Basis Technology Corp.
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
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Enum style singleton to provide utilities related to questions about a files
 * type, and whether it should be supported in Image Gallery.
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
     * Mimetypes that we disable because they create too many false positives (
     * ie files that are not images to show up in Image Gallery)
     */
    private static final Set<String> disabledMimeTypes = new HashSet<>();
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
                // remove any empty extension types provided by ImageIO.getReaderFileSuffixes()
                // This prevents extensions added by SPI implementations from causing errors 
                // (i.e. 'jai-imageio' utilized with IcePDF)
                .filter((extension) -> StringUtils.isNotBlank(extension))        
                .map(String::toLowerCase)
                .collect(Collectors.toList()));
        //add list of known image extensions
        imageExtensions.addAll(Arrays.asList(
                "bmp", //Bitmap NON-NLS
                "gif", //gif NON-NLS
                "jpg", "jpeg", "jpe", "jp2", "jpx", //jpeg variants NON-NLS
                "pbm", "pgm", "ppm",// Portable image format variants NON-NLS
                "png", //portable network graphic NON-NLS
                "tga", //targa NON-NLS
                "psd", //photoshop NON-NLS
                "tif", "tiff", //tiff variants NON-NLS
                "yuv", "ico", //icons NON-NLS
                "ai", //illustrator NON-NLS
                "svg", //scalable vector graphics NON-NLS
                "sn", "ras", //sun raster NON-NLS
                "ico", //windows icons NON-NLS
                "tga", //targa NON-NLS
                "wmf", "emf", // windows meta file NON-NLS
                "wmz", "emz" //compressed windows meta file NON-NLS
        ));

        //add list of known video extensions
        videoExtensions.addAll(Arrays.asList("fxm", "aaf", "3gp", "asf", "avi", //NON-NLS
                "m1v", "m2v", "m4v", "mp4", "mov", "mpeg", "mpg", "mpe", "mp4", //NON-NLS
                "rm", "wmv", "mpv", "flv", "swf")); //NON-NLS

        supportedExtensions = Sets.union(imageExtensions, videoExtensions);

        //add list of mimetypes to count as videos even though they aren't prefixed by video/
        videoMimeTypes.addAll(Arrays.asList("application/x-shockwave-flash")); //NON-NLS

        supportedMimeTypes.addAll(videoMimeTypes);

        /*
         * TODO: windows .cur cursor files get misidentified as
         * application/x-123, so we claim to support application/x-123 so we
         * don't miss them: ie this is a hack to cover another bug. when this is
         * fixed, we should remove application/x-123 from the list of supported
         * mime types.
         */
        supportedMimeTypes.addAll(Arrays.asList("application/x-123")); //NON-NLS
        supportedMimeTypes.addAll(Arrays.asList("application/x-wmf")); //NON-NLS

        /*
         * We could support application/x-emf, but many files get mis-identified
         * as it and so supporting it causes many false positive( ie files that
         * are not images) to show up in Image Gallery.
         * supportedMimeTypes.addAll(Arrays.asList("application/x-emf"));
         */
         //add list of mimetypes ImageIO claims to support
        supportedMimeTypes.addAll(Stream.of(ImageIO.getReaderMIMETypes())
                // remove any empty mime types provided by ImageIO.getReaderMIMETypes()
                // This prevents mime types added by SPI implementations from causing errors 
                // (i.e. 'jai-imageio' utilized with IcePDF)
                .filter((mimeType) -> StringUtils.isNotBlank(mimeType))
                .map(String::toLowerCase)
                .collect(Collectors.toList()));

        /**
         * Many non image files get misidentified as image/vnd.microsoft.icon
         * and application/x-emf, and show up in Image Gallery as 'false
         * positives'.
         */
        disabledMimeTypes.addAll(Arrays.asList("application/octet-stream",//this is rarely usefull //NON-NLS 
                "image/vnd.microsoft.icon",
                "application/x-emf"));

        supportedMimeTypes.removeAll(disabledMimeTypes);

    }

    public static Set<String> getAllSupportedMimeTypes() {
        return Collections.unmodifiableSet(supportedMimeTypes);
    }

    static Set<String> getAllSupportedExtensions() {
        return Collections.unmodifiableSet(supportedExtensions);
    }

    static synchronized FileTypeDetector getFileTypeDetector() throws FileTypeDetector.FileTypeDetectorInitException {
        /*
         * TODO: EUR-740 recreate FileTypeDetector when the user creates new
         * user defined file types
         */
        if (isNull(FILE_TYPE_DETECTOR)) {
            FILE_TYPE_DETECTOR = new FileTypeDetector();
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
    public static boolean isDrawable(AbstractFile file) throws FileTypeDetector.FileTypeDetectorInitException {
        return hasDrawableMIMEType(file);
    }

    static boolean isDrawableMimeType(String mimeType) {
        if (StringUtils.isBlank(mimeType)) {
            return false;
        }
        String mimeTypeLower = mimeType.toLowerCase();
        return (disabledMimeTypes.contains(mimeTypeLower) == false)
                && (mimeTypeLower.startsWith("image/")
                || mimeTypeLower.startsWith("video/")
                || supportedMimeTypes.contains(mimeTypeLower));

    }

    /**
     *
     * TODO: EUR-740 recreate FileTypeDetector when the user creates new user
     * defined file types
     *
     * does the given file have drawable/supported mime type
     *
     * @param file
     *
     * @return an Optional containg: True if the file has an image or video mime
     *         type. False if a non image/video mimetype. empty Optional if a
     *         mimetype could not be detected.
     */
    static boolean hasDrawableMIMEType(AbstractFile file) {
        String mimeType = file.getMIMEType();
        if (mimeType == null) {
            return false;
        }
        
        mimeType = mimeType.toLowerCase();
        return isDrawableMimeType(mimeType) || (mimeType.equals("audio/x-aiff") && "tiff".equalsIgnoreCase(file.getNameExtension()));
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
    public static boolean hasVideoMIMEType(AbstractFile file) {
        String mimeType = file.getMIMEType();       
        if (mimeType == null) {
            return false;
        }
        
        mimeType = mimeType.toLowerCase();
        return mimeType.startsWith("video/") || videoMimeTypes.contains(mimeType);
    }

    /**
     * Get the unique path for the content, or if that fails, just return the
     * name.
     *
     * @param content
     *
     * @return
     */
    static String getContentPathSafe(Content content) {
        try {
            return content.getUniquePath();
        } catch (TskCoreException tskCoreException) {
            String contentName = content.getName();
            LOGGER.log(Level.SEVERE, "Failed to get unique path for " + contentName, tskCoreException); //NOI18N NON-NLS
            return contentName;
        }
    }
}
