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
import java.io.IOException;
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
 * This is an abstract class for translating text and displaying to the user.
 */
public abstract class TranslateTextTask extends SwingWorker<TranslateTextTask.TranslateResult, Void> {

    private static final Logger logger = Logger.getLogger(TranslatedTextViewer.class.getName());

    private final boolean translateText;
    private final String contentDescriptor;

    /**
     * This is a result of running and processing the translation.
     */
    public static class TranslateResult {

        private final String errorMessage;
        private final String result;
        private final boolean successful;

        public static TranslateResult error(String message) {
            return new TranslateResult(null, message, false);
        }

        public static TranslateResult success(String content) {
            return new TranslateResult(content, null, true);
        }

        private TranslateResult(String result, String errorMessage, boolean successful) {
            this.successful = successful;
            this.errorMessage = errorMessage;
            this.result = result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getResult() {
            return result;
        }

        public boolean isSuccessful() {
            return successful;
        }

    }

    /**
     * This is the main constructor for the TranslateTextTask.
     * @param translateText whether or not to translate text
     * @param fileDescriptor the content descriptor for the item being
     * translated (used for logging errors)
     */
    public TranslateTextTask(boolean translateText, String fileDescriptor) {
        this.translateText = translateText;
        this.contentDescriptor = fileDescriptor;
    }

    /**
     * This method retrieves the original text content to be translated.
     * @return      the original text content
     * @throws IOException
     * @throws InterruptedException
     * @throws IllegalStateException 
     */
    protected abstract String retrieveText() throws IOException, InterruptedException, IllegalStateException;

    /**
     * This method should be overridden when a translated text result is received.
     * @param text          the text to display
     * @param orientation   the orientation of the text
     * @param font          the font style (returns plain)
     */
    protected abstract void onTextDisplay(String text, ComponentOrientation orientation, int font);

    /**
     * When a progress result is received, this method is called.
     * This method can be overridden depending on the scenario, but defaults to just displaying using onTextDisplay.
     * @param text          the text of the status update
     * @param orientation   the orientation for the status
     * @param font          the font style of the status
     */
    protected void onProgressDisplay(String text, ComponentOrientation orientation, int font) {
        // This defaults to normal display unless overridden.
        onTextDisplay(text, orientation, font);
    }

    /**
     * When an error result is received, this method is called. This method can be overridden depending on the 
     * scenario but defaults to just displaying using onTextDisplay.
     * @param text          the text of the error
     * @param orientation   the orientation for the error
     * @param font          the font style of the error
     */
    protected void onErrorDisplay(String text, ComponentOrientation orientation, int font) {
        // This defaults to normal display unless overridden.
        onTextDisplay(text, orientation, font);
    }

    @NbBundle.Messages({
        "TranslatedContentViewer.translatingText=Translating text, please wait...",
        "TranslatedContentViewer.fileHasNoText=File has no text.",
        "TranslatedContentViewer.noServiceProvider=The machine translation software was not found.",
        "# {0} - exception message", "TranslatedContentViewer.translationException=An error occurred while translating the text ({0})."
    })
    @Override
    public TranslateResult doInBackground() throws InterruptedException {
        if (this.isCancelled()) {
            throw new InterruptedException();
        }

        String fileText;
        try {
            fileText = retrieveText();
        } catch (IOException | IllegalStateException ex) {
            return TranslateResult.error(ex.getMessage());
        }

        if (this.isCancelled()) {
            throw new InterruptedException();
        }

        if (fileText == null || fileText.isEmpty()) {
            return TranslateResult.error(Bundle.TranslatedContentViewer_fileHasNoText());
        }

        if (!this.translateText) {
            return TranslateResult.success(fileText);
        }

        return translateRetrievedText(fileText);
    }

    /**
     * This is the final step in the translation swing worker prior to being {@link #done() done()}; translates the text if needed.
     * @param fileText          the text to translate
     * @return                  the translated text
     * @throws InterruptedException     if operation is canclled, an interrupted exception is thrown
     */
    private TranslateResult translateRetrievedText(String fileText) throws InterruptedException {
        SwingUtilities.invokeLater(() -> {
            onProgressDisplay(Bundle.TranslatedContentViewer_translatingText(), ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
        });

        try {
            String translation = translate(fileText);
            if (this.isCancelled()) {
                throw new InterruptedException();
            }

            if (translation == null || translation.isEmpty()) {
                return TranslateResult.error(Bundle.TranslatedContentViewer_emptyTranslation());
            } else {
                return TranslateResult.success(translation);
            }

        } catch (NoServiceProviderException ex) {
            logger.log(Level.WARNING, "Error translating text for file " + this.contentDescriptor, ex);
            return TranslateResult.error(Bundle.TranslatedContentViewer_noServiceProvider());
        } catch (TranslationException ex) {
            logger.log(Level.WARNING, "Error translating text for file " + this.contentDescriptor, ex);
            return TranslateResult.error(Bundle.TranslatedContentViewer_translationException(ex.getMessage()));
        }
    }
    

    @Override
    public void done() {
        try {
            TranslateResult executionResult = get();
            if (this.isCancelled()) {
                throw new InterruptedException();
            }

            if (executionResult.isSuccessful()) {
                String result = executionResult.getResult();
                int len = result.length();
                int maxOrientChars = Math.min(len, 1024);
                String orientDetectSubstring = result.substring(0, maxOrientChars);
                ComponentOrientation orientation = TextUtil.getTextDirection(orientDetectSubstring);
                onTextDisplay(result, orientation, Font.PLAIN);
            } else {
                onErrorDisplay(executionResult.getErrorMessage(), ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
            }
        } catch (InterruptedException | CancellationException ignored) {
            // Task cancelled, no error.
        } catch (ExecutionException ex) {
            logger.log(Level.WARNING, "Error occurred during background task execution for file " + this.contentDescriptor, ex);
            onErrorDisplay(Bundle.TranslatedContentViewer_translationException(ex.getMessage()), ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
        }
    }

    /**
     * This method passes the translation off to the {@link org.sleuthkit.autopsy.texttranslation.TextTranslationService translation service provider}.
     *
     * @param input text to be translated
     *
     * @return translated text or error message
     */
    @NbBundle.Messages({
        "TranslatedContentViewer.emptyTranslation=The machine translation software did not return any text."
    })
    protected String translate(String input) throws NoServiceProviderException, TranslationException {
        TextTranslationService translatorInstance = TextTranslationService.getInstance();
        return translatorInstance.translate(input);
    }
}
