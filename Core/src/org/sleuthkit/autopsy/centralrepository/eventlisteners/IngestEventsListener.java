/*
 * Central Repository
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.autopsy.coreutils.ThreadUtils;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_VALUE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_OTHER_CASES;
import org.sleuthkit.autopsy.ingest.events.DataSourceAnalysisEvent;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskData;

/**
 * Listen for ingest events and update entries in the Central Repository
 * database accordingly
 */
@NbBundle.Messages({"IngestEventsListener.ingestmodule.name=Central Repository"})
public class IngestEventsListener {

    private static final Logger LOGGER = Logger.getLogger(CorrelationAttributeInstance.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_COMPLETED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(DATA_ADDED);
    private static final String MODULE_NAME = Bundle.IngestEventsListener_ingestmodule_name();
    private static int correlationModuleInstanceCount;
    private static boolean flagNotableItems;
    private static boolean flagSeenDevices;
    private static boolean createCrProperties;
    private static boolean flagUniqueArtifacts;
    private static final String INGEST_EVENT_THREAD_NAME = "Ingest-Event-Listener-%d";
    private final ExecutorService jobProcessingExecutor;
    private final PropertyChangeListener pcl1 = new IngestModuleEventListener();
    private final PropertyChangeListener pcl2 = new IngestJobEventListener();
    final Collection<String> recentlyAddedCeArtifacts = new LinkedHashSet<>();
    
    static final int MAX_NUM_PREVIOUS_CASES_FOR_LIKELY_NOTABLE_SCORE = 10;
    static final int MAX_NUM_PREVIOUS_CASES_FOR_PREV_SEEN_ARTIFACT_CREATION = 20;

    public IngestEventsListener() {
        jobProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(INGEST_EVENT_THREAD_NAME).build());
    }

    public void shutdown() {
        ThreadUtils.shutDownTaskExecutor(jobProcessingExecutor);
    }

    /*
     * Add all of our Ingest Event Listeners to the IngestManager Instance.
     */
    public void installListeners() {
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, pcl1);
        IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, pcl2);
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
     * Central Repository.
     */
    public synchronized static void incrementCorrelationEngineModuleCount() {
        correlationModuleInstanceCount++;  //Should be called once in the Central Repository module's startup method.
    }

    /**
     * Decrease the number of IngestEventsListeners adding contents to the
     * Central Repository.
     */
    public synchronized static void decrementCorrelationEngineModuleCount() {
        if (getCeModuleInstanceCount() > 0) {  //prevent it ingestJobCounter from going negative
            correlationModuleInstanceCount--;  //Should be called once in the Central Repository module's shutdown method.
        }
    }

    /**
     * Reset the counter which keeps track of if the Central Repository Module
     * is being run during injest to 0.
     */
    synchronized static void resetCeModuleInstanceCount() {
        correlationModuleInstanceCount = 0;  //called when a case is opened in case for some reason counter was not reset
    }

    /**
     * Whether or not the Central Repository Module is enabled for any of the
     * currently running ingest jobs.
     *
     * @return boolean True for Central Repository enabled, False for disabled
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
     * Are correlation properties being created
     *
     * @return True if creating correlation properties; otherwise false.
     */
    public synchronized static boolean shouldCreateCrProperties() {
        return createCrProperties;
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
    
    /**
     * Configure the listener to flag unique apps or not.
     *
     * @param value True to flag unique apps; otherwise false.
     */
    public synchronized static void setFlagUniqueArtifacts(boolean value) {
        flagUniqueArtifacts = value;
    }
    
    /**
     * Are unique apps being flagged?
     *
     * @return True if flagging unique apps; otherwise false.
     */
    public synchronized static boolean isFlagUniqueArtifacts() {
        return flagUniqueArtifacts;
    }

    /**
     * Configure the listener to create correlation properties
     *
     * @param value True to create properties; otherwise false.
     */
    public synchronized static void setCreateCrProperties(boolean value) {
        createCrProperties = value;
    }

    /**
     * Make a "previously seen" artifact based on a new artifact being
     * previously seen.
     *
     * @param originalArtifact Original artifact that we want to flag
     * @param caseDisplayNames List of case names artifact was previously seen
     *                         in
     * @param aType            The correlation type.
     * @param value            The correlation value.
     */
    @NbBundle.Messages({"IngestEventsListener.prevTaggedSet.text=Previously Tagged As Notable (Central Repository)",
        "IngestEventsListener.prevCaseComment.text=Previous Case: "})
    static private void makeAndPostPreviousNotableArtifact(BlackboardArtifact originalArtifact, List<String> caseDisplayNames,
            CorrelationAttributeInstance.Type aType, String value) {
        String prevCases = caseDisplayNames.stream().distinct().collect(Collectors.joining(","));
        String justification = "Previously marked as notable in cases " + prevCases;
        Collection<BlackboardAttribute> attributesForNewArtifact = Arrays.asList(new BlackboardAttribute(
                TSK_SET_NAME, MODULE_NAME,
                Bundle.IngestEventsListener_prevTaggedSet_text()),
                new BlackboardAttribute(
                        TSK_CORRELATION_TYPE, MODULE_NAME,
                        aType.getDisplayName()),
                new BlackboardAttribute(
                        TSK_CORRELATION_VALUE, MODULE_NAME,
                        value),
                new BlackboardAttribute(
                        TSK_OTHER_CASES, MODULE_NAME,
                        prevCases));
        makeAndPostArtifact(BlackboardArtifact.Type.TSK_PREVIOUSLY_NOTABLE, originalArtifact, attributesForNewArtifact, Bundle.IngestEventsListener_prevTaggedSet_text(),
                Score.SCORE_NOTABLE, justification);
    }

    /**
     * Create a "previously seen" hit for a device which was previously seen
     * in the central repository. NOTE: Artifacts that are too common will be skipped.
     *
     * @param originalArtifact the artifact to create the "previously seen" item for
     * @param caseDisplayNames the case names the artifact was previously seen
     *                         in
     * @param aType            The correlation type.
     * @param value            The correlation value.
     */
    @NbBundle.Messages({"IngestEventsListener.prevExists.text=Previously Seen Devices (Central Repository)",
        "# {0} - typeName",
        "# {1} - count",
        "IngestEventsListener.prevCount.text=Number of previous {0}: {1}"})
    static private void makeAndPostPreviousSeenArtifact(BlackboardArtifact originalArtifact, List<String> caseDisplayNames,
            CorrelationAttributeInstance.Type aType, String value) {
        
        // calculate score
        Score score;
        int numCases = caseDisplayNames.size();
        if (numCases <= MAX_NUM_PREVIOUS_CASES_FOR_LIKELY_NOTABLE_SCORE) {
            score = Score.SCORE_LIKELY_NOTABLE;
        } else if (numCases > MAX_NUM_PREVIOUS_CASES_FOR_LIKELY_NOTABLE_SCORE && numCases <= MAX_NUM_PREVIOUS_CASES_FOR_PREV_SEEN_ARTIFACT_CREATION) {
            score = Score.SCORE_NONE; 
        } else {
            // don't make an Analysis Result, the artifact is too common.
            return;
        }
        
        String prevCases = caseDisplayNames.stream().distinct().collect(Collectors.joining(","));
        String justification = "Previously seen in cases " + prevCases;
        Collection<BlackboardAttribute> attributesForNewArtifact = Arrays.asList(new BlackboardAttribute(
                TSK_SET_NAME, MODULE_NAME,
                Bundle.IngestEventsListener_prevExists_text()),
                new BlackboardAttribute(
                        TSK_CORRELATION_TYPE, MODULE_NAME,
                        aType.getDisplayName()),
                new BlackboardAttribute(
                        TSK_CORRELATION_VALUE, MODULE_NAME,
                        value),
                new BlackboardAttribute(
                        TSK_OTHER_CASES, MODULE_NAME,
                        prevCases));     
        makeAndPostArtifact(BlackboardArtifact.Type.TSK_PREVIOUSLY_SEEN, originalArtifact, attributesForNewArtifact, Bundle.IngestEventsListener_prevExists_text(),
                score, justification);
    }
    
    /**
     * Create a "previously unseen" hit for an application which was never seen in
     * the central repository.
     *
     * @param originalArtifact the artifact to create the "previously unseen" item
     *                         for
     * @param aType            The correlation type.
     * @param value            The correlation value.
     */
    static private void makeAndPostPreviouslyUnseenArtifact(BlackboardArtifact originalArtifact, CorrelationAttributeInstance.Type aType, String value) {
                Collection<BlackboardAttribute> attributesForNewArtifact = Arrays.asList(
                new BlackboardAttribute(
                    TSK_CORRELATION_TYPE, MODULE_NAME,
                    aType.getDisplayName()),
                new BlackboardAttribute(
                    TSK_CORRELATION_VALUE, MODULE_NAME,
                    value));
        makeAndPostArtifact(BlackboardArtifact.Type.TSK_PREVIOUSLY_UNSEEN, originalArtifact, attributesForNewArtifact, "",
                Score.SCORE_LIKELY_NOTABLE, "This application has not been previously seen before");
    }   
    
    /**
     * Make an artifact to flag the passed in artifact.
     *
     * @param newArtifactType          Type of artifact to create.
     * @param originalArtifact         Artifact in current case we want to flag
     * @param attributesForNewArtifact Attributes to assign to the new artifact
     * @param configuration            The configuration to be specified for the new artifact hit
     * @param score                    sleuthkit.datamodel.Score to be assigned to this artifact
     * @param justification            Justification string
     */
    private static void makeAndPostArtifact(BlackboardArtifact.Type newArtifactType, BlackboardArtifact originalArtifact, Collection<BlackboardAttribute> attributesForNewArtifact, String configuration,
            Score score, String justification) {
        try {
            SleuthkitCase tskCase = originalArtifact.getSleuthkitCase();
            Blackboard blackboard = tskCase.getBlackboard();
            // Create artifact if it doesn't already exist.
            BlackboardArtifact.ARTIFACT_TYPE type = BlackboardArtifact.ARTIFACT_TYPE.fromID(newArtifactType.getTypeID());
            if (!blackboard.artifactExists(originalArtifact, type, attributesForNewArtifact)) {
                  BlackboardArtifact newArtifact = originalArtifact.newAnalysisResult(
                        newArtifactType, score, 
                        null, configuration, justification, attributesForNewArtifact)
                        .getAnalysisResult();

                try {
                    // index the artifact for keyword search
                    blackboard.postArtifact(newArtifact, MODULE_NAME);
                } catch (Blackboard.BlackboardException ex) {
                    LOGGER.log(Level.SEVERE, "Unable to index blackboard artifact " + newArtifact.getArtifactID(), ex); //NON-NLS
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
            //if ingest is running we want there to check if there is a Central Repository module running 
            //sometimes artifacts are generated by DSPs or other sources while ingest is not running
            //in these cases we still want to create correlation attributesForNewArtifact for those artifacts when appropriate
            if (!IngestManager.getInstance().isIngestRunning() || getCeModuleInstanceCount() > 0) {
                CentralRepository dbManager;
                try {
                    dbManager = CentralRepository.getInstance();
                } catch (CentralRepoException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to connect to Central Repository database.", ex);
                    return;
                }
                switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
                    case DATA_ADDED: {
                        //if ingest isn't running create the "previously seen" items, 
                        // otherwise use the ingest module setting to determine if we create "previously seen" items
                        boolean flagNotable = !IngestManager.getInstance().isIngestRunning() || isFlagNotableItems();
                        boolean flagPrevious = !IngestManager.getInstance().isIngestRunning() || isFlagSeenDevices();
                        boolean createAttributes = !IngestManager.getInstance().isIngestRunning() || shouldCreateCrProperties();
                        boolean flagUnique = !IngestManager.getInstance().isIngestRunning() || isFlagUniqueArtifacts();
                        jobProcessingExecutor.submit(new DataAddedTask(dbManager, evt, flagNotable, flagPrevious, createAttributes, flagUnique));
                        break;
                    }
                    default:
                        break;
                }
            }
        }
    }

    private class IngestJobEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            CentralRepository dbManager;
            try {
                dbManager = CentralRepository.getInstance();
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, "Failed to connect to Central Repository database.", ex);
                return;
            }

            switch (IngestManager.IngestJobEvent.valueOf(evt.getPropertyName())) {
                case DATA_SOURCE_ANALYSIS_COMPLETED: {
                    jobProcessingExecutor.submit(new AnalysisCompleteTask(dbManager, evt));
                    break;
                }
                default:
                    break;
            }
        }

    }

    private final class AnalysisCompleteTask implements Runnable {

        private final CentralRepository dbManager;
        private final PropertyChangeEvent event;

        private AnalysisCompleteTask(CentralRepository db, PropertyChangeEvent evt) {
            dbManager = db;
            event = evt;
        }

        @Override
        public void run() {
            // clear the tracker to reduce memory usage
            if (getCeModuleInstanceCount() == 0) {
                recentlyAddedCeArtifacts.clear();
            }
            //else another instance of the Central Repository Module is still being run.

            /*
             * Ensure the data source in the Central Repository has hash values
             * that match those in the case database.
             */
            if (!CentralRepository.isEnabled()) {
                return;
            }
            Content dataSource;
            String dataSourceName = "";
            long dataSourceObjectId = -1;
            try {
                dataSource = ((DataSourceAnalysisEvent) event).getDataSource();
                /*
                 * We only care about Images for the purpose of updating hash
                 * values.
                 */
                if (!(dataSource instanceof Image)) {
                    return;
                }

                dataSourceName = dataSource.getName();
                dataSourceObjectId = dataSource.getId();

                Case openCase = Case.getCurrentCaseThrows();

                CorrelationCase correlationCase = dbManager.getCase(openCase);
                if (null == correlationCase) {
                    correlationCase = dbManager.newCase(openCase);
                }

                CorrelationDataSource correlationDataSource = dbManager.getDataSource(correlationCase, dataSource.getId());
                if (correlationDataSource == null) {
                    // Add the data source.
                    CorrelationDataSource.fromTSKDataSource(correlationCase, dataSource);
                } else {
                    // Sync the data source hash values if necessary.
                    if (dataSource instanceof Image) {
                        Image image = (Image) dataSource;

                        String imageMd5Hash = image.getMd5();
                        if (imageMd5Hash == null) {
                            imageMd5Hash = "";
                        }
                        String crMd5Hash = correlationDataSource.getMd5();
                        if (StringUtils.equals(imageMd5Hash, crMd5Hash) == false) {
                            correlationDataSource.setMd5(imageMd5Hash);
                        }

                        String imageSha1Hash = image.getSha1();
                        if (imageSha1Hash == null) {
                            imageSha1Hash = "";
                        }
                        String crSha1Hash = correlationDataSource.getSha1();
                        if (StringUtils.equals(imageSha1Hash, crSha1Hash) == false) {
                            correlationDataSource.setSha1(imageSha1Hash);
                        }

                        String imageSha256Hash = image.getSha256();
                        if (imageSha256Hash == null) {
                            imageSha256Hash = "";
                        }
                        String crSha256Hash = correlationDataSource.getSha256();
                        if (StringUtils.equals(imageSha256Hash, crSha256Hash) == false) {
                            correlationDataSource.setSha256(imageSha256Hash);
                        }
                    }
                }
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Unable to fetch data from the Central Repository for data source '%s' (obj_id=%d)",
                        dataSourceName, dataSourceObjectId), ex);
            } catch (NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, "No current case opened.", ex);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, String.format(
                        "Unable to fetch data from the case database for data source '%s' (obj_id=%d)",
                        dataSourceName, dataSourceObjectId), ex);
            }
        } // DATA_SOURCE_ANALYSIS_COMPLETED
    }

    private final class DataAddedTask implements Runnable {

        private final CentralRepository dbManager;
        private final PropertyChangeEvent event;
        private final boolean flagNotableItemsEnabled;
        private final boolean flagPreviousItemsEnabled;
        private final boolean createCorrelationAttributes;
        private final boolean flagUniqueItemsEnabled;

        private DataAddedTask(CentralRepository db, PropertyChangeEvent evt, boolean flagNotableItemsEnabled, boolean flagPreviousItemsEnabled, boolean createCorrelationAttributes, boolean flagUnique) {
            this.dbManager = db;
            this.event = evt;
            this.flagNotableItemsEnabled = flagNotableItemsEnabled;
            this.flagPreviousItemsEnabled = flagPreviousItemsEnabled;
            this.createCorrelationAttributes = createCorrelationAttributes;
            this.flagUniqueItemsEnabled = flagUnique;
        }

        @Override
        public void run() {
            if (!CentralRepository.isEnabled()) {
                return;
            }
            final ModuleDataEvent mde = (ModuleDataEvent) event.getOldValue();
            Collection<BlackboardArtifact> bbArtifacts = mde.getArtifacts();
            if (null == bbArtifacts) { //the ModuleDataEvents don't always have a collection of artifacts set
                return;
            }
            List<CorrelationAttributeInstance> eamArtifacts = new ArrayList<>();

            for (BlackboardArtifact bbArtifact : bbArtifacts) {
                // makeCorrAttrToSave will filter out artifacts which should not be sources of CR data.
                List<CorrelationAttributeInstance> convertedArtifacts = new ArrayList<>();
                if (bbArtifact instanceof DataArtifact){
                    convertedArtifacts.addAll(CorrelationAttributeUtil.makeCorrAttrsToSave((DataArtifact)bbArtifact));
                }                 
                for (CorrelationAttributeInstance eamArtifact : convertedArtifacts) {
                    try {
                        // Only do something with this artifact if it's unique within the job
                        if (recentlyAddedCeArtifacts.add(eamArtifact.toString())) {
                            
                            // Get a list of instances for a given value (hash, email, etc.)
                            List<CorrelationAttributeInstance> previousOccurrences = new ArrayList<>();
                            // check if we are flagging things
                            if (flagNotableItemsEnabled || flagPreviousItemsEnabled || flagUniqueItemsEnabled) {
                                try {
                                    previousOccurrences = dbManager.getArtifactInstancesByTypeValue(eamArtifact.getCorrelationType(), eamArtifact.getCorrelationValue());

                                    // make sure the previous instances do not contain current case
                                    for (Iterator<CorrelationAttributeInstance> iterator = previousOccurrences.iterator(); iterator.hasNext();) {
                                        CorrelationAttributeInstance instance = iterator.next();
                                        if (instance.getCorrelationCase().getCaseUUID().equals(eamArtifact.getCorrelationCase().getCaseUUID())) {
                                            // this is the current case - remove the instace from the previousOccurrences list
                                            iterator.remove();
                                        }
                                    }
                                } catch (CorrelationAttributeNormalizationException ex) {
                                    LOGGER.log(Level.INFO, String.format("Unable to flag previously seen device: %s.", eamArtifact.toString()), ex);
                                }
                            }

                            // Was it previously marked as bad?
                            // query db for artifact instances having this TYPE/VALUE and knownStatus = "Bad".
                            // if getKnownStatus() is "Unknown" and this artifact instance was marked bad in a previous case, 
                            // create TSK_PREVIOUSLY_SEEN artifact on BB.
                            if (flagNotableItemsEnabled) {
                                List<String> caseDisplayNames = getCaseDisplayNamesForNotable(previousOccurrences);
                                if (!caseDisplayNames.isEmpty()) {
                                    makeAndPostPreviousNotableArtifact(bbArtifact,
                                            caseDisplayNames, eamArtifact.getCorrelationType(), eamArtifact.getCorrelationValue());

                                    // if we have marked this artifact as notable, then skip the analysis of whether it was previously seen
                                    continue;
                                }
                            }
                            
                            // flag previously seen devices and communication accounts (emails, phones, etc)
                            if (flagPreviousItemsEnabled && !previousOccurrences.isEmpty()
                                    && (eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.USBID_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.ICCID_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.IMEI_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.IMSI_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.MAC_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.EMAIL_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.PHONE_TYPE_ID)) {

                                List<String> caseDisplayNames = getCaseDisplayNames(previousOccurrences);
                                makeAndPostPreviousSeenArtifact(bbArtifact, caseDisplayNames, eamArtifact.getCorrelationType(), eamArtifact.getCorrelationValue());
                            }
                            
                            // flag previously unseen apps and domains
                            if (flagUniqueItemsEnabled
                                    && (eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.INSTALLED_PROGS_TYPE_ID
                                    || eamArtifact.getCorrelationType().getId() == CorrelationAttributeInstance.DOMAIN_TYPE_ID)) {
                                
                                if (previousOccurrences.isEmpty()) {
                                    makeAndPostPreviouslyUnseenArtifact(bbArtifact, eamArtifact.getCorrelationType(), eamArtifact.getCorrelationValue());
                                }
                            }
                            if (createCorrelationAttributes) {
                                eamArtifacts.add(eamArtifact);
                            }
                        }
                    } catch (CentralRepoException ex) {
                        LOGGER.log(Level.SEVERE, "Error counting notable artifacts.", ex);
                    }
                }
            }
            if (FALSE == eamArtifacts.isEmpty()) {
                for (CorrelationAttributeInstance eamArtifact : eamArtifacts) {
                    try {
                        dbManager.addArtifactInstance(eamArtifact);
                    } catch (CentralRepoException ex) {
                        LOGGER.log(Level.SEVERE, "Error adding artifact to database.", ex); //NON-NLS
                    }
                }
            } // DATA_ADDED
        }
    }
    
    /**
     * Gets case display names for a list of CorrelationAttributeInstance.
     *
     * @param occurrences List of CorrelationAttributeInstance
     *
     * @return List of case display names
     */
    private List<String> getCaseDisplayNames(List<CorrelationAttributeInstance> occurrences) {
        List<String> caseNames = new ArrayList<>();
        for (CorrelationAttributeInstance occurrence : occurrences) {
            caseNames.add(occurrence.getCorrelationCase().getDisplayName());
        }
        return caseNames;
    }

    /**
     * Gets case display names for only occurrences marked as NOTABLE/BAD.
     *
     * @param occurrences List of CorrelationAttributeInstance
     *
     * @return List of case display names of NOTABLE/BAD occurrences
     */
    private List<String> getCaseDisplayNamesForNotable(List<CorrelationAttributeInstance> occurrences) {
        List<String> caseNames = new ArrayList<>();
        for (CorrelationAttributeInstance occurrence : occurrences) {
            if (occurrence.getKnownStatus() == TskData.FileKnown.BAD) {
                caseNames.add(occurrence.getCorrelationCase().getDisplayName());
            }
        }
        return caseNames;
    } 
}
