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

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import java.awt.Component;
import java.util.logging.Logger;
import javax.swing.JLabel;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.texttranslation.TextTranslator;
import org.sleuthkit.autopsy.texttranslation.TranslationException;

@ServiceProvider(service = TextTranslator.class)
public class GoogleTranslator implements TextTranslator {

    private static final Logger logger = Logger.getLogger(GoogleTranslator.class.getName());
    private static final int MAX_STRING_LENGTH = 15000;
    private final Translate translate;

    public GoogleTranslator() {
        // Instantiates a client
        translate = TranslateOptions.getDefaultInstance().getService();
    }

    @Override
    public String translate(String string) throws TranslationException {
        try {
            // Translates some text into English, without specifying the source language.

            // HTML files were producing lots of white space at the end
            String substring = string.trim();

            // WE can't currently set parameters, so we are using the default behavior of
            // asuming the input is HTML. We need to replace newlines with <br> for Google to preserve them
            substring = substring.replaceAll("(\r\n|\n)", "<br />");

            // The API complains if the "Payload" is over 204800 bytes. I'm assuming that 
            // deals with the full request.  At some point, we get different errors about too
            // much text.  Officially, Google says they will translate only 5k chars,
            // but we have seen more than that working.
            // there could be a value betwen 15k and 25k that works.  I (BC) didn't test further
            if (substring.length() > MAX_STRING_LENGTH) {
                substring = substring.substring(0, MAX_STRING_LENGTH);
            }
            Translation translation
                    = translate.translate(substring);
            String translatedString = translation.getTranslatedText();

            // put back the newlines
            translatedString = translatedString.replaceAll("<br />", "\n");
            return translatedString;
        } catch (Throwable e) {
            throw new TranslationException(e.getMessage());
        }
    }

    @Messages({"GoogleTranslator.name.text=Google Translate"})
    @Override
    public String getName() {
        return Bundle.GoogleTranslator_name_text();
    }

    
    @Override
    public Component getComponent() {
        return new JLabel("There are no settings to configure for Google Translate");
    }

    @Override
    public void saveSettings() {
        //There are no settings to configure for Google Translate
        //Possible settings for the future:
        //source language, target language, API key, path to JSON file of API key.
        //We'll need test code to make sure that exceptions are thrown in all of
        //those scenarios.
    }
}
