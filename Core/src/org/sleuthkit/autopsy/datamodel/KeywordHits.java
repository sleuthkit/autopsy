/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.datamodel.Bundle.*;
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
    @NbBundle.Messages("KeywordHits.kwHits.text=Keyword Hits")
    private static final String KEYWORD_HITS = Bundle.KeywordHits_kwHits_text();
    public static final String NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getLabel();
    @NbBundle.Messages("KeywordHits.simpleLiteralSearch.text=Single Literal Keyword Search")
    public static final String SIMPLE_LITERAL_SEARCH = Bundle.KeywordHits_simpleLiteralSearch_text();
    @NbBundle.Messages("KeywordHits.singleRegexSearch.text=Single Regular Expression Search")
    public static final String SIMPLE_REGEX_SEARCH = Bundle.KeywordHits_singleRegexSearch_text();
    private final KeywordResults keywordResults;
    /**
     * String used in the instance MAP so that exact matches and substring can
     * fit into the same data structure as regexps, even though they don't use
     * instances.
     */
    private final String DEFAULT_INSTANCE_NAME = "DEFAULT_INSTANCE_NAME";

    public KeywordHits(SleuthkitCase skCase) {
        this.skCase = skCase;
        keywordResults = new KeywordResults();
    }

    /*
     * All of these maps and code assume the following: Regexps will have an
     * 'instance' layer that shows the specific words that matched the regexp
     * Exact match and substring will not have the instance layer and instead
     * will have the specific hits below their term.
     */
    private final class KeywordResults extends Observable {

        // Map from listName/Type to Map of keywords/regexp to Map of instance terms to Set of artifact Ids
        // NOTE: the map can be accessed by multiple worker threads and needs to be synchronized
        private final Map<String, Map<String, Map<String, Set<Long>>>> topLevelMap = new LinkedHashMap<>();

        KeywordResults() {
            update();
        }

        /**
         * Get the list names used in searches.
         *
         * @return The list of list names.
         */
        List<String> getListNames() {
            synchronized (topLevelMap) {
                List<String> names = new ArrayList<>(topLevelMap.keySet());
                // this causes the "Single ..." terms to be in the middle of the results, 
                // which is wierd.  Make a custom comparator or do something else to maek them on top
                //Collections.sort(names);
                return names;
            }
        }

        /**
         * Get keywords used in a given list. Will be regexp patterns for
         * regexps and search term for non-regexps.
         *
         * @param listName Keyword list name
         *
         * @return
         */
        List<String> getKeywords(String listName) {
            List<String> keywords;
            synchronized (topLevelMap) {
                keywords = new ArrayList<>(topLevelMap.get(listName).keySet());
            }
            Collections.sort(keywords);
            return keywords;
        }

        /**
         * Get specific keyword terms that were found for a given list and
         * keyword combination. For example, a specific phone number for a phone
         * number regexp. Will be the default instance for non-regexp searches.
         *
         * @param listName Keyword list name
         * @param keyword  search term (regexp pattern or exact match term)
         *
         * @return
         */
        List<String> getKeywordInstances(String listName, String keyword) {
            List<String> instances;
            synchronized (topLevelMap) {
                instances = new ArrayList<>(topLevelMap.get(listName).get(keyword).keySet());
            }
            Collections.sort(instances);
            return instances;
        }

        /**
         * Get artifact ids for a given list, keyword, and instance triple
         *
         * @param listName        Keyword list name
         * @param keyword         search term (regexp pattern or exact match
         *                        term)
         * @param keywordInstance specific term that matched (or default
         *                        instance name)
         *
         * @return
         */
        Set<Long> getArtifactIds(String listName, String keyword, String keywordInstance) {
            synchronized (topLevelMap) {
                return topLevelMap.get(listName).get(keyword).get(keywordInstance);
            }
        }

        /**
         * Add a hit for a regexp to the internal data structure.
         *
         * @param listMap         Maps keywords/regexp to instances to artifact
         *                        IDs
         * @param regExp          Regular expression that was used in search
         * @param keywordInstance Specific term that matched regexp
         * @param artifactId      Artifact id of file that had hit
         */
        void addRegExpToList(Map<String, Map<String, Set<Long>>> listMap, String regExp, String keywordInstance, Long artifactId) {
            Map<String, Set<Long>> instanceMap = listMap.computeIfAbsent(regExp, r -> new LinkedHashMap<>());
            // add this ID to the instances entry, creating one if needed
            instanceMap.computeIfAbsent(keywordInstance, ki -> new HashSet<>()).add(artifactId);
        }

        /**
         * Add a hit for a exactmatch (or substring) to the internal data
         * structure.
         *
         * @param listMap    Maps keywords/regexp to instances to artifact IDs
         * @param keyWord    Term that was hit
         * @param artifactId Artifact id of file that had hit
         */
        void addNonRegExpMatchToList(Map<String, Map<String, Set<Long>>> listMap, String keyWord, Long artifactId) {
            Map<String, Set<Long>> instanceMap = listMap.computeIfAbsent(keyWord, k -> new LinkedHashMap<>());

            // Use the default instance name, since we don't need that level in the tree
            instanceMap.computeIfAbsent(DEFAULT_INSTANCE_NAME, DIN -> new HashSet<>()).add(artifactId);
        }

        /**
         * Populate data structure for the tree based on the keyword hit
         * artifacts
         *
         * @param artifactIds Maps Artifact ID to map of attribute types to
         *                    attribute values
         */
        void populateTreeMaps(Map<Long, Map<Long, String>> artifactIds) {
            synchronized (topLevelMap) {
                topLevelMap.clear();

                // map of list name to keword to artifact IDs
                Map<String, Map<String, Map<String, Set<Long>>>> listsMap = new LinkedHashMap<>();

                // Map from from literal keyword to instances (which will be empty) to artifact IDs
                Map<String, Map<String, Set<Long>>> literalMap = new LinkedHashMap<>();

                // Map from regex keyword artifact to instances to artifact IDs
                Map<String, Map<String, Set<Long>>> regexMap = new LinkedHashMap<>();

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
                    // new in 4.4
                    String kwType = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE.getTypeID()));

                    // part of a list
                    if (listName != null) {
                        // get or create list entry
                        Map<String, Map<String, Set<Long>>> listMap = listsMap.computeIfAbsent(listName, ln -> new LinkedHashMap<>());

                        // substring, treated same as exact match
                        // Enum for "1" is defined in KeywordSearch.java
                        if ((kwType != null) && (kwType.equals("1"))) {
                            // original term should be stored in reg
                            if (reg != null) {
                                addNonRegExpMatchToList(listMap, reg, id);
                            } else {
                                addNonRegExpMatchToList(listMap, word, id);
                            }
                        } else if (reg != null) {
                            addRegExpToList(listMap, reg, word, id);
                        } else {
                            addNonRegExpMatchToList(listMap, word, id);
                        }
                    } // regular expression, single term
                    else if (reg != null) {
                        // substring is treated same as exact 
                        if ((kwType != null) && (kwType.equals("1"))) {
                            // original term should be stored in reg
                            addNonRegExpMatchToList(literalMap, reg, id);
                        } else {
                            addRegExpToList(regexMap, reg, word, id);
                        }
                    } // literal, single term
                    else {
                        addNonRegExpMatchToList(literalMap, word, id);
                    }
                }
                topLevelMap.putAll(listsMap);
            }

            setChanged();
            notifyObservers();
        }

        @SuppressWarnings("deprecation")
        public void update() {
            // maps Artifact ID to map of attribute types to attribute values
            Map<Long, Map<Long, String>> artifactIds = new LinkedHashMap<>();

            if (skCase == null) {
                return;
            }

            // query attributes table for the ones that we need for the tree
            int setId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID();
            int wordId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID();
            int regexId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID();
            int artId = BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID();
            String query = "SELECT blackboard_attributes.value_text,blackboard_attributes.value_int32,"
                    + "blackboard_attributes.artifact_id," //NON-NLS
                    + "blackboard_attributes.attribute_type_id FROM blackboard_attributes,blackboard_artifacts WHERE " //NON-NLS
                    + "(blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id AND " //NON-NLS
                    + "blackboard_artifacts.artifact_type_id=" + artId //NON-NLS
                    + ") AND (attribute_type_id=" + setId + " OR " //NON-NLS
                    + "attribute_type_id=" + wordId + " OR " //NON-NLS
                    + "attribute_type_id=" + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE.getTypeID() + " OR " //NON-NLS
                    + "attribute_type_id=" + regexId + ")"; //NON-NLS

            try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                while (resultSet.next()) {
                    String valueStr = resultSet.getString("value_text"); //NON-NLS
                    long artifactId = resultSet.getLong("artifact_id"); //NON-NLS
                    long typeId = resultSet.getLong("attribute_type_id"); //NON-NLS
                    Map<Long, String> typeMap = artifactIds.computeIfAbsent(artifactId, ai -> new LinkedHashMap<>());
                    if (StringUtils.isNotEmpty(valueStr)) {
                        typeMap.put(typeId, valueStr);
                    } else {
                        // Keyword Search Type is an int
                        Long valueLong = resultSet.getLong("value_int32");
                        typeMap.put(typeId, valueLong.toString());
                    }
                }
            } catch (TskCoreException | SQLException ex) {
                logger.log(Level.WARNING, "SQL Exception occurred: ", ex); //NON-NLS
            }

            populateTreeMaps(artifactIds);
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
        @NbBundle.Messages({"KeywordHits.createSheet.name.name=Name",
            "KeywordHits.createSheet.name.displayName=Name",
            "KeywordHits.createSheet.name.desc=no description"})
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(
                    KeywordHits_createSheet_name_name(),
                    KeywordHits_createSheet_name_displayName(),
                    KeywordHits_createSheet_name_desc(),
                    getName()));

            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Creates the list nodes
     */
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

    /**
     * Represents the keyword search lists (or default groupings if list was not
     * given)
     */
    public class ListNode extends DisplayableItemNode implements Observer {

        private final String listName;

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
                for (String instance : keywordResults.getKeywordInstances(listName, word)) {
                    Set<Long> ids = keywordResults.getArtifactIds(listName, word, instance);
                    totalDescendants += ids.size();
                }
            }
            super.setDisplayName(listName + " (" + totalDescendants + ")");
        }

        @Override
        @NbBundle.Messages({"KeywordHits.createSheet.listName.name=List Name",
            "KeywordHits.createSheet.listName.displayName=List Name",
            "KeywordHits.createSheet.listName.desc=no description",
            "KeywordHits.createSheet.numChildren.name=Number of Children",
            "KeywordHits.createSheet.numChildren.displayName=Number of Children",
            "KeywordHits.createSheet.numChildren.desc=no description"})
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty<>(
                    KeywordHits_createSheet_listName_name(),
                    KeywordHits_createSheet_listName_displayName(),
                    KeywordHits_createSheet_listName_desc(),
                    listName));

            ss.put(new NodeProperty<>(
                    KeywordHits_createSheet_numChildren_name(),
                    KeywordHits_createSheet_numChildren_displayName(),
                    KeywordHits_createSheet_numChildren_desc(),
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

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Creates the nodes that represent search terms
     */
    private class TermFactory extends ChildFactory.Detachable<String> implements Observer {

        private final String setName;

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

    /**
     * Represents the search term or regexp that user searched for
     */
    public class TermNode extends DisplayableItemNode implements Observer {

        private final String setName;
        private final String keyword;

        public TermNode(String setName, String keyword) {
            super(Children.create(new RegExpInstancesFactory(setName, keyword), true), Lookups.singleton(keyword));
            super.setName(keyword);
            this.setName = setName;
            this.keyword = keyword;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png"); //NON-NLS
            updateDisplayName();
            keywordResults.addObserver(this);
        }

        private void updateDisplayName() {
            int totalDescendants = 0;

            for (String instance : keywordResults.getKeywordInstances(setName, keyword)) {
                Set<Long> ids = keywordResults.getArtifactIds(setName, keyword, instance);
                totalDescendants += ids.size();
            }

            super.setDisplayName(keyword + " (" + totalDescendants + ")");
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        @Override
        public boolean isLeafTypeNode() {
            List<String> instances = keywordResults.getKeywordInstances(setName, keyword);
            // is this an exact/substring match (i.e. did we use the DEFAULT name)?
            return instances.size() == 1 && instances.get(0).equals(DEFAULT_INSTANCE_NAME);
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        @NbBundle.Messages({"KeywordHits.createSheet.filesWithHits.name=Files with Hits",
            "KeywordHits.createSheet.filesWithHits.displayName=Files with Hits",
            "KeywordHits.createSheet.filesWithHits.desc=no description"})
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            ss.put(new NodeProperty<>(
                    KeywordHits_createSheet_listName_name(),
                    KeywordHits_createSheet_listName_displayName(),
                    KeywordHits_createSheet_listName_desc(),
                    getDisplayName()));

            ss.put(new NodeProperty<>(
                    KeywordHits_createSheet_filesWithHits_name(),
                    KeywordHits_createSheet_filesWithHits_displayName(),
                    KeywordHits_createSheet_filesWithHits_desc(),
                    keywordResults.getKeywordInstances(setName, keyword).size()));

            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Allows us to pass in either longs or strings as they keys for different
     * types of nodes at the same level. Probably a better way to do this, but
     * it works.
     */
    private class RegExpInstanceKey {

        private final boolean isRegExp;
        private String strKey;
        private Long longKey;

        RegExpInstanceKey(String key) {
            isRegExp = true;
            strKey = key;
        }

        RegExpInstanceKey(Long key) {
            isRegExp = false;
            longKey = key;
        }

        boolean isRegExp() {
            return isRegExp;
        }

        Long getIdKey() {
            return longKey;
        }

        String getRegExpKey() {
            return strKey;
        }
    }

    /**
     * Creates the nodes for a given regexp that represent the specific terms
     * that were found
     */
    public class RegExpInstancesFactory extends ChildFactory.Detachable<RegExpInstanceKey> implements Observer {

        private final String keyword;
        private final String setName;

        private final Map<RegExpInstanceKey, DisplayableItemNode> nodesMap = new HashMap<>();

        public RegExpInstancesFactory(String setName, String keyword) {
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
        protected boolean createKeys(List<RegExpInstanceKey> list) {
            List<String> instances = keywordResults.getKeywordInstances(setName, keyword);
            // The keys are different depending on what we are displaying.
            // regexp get another layer to show instances.  
            // Exact/substring matches don't. 
            if ((instances.size() == 1) && (instances.get(0).equals(DEFAULT_INSTANCE_NAME))) {
                for (Long id : keywordResults.getArtifactIds(setName, keyword, DEFAULT_INSTANCE_NAME)) {
                    RegExpInstanceKey key = new RegExpInstanceKey(id);
                    nodesMap.computeIfAbsent(key, k -> createNode(k));
                    list.add(key);
                }
            } else {
                for (String instance : instances) {
                    RegExpInstanceKey key = new RegExpInstanceKey(instance);
                    nodesMap.computeIfAbsent(key, k -> createNode(k));
                    list.add(key);
                }

            }
            return true;
        }

        @Override
        protected Node createNodeForKey(RegExpInstanceKey key) {
            return nodesMap.get(key);
        }

        private DisplayableItemNode createNode(RegExpInstanceKey key) {
            if (key.isRegExp()) {
                return new RegExpInstanceNode(setName, keyword, key.getRegExpKey());
            } else {
                // if it isn't a regexp, then skip the 'instance' layer of the tree
                return createBlackboardArtifactNode(key.getIdKey());
            }

        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    /**
     * Represents a specific term that was found from a regexp
     */
    public class RegExpInstanceNode extends DisplayableItemNode implements Observer {

        private final String setName;
        private final String keyword;
        private final String instance;

        public RegExpInstanceNode(String setName, String keyword, String instance) {
            super(Children.create(new HitsFactory(setName, keyword, instance), true), Lookups.singleton(keyword));
            super.setName(instance);  //the instance represents the name of the keyword hit at this point as the keyword is the regex
            this.setName = setName;
            this.keyword = keyword;
            this.instance = instance;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png"); //NON-NLS
            updateDisplayName();
            keywordResults.addObserver(this);
        }

        private void updateDisplayName() {
            int totalDescendants = keywordResults.getArtifactIds(setName, keyword, instance).size();
            super.setDisplayName(instance + " (" + totalDescendants + ")");
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

            ss.put(new NodeProperty<>(
                    KeywordHits_createSheet_listName_name(),
                    KeywordHits_createSheet_listName_displayName(),
                    KeywordHits_createSheet_listName_desc(),
                    getDisplayName()));

            ss.put(new NodeProperty<>(
                    KeywordHits_createSheet_filesWithHits_name(),
                    KeywordHits_createSheet_filesWithHits_displayName(),
                    KeywordHits_createSheet_filesWithHits_desc(),
                    keywordResults.getKeywordInstances(setName, keyword).size()));

            return s;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }
    }

    /**
     * Create a blackboard node for the given Keyword Hit artifact
     *
     * @param artifactId
     *
     * @return Node or null on error
     */
    @NbBundle.Messages({"KeywordHits.createNodeForKey.modTime.name=ModifiedTime",
        "KeywordHits.createNodeForKey.modTime.displayName=Modified Time",
        "KeywordHits.createNodeForKey.modTime.desc=Modified Time",
        "KeywordHits.createNodeForKey.accessTime.name=AccessTime",
        "KeywordHits.createNodeForKey.accessTime.displayName=Access Time",
        "KeywordHits.createNodeForKey.accessTime.desc=Access Time",
        "KeywordHits.createNodeForKey.chgTime.name=ChangeTime",
        "KeywordHits.createNodeForKey.chgTime.displayName=Change Time",
        "KeywordHits.createNodeForKey.chgTime.desc=Change Time"})
    private BlackboardArtifactNode createBlackboardArtifactNode(Long artifactId) {
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
                    KeywordHits_createNodeForKey_modTime_name(),
                    KeywordHits_createNodeForKey_modTime_displayName(),
                    KeywordHits_createNodeForKey_modTime_desc(),
                    ContentUtils.getStringTime(file.getMtime(), file)));
            n.addNodeProperty(new NodeProperty<>(
                    KeywordHits_createNodeForKey_accessTime_name(),
                    KeywordHits_createNodeForKey_accessTime_displayName(),
                    KeywordHits_createNodeForKey_accessTime_desc(),
                    ContentUtils.getStringTime(file.getAtime(), file)));
            n.addNodeProperty(new NodeProperty<>(
                    KeywordHits_createNodeForKey_chgTime_name(),
                    KeywordHits_createNodeForKey_chgTime_displayName(),
                    KeywordHits_createNodeForKey_chgTime_desc(),
                    ContentUtils.getStringTime(file.getCtime(), file)));
            return n;
        } catch (TskException ex) {
            logger.log(Level.WARNING, "TSK Exception occurred", ex); //NON-NLS
        }
        return null;
    }

    /**
     * Creates nodes for individual files that had hits
     */
    public class HitsFactory extends ChildFactory.Detachable<Long> implements Observer {

        private final String keyword;
        private final String setName;
        private final String instance;

        private final Map<Long, BlackboardArtifactNode> nodesMap = new HashMap<>();

        public HitsFactory(String setName, String keyword, String instance) {
            super();
            this.setName = setName;
            this.keyword = keyword;
            this.instance = instance;
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
            for (Long id : keywordResults.getArtifactIds(setName, keyword, instance)) {
                nodesMap.computeIfAbsent(id, i -> createBlackboardArtifactNode(i));
                list.add(id);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(Long artifactId) {
            return nodesMap.get(artifactId);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }
}
