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
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.ml.naivebayes.NaiveBayesModelWriter;
import opennlp.tools.ml.naivebayes.PlainTextNaiveBayesModelWriter;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.report.GeneralReportModule;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.datamodel.TskCoreException;

public class ClassifierTrainerReportModule implements GeneralReportModule {

    private static String ALGORITHM = "org.sleuthkit.autopsy.experimental.textclassifier.IncrementalNaiveBayesTrainer";

    @Override
    @Messages({"ClassifierTrainerReportModule.srcModuleName.txt=Text classifier model"})
    public void generateReport(String baseReportDir, ReportProgressPanel progressPanel) {
        ObjectStream<DocumentSample> sampleStream = processTrainingData();
        String modelPath = getRelativeFilePath();
        DoccatModel model;
        try {
             model = train(modelPath, sampleStream);
        } catch(IOException ex) {
            throw new RuntimeException("Unable to train text classifier: " + ex);
        }
        
        try {
            writeModel(model, modelPath);
        } catch(IOException ex) {
            throw new RuntimeException("Unable to save text classifier model: " + ex);
        }
    }

    @Override
    public String getName() {
        String name = NbBundle.getMessage(this.getClass(), "ClassifierTrainerReportModule.getName.text");
        return name;
    }

        @Override
    public String getDescription() {
        String desc = NbBundle.getMessage(this.getClass(), "ClassifierTrainerReportModule.getDesc.text");
        return desc;
    }


    @Override
    public String getRelativeFilePath() {
        return "model.txt"; //NON-NLS
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
    private ObjectStream<DocumentSample> processTrainingData() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private void writeModel(DoccatModel model, String modelPath) throws IOException {
        FileWriter fw = new FileWriter(new File(modelPath));
        //TODO: Try the binary naive Bayes model writer
        PlainTextNaiveBayesModelWriter modelWriter;
        modelWriter = new PlainTextNaiveBayesModelWriter(model, new BufferedWriter(fw));
        modelWriter.persist();
        fw.close();
    }
}
