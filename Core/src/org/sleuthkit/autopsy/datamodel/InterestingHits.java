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
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.datamodel.Artifacts.UpdatableCountTypeNode;
import org.sleuthkit.datamodel.AnalysisResult;

public class InterestingHits implements AutopsyVisitableItem {

    private static final String INTERESTING_ITEMS = NbBundle
            .getMessage(InterestingHits.class, "InterestingHits.interestingItems.text");
    private static final String DISPLAY_NAME = NbBundle.getMessage(InterestingHits.class, "InterestingHits.displayName.text");
    private static final Logger logger = Logger.getLogger(InterestingHits.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestModuleEvent.DATA_ADDED);
    private SleuthkitCase skCase;
    private final InterestingResults interestingResults = new InterestingResults();
    private final long filteringDSObjId; // 0 if not filtering/grouping by data source

    /**
     * Constructor
     *
     * @param skCase Case DB
     *
     */
    public InterestingHits(SleuthkitCase skCase) {
        this(skCase, 0);
    }

    /**
     * Constructor
     *
     * @param skCase Case DB
     * @param objId  Object id of the data source
     *
     */
    public InterestingHits(SleuthkitCase skCase, long objId) {
        this.skCase = skCase;
        this.filteringDSObjId = objId;
        interestingResults.update();
    }

    private class InterestingResults {

        // NOTE: the map can be accessed by multiple worker threads and needs to be synchronized
        private final Map<String, Map<String, Set<Long>>> interestingItemsMap = new LinkedHashMap<>();
        
        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

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
        
        public List<String> getSetNames() {
            List<String> setNames;
            synchronized (interestingItemsMap) {
                setNames = new ArrayList<>(interestingItemsMap.keySet());
            }
            Collections.sort(setNames);
            return setNames;
        }

        public Set<Long> getArtifactIds(String setName, String typeName) {
            synchronized (interestingItemsMap) {
                return interestingItemsMap.get(setName).get(typeName);
            }
        }

        public void update() {
            synchronized (interestingItemsMap) {
                interestingItemsMap.clear();
            }
            loadArtifacts(BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT);
            loadArtifacts(BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT);
            pcs.firePropertyChange(InterestingResults.class.getSimpleName(), null, interestingItemsMap);
        }

        /*
         * Reads the artifacts of specified type, grouped by Set, and loads into
         * the interestingItemsMap
         */
        @SuppressWarnings("deprecation")
        private void loadArtifacts(BlackboardArtifact.Type artType) {
            if (skCase == null) {
                return;
            }

            int setNameId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID();
            int artId = artType.getTypeID();
            String query = "SELECT value_text,blackboard_artifacts.artifact_obj_id,attribute_type_id " //NON-NLS
                    + "FROM blackboard_attributes,blackboard_artifacts WHERE " //NON-NLS
                    + "attribute_type_id=" + setNameId //NON-NLS
                    + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id" //NON-NLS
                    + " AND blackboard_artifacts.artifact_type_id=" + artId; //NON-NLS
            if (filteringDSObjId > 0) {
                query += "  AND blackboard_artifacts.data_source_obj_id = " + filteringDSObjId;
            }

            try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                synchronized (interestingItemsMap) {
                    ResultSet resultSet = dbQuery.getResultSet();
                    while (resultSet.next()) {
                        String value = resultSet.getString("value_text"); //NON-NLS
                        long artifactObjId = resultSet.getLong("artifact_obj_id"); //NON-NLS
                        if (!interestingItemsMap.containsKey(value)) {
                            interestingItemsMap.put(value, new LinkedHashMap<>());
                            interestingItemsMap.get(value).put(BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT.getDisplayName(), new HashSet<>());
                            interestingItemsMap.get(value).put(BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT.getDisplayName(), new HashSet<>());
                        }
                        interestingItemsMap.get(value).get(artType.getDisplayName()).add(artifactObjId);
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "SQL Exception occurred: ", ex); //NON-NLS
            }
        }
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Node for the interesting items
     */
    public class RootNode extends UpdatableCountTypeNode {

        public RootNode() {
            super(Children.create(new SetNameFactory(), true),
                    Lookups.singleton(DISPLAY_NAME),
                    DISPLAY_NAME,
                    filteringDSObjId,
                    BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT,
                    BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT);
            super.setName(INTERESTING_ITEMS);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
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

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.desc"),
                    getName()));

            return sheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    private class SetNameFactory extends ChildFactory.Detachable<String> {

        /*
         * This should probably be in the top-level class, but the factory has
         * nice methods for its startup and shutdown, so it seemed like a
         * cleaner place to register the property change listener.
         */
        private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
            String eventType = evt.getPropertyName();
            if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
                /**
                 * Checking for a current case is a stop gap measure until a
                 * different way of handling the closing of cases is worked out.
                 * Currently, remote events may be received for a case that is
                 * already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    /**
                     * Even with the check above, it is still possible that the
                     * case will be closed in a different thread before this
                     * code executes. If that happens, it is possible for the
                     * event to have a null oldValue.
                     */
                    ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                    if (null != eventData && (eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()
                            || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT.getTypeID())) {
                        interestingResults.update();
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
                 * different way of handling the closing of cases is worked out.
                 * Currently, remote events may be received for a case that is
                 * already closed.
                 */
                try {
                    Case.getCurrentCaseThrows();
                    interestingResults.update();
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
        };

        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

        private final PropertyChangeListener interestingResultsWeakPcl = WeakListeners.propertyChange((pce) -> refresh(true), null);

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
            IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
            interestingResults.addListener(interestingResultsWeakPcl);
            interestingResults.update();
        }

        @Override
        protected void finalize() throws Throwable {
            IngestManager.getInstance().removeIngestJobEventListener(weakPcl);
            IngestManager.getInstance().removeIngestModuleEventListener(weakPcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
            interestingResults.removeListener(interestingResultsWeakPcl);
            super.finalize();
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(interestingResults.getSetNames());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new SetNameNode(key);
        }
    }

    public class SetNameNode extends DisplayableItemNode {

        private final PropertyChangeListener interestingResultsWeakPcl = WeakListeners.propertyChange((pce) -> updateDisplayName(), null);

        private final String setName;

        public SetNameNode(String setName) {//, Set<Long> children) {
            super(Children.create(new HitTypeFactory(setName), true), Lookups.singleton(setName));
            this.setName = setName;
            super.setName(setName);
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
            interestingResults.addListener(interestingResultsWeakPcl);
        }

        private void updateDisplayName() {
            int sizeOfSet = interestingResults.getArtifactIds(setName, BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT.getDisplayName()).size()
                    + interestingResults.getArtifactIds(setName, BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT.getDisplayName()).size();
            super.setDisplayName(setName + " (" + sizeOfSet + ")");
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }

            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.desc"),
                    getName()));

            return sheet;
        }

        @Override
        public String getItemType() {
            /**
             * For custom settings for each rule set, return
             * getClass().getName() + setName instead.
             */
            return getClass().getName();
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        protected void finalize() throws Throwable {
            interestingResults.removeListener(interestingResultsWeakPcl);
            super.finalize();
        }

    }

    private class HitTypeFactory extends ChildFactory<String> {

        private final PropertyChangeListener interestingResultsWeakPcl = WeakListeners.propertyChange((pce) -> refresh(true), null);

        private final String setName;

        private HitTypeFactory(String setName) {
            super();
            this.setName = setName;
            interestingResults.addListener(interestingResultsWeakPcl);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.add(BlackboardArtifact.Type.TSK_INTERESTING_FILE_HIT.getDisplayName());
            list.add(BlackboardArtifact.Type.TSK_INTERESTING_ARTIFACT_HIT.getDisplayName());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new InterestingItemTypeNode(setName, key);
        }

        @Override
        protected void finalize() throws Throwable {
            interestingResults.removeListener(interestingResultsWeakPcl);
            super.finalize();
        }
    }

    public class InterestingItemTypeNode extends DisplayableItemNode {

        private final PropertyChangeListener interestingResultsWeakPcl = WeakListeners.propertyChange((pce) -> updateDisplayName(), null);

        private final String typeName;
        private final String setName;

        private InterestingItemTypeNode(String setName, String typeName) {
            super(Children.create(new HitFactory(setName, typeName), true), Lookups.singleton(setName));
            this.typeName = typeName;
            this.setName = setName;
            /**
             * We use the combination of setName and typeName as the name of the
             * node to ensure that nodes have a unique name. This comes into
             * play when associating paging state with the node.
             */
            super.setName(setName + "_" + typeName);
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
            interestingResults.addListener(interestingResultsWeakPcl);
        }

        private void updateDisplayName() {
            super.setDisplayName(typeName + " (" + interestingResults.getArtifactIds(setName, typeName).size() + ")");
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
            sheetSet.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.desc"),
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
             * For custom settings for each rule set, return
             * getClass().getName() + setName instead.
             */
            return getClass().getName();
        }

        @Override
        protected void finalize() throws Throwable {
            interestingResults.removeListener(interestingResultsWeakPcl);
            super.finalize();
        }
    }

    private class HitFactory extends BaseChildFactory<AnalysisResult> {

        private final PropertyChangeListener interestingResultsWeakPcl = WeakListeners.propertyChange((pce) -> refresh(true), null);

        private final String setName;
        private final String typeName;
        private final Map<Long, AnalysisResult> artifactHits = new HashMap<>();

        private HitFactory(String setName, String typeName) {
            /**
             * The node name passed to the parent constructor must be the same
             * as the name set in the InterestingItemTypeNode constructor, i.e.
             * setName underscore typeName
             */
            super(setName + "_" + typeName);
            this.setName = setName;
            this.typeName = typeName;
            interestingResults.addListener(interestingResultsWeakPcl);
        }

        @Override
        protected List<AnalysisResult> makeKeys() {

            if (skCase != null) {
                interestingResults.getArtifactIds(setName, typeName).forEach((id) -> {
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

        @Override
        protected Node createNodeForKey(AnalysisResult art) {
            return new BlackboardArtifactNode(art);
        }

        @Override
        protected void onAdd() {
            // No-op
        }

        @Override
        protected void onRemove() {
            // No-op
        }

        @Override
        protected void finalize() throws Throwable {
            interestingResults.removeListener(interestingResultsWeakPcl);
            super.finalize();
        }
    }
}
