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
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector.FileTypeDetectorInitException;
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
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    
    private Blackboard blackboard;
    private long jobId;
    private NaiveBayesModel model;
    private DocumentCategorizerME categorizer;
    private Tokenizer tokenizer;
    private FileTypeDetector fileTypeDetector;
    
    private void loadModel()  throws IOException {
        File modelFile = InstalledFileLocator.getDefault().locate("text_classifier_model/model.txt", TextClassifierFileIngestModule.class.getPackage().getName(), false);
        this.model = deserializeModel(modelFile);
        DoccatModel doccatModel = new DoccatModel(LANGUAGE_CODE,
                                                  model,
                                                  new HashMap<>(),
                                                  new DoccatFactory());
        this.categorizer = new DocumentCategorizerME(doccatModel);
    }
    
    @Messages({"ObjectDetectionFileIngestModule.noClassifiersFound.subject=No classifiers found.",
        "# {0} - classifierDir", "ObjectDetectionFileIngestModule.noClassifiersFound.message=No classifiers were found in {0}, object detection will not be executed."})
    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        try {
            this.fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetectorInitException ex) {
            throw new IngestModule.IngestModuleException("Exception while constructing FileTypeDector.", ex);
        }
        this.tokenizer = SimpleTokenizer.INSTANCE;
        
        jobId = context.getJobId();
       
        try {
            loadModel();
        } catch (IOException ex) {
            throw new IngestModule.IngestModuleException("Unable to load model for text classifier module.", ex);
        }
                
        try {
            blackboard = Case.getCurrentCaseThrows().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModule.IngestModuleException("Exception while getting open case.", ex);
        }
    }
    
    @Override
    public ProcessResult process(AbstractFile file) {
        isSupported(file);
        
        if (file.getSize() > MAX_FILE_SIZE) {
            //prevent it from allocating gigabytes of memory for extremely large files
            logger.log(Level.INFO, "Encountered file " + file.getParentPath() + file.getName() + " with object id of "
                    + file.getId() + " which exceeds max file size of " + MAX_FILE_SIZE + " bytes, with a size of " + file.getSize());
            return IngestModule.ProcessResult.OK;
        }
        
        boolean isInteresting;
        try {
            isInteresting = classify(file);
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
    private boolean isSupported(AbstractFile abstractFile) {
        String fileMimeType;
        if (fileTypeDetector != null) {
            fileMimeType = fileTypeDetector.getMIMEType(abstractFile);
        } else {
            fileMimeType = abstractFile.getMIMEType();
        }
        return fileMimeType != null && SupportedFormats.contains(fileMimeType);    
    }
    
    private boolean classify(AbstractFile file) throws InitReaderException, IOException, NoTextExtractorFound {
        boolean isInteresting = true;

        Reader reader = TextExtractorFactory.getExtractor(file, null).getReader();
        String text = IOUtils.toString(reader);
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
}
