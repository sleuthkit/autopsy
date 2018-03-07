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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.coreutils.ThreadUtils;

/**
 * Listen for ingest events and update entries in the Central Repository
 * database accordingly
 */
public class IngestEventsListener {

    private static final Logger LOGGER = Logger.getLogger(CorrelationAttribute.class.getName());

    final Collection<String> recentlyAddedCeArtifacts = new LinkedHashSet<>();
    private static int ceModuleInstanceCount = 0;
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
     * Enable this IngestEventsListener to add contents to the Correlation
     * Engine.
     *
     */
    public synchronized static void incrementCorrelationEngineModuleCount() {
        ceModuleInstanceCount++;  //Should be called once in the Correlation Engine module's startup method.
    }

    /**
     * Disable this IngestEventsListener from adding contents to the Correlation
     * Engine.
     */
    public synchronized static void decrementCorrelationEngineModuleCount() {
        if (getCeModuleInstanceCount() > 0) {  //prevent it ingestJobCounter from going negative
            ceModuleInstanceCount--;  //Should be called once in the Correlation Engine module's shutdown method.
        }
    }

    /**
     * Reset the counter which keeps track of if the Correlation Engine Module
     * is being run during injest to 0.
     */
    synchronized static void resetCeModuleInstanceCount() {
        ceModuleInstanceCount = 0;  //called when a case is opened in case for some reason counter was not reset
    }

    /**
     * Wether or not the Correlation Engine Module is enabled for any of the
     * currently running ingest jobs.
     *
     * @return boolean True for Correlation Engine enabled, False for disabled
     */
    private synchronized static int getCeModuleInstanceCount() {
        return ceModuleInstanceCount;
    }

    @NbBundle.Messages({"IngestEventsListener.prevTaggedSet.text=Previously Tagged As Notable (Central Repository)",
        "IngestEventsListener.prevCaseComment.text=Previous Case: ",
        "IngestEventsListener.ingestmodule.name=Correlation Engine"})
    static private void postCorrelatedBadArtifactToBlackboard(BlackboardArtifact bbArtifact, List<String> caseDisplayNames) {

        try {
            AbstractFile af = bbArtifact.getSleuthkitCase().getAbstractFileById(bbArtifact.getObjectID());
            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            String MODULE_NAME = Bundle.IngestEventsListener_ingestmodule_name();
            BlackboardArtifact tifArtifact = af.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
            BlackboardAttribute att = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME,
                    Bundle.IngestEventsListener_prevTaggedSet_text());
            BlackboardAttribute att2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME,
                    Bundle.IngestEventsListener_prevCaseComment_text() + caseDisplayNames.stream().distinct().collect(Collectors.joining(",", "", "")));
            attributes.add(att);
            attributes.add(att2);
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, bbArtifact.getArtifactID()));

            tifArtifact.addAttributes(attributes);
            try {
                // index the artifact for keyword search
                Blackboard blackboard = Case.getOpenCase().getServices().getBlackboard();
                blackboard.indexArtifact(tifArtifact);
            } catch (Blackboard.BlackboardException | NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, "Unable to index blackboard artifact " + tifArtifact.getArtifactID(), ex); //NON-NLS
            }

            // fire event to notify UI of this new artifact
            IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT));
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create BlackboardArtifact.", ex); // NON-NLS
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create BlackboardAttribute.", ex); // NON-NLS
        }
    }

    private class IngestModuleEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (getCeModuleInstanceCount() > 0) {
                EamDb dbManager;
                try {
                    dbManager = EamDb.getInstance();
                } catch (EamDbException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to connect to Central Repository database.", ex);
                    return;
                }
                switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
                    case DATA_ADDED: {
                        jobProcessingExecutor.submit(new DataAddedTask(dbManager, evt));
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

        private DataAddedTask(EamDb db, PropertyChangeEvent evt) {
            dbManager = db;
            event = evt;
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
            List<CorrelationAttribute> eamArtifacts = new ArrayList<>();

            for (BlackboardArtifact bbArtifact : bbArtifacts) {
                // eamArtifact will be null OR a EamArtifact containing one EamArtifactInstance.
                List<CorrelationAttribute> convertedArtifacts = EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(bbArtifact, true, true);
                for (CorrelationAttribute eamArtifact : convertedArtifacts) {
                    try {
                        // Only do something with this artifact if it's unique within the job
                        if (recentlyAddedCeArtifacts.add(eamArtifact.toString())) {
                            // Was it previously marked as bad?
                            // query db for artifact instances having this TYPE/VALUE and knownStatus = "Bad".
                            // if gettKnownStatus() is "Unknown" and this artifact instance was marked bad in a previous case, 
                            // create TSK_INTERESTING_ARTIFACT_HIT artifact on BB.
                            List<String> caseDisplayNames = dbManager.getListCasesHavingArtifactInstancesKnownBad(eamArtifact.getCorrelationType(), eamArtifact.getCorrelationValue());
                            if (!caseDisplayNames.isEmpty()) {
                                postCorrelatedBadArtifactToBlackboard(bbArtifact,
                                        caseDisplayNames);
                            }
                            eamArtifacts.add(eamArtifact);
                        }
                    } catch (EamDbException ex) {
                        LOGGER.log(Level.SEVERE, "Error counting notable artifacts.", ex);
                    }
                }
            }
            if (FALSE == eamArtifacts.isEmpty()) {
                try {
                    for (CorrelationAttribute eamArtifact : eamArtifacts) {
                        dbManager.addArtifact(eamArtifact);
                    }
                } catch (EamDbException ex) {
                    LOGGER.log(Level.SEVERE, "Error connecting to Central Repository database.", ex); //NON-NLS
                }
            } // DATA_ADDED
        }
    }
}
