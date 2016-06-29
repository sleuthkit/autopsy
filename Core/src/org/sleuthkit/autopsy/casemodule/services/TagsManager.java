/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final Logger logger = Logger.getLogger(TagsManager.class.getName());
    private static final String TAGS_SETTINGS_NAME = "Tags"; //NON-NLS
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; //NON-NLS
    private SleuthkitCase caseDb;
    private final HashMap<String, TagName> settingsTagNames = new HashMap<>();
    private final Map<String, TagName> dbTagNames = new HashMap<>();
    private boolean tagNamesLoaded = false;

    /**
     * Constructs a per case Autopsy service that manages the creation,
     * updating, and deletion of tags applied to content and blackboard
     * artifacts by users.
     *
     * @param caseDb The case database.
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
        List<TagName> tags = new ArrayList<>();
        tags.addAll(this.settingsTagNames.values());
        for (TagName tagName : dbTagNames.values()) {
            if (!tags.contains(tagName)) {
                tags.add(tagName);
            }
        }
        return tags;
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        lazyLoadExistingTagNames();
        return settingsTagNames.containsKey(tagDisplayName) || dbTagNames.containsKey(tagDisplayName);
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
        if (settingsTagNames.containsKey(displayName)) {
            throw new TagNameAlreadyExistsException();
        }
        TagName newTagName = null;
        if (dbTagNames.containsKey(displayName)) {
            TagName dbTag = dbTagNames.get(displayName);
            //Checks if the tag that was obtained is the same. If either are not the same, should throw an exception as we already have a tag of this display name.
            if (!(dbTag.getDescription().equals(description) && dbTag.getColor() == color)) {
                throw new TagNameAlreadyExistsException();
            } else {

                /**
                 * Else get the tag from the db and add it to the settings.
                 */
                newTagName = dbTag;
                /*
                 * Add the tag name to the tags settings.
                 */
                settingsTagNames.put(newTagName.getDisplayName(), newTagName);
                saveTagNamesToTagsSettings();

                return newTagName;
            }
        }
        /*
         * Add the tag name to the case.
         */
        newTagName = caseDb.addTagName(displayName, description, color);
        dbTagNames.put(newTagName.getDisplayName(), newTagName);
        /*
         * Add the tag name to the tags settings.
         */
        settingsTagNames.put(newTagName.getDisplayName(), newTagName);
        saveTagNamesToTagsSettings();

        return newTagName;
    }

    /**
     * Deletes the given tag name from the properties.
     *
     * @param tag The tag to delete from the properties
     *
     * @throws TskCoreException If the tag manager has been closed.
     */
    public synchronized void deleteTagName(TagName tag) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
        if (settingsTagNames.containsValue(tag)) {
            settingsTagNames.remove(tag.getDisplayName());
            saveTagNamesToTagsSettings();
        }
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
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
    public ContentTag addContentTag(Content content, TagName tagName, String comment, long beginByteOffset, long endByteOffset) throws IllegalArgumentException, TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        ContentTag tag;
        synchronized (this) {
            lazyLoadExistingTagNames();

            if (null == comment) {
                throw new IllegalArgumentException("Passed null comment argument");
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

            tag = caseDb.addContentTag(content, tagName, comment, beginByteOffset, endByteOffset);
        }

        try {
            Case.getCurrentCase().notifyContentTagAdded(tag);
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, NbBundle.getMessage(TagsManager.class, "TagsManager.addContentTag.noCaseWarning"), ex);
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        synchronized (this) {
            lazyLoadExistingTagNames();
            caseDb.deleteContentTag(tag);
        }

        try {
            Case.getCurrentCase().notifyContentTagDeleted(tag);
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, NbBundle.getMessage(TagsManager.class, "TagsManager.deleteContentTag.noCaseWarning"), ex);
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
    public synchronized List<ContentTag> getAllContentTags() throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
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
    public BlackboardArtifactTag addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName, String comment) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        BlackboardArtifactTag tag;
        synchronized (this) {
            lazyLoadExistingTagNames();
            if (null == comment) {
                throw new IllegalArgumentException("Passed null comment argument");
            }
            tag = caseDb.addBlackboardArtifactTag(artifact, tagName, comment);
        }

        try {
            Case.getCurrentCase().notifyBlackBoardArtifactTagAdded(tag);
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, NbBundle.getMessage(TagsManager.class, "TagsManager.addBlackboardArtifactTag.noCaseWarning"), ex);
        }
        return tag;
    }

    /**
     * Deletes a blackboard artifact tag.
     *
     * @param tag The tag to delete.
     *
     * @throws TskCoreException If there is an error deleting the tag from the
     *                          case database.
     */
    public void deleteBlackboardArtifactTag(BlackboardArtifactTag tag) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        synchronized (this) {
            lazyLoadExistingTagNames();
            caseDb.deleteBlackboardArtifactTag(tag);
        }

        try {
            Case.getCurrentCase().notifyBlackBoardArtifactTagDeleted(tag);
        } catch (IllegalStateException ex) {
            logger.log(Level.WARNING, NbBundle.getMessage(TagsManager.class, "TagsManager.deleteBlackboardArtifactTag.noCaseWarning"), ex);
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
    public synchronized List<BlackboardArtifactTag> getAllBlackboardArtifactTags() throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
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
        if (null == caseDb) {
            throw new TskCoreException("Tags manager has been closed");
        }
        lazyLoadExistingTagNames();
        return caseDb.getBlackboardArtifactTagsByArtifact(artifact);
    }

    /**
     * Closes the tags manager, saving the avaialble tag names to secondary
     * storage.
     *
     * @throws IOException If there is a problem closing the tags manager.
     * @deprecated Tags manager clients should not close the tags manager.
     */
    @Override
    @Deprecated
    public synchronized void close() throws IOException {
        saveTagNamesToTagsSettings();
        caseDb = null;
    }

    /**
     * Populates the tag names collection and the tag names table in the case
     * database with the existing tag names from all sources.
     */
    private void lazyLoadExistingTagNames() {
        if (!tagNamesLoaded) {
            addTagNamesFromCurrentCase();
            addTagNamesFromTagsSettings();
            addPredefinedTagNames();
            saveTagNamesToTagsSettings();
            tagNamesLoaded = true;
        }
    }

    /**
     * Adds any tag names that are in the case database to the tag names
     * collection.
     */
    private void addTagNamesFromCurrentCase() {
        try {
            List<TagName> currentTagNames = caseDb.getAllTagNames();
            for (TagName tagName : currentTagNames) {
                dbTagNames.put(tagName.getDisplayName(), tagName);
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag types from the current case", ex); //NON-NLS
        }
    }

    /**
     * Adds any tag names that are in the properties file to the tag names
     * collection and to the case database. The properties file is used to make
     * it possible to use tag names across cases.
     */
    private void addTagNamesFromTagsSettings() {
        String setting = ModuleSettings.getConfigSetting(TAGS_SETTINGS_NAME, TAG_NAMES_SETTING_KEY);
        if (null != setting && !setting.isEmpty()) {
            // Read the tag name setting and break it into tag name tuples.
            List<String> tagNameTuples = Arrays.asList(setting.split(";"));

            // Parse each tuple and add the tag names to the current case, one
            // at a time to gracefully discard any duplicates or corrupt tuples.
            for (String tagNameTuple : tagNameTuples) {
                String[] tagNameAttributes = tagNameTuple.split(",");
                if (!settingsTagNames.containsKey(tagNameAttributes[0])) {
                    try {
                        if (this.dbTagNames.containsKey(tagNameAttributes[0])) {
                            TagName tagName = dbTagNames.get(tagNameAttributes[0]);
                            settingsTagNames.put(tagName.getDisplayName(), tagName);
                        } else {
                            TagName tagName = caseDb.addTagName(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.getColorByName(tagNameAttributes[2]));
                            settingsTagNames.put(tagName.getDisplayName(), tagName);
                        }
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
    private void addPredefinedTagNames() {
        if (!dbTagNames.containsKey(NbBundle.getMessage(this.getClass(), "TagsManager.predefTagNames.bookmark.text"))) {
            try {
                TagName tagName = caseDb.addTagName(
                        NbBundle.getMessage(this.getClass(), "TagsManager.predefTagNames.bookmark.text"), "", TagName.HTML_COLOR.NONE);
                dbTagNames.put(tagName.getDisplayName(), tagName);
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
        if (!settingsTagNames.isEmpty()) {
            StringBuilder setting = new StringBuilder();
            for (TagName tagName : settingsTagNames.values()) {
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

        private static final long serialVersionUID = 1L;
    }

}
