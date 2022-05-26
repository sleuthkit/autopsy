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

import java.nio.file.Paths;
import org.sleuthkit.autopsy.core.configpath.SharedConfigPath;

/**
 * ModuleSettings keys and paths for translator settings.
 */
class TranslatorSettings {

    private static final String TRANSLATION_FOLDER = "Translation";
    private static final String TRANSLATION_PATH = Paths.get(SharedConfigPath.getInstance().getSharedConfigPath(), TRANSLATION_FOLDER).toString();

    private static TranslatorSettings instance = new TranslatorSettings();

    /**
     * @return The singular instance.
     */
    static TranslatorSettings getInstance() {
        return instance;
    }

    private TranslatorSettings() {
    }

    /**
     * Returns the resource execution context to be used with ModuleSettings.
     *
     * @param translationResource The name of the resource to be used with
     *                            ModuleSettings.
     *
     * @return The resource name to use with ModuleSettings.
     */
    String getModuleSettingsResource(String translationResource) {
        return Paths.get(SharedConfigPath.getInstance().getSharedConfigFolder(), TRANSLATION_FOLDER, translationResource).toString();
    }

    /**
     * @return The base translation folder.
     */
    String getBaseTranslationFolder() {
        return TRANSLATION_PATH;
    }
}
