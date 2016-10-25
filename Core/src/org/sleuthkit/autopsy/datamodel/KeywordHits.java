/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011-2015 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Keyword hits node support
 */
public class KeywordHits implements AutopsyVisitableItem {

    private SleuthkitCase skCase;
    private static final Logger logger = Logger.getLogger(KeywordHits.class.getName());
    private static final String KEYWORD_HITS = NbBundle.getMessage(KeywordHits.class, "KeywordHits.kwHits.text");
    public static final String NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getLabel();
    public static final String SIMPLE_LITERAL_SEARCH = NbBundle
            .getMessage(KeywordHits.class, "KeywordHits.simpleLiteralSearch.text");
    public static final String SIMPLE_REGEX_SEARCH = NbBundle
            .getMessage(KeywordHits.class, "KeywordHits.singleRegexSearch.text");
    private final KeywordResults keywordResults;

    public KeywordHits(SleuthkitCase skCase) {
        this.skCase = skCase;
        keywordResults = new KeywordResults();
    }

    private final class KeywordResults extends Observable {

        // Map from listName/Type to Map of keyword to set of artifact Ids
        // NOTE: the map can be accessed by multiple worker threads and needs to be synchronized
        private final Map<String, Map<String, Set<Long>>> topLevelMap = new LinkedHashMap<>();

        KeywordResults() {
            update();
        }

        List<String> getListNames() {
            synchronized (topLevelMap) {
                List<String> names = new ArrayList<>(topLevelMap.keySet());
                // this causes the "Single ..." terms to be in the middle of the results, 
                // which is wierd.  Make a custom comparator or do something else to maek them on top
                //Collections.sort(names);
                return names;
            }
        }

        List<String> getKeywords(String listName) {
            List<String> keywords;
            synchronized (topLevelMap) {
                keywords = new ArrayList<>(topLevelMap.get(listName).keySet());
            }
            Collections.sort(keywords);
            return keywords;
        }

        Set<Long> getArtifactIds(String listName, String keyword) {
            synchronized (topLevelMap) {
                return topLevelMap.get(listName).get(keyword);
            }
        }

        // populate maps based on artifactIds
        void populateMaps(Map<Long, Map<Long, String>> artifactIds) {
            synchronized (topLevelMap) {
                topLevelMap.clear();

                // map of list name to keword to artifact IDs
                Map<String, Map<String, Set<Long>>> listsMap = new LinkedHashMap<>();

                // Map from from literal keyword to artifact IDs
                Map<String, Set<Long>> literalMap = new LinkedHashMap<>();

                // Map from regex keyword artifact IDs
                Map<String, Set<Long>> regexMap = new LinkedHashMap<>();

                // top-level nodes
                topLevelMap.put(SIMPLE_LITERAL_SEARCH, literalMap);
                topLevelMap.put(SIMPLE_REGEX_SEARCH, regexMap);

                for (Map.Entry<Long, Map<Long, String>> art : artifactIds.entrySet()) {
                    long id = art.getKey();
                    Map<Long, String> attributes = art.getValue();

                    // I think we can use attributes.remove(...) here?
                    String listName = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()));
                    String word = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()));
                    String reg = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID()));

                    // part of a list
                    if (listName != null) {
                        if (listsMap.containsKey(listName) == false) {
                            listsMap.put(listName, new LinkedHashMap<String, Set<Long>>());
                        }

                        Map<String, Set<Long>> listMap = listsMap.get(listName);
                        if (listMap.containsKey(word) == false) {
                            listMap.put(word, new HashSet<Long>());
                        }

                        listMap.get(word).add(id);
                    } // regular expression, single term
                    else if (reg != null) {
                        if (regexMap.containsKey(reg) == false) {
                            regexMap.put(reg, new HashSet<Long>());
                        }
                        regexMap.get(reg).add(id);
                    } // literal, single term
                    else {
                        if (literalMap.containsKey(word) == false) {
                            literalMap.put(word, new HashSet<Long>());
                        }
                        literalMap.get(word).add(id);
                    }
                    topLevelMap.putAll(listsMap);
                }
            }
            
            setChanged();
            notifyObservers();
        }

        @SuppressWarnings("deprecation")
        public void update() {
            Map<Long, Map<Long, String>> artifactIds = new LinkedHashMap<>();

            if (skCase == null) {
                return;
            }

            int setId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID();
            int wordId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID();
            int regexId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID();
            int artId = BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID();
            String query = "SELECT blackboard_attributes.value_text,blackboard_attributes.artifact_id," //NON-NLS
                    + "blackboard_attributes.attribute_type_id FROM blackboard_attributes,blackboard_artifacts WHERE " //NON-NLS
                    + "(blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id AND " //NON-NLS
                    + "blackboard_artifacts.artifact_type_id=" + artId //NON-NLS
                    + ") AND (attribute_type_id=" + setId + " OR " //NON-NLS
                    + "attribute_type_id=" + wordId + " OR " //NON-NLS
                    + "attribute_type_id=" + regexId + ")"; //NON-NLS

            try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                while (resultSet.next()) {
                    String value = resultSet.getString("value_text"); //NON-NLS
                    long artifactId = resultSet.getLong("artifact_id"); //NON-NLS
                    long typeId = resultSet.getLong("attribute_type_id"); //NON-NLS
                    if (!artifactIds.containsKey(artifactId)) {
                        artifactIds.put(artifactId, new LinkedHashMap<Long, String>());
                    }
                    if (!value.equals("")) {
                        artifactIds.get(artifactId).put(typeId, value);
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "SQL Exception occurred: ", ex); //NON-NLS
            }

            populateMaps(artifactIds);
        }
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    // Created by CreateAutopsyNodeVisitor
    public class RootNode extends DisplayableItemNode {

        public RootNode() {
            super(Children.create(new ListFactory(), true), Lookups.singleton(KEYWORD_HITS));
            super.setName(NAME);
            super.setDisplayName(KEYWORD_HITS);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png"); //NON-NLS
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

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.name.displayName"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.name.desc"),
                    getName()));

            return s;
        }

        /*
         * TODO (AUT-1849): Correct or remove peristent column reordering code
         *
         * Added to support this feature.
         */
//        @Override
//        public String getItemType() {
//            return "KeywordRoot"; //NON-NLS
//        }
    }

    private class ListFactory extends ChildFactory.Detachable<String> implements Observer {

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
                        Case.getCurrentCase();
                        /**
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData && eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                            keywordResults.update();
                        }
                    } catch (IllegalStateException notUsed) {
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
                        Case.getCurrentCase();
                        keywordResults.update();
                    } catch (IllegalStateException notUsed) {
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
            Case.addPropertyChangeListener(pcl);
            keywordResults.update();
            keywordResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            Case.removePropertyChangeListener(pcl);
            keywordResults.deleteObserver(this);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(keywordResults.getListNames());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new ListNode(key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    public class ListNode extends DisplayableItemNode implements Observer {

        private String listName;

        public ListNode(String listName) {
            super(Children.create(new TermFactory(listName), true), Lookups.singleton(listName));
            super.setName(listName);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png"); //NON-NLS
            this.listName = listName;
            updateDisplayName();
            keywordResults.addObserver(this);
        }

        private void updateDisplayName() {
            int totalDescendants = 0;
            for (String word : keywordResults.getKeywords(listName)) {
                Set<Long> ids = keywordResults.getArtifactIds(listName, word);
                totalDescendants += ids.size();
            }
            super.setDisplayName(listName + " (" + totalDescendants + ")");
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.listName.name"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.listName.displayName"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.listName.desc"),
                    listName));

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.numChildren.name"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.numChildren.displayName"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.numChildren.desc"),
                    keywordResults.getKeywords(listName).size()));

            return s;
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
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        /*
         * TODO (AUT-1849): Correct or remove peristent column reordering code
         *
         * Added to support this feature.
         */
//        @Override
//        public String getItemType() {
//            return "KeywordList"; //NON-NLS
//        }
    }

    private class TermFactory extends ChildFactory.Detachable<String> implements Observer {

        private String setName;

        private TermFactory(String setName) {
            super();
            this.setName = setName;
        }

        @Override
        protected void addNotify() {
            keywordResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            keywordResults.deleteObserver(this);
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(keywordResults.getKeywords(setName));
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new TermNode(setName, key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    public class TermNode extends DisplayableItemNode implements Observer {

        private String setName;
        private String keyword;

        public TermNode(String setName, String keyword) {
            super(Children.create(new HitsFactory(setName, keyword), true), Lookups.singleton(keyword));
            super.setName(keyword);
            this.setName = setName;
            this.keyword = keyword;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png"); //NON-NLS
            updateDisplayName();
            keywordResults.addObserver(this);
        }

        private void updateDisplayName() {
            super.setDisplayName(keyword + " (" + keywordResults.getArtifactIds(setName, keyword).size() + ")");
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
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

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.listName.name"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.listName.displayName"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.listName.desc"),
                    getDisplayName()));

            ss.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.filesWithHits.name"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.filesWithHits.displayName"),
                    NbBundle.getMessage(this.getClass(), "KeywordHits.createSheet.filesWithHits.desc"),
                    keywordResults.getArtifactIds(setName, keyword).size()));

            return s;
        }

        /*
         * TODO (AUT-1849): Correct or remove peristent column reordering code
         *
         * Added to support this feature.
         */
//        @Override
//        public String getItemType() {
//            return "KeywordTerm"; //NON-NLS
//        }
    }

    public class HitsFactory extends ChildFactory.Detachable<Long> implements Observer {

        private String keyword;
        private String setName;

        public HitsFactory(String setName, String keyword) {
            super();
            this.setName = setName;
            this.keyword = keyword;
        }

        @Override
        protected void addNotify() {
            keywordResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            keywordResults.deleteObserver(this);
        }

        @Override
        protected boolean createKeys(List<Long> list) {
            list.addAll(keywordResults.getArtifactIds(setName, keyword));
            return true;
        }

        @Override
        protected Node createNodeForKey(Long artifactId) {
            if (skCase == null) {
                return null;
            }

            try {
                BlackboardArtifact art = skCase.getBlackboardArtifact(artifactId);
                BlackboardArtifactNode n = new BlackboardArtifactNode(art);
                AbstractFile file;
                try {
                    file = skCase.getAbstractFileById(art.getObjectID());
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "TskCoreException while constructing BlackboardArtifact Node from KeywordHitsKeywordChildren"); //NON-NLS
                    return n;
                }

                // It is possible to get a keyword hit on artifacts generated
                // for the underlying image in which case MAC times are not
                // available/applicable/useful.
                if (file == null) {
                    return n;
                }

                n.addNodeProperty(new NodeProperty<>(
                        NbBundle.getMessage(this.getClass(), "KeywordHits.createNodeForKey.modTime.name"),
                        NbBundle.getMessage(this.getClass(),
                                "KeywordHits.createNodeForKey.modTime.displayName"),
                        NbBundle.getMessage(this.getClass(),
                                "KeywordHits.createNodeForKey.modTime.desc"),
                        ContentUtils.getStringTime(file.getMtime(), file)));
                n.addNodeProperty(new NodeProperty<>(
                        NbBundle.getMessage(this.getClass(), "KeywordHits.createNodeForKey.accessTime.name"),
                        NbBundle.getMessage(this.getClass(),
                                "KeywordHits.createNodeForKey.accessTime.displayName"),
                        NbBundle.getMessage(this.getClass(),
                                "KeywordHits.createNodeForKey.accessTime.desc"),
                        ContentUtils.getStringTime(file.getAtime(), file)));
                n.addNodeProperty(new NodeProperty<>(
                        NbBundle.getMessage(this.getClass(), "KeywordHits.createNodeForKey.chgTime.name"),
                        NbBundle.getMessage(this.getClass(),
                                "KeywordHits.createNodeForKey.chgTime.displayName"),
                        NbBundle.getMessage(this.getClass(),
                                "KeywordHits.createNodeForKey.chgTime.desc"),
                        ContentUtils.getStringTime(file.getCtime(), file)));
                return n;
            } catch (TskException ex) {
                logger.log(Level.WARNING, "TSK Exception occurred", ex); //NON-NLS
            }
            return null;
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }
}
