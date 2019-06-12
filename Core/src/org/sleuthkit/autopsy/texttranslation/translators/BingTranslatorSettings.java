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

import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Class to handle the settings associated with the BingTranslator
 */
public final class BingTranslatorSettings {

    private static final String AUTHENTICATION_KEY = "Credentials";
    private static final String BING_TRANSLATE_NAME = "BingTranslate";
    private static final String DEFAULT_AUTHENTICATION = "";
    private static final String DEFAULT_TARGET_LANGUAGE = "en";
    private static final String TARGET_LANGUAGE_CODE_KEY = "TargetLanguageCode";
    private String authenticationKey;
    private String targetLanguageCode;

    /**
     * Construct a new BingTranslatorSettings object
     */
    BingTranslatorSettings() {
        loadSettings();
    }

    /**
     * Get the Authentication key to be used for the Microsoft translation
     * service
     *
     * @return the Authentication key for the service
     */
    String getAuthenticationKey() {
        return authenticationKey;
    }

    /**
     * Set the Authentication key to be used for the Microsoft translation
     * service
     *
     * @param authKey the Authentication key for the service
     */
    void setAuthenticationKey(String authKey) {
        authenticationKey = authKey;
    }

    /**
     * Load the settings into memory from their on disk storage
     */
    void loadSettings() {
        if (!ModuleSettings.configExists(BING_TRANSLATE_NAME)) {
            ModuleSettings.makeConfigFile(BING_TRANSLATE_NAME);
        }
        if (ModuleSettings.settingExists(BING_TRANSLATE_NAME, AUTHENTICATION_KEY)) {
            authenticationKey = ModuleSettings.getConfigSetting(BING_TRANSLATE_NAME, AUTHENTICATION_KEY);
        }
        if (authenticationKey == null || StringUtils.isBlank(authenticationKey)) {
            authenticationKey = DEFAULT_AUTHENTICATION;
        }
        if (ModuleSettings.settingExists(BING_TRANSLATE_NAME, TARGET_LANGUAGE_CODE_KEY)) {
            targetLanguageCode = ModuleSettings.getConfigSetting(BING_TRANSLATE_NAME, TARGET_LANGUAGE_CODE_KEY);
        }
        if (targetLanguageCode == null || StringUtils.isBlank(targetLanguageCode)) {
            targetLanguageCode = DEFAULT_TARGET_LANGUAGE;
        }
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
     * Save the setting from memory to their location on disk
     */
    void saveSettings() {
        ModuleSettings.setConfigSetting(BING_TRANSLATE_NAME, AUTHENTICATION_KEY, authenticationKey);
        ModuleSettings.setConfigSetting(BING_TRANSLATE_NAME, TARGET_LANGUAGE_CODE_KEY, targetLanguageCode);
    }
}
