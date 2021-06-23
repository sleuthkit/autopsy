/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.Type.TSK_HASHSET_HIT;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.datamodel.Artifacts.UpdatableCountTypeNode;
import org.sleuthkit.datamodel.AnalysisResult;

/**
 * Hash set hits node support. Inner classes have all of the nodes in the tree.
 */
public class HashsetHits implements AutopsyVisitableItem {

    private static final String HASHSET_HITS = BlackboardArtifact.Type.TSK_HASHSET_HIT.getTypeName();
    private static final String DISPLAY_NAME = BlackboardArtifact.Type.TSK_HASHSET_HIT.getDisplayName();
    private static final Logger logger = Logger.getLogger(HashsetHits.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestModuleEvent.DATA_ADDED);
    private SleuthkitCase skCase;
    private final HashsetResults hashsetResults;
    private final long filteringDSObjId; // 0 if not filtering/grouping by data source

    /**
     * Constructor
     *
     * @param skCase Case DB
     *
     */
    public HashsetHits(SleuthkitCase skCase) {
        this(skCase, 0);
    }

    /**
     * Constructor
     *
     * @param skCase Case DB
     * @param objId  Object id of the data source
     *
     */
    public HashsetHits(SleuthkitCase skCase, long objId) {
        this.skCase = skCase;
        this.filteringDSObjId = objId;
        hashsetResults = new HashsetResults();
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Stores all of the hashset results in a single class that is observable
     * for the child nodes
     */
    private class HashsetResults {

        // maps hashset name to list of artifacts for that set
        // NOTE: the map can be accessed by multiple worker threads and needs to be synchronized
        private final Map<String, Set<Long>> hashSetHitsMap = new LinkedHashMap<>();

        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        
        HashsetResults() {
            update();
        }

        /**
         * Adds a property change listener listening for changes in data.
         * @param pcl The property change listener to be subscribed.
         */
        void addListener(PropertyChangeListener pcl) {
            pcs.addPropertyChangeListener(pcl);
        }

        /**
         * Removes a property change listener listening for changes in data.
         * @param pcl The property change listener to be removed from subscription.
         */        
        void removeListener(PropertyChangeListener pcl) {
            pcs.removePropertyChangeListener(pcl);
        }

        List<String> getSetNames() {
            List<String> names;
            synchronized (hashSetHitsMap) {
                names = new ArrayList<>(hashSetHitsMap.keySet());
            }
            Collections.sort(names);
            return names;
        }

        Set<Long> getArtifactIds(String hashSetName) {
            synchronized (hashSetHitsMap) {
                return hashSetHitsMap.get(hashSetName);
            }
        }

        @SuppressWarnings("deprecation")
        final void update() {
            synchronized (hashSetHitsMap) {
                hashSetHitsMap.clear();
            }

            if (skCase == null) {
                return;
            }

            int setNameId = ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID();
            int artId = TSK_HASHSET_HIT.getTypeID();
            String query = "SELECT value_text,blackboard_artifacts.artifact_obj_id,attribute_type_id " //NON-NLS
                    + "FROM blackboard_attributes,blackboard_artifacts WHERE " //NON-NLS
                    + "attribute_type_id=" + setNameId //NON-NLS
                    + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id" //NON-NLS
                    + " AND blackboard_artifacts.artifact_type_id=" + artId; //NON-NLS
            if (filteringDSObjId > 0) {
                query += "  AND blackboard_artifacts.data_source_obj_id = " + filteringDSObjId;
            }

            try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                synchronized (hashSetHitsMap) {
                    while (resultSet.next()) {
                        String setName = resultSet.getString("value_text"); //NON-NLS
                        long artifactObjId = resultSet.getLong("artifact_obj_id"); //NON-NLS
                        if (!hashSetHitsMap.containsKey(setName)) {
                            hashSetHitsMap.put(setName, new HashSet<>());
                        }
                        hashSetHitsMap.get(setName).add(artifactObjId);
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "SQL Exception occurred: ", ex); //NON-NLS
            }

            pcs.firePropertyChange(HashsetResults.class.getSimpleName(), null, hashSetHitsMap);
        }
    }

    /**
     * Top-level node for all hash sets
     */
    public class RootNode extends UpdatableCountTypeNode {

        public RootNode() {
            super(Children.create(new HashsetNameFactory(), true),
                    Lookups.singleton(DISPLAY_NAME),
                    DISPLAY_NAME,
                    filteringDSObjId,
                    TSK_HASHSET_HIT);

            super.setName(HASHSET_HITS);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hashset_hits.png"); //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.desc"),
                    getName()));

            return sheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Creates child nodes for each hashset name
     */
    private class HashsetNameFactory extends ChildFactory.Detachable<String> {

        /*
         * This should probably be in the HashsetHits class, but the factory has
         * nice methods for its startup and shutdown, so it seemed like a
         * cleaner place to register the property change listener.
         */
        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getCurrentCaseThrows();
                        /**
                         * Due to some unresolved issues with how cases are
                         * closed, it is possible for the event to have a null
                         * oldValue if the event is a remote event.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData && eventData.getBlackboardArtifactType().getTypeID() == TSK_HASHSET_HIT.getTypeID()) {
                            hashsetResults.update();
                        }
                    } catch (NoCurrentCaseException notUsed) {
                        /**
                         * Case is closed, do nothing.
                         */
                    }
                } else if (eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getCurrentCaseThrows();
                        hashsetResults.update();
                    } catch (NoCurrentCaseException notUsed) {
                        /**
                         * Case is closed, do nothing.
                         */
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that we don't get called with a stale case handle
                    if (evt.getNewValue() == null) {
                        removeNotify();
                        skCase = null;
                    }
                }
            }
        };
        
        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);
        private final PropertyChangeListener hashsetResultsWeakPcl = WeakListeners.propertyChange((pce) -> refresh(true), null);

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
            IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
            hashsetResults.addListener(hashsetResultsWeakPcl);
            hashsetResults.update();
        }

        @Override
        protected void finalize() throws Throwable {
            IngestManager.getInstance().removeIngestJobEventListener(weakPcl);
            IngestManager.getInstance().removeIngestModuleEventListener(weakPcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
            hashsetResults.removeListener(hashsetResultsWeakPcl);
            super.finalize();
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(hashsetResults.getSetNames());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new HashsetNameNode(key);
        }
    }

    /**
     * Node for a hash set name
     */
    public class HashsetNameNode extends DisplayableItemNode {

        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange((pce) -> updateDisplayName(), null);
        private final String hashSetName;

        public HashsetNameNode(String hashSetName) {
            super(Children.create(new HitFactory(hashSetName), true), Lookups.singleton(hashSetName));
            super.setName(hashSetName);
            this.hashSetName = hashSetName;
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hashset_hits.png"); //NON-NLS
            hashsetResults.addListener(weakPcl);
        }

        /**
         * Update the count in the display name
         */
        private void updateDisplayName() {
            super.setDisplayName(hashSetName + " (" + hashsetResults.getArtifactIds(hashSetName).size() + ")");
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.desc"),
                    getName()));

            return sheet;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public String getItemType() {
            /**
             * For custom settings for each hash set, return
             * getClass().getName() + hashSetName instead.
             */
            return getClass().getName();
        }
        
        @Override
        protected void finalize() throws Throwable {
            hashsetResults.removeListener(weakPcl);
            super.finalize();
        } 
    }

    /**
     * Creates the nodes for the hits in a given set.
     */
    private class HitFactory extends BaseChildFactory<AnalysisResult> {

        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange((pce) -> refresh(true), null);
        private final String hashsetName;
        private final Map<Long, AnalysisResult> artifactHits = new HashMap<>();

        private HitFactory(String hashsetName) {
            super(hashsetName);
            this.hashsetName = hashsetName;
        }

        @Override
        protected void onAdd() {
            hashsetResults.addListener(weakPcl);
        }

        @Override
        protected void onRemove() {
            hashsetResults.removeListener(weakPcl);
        }

        @Override
        protected Node createNodeForKey(AnalysisResult key) {
            return new BlackboardArtifactNode(key);
        }

        @Override
        protected List<AnalysisResult> makeKeys() {
            if (skCase != null) {

                hashsetResults.getArtifactIds(hashsetName).forEach((id) -> {
                    try {
                        if (!artifactHits.containsKey(id)) {
                            AnalysisResult art = skCase.getBlackboard().getAnalysisResultById(id);
                            //Cache attributes while we are off the EDT.
                            //See JIRA-5969
                            art.getAttributes();
                            artifactHits.put(id, art);
                        }
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, "TSK Exception occurred", ex); //NON-NLS
                    }
                });
                return new ArrayList<>(artifactHits.values());
            }
            return Collections.emptyList();
        }
    }
}
