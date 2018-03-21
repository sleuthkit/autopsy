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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
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

/**
 * Keyword hits node support
 */
public class KeywordHits implements AutopsyVisitableItem {

    private static final Logger logger = Logger.getLogger(KeywordHits.class.getName());

    @NbBundle.Messages("KeywordHits.kwHits.text=Keyword Hits")
    private static final String KEYWORD_HITS = KeywordHits_kwHits_text();
    @NbBundle.Messages("KeywordHits.simpleLiteralSearch.text=Single Literal Keyword Search")
    private static final String SIMPLE_LITERAL_SEARCH = KeywordHits_simpleLiteralSearch_text();
    @NbBundle.Messages("KeywordHits.singleRegexSearch.text=Single Regular Expression Search")
    private static final String SIMPLE_REGEX_SEARCH = KeywordHits_singleRegexSearch_text();

    public static final String NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getLabel();

    private SleuthkitCase skCase;
    private final KeywordResults keywordResults;

    /**
     * String used in the instance MAP so that exact matches and substring can
     * fit into the same data structure as regexps, even though they don't use
     * instances.
     */
    private static final String DEFAULT_INSTANCE_NAME = "DEFAULT_INSTANCE_NAME";

    /**
     * query attributes table for the ones that we need for the tree
     */
    private static final String KEYWORD_HIT_ATTRIBUTES_QUERY = "SELECT blackboard_attributes.value_text, "//NON-NLS
            + "blackboard_attributes.value_int32, "//NON-NLS
            + "blackboard_attributes.artifact_id, " //NON-NLS
            + "blackboard_attributes.attribute_type_id "//NON-NLS
            + "FROM blackboard_attributes, blackboard_artifacts "//NON-NLS
            + "WHERE blackboard_attributes.artifact_id = blackboard_artifacts.artifact_id "//NON-NLS
            + " AND blackboard_artifacts.artifact_type_id = " + BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() //NON-NLS
            + " AND (attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()//NON-NLS
            + " OR attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()//NON-NLS
            + " OR attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE.getTypeID()//NON-NLS
            + " OR attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID()//NON-NLS
            + ")"; //NON-NLS

    static private boolean isOnlyDefaultInstance(List<String> instances) {
        return (instances.size() == 1) && (instances.get(0).equals(DEFAULT_INSTANCE_NAME));
    }

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

                    // I think we can use attributes.remove(...) here? - why should bwe use remove?
                    String listName = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()));
                    String word = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()));
                    String reg = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID()));
                    String kwType = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE.getTypeID()));

                    if (listName != null) {     // part of a list
                        // get or create list entry
                        Map<String, Map<String, Set<Long>>> listMap = listsMap.computeIfAbsent(listName, ln -> new LinkedHashMap<>());

                        if ("1".equals(kwType) || reg == null) {  //literal, substring or exact
                            /*
                             * Substring, treated same as exact match. "1" is
                             * the ordinal value for substring as defined in
                             * KeywordSearch.java. The original term should be
                             * stored in reg
                             */
                            word = (reg != null) ? reg : word; //use original term if it there.
                            addNonRegExpMatchToList(listMap, word, id);
                        } else {
                            addRegExpToList(listMap, reg, word, id);
                        }
                    } else {//single term
                        if ("1".equals(kwType) || reg == null) {  //literal, substring or exact
                            /*
                             * Substring, treated same as exact match. "1" is
                             * the ordinal value for substring as defined in
                             * KeywordSearch.java. The original term should be
                             * stored in reg
                             */
                            word = (reg != null) ? reg : word; //use original term if it there.
                            addNonRegExpMatchToList(literalMap, word, id);
                        } else {
                            addRegExpToList(regexMap, reg, word, id);
                        }
                    }
                }
                topLevelMap.putAll(listsMap);
            }

            setChanged();
            notifyObservers();
        }

        public void update() {
            // maps Artifact ID to map of attribute types to attribute values
            Map<Long, Map<Long, String>> artifactIds = new LinkedHashMap<>();

            if (skCase == null) {
                return;
            }

            try (CaseDbQuery dbQuery = skCase.executeQuery(KEYWORD_HIT_ATTRIBUTES_QUERY)) {
                ResultSet resultSet = dbQuery.getResultSet();
                while (resultSet.next()) {
                    long artifactId = resultSet.getLong("artifact_id"); //NON-NLS
                    long typeId = resultSet.getLong("attribute_type_id"); //NON-NLS
                    String valueStr = resultSet.getString("value_text"); //NON-NLS

                    //get the map of attributes for this artifact
                    Map<Long, String> attributesByTypeMap = artifactIds.computeIfAbsent(artifactId, ai -> new LinkedHashMap<>());
                    if (StringUtils.isNotEmpty(valueStr)) {
                        attributesByTypeMap.put(typeId, valueStr);
                    } else {
                        // Keyword Search Type is an int
                        Long valueLong = resultSet.getLong("value_int32");
                        attributesByTypeMap.put(typeId, valueLong.toString());
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

    private abstract class DetachableObserverChildFactory<X> extends ChildFactory.Detachable<X> implements Observer {

        @Override
        protected void addNotify() {
            keywordResults.addObserver(this);
        }

        @Override
        protected void removeNotify() {
            keywordResults.deleteObserver(this);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    /**
     * Creates the list nodes
     */
    private class ListFactory extends DetachableObserverChildFactory<String> {

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
                         * Even with the check above, it is still possible that
                         * the case will be closed in a different thread before
                         * this code executes. If that happens, it is possible
                         * for the event to have a null oldValue.
                         */
                        ModuleDataEvent eventData = (ModuleDataEvent) evt.getOldValue();
                        if (null != eventData && eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                            keywordResults.update();
                        }
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
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
                        keywordResults.update();
                    } catch (NoCurrentCaseException notUsed) {
                        // Case is closed, do nothing.
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())
                        && evt.getNewValue() == null) {
                    /*
                     * Case was closed. Remove listeners so that we don't get
                     * called with a stale case handle
                     */
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
            keywordResults.update();
            super.addNotify();
        }

        @Override
        protected void removeNotify() {
            IngestManager.getInstance().removeIngestJobEventListener(pcl);
            IngestManager.getInstance().removeIngestModuleEventListener(pcl);
            Case.removeEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), pcl);
            super.removeNotify();
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
    }

    private abstract class KWHitsNodeBase extends DisplayableItemNode implements Observer {

        private KWHitsNodeBase(Children children, Lookup lookup) {
            super(children, lookup);
        }

        private KWHitsNodeBase(Children children) {
            super(children);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        @Override
        public void update(Observable o, Object arg) {
            updateDisplayName();
        }

        final void updateDisplayName() {
            super.setDisplayName(getName() + " (" + countTotalDescendants() + ")");
        }

        abstract int countTotalDescendants();
    }

    /**
     * Represents the keyword search lists (or default groupings if list was not
     * given)
     */
    class ListNode extends KWHitsNodeBase {

        private final String listName;

        private ListNode(String listName) {
            super(Children.create(new TermFactory(listName), true), Lookups.singleton(listName));
            super.setName(listName);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png"); //NON-NLS
            this.listName = listName;
            updateDisplayName();
            keywordResults.addObserver(this);
        }

        @Override
        public int countTotalDescendants() {
            int totalDescendants = 0;

            for (String word : keywordResults.getKeywords(listName)) {
                for (String instance : keywordResults.getKeywordInstances(listName, word)) {
                    Set<Long> ids = keywordResults.getArtifactIds(listName, word, instance);
                    totalDescendants += ids.size();
                }
            }
            return totalDescendants;
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
    }

    /**
     * Creates the nodes that represent search terms
     */
    private class TermFactory extends DetachableObserverChildFactory<String> {

        private final String setName;

        private TermFactory(String setName) {
            super();
            this.setName = setName;
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
    }

    /**
     * Represents the search term or regexp that user searched for
     */
    class TermNode extends KWHitsNodeBase {

        private final String setName;
        private final String keyword;

        private TermNode(String setName, String keyword) {
            super(Children.create(new RegExpInstancesFactory(setName, keyword), true), Lookups.singleton(keyword));
            super.setName(keyword);
            this.setName = setName;
            this.keyword = keyword;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png"); //NON-NLS
            updateDisplayName();
            keywordResults.addObserver(this);
        }

        @Override
        int countTotalDescendants() {
            return keywordResults.getKeywordInstances(setName, keyword).stream()
                    .mapToInt(instance -> keywordResults.getArtifactIds(setName, keyword, instance).size())
                    .sum();
        }

        @Override
        public boolean isLeafTypeNode() {
            // is this an exact/substring match (i.e. did we use the DEFAULT name)?
            return isOnlyDefaultInstance(keywordResults.getKeywordInstances(setName, keyword));
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
                    countTotalDescendants()));

            return s;
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
    private class RegExpInstancesFactory extends DetachableObserverChildFactory<RegExpInstanceKey> {

        private final String keyword;
        private final String setName;

        private RegExpInstancesFactory(String setName, String keyword) {
            super();
            this.setName = setName;
            this.keyword = keyword;
        }

        @Override
        protected boolean createKeys(List<RegExpInstanceKey> list) {
            List<String> instances = keywordResults.getKeywordInstances(setName, keyword);
            // The keys are different depending on what we are displaying.
            // regexp get another layer to show instances.  
            // Exact/substring matches don't. 
            if (isOnlyDefaultInstance(instances)) {
                list.addAll(keywordResults.getArtifactIds(setName, keyword, DEFAULT_INSTANCE_NAME).stream()
                        .map(RegExpInstanceKey::new)
                        .collect(Collectors.toList()));
            } else {
                list.addAll(instances.stream()
                        .map(RegExpInstanceKey::new)
                        .collect(Collectors.toList()));
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(RegExpInstanceKey key) {
            if (key.isRegExp()) {
                return new RegExpInstanceNode(setName, keyword, key.getRegExpKey());
            } else {
                // if it isn't a regexp, then skip the 'instance' layer of the tree
                return createBlackboardArtifactNode(key.getIdKey());
            }
        }

    }

    /**
     * Represents a specific term that was found from a regexp
     */
    class RegExpInstanceNode extends KWHitsNodeBase {

        private final String setName;
        private final String keyword;
        private final String instance;

        private RegExpInstanceNode(String setName, String keyword, String instance) {
            super(Children.create(new HitsFactory(setName, keyword, instance), true), Lookups.singleton(instance));
            super.setName(instance);  //the instance represents the name of the keyword hit at this point as the keyword is the regex
            this.setName = setName;
            this.keyword = keyword;
            this.instance = instance;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png"); //NON-NLS
            updateDisplayName();
            keywordResults.addObserver(this);
        }

        @Override
        int countTotalDescendants() {
            return keywordResults.getArtifactIds(setName, keyword, instance).size();
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
                    keywordResults.getArtifactIds(setName, keyword, instance).size()));

            return s;
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
                logger.log(Level.SEVERE, "TskCoreException while constructing BlackboardArtifact Node from KeywordHitsKeywordChildren", ex); //NON-NLS
                return n;
            }

            /*
             * It is possible to get a keyword hit on artifacts generated for
             * the underlying image in which case MAC times are not
             * available/applicable/useful.
             */
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
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "TSK Exception occurred", ex); //NON-NLS
        }
        return null;
    }

    /**
     * Creates nodes for individual files that had hits
     */
    private class HitsFactory extends DetachableObserverChildFactory<Long> {

        private final String keyword;
        private final String setName;
        private final String instance;

        private HitsFactory(String setName, String keyword, String instance) {
            super();
            this.setName = setName;
            this.keyword = keyword;
            this.instance = instance;
        }

        @Override
        protected boolean createKeys(List<Long> list) {
            list.addAll(keywordResults.getArtifactIds(setName, keyword, instance));
            return true;
        }

        @Override
        protected Node createNodeForKey(Long artifactId) {
            return createBlackboardArtifactNode(artifactId);
        }
    }
}
