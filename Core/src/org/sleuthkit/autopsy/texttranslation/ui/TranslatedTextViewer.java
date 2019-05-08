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
package org.sleuthkit.autopsy.texttranslation.ui;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.TextViewer;
import org.sleuthkit.datamodel.AbstractFile;
import javax.swing.SwingWorker;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.corecomponents.DataContentViewerUtility;
import org.sleuthkit.autopsy.coreutils.ExecUtil.ProcessTerminator;
import org.sleuthkit.autopsy.coreutils.TextUtil;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.autopsy.textextractors.configs.ImageConfig;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import org.sleuthkit.datamodel.Content;
import java.util.List;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.texttranslation.ui.TranslationContentPanel.DisplayDropdownOptions;

/**
 * A TextViewer that displays machine translation of text.
 */
@ServiceProvider(service = TextViewer.class, position = 4)
public final class TranslatedTextViewer implements TextViewer {

    private static final boolean OCR_ENABLED = true;
    private static final boolean OCR_DISABLED = false;
    private static final int MAX_SIZE_1MB = 1024000;
    private static final List<String> INSTALLED_LANGUAGE_PACKS = PlatformUtil.getOcrLanguagePacks();
    private final TranslationContentPanel panel = new TranslationContentPanel();

    private volatile Node node;
    private volatile BackgroundTranslationTask updateTask;
    private final ThreadFactory translationThreadFactory
            = new ThreadFactoryBuilder().setNameFormat("translation-content-viewer-%d").build();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(translationThreadFactory);

    @Override
    public void setNode(final Node node) {
        this.node = node;
        SelectionChangeListener displayDropDownListener = new DisplayDropDownChangeListener();
        panel.addDisplayTextActionListener(displayDropDownListener);
        panel.addOcrDropDownActionListener(new OCRDropdownChangeListener());
        Content source = DataContentViewerUtility.getDefaultContent(node);

        if (source instanceof AbstractFile) {
            boolean isImage = ((AbstractFile) source).getMIMEType().toLowerCase().startsWith("image/");
            if (isImage) {
                panel.enableOCRSelection(OCR_ENABLED);
                panel.addLanguagePackNames(INSTALLED_LANGUAGE_PACKS);
            }
        }

        //Force a background task.
        displayDropDownListener.actionPerformed(null);
    }

    @NbBundle.Messages({"TranslatedTextViewer.title=Translation"})
    @Override
    public String getTitle() {
        return Bundle.TranslatedTextViewer_title();
    }

    @NbBundle.Messages({"TranslatedTextViewer.toolTip=Displays translated file text."})
    @Override
    public String getToolTip() {
        return Bundle.TranslatedTextViewer_toolTip();
    }

    @Override
    public TextViewer createInstance() {
        return new TranslatedTextViewer();
    }

    @Override
    public Component getComponent() {
        return panel;
    }

    @Override
    public void resetComponent() {
        panel.reset();
        this.node = null;
        if (updateTask != null) {
            updateTask.cancel(true);
        }
        updateTask = null;
    }

    @Override
    public boolean isSupported(Node node) {
        if (null == node) {
            return false;
        }

        if (!TextTranslationService.getInstance().hasProvider()) {
            return false;
        }

        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        return file != null;
    }

    @Override
    public int isPreferred(Node node) {
        // Returning zero makes it unlikely this object will be the preferred content viewer, 
        // i.e., the active tab, when the content viewers are first displayed.
        return 0;
    }

    /**
     * Fetches file text and performs translation.
     */
    private class BackgroundTranslationTask extends SwingWorker<String, Void> {

        @NbBundle.Messages({
            "TranslatedContentViewer.noIndexedTextMsg=Run the Keyword Search Ingest Module to get text for translation.",
            "TranslatedContentViewer.textAlreadyIndexed=Please view the original text in the Indexed Text viewer.",
            "TranslatedContentViewer.errorMsg=Error encountered while getting file text.",
            "TranslatedContentViewer.errorExtractingText=Could not extract text from file.",
            "TranslatedContentViewer.translatingText=Translating text, please wait..."
        })
        @Override
        public String doInBackground() throws InterruptedException {
            if (this.isCancelled()) {
                throw new InterruptedException();
            }
            String dropdownSelection = panel.getDisplayDropDownSelection();

            if (dropdownSelection.equals(DisplayDropdownOptions.ORIGINAL_TEXT.toString())) {
                try {
                    return getFileText(node);
                } catch (IOException ex) {
                    return Bundle.TranslatedContentViewer_errorMsg();
                } catch (TextExtractor.InitReaderException ex) {
                    return Bundle.TranslatedContentViewer_errorExtractingText();
                }
            } else {
                try {
                    return translate(getFileText(node));
                } catch (IOException ex) {
                    return Bundle.TranslatedContentViewer_errorMsg();
                } catch (TextExtractor.InitReaderException ex) {
                    return Bundle.TranslatedContentViewer_errorExtractingText();
                }
            }
        }

        /**
         * Update the extraction loading message depending on the file type.
         *
         * @param isImage Boolean indicating if the selecting node is an image
         */
        @NbBundle.Messages({"TranslatedContentViewer.extractingImageText=Extracting text from image, please wait...",
            "TranslatedContentViewer.extractingFileText=Extracting text from file, please wait...",})
        private void updateExtractionLoadingMessage(boolean isImage) {
            if (isImage) {
                panel.display(Bundle.TranslatedContentViewer_extractingImageText(),
                        ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
            } else {
                panel.display(Bundle.TranslatedContentViewer_extractingFileText(),
                        ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);
            }
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
                panel.display(result, orientation, Font.PLAIN);
            } catch (InterruptedException | ExecutionException | CancellationException ignored) {
                //InterruptedException & CancellationException - User cancelled, no error.
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
            "TranslatedContentViewer.emptyTranslation=The resulting translation was empty.",
            "TranslatedContentViewer.noServiceProvider=Machine Translation software was not found.",
            "TranslatedContentViewer.translationException=Error encountered while attempting translation."})
        private String translate(String input) throws InterruptedException {
            if (this.isCancelled()) {
                throw new InterruptedException();
            }

            panel.display(Bundle.TranslatedContentViewer_translatingText(),
                    ComponentOrientation.LEFT_TO_RIGHT, Font.ITALIC);

            try {
                TextTranslationService translatorInstance = TextTranslationService.getInstance();
                String translatedResult = translatorInstance.translate(input);
                if (translatedResult.isEmpty()) {
                    return Bundle.TranslatedContentViewer_emptyTranslation();
                }
                return translatedResult;
            } catch (NoServiceProviderException ex) {
                return Bundle.TranslatedContentViewer_noServiceProvider();
            } catch (TranslationException ex) {
                return Bundle.TranslatedContentViewer_translationException();
            }
        }

        /**
         * Extracts text from the given node
         *
         * @param node Selected node in UI
         *
         * @return Extracted text
         *
         * @throws IOException
         * @throws InterruptedException
         * @throws
         * org.sleuthkit.autopsy.textextractors.TextExtractor.InitReaderException
         * @throws NoOpenCoreException
         * @throws KeywordSearchModuleException
         */
        private String getFileText(Node node) throws IOException,
                InterruptedException, TextExtractor.InitReaderException {

            AbstractFile source = (AbstractFile) DataContentViewerUtility.getDefaultContent(node);
            boolean isImage = false;

            if (source != null) {
                isImage = source.getMIMEType().toLowerCase().startsWith("image/");
            }

            updateExtractionLoadingMessage(isImage);

            String result;

            if (isImage) {
                result = extractText(source, OCR_ENABLED);
            } else {
                result = extractText(source, OCR_DISABLED);
            }

            //Correct for UTF-8
            byte[] resultInUTF8Bytes = result.getBytes("UTF8");
            byte[] trimTo1MB = Arrays.copyOfRange(resultInUTF8Bytes, 0, MAX_SIZE_1MB / 1000);
            return new String(trimTo1MB, "UTF-8");
        }

        /**
         * Fetches text from a file.
         *
         * @param source     the AbstractFile source to get a Reader for
         * @param ocrEnabled true if OCR is enabled false otherwise
         *
         * @return Extracted Text
         *
         * @throws IOException
         * @throws InterruptedException
         * @throws
         * org.sleuthkit.autopsy.textextractors.TextExtractor.InitReaderException
         */
        private String extractText(AbstractFile source, boolean ocrEnabled) throws IOException, InterruptedException, TextExtractor.InitReaderException {
            Reader textExtractor = getTextExtractor(source, ocrEnabled);

            char[] cbuf = new char[8096];
            StringBuilder textBuilder = new StringBuilder();

            //bytesRead can be an int so long as the max file size
            //is sufficiently small
            int bytesRead = 0;
            int read;

            while ((read = textExtractor.read(cbuf)) != -1) {
                if (this.isCancelled()) {
                    throw new InterruptedException();
                }

                //Short-circuit the read if its greater than our max
                //translatable size
                int bytesLeft = MAX_SIZE_1MB - bytesRead;

                if (bytesLeft < read) {
                    textBuilder.append(cbuf, 0, bytesLeft);
                    return textBuilder.toString();
                }

                textBuilder.append(cbuf, 0, read);
                bytesRead += read;
            }

            return textBuilder.toString();
        }

        /**
         * Fetches the appropriate reader for the given file mimetype and
         * configures it to use OCR.
         *
         * @param file       File to be read
         * @param ocrEnabled Determines if the extractor should be configured
         *                   for OCR
         *
         * @return Reader containing Content text
         *
         * @throws IOException
         * @throws NoTextReaderFound
         */
        private Reader getTextExtractor(AbstractFile file, boolean ocrEnabled) throws IOException,
                TextExtractor.InitReaderException {
            Lookup context = null;

            if (ocrEnabled) {
                ImageConfig imageConfig = new ImageConfig();
                imageConfig.setOCREnabled(true);

                String ocrSelection = panel.getSelectedOcrLanguagePack();
                if (!ocrSelection.isEmpty()) {
                    imageConfig.setOCRLanguages(Lists.newArrayList(ocrSelection));
                }

                //Terminate any OS process running in the extractor if this 
                //SwingWorker has been cancelled.
                ProcessTerminator terminator = () -> isCancelled();
                context = Lookups.fixed(imageConfig, terminator);
            }

            try {
                return TextExtractorFactory.getExtractor(file, context).getReader();
            } catch (TextExtractorFactory.NoTextExtractorFound ex) {
                //Fall-back onto the strings extractor
                return TextExtractorFactory.getStringsExtractor(file, context).getReader();
            }
        }
    }

    /**
     * Listens for drop-down selection changes and pushes processing off of the
     * EDT and into a SwingWorker.
     */
    private abstract class SelectionChangeListener implements ActionListener {

        public String currentSelection = null;

        public abstract String getSelection();

        @Override
        public final void actionPerformed(ActionEvent e) {
            String selection = getSelection();
            if (!selection.equals(currentSelection)) {
                currentSelection = selection;
                if (updateTask != null && !updateTask.isDone()) {
                    updateTask.cancel(true);
                }
                updateTask = new BackgroundTranslationTask();

                //Pass the background task to a single threaded pool to keep
                //the number of jobs running to one.
                executorService.execute(updateTask);
            }
        }
    }

    /**
     * Fetches the display drop down selection from the panel.
     */
    private class DisplayDropDownChangeListener extends SelectionChangeListener {

        @Override
        public String getSelection() {
            return panel.getDisplayDropDownSelection();
        }
    }

    /**
     * Fetches the OCR drop down selection from the panel.
     */
    private class OCRDropdownChangeListener extends SelectionChangeListener {

        @Override
        public String getSelection() {
            return panel.getSelectedOcrLanguagePack();
        }
    }
}
