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
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerEvaluator;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.ml.naivebayes.NaiveBayesModel;
import opennlp.tools.ml.naivebayes.NaiveBayesModelReader;
import opennlp.tools.ml.naivebayes.PlainTextNaiveBayesModelReader;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.ObjectStream;
import org.apache.commons.io.IOUtils;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.textextractors.TextExtractor.InitReaderException;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory.NoTextExtractorFound;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Classifies a file as interesting or not interesting, based on a model 
 * trained on labeled data.
 */
public class TextClassifierFileIngestModule extends FileIngestModuleAdapter {
    private final static Logger logger = Logger.getLogger(TextClassifierFileIngestModule.class.getName());
    private final static String LANGUAGE_CODE = "en";
    private final static int MAX_FILE_SIZE = 100000000;
    
    NaiveBayesModel model;
    DocumentCategorizerME categorizer;
    Tokenizer tokenizer;
    
    public TextClassifierFileIngestModule() {
        this(new File("C:\\Users\\Brian Kjersten\\Documents\\Story-specific\\5332\\model11314.txt"), SimpleTokenizer.INSTANCE);
    }
    
    //TODO: Constructor. I suppose the constructor needs a model
    public TextClassifierFileIngestModule(File modelFile, Tokenizer tokenizer) {
        try {
            this.model = deserializeModel(modelFile);
        } catch (IOException ex) {
            //TODO: RuntimeException is unacceptable in production
            throw new RuntimeException("Cannot deserialize model file: " + ex.getMessage());
        }
        DoccatModel doccatModel = new DoccatModel(LANGUAGE_CODE,
                                                  model,
                                                  new HashMap<>(),
                                                  new DoccatFactory());
        this.categorizer = new DocumentCategorizerME(doccatModel);
        this.tokenizer = tokenizer;
    }
    
    @Override
    public ProcessResult process(AbstractFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            //prevent it from allocating gigabytes of memory for extremely large files
            logger.log(Level.INFO, "Encountered file " + file.getParentPath() + file.getName() + " with object id of "
                    + file.getId() + " which exceeds max file size of " + MAX_FILE_SIZE + " bytes, with a size of " + file.getSize());
            return IngestModule.ProcessResult.OK;
        }
        
        boolean isInteresting;
        try {
            isInteresting = isInteresting(file);
        } catch (TextExtractorFactory.NoTextExtractorFound ex) {
            logger.log(Level.SEVERE, "NoTextExtractorFound in categorizing : " + ex.getMessage());
            return ProcessResult.ERROR;
        } catch (InitReaderException ex) {
            logger.log(Level.SEVERE, "InitReaderException in categorizing : " + ex.getMessage());
            return ProcessResult.ERROR;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "IOException in categorizing : " + ex.getMessage());
            return ProcessResult.ERROR;
        }
        
        if(isInteresting) {
            try {
                BlackboardArtifact artifact = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                artifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME,
                                                              Bundle.TextClassifierModuleFactory_moduleName_text(),
                                                              "Possible notable text"));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "TskCoreException in categorizing : " + ex.getMessage());
            }
        }
        return ProcessResult.OK;
    }
    
    private boolean isInteresting(AbstractFile file) throws InitReaderException, IOException, NoTextExtractorFound {
        boolean isInteresting = true;

        String text;
        Reader reader = TextExtractorFactory.getExtractor(file, null).getReader();
        text = IOUtils.toString(reader);
        String[] tokens = tokenizer.tokenize(text);
        String category = categorizer.getBestCategory(categorizer.categorize(tokens));
        isInteresting = "interesting".equalsIgnoreCase(category);
       
        return isInteresting;
    }

    public static NaiveBayesModel deserializeModel(File inputFile) throws IOException {
        FileReader fr = new FileReader(inputFile);
        NaiveBayesModelReader reader = new PlainTextNaiveBayesModelReader(new BufferedReader(fr));
        reader.checkModelType();
        NaiveBayesModel model = (NaiveBayesModel)reader.constructModel();
        fr.close();
        return model;
    }
    
    
    public void test(DoccatModel model, ObjectStream<DocumentSample> sampleStream) throws IOException {
        long startTime = System.nanoTime();

        DocumentCategorizerME myCategorizer = new DocumentCategorizerME(model);

        DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(myCategorizer);
        DocumentSample sample = sampleStream.read();
        while (sample != null) {
            evaluator.processSample(sample);
            sample = sampleStream.read();
        }

        double duration = (System.nanoTime() - startTime) / 1.0e9;
        System.out.println(duration + "\ttest time");
        System.out.println(evaluator.getAccuracy() + "\taccuracy");
    }
}
