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

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.MutableContext;
import opennlp.tools.ml.naivebayes.NaiveBayesModel;
import opennlp.tools.ml.naivebayes.NaiveBayesModelReader;
import opennlp.tools.ml.naivebayes.PlainTextNaiveBayesModelReader;
import opennlp.tools.util.TrainingParameters;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * This trains a naive Bayes classifier incrementally. It is modeled after the
 * class NaiveBayseTrainer in OpenNLP. The difference is that it stores the
 * model data in a Map that can easily be added to. It can upload a model from
 * disk, add to it, and write to disk.
 */
public class IncrementalNaiveBayesTrainer extends AbstractEventTrainer {

    private final static Logger LOGGER = Logger.getLogger(IncrementalNaiveBayesTrainer.class.getName());

    //These are the values that we need to serialize
    //For the document classification task, this is the list of categories.
    private List<String> masterOutcomeLabels = new ArrayList<>();
    // For the document classification task, this is the list of words in the
    // vocabulary
    private List<String> masterPredLabels = new ArrayList<>();
    // For the document classification task, there is one MutableContext for
    // word in the vocabulary, and the MutableContext contains the counts of how
    // many times the word coocurred with each class
    private List<MutableContext> parameters = new ArrayList<>();
    //These are the values that we don't need to serialize
    // For the document classification task, this maps each category name to a
    // unique integer for more compact memory usage.
    private Map<String, Integer> masterOutcomeMap = new HashMap<>();
    // For the document classification task, this maps each word in the
    // vocabulary name to a unique integer for more compact memory usage.
    private Map<String, Integer> masterPredMap = new HashMap<>();

    public IncrementalNaiveBayesTrainer() {
        this.setMasterPredLabels(new ArrayList<>());
        this.setMasterOutcomeLabels(new ArrayList<>());
        this.setParameters(new ArrayList<>());
    }

    /**
     * Constructs a trainer that starts with the values in a model, and can
     * incrementally learn from a corpus.
     *
     * @param modelInputPath
     */
    private void uploadModel(String modelInputPath) throws IOException {
        FileReader fr = new FileReader(modelInputPath);
        NaiveBayesModelReader reader = new PlainTextNaiveBayesModelReader(new BufferedReader(fr));
        reader.checkModelType();
        NaiveBayesModel initialModel = (NaiveBayesModel) reader.constructModel();
        fr.close();

        Object[] data = initialModel.getDataStructures();

        //We have no use for data[0], because we can't reconstruct which row goes with which predicate string.
        //pmap has the same data as data[0], except it also contains the predicate string.
        Map<String, Context> pmap = (Map<String, Context>) data[1];
        String[] outcomeNames = (String[]) data[2];

        this.setMasterOutcomeLabels(Arrays.asList(outcomeNames));
        this.setMasterPredLabels(new ArrayList<>(pmap.keySet()));
        List<MutableContext> paramsList = new ArrayList<>();
        for (String pred : masterPredLabels) {
            Context context = pmap.get(pred);
            MutableContext mutableContext = new MutableContext(context.getOutcomes(), context.getParameters());
            paramsList.add(mutableContext);
        }
        this.setParameters(paramsList);
    }

    @Override
    public void init(TrainingParameters trainingParameters, Map<String, String> reportMap) {
        super.init(trainingParameters, reportMap);

        String oldModelPath = trainingParameters.getStringParameter("MODEL_INPUT", null);

        if (oldModelPath != null) {
            try {
                uploadModel(oldModelPath);
            } catch (IOException ex) {
                //If the model doesn't exist, start with a blank one.
                this.setParameters(new ArrayList<>());
            }
        }
    }

    //Method from AbstractEventTrainer
    @Override
    public boolean isSortAndMerge() {
        return false;
    }

    //Method from AbstractEventTrainer
    @Override
    public MaxentModel doTrain(DataIndexer indexer) throws IOException {
        return this.trainModel(indexer);
    }

    private MaxentModel trainModel(DataIndexer di) {
        LOGGER.log(Level.INFO, "Incorporating indexed data for training...  ");
        int[][] contexts = di.getContexts();
        float[][] values = di.getValues();
        int[] numTimesEventsSeen = di.getNumTimesEventsSeen();
        int numUniqueEvents = contexts.length;

        String[] newOutcomeLabels = di.getOutcomeLabels();
        int[] outcomeList = di.getOutcomeList();

        String[] newPredLabels = di.getPredLabels();
        int numPreds = newPredLabels.length; //Number of uniq predicates / vocab size
        int numOutcomes = newOutcomeLabels.length;

        LOGGER.log(Level.INFO, "done.");

        LOGGER.log(Level.INFO, "\tNumber of Event Tokens: " + numUniqueEvents);
        LOGGER.log(Level.INFO, "\t    Number of Outcomes: " + numOutcomes);
        LOGGER.log(Level.INFO, "\t  Number of Predicates: " + numPreds);

        LOGGER.log(Level.INFO, "Computing model parameters...");

        MutableContext[] newParameters = findParameters(numOutcomes, outcomeList, contexts, values, numTimesEventsSeen, numPreds);
        mergeInParameters(newParameters, newOutcomeLabels, newPredLabels);

        LOGGER.log(Level.INFO, "...done.");

        /* Create and return the model ****/
        Context[] finalParameters = parameters.toArray(new Context[parameters.size()]);
        String[] predLabels = masterPredLabels.toArray(new String[masterPredLabels.size()]);
        String[] outcomeLabels = masterOutcomeLabels.toArray(new String[masterOutcomeLabels.size()]);
        return new NaiveBayesModel(finalParameters, predLabels, outcomeLabels);
    }

    private void mergeInParameters(MutableContext[] newParameters, String[] newOutcomeLabels, String[] newPredLabels) {

        List<Integer> mappingNewOutcomeToOldOutcome = new ArrayList<>();
        for (int newIndex = 0; newIndex < newOutcomeLabels.length; newIndex++) {
            String newLabel = newOutcomeLabels[newIndex];
            if (!masterOutcomeMap.containsKey(newLabel)) {
                masterOutcomeMap.put(newLabel, masterOutcomeLabels.size());
                masterOutcomeLabels.add(newLabel);
            }
            mappingNewOutcomeToOldOutcome.add(masterOutcomeMap.get(newLabel));
        }

        int[] allOutcomesPattern = new int[getNumOutcomes()];
        for (int oi = 0; oi < getNumOutcomes(); oi++) {
            allOutcomesPattern[oi] = oi;
        }

        List<Integer> mappingNewPredToOldPred = new ArrayList<>();
        for (int newIndex = 0; newIndex < newPredLabels.length; newIndex++) {
            String newLabel = newPredLabels[newIndex];
            if (!masterPredMap.containsKey(newLabel)) {
                masterPredMap.put(newLabel, masterPredLabels.size());
                masterPredLabels.add(newLabel);
                parameters.add(new MutableContext(allOutcomesPattern, new double[getNumOutcomes()]));
            }
            mappingNewPredToOldPred.add(masterPredMap.get(newLabel));
        }

        for (int newPredIndex = 0; newPredIndex < newParameters.length; newPredIndex++) {
            int masterPredIndex = mappingNewPredToOldPred.get(newPredIndex);
            MutableContext newContext = newParameters[newPredIndex];
            for (int newOutcomeIndex : newContext.getOutcomes()) {
                int masterOutcomeIndex = mappingNewOutcomeToOldOutcome.get(newOutcomeIndex);
                double toAdd = newContext.getParameters()[newOutcomeIndex];
                //This will fail if masterOutcomeIndex is beyond the range of what we've seen before.
                //In other words, the first batch needs to include all classes.
                parameters.get(masterPredIndex).updateParameter(masterOutcomeIndex, toAdd);
            }
        }
    }

    private int getNumOutcomes() {
        return masterOutcomeLabels.size();
    }

    private MutableContext[] findParameters(int numOutcomes, int[] outcomeList, int[][] contexts, float[][] values, int[] numTimesEventsSeen, int numPreds) {
        int numUniqueEvents = contexts.length;

        int[] allOutcomesPattern = new int[numOutcomes];
        for (int oi = 0; oi < numOutcomes; oi++) {
            allOutcomesPattern[oi] = oi;
        }

        // Stores the estimated parameter value of each predicate during iteration.
        MutableContext[] params = new MutableContext[numPreds];
        for (int pi = 0; pi < numPreds; pi++) {
            params[pi] = new MutableContext(allOutcomesPattern, new double[numOutcomes]);
            for (int aoi = 0; aoi < numOutcomes; aoi++) {
                params[pi].setParameter(aoi, 0.0);
            }
        }
        double stepSize = 1;

        for (int ei = 0; ei < numUniqueEvents; ei++) {
            int targetOutcome = outcomeList[ei];
            for (int ni = 0; ni < numTimesEventsSeen[ei]; ni++) {
                for (int ci = 0; ci < contexts[ei].length; ci++) {
                    int pi = contexts[ei][ci];
                    if (values == null) {
                        params[pi].updateParameter(targetOutcome, stepSize);
                    } else {
                        params[pi].updateParameter(targetOutcome, stepSize * values[ei][ci]);
                    }
                }
            }
        }
        return params;
    }

    private void setMasterOutcomeLabels(List<String> outcomes) {
        this.masterOutcomeLabels = outcomes;

        //Set masterOutcomeMap.
        masterOutcomeMap = new HashMap<>();
        for (int i = 0; i < outcomes.size(); i++) {
            masterOutcomeMap.put(outcomes.get(i), i);
        }
    }

    private void setMasterPredLabels(List<String> predicates) {
        this.masterPredLabels = predicates;

        //Set masterPredMap
        masterPredMap = new HashMap<>();
        for (int i = 0; i < predicates.size(); i++) {
            masterPredMap.put(predicates.get(i), i);
        }
    }

    private void setParameters(List<MutableContext> counts) {
        this.parameters = counts;
    }
}
