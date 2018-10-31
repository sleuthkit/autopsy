/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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

import java.util.Optional;

import org.openide.util.Lookup;

/**
 * Performs a lookup for a TextTranslator service provider and if present,
 * will use this provider to run translation on the input.
 */
public final class TextTranslationService {
    
    private final static TextTranslationService tts = new TextTranslationService();

    private final Optional<TextTranslator> translator;
    
    private TextTranslationService(){
        //Perform look up for Text Translation implementations ONLY ONCE during 
        //class loading.
        translator = Optional.ofNullable(Lookup.getDefault()
                .lookup(TextTranslator.class));
    }
    
    public static TextTranslationService getInstance() {
        return tts;
    }

    /**
     * Translates the input string using whichever TextTranslator Service Provider 
     * was found during lookup.
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
        if (translator.isPresent()) {
            return translator.get().translate(input);
        }
        throw new NoServiceProviderException(
                "Could not find a TextTranslator service provider");
    }

    /**
     * Returns if a TextTranslator lookup successfully found an implementing
     * class.
     *
     * @return 
     */
    public boolean hasProvider() {
        return translator.isPresent();
    }
}
