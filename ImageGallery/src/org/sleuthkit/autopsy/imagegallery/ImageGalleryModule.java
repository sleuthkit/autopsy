/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/** static definitions and utilities for the ImageGallery module */
public class ImageGalleryModule {

    private static final Logger LOGGER = Logger.getLogger(ImageGalleryModule.class.getName());

    private static final String MODULE_NAME = "Image Gallery";

    static String getModuleName() {
        return MODULE_NAME;
    }

    private static final Set<String> videoExtensions
            = Sets.newHashSet("aaf", "3gp", "asf", "avi", "m1v", "m2v", "m4v", "mp4",
                    "mov", "mpeg", "mpg", "mpe", "mp4", "rm", "wmv", "mpv",
                    "flv", "swf");

    private static final Set<String> imageExtensions = Sets.newHashSet(ImageIO.getReaderFileSuffixes());

    private static final Set<String> supportedExtensions = Sets.union(imageExtensions, videoExtensions);

    private static FileTypeDetector FILE_TYPE_DETECTOR;

    private static synchronized FileTypeDetector getFileTypeDetector() {
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
     * get the Path to the Case's ImageGallery ModuleOutput subfolder; ie
     * ".../[CaseName]/ModuleOutput/Image Gallery/"
     *
     * @param theCase the case to get the ImageGallery ModuleOutput subfolder
     *                for
     *
     * @return the Path to the ModuleOuput subfolder for Image Gallery
     */
    static Path getModuleOutputDir(Case theCase) {
        return Paths.get(theCase.getModulesOutputDirAbsPath(), getModuleName());
    }

    /** provides static utilities, can not be instantiated */
    private ImageGalleryModule() {
    }

    /** is listening enabled for the given case
     *
     * @param c
     *
     * @return true if listening is enabled for the given case, false otherwise
     */
    static boolean isEnabledforCase(Case c) {
        if (c != null) {
            String enabledforCaseProp = new PerCaseProperties(c).getConfigSetting(ImageGalleryModule.MODULE_NAME, PerCaseProperties.ENABLED);
            return isNotBlank(enabledforCaseProp) ? Boolean.valueOf(enabledforCaseProp) : ImageGalleryPreferences.isEnabledByDefault();
        } else {
            return false;
        }
    }

    /** is the drawable db out of date for the given case
     *
     * @param c
     *
     * @return true if the drawable db is out of date for the given case, false
     *         otherwise
     */
    public static boolean isDrawableDBStale(Case c) {
        if (c != null) {
            String stale = new PerCaseProperties(c).getConfigSetting(ImageGalleryModule.MODULE_NAME, PerCaseProperties.STALE);
            return StringUtils.isNotBlank(stale) ? Boolean.valueOf(stale) : true;
        } else {
            return false;
        }
    }

    static Set<String> getAllSupportedExtensions() {
        return Collections.unmodifiableSet(supportedExtensions);
    }

    /** is the given file suported by image analyzer: ie, does it have a
     * supported mime type (image/*, or video/*). if no mime type is found, does
     * it have a supported extension or a jpeg/png header.
     *
     * @param file
     *
     * @return true if this file is supported or false if not
     */
    static Boolean isDrawable(AbstractFile file) {
        //if there were no file type attributes,  fall back on extension and jpeg header
        return Optional.ofNullable(hasDrawableMimeType(file)).orElseGet(() -> {
            return supportedExtensions.contains(file.getNameExtension())
                    || ImageUtils.isJpegFileHeader(file) || ImageUtils.isPngFileHeader(file);
        });
    }

    /**
     *
     * @param file
     *
     * @return true if the file has an image or video mime type.
     *         false if a non image/video mimetype.
     *         null if a mimetype could not be detected.
     */
    static Boolean hasDrawableMimeType(AbstractFile file) {
        try {
            final FileTypeDetector fileTypeDetector = getFileTypeDetector();
            if (nonNull(fileTypeDetector)) {
                String mimeType = fileTypeDetector.getFileType(file);
                return isNull(mimeType) ? null
                        : mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.equalsIgnoreCase("application/x-shockwave-flash");
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.INFO, "failed to get mime type for " + file.getName(), ex);
        }
        return null;
    }

    /** @param file
     *
     * @return true if the given file has a video mime type (video/* or
     *         application/x-shockwave-flash) or if no mimetype is available a video
     *         extension
     */
    public static boolean isVideoFile(AbstractFile file) {
        try {
            final FileTypeDetector fileTypeDetector = getFileTypeDetector();
            if (nonNull(fileTypeDetector)) {
                String mimeType = fileTypeDetector.getFileType(file);
                if (nonNull(mimeType)) {
                    return mimeType.startsWith("video/") || mimeType.equalsIgnoreCase("application/x-shockwave-flash");
                }
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.INFO, "failed to get mime type for " + file.getName(), ex);

        }
        return videoExtensions.contains(file.getNameExtension());
    }

    /**
     * Is the given file 'supported' and not 'known'(nsrl hash hit). If so we
     * should include it in {@link DrawableDB} and UI
     *
     * @param abstractFile
     *
     * @return true if the given {@link AbstractFile} is "drawable" and not
     *         'known', else false
     */
    public static boolean isDrawableAndNotKnown(AbstractFile abstractFile) {
        return (abstractFile.getKnown() != TskData.FileKnown.KNOWN) && ImageGalleryModule.isDrawable(abstractFile);
    }
}
