/*
 * Autopsy Forensic Browser
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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.IOException;
import javax.swing.JPanel;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.texttranslation.TextTranslator;
import org.sleuthkit.autopsy.texttranslation.TranslationConfigException;
import org.sleuthkit.autopsy.texttranslation.TranslationException;

/**
 * Translates text by making HTTP requests to Bing Translator. This requires a
 * valid subscription key for a Microsoft Azure account.
 */
@ServiceProvider(service = TextTranslator.class)
public class BingTranslator implements TextTranslator {

    //The target language follows the to= in the string below. You can include multiple target
    //languages separated by commas. A full list of supported languages is here:
    //https://docs.microsoft.com/en-us/azure/cognitive-services/translator/language-support
    private static final String BASE_URL = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=";
    private static final int MAX_STRING_LENGTH = 5000;
    private BingTranslatorSettingsPanel settingsPanel;
    private final BingTranslatorSettings settings = new BingTranslatorSettings();
    // This sends messages to Microsoft.
    private final OkHttpClient CLIENT = new OkHttpClient();

    /**
     * Create a Bing Translator
     */
    public BingTranslator() {
        
    }

    /**
     * Get the tranlation url for the specified language code
     *
     *
     *
     * @param languageCode language code for language to translate to
     *
     * @return a string representation of the url to request translation from
     */
    static String getTranlatorUrl(String languageCode) {
        return BASE_URL + languageCode;
    }

    /**
     * Converts an input text to the JSON format required by Bing Translator,
     * posts it to Microsoft, and returns the JSON text response.
     *
     * @param string The input text to be translated.
     *
     * @return The translation response as a JSON string
     *
     * @throws IOException if the request could not be executed due to
     *                     cancellation, a connectivity problem or timeout.
     */
    public String postTranslationRequest(String string) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");

        JsonArray jsonArray = new JsonArray();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("Text", string);
        jsonArray.add(jsonObject);
        String bodyString = jsonArray.toString();

        RequestBody body = RequestBody.create(mediaType,
                bodyString);
        Request request = new Request.Builder()
                .url(getTranlatorUrl(settings.getTargetLanguageCode())).post(body)
                .addHeader("Ocp-Apim-Subscription-Key", settings.getAuthenticationKey())
                .addHeader("Content-type", "application/json").build();
        Response response = CLIENT.newCall(request).execute();
        return response.body().string();
    }

    @Override
    public String translate(String string) throws TranslationException {
        if (settings.getAuthenticationKey() == null || settings.getAuthenticationKey().isEmpty()) {
            throw new TranslationException("Bing Translator has not been configured, authentication key needs to be specified");
        }
        String toTranslate = string.trim();
        //Translates some text into English, without specifying the source langauge.

        //Google Translate required us to replace (\r\n|\n) with <br /> 
        //but Bing Translator doesn not have that requirement.
        //The free account has a maximum file size. If you have a paid account,
        //you probably still want to limit file size to prevent accidentally
        //translating very large documents.
        if (toTranslate.length() > MAX_STRING_LENGTH) {
            toTranslate = toTranslate.substring(0, MAX_STRING_LENGTH);
        }

        try {
            String response = postTranslationRequest(toTranslate);
            return parseJSONResponse(response);
        } catch (IOException | TranslationException ex) {
            throw new TranslationException("Exception while attempting to translate using BingTranslator", ex);
        }
    }

    @Messages({"BingTranslator.name.text=Bing Translator"})
    @Override
    public String getName() {
        return Bundle.BingTranslator_name_text();
    }

    @Override
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public JPanel getSettingsPanel() {
        if(settingsPanel == null) {
            settingsPanel = new BingTranslatorSettingsPanel(settings.getAuthenticationKey(), settings.getTargetLanguageCode());
        }
        return settingsPanel;
    }

    @Override
    public void saveSettings() throws TranslationConfigException {
        settings.setAuthenticationKey(settingsPanel.getAuthenticationKey());
        settings.setTargetLanguageCode(settingsPanel.getTargetLanguageCode());
        settings.saveSettings();
    }

    /**
     * Parse the response to get the translated text
     *
     * @param json_text the json which was received as a response to a
     *                  translation request
     *
     * @return the translated text
     *
     * @throws TranslationException
     */
    private String parseJSONResponse(String json_text) throws TranslationException {
        /*
         * Here is an example of the text we get from Bing when input is "gato",
         * the Spanish word for cat: [ { "detectedLanguage": { "language": "es",
         * "score": 1.0 }, "translations": [ { "text": "cat", "to": "en" } ] } ]
         */
        try {
            JsonArray responses = JsonParser.parseString(json_text).getAsJsonArray();
            //As far as I know, there's always exactly one item in the array.
            JsonObject response0 = responses.get(0).getAsJsonObject();
            JsonArray translations = response0.getAsJsonArray("translations");
            JsonObject translation0 = translations.get(0).getAsJsonObject();
            return translation0.get("text").getAsString();
        } catch (IllegalStateException | ClassCastException | NullPointerException | IndexOutOfBoundsException e) {
            throw new TranslationException("JSON text does not match Bing Translator scheme: " + e);
        }
    }

    @Override
    public int getMaxTextChars() {
        return MAX_STRING_LENGTH;
    }
}
