/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.texttranslation;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Performs a lookup for a TextTranslator service provider and if present, will
 * use this provider to run translation on the input.
 */
public final class TextTranslationService {

    private final static TextTranslationService tts = new TextTranslationService();

    private final Collection<? extends TextTranslator> translators;
    private Optional<TextTranslator> selectedTranslator;

    private TextTranslationService() {
        //Perform look up for Text Translation implementations ONLY ONCE during 
        //class loading.
        translators = Lookup.getDefault().lookupAll(TextTranslator.class);
        updateSelectedTranslator();
    }

    public static TextTranslationService getInstance() {
        return tts;
    }

    /**
     * Update the translator currently in use to match the one saved to the user
     * preferences
     */
    public void updateSelectedTranslator() {
        String translatorName = UserPreferences.getTextTranslatorName();
        for (TextTranslator translator : translators) {
            if (translator.getName().equals(translatorName)) {
                selectedTranslator = Optional.ofNullable(translator);
                return;
            }
        }
        selectedTranslator = Optional.empty();
    }

    /**
     * Translates the input string using whichever TextTranslator Service
     * Provider was found during lookup.
     *
     * @param input Input string to be translated
     *
     * @return Translation string
     *
     * @throws NoServiceProviderException Failed to find a Translation service
     *                                    provider
     * @throws TranslationException       System exception for classes to use
     *                                    when specific translation
     *                                    implementations fail
     */
    public String translate(String input) throws NoServiceProviderException, TranslationException {
        if (hasProvider()) {
            return selectedTranslator.get().translate(input);
        }
        throw new NoServiceProviderException(
                "Could not find a TextTranslator service provider");
    }

    /**
     * Get a specific translator by name
     *
     * @param translatorName the name of the translator to get
     *
     * @return the translator which matches the name specified
     *
     * @throws NoServiceProviderException
     */
    public TextTranslator getTranslatorByName(String translatorName) throws NoServiceProviderException {
        for (TextTranslator translator : translators) {
            if (translator.getName().equals(translatorName)) {
                return translator;
            }
        }
        throw new NoServiceProviderException(
                "Could not find the specified TextTranslator service provider: " + translatorName);
    }

    /**
     * Get all the TextTranslator implementations which were found to exist
     *
     * @return an unmodifiable collection of TextTranslators
     */
    public Collection<? extends TextTranslator> getTranslators() {
        return Collections.unmodifiableCollection(translators);
    }

    /**
     * Returns if a TextTranslator lookup successfully found an implementing
     * class.
     *
     * @return
     */
    public boolean hasProvider() {
        return selectedTranslator.isPresent();
    }
    
    /**
     * 
     * @return 
     */
    public int getMaxPayloadSize() {
        return selectedTranslator.get().getMaxPayloadSize();
    }
}
