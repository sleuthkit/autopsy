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

import java.awt.event.ActionEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.BlackboardResultViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Support for tags in the directory tree. Tag nodes representing file and
 * result tags, encapsulate TSK_TAG_FILE and TSK_TAG_ARTIFACT typed artifacts.
 *
 * The class implements querying of data model and populating node hierarchy
 * using child factories.
 *
 */
public class Tags implements AutopsyVisitableItem {

    private static final Logger logger = Logger.getLogger(Tags.class.getName());
    private static final String FILE_TAG_LABEL_NAME = "File Tags";
    private static final String RESULT_TAG_LABEL_NAME = "Result Tags";
    private SleuthkitCase skCase;
    public static final String NAME = "Tags";
    private static final String TAG_ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png";
    //bookmarks are specializations of tags
    public static final String BOOKMARK_TAG_NAME = "Bookmark";
    private static final String BOOKMARK_ICON_PATH = "org/sleuthkit/autopsy/images/star-bookmark-icon-16.png";
    private Map<BlackboardArtifact.ARTIFACT_TYPE, Map<String, List<BlackboardArtifact>>> tags;
    private static final String EMPTY_COMMENT = "";
    private static final String APP_SETTINGS_FILE_NAME = "app"; // @@@ TODO: Need a general app settings or user preferences file, this will do for now.
    private static final String TAG_NAMES_SETTING_KEY = "tag_names";    
    private static final HashSet<String> appSettingTagNames = new HashSet<>();
    private static final StringBuilder tagNamesAppSetting = new StringBuilder();

    // When this class is loaded, either create an new app settings file or 
    // get the tag names setting from the existing app settings file.
    static {
        String setting = ModuleSettings.getConfigSetting(APP_SETTINGS_FILE_NAME, TAG_NAMES_SETTING_KEY);
        if (null != setting && !setting.isEmpty()) {                
            // Make a speedy lookup for the tag names in the setting to aid in the
            // detection of new tag names.
            List<String> tagNamesFromAppSettings = Arrays.asList(setting.split(","));
            for (String tagName : tagNamesFromAppSettings) {
                appSettingTagNames.add(tagName);
            }

            // Load the raw comma separated values list from the setting into a 
            // string builder to facilitate adding new tag names to the list and writing
            // it back to the app settings file.
            tagNamesAppSetting.append(setting);                
        }                    
    }
    
    Tags(SleuthkitCase skCase) {
        this.skCase = skCase;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> v) {
        return v.visit(this);
    }

    /**
     * Root of all Tag nodes. This node is shown directly under Results in the
     * directory tree.
     */
    public class TagsRootNode extends DisplayableItemNode {

        public TagsRootNode() {
            super(Children.create(new Tags.TagsRootChildren(), true), Lookups.singleton(NAME));
            super.setName(NAME);
            super.setDisplayName(NAME);
            this.setIconBaseWithExtension(TAG_ICON_PATH);
            initData();
        }

        private void initData() {
            try {
                // Get all file and artifact tags

                //init data
                tags = new EnumMap<>(BlackboardArtifact.ARTIFACT_TYPE.class);
                tags.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE, new HashMap<String, List<BlackboardArtifact>>());
                tags.put(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT, new HashMap<String, List<BlackboardArtifact>>());

                //populate
                for (BlackboardArtifact.ARTIFACT_TYPE artType : tags.keySet()) {
                    final Map<String, List<BlackboardArtifact>> artTags = tags.get(artType);
                    for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(artType)) {
                        for (BlackboardAttribute attribute : artifact.getAttributes()) {
                            if (attribute.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID()) {
                                String tagName = attribute.getValueString();
                                if (artTags.containsKey(tagName)) {
                                    List<BlackboardArtifact> artifacts = artTags.get(tagName);
                                    artifacts.add(artifact);
                                } else {
                                    List<BlackboardArtifact> artifacts = new ArrayList<>();
                                    artifacts.add(artifact);
                                    artTags.put(tagName, artifacts);
                                }
                                break;
                            }
                        }
                    }
                }


            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Count not initialize tag nodes", ex);
            }
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

        @Override
        public DisplayableItemNode.TYPE getDisplayableItemNodeType() {
            return DisplayableItemNode.TYPE.ARTIFACT;
        }
    }

    /**
     * bookmarks root child node creating types of bookmarks nodes
     */
    private class TagsRootChildren extends ChildFactory<BlackboardArtifact.ARTIFACT_TYPE> {

        @Override
        protected boolean createKeys(List<BlackboardArtifact.ARTIFACT_TYPE> list) {
            for (BlackboardArtifact.ARTIFACT_TYPE artType : tags.keySet()) {
                list.add(artType);
            }

            return true;
        }

        @Override
        protected Node createNodeForKey(BlackboardArtifact.ARTIFACT_TYPE key) {
            return new TagsNodeRoot(key, tags.get(key));
        }
    }

    /**
     * Tag node representation (file or result)
     */
    public class TagsNodeRoot extends DisplayableItemNode {

        TagsNodeRoot(BlackboardArtifact.ARTIFACT_TYPE tagType, Map<String, List<BlackboardArtifact>> subTags) {
            super(Children.create(new TagRootChildren(tagType, subTags), true), Lookups.singleton(tagType.getDisplayName()));

            String name = null;
            if (tagType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE)) {
                name = FILE_TAG_LABEL_NAME;
            } else if (tagType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT)) {
                name = RESULT_TAG_LABEL_NAME;
            }

            super.setName(name);
            super.setDisplayName(name + " (" + subTags.values().size() + ")");

            this.setIconBaseWithExtension(TAG_ICON_PATH);
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

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public TYPE getDisplayableItemNodeType() {
            return TYPE.META;
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }
    }

    /**
     * Child factory to add all the Tag artifacts to a TagsRootNode with the tag
     * name.
     */
    private class TagRootChildren extends ChildFactory<String> {

        private Map<String, List<BlackboardArtifact>> subTags;
        private BlackboardArtifact.ARTIFACT_TYPE tagType;

        TagRootChildren(BlackboardArtifact.ARTIFACT_TYPE tagType, Map<String, List<BlackboardArtifact>> subTags) {
            super();
            this.tagType = tagType;
            this.subTags = subTags;
        }

        @Override
        protected boolean createKeys(List<String> list) {
            list.addAll(subTags.keySet());

            return true;
        }

        @Override
        protected Node createNodeForKey(String key) {
            return new Tags.TagNodeRoot(tagType, key, subTags.get(key));
        }
    }

    /**
     * Node for each unique tag name. Shown directly under Results > Tags.
     */
    public class TagNodeRoot extends DisplayableItemNode {

        TagNodeRoot(BlackboardArtifact.ARTIFACT_TYPE tagType, String tagName, List<BlackboardArtifact> artifacts) {
            super(Children.create(new Tags.TagsChildrenNode(tagType, tagName, artifacts), true), Lookups.singleton(tagName));

            super.setName(tagName);
            super.setDisplayName(tagName + " (" + artifacts.size() + ")");

            if (tagName.equals(BOOKMARK_TAG_NAME)) {
                this.setIconBaseWithExtension(BOOKMARK_ICON_PATH);
            } else {
                this.setIconBaseWithExtension(TAG_ICON_PATH);
            }
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

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public DisplayableItemNode.TYPE getDisplayableItemNodeType() {
            return DisplayableItemNode.TYPE.ARTIFACT;
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }
    }

    /**
     * Node representing an individual Tag artifact. For each TagsNodeRoot under
     * Results > Tags, this is one of the nodes listed in the result viewer.
     */
    private class TagsChildrenNode extends ChildFactory<BlackboardArtifact> {

        private List<BlackboardArtifact> artifacts;
        private BlackboardArtifact.ARTIFACT_TYPE tagType;
        private String tagName;

        private TagsChildrenNode(BlackboardArtifact.ARTIFACT_TYPE tagType, String tagName, List<BlackboardArtifact> artifacts) {
            super();
            this.tagType = tagType;
            this.tagName = tagName;
            this.artifacts = artifacts;
        }

        @Override
        protected boolean createKeys(List<BlackboardArtifact> list) {
            list.addAll(artifacts);
            return true;
        }

        @Override
        protected Node createNodeForKey(final BlackboardArtifact artifact) {
            //create node with action
            BlackboardArtifactNode tagNode = null;

            String iconPath;
            if (tagName.equals(BOOKMARK_TAG_NAME)) {
                iconPath = BOOKMARK_ICON_PATH;
            } else {
                iconPath = TAG_ICON_PATH;
            }

            //create actions here where Tag logic belongs
            //instead of DataResultFilterNode w/visitors, which is much less pluggable and cluttered
            if (tagType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT)) {
                //in case of result tag, add a action by sublcassing bb art node
                //this action will be merged with other actions set  DataResultFIlterNode
                //otherwise in case of 
                tagNode = new BlackboardArtifactNode(artifact, iconPath) {
                    @Override
                    public Action[] getActions(boolean bln) {
                        //Action [] actions = super.getActions(bln); //To change body of generated methods, choose Tools | Templates.
                        Action[] actions = new Action[1];
                        actions[0] = new AbstractAction("View Source Result") {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                //open the source artifact in dir tree
                                BlackboardArtifact sourceArt = Tags.getArtifactFromTag(artifact.getArtifactID());
                                if (sourceArt != null) {
                                    BlackboardResultViewer v = Lookup.getDefault().lookup(BlackboardResultViewer.class);
                                    v.viewArtifact(sourceArt);
                                }
                            }
                        };
                        return actions;
                    }
                };
            } else {
                //for file tag, don't subclass to add the additional actions
                tagNode = new BlackboardArtifactNode(artifact, iconPath);
            }

            //add some additional node properties
            int artifactTypeID = artifact.getArtifactTypeID();
             final String NO_DESCR = "no description";
            if (artifactTypeID == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                BlackboardArtifact sourceResult = Tags.getArtifactFromTag(artifact.getArtifactID());
                String resultType = sourceResult.getDisplayName();

                NodeProperty resultTypeProp = new NodeProperty("Source Result Type",
                        "Result Type",
                        NO_DESCR,
                        resultType);


                tagNode.addNodeProperty(resultTypeProp);

            }
            try {
                //add source path property
                 final AbstractFile sourceFile = skCase.getAbstractFileById(artifact.getObjectID());
                 final String sourcePath = sourceFile.getUniquePath();
                 NodeProperty sourcePathProp = new NodeProperty("Source File Path",
                        "Source File Path",
                        NO_DESCR,
                        sourcePath);


                tagNode.addNodeProperty(sourcePathProp);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting a file from artifact to get source file path for a tag, ", ex);
            }
            
            return tagNode;
        }
    }

    /**
     * Create a tag for a file with TSK_TAG_NAME as tagName.
     *
     * @param file to create tag for
     * @param tagName TSK_TAG_NAME
     * @param comment the tag comment, or null if not present
     */
    public static void createTag(AbstractFile file, String tagName, String comment) {
        try {
            final BlackboardArtifact bookArt = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
            List<BlackboardAttribute> attrs = new ArrayList<>();


            BlackboardAttribute attr1 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID(),
                    "", tagName);
            attrs.add(attr1);

            if (comment != null && !comment.isEmpty()) {
                BlackboardAttribute attr2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(),
                        "", comment);
                attrs.add(attr2);
            }
            bookArt.addAttributes(attrs);
            
            updateTagNamesAppSetting(tagName);            
        } 
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to create tag for " + file.getName(), ex);
        }
    }

    /**
     * Create a tag for an artifact with TSK_TAG_NAME as tagName.
     *
     * @param artifact to create tag for
     * @param tagName TSK_TAG_NAME
     * @param comment the tag comment or null if not present
     */
    public static void createTag(BlackboardArtifact artifact, String tagName, String comment) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();

            AbstractFile file = skCase.getAbstractFileById(artifact.getObjectID());
            final BlackboardArtifact bookArt = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
            List<BlackboardAttribute> attrs = new ArrayList<>();


            BlackboardAttribute attr1 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID(),
                    "", tagName);

            if (comment != null && !comment.isEmpty()) {
                BlackboardAttribute attr2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(),
                        "", comment);
                attrs.add(attr2);
            }

            BlackboardAttribute attr3 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID(),
                    "", artifact.getArtifactID());
            attrs.add(attr1);

            attrs.add(attr3);
            bookArt.addAttributes(attrs);     
            
            updateTagNamesAppSetting(tagName);
        } 
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to create tag for artifact " + artifact.getArtifactID(), ex);
        }
    }

    private static void updateTagNamesAppSetting(String tagName) {
        // If this tag name is not in the current tag names app setting...
        if (!appSettingTagNames.contains(tagName)) {
            // Add it to the lookup.
            appSettingTagNames.add(tagName);
            
            // Add it to the setting and write the setting back to the app settings file.
            if (tagNamesAppSetting.length() != 0) {
                tagNamesAppSetting.append(",");
            }
            tagNamesAppSetting.append(tagName);
            ModuleSettings.setConfigSetting(APP_SETTINGS_FILE_NAME, TAG_NAMES_SETTING_KEY, tagNamesAppSetting.toString());
        }        
    }
    
    /**
     * Create a bookmark tag for a file.
     *
     * @param file to create bookmark tag for
     * @param comment the bookmark comment
     */
    public static void createBookmark(AbstractFile file, String comment) {
        createTag(file, Tags.BOOKMARK_TAG_NAME, comment);
    }

    /**
     * Create a bookmark tag for an artifact.
     *
     * @param artifact to create bookmark tag for
     * @param comment the bookmark comment
     */
    public static void createBookmark(BlackboardArtifact artifact, String comment) {
        createTag(artifact, Tags.BOOKMARK_TAG_NAME, comment);
    }

    /**
     * Get a list of all the bookmarks.
     *
     * @return a list of all bookmark artifacts
     */
    static List<BlackboardArtifact> getBookmarks() {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();
            return skCase.getBlackboardArtifacts(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME, Tags.BOOKMARK_TAG_NAME);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get list of artifacts from the case", ex);
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of all the unique tag names associated with the current case plus any
     * tag names stored in the application settings file.
     *
     * @return A collection of tag names.
     */
    public static TreeSet<String> getAllTagNames() {
        // Use a TreeSet<> so the union of the tag names from the two sources will be sorted.
        TreeSet<String> tagNames = getTagNamesFromCurrentCase();
        tagNames.addAll(appSettingTagNames);
        
        // Make sure the book mark tag is always included.
        tagNames.add(BOOKMARK_TAG_NAME);
                        
        return tagNames;
    }
        
    /**
     * Get a list of all the unique tag names associated with the current case. 
     * Uses a custom query for speed when dealing with thousands of tags.
     *
     * @return A collection of tag names.
     */
    @SuppressWarnings("deprecation")
    public static TreeSet<String> getTagNamesFromCurrentCase() {
        TreeSet<String> tagNames = new TreeSet<>();
        
        ResultSet rs = null;
        SleuthkitCase skCase = null;
        try {
            skCase = Case.getCurrentCase().getSleuthkitCase();
            rs = skCase.runQuery("SELECT value_text"
                    + " FROM blackboard_attributes"
                    + " WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID()
                    + " GROUP BY value_text"
                    + " ORDER BY value_text");
            while (rs.next()) {
                tagNames.add(rs.getString("value_text"));
            }
        } 
        catch (IllegalStateException ex) {
            // Case.getCurrentCase() throws IllegalStateException if there is no current autopsy case.
        }
        catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to query the blackboard for tag names", ex);
        } 
        finally {
            if (null != skCase && null != rs) {
                try {
                    skCase.closeRunQuery(rs);
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to close the query for blackboard for tag names", ex);
                }
            }
        }
        
        // Make sure the book mark tag is always included.
        tagNames.add(BOOKMARK_TAG_NAME);
                
        return tagNames;
    }

    /**
     * Get the tag comment for a specified tag.
     *
     * @param tagArtifactId artifact id of the tag
     * @return the tag comment
     */
    static String getCommentFromTag(long tagArtifactId) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();

            BlackboardArtifact artifact = skCase.getBlackboardArtifact(tagArtifactId);
            if (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                    || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                List<BlackboardAttribute> attributes = artifact.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID()) {
                        return att.getValueString();
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get artifact " + tagArtifactId + " from case", ex);
        }

        return EMPTY_COMMENT;
    }

    /**
     * Get the artifact for a result tag.
     *
     * @param tagArtifactId artifact id of the tag
     * @return the tag's artifact
     */
    static BlackboardArtifact getArtifactFromTag(long tagArtifactId) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();

            BlackboardArtifact artifact = skCase.getBlackboardArtifact(tagArtifactId);
            if (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                    || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                List<BlackboardAttribute> attributes = artifact.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()) {
                        return skCase.getBlackboardArtifact(att.getValueLong());
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get artifact " + tagArtifactId + " from case.");
        }

        return null;
    }

    /**
     * Looks up the tag names associated with either a tagged artifact or a tag artifact.
     * 
     * @param artifact The artifact
     * @return A set of unique tag names
     */
    public static HashSet<String> getUniqueTagNamesForArtifact(BlackboardArtifact artifact) {
        return getUniqueTagNamesForArtifact(artifact.getArtifactID(), artifact.getArtifactTypeID());
    }    

    /**
     * Looks up the tag names associated with either a tagged artifact or a tag artifact.
     * 
     * @param artifactID The ID of the artifact
     * @param artifactTypeID The ID of the artifact type
     * @return A set of unique tag names
     */
    public static HashSet<String> getUniqueTagNamesForArtifact(long artifactID, int artifactTypeID) {
        HashSet<String> tagNames = new HashSet<>();
        
        try {
            ArrayList<Long> tagArtifactIDs = new ArrayList<>();
            if (artifactTypeID == ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID() ||
                artifactTypeID == ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                tagArtifactIDs.add(artifactID);
            } else {
                List<BlackboardArtifact> tags = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifacts(ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT, artifactID);
                for (BlackboardArtifact tag : tags) {
                    tagArtifactIDs.add(tag.getArtifactID());
                }
            }

            for (Long tagArtifactID : tagArtifactIDs) {
                String whereClause = "WHERE artifact_id = " + tagArtifactID + " AND attribute_type_id = " + ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID();
                List<BlackboardAttribute> attributes = Case.getCurrentCase().getSleuthkitCase().getMatchingAttributes(whereClause);
                for (BlackboardAttribute attr : attributes) {
                    tagNames.add(attr.getValueString());
                }
            }
        } 
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for artifact " + artifactID, ex);
        }
        
        return tagNames;
    }    
}
