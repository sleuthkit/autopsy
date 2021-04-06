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

import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.texttranslation.TextTranslator;
import org.sleuthkit.autopsy.texttranslation.TranslationConfigException;
import org.sleuthkit.autopsy.texttranslation.TranslationException;

/**
 * TextTranslator which utilizes Google Cloud Translation to perform translation
 * in Autopsy
 */
@ServiceProvider(service = TextTranslator.class)
public final class GoogleTranslator implements TextTranslator {

    private static final Logger logger = Logger.getLogger(GoogleTranslator.class.getName());
    //See translate method for justification of this limit.
    private static final int MAX_PAYLOAD_SIZE = 5000;
    private GoogleTranslatorSettingsPanel settingsPanel;
    private final GoogleTranslatorSettings settings = new GoogleTranslatorSettings();
    private Translate googleTranslate;

    /**
     * Constructs a new GoogleTranslator
     */
    public GoogleTranslator() {
        // Instantiates a client
        loadTranslator();
    }

    /**
     * Check if google is able to be reached
     *
     * @return true if it can be, false otherwise
     */
    private static boolean googleIsReachable() {
        String host = "www.google.com";
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
            return address.isReachable(1500);
        } catch (UnknownHostException ex) {
            logger.log(Level.WARNING, "Unable to reach google.com due to unknown host", ex);
            return false;
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to reach google.com due IOException", ex);
            return false;
        }
    }

    @Override
    public String translate(String string) throws TranslationException {
        if (!googleIsReachable()) {
            throw new TranslationException("Failure translating using GoogleTranslator: Cannot connect to Google");
        }

        if (googleTranslate != null) {
            try {
                // Translates some text into English, without specifying the source language.
                String substring = string.trim();

                // We can't currently set parameters, so we are using the default behavior of 
                // assuming the input is HTML. We need to replace newlines with <br> for Google to preserve them
                substring = substring.replaceAll("(\r\n|\n)", "<br />");

                // The API complains if the "Payload" is over 204800 bytes. Google references that
                //their service is optimized for 2K code points and recommends keeping the requests that size. 
                //There is a hard limit of 30K code points per request. There is also a time-based quota that 
                //we are not enforcing, which may lead to 403 errors. We are currently configured for a max of 5K 
                //in each request, for two reasons. 1) To be more in line with Google's recommendation. 2) To 
                //minimize accidental exceedence of time based quotas.
                if (substring.length() > MAX_PAYLOAD_SIZE) {
                    substring = substring.substring(0, MAX_PAYLOAD_SIZE);
                }
                Translation translation
                        = googleTranslate.translate(substring);
                String translatedString = translation.getTranslatedText();

                // put back the newlines
                translatedString = translatedString.replaceAll("<br />", "\n");

                // With our current settings, Google Translate outputs HTML
                // so we need to undo the escape characters.
                translatedString = EscapeUtil.unEscapeHtml(translatedString);
                return translatedString;
            } catch (Throwable ex) {
                //Catching throwables because some of this Google Translate code throws throwables
                throw new TranslationException("Failure translating using GoogleTranslator", ex);
            }
        } else {
            throw new TranslationException("Google Translator has not been configured, credentials need to be specified");
        }
    }

    @Messages({"GoogleTranslator.name.text=Google Translate"})
    @Override
    public String getName() {
        return Bundle.GoogleTranslator_name_text();
    }

    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public JPanel getSettingsPanel() {
        if(settingsPanel == null) {
            settingsPanel = new GoogleTranslatorSettingsPanel(settings.getCredentialPath(), settings.getTargetLanguageCode());
        }
        return settingsPanel;
    }

    /**
     * Load the Google Cloud Translation service given the currently saved
     * settings
     */
    private void loadTranslator() {
        InputStream credentialStream = null;
        Credentials creds = null;
        if (StringUtils.isBlank(settings.getCredentialPath())) {
            logger.log(Level.INFO, "No credentials file has been provided for Google Translator");
        } else {
            try {
                credentialStream = new FileInputStream(settings.getCredentialPath());
            } catch (FileNotFoundException ex) {
                logger.log(Level.WARNING, "JSON file for GoogleTranslator credentials not found", ex);
            }
        }
        if (credentialStream != null) {
            try {
                creds = ServiceAccountCredentials.fromStream(credentialStream);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error converting JSON file to Credentials object for GoogleTranslator", ex);
            }
        }
        if (creds == null) {
            logger.log(Level.WARNING, "Credentials were not successfully made, no translations will be available from the GoogleTranslator");
            googleTranslate = null;
        } else {
            TranslateOptions.Builder builder = TranslateOptions.newBuilder();
            builder.setCredentials(creds);
            builder.setTargetLanguage(settings.getTargetLanguageCode());
            googleTranslate = builder.build().getService();
        }
    }

    @Override
    public void saveSettings() throws TranslationConfigException {
        settings.setTargetLanguageCode(settingsPanel.getTargetLanguageCode());
        settings.setCredentialPath(settingsPanel.getCredentialsPath());
        settings.saveSettings();
        loadTranslator();
    }

    @Override
    public int getMaxTextChars() {
        return MAX_PAYLOAD_SIZE;
    }
}
