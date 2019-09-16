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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.naivebayes.NaiveBayesModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.GeneralReportModuleAdapter;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * This is a ReportModule that trains a text classifier. It trains on all
 * text-like documents in the current case. The two classes are the ones labeled
 * as notable by the user and the ones not labeled as notable. It will not train
 * a model if there are no text-like documents in the collection.
 *
 * The model is stored in %APPDATA%\autopsy\text_classifiers. If a model is
 * already present in that location, the TextClassifierTrainerReportModule adds
 * more examples to it.
 */
@ServiceProvider(service = GeneralReportModule.class)
public class TextClassifierTrainerReportModule extends GeneralReportModuleAdapter {

    private final static Logger LOGGER = Logger.getLogger(TextClassifierTrainerReportModule.class.getName());

    @Override
    @Messages({
        "TextClassifierTrainerReportModule.noDocs.text=No documents found. You may need to run the Ingest Module for File Type Detection",
        "TextClassifierTrainerReportModule.fetching.text=Fetching training documents",
        "TextClassifierTrainerReportModule.srcModuleName.text=Text classifier model",
        "TextClassifierTrainerReportModule.inProgress.text=In progress",
        "TextClassifierTrainerReportModule.needFileType.text=File type detection must complete before generating this report.",
        "TextClassifierTrainerReportModule.fetchFailed.text=Exception while fetching training data",
        "TextClassifierTrainerReportModule.cannotProcess.text=Exception while converting training data",
        "TextClassifierTrainerReportModule.training.text=Training model.",
        "TextClassifierTrainerReportModule.noModel.text=No model was trained.",
        "TextClassifierTrainerReportModule.writingModel.text=Writing model: ",
        "TextClassifierTrainerReportModule.completeModelLocation.text=Complete. Model location: ",
        "TextClassifierTrainerReportModule.cannotSave.text=Cannot save model: ",
        "TextClassifierTrainerReportModule.trainIOException.text=IOException while training model"
    })
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        //Fail early if ingest is still running
        if (IngestManager.getInstance().isIngestRunning()) {
            MessageNotifyUtil.Message.error(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.needFileType.text"));
        }

        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.complete(ReportStatus.RUNNING);
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.inProgress.text"));

        Queue<AbstractFile> allDocs;
        Set<Long> notableObjectIDs;
        try {
            //Get all files in the case that are documents. Some of these are
            //notable.
            allDocs = fetchAllDocuments(progressPanel);
            //Fetch all notable objects. Some of these are documents.
            notableObjectIDs = fetchNotableObjectIDs();
        } catch (TskCoreException ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.fetchFailed.text"));
            LOGGER.log(Level.SEVERE, "Exception while fetching training data", ex);
            new File(baseReportDir).delete();
            return;
        }
        if (allDocs.isEmpty()) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.noDocs.text"));
            new File(baseReportDir).delete();
            return;
        }

        int BATCH_SIZE = 1000;
        while(!allDocs.isEmpty()) {
            //Convert allDocs to the format that OpenNLP needs.
            ObjectStream<DocumentSample> sampleStream;
            
            Queue<AbstractFile> batchDocs = new LinkedList<>();
            while (!allDocs.isEmpty() && batchDocs.size() < BATCH_SIZE) {
                batchDocs.offer(allDocs.poll());
            }
            //TODO: Delete this
            System.out.println("********** allDocs.size()\t" + allDocs.size() + "**********");
            
            sampleStream = convertTrainingData(batchDocs, notableObjectIDs, progressPanel);
            
            //Train the model
            progressPanel.setIndeterminate(true);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.training.text"));
            DoccatModel model;
            try {
                model = train(progressPanel, TextClassifierUtils.MODEL_PATH, sampleStream);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "IOException during training", ex);
                progressPanel.complete(ReportStatus.ERROR);
                progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.trainIOException.text"));
                new File(baseReportDir).delete();
                return;
            }

            //If there was an uncaught training error
            if (model == null) {
                continue;
            }

            //Write the model to disk
            progressPanel.setIndeterminate(true);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.writingModel.text") + TextClassifierUtils.MODEL_PATH);
            try {
                TextClassifierUtils.writeModel((NaiveBayesModel) model.getMaxentModel());
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Cannot save model.", ex);
                progressPanel.complete(ReportStatus.ERROR);
                progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.cannotSave.text") + TextClassifierUtils.MODEL_PATH);
                new File(baseReportDir).delete();
                return;
            }
        }
        progressPanel.complete(ReportStatus.COMPLETE);
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.completeModelLocation.text") + TextClassifierUtils.MODEL_PATH);
    }

    @Messages({"TextClassifierTrainerReportModule.getName.text=Text classifier model"})
    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.getName.text");
    }

    @Messages({"TextClassifierTrainerReportModule.getDesc.text=Train a machine learning "
        + "model to classify which documents are notable. Before training, "
        + "you will need to\n"
        + "1. run the Ingest Module for File Type Identification\n"
        + "2. add the file tag \"Notable item (notable)\" to documents you "
        + "know are notable.\n"
        + "Once you've trained a model, you can classify documents by "
        + "running the \"Text Classifier\" Ingest Module."})
    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.getDesc.text");
    }

    private DoccatModel train(ReportProgressPanel progressPanel, String oldModelPath, ObjectStream<DocumentSample> sampleStream) throws IOException {
        TrainingParameters params = new TrainingParameters();
        params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(0));
        params.put(TrainingParameters.ALGORITHM_PARAM, TextClassifierUtils.ALGORITHM);
        //Use the one-pass data indexer. The 2-pass indexer writes all the
        //during training, and fails if this output is more than 64kB.
        params.put(AbstractEventTrainer.DATA_INDEXER_PARAM, AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE);
        if (oldModelPath != null) {
            params.put("MODEL_INPUT", oldModelPath);
        }
        return DocumentCategorizerME.train(TextClassifierUtils.LANGUAGE_CODE, sampleStream, params, new DoccatFactory());
    }

    /**
     * Converts all documents to a format OpenNLP can use.
     *
     * @param documents
     * @param notableObjectIDs
     * @param progressPanel
     * @return training data usable by OpenNLP
     * @throws TskCoreException
     */
    @Messages({
        "TextClassifierTrainerReportModule.converting.text=Converting training documents",
        "TextClassifierTrainerReportModule.needNotable.text=Training set must contain at least one notable document",
        "TextClassifierTrainerReportModule.needNonnotable.text=Training set must contain at least one nonnotable document",})
    private ObjectStream<DocumentSample> convertTrainingData(Collection<AbstractFile> documents, Set<Long> notableObjectIDs, ReportProgressPanel progressPanel) {

        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.converting.text"));
        progressPanel.setMaximumProgress(documents.size());
        List<DocumentSample> docSamples = new ArrayList<>();
        String label;
        
        docSamples.add(new DocumentSample(TextClassifierUtils.NOTABLE_LABEL, new String[0]));
        docSamples.add(new DocumentSample(TextClassifierUtils.NONNOTABLE_LABEL, new String[0]));
        
        for (AbstractFile doc : documents) {
            if (notableObjectIDs.contains(doc.getId())) {
                label = TextClassifierUtils.NOTABLE_LABEL;
            } else {
                label = TextClassifierUtils.NONNOTABLE_LABEL;
            }
            DocumentSample docSample = new DocumentSample(label, TextClassifierUtils.extractTokens(doc));
            docSamples.add(docSample);

            progressPanel.increment();
        }
        return new ListObjectStream<>(docSamples);
    }

    private Set<Long> fetchNotableObjectIDs() throws TskCoreException {
        //Get files labeled as notable
        TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();

        Set<Long> notableObjectIDs = new HashSet<>();
        List<ContentTag> tags = tagsManager.getAllContentTags();
        for (ContentTag tag : tags) {
            if (tag.getName().getKnownStatus() == TskData.FileKnown.BAD) {
                notableObjectIDs.add(tag.getContent().getId());
            }
        }
        return notableObjectIDs;
    }

    private Queue<AbstractFile> fetchAllDocuments(ReportProgressPanel progressPanel) throws TskCoreException {
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainerReportModule.fetching.text"));
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        //The only difference between SupportedFormats's getDocumentMIMETypes()
        //and FileTypeUtils.FileTypeCategory.DOCUMENTS.getMediaTypes() is that
        //this one contains contains message/rfc822 which is what our test
        //corpus( 20 Newsgroups) has.
        List<AbstractFile> allDocs = fileManager.findFilesByMimeType(SupportedFormats.getDocumentMIMETypes());
        LOGGER.log(Level.INFO, "There are {0} documents", allDocs.size());
        return new LinkedList(allDocs);

    }
}
