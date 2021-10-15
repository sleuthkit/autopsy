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
import java.util.Observable;
import java.util.Observer;
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
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.autopsy.datamodel.Artifacts.UpdatableCountTypeNode;

public class InterestingHits implements AutopsyVisitableItem {

    private static final Logger logger = Logger.getLogger(InterestingHits.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestModuleEvent.DATA_ADDED);

    private SleuthkitCase skCase;
    private final InterestingResults interestingResults = new InterestingResults();
    private final long filteringDSObjId; // 0 if not filtering/grouping by data source
    private final BlackboardArtifact.Type artifactType;

    /**
     * Constructor
     *
     * @param skCase       Case DB
     * @param artifactType The artifact type (either interesting file or
     *                     artifact).
     *
     */
    public InterestingHits(SleuthkitCase skCase, BlackboardArtifact.Type artifactType) {
        this(skCase, artifactType, 0);
    }

    /**
     * Constructor
     *
     * @param skCase       Case DB
     * @param artifactType The artifact type (either interesting file or
     *                     artifact).
     * @param objId        Object id of the data source
     *
     */
    public InterestingHits(SleuthkitCase skCase, BlackboardArtifact.Type artifactType, long objId) {
        this.skCase = skCase;
        this.artifactType = artifactType;
        this.filteringDSObjId = objId;
        interestingResults.update();
    }

    /**
     * Cache of result ids mapped by artifact type -> set name -> artifact id.
     */
    private class InterestingResults extends Observable {

        // NOTE: the map can be accessed by multiple worker threads and needs to be synchronized
        private final Map<String, Set<Long>> interestingItemsMap = new LinkedHashMap<>();

        /**
         * Returns all the set names for a given interesting item type.
         *
         * @param type The interesting item type.
         *
         * @return The set names.
         */
        List<String> getSetNames() {
            List<String> setNames;
            synchronized (interestingItemsMap) {
                setNames = new ArrayList<>(interestingItemsMap.keySet());
            }
            Collections.sort(setNames, (a, b) -> a.compareToIgnoreCase(b));
            return setNames;
        }

        /**
         * Returns all artifact ids belonging to the specified interesting item
         * type and set name.
         *
         * @param type    The interesting item type.
         * @param setName The set name.
         *
         * @return The artifact ids in that set name and type.
         */
        Set<Long> getArtifactIds(String setName) {
            synchronized (interestingItemsMap) {
                return new HashSet<>(interestingItemsMap.getOrDefault(setName, Collections.emptySet()));
            }
        }

        /**
         * Triggers a fetch from the database to update this cache.
         */
        void update() {
            synchronized (interestingItemsMap) {
                interestingItemsMap.clear();
            }
            loadArtifacts();
            setChanged();
            notifyObservers();
        }

        /*
         * Reads the artifacts of specified type, grouped by Set, and loads into
         * the interestingItemsMap
         */
        @SuppressWarnings("deprecation")
        private void loadArtifacts() {
            if (skCase == null) {
                return;
            }

            int setNameId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID();

            String query = "SELECT value_text, blackboard_artifacts.artifact_obj_id " //NON-NLS
                    + "FROM blackboard_attributes,blackboard_artifacts WHERE " //NON-NLS
                    + "attribute_type_id=" + setNameId //NON-NLS
                    + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id" //NON-NLS
                    + " AND blackboard_artifacts.artifact_type_id = " + artifactType.getTypeID(); //NON-NLS
            if (filteringDSObjId > 0) {
                query += "  AND blackboard_artifacts.data_source_obj_id = " + filteringDSObjId;
            }

            try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                synchronized (interestingItemsMap) {
                    ResultSet resultSet = dbQuery.getResultSet();
                    while (resultSet.next()) {
                        String value = resultSet.getString("value_text"); //NON-NLS
                        long artifactObjId = resultSet.getLong("artifact_obj_id"); //NON-NLS
                        interestingItemsMap
                                .computeIfAbsent(value, (k) -> new HashSet<>())
                                .add(artifactObjId);
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
     * Creates nodes for all sets for a specified interesting item type.
     */
    private class SetNameFactory extends ChildFactory.Detachable<String> implements Observer {

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
                    if (null != eventData && (eventData.getBlackboardArtifactType().getTypeID() == artifactType.getTypeID())) {
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

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(interestingResults.getSetNames());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new SetNameNode(key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
            IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, weakPcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
            interestingResults.addObserver(this);
            interestingResults.update();
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            IngestManager.getInstance().removeIngestJobEventListener(weakPcl);
            IngestManager.getInstance().removeIngestModuleEventListener(weakPcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), weakPcl);
            interestingResults.deleteObserver(this);
        }
    }

    /**
     * A node for a set to be displayed in the tree.
     */
    public class SetNameNode extends DisplayableItemNode implements Observer {

        private final String setName;

        public SetNameNode(String setName) {//, Set<Long> children) {
            super(Children.create(new HitFactory(setName), true), Lookups.singleton(setName));
            this.setName = setName;
            super.setName(setName);
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
            interestingResults.addObserver(this);
        }

        private void updateDisplayName() {
            int sizeOfSet = interestingResults.getArtifactIds(setName).size();
            super.setDisplayName(setName + " (" + sizeOfSet + ")");
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
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        @Override
        public String getItemType() {
            /**
             * For custom settings for each rule set, return
             * getClass().getName() + setName instead.
             */
            return getClass().getName();
        }
    }

    /**
     * Parent node for interesting item type that shows child set nodes.
     */
    public class RootNode extends UpdatableCountTypeNode {

        /**
         * Main constructor.
         */
        public RootNode() {
            super(Children.create(new SetNameFactory(), true),
                    Lookups.singleton(artifactType),
                    artifactType.getDisplayName(),
                    filteringDSObjId,
                    artifactType);

            /**
             * We use the combination of setName and typeName as the name of the
             * node to ensure that nodes have a unique name. This comes into
             * play when associating paging state with the node.
             */
            setName(artifactType.getDisplayName());
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
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
    }

    /**
     * Factory for creating individual interesting item BlackboardArtifactNodes.
     */
    private class HitFactory extends BaseChildFactory<AnalysisResult> implements Observer {

        private final String setName;
        private final Map<Long, AnalysisResult> artifactHits = new HashMap<>();

        /**
         * Main constructor.
         *
         * @param setName The set name of artifacts to be displayed.
         */
        private HitFactory(String setName) {
            /**
             * The node name passed to the parent constructor must be the same
             * as the name set in the InterestingItemTypeNode constructor, i.e.
             * setName underscore typeName
             */
            super(setName);
            this.setName = setName;
            interestingResults.addObserver(this);
        }

        @Override
        protected List<AnalysisResult> makeKeys() {

            if (skCase != null) {
                interestingResults.getArtifactIds(setName).forEach((id) -> {
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
        public void update(Observable o, Object arg) {
            refresh(true);
        }

        @Override
        protected void onAdd() {
            // No-op
        }

        @Override
        protected void onRemove() {
            // No-op
        }
    }
}
