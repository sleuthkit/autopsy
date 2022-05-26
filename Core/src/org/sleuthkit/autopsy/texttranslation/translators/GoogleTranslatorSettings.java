/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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

import com.google.cloud.translate.TranslateOptions;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Class to handle the settings associated with the GoogleTranslator
 */
public final class GoogleTranslatorSettings {

    private static final String DEFAULT_TARGET_LANGUAGE = TranslateOptions.getDefaultInstance().getTargetLanguage();
    private static final String CREDENTIAL_PATH_KEY = "CredentialPath";
    private static final String TARGET_LANGUAGE_CODE_KEY = "TargetLanguageCode";
    private static final String GOOGLE_TRANSLATE_NAME = "GoogleTranslate";
    private static final String DEFAULT_CREDENTIAL_PATH = "";
    private String targetLanguageCode;
    private String credentialPath;

    /**
     * Construct a new GoogleTranslatorSettingsObject
     */
    GoogleTranslatorSettings() {
        loadSettings();
    }

    /**
     * Get the target language code
     *
     * @return the code used to identify the target language
     */
    String getTargetLanguageCode() {
        return targetLanguageCode;
    }

    /**
     * Get the path to the JSON credentials file
     *
     * @return the path to the credentials file
     */
    String getCredentialPath() {
        return credentialPath;
    }

    /**
     * Set the path to the JSON credentials file
     *
     * @param path the path to the credentials file
     */
    void setCredentialPath(String path) {
        credentialPath = path;
    }

    /**
     * Set the target language code. If a blank code is specified it sets the
     * default code instead.
     *
     * @param code the target language code to set
     */
    void setTargetLanguageCode(String code) {
        if (StringUtils.isBlank(code)) {
            targetLanguageCode = DEFAULT_TARGET_LANGUAGE;
        } else {
            targetLanguageCode = code;
        }
    }

    /**
     * Load the settings into memory from their on disk storage
     */
    void loadSettings() {
        if (!ModuleSettings.configExists(GOOGLE_TRANSLATE_NAME)) {
            ModuleSettings.makeConfigFile(GOOGLE_TRANSLATE_NAME);
        }
        if (ModuleSettings.settingExists(GOOGLE_TRANSLATE_NAME, TARGET_LANGUAGE_CODE_KEY)) {
            targetLanguageCode = ModuleSettings.getConfigSetting(GOOGLE_TRANSLATE_NAME, TARGET_LANGUAGE_CODE_KEY);
        }
        if (targetLanguageCode == null || StringUtils.isBlank(targetLanguageCode)) {
            targetLanguageCode = DEFAULT_TARGET_LANGUAGE;
        }
        if (ModuleSettings.settingExists(GOOGLE_TRANSLATE_NAME, CREDENTIAL_PATH_KEY)) {
            credentialPath = ModuleSettings.getConfigSetting(GOOGLE_TRANSLATE_NAME, CREDENTIAL_PATH_KEY);
        }
        if (credentialPath == null) {
            credentialPath = DEFAULT_CREDENTIAL_PATH;
        }
    }

    /**
     * Save the setting from memory to their location on disk
     */
    void saveSettings() {
        ModuleSettings.setConfigSetting(GOOGLE_TRANSLATE_NAME, TARGET_LANGUAGE_CODE_KEY, targetLanguageCode);
        ModuleSettings.setConfigSetting(GOOGLE_TRANSLATE_NAME, CREDENTIAL_PATH_KEY, credentialPath);
    }
}
