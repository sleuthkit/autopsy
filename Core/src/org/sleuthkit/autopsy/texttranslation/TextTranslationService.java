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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.openide.util.Lookup;

/**
 * Service for finding and running TextTranslator implementations
 */
public class TextTranslationService {

    private final static Optional<TextTranslator> translator;
    
    private static ExecutorService pool;
    private final static Integer MAX_POOL_SIZE = 10;

    static {
        //Perform look up for Text Translation implementations
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

    /**
     * Allows the translation task to happen asynchronously, promising to use
     * the TranslationCallback methods when the translation is complete.
     *
     * @param input
     * @param tcb
     */
    public void translateAsynchronously(String input, TranslationCallback tcb) {
        if(translator.isPresent()) {
            //Delay thread pool initialization until an asynchronous task is first called.
            //That way we don't have threads sitting parked in the background for no reason.
            if (pool == null) {
                ThreadFactory translationFactory = new ThreadFactoryBuilder()
                    .setNameFormat("translation-thread-%d")
                    .build();
                pool = Executors.newFixedThreadPool(MAX_POOL_SIZE, translationFactory);
            }
            
            pool.submit(() -> {
                try {
                    String translation = translate(input);
                    tcb.onTranslationResult(translation);
                } catch (NoServiceProviderException ex) {
                    tcb.onNoServiceProviderException(ex);
                } catch (TranslationException ex) {
                    tcb.onTranslationException(ex);
                }
            });
        } 
        
        tcb.onNoServiceProviderException(new NoServiceProviderException(
                "Could not find a TextTranslator service provider"));
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
