/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
import javax.annotation.Nonnull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * This class is reponsible for keeping track of module state for the image
 * gallery.
 */
@NbBundle.Messages({"ImageGalleryModule.moduleName=Image Gallery"})
public final class ImageGalleryModule {

    private static final String MODULE_NAME = Bundle.ImageGalleryModule_moduleName();

    /**
     * Gets the image gallery module name.
     *
     * @return The module name.
     */
    static String getModuleName() {
        return MODULE_NAME;
    }

    /**
     * Gets the path to the image gallery module output folder for a given case.
     *
     * @param theCase The case.
     *
     * @return The path to the image gallery module output folder for the case.
     */
    public static Path getModuleOutputDir(Case theCase) {
        return Paths.get(theCase.getModuleDirectory(), MODULE_NAME);
    }

    /**
     * Indicates whether or not the image gallery module is enabled for a given
     * case.
     *
     * @param theCase The case.
     *
     * @return True or false.
     */
    static boolean isEnabledforCase(@Nonnull Case theCase) {
        PerCaseProperties properties = new PerCaseProperties(theCase);
        String enabled = properties.getConfigSetting(ImageGalleryModule.MODULE_NAME, PerCaseProperties.ENABLED);
        return isNotBlank(enabled) ? Boolean.valueOf(enabled) : ImageGalleryPreferences.isEnabledByDefault();
    }

    /**
     * Prevents instantiation.
     */
    private ImageGalleryModule() {
    }

}
