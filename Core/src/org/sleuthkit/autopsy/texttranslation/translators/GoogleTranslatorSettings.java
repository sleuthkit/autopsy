/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.texttranslation.translators;

import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author wschaefer
 */
public class GoogleTranslatorSettings {

    private static final Logger logger = Logger.getLogger(GoogleTranslatorSettings.class.getName());
    private Translate translate = null;
    
    Translate getTranslator(){
        return translate;
    }
    
    void loadTranslator(String credentialPath, String targetLanguage) {
        InputStream credentialStream = null;
        Credentials creds = null;
        try {
            credentialStream = new FileInputStream(credentialPath);
        } catch (FileNotFoundException ex) {
            logger.log(Level.WARNING, "JSON file for GoogleTranslator credentials not found", ex);
        }
        if (credentialStream != null) {
            try {
                creds = ServiceAccountCredentials.fromStream(credentialStream);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error converting JSON file to Credentials object for GoogleTranslator", ex);
            }
        }
        if (creds == null) {
            logger.log(Level.INFO, "Credentials were not successfully made, no translations will be available from the GoogleTranslator");
            translate = null;
        } else {
            TranslateOptions.Builder builder = TranslateOptions.newBuilder();
            builder.setCredentials(creds);
            builder.setTargetLanguage(targetLanguage);
            translate = builder.build().getService();
        }
    }
}
