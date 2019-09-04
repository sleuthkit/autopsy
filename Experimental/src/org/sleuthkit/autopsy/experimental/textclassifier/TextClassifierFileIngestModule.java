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
package org.sleuthkit.autopsy.experimental.textclassifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.ml.naivebayes.NaiveBayesModel;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.textextractors.TextExtractor.InitReaderException;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory.NoTextExtractorFound;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Classifies a file as notable or not notable, based on a model trained on
 * labeled data.
 */
public class TextClassifierFileIngestModule extends FileIngestModuleAdapter {

    private final static Logger LOGGER = Logger.getLogger(TextClassifierFileIngestModule.class.getName());
    private Blackboard blackboard;
    private DocumentCategorizerME categorizer;
    private TextClassifierUtils utils;

    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        utils = new TextClassifierUtils();
        try {
            NaiveBayesModel model = TextClassifierUtils.loadModel();
            this.categorizer = new DocumentCategorizerME(new DoccatModel(TextClassifierUtils.LANGUAGE_CODE, model, new HashMap<>(), new DoccatFactory()));
        } catch (IOException ex) {
            throw new IngestModule.IngestModuleException("Unable to load model for text classifier module.", ex);
        }

        try {
            blackboard = Case.getCurrentCaseThrows().getServices().getArtifactsBlackboard();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModule.IngestModuleException("Exception while getting open case.", ex);
        }
    }

    @Override
    public ProcessResult process(AbstractFile file) {
        if (!utils.isSupported(file)) {
            return ProcessResult.OK;
        }

        if (file.getSize() > TextClassifierUtils.MAX_FILE_SIZE) {
            //prevent it from allocating gigabytes of memory for extremely large files
            LOGGER.log(Level.INFO, "Encountered file {0}{1} with object id of {2} which exceeds max file size of {3} bytes, with a size of {4}", new Object[]{file.getParentPath(), file.getName(), file.getId(), TextClassifierUtils.MAX_FILE_SIZE, file.getSize()});
            return IngestModule.ProcessResult.OK;
        }

        boolean isNotable;
        try {
            isNotable = classify(file);
        } catch (IOException | InitReaderException | NoTextExtractorFound ex) {
            LOGGER.log(Level.SEVERE, "Exception while categorizing : " + ex.getMessage(), ex);
            return ProcessResult.ERROR;
        }

        if (isNotable) {
            try {
                BlackboardArtifact artifact = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                artifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME,
                        Bundle.TextClassifierModuleFactory_moduleName_text(),
                        "Possible notable text"));
                try {
                    //Index the artifact for keyword search
                    blackboard.postArtifact(artifact, Bundle.TextClassifierModuleFactory_moduleName_text());
                } catch (Blackboard.BlackboardException ex) {
                    LOGGER.log(Level.SEVERE, "Unable to post blackboard artifact " + artifact.getArtifactID(), ex);
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "TskCoreException in categorizing : " + ex.getMessage(), ex);
            }

        }
        return ProcessResult.OK;
    }

    private boolean classify(AbstractFile file) throws InitReaderException, IOException, NoTextExtractorFound {
        String[] tokens = TextClassifierUtils.extractTokens(file);
        String category = categorizer.getBestCategory(categorizer.categorize(tokens));
        return TextClassifierUtils.NOTABLE_LABEL.equalsIgnoreCase(category);
    }
}
