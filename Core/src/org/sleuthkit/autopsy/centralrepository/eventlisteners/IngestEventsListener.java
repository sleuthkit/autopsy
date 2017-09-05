/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import static java.lang.Boolean.FALSE;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifact;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;

/**
 * Listen for ingest events and update entries in the Central Repository
 * database accordingly
 */
public class IngestEventsListener {

    private static final Logger LOGGER = Logger.getLogger(EamArtifact.class.getName());

    final Collection<String> addedCeArtifactTrackerSet = new LinkedHashSet<>();
    private static long ingestJobCounter = 0;
    private final PropertyChangeListener pcl1 = new IngestModuleEventListener();
    private final PropertyChangeListener pcl2 = new IngestJobEventListener();

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

    public synchronized static void enableCentralRepositoryModule() {
        ingestJobCounter++;
    }
    
    public synchronized static void disableCentralRepositoryModule() {
        ingestJobCounter--;
    }
    
    private synchronized static long getIngestJobCounter(){
        return ingestJobCounter;
    } 
    
    private class IngestModuleEventListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            
            if (getIngestJobCounter() > 0) {
                EamDb dbManager;
                try {
                    dbManager = EamDb.getInstance();
                } catch (EamDbException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to connect to Central Repository database.", ex);
                    return;
                }
                switch (IngestManager.IngestModuleEvent.valueOf(evt.getPropertyName())) {
                    case DATA_ADDED: {
                        if (!EamDb.isEnabled()) {
                            return;
                        }

                        final ModuleDataEvent mde = (ModuleDataEvent) evt.getOldValue();
                        Collection<BlackboardArtifact> bbArtifacts = mde.getArtifacts();
                        if (null == bbArtifacts) {
                            LOGGER.log(Level.WARNING, "Error getting artifacts from Module Data Event. getArtifacts() returned null.");
                            return;
                        }
                        List<EamArtifact> eamArtifacts = new ArrayList<>();
                        try {
                            for (BlackboardArtifact bbArtifact : bbArtifacts) {
                                // eamArtifact will be null OR a EamArtifact containing one EamArtifactInstance.
                                List<EamArtifact> convertedArtifacts = EamArtifactUtil.fromBlackboardArtifact(bbArtifact, true, dbManager.getCorrelationTypes(), true);
                                for (EamArtifact eamArtifact : convertedArtifacts) {
                                    try {
                                        // Only do something with this artifact if it's unique within the job
                                        if (addedCeArtifactTrackerSet.add(eamArtifact.toString())) {
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
                                        LOGGER.log(Level.SEVERE, "Error counting known bad artifacts.", ex);
                                    }
                                }
                            }
                        } catch (EamDbException ex) {
                            LOGGER.log(Level.SEVERE, "Error getting correlation types.", ex);
                        }
                        if (FALSE == eamArtifacts.isEmpty()) {
                            // send update to entperirse artifact manager db
                            Runnable r = new NewArtifactsRunner(eamArtifacts);
                            // TODO: send r into a thread pool instead
                            Thread t = new Thread(r);
                            t.start();
                        } // DATA_ADDED
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
                    // clear the tracker to reduce memory usage
                    // @@@ This isnt' entirely accurate to do here.  We could have multiple
                    // ingest jobs at the same time
                    addedCeArtifactTrackerSet.clear();

                } // DATA_SOURCE_ANALYSIS_COMPLETED
                break;
            }
        }
    }

    @NbBundle.Messages({"IngestEventsListener.prevcases.text=Previous Cases",
        "IngestEventsListener.ingestmodule.name=Correlation Engine"})
    private void postCorrelatedBadArtifactToBlackboard(BlackboardArtifact bbArtifact, List<String> caseDisplayNames) {

        try {
            AbstractFile af = bbArtifact.getSleuthkitCase().getAbstractFileById(bbArtifact.getObjectID());

            String MODULE_NAME = Bundle.IngestEventsListener_ingestmodule_name();
            BlackboardArtifact tifArtifact = af.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
            BlackboardAttribute att = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME,
                    Bundle.IngestEventsListener_prevcases_text());
            BlackboardAttribute att2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME,
                    "Previous Case: " + caseDisplayNames.stream().distinct().collect(Collectors.joining(",", "", "")));
            tifArtifact.addAttribute(att);
            tifArtifact.addAttribute(att2);
            tifArtifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, bbArtifact.getArtifactID()));

            try {
                // index the artifact for keyword search
                Blackboard blackboard = Case.getCurrentCase().getServices().getBlackboard();
                blackboard.indexArtifact(tifArtifact);
            } catch (Blackboard.BlackboardException ex) {
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
}
