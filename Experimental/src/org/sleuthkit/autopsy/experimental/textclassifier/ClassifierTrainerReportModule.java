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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.naivebayes.NaiveBayesModelWriter;
import opennlp.tools.ml.naivebayes.PlainTextNaiveBayesModelWriter;
import opennlp.tools.ml.naivebayes.NaiveBayesModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import org.apache.commons.io.IOUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

//TODO: Title should include "TextClassifier"
@ServiceProvider(service = GeneralReportModule.class)
public class ClassifierTrainerReportModule implements GeneralReportModule {

    private static String ALGORITHM = "org.sleuthkit.autopsy.experimental.textclassifier.IncrementalNaiveBayesTrainer";
    //TODO: tokenizer should be shared with TextClassifierFileIngestModule so they don't get out of sync
    private final Tokenizer tokenizer = SimpleTokenizer.INSTANCE;

    private ClassifierTrainerReportModuleConfigPanel configPanel;
    
    @Override
    @Messages({"ClassifierTrainerReportModule.srcModuleName.txt=Text classifier model"})
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
         // Start the progress bar and setup the report
        progressPanel.setIndeterminate(false);
        progressPanel.start();
        progressPanel.updateStatusLabel("In progress");        
        String modelPath = baseReportDir + getRelativeFilePath();
        
        
        /*
        ObjectStream<DocumentSample> sampleStream;
        try {
            sampleStream = processTrainingData();
        } catch (Exception  ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("Unable to process training data: " + ex);
            new File(baseReportDir).delete();
            return;
        }
        DoccatModel model;
        try {
            model = train(modelPath, sampleStream);
        } catch(IOException ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("Unable to train text classifier: " + ex);
            new File(baseReportDir).delete();    
            return;
        }

        try {
            writeModel((NaiveBayesModel) model.getMaxentModel(), modelPath);
        } catch(IOException ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("Unable to save text classifier model: " + ex);
            new File(baseReportDir).delete();
            return;
        }
        */
       
        try {
            File modelFile = new File(modelPath);
            FileWriter fw = new FileWriter(modelFile);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("This is a model file!\n");
            bw.close();
            fw.close();
            progressPanel.complete(ReportStatus.COMPLETE);
            progressPanel.updateStatusLabel("SUCCESS");
        } catch (IOException ex) {
            progressPanel.complete(ReportStatus.ERROR);
            progressPanel.updateStatusLabel("Unable to save text classifier model: " + ex);
            new File(baseReportDir).delete();
            return;
        }
    }

    @Override
    public String getName() {
        //String name = NbBundle.getMessage(this.getClass(), "ClassifierTrainerReportModule.getName.text");
        String name = "Text Classifier Trainer";
        return name;
    }

    @Override
    public String getDescription() {
        //String desc = NbBundle.getMessage(this.getClass(), "ClassifierTrainerReportModule.getDesc.text");
        String desc = "Machine learning model to classify which documents are notable";
        return desc;
    }


    @Override
    public String getRelativeFilePath() {
        return "model.txt"; //NON-NLS
    }
    
    @Override
    public JPanel getConfigurationPanel() {
        configPanel = new ClassifierTrainerReportModuleConfigPanel();
        return configPanel;
    }

    public DoccatModel train(String oldModelPath, ObjectStream<DocumentSample> sampleStream) throws IOException {
        long startTime = System.nanoTime();

        TrainingParameters params = new TrainingParameters();
        params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(0));
        params.put(TrainingParameters.ALGORITHM_PARAM, ALGORITHM);
        if (oldModelPath != null) {
            params.put("MODEL_INPUT", oldModelPath);
        }

        DoccatModel model = DocumentCategorizerME.train("en", sampleStream, params, new DoccatFactory());

        double duration = (System.nanoTime() - startTime) / 1.0e9;
        System.out.println(duration + "\ttrain time for text classifier");
        return model;
    }

    /**
     * Fetches the training data and converts it to a format OpenNLP can use.
     * @return training data usable by OpenNLP
     */
    private ObjectStream<DocumentSample> processTrainingData() throws TskCoreException, TextExtractor.InitReaderException, IOException, TextExtractorFactory.NoTextExtractorFound {
        List<AbstractFile> allDocs = fetchAllDocuments();
        Set<Long> notableObjectIDs = fetchNotableObjectIDs();

        List<DocumentSample> docSamples = new ArrayList<>();
        String label;
        for (AbstractFile doc : allDocs) {
            if (notableObjectIDs.contains(doc.getId())) {
                label = "notable";
            } else {
                label = "nonnotable";
            }
            //TODO: The method to build a reader and get text should be in another class accessable to TextClassifierFileIngestModule
            Reader reader = TextExtractorFactory.getExtractor(doc, null).getReader();
            String text = IOUtils.toString(reader);
            DocumentSample docSample = new DocumentSample(label, tokenizer.tokenize(text));

            docSamples.add(docSample);
        }
        return new ListObjectStream<DocumentSample>(docSamples);
    }

    private Set<Long> fetchNotableObjectIDs() throws TskCoreException {
        //Get files labeled as interesting
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
        //Get all files
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
        List<AbstractFile> allFiles = fileManager.findFilesByMimeType(FileTypeUtils.FileTypeCategory.DOCUMENTS.getMediaTypes());
        return allFiles;
    }

    private ObjectStream<DocumentSample> convertTrainingData(Collection<AbstractFile> trainingFiles) {
        throw new UnsupportedOperationException();
    }

    private void writeModel(NaiveBayesModel model, String modelPath) throws IOException {
        FileWriter fw = new FileWriter(new File(modelPath));
        //TODO: Try the binary naive Bayes model writer
        PlainTextNaiveBayesModelWriter modelWriter;
        modelWriter = new PlainTextNaiveBayesModelWriter(model, new BufferedWriter(fw));
        modelWriter.persist();
        fw.close();
    }
}
