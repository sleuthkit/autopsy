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

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.lang3.StringUtils;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/** static definitions and utilities for the ImageGallery module */
@NbBundle.Messages({"ImageGalleryModule.moduleName=Image Gallery"})
public class ImageGalleryModule {

    private static final Logger LOGGER = Logger.getLogger(ImageGalleryModule.class.getName());

    private static final String MODULE_NAME = Bundle.ImageGalleryModule_moduleName();

    static String getModuleName() {
        return MODULE_NAME;
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
        return Paths.get(theCase.getModuleDirectory(), getModuleName());
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
            
            return ( ImageGalleryController.getDefault().isDataSourcesTableStale() || 
                     (StringUtils.isNotBlank(stale) ? Boolean.valueOf(stale) : true) );
        } else {
            return false;
        }
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
    public static boolean isDrawableAndNotKnown(AbstractFile abstractFile) throws TskCoreException, FileTypeDetector.FileTypeDetectorInitException {
        return (abstractFile.getKnown() != TskData.FileKnown.KNOWN) && FileTypeUtils.isDrawable(abstractFile);
    }
}
