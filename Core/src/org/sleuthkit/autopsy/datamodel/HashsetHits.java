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
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Hash set hits node support. Inner classes have all of the nodes in the tree.
 */
public class HashsetHits implements AutopsyVisitableItem {

    private static final String HASHSET_HITS = BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getLabel();
    private static final String DISPLAY_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName();
    private static final Logger logger = Logger.getLogger(HashsetHits.class.getName());
    private SleuthkitCase skCase;
    private final HashsetResults hashsetResults;

    public HashsetHits(SleuthkitCase skCase) {
        this.skCase = skCase;
        hashsetResults = new HashsetResults();
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    /**
     * Stores all of the hashset results in a single class that is observable
     * for the child nodes
     */
    private class HashsetResults extends Observable {

        // maps hashset name to list of artifacts for that set
        // NOTE: the map can be accessed by multiple worker threads and needs to be synchronized
        private final Map<String, Set<Long>> hashSetHitsMap = new LinkedHashMap<>();

        HashsetResults() {
            update();
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
            int artId = ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID();
            String query = "SELECT value_text,blackboard_attributes.artifact_id,attribute_type_id " //NON-NLS
                    + "FROM blackboard_attributes,blackboard_artifacts WHERE " //NON-NLS
                    + "attribute_type_id=" + setNameId //NON-NLS
                    + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id" //NON-NLS
                    + " AND blackboard_artifacts.artifact_type_id=" + artId; //NON-NLS

            try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                synchronized (hashSetHitsMap) {
                    while (resultSet.next()) {
                        String setName = resultSet.getString("value_text"); //NON-NLS
                        long artifactId = resultSet.getLong("artifact_id"); //NON-NLS
                        if (!hashSetHitsMap.containsKey(setName)) {
                            hashSetHitsMap.put(setName, new HashSet<Long>());
                        }
                        hashSetHitsMap.get(setName).add(artifactId);
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "SQL Exception occurred: ", ex); //NON-NLS
            }

            setChanged();
            notifyObservers();
        }
    }

    /**
     * Top-level node for all hash sets
     */
    public class RootNode extends DisplayableItemNode {

        public RootNode() {
            super(Children.create(new HashsetNameFactory(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(HASHSET_HITS);
            super.setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hashset_hits.png"); //NON-NLS
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

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.desc"),
                    getName()));

            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Creates child nodes for each hashset name
     */
    private class HashsetNameFactory extends ChildFactory.Detachable<String> implements Observer {

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
                        Case.getOpenCase();
                        /**
                         * Due to some unresolved issues with how cases are
                         * closed, it is possible for the event to have a null
                         * oldValue if the event is a remote event.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData && eventData.getBlackboardArtifactType().getTypeID() == ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
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
                        Case.getOpenCase();
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

        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(pcl);
            IngestManager.getInstance().addIngestModuleEventListener(pcl);
            Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            hashsetResults.update();
            hashsetResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            hashsetResults.deleteObserver(this);
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

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    /**
     * Node for a hash set name
     */
    public class HashsetNameNode extends DisplayableItemNode implements Observer {

        private final String hashSetName;

        public HashsetNameNode(String hashSetName) {
            super(Children.create(new HitFactory(hashSetName), true), Lookups.singleton(hashSetName));
            super.setName(hashSetName);
            this.hashSetName = hashSetName;
            updateDisplayName();
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hashset_hits.png"); //NON-NLS
            hashsetResults.addObserver(this);
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
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "HashsetHits.createSheet.name.desc"),
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
             * For custom settings for each hash set, return
             * getClass().getName() + hashSetName instead.
             */
            return getClass().getName();
        }
    }

    /**
     * Creates the nodes for the hits in a given set.
     */
    private class HitFactory extends ChildFactory.Detachable<Long> implements Observer {

        private String hashsetName;
        private Map<Long, BlackboardArtifact> artifactHits = new HashMap<>();
 
        private HitFactory(String hashsetName) {
            super();
            this.hashsetName = hashsetName;
        }

        @Override
        protected void addNotify() {
            hashsetResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            hashsetResults.deleteObserver(this);
        }

        @Override
        protected boolean createKeys(List<Long> list) {
 
            if (skCase == null) {
               return true;
            }
            
            hashsetResults.getArtifactIds(hashsetName).forEach((id) -> {
                try {
                    if (!artifactHits.containsKey(id)) {
                        BlackboardArtifact art = skCase.getBlackboardArtifact(id);
                        artifactHits.put(id, art);
                    }
                    list.add(id);
                } catch (TskException ex) {
                    logger.log(Level.SEVERE, "TSK Exception occurred", ex); //NON-NLS
                }
            });
            return true;
        }

        @Override
        protected Node createNodeForKey(Long id) {     
            BlackboardArtifact art = artifactHits.get(id);
            return (null == art) ? null : new BlackboardArtifactNode(art);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }
}
