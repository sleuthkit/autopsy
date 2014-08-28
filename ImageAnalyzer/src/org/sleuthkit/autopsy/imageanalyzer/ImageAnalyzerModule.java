/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-14 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer;

import org.sleuthkit.autopsy.coreutils.ThreadConfined;
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
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableDB;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/** static definitions and utilities for the Eureka Module
 *
 */
public class ImageAnalyzerModule {

    static private final Logger LOGGER = Logger.getLogger(ImageAnalyzerModule.class.getName());

    static final String MODULE_NAME = ImageAnalyzerModule.class.getSimpleName();

    static private final Set<String> videoExtensions
            = Sets.newHashSet("aaf", "3gp", "asf", "avi", "m1v", "m2v", "m4v", "mp4",
                              "mov", "mpeg", "mpg", "mpe", "mp4", "rm", "wmv", "mpv",
                              "flv", "swf");

    static private final Set<String> imageExtensions = Sets.newHashSet(ImageIO.getReaderFileSuffixes());

    static private final Set<String> supportedExtensions = Sets.union(imageExtensions, videoExtensions);

    static private final Set<String> imageMimes = Sets.newHashSet("image/jpeg", "image/bmp", "image/gif", "image/png");

    static private final Set<String> videoMimes = Sets.newHashSet("video/mp4", "video/x-flv", "video/x-javafx");

    static private final Set<String> supportedMimes = Sets.union(imageMimes, videoMimes);

    public static Set<String> getSupportedMimes() {
        return Collections.unmodifiableSet(supportedMimes);
    }

    private ImageAnalyzerModule() {
    }

    static boolean isEnabledforCase(Case c) {
        if (c != null) {
            String enabledforCaseProp = new PerCaseProperties(c).getConfigSetting(ImageAnalyzerModule.MODULE_NAME, PerCaseProperties.ENABLED);
            return StringUtils.isNotBlank(enabledforCaseProp) ? Boolean.valueOf(enabledforCaseProp) : ImageAnalyzerPreferences.isEnabledByDefault();
        } else {
            return false;
        }
    }

    public static boolean isCaseStale(Case c) {
        if (c != null) {
            String stale = new PerCaseProperties(c).getConfigSetting(ImageAnalyzerModule.MODULE_NAME, PerCaseProperties.STALE);
            return StringUtils.isNotBlank(stale) ? Boolean.valueOf(stale) : false;
        } else {
            return false;
        }
    }

    public static Set<String> getAllSupportedExtensions() {
        return supportedExtensions;
    }

    public static Boolean isSupported(AbstractFile file) {
        //if there were no file type attributes, or we failed to read it, fall back on extension and jpeg header
        return Optional.ofNullable(hasSupportedMimeType(file)).orElseGet(
                () -> supportedExtensions.contains(getFileExtension(file)) || ImageUtils.isJpegFileHeader(file));
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
     * @return
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
        return (abstractFile.getKnown() != TskData.FileKnown.KNOWN) && ImageAnalyzerModule.isSupported(abstractFile);
    }

    //TODO: this doesn ot really belong here, move it to EurekaController? Module?
    @ThreadConfined(type = ThreadConfined.ThreadType.UI)
    public static void closeTopComponent() {
        final TopComponent etc = WindowManager.getDefault().findTopComponent("EurekaTopComponent");
        if (etc != null) {
            try {
                etc.close();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "failed to close EurekaTopComponent", e);
            }
        }
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.UI)
    public static void openTopComponent() {
        //TODO:eventually move to this model, throwing away everything and rebuilding controller groupmanager etc for each case.
//        synchronized (OpenTimelineAction.class) {
//            if (timeLineController == null) {
//                timeLineController = new TimeLineController();
//                LOGGER.log(Level.WARNING, "Failed to get TimeLineController from lookup. Instantiating one directly.S");
//            }
//        }
//        timeLineController.openTimeLine();
        final ImageAnalyzerTopComponent EurekaTc = (ImageAnalyzerTopComponent) WindowManager.getDefault().findTopComponent("EurekaTopComponent");
        if (EurekaTc != null) {
            WindowManager.getDefault().isTopComponentFloating(EurekaTc);
            Mode mode = WindowManager.getDefault().findMode("timeline");
            if (mode != null) {
                mode.dockInto(EurekaTc);

            }
            EurekaTc.open();
            EurekaTc.requestActive();
        }
    }
}
