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
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.GeneralReportModuleAdapter;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * This is a ReportModule that trains a text classifier. The training data it
 * uses is all text-like documents in the current case. The two classes are the
 * ones labeled as notable by the user and the ones not labeled as notable. It
 * will not train a model if none of the documents were labeled as notable, if
 * all of the documents were labeled as notable, or if there are not enough
 * documents in the case for the underlying model trainer to run.
 *
 * The model is stored in %APPDATA%\autopsy\text_classifiers. If a model is
 * already present in that location, the TextClassifierTrainer adds more
 * examples to it.
 */
@ServiceProvider(service = GeneralReportModule.class)
public class TextClassifierTrainer extends GeneralReportModuleAdapter {

    @Override
    @Messages({
        "TextClassifierTrainer.srcModuleName.text=Text classifier model",
        "TextClassifierTrainer.inProgress.text=In progress",
        "TextClassifierTrainer.needFileType.text=File type detection must complete before generating this report.",
        "TextClassifierTrainer.training.text=Training model.",
        "TextClassifierTrainer.noModel.text=No model was trained.",
        "TextClassifierTrainer.writingModel.text=Writing model: ",
        "TextClassifierTrainer.completeModelLocation.text=Complete. Model location: ",
        "TextClassifierTrainer.cannotSave.text=Cannot save model: "
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
        } catch (IOException | TextExtractor.InitReaderException | TextExtractorFactory.NoTextExtractorFound | TskCoreException ex) {
            new File(baseReportDir).delete();
            return;
        }

        DoccatModel model = null;

        progressPanel.setIndeterminate(true);
        progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.training.text"));
        try {
            model = train(TextClassifierUtils.MODEL_PATH, sampleStream);
        } catch (IOException ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.noModel.text"));
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
            TextClassifierUtils.writeModel((NaiveBayesModel) model.getMaxentModel(), TextClassifierUtils.MODEL_PATH);
            progressPanel.complete(ReportStatus.COMPLETE);
            progressPanel.updateStatusLabel(NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.completeModelLocation.text") + TextClassifierUtils.MODEL_PATH);
        } catch (IOException ex) {
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

    @Messages({"TextClassifierTrainer.getDesc.text=Machine learning model to classify which documents are notable"})
    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.getDesc.text");
    }

    public DoccatModel train(String oldModelPath, ObjectStream<DocumentSample> sampleStream) throws IOException {
        TrainingParameters params = new TrainingParameters();
        params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(0));
        params.put(TrainingParameters.ALGORITHM_PARAM, TextClassifierUtils.ALGORITHM);
        if (oldModelPath != null) {
            params.put("MODEL_INPUT", oldModelPath);
        }

        return DocumentCategorizerME.train(TextClassifierUtils.LANGUAGE_CODE, sampleStream, params, new DoccatFactory());
    }

    /**
     * Fetches the training data and converts it to a format OpenNLP can use.
     *
     * @return training data usable by OpenNLP
     */
    private ObjectStream<DocumentSample> processTrainingData(ReportProgressPanel progressPanel) throws TskCoreException, TextExtractorFactory.NoTextExtractorFound, TextExtractor.InitReaderException, IOException {
        progressPanel.updateStatusLabel("Fetching training data");

        List<AbstractFile> allDocs;
        try {
            allDocs = fetchAllDocuments();
        } catch (TskCoreException ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("Cannot fetch documents.");
            throw ex;
        }

        if (allDocs.isEmpty()) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("No documents found. You may need to run the Ingest Module for File Type Detection.");
            throw new TskCoreException();
        }

        Set<Long> notableObjectIDs = fetchNotableObjectIDs();

        progressPanel.updateStatusLabel("Converting training data");
        progressPanel.setMaximumProgress(allDocs.size());
        List<DocumentSample> docSamples = new ArrayList<>();
        String label;
        for (AbstractFile doc : allDocs) {
            if (notableObjectIDs.contains(doc.getId())) {
                label = TextClassifierUtils.NOTABLE_LABEL;
            } else {
                label = TextClassifierUtils.NONNOTABLE_LABEL;
            }
            String[] tokens;
            try {
                tokens = TextClassifierUtils.extractTokens(doc);
            } catch (TextExtractor.InitReaderException ex) {
                progressPanel.complete(ReportStatus.ERROR);
                progressPanel.updateStatusLabel("Cannot initialize reader for document of type " + doc.getMIMEType());
                throw ex;
            } catch (TextExtractorFactory.NoTextExtractorFound ex) {
                progressPanel.complete(ReportStatus.ERROR);
                progressPanel.updateStatusLabel("No text extractor found for document of type " + doc.getMIMEType());
                throw ex;
            }
            DocumentSample docSample = new DocumentSample(label, tokens);
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

    private List<AbstractFile> fetchAllDocuments() throws TskCoreException {
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();

        //The only difference between SupportedFormats's getDocumentMIMETypes() 
        //and FileTypeUtils.FileTypeCategory.DOCUMENTS.getMediaTypes() is that
        //this one contains contains message/rfc822 which is what our test
        //corpus( 20 Newsgroups) has.
        return fileManager.findFilesByMimeType(SupportedFormats.getDocumentMIMETypes());
    }
}
