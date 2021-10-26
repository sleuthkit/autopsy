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
package org.sleuthkit.autopsy.discovery.search;

import com.google.common.io.Files;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.autopsy.textsummarizer.TextSummarizer;
import org.sleuthkit.autopsy.textsummarizer.TextSummary;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Utility class for code which helps create summaries for Discovery.
 */
class SummaryHelpers {

    private static final int PREVIEW_SIZE = 256;
    private final static Logger logger = Logger.getLogger(SummaryHelpers.class.getName());
    private static volatile TextSummarizer summarizerToUse = null;

    private SummaryHelpers() {
        // Class should not be instantiated
    }

    /**
     * Get the default text summary for the document.
     *
     * @param file The file to summarize.
     *
     * @return The TextSummary object which is a default summary for the file.
     */
    static TextSummary getDefaultSummary(AbstractFile file) {
        Image image = null;
        int countOfImages = 0;
        try {
            Content largestChild = null;
            for (Content child : file.getChildren()) {
                if (child instanceof AbstractFile && ImageUtils.isImageThumbnailSupported((AbstractFile) child)) {
                    countOfImages++;
                    if (largestChild == null || child.getSize() > largestChild.getSize()) {
                        largestChild = child;
                    }
                }
            }
            if (largestChild != null) {
                image = ImageUtils.getThumbnail(largestChild, ImageUtils.ICON_SIZE_LARGE);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting children for file: " + file.getId(), ex);
        }
        image = image == null ? image : image.getScaledInstance(ImageUtils.ICON_SIZE_MEDIUM, ImageUtils.ICON_SIZE_MEDIUM,
                Image.SCALE_SMOOTH);
        String summaryText = null;
        if (file.getMd5Hash() != null) {
            try {
                summaryText = getSavedSummary(Paths.get(Case.getCurrentCaseThrows().getCacheDirectory(), "summaries", file.getMd5Hash() + "-default-" + PREVIEW_SIZE + "-translated.txt").toString());
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Unable to retrieve saved summary. No case is open.", ex);
            }
        }
        if (StringUtils.isBlank(summaryText)) {
            String firstLines = getFirstLines(file);
            String translatedFirstLines = getTranslatedVersion(firstLines);
            if (!StringUtils.isBlank(translatedFirstLines)) {
                summaryText = translatedFirstLines;
                if (file.getMd5Hash() != null) {
                    try {
                        saveSummary(summaryText, Paths.get(Case.getCurrentCaseThrows().getCacheDirectory(), "summaries", file.getMd5Hash() + "-default-" + PREVIEW_SIZE + "-translated.txt").toString());
                    } catch (NoCurrentCaseException ex) {
                        logger.log(Level.WARNING, "Unable to save translated summary. No case is open.", ex);
                    }
                }
            } else {
                summaryText = firstLines;
            }
        }
        return new TextSummary(summaryText, image, countOfImages);
    }

    /**
     * Provide an English version of the specified String if it is not English,
     * translation is enabled, and it can be translated.
     *
     * @param documentString The String to provide an English version of.
     *
     * @return The English version of the provided String, or null if no
     *         translation occurred.
     */
    static String getTranslatedVersion(String documentString) {
        try {
            TextTranslationService translatorInstance = TextTranslationService.getInstance();
            if (translatorInstance.hasProvider()) {
                String translatedResult = translatorInstance.translate(documentString);
                if (translatedResult.isEmpty() == false) {
                    return translatedResult;
                }
            }
        } catch (NoServiceProviderException | TranslationException ex) {
            logger.log(Level.INFO, "Error translating string for summary", ex);
        }
        return null;
    }

    /**
     * Find and load a saved summary from the case folder for the specified
     * file.
     *
     * @param summarySavePath The full path for the saved summary file.
     *
     * @return The summary found given the specified path, null if no summary
     *         was found.
     */
    static String getSavedSummary(String summarySavePath) {
        if (summarySavePath == null) {
            return null;
        }
        File savedFile = new File(summarySavePath);
        if (savedFile.exists()) {
            try (BufferedReader bReader = new BufferedReader(new FileReader(savedFile))) {
                // pass the path to the file as a parameter
                StringBuilder sBuilder = new StringBuilder(PREVIEW_SIZE);
                String sCurrentLine = bReader.readLine();
                while (sCurrentLine != null) {
                    sBuilder.append(sCurrentLine).append('\n');
                    sCurrentLine = bReader.readLine();
                }
                return sBuilder.toString();
            } catch (IOException ingored) {
                //summary file may not exist or may be incomplete in which case return null so a summary can be generated
                return null; //no saved summary was able to be found
            }
        } else {
            try {  //if the file didn't exist make sure the parent directories exist before we move on to creating a summary
                Files.createParentDirs(savedFile);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Unable to create summaries directory in case folder for file at: " + summarySavePath, ex);
            }
            return null; //no saved summary was able to be found
        }

    }

    /**
     * Save a summary at the specified location.
     *
     * @param summary         The text of the summary being saved.
     * @param summarySavePath The full path for the saved summary file.
     */
    static void saveSummary(String summary, String summarySavePath) {
        if (summarySavePath == null) {
            return;  //can't save a summary if we don't have a path
        }
        try (FileWriter myWriter = new FileWriter(summarySavePath)) {
            myWriter.write(summary);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to save summary at: " + summarySavePath, ex);
        }
    }

    @NbBundle.Messages({"SummaryHelper.documentSummary.unable.to.read=Error trying to extract text from file."})
    /**
     * Get the beginning of text from the specified AbstractFile.
     *
     * @param file The AbstractFile to get text from.
     *
     * @return The beginning of text from the specified AbstractFile.
     */
    static String getFirstLines(AbstractFile file) {
        TextExtractor extractor;
        try {
            extractor = TextExtractorFactory.getExtractor(file, null);
        } catch (TextExtractorFactory.NoTextExtractorFound ignored) {
            //no extractor found, use Strings Extractor
            extractor = TextExtractorFactory.getStringsExtractor(file, null);
        }

        try (Reader reader = extractor.getReader()) {
            char[] cbuf = new char[PREVIEW_SIZE];
            reader.read(cbuf, 0, PREVIEW_SIZE);
            return new String(cbuf);
        } catch (IOException ex) {
            return Bundle.FileSearch_documentSummary_noBytes();
        } catch (TextExtractor.InitReaderException ex) {
            return Bundle.SummaryHelper_documentSummary_unable_to_read();
        }
    }

    /**
     * Get the first TextSummarizer found by a lookup of TextSummarizers.
     *
     * @return The first TextSummarizer found by a lookup of TextSummarizers.
     *
     * @throws IOException
     */
    static TextSummarizer getLocalSummarizer() {
        if (summarizerToUse == null) {
            Collection<? extends TextSummarizer> summarizers
                    = Lookup.getDefault().lookupAll(TextSummarizer.class
                    );
            if (!summarizers.isEmpty()) {
                summarizerToUse = summarizers.iterator().next();
            }
        }
        return summarizerToUse;
    }

}
