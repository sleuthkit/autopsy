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
 * abstract class for translating text and displaying to the user
 */
public abstract class TranslateTextTask extends SwingWorker<TranslateTextTask.Result, Void> {

    private static final Logger logger = Logger.getLogger(TranslatedTextViewer.class.getName());

    private final boolean translateText;
    private final String contentDescriptor;

    /**
     * as a result of running and processing the translation
     */
    public static class Result {

        private final String errorMessage;
        private final String result;
        private final boolean successful;

        public static Result error(String message) {
            return new Result(null, message, false);
        }

        public static Result success(String content) {
            return new Result(content, null, true);
        }

        private Result(String result, String errorMessage, boolean successful) {
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
     *
     * @param translateText whether or not to translate text
     * @param contentDescriptor the content descriptor for the item being
     * translated (used for logging errors)
     */
    public TranslateTextTask(boolean translateText, String fileDescriptor) {
        this.translateText = translateText;
        this.contentDescriptor = fileDescriptor;
    }

    protected abstract String retrieveText() throws IOException, InterruptedException, IllegalStateException;

    protected abstract void onTextDisplay(String text, ComponentOrientation orientation, int font);

    protected void onProgressDisplay(String text, ComponentOrientation orientation, int font) {
        // default to normal display unless overridden
        onTextDisplay(text, orientation, font);
    }

    protected void onErrorDisplay(String text, ComponentOrientation orientation, int font) {
        // default to normal display unless overridden
        onTextDisplay(text, orientation, font);
    }

    @NbBundle.Messages({
        "TranslatedContentViewer.translatingText=Translating text, please wait...",
        "TranslatedContentViewer.fileHasNoText=File has no text.",
        "TranslatedContentViewer.noServiceProvider=The machine translation software was not found.",
        "# {0} - exception message", "TranslatedContentViewer.translationException=An error occurred while translating the text ({0})."
    })
    @Override
    public Result doInBackground() throws InterruptedException {
        if (this.isCancelled()) {
            throw new InterruptedException();
        }

        String fileText;
        try {
            fileText = retrieveText();
        } catch (IOException | IllegalStateException ex) {
            return Result.error(ex.getMessage());
        }

        if (this.isCancelled()) {
            throw new InterruptedException();
        }

        if (fileText == null || fileText.isEmpty()) {
            return Result.error(Bundle.TranslatedContentViewer_fileHasNoText());
        }

        if (!this.translateText) {
            return Result.success(fileText);
        }

        SwingUtilities.invokeLater(() -> {
            onProgressDisplay(Bundle.TranslatedContentViewer_translatingText(), ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
        });

        try {
            String translation = translate(fileText);
            if (this.isCancelled()) {
                throw new InterruptedException();
            }

            if (translation == null || translation.isEmpty()) {
                return Result.error(Bundle.TranslatedContentViewer_emptyTranslation());
            } else {
                return Result.success(translation);
            }

        } catch (NoServiceProviderException ex) {
            logger.log(Level.WARNING, "Error translating text for file " + this.contentDescriptor, ex);
            return Result.error(Bundle.TranslatedContentViewer_noServiceProvider());
        } catch (TranslationException ex) {
            logger.log(Level.WARNING, "Error translating text for file " + this.contentDescriptor, ex);
            return Result.error(Bundle.TranslatedContentViewer_translationException(ex.getMessage()));
        }
    }

    @Override
    public void done() {
        try {
            Result executionResult = get();
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
     * Pass the translation off to the Translation service provider.
     *
     * @param input Text to be translated
     *
     * @return Translated text or error message
     */
    @NbBundle.Messages({
        "TranslatedContentViewer.emptyTranslation=The machine translation software did not return any text."
    })
    protected String translate(String input) throws NoServiceProviderException, TranslationException {
        TextTranslationService translatorInstance = TextTranslationService.getInstance();
        return translatorInstance.translate(input);
    }
}
