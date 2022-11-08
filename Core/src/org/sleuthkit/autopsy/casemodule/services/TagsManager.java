/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.services;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.events.TagNamesEvent;
import org.sleuthkit.autopsy.casemodule.events.TagNamesEvent.TagNamesDeletedEvent;
import org.sleuthkit.autopsy.casemodule.services.contentviewertags.ContentViewerTagManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.CaseDbAccessManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TagSet;
import org.sleuthkit.datamodel.TaggingManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.DbType;

/**
 * A per case Autopsy service that manages the addition of content and artifact
 * tags to the case database.
 */
public class TagsManager implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(TagsManager.class.getName());
    private final SleuthkitCase caseDb;

    // NOTE: This name is also hard coded in Image Gallery and Projet Vic module. 
    // They need to stay in sync
    private static String PROJECT_VIC_TAG_SET_NAME = "Project VIC";

    private static final Object lock = new Object();
    
    private final Map<String, TagName> allTagNameMap = Collections.synchronizedMap(new HashMap<>());
    
    private final PropertyChangeListener listener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(Case.Events.TAG_NAMES_ADDED.name())
                    || evt.getPropertyName().equals(Case.Events.TAG_NAMES_UPDATED.name())) {
                TagNamesEvent tagEvent = (TagNamesEvent) evt;
                List<TagName> addTagNames = tagEvent.getTagNames();
                for (TagName tag : addTagNames) {
                    allTagNameMap.put(tag.getDisplayName(), tag);
                }
            } else if (evt.getPropertyName().equals(Case.Events.TAG_NAMES_DELETED.name())) {
                TagNamesDeletedEvent tagEvent = (TagNamesDeletedEvent) evt;
                List<Long> deletedIds = tagEvent.getTagNameIds();
                List<String> keysToRemove = new ArrayList<>();
                for (TagName tagName : getAllTagNames()) {
                    if (deletedIds.contains(tagName.getId())) {
                        keysToRemove.add(tagName.getDisplayName());
                    }
                }

                for (String key : keysToRemove) {
                    allTagNameMap.remove(key);
                }
            }
        }
    };
    
    private final PropertyChangeListener weakListener = WeakListeners.propertyChange(listener, null);

    static {

        //Create the contentviewer tags table if the current case does not 
        //have the table present
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), evt -> {
            if (evt.getNewValue() != null) {
                Case currentCase = (Case) evt.getNewValue();
                try {
                    CaseDbAccessManager caseDb = currentCase.getSleuthkitCase().getCaseDbAccessManager();
                    if (caseDb.tableExists(ContentViewerTagManager.TABLE_NAME)) {
                        return;
                    }

                    if (currentCase.getSleuthkitCase().getDatabaseType().equals(DbType.SQLITE)) {
                        caseDb.createTable(ContentViewerTagManager.TABLE_NAME, ContentViewerTagManager.TABLE_SCHEMA_SQLITE);
                    } else if (currentCase.getSleuthkitCase().getDatabaseType().equals(DbType.POSTGRESQL)) {
                        caseDb.createTable(ContentViewerTagManager.TABLE_NAME, ContentViewerTagManager.TABLE_SCHEMA_POSTGRESQL);
                    }
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE,
                            String.format("Unable to create the %s table for image tag storage.",
                                    ContentViewerTagManager.TABLE_NAME), ex);
                }
            }
        });
    }

    /**
     * Tests whether or not a given tag display name contains an illegal
     * character.
     *
     * @param tagDisplayName Display name of a tag.
     *
     * @return True or false.
     */
    public static boolean containsIllegalCharacters(String tagDisplayName) {
        return (tagDisplayName.contains("\\")
                || tagDisplayName.contains(":")
                || tagDisplayName.contains("*")
                || tagDisplayName.contains("?")
                || tagDisplayName.contains("\"")
                || tagDisplayName.contains("<")
                || tagDisplayName.contains(">")
                || tagDisplayName.contains("|")
                || tagDisplayName.contains(",")
                || tagDisplayName.contains(";"));

    }

    @NbBundle.Messages({"TagsManager.notableTagEnding.text= (Notable)"})
    /**
     * Get String of text which is used to label tags as notable to the user.
     *
     * @return Bundle message TagsManager.notableTagEnding.text
     */
    public static String getNotableTagLabel() {
        return Bundle.TagsManager_notableTagEnding_text();
    }

    /**
     * Gets the set of display names of the currently available tag types. This
     * includes the display names of the standard tag types, the current user's
     * custom tag types, and the tags in the case database of the current case
     * (if there is a current case).
     *
     * @return A set, possibly empty, of tag type display names.
     *
     * @throws TskCoreException If there is a current case and there is an error
     *                          querying the case database for tag types.
     */
    public static Set<String> getTagDisplayNames() throws TskCoreException {
        Set<String> tagDisplayNames = new HashSet<>();
        Set<TagNameDefinition> customNames = TagNameDefinition.getTagNameDefinitions();
        customNames.forEach((tagType) -> {
            tagDisplayNames.add(tagType.getDisplayName());
        });
        try {
            TagsManager tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
            for (TagName tagName : tagsManager.getAllTagNames()) {
                tagDisplayNames.add(tagName.getDisplayName());
            }
        } catch (NoCurrentCaseException ignored) {
            /*
             * No current case, nothing more to add to the set.
             */
        }
        return tagDisplayNames;
    }

    /**
     * Gets the set of display names of notable (TskData.FileKnown.BAD) tag
     * types. If a case is not open the list will only include only the user
     * defined custom tags. Otherwise the list will include all notable tags.
     *
     * @return
     */
    public static List<String> getNotableTagDisplayNames() {
        List<String> tagDisplayNames = new ArrayList<>();
        for (TagNameDefinition tagDef : TagNameDefinition.getTagNameDefinitions()) {
            if (tagDef.getKnownStatus() == TskData.FileKnown.BAD) {
                tagDisplayNames.add(tagDef.getDisplayName());
            }
        }

        try {
            TagsManager tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
            for (TagName tagName : tagsManager.getAllTagNames()) {
                if (tagName.getKnownStatus() == TskData.FileKnown.BAD
                        && !tagDisplayNames.contains(tagName.getDisplayName())) {
                    tagDisplayNames.add(tagName.getDisplayName());
                }
            }
        } catch (NoCurrentCaseException ignored) {
            /*
             * No current case, nothing more to add to the set.
             */
        }
        return tagDisplayNames;
    }

    /**
     * Returns a list of names of standard/predefined tags
     *
     * @return list of predefined tag names
     */
    public static List<String> getStandardTagNames() {
        List<String> tagList = new ArrayList<>();

        for (TagNameDefinition tagNameDef : TagNameDefinition.getStandardTagNameDefinitions()) {
            tagList.add(tagNameDef.getDisplayName());
        }

        try {
            List<TagSet> tagSetList = Case.getCurrentCaseThrows().getSleuthkitCase().getTaggingManager().getTagSets();
            for (TagSet tagSet : tagSetList) {
                if (tagSet.getName().equals(PROJECT_VIC_TAG_SET_NAME)) {
                    for (TagName tagName : tagSet.getTagNames()) {
                        tagList.add(tagName.getDisplayName());
                    }
                }
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get Project VIC tags from the database.", ex);
        }

        return tagList;
    }

    /**
     * Returns the bookmark tag display string.
     *
     * @return
     */
    public static String getBookmarkTagDisplayName() {
        return TagNameDefinition.getBookmarkTagDisplayName();
    }

    /**
     * Returns the Follow Up tag display string.
     *
     * @return
     */
    public static String getFollowUpTagDisplayName() {
        return TagNameDefinition.getFollowUpTagDisplayName();
    }

    /**
     * Returns the Notable tag display string.
     *
     * @return
     */
    public static String getNotableTagDisplayName() {
        return TagNameDefinition.getNotableTagDisplayName();
    }

    /**
     * Creates a new TagSetDefinition file that will be used for future cases
     *
     * @param tagSetDef The tag set definition.
     *
     * @throws IOException
     */
    public static void addTagSetDefinition(TagSetDefinition tagSetDef) throws IOException {
        synchronized (lock) {
            TagSetDefinition.writeTagSetDefinition(tagSetDef);
        }
    }

    /**
     * Constructs a per case Autopsy service that manages the addition of
     * content and artifact tags to the case database.
     *
     * @param caseDb The case database.
     */
    TagsManager(SleuthkitCase caseDb) {
        this.caseDb = caseDb;

        // Add standard tags and any configured tag sets.
        TaggingManager taggingMgr = caseDb.getTaggingManager();
        try {
            List<TagSet> tagSetsInCase = taggingMgr.getTagSets();
            if (tagSetsInCase.isEmpty()) {
                
                // add the standard tag names
                for (TagNameDefinition def : TagNameDefinition.getStandardTagNameDefinitions()) {
                    taggingMgr.addOrUpdateTagName(def.getDisplayName(), def.getDescription(), def.getColor(), def.getKnownStatus());
                }
                
                //Assume new case and add all tag sets
                for (TagSetDefinition setDef : TagSetDefinition.readTagSetDefinitions()) {
                    List<TagName> tagNamesInSet = new ArrayList<>();
                    for (TagNameDefinition tagNameDef : setDef.getTagNameDefinitions()) {
                        tagNamesInSet.add(taggingMgr.addOrUpdateTagName(tagNameDef.getDisplayName(), tagNameDef.getDescription(), tagNameDef.getColor(), tagNameDef.getKnownStatus()));
                    }

                    if (!tagNamesInSet.isEmpty()) {
                        taggingMgr.addTagSet(setDef.getName(), tagNamesInSet);
                    }
                }
            }         

            for(TagName tagName: caseDb.getAllTagNames()) {
                allTagNameMap.put(tagName.getDisplayName(), tagName);
            }
            
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error updating standard tag name and tag set definitions", ex);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error loading tag set JSON files", ex);
        }

        for (TagNameDefinition tagName : TagNameDefinition.getTagNameDefinitions()) {
            tagName.saveToCase(caseDb);
        }
        
        Case.addEventTypeSubscriber(Collections.singleton(Case.Events.TAG_NAMES_UPDATED), weakListener);
        Case.addEventTypeSubscriber(Collections.singleton(Case.Events.TAG_NAMES_ADDED), weakListener);
        Case.addEventTypeSubscriber(Collections.singleton(Case.Events.TAG_NAMES_DELETED), weakListener);
    }

    /**
     * Get a list of all tag sets currently in the case database.
     *
     * @return A list, possibly empty, of TagSet objects.
     *
     * @throws TskCoreException
     */
    public List<TagSet> getAllTagSets() throws TskCoreException {
        return caseDb.getTaggingManager().getTagSets();
    }

    /**
     * Gets the tag set a tag name (tag definition) belongs to, if any.
     *
     * @param tagName The tag name.
     *
     * @return A TagSet object or null.
     *
     * @throws TskCoreException If there is an error querying the case database.
     */
    public TagSet getTagSet(TagName tagName) throws TskCoreException {
        return caseDb.getTaggingManager().getTagSet(tagName);        
    }

    /**
     * Add a new TagSet to the case database. Tags will be ranked in the order
     * which they are passed to this method.
     *
     * @param name        Tag set name.
     * @param tagNameList List of TagName in rank order.
     *
     * @return A new TagSet object.
     *
     * @throws TskCoreException
     */
    public TagSet addTagSet(String name, List<TagName> tagNameList) throws TskCoreException {
        return caseDb.getTaggingManager().addTagSet(name, tagNameList);
    }

    /**
     * Gets a list of all tag names currently in the case database.
     *
     * @return A list, possibly empty, of TagName objects.
     */
    public synchronized List<TagName> getAllTagNames() {
        
        List<TagName> tagNames = new ArrayList<>();
        tagNames.addAll(allTagNameMap.values());
        return tagNames;
    }

    /**
     * Gets a list of all tag names currently in use in the case database for
     * tagging content or artifacts.
     *
     * @return A list, possibly empty, of TagName objects.
     *
     * @throws TskCoreException If there is an error querying the case database.
     */
    public List<TagName> getTagNamesInUse() throws TskCoreException {
        return caseDb.getTagNamesInUse();
    }

    /**
     * Gets a list of all tag names currently in use in the case database for
     * tagging content or artifacts by the specified user.
     *
     * @param userName - the user name that you want to get tags for
     *
     * @return A list, possibly empty, of TagName objects.
     *
     * @throws TskCoreException If there is an error querying the case database.
     */
    public List<TagName> getTagNamesInUseForUser(String userName) throws TskCoreException {
        Set<TagName> tagNameSet = new HashSet<>();
        List<BlackboardArtifactTag> artifactTags = caseDb.getAllBlackboardArtifactTags();
        for (BlackboardArtifactTag tag : artifactTags) {
            if (tag.getUserName().equals(userName)) {
                tagNameSet.add(tag.getName());
            }
        }
        List<ContentTag> contentTags = caseDb.getAllContentTags();
        for (ContentTag tag : contentTags) {
            if (tag.getUserName().equals(userName)) {
                tagNameSet.add(tag.getName());
            }
        }
        return new ArrayList<>(tagNameSet);
    }

    /**
     * Selects all of the rows from the tag_names table in the case database for
     * which there is at least one matching row in the content_tags or
     * blackboard_artifact_tags tables, for the given data source object id.
     *
     * @param dsObjId data source object id
     *
     * @return A list, possibly empty, of TagName data transfer objects (DTOs)
     *         for the rows.
     *
     * @throws TskCoreException
     */
    public List<TagName> getTagNamesInUse(long dsObjId) throws TskCoreException {
        return caseDb.getTagNamesInUse(dsObjId);
    }

    /**
     * Selects all of the rows from the tag_names table in the case database for
     * which there is at least one matching row in the content_tags or
     * blackboard_artifact_tags tables, for the given data source object id and
     * user.
     *
     * @param dsObjId  data source object id
     * @param userName - the user name that you want to get tags for
     *
     * @return A list, possibly empty, of TagName data transfer objects (DTOs)
     *         for the rows.
     *
     * @throws TskCoreException
     */
    public List<TagName> getTagNamesInUseForUser(long dsObjId, String userName) throws TskCoreException {
        Set<TagName> tagNameSet = new HashSet<>();
        List<BlackboardArtifactTag> artifactTags = caseDb.getAllBlackboardArtifactTags();
        for (BlackboardArtifactTag tag : artifactTags) {
            if (tag.getUserName().equals(userName) && tag.getArtifact().getDataSource().getId() == dsObjId) {
                tagNameSet.add(tag.getName());
            }
        }
        List<ContentTag> contentTags = caseDb.getAllContentTags();
        for (ContentTag tag : contentTags) {
            if (tag.getUserName().equals(userName) && tag.getContent().getDataSource().getId() == dsObjId) {
                tagNameSet.add(tag.getName());
            }
        }
        return new ArrayList<>(tagNameSet);
    }

    /**
     * Gets a map of tag display names to tag name entries in the case database.
     *
     * @return A map of tag display names to TagName object references.
     *
     * @throws TskCoreException if there is an error querying the case database.
     */
    public Map<String, TagName> getDisplayNamesToTagNamesMap() throws TskCoreException {
        Map<String, TagName> tagNames = new HashMap<>();
        for (TagName tagName : getAllTagNames()) {
            tagNames.put(tagName.getDisplayName(), tagName);
        }
        return tagNames;
    }

    /**
     * Adds a tag name entry to the case database and adds a corresponding tag
     * type to the current user's custom tag types.
     *
     * @param displayName The display name for the new tag type.
     *
     * @return A TagName representing the tag name database entry that can be
     *         used to add instances of the tag type to the case database.
     *
     * @throws TagNameAlreadyExistsException If the tag name already exists in
     *                                       the case database.
     * @throws TskCoreException              If there is an error adding the tag
     *                                       name to the case database.
     */
    public TagName addTagName(String displayName) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, "", TagName.HTML_COLOR.NONE, TskData.FileKnown.UNKNOWN);
    }

    /**
     * Adds a tag name entry to the case database and adds a corresponding tag
     * type to the current user's custom tag types.
     *
     * @param displayName The display name for the new tag type.
     * @param description The description for the new tag type.
     *
     * @return A TagName object that can be used to add instances of the tag
     *         type to the case database.
     *
     * @throws TagNameAlreadyExistsException If the tag name already exists in
     *                                       the case database.
     * @throws TskCoreException              If there is an error adding the tag
     *                                       name to the case database.
     */
    public TagName addTagName(String displayName, String description) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, description, TagName.HTML_COLOR.NONE, TskData.FileKnown.UNKNOWN);
    }

    /**
     * Adds a tag name entry to the case database and adds a corresponding tag
     * type to the current user's custom tag types.
     *
     * @param displayName The display name for the new tag type.
     * @param description The description for the new tag type.
     * @param color       The color to associate with the new tag type.
     *
     * @return A TagName object that can be used to add instances of the tag
     *         type to the case database.
     *
     * @throws TagNameAlreadyExistsException If the tag name already exists.
     * @throws TskCoreException              If there is an error adding the tag
     *                                       name to the case database.
     */
    public TagName addTagName(String displayName, String description, TagName.HTML_COLOR color) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, description, color, TskData.FileKnown.UNKNOWN);
    }

    /**
     * Adds a tag name entry to the case database and adds a corresponding tag
     * type to the current user's custom tag types.
     *
     * @param displayName The display name for the new tag type.
     * @param description The description for the new tag type.
     * @param color       The color to associate with the new tag type.
     * @param knownStatus The knownStatus to be used for the tag when
     *                    correlating on the tagged item
     *
     * @return A TagName object that can be used to add instances of the tag
     *         type to the case database.
     *
     * @throws TagNameAlreadyExistsException If the tag name already exists.
     * @throws TskCoreException              If there is an error adding the tag
     *                                       name to the case database.
     */
    public TagName addTagName(String displayName, String description, TagName.HTML_COLOR color, TskData.FileKnown knownStatus) throws TagNameAlreadyExistsException, TskCoreException {
        synchronized (lock) {
            try {
                TagName tagName = caseDb.getTaggingManager().addOrUpdateTagName(displayName, description, color, knownStatus);
                Set<TagNameDefinition> customTypes = TagNameDefinition.getTagNameDefinitions();
                customTypes.add(new TagNameDefinition(displayName, description, color, knownStatus));
                TagNameDefinition.setTagNameDefinitions(customTypes);
                return tagName;
            } catch (TskCoreException ex) {
                List<TagName> existingTagNames = getAllTagNames();
                for (TagName tagName : existingTagNames) {
                    if (tagName.getDisplayName().equals(displayName)) {
                        throw new TagNameAlreadyExistsException();
                    }
                }
                throw ex;
            }
        }
    }

    /**
     * Tags a content object.
     *
     * @param content The content to tag.
     * @param tagName The representation of the desired tag type in the case
     *                database, which can be obtained by calling getTagNames
     *                and/or addTagName.
     *
     * @return A ContentTag object representing the new tag.
     *
     * @throws TskCoreException If there is an error adding the tag to the case
     *                          database.
     */
    public ContentTag addContentTag(Content content, TagName tagName) throws TskCoreException {
        return addContentTag(content, tagName, "", -1, -1);
    }

    /**
     * Tags a content object.
     *
     * @param content The content to tag.
     * @param tagName The representation of the desired tag type in the case
     *                database, which can be obtained by calling getTagNames
     *                and/or addTagName.
     * @param comment A comment to store with the tag.
     *
     * @return A ContentTag object representing the new tag.
     *
     * @throws TskCoreException If there is an error adding the tag to the case
     *                          database.
     */
    public ContentTag addContentTag(Content content, TagName tagName, String comment) throws TskCoreException {
        return addContentTag(content, tagName, comment, -1, -1);
    }

    /**
     * Tags a content object or a section of a content object.
     *
     * @param content         The content to tag.
     * @param tagName         The representation of the desired tag type in the
     *                        case database, which can be obtained by calling
     *                        getTagNames and/or addTagName.
     * @param comment         A comment to store with the tag.
     * @param beginByteOffset Designates the beginning of a tagged section.
     * @param endByteOffset   Designates the end of a tagged section.
     *
     * @return A ContentTag object representing the new tag.
     *
     * @throws TskCoreException If there is an error adding the tag to the case
     *                          database.
     */
    public ContentTag addContentTag(Content content, TagName tagName, String comment, long beginByteOffset, long endByteOffset) throws TskCoreException {
        TaggingManager.ContentTagChange tagChange = caseDb.getTaggingManager().addContentTag(content, tagName, comment, beginByteOffset, endByteOffset);
        try {
            Case currentCase = Case.getCurrentCaseThrows();

            currentCase.notifyContentTagAdded(tagChange.getAddedTag(), tagChange.getRemovedTags().isEmpty() ? null : tagChange.getRemovedTags());

        } catch (NoCurrentCaseException ex) {
            throw new TskCoreException("Added a tag to a closed case", ex);
        }
        return tagChange.getAddedTag();
    }

    /**
     * Deletes a content tag.
     *
     * @param tag The tag to delete.
     *
     * @throws TskCoreException If there is an error deleting the tag from the
     *                          case database.
     */
    public void deleteContentTag(ContentTag tag) throws TskCoreException {
        caseDb.deleteContentTag(tag);
        try {
            Case.getCurrentCaseThrows().notifyContentTagDeleted(tag);
        } catch (NoCurrentCaseException ex) {
            throw new TskCoreException("Deleted a tag from a closed case", ex);
        }
    }

    /**
     * Gets all content tags for the current case.
     *
     * @return A list, possibly empty, of content tags.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public List<ContentTag> getAllContentTags() throws TskCoreException {
        return caseDb.getAllContentTags();
    }

    /**
     * Gets content tags count by tag name.
     *
     * @param tagName The representation of the desired tag type in the case
     *                database, which can be obtained by calling getTagNames
     *                and/or addTagName.
     *
     * @return A count of the content tags with the specified tag name.
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public long getContentTagsCountByTagName(TagName tagName) throws TskCoreException {
        return caseDb.getContentTagsCountByTagName(tagName);
    }

    /**
     * Gets content tags count by tag name for the specified user.
     *
     * @param tagName  The representation of the desired tag type in the case
     *                 database, which can be obtained by calling getTagNames
     *                 and/or addTagName.
     * @param userName - the user name that you want to get tags for
     *
     * @return A count of the content tags with the specified tag name for the
     *         specified user.
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public long getContentTagsCountByTagNameForUser(TagName tagName, String userName) throws TskCoreException {
        long count = 0;
        List<ContentTag> contentTags = getContentTagsByTagName(tagName);
        for (ContentTag tag : contentTags) {
            if (userName.equals(tag.getUserName())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets content tags count by tag name, for the given data source
     *
     * @param tagName The representation of the desired tag type in the case
     *                database, which can be obtained by calling getTagNames
     *                and/or addTagName.
     *
     * @param dsObjId data source object id
     *
     * @return A count of the content tags with the specified tag name, and for
     *         the given data source
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public long getContentTagsCountByTagName(TagName tagName, long dsObjId) throws TskCoreException {
        return caseDb.getContentTagsCountByTagName(tagName, dsObjId);
    }

    /**
     * Gets content tags count by tag name, for the given data source and user
     *
     * @param tagName  The representation of the desired tag type in the case
     *                 database, which can be obtained by calling getTagNames
     *                 and/or addTagName.
     *
     * @param dsObjId  data source object id
     * @param userName - the user name that you want to get tags for
     *
     * @return A count of the content tags with the specified tag name, and for
     *         the given data source and user
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public long getContentTagsCountByTagNameForUser(TagName tagName, long dsObjId, String userName) throws TskCoreException {
        long count = 0;
        List<ContentTag> contentTags = getContentTagsByTagName(tagName, dsObjId);
        for (ContentTag tag : contentTags) {
            if (userName.equals(tag.getUserName())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets a content tag by tag id.
     *
     * @param tagId The tag id of interest.
     *
     * @return The content tag with the specified tag id.
     *
     * @throws TskCoreException If there is an error getting the tag from the
     *                          case database.
     */
    public ContentTag getContentTagByTagID(long tagId) throws TskCoreException {
        return caseDb.getContentTagByID(tagId);
    }

    /**
     * Gets content tags by tag name.
     *
     * @param tagName The tag name of interest.
     *
     * @return A list, possibly empty, of the content tags with the specified
     *         tag name.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public List<ContentTag> getContentTagsByTagName(TagName tagName) throws TskCoreException {
        return caseDb.getContentTagsByTagName(tagName);
    }

    /**
     * Gets content tags by tag name, for the given data source.
     *
     * @param tagName The tag name of interest.
     *
     * @param dsObjId data source object id
     *
     * @return A list, possibly empty, of the content tags with the specified
     *         tag name, and for the given data source.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public List<ContentTag> getContentTagsByTagName(TagName tagName, long dsObjId) throws TskCoreException {
        return caseDb.getContentTagsByTagName(tagName, dsObjId);
    }

    /**
     * Gets content tags count by content.
     *
     * @param content The content of interest.
     *
     * @return A list, possibly empty, of the tags that have been applied to the
     *         content.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public List<ContentTag> getContentTagsByContent(Content content) throws TskCoreException {
        return caseDb.getContentTagsByContent(content);
    }

    /**
     * Tags an artifact.
     *
     * @param artifact The artifact to tag.
     * @param tagName  The representation of the desired tag type in the case
     *                 database, which can be obtained by calling getTagNames
     *                 and/or addTagName.
     *
     * @return A BlackboardArtifactTag object representing the new tag.
     *
     * @throws TskCoreException If there is an error adding the tag to the case
     *                          database.
     */
    public BlackboardArtifactTag addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName) throws TskCoreException {
        return addBlackboardArtifactTag(artifact, tagName, "");
    }

    /**
     * Tags an artifact.
     *
     * @param artifact The artifact to tag.
     * @param tagName  The representation of the desired tag type in the case
     *                 database, which can be obtained by calling getTagNames
     *                 and/or addTagName.
     * @param comment  A comment to store with the tag.
     *
     * @return A BlackboardArtifactTag object representing the new tag.
     *
     * @throws TskCoreException If there is an error adding the tag to the case
     *                          database.
     */
    public BlackboardArtifactTag addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName, String comment) throws TskCoreException {
        TaggingManager.BlackboardArtifactTagChange tagChange = caseDb.getTaggingManager().addArtifactTag(artifact, tagName, comment);
        try {
            Case currentCase = Case.getCurrentCaseThrows();
            currentCase.notifyBlackBoardArtifactTagAdded(tagChange.getAddedTag(), tagChange.getRemovedTags().isEmpty() ? null : tagChange.getRemovedTags());
        } catch (NoCurrentCaseException ex) {
            throw new TskCoreException("Added a tag to a closed case", ex);
        }
        return tagChange.getAddedTag();
    }

    /**
     * Deletes an artifact tag.
     *
     * @param tag The tag to delete.
     *
     * @throws TskCoreException If there is an error deleting the tag from the
     *                          case database.
     */
    public void deleteBlackboardArtifactTag(BlackboardArtifactTag tag) throws TskCoreException {
        caseDb.deleteBlackboardArtifactTag(tag);
        try {
            Case.getCurrentCaseThrows().notifyBlackBoardArtifactTagDeleted(tag);
        } catch (NoCurrentCaseException ex) {
            throw new TskCoreException("Deleted a tag from a closed case", ex);
        }
    }

    /**
     * Gets all artifact tags for the current case.
     *
     * @return A list, possibly empty, of artifact tags.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public List<BlackboardArtifactTag> getAllBlackboardArtifactTags() throws TskCoreException {
        return caseDb.getAllBlackboardArtifactTags();
    }

    /**
     * Gets an artifact tags count by tag name.
     *
     * @param tagName The representation of the desired tag type in the case
     *                database, which can be obtained by calling getTagNames
     *                and/or addTagName.
     *
     * @return A count of the artifact tags with the specified tag name.
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public long getBlackboardArtifactTagsCountByTagName(TagName tagName) throws TskCoreException {
        return caseDb.getBlackboardArtifactTagsCountByTagName(tagName);
    }

    /**
     * Gets an artifact tags count by tag name for a specific user.
     *
     * @param tagName  The representation of the desired tag type in the case
     *                 database, which can be obtained by calling getTagNames
     *                 and/or addTagName.
     * @param userName - the user name that you want to get tags for
     *
     * @return A count of the artifact tags with the specified tag name for the
     *         specified user.
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public long getBlackboardArtifactTagsCountByTagNameForUser(TagName tagName, String userName) throws TskCoreException {
        long count = 0;
        List<BlackboardArtifactTag> artifactTags = getBlackboardArtifactTagsByTagName(tagName);
        for (BlackboardArtifactTag tag : artifactTags) {
            if (userName.equals(tag.getUserName())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets an artifact tags count by tag name, for the given data source.
     *
     * @param tagName The representation of the desired tag type in the case
     *                database, which can be obtained by calling getTagNames
     *                and/or addTagName.
     * @param dsObjId data source object id
     *
     * @return A count of the artifact tags with the specified tag name, for the
     *         given data source.
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public long getBlackboardArtifactTagsCountByTagName(TagName tagName, long dsObjId) throws TskCoreException {
        return caseDb.getBlackboardArtifactTagsCountByTagName(tagName, dsObjId);
    }

    /**
     * Gets an artifact tags count by tag name, for the given data source and
     * user.
     *
     * @param tagName  The representation of the desired tag type in the case
     *                 database, which can be obtained by calling getTagNames
     *                 and/or addTagName.
     * @param dsObjId  data source object id
     * @param userName - the user name that you want to get tags for
     *
     * @return A count of the artifact tags with the specified tag name, for the
     *         given data source and user.
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public long getBlackboardArtifactTagsCountByTagNameForUser(TagName tagName, long dsObjId, String userName) throws TskCoreException {
        long count = 0;
        List<BlackboardArtifactTag> artifactTags = getBlackboardArtifactTagsByTagName(tagName, dsObjId);
        for (BlackboardArtifactTag tag : artifactTags) {
            if (userName.equals(tag.getUserName())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets an artifact tag by tag id.
     *
     * @param tagId The tag id of interest.
     *
     * @return The artifact tag with the specified tag id.
     *
     * @throws TskCoreException If there is an error getting the tag from the
     *                          case database.
     */
    public BlackboardArtifactTag getBlackboardArtifactTagByTagID(long tagId) throws TskCoreException {
        return caseDb.getBlackboardArtifactTagByID(tagId);
    }

    /**
     * Gets artifact tags by tag name.
     *
     * @param tagName The representation of the desired tag type in the case
     *                database, which can be obtained by calling getTagNames
     *                and/or addTagName.
     *
     * @return A list, possibly empty, of the artifact tags with the specified
     *         tag name.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public List<BlackboardArtifactTag> getBlackboardArtifactTagsByTagName(TagName tagName) throws TskCoreException {
        return caseDb.getBlackboardArtifactTagsByTagName(tagName);
    }

    /**
     * Gets artifact tags by tag name, for specified data source.
     *
     * @param tagName The representation of the desired tag type in the case
     *                database, which can be obtained by calling getTagNames
     *                and/or addTagName.
     * @param dsObjId data source object id
     *
     * @return A list, possibly empty, of the artifact tags with the specified
     *         tag name, for the specified data source.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public List<BlackboardArtifactTag> getBlackboardArtifactTagsByTagName(TagName tagName, long dsObjId) throws TskCoreException {
        return caseDb.getBlackboardArtifactTagsByTagName(tagName, dsObjId);
    }

    /**
     * Gets artifact tags for a particular artifact.
     *
     * @param artifact The artifact of interest.
     *
     * @return A list, possibly empty, of the tags that have been applied to the
     *         artifact.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public List<BlackboardArtifactTag> getBlackboardArtifactTagsByArtifact(BlackboardArtifact artifact) throws TskCoreException {
        return caseDb.getBlackboardArtifactTagsByArtifact(artifact);
    }

    /**
     * Exception thrown if there is an attempt to add a duplicate tag name.
     */
    public static class TagNameAlreadyExistsException extends Exception {

        private static final long serialVersionUID = 1L;
    }

    /**
     * Checks whether a tag name with a given display name exists in the case
     * database.
     *
     * @param tagDisplayName The display name.
     *
     * @return True or false.
     *
     * @deprecated Not reliable for multi-user cases.
     */
    @Deprecated
    public boolean tagNameExists(String tagDisplayName) {
        try {
            Map<String, TagName> tagNames = getDisplayNamesToTagNamesMap();
            return tagNames.containsKey(tagDisplayName) && (tagNames.get(tagDisplayName) != null);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error querying case database for tag names", ex);
            return false;
        }
    }

    /**
     * Closes the tags manager.
     *
     * @throws IOException If there is a problem closing the tags manager.
     * @deprecated Tags manager clients should not close the tags manager.
     */
    @Deprecated
    @Override
    public void close() throws IOException {
    }
}
