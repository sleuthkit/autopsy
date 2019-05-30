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
import java.awt.Component;
import java.io.IOException;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.texttranslation.TextTranslator;
import org.sleuthkit.autopsy.texttranslation.TranslationException;

/**
 * Translates text by making HTTP requests to Bing Translator.
 * This requires a valid subscription key for a Microsoft Azure account.
 */
@ServiceProvider(service = TextTranslator.class)
public class BingTranslator implements TextTranslator{
    //In the String below, "en" is the target language. You can include multiple target
    //languages separated by commas. A full list of supported languages is here:
    //https://docs.microsoft.com/en-us/azure/cognitive-services/translator/language-support
    private static final String BASE_URL = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=";
    private final BingTranslatorSettingsPanel settingsPanel;
    private final BingTranslatorSettings settings = new BingTranslatorSettings();
    

    // This sends messages to Microsoft.
    private final OkHttpClient CLIENT = new OkHttpClient();
    
    //We might want to make this a configurable setting for anyone who has a 
    //paid account that's willing to pay for long documents.
    private final int MAX_STRING_LENGTH = 5000;
    
    
    public BingTranslator(){
        settingsPanel = new BingTranslatorSettingsPanel(settings.getCredentials(), settings.getTargetLanguageCode());
    }
    
    static String getTranlatorUrl(String languageCode){
        return BASE_URL + languageCode;
    }
    
    /**
     * Converts an input test to the JSON format required by Bing Translator,
     * posts it to Microsoft, and returns the JSON text response.
     * 
     * @param string The input text to be translated.
     * @return The translation response as a JSON string
     * @throws IOException if the request could not be executed due to cancellation, a connectivity problem or timeout.
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
            .addHeader("Ocp-Apim-Subscription-Key", settings.getCredentials())
            .addHeader("Content-type", "application/json").build();
        Response response = CLIENT.newCall(request).execute();
        return response.body().string();
    }

    @Override
    public String translate(String string) throws TranslationException {
        if (settings.getCredentials() == null || settings.getCredentials().isEmpty()) {
            throw new TranslationException("Bing Translator has not been configured, credentials need to be specified");
        }
        String toTranslate = string.trim();
        //Translates some text into English, without specifying the source langauge.
        
        // HTML files were producing lots of white space at the end
        
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
        } catch (Throwable e) {
            throw new TranslationException(e.getMessage()); 
        }
    }
    
    @Messages({"BingTranslator.name.text=Bing Translator"})
    @Override
    public String getName() {
        return Bundle.BingTranslator_name_text();
    }

    @Override
    public Component getComponent() {
        return settingsPanel;
    }

    @Override
    public void saveSettings() {
        settings.setCredentials(settingsPanel.getCredentials());
        settings.setTargetLanguageCode(settingsPanel.getTargetLanguageCode());
    }

    private String parseJSONResponse(String json_text) throws TranslationException {
        /* Here is an example of the text we get from Bing when input is "gato",
           the Spanish word for cat:
            [
              {
                "detectedLanguage": {
                  "language": "es",
                  "score": 1.0
                },
                "translations": [
                  {
                    "text": "cat",
                    "to": "en"
                  }
                ]
              }
            ]       
        */
        JsonParser parser = new JsonParser();
        try {
            JsonArray responses = parser.parse(json_text).getAsJsonArray();
            //As far as I know, there's always exactly one item in the array.
            JsonObject response0 = responses.get(0).getAsJsonObject();
            JsonArray translations = response0.getAsJsonArray("translations");
            JsonObject translation0 = translations.get(0).getAsJsonObject();
            String text = translation0.get("text").getAsString();
            return text;
        } catch (IllegalStateException | ClassCastException | NullPointerException | IndexOutOfBoundsException e) {
            throw new TranslationException("JSON text does not match Bing Translator scheme: " + e);
        }
    }
}