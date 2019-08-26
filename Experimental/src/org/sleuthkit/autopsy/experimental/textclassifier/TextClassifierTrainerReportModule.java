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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
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
 * a model if none of the documents were labeled as notable, if all of the
 * documents were labeled as notable, or if there are not enough documents in
 * the case for the underlying model trainer to run.
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
        "TextClassifierTrainer.srcModuleName.text=Text classifier model",
        "TextClassifierTrainer.inProgress.text=In progress",
        "TextClassifierTrainer.needFileType.text=File type detection must complete before generating this report.",
        "TextClassifierTrainer.cannotProcess.text=Exception while processing training data",
        "TextClassifierTrainer.training.text=Training model.",
        "TextClassifierTrainer.noModel.text=No model was trained.",
        "TextClassifierTrainer.writingModel.text=Writing model: ",
        "TextClassifierTrainer.completeModelLocation.text=Complete. Model location: ",
        "TextClassifierTrainer.cannotSave.text=Cannot save model: ",
        "TextClassifierTrainer.trainIOException.text=IOException while training model"
    })
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {

        if (IngestManager.getInstance().isIngestRunning()) {
            MessageNotifyUtil.Message.error(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.needFileType.text"));
        }

        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.complete(ReportStatus.RUNNING);
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.inProgress.text"));

        ObjectStream<DocumentSample> sampleStream;
        try {
            sampleStream = processTrainingData(progressPanel);
        } catch (TskCoreException ex) {
            //prograssPanel was updated in processTrainingData, so no need
            //to do it here.
            LOGGER.log(Level.SEVERE, "Exception while processing training data", ex);
            new File(baseReportDir).delete();
            return;
        }

        DoccatModel model;

        progressPanel.setIndeterminate(true);
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.training.text"));
        try {
            model = train(TextClassifierUtils.MODEL_PATH, sampleStream);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "IOException during training", ex);
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.trainIOException.text"));
            new File(baseReportDir).delete();
            return;
        }

        if (model == null) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.noModel.text"));
            new File(baseReportDir).delete();
            return;
        }
        try {
            progressPanel.setIndeterminate(true);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.writingModel.text") + TextClassifierUtils.MODEL_PATH);
            TextClassifierUtils.writeModel((NaiveBayesModel) model.getMaxentModel());
            progressPanel.complete(ReportStatus.COMPLETE);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.completeModelLocation.text") + TextClassifierUtils.MODEL_PATH);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Cannot save model.", ex);
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.cannotSave.text") + TextClassifierUtils.MODEL_PATH);
            new File(baseReportDir).delete();
            return;
        }
    }

    @Messages({"TextClassifierTrainer.getName.text=Text classifier model"})
    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.getName.text");
    }

    @Messages({"TextClassifierTrainer.getDesc.text=Train a machine learning "
        + "model to classify which documents are notable. Before training, "
        + "you will need to\n"
        + "1. run the Ingest Module for File Type Identification\n"
        + "2. add the file tag \"Notable item (notable)\" to documents you "
        + "know are notable.\n"
        + "Once you've trained a model, you can classify documents by "
        + "running the \"Text Classifier\" Ingest Module."})
    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.getDesc.text");
    }

    private DoccatModel train(String oldModelPath, ObjectStream<DocumentSample> sampleStream) throws IOException {
        TrainingParameters params = new TrainingParameters();
        params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(0));
        params.put(TrainingParameters.ALGORITHM_PARAM, TextClassifierUtils.ALGORITHM);
        if (oldModelPath != null) {
            params.put("MODEL_INPUT", oldModelPath);
        }

        return DocumentCategorizerME.train(TextClassifierUtils.LANGUAGE_CODE, sampleStream, params, new DoccatFactory());
    }

    @Messages({
        "TextClassifierTrainer.noDocs.text=No documents found. You may need to run the Ingest Module for File Type Detection",
        "TextClassifierTrainer.fetching.text=Fetching training documents",
        "TextClassifierTrainer.converting.text=Converting training documents",
        "TextClassifierTrainer.needNotable.text=Training set must contain at least one notable document",
        "TextClassifierTrainer.needNonnotable.text=Training set must contain at least one nonnotable document",})
    /**
     * Fetches the training data and converts it to a format OpenNLP can use.
     *
     * @return training data usable by OpenNLP
     */
    private ObjectStream<DocumentSample> processTrainingData(ReportProgressPanel progressPanel) throws TskCoreException {

        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.fetching.text"));

        List<AbstractFile> allDocs;
        allDocs = fetchAllDocuments();

        LOGGER.log(Level.INFO, "There are {0} documents", allDocs.size());

        if (allDocs.isEmpty()) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.noDocs.text"));
            throw new TskCoreException("No documents found. You may need to run the Ingest Module for File Type Detection.");
        }

        Set<Long> notableObjectIDs = fetchNotableObjectIDs();

        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.converting.text"));
        progressPanel.setMaximumProgress(allDocs.size());
        List<DocumentSample> docSamples = new ArrayList<>();
        String label;
        boolean containsNotableDocument = false;
        boolean containsNonNotableDocument = false;
        for (AbstractFile doc : allDocs) {
            if (notableObjectIDs.contains(doc.getId())) {
                label = TextClassifierUtils.NOTABLE_LABEL;
                containsNotableDocument = true;
            } else {
                label = TextClassifierUtils.NONNOTABLE_LABEL;
                containsNonNotableDocument = true;
            }
            DocumentSample docSample = new DocumentSample(label, TextClassifierUtils.extractTokens(doc));
            docSamples.add(docSample);

            progressPanel.increment();
        }

        if (!containsNotableDocument) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.needNotable.text"));
            throw new TskCoreException("Training set must contain at least one notable document");
        }
        if (!containsNonNotableDocument) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.needNonnotable.text"));
            throw new TskCoreException("Training set must contain at least one nonnotable document");
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

    private List<AbstractFile> fetchAllDocuments() throws TskCoreException {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        //The only difference between SupportedFormats's getDocumentMIMETypes()
        //and FileTypeUtils.FileTypeCategory.DOCUMENTS.getMediaTypes() is that
        //this one contains contains message/rfc822 which is what our test
        //corpus( 20 Newsgroups) has.
        return fileManager.findFilesByMimeType(SupportedFormats.getDocumentMIMETypes());
    }
}
