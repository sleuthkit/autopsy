/*
 * Autopsy
 *
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.texttranslation.translators;

import java.io.File;
import java.util.Map;
import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Installer for text translators to run at startup.
 */
public class Installer extends ModuleInstall {

    private static final long serialVersionUID = 1L;
    private static Installer instance;

    /**
     * Gets the singleton "package installer" used by the registered Installer
     * for the Autopsy-Core module located in the org.sleuthkit.autopsy.core
     * package.
     *
     * @return The "package installer" singleton for the
     *         org.sleuthkit.autopsy.centralrepository.eventlisteners package.
     */
    public synchronized static Installer getDefault() {
        if (instance == null) {
            instance = new Installer();
        }
        return instance;
    }

    /**
     * Constructs the singleton "package installer" used by the registered
     * Installer for the Autopsy-Core module located in the
     * org.sleuthkit.autopsy.core package.
     */
    private Installer() {
        super();
    }

    @Override
    public void restored() {
        // update path for translators.
        copyIfExists(BingTranslatorSettings.BING_TRANSLATE_SIMPLE_NAME, BingTranslatorSettings.BING_TRANSLATE_NAME);
        copyIfExists(GoogleTranslatorSettings.GOOGLE_TRANSLATE_SIMPLE_NAME, GoogleTranslatorSettings.GOOGLE_TRANSLATE_NAME);
    }

    private void copyIfExists(String oldModuleSettingsPath, String newModuleSettingsPath) {
        if (new File(ModuleSettings.getSettingsFilePath(oldModuleSettingsPath)).exists()) {
            Map<String, String> settings = ModuleSettings.getConfigSettings(oldModuleSettingsPath);
            if (!settings.isEmpty()) {
                ModuleSettings.setConfigSettings(newModuleSettingsPath, settings);    
            }
        }
    }

}
