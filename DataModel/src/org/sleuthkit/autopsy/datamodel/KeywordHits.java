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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    
    SleuthkitCase skCase;
    private static final Logger logger = Logger.getLogger(KeywordHits.class.getName());
    private static final String KEYWORD_HITS = "Keyword Hits";
    private static final String SIMPLE_SEARCH = "Single Keyword Search";
    //Map<String, Map<String, List<BlackboardArtifact>>> artifactMap = new HashMap<String, Map<String, List<BlackboardArtifact>>>();
    Map<String, List<Long>> listMap = new LinkedHashMap<String, List<Long>>();

    public KeywordHits(SleuthkitCase skCase) {
        this.skCase = skCase;
        initMap();
    }
    
    private void initMap() {
        listMap.clear();
        try {
            String query = "select value_text,artifact_id from blackboard_attributes where attribute_type_id=" + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SET.getTypeID();
            ResultSet rs = skCase.runQuery(query);
            listMap.put(SIMPLE_SEARCH, new ArrayList<Long>());
            while(rs.next()){
                String listName = rs.getString("value_text");
                if(listName == null || listName.equals(""))
                    listName = SIMPLE_SEARCH;
                long artifactID = rs.getLong("artifact_id");
                if(!listMap.containsKey(listName))
                    listMap.put(listName, new ArrayList<Long>());
                listMap.get(listName).add(artifactID);
            }
            if(listMap.get(SIMPLE_SEARCH).isEmpty())
                listMap.remove(SIMPLE_SEARCH);
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
            initMap();
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

        KeywordHitsRootChildren() {
            super();
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(listMap.keySet());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new KeywordHitsSetNode(key);
        }
    }
    
    public class KeywordHitsSetNode extends AbstractNode implements DisplayableItemNode {

        String setName;
        public KeywordHitsSetNode(String key) {
            super(Children.create(new KeywordHitsSetChildren(key), true), Lookups.singleton(key));
            super.setName(key);
            super.setDisplayName(key + " (" + listMap.get(key).size() + ")");
            setName = key;
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
                    listMap.get(setName).size()));
            
            return s;
        }
    }

    class KeywordHitsSetChildren extends ChildFactory<BlackboardArtifact> {

        String setName;
        KeywordHitsSetChildren(String setName) {
            super();
            this.setName = setName;
        }

        @Override
        protected boolean createKeys(List<BlackboardArtifact> list) {
            for (long l : listMap.get(setName)) {
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
