/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A per case Autopsy service that manages the addition of content and artifact
 * tags to the case database.
 */
public class TagsManager implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(TagsManager.class.getName());

    private final SleuthkitCase caseDb;

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
    public static String getNotableTagLabel(){
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
            TagsManager tagsManager = Case.getOpenCase().getServices().getTagsManager();
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

    public static List<String> getNotableTagDisplayNames() {
        List<String> tagDisplayNames = new ArrayList<>();
        for (TagNameDefinition tagDef : TagNameDefinition.getTagNameDefinitions()) {
            if (tagDef.getKnownStatus() == TskData.FileKnown.BAD) {
                tagDisplayNames.add(tagDef.getDisplayName());
            }
        }
        return tagDisplayNames;
    }

    /**
     * Constructs a per case Autopsy service that manages the addition of
     * content and artifact tags to the case database.
     *
     * @param caseDb The case database.
     */
    TagsManager(SleuthkitCase caseDb) {
        this.caseDb = caseDb;
        for (TagNameDefinition tagName : TagNameDefinition.getTagNameDefinitions()) {
            tagName.saveToCase(caseDb);
        }
    }

    /**
     * Gets a list of all tag names currently in the case database.
     *
     * @return A list, possibly empty, of TagName objects.
     *
     * @throws TskCoreException If there is an error querying the case database.
     */
    public List<TagName> getAllTagNames() throws TskCoreException {
        return caseDb.getAllTagNames();
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
     * Gets a map of tag display names to tag name entries in the case database.
     * It has keys for the display names of the standard tag types, the current
     * user's custom tag types, and the tags in the case database. The value for
     * a given key will be null if the corresponding tag type is defined, but a
     * tag name entry has not yet added to the case database. In that case,
     * addTagName may be called to add the tag name entry.
     *
     * @return A map of tag display names to possibly null TagName object
     *         references.
     *
     * @throws TskCoreException if there is an error querying the case database.
     */
    public Map<String, TagName> getDisplayNamesToTagNamesMap() throws TskCoreException {
        /**
         * Order is important here. The keys (display names) for the current
         * user's custom tag types are added to the map first, with null TagName
         * values. If tag name entries exist for those keys, loading of the tag
         * names from the database supplies the missing values. Standard tag
         * names are added during the initialization of the case database.
         *
         * Note that creating the map on demand increases the probability that
         * the display names of newly added custom tag types and the display
         * names of tags added to a multi-user case by other users appear in the
         * map.
         */
        Map<String, TagName> tagNames = new HashMap<>();
        Set<TagNameDefinition> customTypes = TagNameDefinition.getTagNameDefinitions();
        for (TagNameDefinition tagType : customTypes) {
            tagNames.put(tagType.getDisplayName(), null);
        }
        for (TagName tagName : caseDb.getAllTagNames()) {
            tagNames.put(tagName.getDisplayName(), tagName);
        }
        return new HashMap<>(tagNames);
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
    public synchronized TagName addTagName(String displayName) throws TagNameAlreadyExistsException, TskCoreException {
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
    public synchronized TagName addTagName(String displayName, String description) throws TagNameAlreadyExistsException, TskCoreException {
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
    public synchronized TagName addTagName(String displayName, String description, TagName.HTML_COLOR color) throws TagNameAlreadyExistsException, TskCoreException {
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
    public synchronized TagName addTagName(String displayName, String description, TagName.HTML_COLOR color, TskData.FileKnown knownStatus) throws TagNameAlreadyExistsException, TskCoreException {
        try {
            TagName tagName = caseDb.addOrUpdateTagName(displayName, description, color, knownStatus);
            Set<TagNameDefinition> customTypes = TagNameDefinition.getTagNameDefinitions();
            customTypes.add(new TagNameDefinition(displayName, description, color, knownStatus));
            TagNameDefinition.setTagNameDefinitions(customTypes);
            return tagName;
        } catch (TskCoreException ex) {
            List<TagName> existingTagNames = caseDb.getAllTagNames();
            for (TagName tagName : existingTagNames) {
                if (tagName.getDisplayName().equals(displayName)) {
                    throw new TagNameAlreadyExistsException();
                }
            }
            throw ex;
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
        ContentTag tag;
        tag = caseDb.addContentTag(content, tagName, comment, beginByteOffset, endByteOffset);
        try {
            Case.getOpenCase().notifyContentTagAdded(tag);
        } catch (NoCurrentCaseException ex) {
            throw new TskCoreException("Added a tag to a closed case", ex);
        }
        return tag;
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
            Case.getOpenCase().notifyContentTagDeleted(tag);
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
        BlackboardArtifactTag tag = caseDb.addBlackboardArtifactTag(artifact, tagName, comment);
        try {
            Case.getOpenCase().notifyBlackBoardArtifactTagAdded(tag);
        } catch (NoCurrentCaseException ex) {
            throw new TskCoreException("Added a tag to a closed case", ex);
        }
        return tag;
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
            Case.getOpenCase().notifyBlackBoardArtifactTagDeleted(tag);
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
