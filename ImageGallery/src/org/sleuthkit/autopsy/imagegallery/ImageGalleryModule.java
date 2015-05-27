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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/** static definitions and utilities for the ImageGallery module */
public class ImageGalleryModule {

    private static final Logger LOGGER = Logger.getLogger(ImageGalleryModule.class.getName());

    private static final String MODULE_NAME = "Image Gallery";

    public static String getModuleName() {
        return MODULE_NAME;
    }

    private static final Set<String> videoExtensions
            = Sets.newHashSet("aaf", "3gp", "asf", "avi", "m1v", "m2v", "m4v", "mp4",
                    "mov", "mpeg", "mpg", "mpe", "mp4", "rm", "wmv", "mpv",
                    "flv", "swf");

    private static final Set<String> imageExtensions = Sets.newHashSet(ImageIO.getReaderFileSuffixes());

    private static final Set<String> supportedExtensions = Sets.union(imageExtensions, videoExtensions);

    /** mime types of images we can display */
    private static final Set<String> imageMimes = Sets.newHashSet("image/jpeg", "image/bmp", "image/gif", "image/png", "image/x-ms-bmp");
    /** mime types of videos we can display */
    private static final Set<String> videoMimes = Sets.newHashSet("video/mp4", "video/x-flv", "video/x-javafx");
    /** mime types of files we can display */
    private static final Set<String> supportedMimes = Sets.union(imageMimes, videoMimes);

    public static Set<String> getSupportedMimes() {
        return Collections.unmodifiableSet(supportedMimes);
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
            return StringUtils.isNotBlank(enabledforCaseProp) ? Boolean.valueOf(enabledforCaseProp) : ImageGalleryPreferences.isEnabledByDefault();
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
    public static boolean isCaseStale(Case c) {
        if (c != null) {
            String stale = new PerCaseProperties(c).getConfigSetting(ImageGalleryModule.MODULE_NAME, PerCaseProperties.STALE);
            return StringUtils.isNotBlank(stale) ? Boolean.valueOf(stale) : true;
        } else {
            return false;
        }
    }

    public static Set<String> getAllSupportedExtensions() {
        return supportedExtensions;
    }

    /** is the given file suported by image analyzer: ie, does it have a
     * supported mime type. if no mime type is found, does it have a supported
     * extension or a jpeg header.
     *
     * @param file
     *
     * @return true if this file is supported or false if not
     */
    public static Boolean isSupported(AbstractFile file) {
        //if there were no file type attributes, or we failed to read it, fall back on extension and jpeg header
        return Optional.ofNullable(hasSupportedMimeType(file)).orElseGet(() -> {
            return supportedExtensions.contains(getFileExtension(file))
                    || ImageUtils.isJpegFileHeader(file);
        });
    }

    /**
     *
     * @param file
     *
     * @return true if the file had a TSK_FILE_TYPE_SIG attribute on a
     *         TSK_GEN_INFO that is in the supported list. False if there was an
     *         unsupported attribute, null if no attributes were found
     */
    public static Boolean hasSupportedMimeType(AbstractFile file) {
        try {
            ArrayList<BlackboardAttribute> fileSignatureAttrs = file.getGenInfoAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            if (fileSignatureAttrs.isEmpty() == false) {
                return fileSignatureAttrs.stream().anyMatch(attr -> supportedMimes.contains(attr.getValueString()));
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.INFO, "failed to read TSK_FILE_TYPE_SIG attribute for " + file.getName(), ex);
        }
        return null;
    }

    /** @param file
     *
     * @return true if the given file has a supported video mime type or
     *         extension, else false
     */
    public static boolean isVideoFile(AbstractFile file) {
        try {
            ArrayList<BlackboardAttribute> fileSignatureAttrs = file.getGenInfoAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            if (fileSignatureAttrs.isEmpty() == false) {
                return fileSignatureAttrs.stream().anyMatch(attr -> videoMimes.contains(attr.getValueString()));
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.INFO, "failed to read TSK_FILE_TYPE_SIG attribute for " + file.getName(), ex);
        }
        //if there were no file type attributes, or we failed to read it, fall back on extension
        return videoExtensions.contains(getFileExtension(file));
    }

    private static String getFileExtension(AbstractFile file) {
        return Iterables.getLast(Arrays.asList(StringUtils.split(file.getName(), '.')), "");
    }

    /**
     * Is the given file 'supported' and not 'known'(nsrl hash hit). If so we
     * should include it in {@link DrawableDB} and UI
     *
     * @param abstractFile
     *
     * @return true if the given {@link AbstractFile} is 'supported' and not
     *         'known', else false
     */
    static public boolean isSupportedAndNotKnown(AbstractFile abstractFile) {
        return (abstractFile.getKnown() != TskData.FileKnown.KNOWN) && ImageGalleryModule.isSupported(abstractFile);
    }
}
