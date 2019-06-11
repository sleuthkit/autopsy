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

import com.google.cloud.translate.Language;

/**
 * Wrapper for Language definitions used by translators
 */
class LanguageWrapper {

    private final String languageCode;
    private final String languageDisplayName;

    /**
     * Create a new LanguageWrapper to wrap the google language object
     *
     * @param lang the Language object to wrap
     */
    LanguageWrapper(Language language) {
        languageCode = language.getCode();
        languageDisplayName = language.getName();
    }

    /**
     * Create a new LanguageWrapper to wrap json elements that identify a
     * language for microsofts translation service
     *
     * @param code the code which uniquely identifies a language
     * @param name the name of the language
     */
    LanguageWrapper(String code, String name) {
        languageCode = code;
        languageDisplayName = name;
    }

    /**
     * Get the wrapped Language object
     *
     * @return the wrapped Language
     */
    String getLanguageCode() {
        return languageCode;
    }

    @Override
    public String toString() {
        //toString overridden so that the jComboBox in the TranslatorSettingsPanels will display the name of the language
        return languageDisplayName;
    }

}
