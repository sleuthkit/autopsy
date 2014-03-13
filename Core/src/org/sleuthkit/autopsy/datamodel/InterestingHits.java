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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;


public class InterestingHits implements AutopsyVisitableItem {
    
    private static final String INTERESTING_ITEMS = NbBundle
            .getMessage(InterestingHits.class, "InterestingHits.interestingItems.text");
    private static final String DISPLAY_NAME = NbBundle.getMessage(InterestingHits.class, "InterestingHits.displayName.text");
    private static final Logger logger = Logger.getLogger(InterestingHits.class.getName());
    private SleuthkitCase skCase;
    private Map<String, Set<Long>> interestingItemsMap;
   
    public InterestingHits(SleuthkitCase skCase) {
        this.skCase = skCase;
        interestingItemsMap = new LinkedHashMap<>();
    }
 
    @SuppressWarnings("deprecation")
    private void initArtifacts() {
        interestingItemsMap.clear();
        loadArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        loadArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
    }
    
    /*
     * Reads the artifacts of specified type, grouped by Set, and loads into the interestingItemsMap
     */
    private void loadArtifacts(BlackboardArtifact.ARTIFACT_TYPE artType) {
        ResultSet rs = null;
        try {
            int setNameId = BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID();
            int artId = artType.getTypeID();
            String query = "SELECT value_text,blackboard_attributes.artifact_id,attribute_type_id "
                    + "FROM blackboard_attributes,blackboard_artifacts WHERE "
                    + "attribute_type_id=" + setNameId
                    + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id"
                    + " AND blackboard_artifacts.artifact_type_id=" + artId;
            rs = skCase.runQuery(query);
            while (rs.next()) {
                String value = rs.getString("value_text");
                long artifactId = rs.getLong("artifact_id");
                if (!interestingItemsMap.containsKey(value)) {
                    interestingItemsMap.put(value, new HashSet<Long>());
                }
                interestingItemsMap.get(value).add(artifactId);
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "SQL Exception occurred: ", ex);
        }
        finally {
            if (rs != null) {
                try {
                    skCase.closeRunQuery(rs);
                } catch (SQLException ex) {
                   logger.log(Level.WARNING, "Error closing result set after getting artifacts", ex);
                }
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
    public class InterestingHitsRootNode extends DisplayableItemNode {

        public InterestingHitsRootNode() {
            super(Children.create(new InterestingHitsRootChildren(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(INTERESTING_ITEMS);
            super.setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png");
            initArtifacts();
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
    }
    
     private class InterestingHitsRootChildren extends ChildFactory<String> {

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(interestingItemsMap.keySet());
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new InterestingHitsSetNode(key, interestingItemsMap.get(key));
        }
    }
     
    public class InterestingHitsSetNode extends DisplayableItemNode {

        public InterestingHitsSetNode(String name, Set<Long> children) {
            super(Children.create(new InterestingHitsSetChildren(children), true), Lookups.singleton(name));
            super.setName(name);
            super.setDisplayName(name + " (" + children.size() + ")");
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/interesting_item.png");
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
    }
     
    private class InterestingHitsSetChildren extends ChildFactory<BlackboardArtifact> {

        private Set<Long> children;

        private InterestingHitsSetChildren(Set<Long> children) {
            super();
            this.children = children;
        }

        @Override
        protected boolean createKeys(List<BlackboardArtifact> list) {
            for (long l : children) {
                try {
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
