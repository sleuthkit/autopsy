/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.util.List;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactTagNode;
import org.sleuthkit.autopsy.datamodel.ContentTagTypeNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class are elements in a sub-tree of the Autopsy
 * presentation of the SleuthKit data model. The sub-tree consists of content 
 * and blackboard artifact tags, grouped first by tag type, then by tag name.
 */
public class BlackboardArtifactTagTypeNode extends DisplayableItemNode {
    private static final String DISPLAY_NAME = NbBundle.getMessage(BlackboardArtifactTagTypeNode.class,
                                                                   "BlackboardArtifactTagTypeNode.displayName.text");
    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png";

    public BlackboardArtifactTagTypeNode(TagName tagName) {
        super(Children.create(new BlackboardArtifactTagNodeFactory(tagName), true), Lookups.singleton(tagName.getDisplayName() + " " + DISPLAY_NAME));

        long tagsCount = 0;
        try {
            tagsCount = Case.getCurrentCase().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagName(tagName);
        }
        catch (TskCoreException ex) {
            Logger.getLogger(BlackboardArtifactTagTypeNode.class.getName()).log(Level.SEVERE, "Failed to get blackboard artifact tags count for " + tagName.getDisplayName() + " tag name", ex);
        }
        
        super.setName(DISPLAY_NAME);
        super.setDisplayName(DISPLAY_NAME + " (" + tagsCount + ")");
        this.setIconBaseWithExtension(ICON_PATH);
    }

    @Override
    protected Sheet createSheet() {
        Sheet propertySheet = super.createSheet();
        Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            propertySheet.put(properties);
        }

        properties.put(new NodeProperty<>(
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagTypeNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagTypeNode.createSheet.name.displayName"),
                "",
                getName()));

        return propertySheet;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
    
    private static class BlackboardArtifactTagNodeFactory extends ChildFactory<BlackboardArtifactTag> {
        private final TagName tagName;
        
        BlackboardArtifactTagNodeFactory(TagName tagName) {
            this.tagName = tagName;
        }
        
        @Override
        protected boolean createKeys(List<BlackboardArtifactTag> keys) {
            try  {
                // Use the blackboard artifact tags bearing the specified tag name as the keys. 
                keys.addAll(Case.getCurrentCase().getServices().getTagsManager().getBlackboardArtifactTagsByTagName(tagName));            
            }
            catch (TskCoreException ex) {
                Logger.getLogger(BlackboardArtifactTagTypeNode.BlackboardArtifactTagNodeFactory.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex);                    
            }                                    
            return true;
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifactTag key) {
            // The blackboard artifact tags to be wrapped are used as the keys. 
            return new BlackboardArtifactTagNode(key);
        }
    }
}