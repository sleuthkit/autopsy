/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 * Keyword hits node support
 */
public class KeywordHits implements AutopsyVisitableItem {

    private SleuthkitCase skCase;
    private static final Logger logger = Logger.getLogger(KeywordHits.class.getName());
    private static final String KEYWORD_HITS = "Keyword Hits";
    public static final String NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getLabel();
    public static final String SIMPLE_LITERAL_SEARCH = "Single Literal Keyword Search";
    public static final String SIMPLE_REGEX_SEARCH = "Single Regular Expression Search";
    // Map from String (list name) to Map from string (keyword) to set<long> (artifact ids)
    private Map<String, Map<String, Set<Long>>> topLevelMap;
    private Map<String, Map<String, Set<Long>>> listsMap;
    // Map from String (literal keyword) to set<long> (artifact ids)
    private Map<String, Set<Long>> literalMap;
    // Map from String (regex keyword) to set<long> (artifact ids);
    private Map<String, Set<Long>> regexMap;
    Map<Long, Map<Long, String>> artifacts;

    public KeywordHits(SleuthkitCase skCase) {
        this.skCase = skCase;
        artifacts = new LinkedHashMap<Long, Map<Long, String>>();
        listsMap = new LinkedHashMap<String, Map<String, Set<Long>>>();
        literalMap = new LinkedHashMap<String, Set<Long>>();
        regexMap = new LinkedHashMap<String, Set<Long>>();
        topLevelMap = new LinkedHashMap<String, Map<String, Set<Long>>>();
    }

    private void initMaps() {
        topLevelMap.clear();
        topLevelMap.put(SIMPLE_LITERAL_SEARCH, literalMap);
        topLevelMap.put(SIMPLE_REGEX_SEARCH, regexMap);
        listsMap.clear();
        regexMap.clear();
        literalMap.clear();
        for (Map.Entry<Long, Map<Long, String>> art : artifacts.entrySet()) {
            long id = art.getKey();
            Map<Long, String> attributes = art.getValue();
            // I think we can use attributes.remove(...) here?
            String listName = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()));
            String word = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()));
            String reg = attributes.get(Long.valueOf(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID()));
            if (listName != null) {
                if (!listsMap.containsKey(listName)) {
                    listsMap.put(listName, new LinkedHashMap<String, Set<Long>>());
                }
                if (!listsMap.get(listName).containsKey(word)) {
                    listsMap.get(listName).put(word, new HashSet<Long>());
                }
                listsMap.get(listName).get(word).add(id);
            } else if (reg != null) {
                if (!regexMap.containsKey(reg)) {
                    regexMap.put(reg, new HashSet<Long>());
                }
                regexMap.get(reg).add(id);
            } else {
                if (!literalMap.containsKey(word)) {
                    literalMap.put(word, new HashSet<Long>());
                }
                literalMap.get(word).add(id);
            }
            topLevelMap.putAll(listsMap);

        }
    }

    private void initArtifacts() {
        artifacts.clear();
        try {
            int setId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID();
            int wordId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID();
            int regexId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID();
            int artId = BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID();
            String query = "SELECT blackboard_attributes.value_text,blackboard_attributes.artifact_id,"
                    + "blackboard_attributes.attribute_type_id FROM blackboard_attributes,blackboard_artifacts WHERE "
                    + "(blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id AND "
                    + "blackboard_artifacts.artifact_type_id=" + artId
                    + ") AND (attribute_type_id=" + setId + " OR "
                    + "attribute_type_id=" + wordId + " OR "
                    + "attribute_type_id=" + regexId + ")";
            ResultSet rs = skCase.runQuery(query);
            while (rs.next()) {
                String value = rs.getString("value_text");
                long artifactId = rs.getLong("artifact_id");
                long typeId = rs.getLong("attribute_type_id");
                if (!artifacts.containsKey(artifactId)) {
                    artifacts.put(artifactId, new LinkedHashMap<Long, String>());
                }
                if (!value.equals("")) {
                    artifacts.get(artifactId).put(typeId, value);
                }

            }
            Statement s = rs.getStatement();
            rs.close();
            if (s != null) {
                s.close();
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "SQL Exception occurred: ", ex);
        }
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    public class KeywordHitsRootNode extends DisplayableItemNode {

        public KeywordHitsRootNode() {
            super(Children.create(new KeywordHitsRootChildren(), true), Lookups.singleton(KEYWORD_HITS));
            super.setName(NAME);
            super.setDisplayName(KEYWORD_HITS);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png");
            //long start = System.currentTimeMillis();
            initArtifacts();
            initMaps();
            //long finish = System.currentTimeMillis();
            //logger.info("Process took " + (finish-start) + " ms" );
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public TYPE getDisplayableItemNodeType() {
            return TYPE.ARTIFACT;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty("Name",
                    "Name",
                    "no description",
                    getName()));

            return s;
        }
    }

    private class KeywordHitsRootChildren extends ChildFactory<String> {

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(topLevelMap.keySet());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new KeywordHitsListNode(key, topLevelMap.get(key));
        }
    }

    public class KeywordHitsListNode extends DisplayableItemNode {

        private String name;
        private Map<String, Set<Long>> children;

        public KeywordHitsListNode(String name, Map<String, Set<Long>> children) {
            super(Children.create(new KeywordHitsListChildren(children), true), Lookups.singleton(name));
            super.setName(name);
            int totalDescendants = 0;
            for (Set<Long> grandChildren : children.values()) {
                totalDescendants += grandChildren.size();
            }
            super.setDisplayName(name + " (" + totalDescendants + ")");
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png");
            this.name = name;
            this.children = children;
        }

        @Override
        public TYPE getDisplayableItemNodeType() {
            return TYPE.ARTIFACT;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty("List Name",
                    "List Name",
                    "no description",
                    name));


            ss.put(new NodeProperty("Number of Children",
                    "Number of Children",
                    "no description",
                    children.size()));

            return s;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }
    }

    private class KeywordHitsListChildren extends ChildFactory<String> {

        private Map<String, Set<Long>> children;

        private KeywordHitsListChildren(Map<String, Set<Long>> children) {
            super();
            this.children = children;
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(children.keySet());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new KeywordHitsKeywordNode(key, children.get(key));
        }
    }

    public class KeywordHitsKeywordNode extends DisplayableItemNode {

        private String name;
        private Set<Long> children;

        public KeywordHitsKeywordNode(String name, Set<Long> children) {
            super(Children.create(new KeywordHitsKeywordChildren(children), true), Lookups.singleton(name));
            super.setName(name);
            super.setDisplayName(name + " (" + children.size() + ")");
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword_hits.png");
            this.name = name;
            this.children = children;
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
        public TYPE getDisplayableItemNodeType() {
            return TYPE.ARTIFACT;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }

            ss.put(new NodeProperty("List Name",
                    "List Name",
                    "no description",
                    name));


            ss.put(new NodeProperty("Number of Hits",
                    "Number of Hits",
                    "no description",
                    children.size()));

            return s;
        }
    }

    private class KeywordHitsKeywordChildren extends ChildFactory<BlackboardArtifact> {

        private Set<Long> children;

        private KeywordHitsKeywordChildren(Set<Long> children) {
            super();
            this.children = children;
        }

        @Override
        protected boolean createKeys(List<BlackboardArtifact> list) {
            for (long l : children) {
                try {
                    //TODO: bulk artifact gettings
                    list.add(skCase.getBlackboardArtifact(l));
                } catch (TskException ex) {
                    logger.log(Level.WARNING, "TSK Exception occurred", ex);
                }
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifact artifact) {
            return new BlackboardArtifactNode(artifact);
        }
    }
}
