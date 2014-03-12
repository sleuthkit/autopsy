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
package org.sleuthkit.autopsy.datamodel;

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
import org.sleuthkit.autopsy.directorytree.BlackboardArtifactTagTypeNode;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class are elements of Node hierarchies consisting of
 * content and blackboard artifact tags, grouped first by tag type, then by tag
 * name.
 */
public class TagNameNode extends DisplayableItemNode {

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png";
    private static final String BOOKMARK_TAG_ICON_PATH = "org/sleuthkit/autopsy/images/star-bookmark-icon-16.png";
    private final TagName tagName;
    private static final String CONTENT_TAG_TYPE_NODE_KEY = NbBundle.getMessage(TagNameNode.class,
            "TagNameNode.contentTagTypeNodeKey.text");
    private static final String BLACKBOARD_ARTIFACT_TAG_TYPE_NODE_KEY = NbBundle.getMessage(TagNameNode.class,
            "TagNameNode.bbArtTagTypeNodeKey.text");

    public TagNameNode(TagName tagName) {
        super(Children.create(new TagTypeNodeFactory(tagName), true), Lookups.singleton(
                NbBundle.getMessage(TagNameNode.class, "TagNameNode.namePlusTags.text", tagName.getDisplayName())));
        this.tagName = tagName;

        long tagsCount = 0;
        try {
            tagsCount = Case.getCurrentCase().getServices().getTagsManager().getContentTagsCountByTagName(tagName);
            tagsCount += Case.getCurrentCase().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagName(tagName);
        } catch (TskCoreException ex) {
            Logger.getLogger(TagNameNode.class.getName()).log(Level.SEVERE, "Failed to get tags count for " + tagName.getDisplayName() + " tag name", ex);
        }

        super.setName(tagName.getDisplayName());
        super.setDisplayName(tagName.getDisplayName() + " (" + tagsCount + ")");
        if (tagName.getDisplayName().equals(NbBundle.getMessage(this.getClass(), "TagNameNode.bookmark.text"))) {
            setIconBaseWithExtension(BOOKMARK_TAG_ICON_PATH);
        } else {
            setIconBaseWithExtension(ICON_PATH);
        }
    }

    @Override
    protected Sheet createSheet() {
        Sheet propertySheet = super.createSheet();
        Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
        if (properties == null) {
            properties = Sheet.createPropertiesSet();
            propertySheet.put(properties);
        }

        properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "TagNameNode.createSheet.name.name"),
                NbBundle.getMessage(this.getClass(), "TagNameNode.createSheet.name.displayName"),
                tagName.getDescription(),
                getName()));

        return propertySheet;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        // See classes derived from DisplayableItemNodeVisitor<AbstractNode> 
        // for behavior added using the Visitor pattern.
        return v.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    private static class TagTypeNodeFactory extends ChildFactory<String> {

        private final TagName tagName;

        TagTypeNodeFactory(TagName tagName) {
            this.tagName = tagName;
        }

        @Override
        protected boolean createKeys(List<String> keys) {
            keys.add(CONTENT_TAG_TYPE_NODE_KEY);
            keys.add(BLACKBOARD_ARTIFACT_TAG_TYPE_NODE_KEY);
            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
//            switch (key) {
//                case CONTENT_TAG_TYPE_NODE_KEY:
//                    return new ContentTagTypeNode(tagName);
//                case BLACKBOARD_ARTIFACT_TAG_TYPE_NODE_KEY:
//                    return new BlackboardArtifactTagTypeNode(tagName);
//                default:
//                    Logger.getLogger(TagNameNode.class.getName()).log(Level.SEVERE, "{0} not a recognized key", key);
//                    return null;
//            }
            // converted switch to if/else due to non-constant strings in case key
            if (CONTENT_TAG_TYPE_NODE_KEY.equals(key)) {
                return new ContentTagTypeNode(tagName);
            } else if (BLACKBOARD_ARTIFACT_TAG_TYPE_NODE_KEY.equals(key)) {
                return new BlackboardArtifactTagTypeNode(tagName);
            } else {
                Logger.getLogger(TagNameNode.class.getName()).log(Level.SEVERE, "{0} not a recognized key", key);
                return null;
            }
        }
    }
}
