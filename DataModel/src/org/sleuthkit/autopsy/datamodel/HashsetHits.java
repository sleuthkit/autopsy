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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

/**
 *
 * @author dfickling
 */
public class HashsetHits implements AutopsyVisitableItem {
    
    private static final String HASHSET_HITS = BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getLabel();
    private static final String DISPLAY_NAME = BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName();
    private static final Logger logger = Logger.getLogger(HashsetHits.class.getName());
    
    private SleuthkitCase skCase;
    
    public HashsetHits(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }
    
    public class HashsetHitsRootNode extends AbstractNode implements DisplayableItemNode{

        public HashsetHitsRootNode() {
            super(Children.create(new HashsetHitsRootChildren(), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(HASHSET_HITS);
            List<BlackboardArtifact> arts = new ArrayList<BlackboardArtifact>();
            try {
                arts = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID());
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Error retrieving artifacts", ex);
            }
            super.setDisplayName(DISPLAY_NAME + " (" + arts.size() + ")");
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/hashset_hits.png");
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

            ss.put(new NodeProperty("Name",
                    "Name",
                    "no description",
                    getName()));
            
            return s;
        }
    }
    
    private class HashsetHitsRootChildren extends ChildFactory<BlackboardArtifact> {

        @Override
        protected boolean createKeys(List<BlackboardArtifact> list) {
            try {
                list.addAll(skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()));
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Error getting Blackboard Artifacts", ex);
            }
            return true;
        }
        
        @Override
        protected Node createNodeForKey(BlackboardArtifact key) {
            return new BlackboardArtifactNode(key);
        }
    }
    
}
