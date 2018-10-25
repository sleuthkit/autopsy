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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.openide.util.Lookup;

/**
 * Service for finding and running TextTranslator implementations
 */
public class TextTranslationService {
    
    private final Optional<TextTranslator> translator;
    private final static ExecutorService pool;
    private final static Integer MAX_POOL_SIZE = 10;
    
    static {
        pool = Executors.newFixedThreadPool(MAX_POOL_SIZE);   
    }
       
    /**
     * 
     */
    public TextTranslationService() {
        translator = Optional.ofNullable(Lookup.getDefault()
                .lookup(TextTranslator.class));
    }

    /**
     * Performs a lookup for a TextTranslator service provider and if present,
     * will use this provider to run translation on the input.
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
    
    public void translate(String input, TranslationCallback tcb) {
        pool.submit(() -> {
            try {
                String translation = translate(input);
                tcb.onTranslation(translation);
            } catch (NoServiceProviderException ex) {
                tcb.onNoServiceProviderException(ex);
            } catch (TranslationException ex) {
                tcb.onTranslationException(ex);
            }
        });
    }
    
    /**
     * 
     * @return 
     */
    public boolean hasProvider() {
        return translator.isPresent();
    }
}