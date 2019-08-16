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
import javax.swing.JPanel;
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
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

@ServiceProvider(service = GeneralReportModule.class)
public class TextClassifierTrainer implements GeneralReportModule {

    private TextClassifierTrainerConfigPanel configPanel;

    @Override
    @Messages({"TextClassifierTrainer.srcModuleName.txt=Text classifier model"})
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        // Start the progress bar and setup the report
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.complete(ReportStatus.RUNNING);
        progressPanel.updateStatusLabel("In progress");

        ObjectStream<DocumentSample> sampleStream;
        try {
            sampleStream = processTrainingData(progressPanel);
        } catch (Exception ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("Unable to process training data: " + ex);
            new File(baseReportDir).delete();
            return;
        }

        DoccatModel model = null;

        try {
            progressPanel.setIndeterminate(true);
            progressPanel.updateStatusLabel("Training model");
            model = train(TextClassifierUtils.MODEL_PATH, sampleStream);
        } catch (IOException ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("Unable to train text classifier: " + ex);
            new File(baseReportDir).delete();
            return;
        }

        if (model == null) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("No model was trained");
            new File(baseReportDir).delete();
            return;
        }
        try {
            progressPanel.setIndeterminate(true);
            progressPanel.updateStatusLabel("Writing model to " + TextClassifierUtils.MODEL_PATH);
            TextClassifierUtils.writeModel((NaiveBayesModel) model.getMaxentModel(), TextClassifierUtils.MODEL_PATH);
            progressPanel.complete(ReportStatus.COMPLETE);
            progressPanel.updateStatusLabel("Complete. Model is at " + TextClassifierUtils.MODEL_PATH);
        } catch (IOException ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("Unable to save text classifier model: " + ex);
            new File(baseReportDir).delete();
            return;
        }
    }

    @Messages({"TextClassifierTrainer.getName.text=Text classifier model"})
    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.getName.text");
        return name;
    }

    @Messages({"TextClassifierTrainer.getDesc.text=Machine learning model to classify which documents are notable"})
    @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "TextClassifierTrainer.getDesc.text");
        return desc;
    }

    @Override
    public String getRelativeFilePath() {
        return "classifierTrainerReport.txt";
    }

    @Override
    public JPanel getConfigurationPanel() {
        configPanel = new TextClassifierTrainerConfigPanel();
        return configPanel;
    }

    public DoccatModel train(String oldModelPath, ObjectStream<DocumentSample> sampleStream) throws IOException {
        long startTime = System.nanoTime();

        TrainingParameters params = new TrainingParameters();
        params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(0));
        params.put(TrainingParameters.ALGORITHM_PARAM, TextClassifierUtils.ALGORITHM);
        if (oldModelPath != null) {
            params.put("MODEL_INPUT", oldModelPath);
        }

        DoccatModel model = DocumentCategorizerME.train("en", sampleStream, params, new DoccatFactory());

        double duration = (System.nanoTime() - startTime) / 1.0e9;
        return model;
    }

    /**
     * Fetches the training data and converts it to a format OpenNLP can use.
     *
     * @return training data usable by OpenNLP
     */
    private ObjectStream<DocumentSample> processTrainingData(ReportProgressPanel progressPanel) throws TskCoreException, TextExtractor.InitReaderException, IOException, TextExtractorFactory.NoTextExtractorFound {
        progressPanel.updateStatusLabel("Fetching training data");

        List<AbstractFile> allDocs = fetchAllDocuments();
        Set<Long> notableObjectIDs = fetchNotableObjectIDs();

        int notableDocCount = 0;
        int nonnotableDocCount = 0;

        progressPanel.updateStatusLabel("Converting training data");
        progressPanel.setMaximumProgress(allDocs.size());
        List<DocumentSample> docSamples = new ArrayList<>();
        String label;
        for (AbstractFile doc : allDocs) {
            if (notableObjectIDs.contains(doc.getId())) {
                label = TextClassifierUtils.NOTABLE_LABEL;
                notableDocCount++;
            } else {
                label = TextClassifierUtils.NONNOTABLE_LABEL;
                nonnotableDocCount++;
            }

            String[] tokens = TextClassifierUtils.extractTokens(doc);
            DocumentSample docSample = new DocumentSample(label, tokens);
            docSamples.add(docSample);

            progressPanel.increment();
        }

        ObjectStream<DocumentSample> objectStream = new ListObjectStream<>(docSamples);

        return objectStream;
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
        List<AbstractFile> allDocs = fileManager.findFilesByMimeType(SupportedFormats.getDocumentMIMETypes());
        return allDocs;
    }

}
