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
import java.util.logging.Level;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
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
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
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

    private final static Logger LOGGER = Logger.getLogger(TextClassifierUtils.class.getName());

    private static final Tokenizer TOKENIZER = SimpleTokenizer.INSTANCE;

    private FileTypeDetector fileTypeDetector;

    static final String NOTABLE_LABEL = "notable";
    static final String NONNOTABLE_LABEL = "nonnotable";
    static final int MAX_FILE_SIZE = 100000000;
    static final String MODEL_DIR = PlatformUtil.getTextClassifierPath();
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

    boolean isSupported(AbstractFile abstractFile, TextClassifierFileIngestModule textClassifierFileIngestModule) {
        String fileMimeType;
        if (fileTypeDetector != null) {
            fileMimeType = fileTypeDetector.getMIMEType(abstractFile);
        } else {
            fileMimeType = abstractFile.getMIMEType();
        }
        return fileMimeType != null && SupportedFormats.contains(fileMimeType);
    }

    static String[] extractTokens(AbstractFile file) {
        Reader reader;
        try {
            try {
                reader = TextExtractorFactory.getExtractor(file).getReader();
            } catch (NoTextExtractorFound ex) {
                LOGGER.log(Level.INFO, "Using StringsExtractor for file \"{0}\" of type {1}", new Object[]{file.getName(), file.getMIMEType()});
                reader = TextExtractorFactory.getStringsExtractor(file, null).getReader();
            }
        } catch (InitReaderException ex) {
            LOGGER.log(Level.WARNING, "Cannot initialize reader for file \"{0}\" of type {1}", new Object[]{file.getName(), file.getMIMEType()});
            return new String[0];
        }
        try {
            String text = IOUtils.toString(reader);
            return TOKENIZER.tokenize(text);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Cannot extract tokens from file " + file.getName(), ex);
            return new String[0];
        }
    }

    static DocumentCategorizerME loadModel() throws IOException {
        FileReader fr = new FileReader(new File(MODEL_PATH));
        NaiveBayesModelReader reader = new PlainTextNaiveBayesModelReader(new BufferedReader(fr));
        reader.checkModelType();
        NaiveBayesModel model = (NaiveBayesModel) reader.constructModel();
        fr.close();
        DoccatModel doccatModel = new DoccatModel(LANGUAGE_CODE, model, new HashMap<>(), new DoccatFactory());
        return new DocumentCategorizerME(doccatModel);
    }

    static void writeModel(NaiveBayesModel model, String modelPath) throws IOException {
        FileWriter fw = new FileWriter(new File(modelPath));
        //TODO: Try the binary naive Bayes model writer
        PlainTextNaiveBayesModelWriter modelWriter;
        modelWriter = new PlainTextNaiveBayesModelWriter(model, new BufferedWriter(fw));
        modelWriter.persist();
        fw.close();
    }

}
