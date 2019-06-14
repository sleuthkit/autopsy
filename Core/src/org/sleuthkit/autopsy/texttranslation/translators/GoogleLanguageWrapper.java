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
 * Wrapper for the Language class
 */
class GoogleLanguageWrapper {

    private final Language language;

    /**
     * Create a new GoogleLanguageWrapper
     *
     * @param lang the Language object to wrap
     */
    GoogleLanguageWrapper(Language lang) {
        language = lang;
    }

    /**
     * Get the wrapped Language object
     *
     * @return the wrapped Language
     */
    Language getLanguage() {
        return language;
    }

    @Override
    public String toString() {
        //toString overridden so that the jComboBox in the GoogleTranslatorSettingsPanel will display the name of the language
        return language.getName();
    }

}
