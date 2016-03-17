/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-15 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A per case Autopsy service that manages the creation, updating, and deletion
 * of tags applied to content and blackboard artifacts by users.
 */
public class TagsManager implements Closeable {

    private static final String TAGS_SETTINGS_NAME = "Tags"; //NON-NLS
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; //NON-NLS
    private final SleuthkitCase caseDb;
    private final HashMap<String, TagName> uniqueTagNames = new HashMap<>();
    private boolean tagNamesInitialized = false;

    /**
     * Constructs a per case Autopsy service that manages the creation,
     * updating, and deletion of tags applied to content and blackboard
     * artifacts by users.
     *
     * @param caseDb The case database for the current case.
     */
    TagsManager(SleuthkitCase caseDb) {
        this.caseDb = caseDb;
    }

    /**
     * Gets a list of all tag names currently available for tagging content or
     * artifacts.
     *
     * @return A list, possibly empty, of TagName data transfer objects (DTOs).
     *
     * @throws TskCoreException If there is an error reading from the case
     *                          database.
     */
    public synchronized List<TagName> getAllTagNames() throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return caseDb.getAllTagNames();
    }

    /**
     * Gets a list of all tag names currently in use for tagging content or
     * artifacts.
     *
     * @return A list, possibly empty, of TagName data transfer objects (DTOs).
     *
     * @throws TskCoreException If there is an error reading from the case
     *                          database.
     */
    public synchronized List<TagName> getTagNamesInUse() throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return caseDb.getTagNamesInUse();
    }

    /**
     * Checks whether a tag name with a given display name exists.
     *
     * @param tagDisplayName The display name to check.
     *
     * @return True or false.
     */
    public synchronized boolean tagNameExists(String tagDisplayName) {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return uniqueTagNames.containsKey(tagDisplayName);
    }

    /**
     * Adds a new tag name to the current case and to the tags settings.
     *
     * @param displayName The display name for the new tag name.
     *
     * @return A TagName data transfer object (DTO) representing the new tag
     *         name.
     *
     * @throws TagNameAlreadyExistsException If the tag name would be a
     *                                       duplicate.
     * @throws TskCoreException              If there is an error adding the tag
     *                                       to the case database.
     */
    public TagName addTagName(String displayName) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, "", TagName.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag name to the current case and to the tags settings.
     *
     * @param displayName The display name for the new tag name.
     * @param description The description for the new tag name.
     *
     * @return A TagName data transfer object (DTO) representing the new tag
     *         name.
     *
     * @throws TagNameAlreadyExistsException If the tag name would be a
     *                                       duplicate.
     * @throws TskCoreException              If there is an error adding the tag
     *                                       to the case database.
     */
    public TagName addTagName(String displayName, String description) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, description, TagName.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag name to the current case and to the tags settings.
     *
     * @param displayName The display name for the new tag name.
     * @param description The description for the new tag name.
     * @param color       The HTML color to associate with the new tag name.
     *
     * @return A TagName data transfer object (DTO) representing the new tag
     *         name.
     *
     * @throws TagNameAlreadyExistsException If the tag name would be a
     *                                       duplicate.
     * @throws TskCoreException              If there is an error adding the tag
     *                                       to the case database.
     */
    public synchronized TagName addTagName(String displayName, String description, TagName.HTML_COLOR color) throws TagNameAlreadyExistsException, TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        if (uniqueTagNames.containsKey(displayName)) {
            throw new TagNameAlreadyExistsException();
        }

        /*
         * Add the tag name to the case.
         */
        TagName newTagName = caseDb.addTagName(displayName, description, color);

        /*
         * Add the tag name to the tags settings.
         */
        uniqueTagNames.put(newTagName.getDisplayName(), newTagName);
        saveTagNamesToTagsSettings();

        return newTagName;
    }

    /**
     * Tags a content object.
     *
     * @param content The content to tag.
     * @param tagName The name to use for the tag.
     *
     * @return A ContentTag data transfer object (DTO) representing the new tag.
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
     * @param tagName The name to use for the tag.
     * @param comment A comment to store with the tag.
     *
     * @return A ContentTag data transfer object (DTO) representing the new tag.
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
     * @param tagName         The name to use for the tag.
     * @param comment         A comment to store with the tag.
     * @param beginByteOffset Designates the beginning of a tagged section.
     * @param endByteOffset   Designates the end of a tagged section.
     *
     * @return A ContentTag data transfer object (DTO) representing the new tag.
     *
     * @throws IllegalArgumentException If a requested byte offset is out of
     *                                  range.
     * @throws TskCoreException         If there is an error adding the tag to
     *                                  the case database.
     */
    public synchronized ContentTag addContentTag(Content content, TagName tagName, String comment, long beginByteOffset, long endByteOffset) throws IllegalArgumentException, TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        if (beginByteOffset >= 0 && endByteOffset >= 1) {
            if (beginByteOffset > content.getSize() - 1) {
                throw new IllegalArgumentException(NbBundle.getMessage(this.getClass(),
                        "TagsManager.addContentTag.exception.beginByteOffsetOOR.msg",
                        beginByteOffset, content.getSize() - 1));
            }

            if (endByteOffset > content.getSize() - 1) {
                throw new IllegalArgumentException(
                        NbBundle.getMessage(this.getClass(), "TagsManager.addContentTag.exception.endByteOffsetOOR.msg",
                                endByteOffset, content.getSize() - 1));
            }

            if (endByteOffset < beginByteOffset) {
                throw new IllegalArgumentException(
                        NbBundle.getMessage(this.getClass(), "TagsManager.addContentTag.exception.endLTbegin.msg"));
            }
        }

        ContentTag newContentTag = caseDb.addContentTag(content, tagName, comment, beginByteOffset, endByteOffset);
        try {
            Case.getCurrentCase().notifyContentTagAdded(newContentTag);
        } catch (IllegalStateException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.WARNING, NbBundle.getMessage(TagsManager.class, "TagsManager.addContentTag.noCaseWarning"));
        }
        return newContentTag;
    }

    /**
     * Deletes a content tag.
     *
     * @param tag The tag to delete.
     *
     * @throws TskCoreException If there is an error deleting the tag from the
     *                          case database.
     */
    public synchronized void deleteContentTag(ContentTag tag) throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        caseDb.deleteContentTag(tag);
        try {
            Case.getCurrentCase().notifyContentTagDeleted(tag);
        } catch (IllegalStateException e) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.WARNING, NbBundle.getMessage(TagsManager.class, "TagsManager.deleteContentTag.noCaseWarning"));
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
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return caseDb.getAllContentTags();
    }

    /**
     * Gets content tags count by tag name.
     *
     * @param tagName The tag name of interest.
     *
     * @return A count of the content tags with the specified tag name.
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public synchronized long getContentTagsCountByTagName(TagName tagName) throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return caseDb.getContentTagsCountByTagName(tagName);
    }

    /**
     * Gets a content tag by tag id.
     *
     * @param tagID The tag id of interest.
     *
     * @return The content tag with the specified tag id.
     *
     * @throws TskCoreException If there is an error getting the tag from the
     *                          case database.
     */
    public synchronized ContentTag getContentTagByTagID(long tagID) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        return caseDb.getContentTagByID(tagID);
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
    public synchronized List<ContentTag> getContentTagsByTagName(TagName tagName) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        return caseDb.getContentTagsByTagName(tagName);
    }

    /**
     * Gets content tags count by content.
     *
     * @param content The content of interest.
     *
     * @return A list, possibly empty, of the tags that have been applied to the
     *         artifact.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public synchronized List<ContentTag> getContentTagsByContent(Content content) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        return caseDb.getContentTagsByContent(content);
    }

    /**
     * Tags a blackboard artifact object.
     *
     * @param artifact The blackboard artifact to tag.
     * @param tagName  The name to use for the tag.
     *
     * @return A BlackboardArtifactTag data transfer object (DTO) representing
     *         the new tag.
     *
     * @throws TskCoreException If there is an error adding the tag to the case
     *                          database.
     */
    public BlackboardArtifactTag addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName) throws TskCoreException {
        return addBlackboardArtifactTag(artifact, tagName, "");
    }

    /**
     * Tags a blackboard artifact object.
     *
     * @param artifact The blackboard artifact to tag.
     * @param tagName  The name to use for the tag.
     * @param comment  A comment to store with the tag.
     *
     * @return A BlackboardArtifactTag data transfer object (DTO) representing
     *         the new tag.
     *
     * @throws TskCoreException If there is an error adding the tag to the case
     *                          database.
     */
    public synchronized BlackboardArtifactTag addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName, String comment) throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        BlackboardArtifactTag addBlackboardArtifactTag = caseDb.addBlackboardArtifactTag(artifact, tagName, comment);
        try {
            Case.getCurrentCase().notifyBlackBoardArtifactTagAdded(addBlackboardArtifactTag);
        } catch (IllegalStateException e) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.WARNING, NbBundle.getMessage(TagsManager.class, "TagsManager.addBlackboardArtifactTag.noCaseWarning"));
        }
        return addBlackboardArtifactTag;
    }

    /**
     * Deletes a blackboard artifact tag.
     *
     * @param tag The tag to delete.
     *
     * @throws TskCoreException If there is an error deleting the tag from the
     *                          case database.
     */
    public synchronized void deleteBlackboardArtifactTag(BlackboardArtifactTag tag) throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        caseDb.deleteBlackboardArtifactTag(tag);
        try {
            Case.getCurrentCase().notifyBlackBoardArtifactTagDeleted(tag);
        } catch (IllegalStateException e) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.WARNING, NbBundle.getMessage(TagsManager.class, "TagsManager.deleteBlackboardArtifactTag.noCaseWarning"));
        }
    }

    /**
     * Gets all blackboard artifact tags for the current case.
     *
     * @return A list, possibly empty, of blackboard artifact tags.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public List<BlackboardArtifactTag> getAllBlackboardArtifactTags() throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return caseDb.getAllBlackboardArtifactTags();
    }

    /**
     * Gets blackboard artifact tags count by tag name.
     *
     * @param tagName The tag name of interest.
     *
     * @return A count of the blackboard artifact tags with the specified tag
     *         name.
     *
     * @throws TskCoreException If there is an error getting the tags count from
     *                          the case database.
     */
    public synchronized long getBlackboardArtifactTagsCountByTagName(TagName tagName) throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return caseDb.getBlackboardArtifactTagsCountByTagName(tagName);
    }

    /**
     * Gets a blackboard artifact tag by tag id.
     *
     * @param tagID The tag id of interest.
     *
     * @return the blackboard artifact tag with the specified tag id.
     *
     * @throws TskCoreException If there is an error getting the tag from the
     *                          case database.
     */
    public synchronized BlackboardArtifactTag getBlackboardArtifactTagByTagID(long tagID) throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return caseDb.getBlackboardArtifactTagByID(tagID);
    }

    /**
     * Gets blackboard artifact tags by tag name.
     *
     * @param tagName The tag name of interest.
     *
     * @return A list, possibly empty, of the blackboard artifact tags with the
     *         specified tag name.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public synchronized List<BlackboardArtifactTag> getBlackboardArtifactTagsByTagName(TagName tagName) throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return caseDb.getBlackboardArtifactTagsByTagName(tagName);
    }

    /**
     * Gets blackboard artifact tags for a particular blackboard artifact.
     *
     * @param artifact The blackboard artifact of interest.
     *
     * @return A list, possibly empty, of the tags that have been applied to the
     *         artifact.
     *
     * @throws TskCoreException If there is an error getting the tags from the
     *                          case database.
     */
    public synchronized List<BlackboardArtifactTag> getBlackboardArtifactTagsByArtifact(BlackboardArtifact artifact) throws TskCoreException {
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        return caseDb.getBlackboardArtifactTagsByArtifact(artifact);
    }

    /**
     * Saves the avaialble tag names to secondary storage.
     */
    @Override
    public void close() throws IOException {
        saveTagNamesToTagsSettings();
    }

    /**
     * Populates the tag names collection with the existing tag names from all
     * sources.
     */
    private void getExistingTagNames() {
        getTagNamesFromCurrentCase();
        getTagNamesFromTagsSettings();
        getPredefinedTagNames();
        saveTagNamesToTagsSettings();
        tagNamesInitialized = true;
    }

    /**
     * Adds any tag names that are in the case database to the tag names
     * collection.
     */
    private void getTagNamesFromCurrentCase() {
        try {
            List<TagName> currentTagNames = caseDb.getAllTagNames();
            for (TagName tagName : currentTagNames) {
                uniqueTagNames.put(tagName.getDisplayName(), tagName);
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag types from the current case", ex); //NON-NLS
        }
    }

    /**
     * Adds any tag names that are in the properties file to the tag names
     * collection. The properties file is used to make it possible to use tag
     * names across cases.
     */
    private void getTagNamesFromTagsSettings() {
        String setting = ModuleSettings.getConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY);
        if (null != setting && !setting.isEmpty()) {
            // Read the tag name setting and break it into tag name tuples.
            List<String> tagNameTuples = Arrays.asList(setting.split(";"));

            // Parse each tuple and add the tag names to the current case, one
            // at a time to gracefully discard any duplicates or corrupt tuples.
            for (String tagNameTuple : tagNameTuples) {
                String[] tagNameAttributes = tagNameTuple.split(",");
                if (!uniqueTagNames.containsKey(tagNameAttributes[0])) {
                    try {
                        TagName tagName = caseDb.addTagName(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.getColorByName(tagNameAttributes[2]));
                        uniqueTagNames.put(tagName.getDisplayName(), tagName);
                    } catch (TskCoreException ex) {
                        Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to add saved tag name " + tagNameAttributes[0], ex); //NON-NLS
                    }
                }
            }
        }
    }

    /**
     * Adds the standard tag names to the tag names collection.
     */
    private void getPredefinedTagNames() {
        if (!uniqueTagNames.containsKey(NbBundle.getMessage(this.getClass(), "TagsManager.predefTagNames.bookmark.text"))) {
            try {
                TagName tagName = caseDb.addTagName(
                        NbBundle.getMessage(this.getClass(), "TagsManager.predefTagNames.bookmark.text"), "", TagName.HTML_COLOR.NONE);
                uniqueTagNames.put(tagName.getDisplayName(), tagName);
            } catch (TskCoreException ex) {
                Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to add standard 'Bookmark' tag name to case database", ex); //NON-NLS
            }
        }
    }

    /**
     * Saves the tag names to a properties file. The properties file is used to
     * make it possible to use tag names across cases.
     */
    private void saveTagNamesToTagsSettings() {
        if (!uniqueTagNames.isEmpty()) {
            StringBuilder setting = new StringBuilder();
            for (TagName tagName : uniqueTagNames.values()) {
                if (setting.length() != 0) {
                    setting.append(";");
                }
                setting.append(tagName.getDisplayName()).append(",");
                setting.append(tagName.getDescription()).append(",");
                setting.append(tagName.getColor().name());
            }
            ModuleSettings.setConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY, setting.toString());
        }
    }

    /**
     * Exception thrown if there is an attempt to add a duplicate tag name.
     */
    public static class TagNameAlreadyExistsException extends Exception {
    }

}
