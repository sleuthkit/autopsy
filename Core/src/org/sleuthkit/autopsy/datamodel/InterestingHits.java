/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

public class InterestingHits implements AutopsyVisitableItem {

    private static final String INTERESTING_ITEMS = NbBundle
            .getMessage(InterestingHits.class, "InterestingHits.interestingItems.text");
    private static final String DISPLAY_NAME = NbBundle.getMessage(InterestingHits.class, "InterestingHits.displayName.text");
    private static final Logger logger = Logger.getLogger(InterestingHits.class.getName());
    private SleuthkitCase skCase;
    private final InterestingResults interestingResults = new InterestingResults();

    public InterestingHits(SleuthkitCase skCase) {
        this.skCase = skCase;
        interestingResults.update();
    }

    private class InterestingResults extends Observable {

        // NOTE: the map can be accessed by multiple worker threads and needs to be synchronized
        private final Map<String, Map<String, Set<Long>>> interestingItemsMap = new LinkedHashMap<>();

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
            loadArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
            loadArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
            setChanged();
            notifyObservers();
        }

        /*
         * Reads the artifacts of specified type, grouped by Set, and loads into
         * the interestingItemsMap
         */
        @SuppressWarnings("deprecation")
        private void loadArtifacts(BlackboardArtifact.ARTIFACT_TYPE artType) {
            if (skCase == null) {
                return;
            }

            int setNameId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID();
            int artId = artType.getTypeID();
            String query = "SELECT value_text,blackboard_attributes.artifact_id,attribute_type_id " //NON-NLS
                    + "FROM blackboard_attributes,blackboard_artifacts WHERE " //NON-NLS
                    + "attribute_type_id=" + setNameId //NON-NLS
                    + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id" //NON-NLS
                    + " AND blackboard_artifacts.artifact_type_id=" + artId; //NON-NLS

            try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                synchronized (interestingItemsMap) {
                    ResultSet resultSet = dbQuery.getResultSet();
                    while (resultSet.next()) {
                        String value = resultSet.getString("value_text"); //NON-NLS
                        long artifactId = resultSet.getLong("artifact_id"); //NON-NLS
                        if (!interestingItemsMap.containsKey(value)) {
                            interestingItemsMap.put(value, new LinkedHashMap<>());
                            interestingItemsMap.get(value).put(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getDisplayName(), new HashSet<>());
                            interestingItemsMap.get(value).put(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getDisplayName(), new HashSet<>());
                        }
                        interestingItemsMap.get(value).get(artType.getDisplayName()).add(artifactId);
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "SQL Exception occurred: ", ex); //NON-NLS
            }
        }
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    /**
     * Node for the interesting items
     */
    public class RootNode extends DisplayableItemNode {

        public RootNode() {
            super(Children.create(new SetNameFactory(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(INTERESTING_ITEMS);
            super.setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.desc"),
                    getName()));

            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

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
                 * different way of handling the closing of cases is worked
                 * out. Currently, remote events may be received for a case
                 * that is already closed.
                 */
                try {
                    Case.getOpenCase();
                    /**
                     * Even with the check above, it is still possible that
                     * the case will be closed in a different thread before
                     * this code executes. If that happens, it is possible
                     * for the event to have a null oldValue.
                     */
                    ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                    if (null != eventData && (eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID()
                            || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID())) {
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
                 * different way of handling the closing of cases is worked
                 * out. Currently, remote events may be received for a case
                 * that is already closed.
                 */
                try {
                    Case.getOpenCase();
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

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            interestingResults.update();
            interestingResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            interestingResults.deleteObserver(this);
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

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    public class SetNameNode extends DisplayableItemNode implements Observer {

        private final String setName;

        public SetNameNode(String setName) {//, Set<Long> children) {
            super(Children.create(new HitTypeFactory(setName), true), Lookups.singleton(setName));
            this.setName = setName;
            super.setName(setName);
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
            interestingResults.addObserver(this);
        }

        private void updateDisplayName() {
            int sizeOfSet = interestingResults.getArtifactIds(setName, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getDisplayName()).size()
                    + interestingResults.getArtifactIds(setName, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getDisplayName()).size();
            super.setDisplayName(setName + " (" + sizeOfSet + ")");
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.desc"),
                    getName()));

            return s;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
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

    private class HitTypeFactory extends ChildFactory<String> implements Observer {

        private final String setName;
        private final Map<Long, BlackboardArtifact> artifactHits = new HashMap<>();

        private HitTypeFactory(String setName) {
            super();
            this.setName = setName;
            interestingResults.addObserver(this);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getDisplayName());
            list.add(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getDisplayName());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new InterestingItemTypeNode(setName, key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    public class InterestingItemTypeNode extends DisplayableItemNode implements Observer {

        private final String typeName;
        private final String setName;

        private InterestingItemTypeNode(String setName, String typeName) {
            super(Children.create(new HitFactory(setName, typeName), true), Lookups.singleton(setName));
            this.typeName = typeName;
            this.setName = setName;
            super.setName(typeName);
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png"); //NON-NLS
            interestingResults.addObserver(this);
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
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "InterestingHits.createSheet.name.desc"),
                    getName()));
            return s;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
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

    private class HitFactory extends ChildFactory<Long> implements Observer {

        private final String setName;
        private final String typeName;
        private final Map<Long, BlackboardArtifact> artifactHits = new HashMap<>();

        private HitFactory(String setName, String typeName) {
            super();
            this.setName = setName;
            this.typeName = typeName;
            interestingResults.addObserver(this);
        }

        @Override
        protected boolean createKeys(List<Long> list) {

            if (skCase == null) {
                return true;
            }

            interestingResults.getArtifactIds(setName, typeName).forEach((id) -> {
                try {
                    if (!artifactHits.containsKey(id)) {
                        BlackboardArtifact art = skCase.getBlackboardArtifact(id);
                        artifactHits.put(id, art);  
                    }
                    list.add(id);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "TSK Exception occurred", ex); //NON-NLS
                }
            });
            return true;
        }

        @Override
        protected Node createNodeForKey(Long l) {
            BlackboardArtifact art = artifactHits.get(l);
            return (null == art) ? null : new BlackboardArtifactNode(art);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }
}
