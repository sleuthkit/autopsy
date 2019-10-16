/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Detects the language of the given contents. Only languages which should be indexed to a corresponding
 * language-specific field are detected.
 */
class LanguageDetector {

    private com.optimaize.langdetect.LanguageDetector impl;
    private TextObjectFactory textObjectFactory;

    LanguageDetector() {
        try {
            impl = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(new LanguageProfileReader().readAllBuiltIn())
                .build();
            textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
        } catch (IOException e) {
            // The IOException here could occur when failing to read the language profiles from the classpath.
            // That can be considered to be a severe IO problem. Nothing can be done here.
            throw new UncheckedIOException(e);
        }
    }

    Optional<Language> detect(String text) {
        TextObject textObject = textObjectFactory.forText(text);
        Optional<LdLocale> localeOpt = impl.detect(textObject).transform(Optional::of).or(Optional.empty());
        return localeOpt.map(LdLocale::getLanguage).flatMap(Language::fromValue);
    }
}
