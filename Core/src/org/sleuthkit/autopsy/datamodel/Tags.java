/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.tags.TagUtils;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class act as keys for use by instances of the
 * RootContentChildren class. RootContentChildren is a NetBeans child node
 * factory built on top of the NetBeans Children.Keys class.
 */
public class Tags implements AutopsyVisitableItem {
    // Creation of a RootNode object corresponding to a Tags object is done
    // by a CreateAutopsyNodeVisitor dispatched from the AbstractContentChildren
    // override of Children.Keys<T>.createNodes().

    private final static String DISPLAY_NAME = NbBundle.getMessage(RootNode.class, "TagsNode.displayName.text");
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED);
    private static final String USER_NAME_PROPERTY = "user.name"; //NON-NLS
    private final TagResults tagResults = new TagResults();
    private final String ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png"; //NON-NLS

    private final long filteringDSObjId; // 0 if not filtering/grouping by data source

    Tags() {
        this(0);
    }

    Tags(long dsObjId) {
        this.filteringDSObjId = dsObjId;
    }

    /**
     * Return the display name used by the tags node in the tree.
     *
     * @return - DISPLAY_NAME
     */
    public static String getTagsDisplayName() {
        return DISPLAY_NAME;
    }

    long filteringDataSourceObjId() {
        return this.filteringDSObjId;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * This class largely does nothing except act as a top-level object that the
     * other nodes can listen to. This mimics what other nodes have (keword
     * search, etc.), but theirs stores data.
     */
    private class TagResults extends Observable {

        public void update() {
            setChanged();
            notifyObservers();
        }
    }

    /**
     * Instances of this class are the root nodes of tree that is a sub-tree of
     * the Autopsy presentation of the SleuthKit data model. The sub-tree
     * consists of content and blackboard artifact tags, grouped first by tag
     * type, then by tag name.
     */
    public class RootNode extends DisplayableItemNode {

        public RootNode(long objId) {
            super(Children.create(new TagNameNodeFactory(objId), true), Lookups.singleton(DISPLAY_NAME));
            super.setName(DISPLAY_NAME);
            super.setDisplayName(DISPLAY_NAME);
            this.setIconBaseWithExtension(ICON_PATH);
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        protected Sheet createSheet() {
            Sheet propertySheet = super.createSheet();
            Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
            if (properties == null) {
                properties = Sheet.createPropertiesSet();
                propertySheet.put(properties);
            }
            properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "TagsNode.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "TagsNode.createSheet.name.displayName"), "", getName()));
            return propertySheet;
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        /**
         * Cause the contents of the RootNode and its children to be updated.
         */
        public void refresh() {
            tagResults.update();
        }

    }

    private class TagNameNodeFactory extends ChildFactory.Detachable<TagName> implements Observer {

        private final long filteringDSObjId; // 0 if not filtering/grouping by data source

        private final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED,
                Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED,
                Case.Events.CONTENT_TAG_ADDED,
                Case.Events.CONTENT_TAG_DELETED,
                Case.Events.CURRENT_CASE);

        private final PropertyChangeListener pcl = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String eventType = evt.getPropertyName();
                if (eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_ADDED.toString())
                        || eventType.equals(Case.Events.BLACKBOARD_ARTIFACT_TAG_DELETED.toString())
                        || eventType.equals(Case.Events.CONTENT_TAG_ADDED.toString())
                        || eventType.equals(Case.Events.CONTENT_TAG_DELETED.toString())) {
                    /**
                     * Checking for a current case is a stop gap measure until a
                     * different way of handling the closing of cases is worked
                     * out. Currently, remote events may be received for a case
                     * that is already closed.
                     */
                    try {
                        Case.getCurrentCaseThrows();
                        refresh(true);
                        tagResults.update();
                    } catch (NoCurrentCaseException notUsed) {
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
                        Case.getCurrentCaseThrows();
                        refresh(true);
                        tagResults.update();
                    } catch (NoCurrentCaseException notUsed) {
                        /**
                         * Case is closed, do nothing.
                         */
                    }
                } else if (eventType.equals(Case.Events.CURRENT_CASE.toString())) {
                    // case was closed. Remove listeners so that this can be garbage collected
                    if (evt.getNewValue() == null) {
                        removeNotify();
                    }
                }
            }
        };
        
        private final PropertyChangeListener weakPcl = WeakListeners.propertyChange(pcl, null);

        /**
         * Constructor
         *
         * @param objId data source object id
         */
        TagNameNodeFactory(long objId) {
            this.filteringDSObjId = objId;
        }
        
        @Override
        protected void addNotify() {
            IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, weakPcl);
            Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
            tagResults.update();
            tagResults.addObserver(this);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            IngestManager.getInstance().removeIngestJobEventListener(weakPcl);
            Case.removeEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, weakPcl);
            tagResults.deleteObserver(this);
        }

        @Override
        protected boolean createKeys(List<TagName> keys) {
            try {
                List<TagName> tagNamesInUse;
                if (UserPreferences.showOnlyCurrentUserTags()) {
                    String userName = System.getProperty(USER_NAME_PROPERTY);
                    tagNamesInUse = (filteringDSObjId > 0)
                            ? Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUseForUser(filteringDSObjId, userName)
                            : Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUseForUser(userName);
                } else {
                    tagNamesInUse = (filteringDSObjId > 0)
                            ? Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUse(filteringDSObjId)
                            : Case.getCurrentCaseThrows().getServices().getTagsManager().getTagNamesInUse();
                }
                Collections.sort(tagNamesInUse, new Comparator<TagName>() {
                    @Override
                    public int compare(TagName o1, TagName o2) {
                        return TagUtils.getDecoratedTagDisplayName(o1).compareTo(TagUtils.getDecoratedTagDisplayName(o2));
                    }
                });
                keys.addAll(tagNamesInUse);
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(TagNameNodeFactory.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(TagName key) {
            return new TagNameNode(key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    /**
     * Instances of this class are elements of Node hierarchies consisting of
     * content and blackboard artifact tags, grouped first by tag type, then by
     * tag name.
     */
    public class TagNameNode extends DisplayableItemNode implements Observer {

        private final String ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png"; //NON-NLS
        private final String BOOKMARK_TAG_ICON_PATH = "org/sleuthkit/autopsy/images/star-bookmark-icon-16.png"; //NON-NLS
        private final TagName tagName;

        public TagNameNode(TagName tagName) {
            super(Children.create(new TagTypeNodeFactory(tagName), true), Lookups.singleton(NbBundle.getMessage(TagNameNode.class, "TagNameNode.namePlusTags.text", tagName.getDisplayName())));
            this.tagName = tagName;
            setName(TagUtils.getDecoratedTagDisplayName(tagName));
            updateDisplayName();
            if (tagName.getDisplayName().equals(TagsManager.getBookmarkTagDisplayName())) {
                setIconBaseWithExtension(BOOKMARK_TAG_ICON_PATH);
            } else {
                setIconBaseWithExtension(ICON_PATH);
            }
            tagResults.addObserver(this);
        }

        private void updateDisplayName() {
            long tagsCount = 0;
            try {
                TagsManager tm = Case.getCurrentCaseThrows().getServices().getTagsManager();
                if (UserPreferences.showOnlyCurrentUserTags()) {
                    String userName = System.getProperty(USER_NAME_PROPERTY);
                    if (filteringDSObjId > 0) {
                        tagsCount = tm.getContentTagsCountByTagNameForUser(tagName, filteringDSObjId, userName);
                        tagsCount += tm.getBlackboardArtifactTagsCountByTagNameForUser(tagName, filteringDSObjId, userName);
                    } else {
                        tagsCount = tm.getContentTagsCountByTagNameForUser(tagName, userName);
                        tagsCount += tm.getBlackboardArtifactTagsCountByTagNameForUser(tagName, userName);
                    }
                } else {
                    if (filteringDSObjId > 0) {
                        tagsCount = tm.getContentTagsCountByTagName(tagName, filteringDSObjId);
                        tagsCount += tm.getBlackboardArtifactTagsCountByTagName(tagName, filteringDSObjId);
                    } else {
                        tagsCount = tm.getContentTagsCountByTagName(tagName);
                        tagsCount += tm.getBlackboardArtifactTagsCountByTagName(tagName);
                    }
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(TagNameNode.class.getName()).log(Level.SEVERE, "Failed to get tags count for " + tagName.getDisplayName() + " tag name", ex); //NON-NLS
            }
            setDisplayName(TagUtils.getDecoratedTagDisplayName(tagName) + " (" + tagsCount + ")");
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
                    NbBundle.getMessage(this.getClass(), "TagNameNode.createSheet.name.displayName"), tagName.getDescription(), getName()));
            return propertySheet;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            // See classes derived from DisplayableItemNodeVisitor<AbstractNode>
            // for behavior added using the Visitor pattern.
            return visitor.visit(this);
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
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
     * Creates nodes for the two types of tags: file and artifact. Does not need
     * observer / messages since it always has the same children
     */
    private class TagTypeNodeFactory extends ChildFactory<String> {

        private final TagName tagName;
        private final String CONTENT_TAG_TYPE_NODE_KEY = NbBundle.getMessage(TagNameNode.class, "TagNameNode.contentTagTypeNodeKey.text");
        private final String BLACKBOARD_ARTIFACT_TAG_TYPE_NODE_KEY = NbBundle.getMessage(TagNameNode.class, "TagNameNode.bbArtTagTypeNodeKey.text");

        TagTypeNodeFactory(TagName tagName) {
            super();
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
            if (CONTENT_TAG_TYPE_NODE_KEY.equals(key)) {
                return new ContentTagTypeNode(tagName);
            } else if (BLACKBOARD_ARTIFACT_TAG_TYPE_NODE_KEY.equals(key)) {
                return new BlackboardArtifactTagTypeNode(tagName);
            } else {
                Logger.getLogger(TagNameNode.class.getName()).log(Level.SEVERE, "{0} not a recognized key", key); //NON-NLS
                return null;
            }
        }
    }

    private final String CONTENT_DISPLAY_NAME = NbBundle.getMessage(ContentTagTypeNode.class, "ContentTagTypeNode.displayName.text");

    /**
     * Node for the content tags. Children are specific tags. Instances of this
     * class are are elements of a directory tree sub-tree consisting of content
     * and blackboard artifact tags, grouped first by tag type, then by tag
     * name.
     */
    public class ContentTagTypeNode extends DisplayableItemNode implements Observer {

        private final String ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png"; //NON-NLS
        private final TagName tagName;

        public ContentTagTypeNode(TagName tagName) {
            super(Children.create(new ContentTagNodeFactory(tagName), true), Lookups.singleton(tagName.getDisplayName() + " " + CONTENT_DISPLAY_NAME));
            this.tagName = tagName;
            super.setName(CONTENT_DISPLAY_NAME);
            updateDisplayName();
            this.setIconBaseWithExtension(ICON_PATH);
            tagResults.addObserver(this);
        }

        private void updateDisplayName() {
            long tagsCount = 0;
            try {

                if (UserPreferences.showOnlyCurrentUserTags()) {
                    String userName = System.getProperty(USER_NAME_PROPERTY);
                    tagsCount = (filteringDSObjId > 0)
                            ? Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsCountByTagNameForUser(tagName, filteringDSObjId, userName)
                            : Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsCountByTagNameForUser(tagName, userName);
                } else {
                    tagsCount = (filteringDSObjId > 0)
                            ? Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsCountByTagName(tagName, filteringDSObjId)
                            : Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsCountByTagName(tagName);
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(ContentTagTypeNode.class.getName()).log(Level.SEVERE, "Failed to get content tags count for " + tagName.getDisplayName() + " tag name", ex); //NON-NLS
            }
            super.setDisplayName(CONTENT_DISPLAY_NAME + " (" + tagsCount + ")");
        }

        @Override
        protected Sheet createSheet() {
            Sheet propertySheet = super.createSheet();
            Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
            if (properties == null) {
                properties = Sheet.createPropertiesSet();
                propertySheet.put(properties);
            }
            properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "ContentTagTypeNode.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "ContentTagTypeNode.createSheet.name.displayName"), "", getName()));
            return propertySheet;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
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

    private class ContentTagNodeFactory extends ChildFactory<ContentTag> implements Observer {

        private final TagName tagName;

        ContentTagNodeFactory(TagName tagName) {
            super();
            this.tagName = tagName;
            tagResults.addObserver(this);
        }

        @Override
        protected boolean createKeys(List<ContentTag> keys) {
            // Use the content tags bearing the specified tag name as the keys.
            try {
                List<ContentTag> contentTags = (filteringDSObjId > 0)
                        ? Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByTagName(tagName, filteringDSObjId)
                        : Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByTagName(tagName);
                if (UserPreferences.showOnlyCurrentUserTags()) {
                    String userName = System.getProperty(USER_NAME_PROPERTY);
                    for (ContentTag tag : contentTags) {
                        if (userName.equals(tag.getUserName())) {
                            keys.add(tag);
                        }
                    }
                } else {
                    keys.addAll(contentTags);
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(ContentTagNodeFactory.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(ContentTag key) {
            // The content tags to be wrapped are used as the keys.
            return new ContentTagNode(key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }

    private final String ARTIFACT_DISPLAY_NAME = NbBundle.getMessage(BlackboardArtifactTagTypeNode.class, "BlackboardArtifactTagTypeNode.displayName.text");

    /**
     * Instances of this class are elements in a sub-tree of the Autopsy
     * presentation of the SleuthKit data model. The sub-tree consists of
     * content and blackboard artifact tags, grouped first by tag type, then by
     * tag name.
     */
    public class BlackboardArtifactTagTypeNode extends DisplayableItemNode implements Observer {

        private final TagName tagName;
        private final String ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png"; //NON-NLS

        public BlackboardArtifactTagTypeNode(TagName tagName) {
            super(Children.create(new BlackboardArtifactTagNodeFactory(tagName), true), Lookups.singleton(tagName.getDisplayName() + " " + ARTIFACT_DISPLAY_NAME));
            this.tagName = tagName;
            super.setName(ARTIFACT_DISPLAY_NAME);
            this.setIconBaseWithExtension(ICON_PATH);
            updateDisplayName();
            tagResults.addObserver(this);
        }

        private void updateDisplayName() {
            long tagsCount = 0;
            try {
                if (UserPreferences.showOnlyCurrentUserTags()) {
                    String userName = System.getProperty(USER_NAME_PROPERTY);
                    tagsCount = (filteringDSObjId > 0)
                            ? Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagNameForUser(tagName, filteringDSObjId, userName)
                            : Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagNameForUser(tagName, userName);
                } else {
                    tagsCount = (filteringDSObjId > 0)
                            ? Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagName(tagName, filteringDSObjId)
                            : Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsCountByTagName(tagName);
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(BlackboardArtifactTagTypeNode.class.getName()).log(Level.SEVERE, "Failed to get blackboard artifact tags count for " + tagName.getDisplayName() + " tag name", ex); //NON-NLS
            }
            super.setDisplayName(ARTIFACT_DISPLAY_NAME + " (" + tagsCount + ")");
        }

        @Override
        protected Sheet createSheet() {
            Sheet propertySheet = super.createSheet();
            Sheet.Set properties = propertySheet.get(Sheet.PROPERTIES);
            if (properties == null) {
                properties = Sheet.createPropertiesSet();
                propertySheet.put(properties);
            }
            properties.put(new NodeProperty<>(NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagTypeNode.createSheet.name.name"),
                    NbBundle.getMessage(this.getClass(), "BlackboardArtifactTagTypeNode.createSheet.name.displayName"), "", getName()));
            return propertySheet;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
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

    private class BlackboardArtifactTagNodeFactory extends ChildFactory<BlackboardArtifactTag> implements Observer {

        private final TagName tagName;

        BlackboardArtifactTagNodeFactory(TagName tagName) {
            super();
            this.tagName = tagName;
            tagResults.addObserver(this);
        }

        @Override
        protected boolean createKeys(List<BlackboardArtifactTag> keys) {
            try {
                // Use the blackboard artifact tags bearing the specified tag name as the keys.
                List<BlackboardArtifactTag> artifactTags = (filteringDSObjId > 0)
                        ? Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByTagName(tagName, filteringDSObjId)
                        : Case.getCurrentCaseThrows().getServices().getTagsManager().getBlackboardArtifactTagsByTagName(tagName);
                if (UserPreferences.showOnlyCurrentUserTags()) {
                    String userName = System.getProperty(USER_NAME_PROPERTY);
                    for (BlackboardArtifactTag tag : artifactTags) {
                        if (userName.equals(tag.getUserName())) {
                            keys.add(tag);
                        }
                    }
                } else {
                    keys.addAll(artifactTags);
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(BlackboardArtifactTagNodeFactory.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifactTag key) {
            // The blackboard artifact tags to be wrapped are used as the keys.
            return new BlackboardArtifactTagNode(key);
        }

        @Override
        public void update(Observable o, Object arg) {
            refresh(true);
        }
    }
}
