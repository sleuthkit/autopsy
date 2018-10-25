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

/**
 * This callback interface will be used by TextTranslationService when doing 
 * translations on background tasks. When the translation is done, it's result will
 * be delegated to one of the following methods. It can either be successful or fail with
 * exceptions.
 */
public interface TranslationCallback {
    
    /**
     * Provides a method to handle the translation result
     * 
     * @param translation result of calling TextTranslationService.
     */
    public void onTranslationResult(String translation);
    
    /**
     * Provides a way to handle Translation Exceptions.
     * 
     * @param ex 
     */
    public void onTranslationException(TranslationException ex);
    
    /**
     * Provides a way to handle NoServiceProviderExceptions.
     * 
     * @param ex 
     */
    public void onNoServiceProviderException(NoServiceProviderException ex);
}
