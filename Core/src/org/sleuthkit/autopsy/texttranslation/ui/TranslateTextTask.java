/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.texttranslation.ui;

import java.awt.ComponentOrientation;
import java.awt.Font;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TextUtil;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;

/**
* abstract class for translating text and displaying to the user
*/
public abstract class TranslateTextTask extends SwingWorker<String, Void> {
   private static final Logger logger = Logger.getLogger(TranslatedTextViewer.class.getName());

   private final boolean translateText;
   private final String contentDescriptor;
   

   /**
    * 
    * @param translateText      whether or not to translate text
    * @param contentDescriptor     the content descriptor for the item being translated (used for logging errors)
    */
    public TranslateTextTask(boolean translateText, String fileDescriptor) {
        this.translateText = translateText;
        this.contentDescriptor = fileDescriptor;
    }

   
    protected abstract String retrieveText() throws Exception;
    
    protected abstract void onTextDisplay(String text, ComponentOrientation orientation, int font);

   @NbBundle.Messages({
       "TranslatedContentViewer.extractingText=Extracting text, please wait...",
       "TranslatedContentViewer.translatingText=Translating text, please wait...",
       "# {0} - exception message", "TranslatedContentViewer.errorExtractingText=An error occurred while extracting the text ({0}).",
       "TranslatedContentViewer.fileHasNoText=File has no text.",
       "TranslatedContentViewer.noServiceProvider=The machine translation software was not found.",
       "# {0} - exception message", "TranslatedContentViewer.translationException=An error occurred while translating the text ({0})."
   })
   @Override
   public String doInBackground() throws InterruptedException {
       if (this.isCancelled()) {
           throw new InterruptedException();
       }

       SwingUtilities.invokeLater(() -> {
           onTextDisplay(Bundle.TranslatedContentViewer_extractingText(), ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
       });
       String fileText;
       try {
           fileText = retrieveText();
       } catch (InterruptedException | CancellationException e) {
           // bubble up cancellation instead of continuing
           throw e;
       } catch (Exception ex) {
           logger.log(Level.WARNING, "Error extracting text for file " + this.contentDescriptor, ex);
           return Bundle.TranslatedContentViewer_errorExtractingText(ex.getMessage());
       }

       if (this.isCancelled()) {
           throw new InterruptedException();
       }

       if (fileText == null || fileText.isEmpty()) {
           return Bundle.TranslatedContentViewer_fileHasNoText();
       }

       if (!this.translateText) {
           return fileText;
       }

       SwingUtilities.invokeLater(() -> {
           onTextDisplay(Bundle.TranslatedContentViewer_translatingText(), ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
       });
       String translation;
       try {
           translation = translate(fileText);
       } catch (NoServiceProviderException ex) {
           logger.log(Level.WARNING, "Error translating text for file " + this.contentDescriptor, ex);
           translation = Bundle.TranslatedContentViewer_noServiceProvider();
       } catch (TranslationException ex) {
           logger.log(Level.WARNING, "Error translating text for file " + this.contentDescriptor, ex);
           translation = Bundle.TranslatedContentViewer_translationException(ex.getMessage());
       }

       if (this.isCancelled()) {
           throw new InterruptedException();
       }

       return translation;
   }

   @Override
   public void done() {
       try {
           String result = get();
           if (this.isCancelled()) {
               throw new InterruptedException();
           }
           int len = result.length();
           int maxOrientChars = Math.min(len, 1024);
           String orientDetectSubstring = result.substring(0, maxOrientChars);
           ComponentOrientation orientation = TextUtil.getTextDirection(orientDetectSubstring);
           onTextDisplay(result, orientation, Font.PLAIN);

       } catch (InterruptedException | CancellationException ignored) {
           // Task cancelled, no error.
       } catch (ExecutionException ex) {
           logger.log(Level.WARNING, "Error occurred during background task execution for file " + this.contentDescriptor, ex);
           onTextDisplay(Bundle.TranslatedContentViewer_translationException(ex.getMessage()), ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
       }
   }

   /**
    * Pass the translation off to the Translation service provider.
    *
    * @param input Text to be translated
    *
    * @return Translated text or error message
    */
   @NbBundle.Messages({
       "TranslatedContentViewer.emptyTranslation=The machine translation software did not return any text."
   })
   private String translate(String input) throws NoServiceProviderException, TranslationException {
       TextTranslationService translatorInstance = TextTranslationService.getInstance();
       String translatedResult = translatorInstance.translate(input);
       if (translatedResult.isEmpty()) {
           return Bundle.TranslatedContentViewer_emptyTranslation();
       }
       return translatedResult;
   }
}
