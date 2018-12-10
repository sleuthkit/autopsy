/*
 * Central Repository
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.eventlisteners;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import static java.lang.Boolean.FALSE;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadUtils;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Listen for ingest events and update entries in the Central Repository
 * database accordingly
 */
@NbBundle.Messages({"IngestEventsListener.ingestmodule.name=Correlation Engine"})
public class IngestEventsListener {
    
    private static final Logger LOGGER = Logger.getLogger(CorrelationAttributeInstance.class.getName());
    private static final String MODULE_NAME = Bundle.IngestEventsListener_ingestmodule_name();
    
    final Collection<String> recentlyAddedCeArtifacts = new LinkedHashSet<>();
    private static int correlationModuleInstanceCount;
    private static boolean flagNotableItems;
    private static boolean flagSeenDevices;
    private final ExecutorService jobProcessingExecutor;
    private static final String INGEST_EVENT_THREAD_NAME = "Ingest-Event-Listener-%d";
    private final PropertyChangeListener pcl1 = new IngestModuleEventListener();
    private final PropertyChangeListener pcl2 = new IngestJobEventListener();
    
    IngestEventsListener() {
        jobProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(INGEST_EVENT_THREAD_NAME).build());
    }
    
    void shutdown() {
        ThreadUtils.shutDownTaskExecutor(jobProcessingExecutor);
    }

    /*
     * Add all of our Ingest Event Listeners to the IngestManager Instance.
     */
    public void installListeners() {
        IngestManager.getInstance().addIngestModuleEventListener(pcl1);
        IngestManager.getInstance().addIngestJobEventListener(pcl2);
    }

    /*
     * Remove all of our Ingest Event Listeners from the IngestManager Instance.
     */
    public void uninstallListeners() {
        IngestManager.getInstance().removeIngestModuleEventListener(pcl1);
        IngestManager.getInstance().removeIngestJobEventListener(pcl2);
    }

    /**
     * Increase the number of IngestEventsListeners adding contents to the
     * Correlation Engine.
     */
    public synchronized static void incrementCorrelationEngineModuleCount() {
        correlationModuleInstanceCount++;  //Should be called once in the Correlation Engine module's startup method.
    }

    /**
     * Decrease the number of IngestEventsListeners adding contents to the
     * Correlation Engine.
     */
    public synchronized static void decrementCorrelationEngineModuleCount() {
        if (getCeModuleInstanceCount() > 0) {  //prevent it ingestJobCounter from going negative
            correlationModuleInstanceCount--;  //Should be called once in the Correlation Engine module's shutdown method.
        }
    }

    /**
     * Reset the counter which keeps track of if the Correlation Engine Module
     * is being run during injest to 0.
     */
    synchronized static void resetCeModuleInstanceCount() {
        correlationModuleInstanceCount = 0;  //called when a case is opened in case for some reason counter was not reset
    }

    /**
     * Whether or not the Correlation Engine Module is enabled for any of the
     * currently running ingest jobs.
     *
     * @return boolean True for Correlation Engine enabled, False for disabled
     */
    public synchronized static int getCeModuleInstanceCount() {
        return correlationModuleInstanceCount;
    }

    /**
     * Are notable items being flagged?
     *
     * @return True if flagging notable items; otherwise false.
     */
    public synchronized static boolean isFlagNotableItems() {
        return flagNotableItems;
    }

    /**
     * Are previously seen devices being flagged?
     *
     * @return True if flagging seen devices; otherwise false.
     */
    public synchronized static boolean isFlagSeenDevices() {
        return flagSeenDevices;
    }

    /**
     * Configure the listener to flag notable items or not.
     *
     * @param value True to flag notable items; otherwise false.
     */
    public synchronized static void setFlagNotableItems(boolean value) {
        flagNotableItems = value;
    }

    /**
     * Configure the listener to flag previously seen devices or not.
     *
     * @param value True to flag seen devices; otherwise false.
     */
    public synchronized static void setFlagSeenDevices(boolean value) {
        flagSeenDevices = value;
    }
    
    @NbBundle.Messages({"IngestEventsListener.prevTaggedSet.text=Previously Tagged As Notable (Central Repository)",
        "IngestEventsListener.prevCaseComment.text=Previous Case: "})
    static private void postCorrelatedBadArtifactToBlackboard(BlackboardArtifact bbArtifact, List<String> caseDisplayNames) {
        
        Collection<BlackboardAttribute> attributes = Arrays.asList(
                new BlackboardAttribute(
                        TSK_SET_NAME, MODULE_NAME,
                        Bundle.IngestEventsListener_prevTaggedSet_text()),
                new BlackboardAttribute(
                        TSK_COMMENT, MODULE_NAME,
                        Bundle.IngestEventsListener_prevCaseComment_text() + caseDisplayNames.stream().distinct().collect(Collectors.joining(","))),
                new BlackboardAttribute(
                        TSK_ASSOCIATED_ARTIFACT, MODULE_NAME,
                        bbArtifact.getArtifactID()));
        postArtifactToBlackboard(bbArtifact, attributes);
    }

    /**
     * Create an Interesting Aritfact hit for a device which was previously seen
     * in the central repository.
     *
     * @param bbArtifact the artifact to create the interesting item for
     */
    @NbBundle.Messages({"IngestEventsListener.prevExists.text=Previously Seen Devices (Central Repository)",
        "# {0} - typeName",
        "# {1} - count",
        "IngestEventsListener.prevCount.text=Number of previous {0}: {1}"})
    static private void postCorrelatedPreviousArtifactToBlackboard(BlackboardArtifact bbArtifact) {
        Collection<BlackboardAttribute> attributes = Arrays.asList(
                new BlackboardAttribute(
                        TSK_SET_NAME, MODULE_NAME,
                        Bundle.IngestEventsListener_prevExists_text()),
                new BlackboardAttribute(
                        TSK_ASSOCIATED_ARTIFACT, MODULE_NAME,
                        bbArtifact.getArtifactID()));
        postArtifactToBlackboard(bbArtifact, attributes);
    }
    
    private static void postArtifactToBlackboard(BlackboardArtifact bbArtifact, Collection<BlackboardAttribute> attributes) {
        try {
            
            SleuthkitCase tskCase = bbArtifact.getSleuthkitCase();
            AbstractFile abstractFile = bbArtifact.getSleuthkitCase().getAbstractFileById(bbArtifact.getObjectID());
            Blackboard blackboard = tskCase.getBlackboard();
            // Create artifact if it doesn't already exist.
            if (!blackboard.artifactExists(abstractFile, TSK_INTERESTING_ARTIFACT_HIT, attributes)) {
                BlackboardArtifact tifArtifact = abstractFile.newArtifact(TSK_INTERESTING_ARTIFACT_HIT);
                tifArtifact.addAttributes(attributes);
                try {
                    // index the artifact for keyword search
                    blackboard.postArtifact(tifArtifact, MODULE_NAME);
                } catch (Blackboard.BlackboardException ex) {
                    LOGGER.log(Level.SEVERE, "Unable to index blackboard artifact " + tifArtifact.getArtifactID(), ex); //NON-NLS
                }
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create BlackboardArtifact.", ex); // NON-NLS
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create BlackboardAttribute.", ex); // NON-NLS
        }
    }
    
    private class IngestModuleEventListener implements PropertyChangeListener {
        
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            //if ingest is running we want there to check if there is a Correlation Engine module running 
            //sometimes artifacts are generated by DSPs or other sources while ingest is not running
            //in these cases we still want to create correlation attributes for those artifacts when appropriate
            if (!IngestManager.getInstance().isIngestRunning() || getCeModuleInstanceCount() > 0) {
                EamDb dbManager;
                try {
                    dbManager = EamDb.getInstance();
                } catch (EamDbException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to connect to Central Repository database.", ex);
                    return;
                }
                switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
                    case DATA_ADDED: {
                        //if ingest isn't running create the interesting items otherwise use the ingest module setting to determine if we create interesting items
                        boolean flagNotable = !IngestManager.getInstance().isIngestRunning() || isFlagNotableItems();
                        boolean flagPrevious = !IngestManager.getInstance().isIngestRunning() || isFlagSeenDevices();
                        jobProcessingExecutor.submit(new DataAddedTask(dbManager, evt, flagNotable, flagPrevious));
                        break;
                    }
                }
            }
        }
    }
    
    private class IngestJobEventListener implements PropertyChangeListener {
        
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            switch (IngestManager.IngestJobEvent.valueOf(evt.getPropertyName())) {
                case DATA_SOURCE_ANALYSIS_COMPLETED: {
                    jobProcessingExecutor.submit(new AnalysisCompleteTask());
                    break;
                }
            }
        }
        
    }
    
    private final class AnalysisCompleteTask implements Runnable {
        
        @Override
        public void run() {
            // clear the tracker to reduce memory usage
            if (getCeModuleInstanceCount() == 0) {
                recentlyAddedCeArtifacts.clear();
            }
            //else another instance of the Correlation Engine Module is still being run.
        } // DATA_SOURCE_ANALYSIS_COMPLETED
    }
    
    private final class DataAddedTask implements Runnable {
        
        private final EamDb dbManager;
        private final PropertyChangeEvent event;
        private final boolean flagNotableItemsEnabled;
        private final boolean flagPreviousItemsEnabled;
        
        private DataAddedTask(EamDb db, PropertyChangeEvent evt, boolean flagNotableItemsEnabled, boolean flagPreviousItemsEnabled) {
            dbManager = db;
            event = evt;
            this.flagNotableItemsEnabled = flagNotableItemsEnabled;
            this.flagPreviousItemsEnabled = flagPreviousItemsEnabled;
        }
        
        @Override
        public void run() {
            if (!EamDb.isEnabled()) {
                return;
            }
            final ModuleDataEvent mde = (ModuleDataEvent) event.getOldValue();
            Collection<BlackboardArtifact> bbArtifacts = mde.getArtifacts();
            if (null == bbArtifacts) { //the ModuleDataEvents don't always have a collection of artifacts set
                return;
            }
            List<CorrelationAttributeInstance> eamArtifacts = new ArrayList<>();
            
            for (BlackboardArtifact bbArtifact : bbArtifacts) {
                // eamArtifact will be null OR a EamArtifact containing one EamArtifactInstance.
                List<CorrelationAttributeInstance> convertedArtifacts = EamArtifactUtil.makeInstancesFromBlackboardArtifact(bbArtifact, true);
                for (CorrelationAttributeInstance eamArtifact : convertedArtifacts) {
                    try {
                        // Only do something with this artifact if it's unique within the job
                        if (recentlyAddedCeArtifacts.add(eamArtifact.toString())) {
                            // Was it previously marked as bad?
                            // query db for artifact instances having this TYPE/VALUE and knownStatus = "Bad".
                            // if getKnownStatus() is "Unknown" and this artifact instance was marked bad in a previous case, 
                            // create TSK_INTERESTING_ARTIFACT_HIT artifact on BB.
                            if (flagNotableItemsEnabled) {
                                List<String> caseDisplayNames;
                                try {
                                    caseDisplayNames = dbManager.getListCasesHavingArtifactInstancesKnownBad(eamArtifact.getCorrelationType(), eamArtifact.getCorrelationValue());
                                    if (!caseDisplayNames.isEmpty()) {
                                        postCorrelatedBadArtifactToBlackboard(bbArtifact,
                                                caseDisplayNames);
                                    }
                                } catch (CorrelationAttributeNormalizationException ex) {
                                    LOGGER.log(Level.INFO, String.format("Unable to flag notable item: %s.", eamArtifact.toString()), ex);
                                }
                            }
                            if (flagPreviousItemsEnabled
                                && (eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.USBID_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.ICCID_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.IMEI_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.IMSI_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.MAC_TYPE_ID)) {
                                try {
                                    Long countPreviousOccurences = dbManager.getCountArtifactInstancesByTypeValue(eamArtifact.getCorrelationType(), eamArtifact.getCorrelationValue());
                                    if (countPreviousOccurences > 0) {
                                        postCorrelatedPreviousArtifactToBlackboard(bbArtifact);
                                    }
                                } catch (CorrelationAttributeNormalizationException ex) {
                                    LOGGER.log(Level.INFO, String.format("Unable to flag notable item: %s.", eamArtifact.toString()), ex);
                                }
                            }
                            eamArtifacts.add(eamArtifact);
                        }
                    } catch (EamDbException ex) {
                        LOGGER.log(Level.SEVERE, "Error counting notable artifacts.", ex);
                    }
                }
            }
            if (FALSE == eamArtifacts.isEmpty()) {
                for (CorrelationAttributeInstance eamArtifact : eamArtifacts) {
                    try {
                        dbManager.addArtifactInstance(eamArtifact);
                    } catch (EamDbException ex) {
                        LOGGER.log(Level.SEVERE, "Error adding artifact to database.", ex); //NON-NLS
                    }
                }
            } // DATA_ADDED
        }
    }
}
