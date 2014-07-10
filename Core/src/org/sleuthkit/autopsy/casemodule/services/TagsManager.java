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
package org.sleuthkit.autopsy.casemodule.services;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A per case instance of this class functions as an Autopsy service that 
 * manages the creation, updating, and deletion of tags applied to content and 
 * blackboard artifacts by users. 
 */
public class TagsManager implements Closeable {
    private static final String TAGS_SETTINGS_NAME = "Tags"; //NON-NLS
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; //NON-NLS
    private final SleuthkitCase tskCase;    
    private final HashMap<String, TagName> uniqueTagNames = new HashMap<>();
    private boolean tagNamesInitialized = false; // @@@ This is part of a work around to be removed when database access on the EDT is correctly synchronized.  
    
    // Use this exception and the member hash map to manage uniqueness of hash 
    // names. This is deemed more proactive and informative than leaving this to 
    // the UNIQUE constraint on the display_name field of the tag_names table in 
    // the case database.
    public class TagNameAlreadyExistsException extends Exception {
    }
        
    /**
     * Package-scope constructor for use of the Services class. An instance of 
     * TagsManager should be created for each case that is opened.
     * @param [in] tskCase The SleuthkitCase object for the current case. 
     */
    TagsManager(SleuthkitCase tskCase) {
        this.tskCase = tskCase;
        // @@@ The removal of this call is a work around until database access on the EDT is correctly synchronized.
        // getExistingTagNames();
    }

    /**
     * Gets a list of all tag names currently available for tagging content or 
     * blackboard artifacts.
     * @return A list, possibly empty, of TagName data transfer objects (DTOs). 
     * @throws TskCoreException 
     */
    public synchronized List<TagName> getAllTagNames() throws TskCoreException {    
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return tskCase.getAllTagNames();
    }

    /**
     * Gets a list of all tag names currently used for tagging content or 
     * blackboard artifacts.
     * @return A list, possibly empty, of TagName data transfer objects (DTOs). 
     * @throws TskCoreException 
     */
    public synchronized List<TagName> getTagNamesInUse() throws TskCoreException {    
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return tskCase.getTagNamesInUse();
    }
            
    /**
     * Checks whether a tag name with a given display name exists.
     * @param [in] tagDisplayName The display name for which to check.
     * @return True if the tag name exists, false otherwise.
     */
    public synchronized boolean tagNameExists(String tagDisplayName) {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return uniqueTagNames.containsKey(tagDisplayName);
    }
                    
    /**
     * Adds a new tag name to the current case and to the tags settings.
     * @param [in] displayName The display name for the new tag name.
     * @return A TagName data transfer object (DTO) representing the new tag name.  
     * @throws TagNameAlreadyExistsException, TskCoreException 
     */
    public TagName addTagName(String displayName) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, "", TagName.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag name to the current case and to the tags settings.
     * @param [in] displayName The display name for the new tag name.
     * @param [in] description The description for the new tag name.
     * @return A TagName data transfer object (DTO) representing the new tag name.  
     * @throws TagNameAlreadyExistsException, TskCoreException 
     */
    public TagName addTagName(String displayName, String description) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, description, TagName.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag name to the current case and to the tags settings.
     * @param [in] displayName The display name for the new tag name.
     * @param [in] description The description for the new tag name.
     * @param [in] color The HTML color to associate with the new tag name.
     * @return A TagName data transfer object (DTO) representing the new tag name.  
     * @throws TagNameAlreadyExistsException, TskCoreException 
     */
    public synchronized TagName addTagName(String displayName, String description, TagName.HTML_COLOR color) throws TagNameAlreadyExistsException, TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        if (uniqueTagNames.containsKey(displayName)) {
            throw new TagNameAlreadyExistsException();
        }

        // Add the tag name to the case.
        TagName newTagName = tskCase.addTagName(displayName, description, color); 

        // Add the tag name to the tags settings.
        uniqueTagNames.put(newTagName.getDisplayName(), newTagName);
        saveTagNamesToTagsSettings();        

        return newTagName;
    }
        
    /**
     * Tags a content object.
     * @param [in] content The content to tag.
     * @param [in] tagName The name to use for the tag.
     * @return A ContentTag data transfer object (DTO) representing the new tag.  
     * @throws TskCoreException 
     */
    public ContentTag addContentTag(Content content, TagName tagName) throws TskCoreException {
        return addContentTag(content, tagName, "", -1, -1);
    }    
    
    /**
     * Tags a content object.
     * @param [in] content The content to tag.
     * @param [in] tagName The name to use for the tag.
     * @param [in] comment A comment to store with the tag.
     * @return A ContentTag data transfer object (DTO) representing the new tag.  
     * @throws TskCoreException 
     */
    public ContentTag addContentTag(Content content, TagName tagName, String comment) throws TskCoreException {
        return addContentTag(content, tagName, comment, -1, -1);
    }    
    
    /**
     * Tags a content object or a section of a content object.
     * @param [in] content The content to tag.
     * @param [in] tagName The name to use for the tag.
     * @param [in] comment A comment to store with the tag.
     * @param [in] beginByteOffset Designates the beginning of a tagged section. 
     * @param [in] endByteOffset Designates the end of a tagged section.
     * @return A ContentTag data transfer object (DTO) representing the new tag.  
     * @throws IllegalArgumentException, TskCoreException 
     */
    public synchronized ContentTag addContentTag(Content content, TagName tagName, String comment, long beginByteOffset, long endByteOffset) throws IllegalArgumentException, TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
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
            
        return tskCase.addContentTag(content, tagName, comment, beginByteOffset, endByteOffset);
    }
    
    /**
     * Deletes a content tag.
     * @param [in] tag The tag to delete.
     * @throws TskCoreException 
     */
    public synchronized void deleteContentTag(ContentTag tag) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        tskCase.deleteContentTag(tag);
    }

    /**
     * Gets all content tags for the current case.
     * @return A list, possibly empty, of content tags.
     * @throws TskCoreException 
     */
    public List<ContentTag> getAllContentTags() throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        return tskCase.getAllContentTags();        
    }
    
    /**
     * Gets content tags count by tag name.
     * @param [in] tagName The tag name of interest. 
     * @return A count of the content tags with the specified tag name.
     * @throws TskCoreException 
     */
    public synchronized long getContentTagsCountByTagName(TagName tagName) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return tskCase.getContentTagsCountByTagName(tagName);        
    }
            
    /**
     * Gets content tags by tag name.
     * @param [in] tagName The tag name of interest. 
     * @return A list, possibly empty, of the content tags with the specified tag name.
     * @throws TskCoreException 
     */
    public synchronized List<ContentTag> getContentTagsByTagName(TagName tagName) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return tskCase.getContentTagsByTagName(tagName);        
    }
        
    /**
     * Gets content tags count by content.
     * @param [in] content The content of interest.
     * @return A list, possibly empty, of the tags that have been applied to the artifact.
     * @throws TskCoreException 
     */
    public synchronized List<ContentTag> getContentTagsByContent(Content content) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return tskCase.getContentTagsByContent(content);        
    }
        
    /**
     * Tags a blackboard artifact object.
     * @param [in] artifact The blackboard artifact to tag.
     * @param [in] tagName The name to use for the tag.
     * @return A BlackboardArtifactTag data transfer object (DTO) representing the new tag.  
     * @throws TskCoreException 
     */
    public BlackboardArtifactTag addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName) throws TskCoreException {
        return addBlackboardArtifactTag(artifact, tagName, "");   
    }
        
    /**
     * Tags a blackboard artifact object.
     * @param [in] artifact The blackboard artifact to tag.
     * @param [in] tagName The name to use for the tag.
     * @param [in] comment A comment to store with the tag.
     * @return A BlackboardArtifactTag data transfer object (DTO) representing the new tag.  
     * @throws TskCoreException 
     */
    public synchronized BlackboardArtifactTag addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName, String comment) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return tskCase.addBlackboardArtifactTag(artifact, tagName, comment);       
    }

    /**
     * Deletes a blackboard artifact tag.
     * @param [in] tag The tag to delete.
     * @throws TskCoreException 
     */
    public synchronized void deleteBlackboardArtifactTag(BlackboardArtifactTag tag) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        tskCase.deleteBlackboardArtifactTag(tag);
    }
        
    /**
     * Gets all blackboard artifact tags for the current case.
     * @return A list, possibly empty, of blackboard artifact tags.
     * @throws TskCoreException 
     */
    public List<BlackboardArtifactTag> getAllBlackboardArtifactTags() throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        return tskCase.getAllBlackboardArtifactTags();        
    }
    
    /**
     * Gets blackboard artifact tags count by tag name.
     * @param [in] tagName The tag name of interest. 
     * @return A count of the blackboard artifact tags with the specified tag name.
     * @throws TskCoreException 
     */
    public synchronized long getBlackboardArtifactTagsCountByTagName(TagName tagName) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return tskCase.getBlackboardArtifactTagsCountByTagName(tagName);        
    }
                
    /**
     * Gets blackboard artifact tags by tag name.
     * @param [in] tagName The tag name of interest. 
     * @return A list, possibly empty, of the blackboard artifact tags with the specified tag name.
     * @throws TskCoreException 
     */
    public synchronized List<BlackboardArtifactTag> getBlackboardArtifactTagsByTagName(TagName tagName) throws TskCoreException {        
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return tskCase.getBlackboardArtifactTagsByTagName(tagName);        
    }

    /**
     * Gets blackboard artifact tags for a particular blackboard artifact.
     * @param [in] artifact The blackboard artifact of interest.
     * @return A list, possibly empty, of the tags that have been applied to the artifact.
     * @throws TskCoreException 
     */
    public synchronized List<BlackboardArtifactTag> getBlackboardArtifactTagsByArtifact(BlackboardArtifact artifact) throws TskCoreException {        
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        return tskCase.getBlackboardArtifactTagsByArtifact(artifact);        
    }
        
    @Override
    public void close() throws IOException {  
        saveTagNamesToTagsSettings();            
    }    
            
    private void getExistingTagNames() {
        getTagNamesFromCurrentCase();
        getTagNamesFromTagsSettings();
        getPredefinedTagNames();
        saveTagNamesToTagsSettings();    
        tagNamesInitialized = true; // @@@ This is part of a work around to be removed when database access on the EDT is correctly synchronized.
    }
    
    private void getTagNamesFromCurrentCase() {
        try {
            List<TagName> currentTagNames = tskCase.getAllTagNames();
            for (TagName tagName : currentTagNames) {
                uniqueTagNames.put(tagName.getDisplayName(), tagName);
            }
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag types from the current case", ex); //NON-NLS
        }        
    }
    
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
                        TagName tagName = tskCase.addTagName(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.getColorByName(tagNameAttributes[2]));
                        uniqueTagNames.put(tagName.getDisplayName(), tagName);
                    }
                    catch (TskCoreException ex) {
                        Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to add saved tag name " + tagNameAttributes[0], ex); //NON-NLS
                    }
                }
            }
        }                                            
    }

    private void getPredefinedTagNames() {
        if (!uniqueTagNames.containsKey(NbBundle.getMessage(this.getClass(), "TagsManager.predefTagNames.bookmark.text"))) {
            try {
                TagName tagName = tskCase.addTagName(
                        NbBundle.getMessage(this.getClass(), "TagsManager.predefTagNames.bookmark.text"), "", TagName.HTML_COLOR.NONE);
                uniqueTagNames.put(tagName.getDisplayName(), tagName);
            }
            catch (TskCoreException ex) {
                Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to add predefined 'Bookmark' tag name", ex); //NON-NLS
            }
        }
    }   
    
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
}
