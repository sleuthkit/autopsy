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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.ml.naivebayes.NaiveBayesModel;
import opennlp.tools.ml.naivebayes.NaiveBayesModelReader;
import opennlp.tools.ml.naivebayes.PlainTextNaiveBayesModelReader;
import opennlp.tools.ml.naivebayes.PlainTextNaiveBayesModelWriter;
import opennlp.tools.sentdetect.SentenceDetector;
import org.apache.commons.io.IOUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.datamodel.AbstractFile;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import org.openide.modules.InstalledFileLocator;
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

    private static SentenceDetector sentenceDetector;

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

        //Read the file and tokenize it.
        try {
            //Read the text
            String text = IOUtils.toString(reader);
            return cleanAndTokenize(text);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Cannot extract tokens from file " + file.getName(), ex);
            return new String[0];
        }
    }

    private static synchronized void initializeSentenceDetector() throws IOException {
        //Initialize the sentenceDetector if that hasn't been done yet.
        if (sentenceDetector == null) {
            //Define the sentence detector, trained on the train section of the EWT corpus, from Universal Dependencies
            final File sentenceModelFile = InstalledFileLocator.getDefault().locate("textclassifier/en-sent-ewt.bin", TextClassifierUtils.class.getPackage().getName(), false); //NON-NLS
            if (sentenceModelFile == null) {
                throw new IOException("Error finding sentence detector module");
            }
            sentenceDetector = new NewlineHeuristicSentenceDetector(sentenceModelFile);
        }
    }

    private static String[] cleanAndTokenize(String text) throws IOException {
        initializeSentenceDetector();

        ArrayList<String> tokens = new ArrayList<>();
        String[] sentences = sentenceDetector.sentDetect(text);
        for (String sentence : sentences) {
            //Tokenize the file.
            String[] sentenceTokens = TOKENIZER.tokenize(sentence);
            if (sentenceTokens.length > 5) {
                tokens.addAll(Arrays.asList(sentenceTokens));
            }
        }
        return tokens.toArray(new String[0]);
    }

    /**
     * Loads a Naive Bayes categorizer from disk.
     *
     * @return the categorizer
     * @throws IOException if the model cannot be found on disk, or if the file
     * does not seem to be a model file
     */
    static DocumentCategorizerME loadCategorizer() throws IOException {
        ensureTextClassifierFolderExists();
        NaiveBayesModel model;
        try (FileReader fr = new FileReader(new File(MODEL_PATH))) {
            NaiveBayesModelReader reader = new PlainTextNaiveBayesModelReader(new BufferedReader(fr));
            reader.checkModelType();
            model = (NaiveBayesModel) reader.constructModel();
        }
        DoccatModel doccatModel = new DoccatModel(LANGUAGE_CODE, model, new HashMap<>(), new DoccatFactory());
        return new DocumentCategorizerME(doccatModel);
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

}
