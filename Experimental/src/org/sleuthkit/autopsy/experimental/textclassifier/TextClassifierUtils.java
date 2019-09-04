/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.textclassifier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.naivebayes.NaiveBayesModel;
import opennlp.tools.ml.naivebayes.NaiveBayesModelReader;
import opennlp.tools.ml.naivebayes.PlainTextNaiveBayesModelReader;
import opennlp.tools.ml.naivebayes.PlainTextNaiveBayesModelWriter;
import org.apache.commons.io.IOUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.datamodel.AbstractFile;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import static org.sleuthkit.autopsy.coreutils.PlatformUtil.getUserDirectory;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector.FileTypeDetectorInitException;
import org.sleuthkit.autopsy.textextractors.TextExtractor.InitReaderException;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory.NoTextExtractorFound;

/**
 * A collection of helpful methods and objects for training and running the text
 * classifier.
 */
class TextClassifierUtils {

    private static final Logger LOGGER = Logger.getLogger(TextClassifierUtils.class.getName());
    private static final String TEXT_CLASSIFIERS_SUBDIRECTORY = "text_classifiers"; //NON-NLS
    private static final Tokenizer TOKENIZER = SimpleTokenizer.INSTANCE;
    private FileTypeDetector fileTypeDetector;
    static final String NOTABLE_LABEL = "notable";
    static final String NONNOTABLE_LABEL = "nonnotable";
    static final int MAX_FILE_SIZE = 100000000;
    static final String MODEL_DIR = getTextClassifierPath();
    static final String MODEL_PATH = MODEL_DIR + File.separator + "model.txt";
    static final String LANGUAGE_CODE = "en";
    static final String ALGORITHM = "org.sleuthkit.autopsy.experimental.textclassifier.IncrementalNaiveBayesTrainer";

    TextClassifierUtils() throws IngestModuleException {
        try {
            this.fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetectorInitException ex) {
            throw new IngestModuleException("Exception while constructing FileTypeDector.", ex);
        }
    }

    /**
     * Get root path where the user's text classifiers are stored.
     *
     * @return Absolute path to the text classifiers root directory.
     */
    static String getTextClassifierPath() {
        return getUserDirectory().getAbsolutePath() + File.separator + TEXT_CLASSIFIERS_SUBDIRECTORY;
    }

    /**
     * Make a folder in the config directory for test classifiers if one does
     * not exist.
     */
    static void ensureTextClassifierFolderExists() {
        File textClassifierDir = new File(getTextClassifierPath());
        textClassifierDir.mkdir();
    }

    /**
     * Checks whether the text classifier supports a file of this MIME type.
     * Supported MIME types are document types that tend to contain natural
     * language text.
     *
     * @param abstractFile A file
     * @return true if this file's MIME type is supported.
     */
    boolean isSupported(AbstractFile abstractFile) {
        String fileMimeType;
        if (fileTypeDetector != null) {
            fileMimeType = fileTypeDetector.getMIMEType(abstractFile);
        } else {
            fileMimeType = abstractFile.getMIMEType();
        }
        return fileMimeType != null && SupportedFormats.contains(fileMimeType);
    }

    /**
     * Divides a file into tokens
     *
     * @param a file
     * @return An array of all tokens in the file
     */
    static String[] extractTokens(AbstractFile file) {
        //Build the Reader we will use to read the file.
        Reader reader;
        try {
            try {
                //If a TextExtractor exists for this file type, use that one.
                reader = TextExtractorFactory.getExtractor(file).getReader();
            } catch (NoTextExtractorFound ex) {
                //Else, use the StringsExtractor.
                LOGGER.log(Level.INFO, "Using StringsExtractor for file \"{0}\" of type {1}", new Object[]{file.getName(), file.getMIMEType()});
                reader = TextExtractorFactory.getStringsExtractor(file, null).getReader();
            }
        } catch (InitReaderException ex) {
            LOGGER.log(Level.WARNING, "Cannot initialize reader for file \"{0}\" of type {1}", new Object[]{file.getName(), file.getMIMEType()});
            return new String[0];
        }

        //Read the file, and tokenize it.
        try {
            //Read the text
            String text = IOUtils.toString(reader);
            //Tokenize the file.
            return TOKENIZER.tokenize(text);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Cannot extract tokens from file " + file.getName(), ex);
            return new String[0];
        }
    }

    /**
     * Loads a Naive Bayes model from disk.
     *
     * @return the model
     * @throws IOException if the model cannot be found on disk, or if the file
     * does not seem to be a model file
     */
    static NaiveBayesModel loadModel() throws IOException {
        try (FileReader fr = new FileReader(MODEL_PATH)) {
            NaiveBayesModelReader reader = new PlainTextNaiveBayesModelReader(new BufferedReader(fr));
            reader.checkModelType();
            return (NaiveBayesModel) reader.constructModel();
        }
    }

    /**
     * Writes a naive Bayes classifier model to disk.
     *
     * @param model the model
     * @throws IOException If the model file cannot be written. Large files can
     * cause this.
     */
    static void writeModel(NaiveBayesModel model) throws IOException {
        ensureTextClassifierFolderExists();
        try (FileWriter fw = new FileWriter(new File(MODEL_PATH))) {
            PlainTextNaiveBayesModelWriter modelWriter;
            modelWriter = new PlainTextNaiveBayesModelWriter(model, new BufferedWriter(fw));
            modelWriter.persist();
        }
    }

    static Map<String, Double> countTokens(NaiveBayesModel model) {
        Object[] data = model.getDataStructures();
        Map<String, Context> pmap = (Map<String, Context>) data[1];
        String[] outcomeNames = (String[]) data[2];

        //Initialize counts to 0
        Map<String, Double> categoryToTokenCount = new HashMap<>();
        for (String outcomeName : outcomeNames) {
            categoryToTokenCount.put(outcomeName, 0.0);
        }

        //Count how many tokens are in the training data for each category.
        for (String pred : pmap.keySet()) {
            Context context = pmap.get(pred);
            int outcomeIndex = 0;
            for (String outcomeName : outcomeNames) {
                double oldValue = categoryToTokenCount.get(outcomeName);
                double toAdd = context.getParameters()[outcomeIndex];
                categoryToTokenCount.put(outcomeName, oldValue + toAdd);
                outcomeIndex++;
            }
        }
        return categoryToTokenCount;
    }
}
