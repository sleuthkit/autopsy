/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.texttranslation.translators;

import com.google.cloud.translate.TranslateOptions;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 *
 * @author wschaefer
 */
public final class GoogleTranslatorSettings {

    private static final String DEFAULT_TARGET_LANGUAGE = TranslateOptions.getDefaultInstance().getTargetLanguage();
    private static final String CREDENTIAL_PATH_KEY = "CredentialPath";
    private static final String TARGET_LANGUAGE_CODE_KEY = "TargetLanguageCode";
    private static final String GOOGLE_TRANSLATE_NAME = "GoogleTranslate";
    private static final String DEFAULT_CREDENTIAL_PATH = "";
    private String targetLanguageCode;
    private String credentialPath;

    GoogleTranslatorSettings() {
        loadSettings();
    }

    String getTargetLanguageCode() {
        return targetLanguageCode;
    }

    String getCredentialPath() {
        return credentialPath;
    }

    void setCredentialPath(String path) {
        credentialPath = path;
    }

    void setTargetLanguageCode(String code) {
        if (StringUtils.isBlank(code)) {
            targetLanguageCode = DEFAULT_TARGET_LANGUAGE;
        } else {
            targetLanguageCode = code;
        }
    }

    void loadSettings() {
        if (!ModuleSettings.configExists(GOOGLE_TRANSLATE_NAME)) {
            ModuleSettings.makeConfigFile(GOOGLE_TRANSLATE_NAME);
        }
        if (ModuleSettings.settingExists(GOOGLE_TRANSLATE_NAME, TARGET_LANGUAGE_CODE_KEY)) {
            targetLanguageCode = ModuleSettings.getConfigSetting(GOOGLE_TRANSLATE_NAME, TARGET_LANGUAGE_CODE_KEY);
        } else {
            targetLanguageCode = DEFAULT_TARGET_LANGUAGE;
        }
        if (ModuleSettings.settingExists(GOOGLE_TRANSLATE_NAME, CREDENTIAL_PATH_KEY)) {
            credentialPath = ModuleSettings.getConfigSetting(GOOGLE_TRANSLATE_NAME, CREDENTIAL_PATH_KEY);
        } else {
            credentialPath = DEFAULT_CREDENTIAL_PATH;
        }
    }

    void saveSettings() {
        ModuleSettings.setConfigSetting(GOOGLE_TRANSLATE_NAME, TARGET_LANGUAGE_CODE_KEY, targetLanguageCode);
        ModuleSettings.setConfigSetting(GOOGLE_TRANSLATE_NAME, CREDENTIAL_PATH_KEY, credentialPath);
    }
}
