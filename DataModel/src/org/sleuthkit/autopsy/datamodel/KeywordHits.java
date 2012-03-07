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
import java.util.logging.Logger;
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
 *
 * @author dfickling
 */
public class KeywordHits implements AutopsyVisitableItem {
    
    private SleuthkitCase skCase;
    private static final Logger logger = Logger.getLogger(KeywordHits.class.getName());
    private static final String KEYWORD_HITS = "Keyword Hits";
    private static final String LIST_SEARCH = "List Search";
    private static final String SIMPLE_LITERAL_SEARCH = "Single Literal Keyword Search";
    private static final String SIMPLE_REGEX_SEARCH = "Single Regular Expression Search";
    
    // The artifact IDs associated with each type of search
    private Set<Long> listArtifactIds;
    private Set<Long> literalArtifactIds;
    private Set<Long> regexArtifactIds;
    
    // A map of list/keyword/regex name to artifactID
    private Map<String, Set<Long>> listMap;
    private Map<String, Set<Long>> literalWordMap;
    private Map<String, Set<Long>> regexWordMap;
    
    // A map of search type (e.g., list, keyword, regex) to search map (above)
    private Map<String, Map<String, Set<Long>>> artifactMaps;

    
    public KeywordHits(SleuthkitCase skCase) {
        this.skCase = skCase;
        listMap = new LinkedHashMap<String, Set<Long>>();
        literalWordMap = new LinkedHashMap<String, Set<Long>>();
        regexWordMap = new LinkedHashMap<String, Set<Long>>();
        artifactMaps = new LinkedHashMap<String, Map<String, Set<Long>>>();
        artifactMaps.put(LIST_SEARCH, listMap);
        artifactMaps.put(SIMPLE_LITERAL_SEARCH, literalWordMap);
        artifactMaps.put(SIMPLE_REGEX_SEARCH, regexWordMap);
        listArtifactIds = new HashSet<Long>();
        literalArtifactIds = new HashSet<Long>();
        regexArtifactIds = new HashSet<Long>();
        
    }
    
    private void initListMap() {
        listMap.clear();
        listArtifactIds.clear();
        try {
            int typeId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SET.getTypeID();
            String query = "select value_text,artifact_id from blackboard_attributes where " + 
                    "attribute_type_id=" + typeId;// + " and " +
                    //"value_text is not null and " + 
                    //"value_text != \"\"";
            ResultSet rs = skCase.runQuery(query);
            while(rs.next()){
                String listName = rs.getString("value_text");
                long artifactID = rs.getLong("artifact_id");
                if (listName != null && !listName.equals("")) {
                    if (!listMap.containsKey(listName)) {
                        listMap.put(listName, new HashSet<Long>());
                    }
                    listMap.get(listName).add(artifactID);
                    listArtifactIds.add(artifactID);
                }
            }
            Statement s = rs.getStatement();
            rs.close();
            if (s != null)
                s.close();
        } catch (SQLException ex) {
            logger.log(Level.INFO, "SQL Exception occurred: ", ex);
        }
    }
    
    private void initRegexWordMap() {
        regexWordMap.clear();
        regexArtifactIds.clear();
        try {
            int typeId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID();
            StringBuilder query = new StringBuilder("select value_text,artifact_id from blackboard_attributes where attribute_type_id=");
            query.append(typeId);
            ResultSet rs = skCase.runQuery(query.toString());
            while (rs.next()) {
                String expression = rs.getString("value_text");
                long artifactID = rs.getLong("artifact_id");
                if(expression != null && !expression.equals("") && !listArtifactIds.contains(artifactID)) {
                    if (!regexWordMap.containsKey(expression))
                        regexWordMap.put(expression, new HashSet<Long>());
                    regexWordMap.get(expression).add(artifactID);
                    regexArtifactIds.add(artifactID);
                }
            }
            Statement s = rs.getStatement();
            rs.close();
            if (s != null)
                s.close();
        } catch (SQLException ex) {
            logger.log(Level.INFO, "SQL Exception occurred: ", ex);
        }
    }
    
    private void initLiteralWordMap() {
        literalWordMap.clear();
        literalArtifactIds.clear();
        try {
            int typeId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID();
            StringBuilder query = new StringBuilder("select value_text,artifact_id from blackboard_attributes where attribute_type_id=");
            query.append(typeId);
            ResultSet rs = skCase.runQuery(query.toString());
            while (rs.next()) {
                String keyword = rs.getString("value_text");
                long artifactID = rs.getLong("artifact_id");
                if(!listArtifactIds.contains(artifactID) && !regexArtifactIds.contains(artifactID)) {
                    if (!literalWordMap.containsKey(keyword))
                        literalWordMap.put(keyword, new HashSet<Long>());
                    literalWordMap.get(keyword).add(artifactID);
                    literalArtifactIds.add(artifactID);
                }
            }
            Statement s = rs.getStatement();
            rs.close();
            if (s != null)
                s.close();
        } catch (SQLException ex) {
            logger.log(Level.INFO, "SQL Exception occurred: ", ex);
        }
    }
    
    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    public class KeywordHitsRootNode extends AbstractNode implements DisplayableItemNode {


        public KeywordHitsRootNode() {
            super(Children.create(new KeywordHitsRootChildren(), true), Lookups.singleton(KEYWORD_HITS));
            super.setName(KEYWORD_HITS);
            super.setDisplayName(KEYWORD_HITS);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword-search-icon.png");
            initListMap();
            initRegexWordMap();
            initLiteralWordMap();
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

            ss.put(new NodeProperty(KEYWORD_HITS,
                    KEYWORD_HITS,
                    "no description",
                    getName()));
            
            return s;
        }
    }

    class KeywordHitsRootChildren extends ChildFactory<String> {

        @Override
        protected boolean createKeys(List<String> list) {
            list.add(SIMPLE_LITERAL_SEARCH);
            list.add(SIMPLE_REGEX_SEARCH);
            list.addAll(listMap.keySet());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            if(key.equals(SIMPLE_LITERAL_SEARCH) || key.equals(SIMPLE_REGEX_SEARCH)){
                return new KeywordHitsMultiLevelNode(key, artifactMaps.get(key));
            }else
                return new KeywordHitsSetNode(key, listMap.get(key));
        }
    }
    
    public class KeywordHitsMultiLevelNode extends AbstractNode implements DisplayableItemNode {

        String name;
        Map<String, Set<Long>> map;
        public KeywordHitsMultiLevelNode(String name, Map<String, Set<Long>> map) {
            super(Children.create(new KeywordHitsMultiLevelChildren(map), true), Lookups.singleton(name));
            super.setName(name);
            super.setDisplayName(name + " (" +map.size() + ")");
            this.name = name;
            this.map = map;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword-search-icon.png");
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
                    map.size()));
            
            return s;
        }
        
        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }
        
    }
    
    class KeywordHitsMultiLevelChildren extends ChildFactory<String> {

        Map<String, Set<Long>> map;
        
        KeywordHitsMultiLevelChildren(Map<String, Set<Long>> map) {
            super();
            this.map = map;
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(map.keySet());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new KeywordHitsSetNode(key, map.get(key));
        }
    }
    
    public class KeywordHitsSetNode extends AbstractNode implements DisplayableItemNode {

        String setName;
        Set<Long> artifacts;
        public KeywordHitsSetNode(String name, Set<Long> artifacts) {
            super(Children.create(new KeywordHitsSetChildren(name, artifacts), true), Lookups.singleton(name));
            super.setName(name);
            super.setDisplayName(name + " (" + artifacts.size() + ")");
            setName = name;
            this.artifacts = artifacts;
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/keyword-search-icon.png");
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

            ss.put(new NodeProperty("List Name",
                    "List Name",
                    "no description",
                    setName));
            
            
            ss.put(new NodeProperty("Number of Hits",
                    "Number of Hits",
                    "no description",
                    artifacts.size()));
            
            return s;
        }
    }

    class KeywordHitsSetChildren extends ChildFactory<BlackboardArtifact> {

        String setName;
        Set<Long> artifacts;
        KeywordHitsSetChildren(String child, Set<Long> artifacts) {
            super();
            this.setName = child;
            this.artifacts = artifacts;
        }

        @Override
        protected boolean createKeys(List<BlackboardArtifact> list) {
            for (long l :artifacts) {
                try {
                    //TODO: bulk artifact gettings
                    list.add(skCase.getBlackboardArtifact(l));
                } catch (TskException ex) {
                    logger.log(Level.INFO, "TSK Exception occurred", ex);
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
