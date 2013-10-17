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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A singleton instance of this class functions as an Autopsy service that 
 * manages the creation, updating, and deletion of tags applied to content and 
 * blackboard artifacts by users. 
 */
public class TagsManager implements Closeable {
    private static final String TAGS_SETTINGS_NAME = "Tags";
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; 
    private static final TagName[] predefinedTagNames = new TagName[]{new TagName("Bookmark", "", TagName.HTML_COLOR.NONE)};
    private final SleuthkitCase tskCase;    
    private final HashMap<String, TagName> tagNames = new HashMap<>();
    private final Object lock = new Object();
    
    // Use this exception and the member hash map to manage uniqueness of hash 
    // names. This is deemed more proactive and informative than leaving this to 
    // the UNIQUE constraint on the display_name field of the tag_names table in 
    // the case database.
    public class TagNameAlreadyExistsException extends Exception {
    }
        
    /**
     * Package-scope constructor for use of Services class. An instance of 
     * TagsManager should be created for each case that is opened.
     * @param [in] tskCase The SleuthkitCase object for the current case. 
     */
    TagsManager(SleuthkitCase tskCase) {
        this.tskCase = tskCase;
        getExistingTagNames();
        saveTagNamesToTagsSettings();                            
    }

    private void getExistingTagNames() {
        getTagNamesFromCurrentCase();
        getTagNamesFromTagsSettings();
        getPredefinedTagNames();
    }
    
    private void getTagNamesFromCurrentCase() {
        try {
            ArrayList<TagName> currentTagNames = new ArrayList<>();
            tskCase.getAllTagNames(currentTagNames);
            for (TagName tagName : currentTagNames) {
                tagNames.put(tagName.getDisplayName(), tagName);
            }
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag types from the current case", ex);                    
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
                if (!tagNames.containsKey(tagNameAttributes[0])) {
                    TagName tagName = new TagName(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.getColorByName(tagNameAttributes[2]));
                    addTagName(tagName, "Failed to add " + tagName.getDisplayName() + " tag name from tag settings to the current case");
                }
            }
        }                                            
    }

    private void getPredefinedTagNames() {
        for (TagName tagName : predefinedTagNames) {
            if (!tagNames.containsKey(tagName.getDisplayName())) {
                addTagName(tagName, "Failed to add predefined " + tagName.getDisplayName() + " tag name to the current case");
            }
        }
    }
    
    private void addTagName(TagName tagName, String errorMessage) {
        try {
            tskCase.addTagName(tagName);
            tagNames.put(tagName.getDisplayName(), tagName);
        }
        catch(TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, errorMessage, ex);            
        }                                
    }
    
    private void saveTagNamesToTagsSettings() {
        if (!tagNames.isEmpty()) {
            StringBuilder setting = new StringBuilder();
            for (TagName tagName : tagNames.values()) {
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
     * Gets a list of all tag names currently available for tagging content or 
     * blackboard artifacts.
     * @return [out] A list, possibly empty, of TagName data transfer objects (DTOs). 
     */
    public void getAllTagNames(List<TagName> tagNames) {    
        try {
            tagNames.clear();
            tskCase.getAllTagNames(tagNames);
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag names from the current case", ex);                    
        }
    }

    /**
     * Gets a list of all tag names currently used for tagging content or 
     * blackboard artifacts.
     * @return [out] A list, possibly empty, of TagName data transfer objects (DTOs). 
     */
    public void getTagNamesInUse(List<TagName> tagNames) {    
        try {
            tagNames.clear();
            tskCase.getTagNamesInUse(tagNames);
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag names from the current case", ex);                    
        }
    }
        
    /**
     * Checks whether a tag name with a given display name exists.
     * @param [in] tagDisplayName The display name for which to check.
     * @return True if the tag name exists, false otherwise.
     */
    public boolean tagNameExists(String tagDisplayName) {
        synchronized(lock) {
            return tagNames.containsKey(tagDisplayName);
        }
    }
                    
    /**
     * Adds a new tag name to the current case and to the tags settings file.
     * @param [in] displayName The display name for the new tag name.
     * @return A TagName data transfer object (DTO) representing the new tag name.  
     * @throws TskCoreException 
     */
    public TagName addTagName(String displayName) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, "", TagName.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag name to the current case and to the tags settings file.
     * @param [in] displayName The display name for the new tag name.
     * @param [in] description The description for the new tag name.
     * @return A TagName data transfer object (DTO) representing the new tag name.  
     * @throws TskCoreException 
     */
    public TagName addTagName(String displayName, String description) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, description, TagName.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag name to the current case and to the tags settings file.
     * @param [in] displayName The display name for the new tag name.
     * @param [in] description The description for the new tag name.
     * @param [in] color The HTML color to associate with the new tag name.
     * @return A TagName data transfer object (DTO) representing the new tag name.  
     * @throws TskCoreException 
     */
    public synchronized TagName addTagName(String displayName, String description, TagName.HTML_COLOR color) throws TagNameAlreadyExistsException, TskCoreException {
        synchronized(lock) {
            if (tagNames.containsKey(displayName)) {
                throw new TagNameAlreadyExistsException();
            }

            // Add the tag name to the case.
            TagName newTagName = new TagName(displayName, description, color);
            tskCase.addTagName(newTagName); 

            // Add the tag name to the tags settings.
            tagNames.put(newTagName.getDisplayName(), newTagName);
            saveTagNamesToTagsSettings();        
    
            return newTagName;
        }        
    }
        
    /**
     * Tags a content object.
     * @param [in] content The content to tag.
     * @param [in] tagName The name to use for the tag.
     * @throws TskCoreException 
     */
    public void addContentTag(Content content, TagName tagName) throws TskCoreException {
        addContentTag(content, tagName, "", 0, content.getSize() - 1);
    }    
    
    /**
     * Tags a content object.
     * @param [in] content The content to tag.
     * @param [in] tagName The name to use for the tag.
     * @param [in] comment A comment to store with the tag.
     * @throws TskCoreException 
     */
    public void addContentTag(Content content, TagName tagName, String comment) throws TskCoreException {
        addContentTag(content, tagName, comment, 0, content.getSize() - 1);
    }    
    
    /**
     * Tags a content object or a portion of a content object.
     * @param [in] content The content to tag.
     * @param [in] tagName The name to use for the tag.
     * @param [in] comment A comment to store with the tag.
     * @param [in] beginByteOffset Designates the beginning of a tagged extent. 
     * @param [in] endByteOffset Designates the end of a tagged extent.
     * @throws TskCoreException 
     */
    public void addContentTag(Content content, TagName tagName, String comment, long beginByteOffset, long endByteOffset) throws IllegalArgumentException, TskCoreException {
        if (beginByteOffset < 0) {
            throw new IllegalArgumentException("Content extent incorrect: beginByteOffset < 0");            
        }
            
        if (endByteOffset <= beginByteOffset) {
            throw new IllegalArgumentException("Content extent incorrect: endByteOffset <= beginByteOffset");            
        }
            
        if (endByteOffset > content.getSize() - 1) {
            throw new IllegalArgumentException("Content extent incorrect: endByteOffset exceeds content size");            
        }
        
        tskCase.addContentTag(new ContentTag(content, tagName, comment, beginByteOffset, endByteOffset));
    }
    
    /**
     * Deletes a content tag.
     * @param [in] tag The tag to delete.
     * @throws TskCoreException 
     */
    public void deleteContentTag(ContentTag tag) throws TskCoreException {
        tskCase.deleteContentTag(tag);
    }
    
    /**
     * Gets content tags by tag name.
     * @param [in] tagName The tag name of interest. 
     * @return A list, possibly empty, of the content tags with the specified tag name.
     */
    public void getContentTagsByTagName(TagName tagName, List<ContentTag> tags) {
        try {
            tskCase.getContentTagsByTagName(tagName, tags);        
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get content tags from the current case", ex);                    
        }
    }
        
    /**
     * Tags a blackboard artifact object.
     * @param [in] artifact The blackboard artifact to tag.
     * @param [in] tagName The name to use for the tag.
     * @throws TskCoreException 
     */
    public void addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName) throws TskCoreException {
        addBlackboardArtifactTag(artifact, tagName, "");   
    }
        
    /**
     * Tags a blackboard artifact object.
     * @param [in] artifact The blackboard artifact to tag.
     * @param [in] tagName The name to use for the tag.
     * @param [in] comment A comment to store with the tag.
     * @throws TskCoreException 
     */
    public void addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName, String comment) throws TskCoreException {
        tskCase.addBlackboardArtifactTag(new BlackboardArtifactTag(artifact, tskCase.getContentById(artifact.getObjectID()), tagName, comment));       
    }

    /**
     * Deletes a blackboard artifact tag.
     * @param [in] tag The tag to delete.
     * @throws TskCoreException 
     */
    public void deleteBlackboardArtifactTag(BlackboardArtifactTag tag) throws TskCoreException {
        tskCase.deleteBlackboardArtifactTag(tag);
    }
        
    /**
     * Gets blackboard artifact tags by tag name.
     * @param [in] tagName The tag name of interest. 
     * @return A list, possibly empty, of the content tags with the specified tag name.
     */
    public void getBlackboardArtifactTagsByTagName(TagName tagName, List<BlackboardArtifactTag> tags) {        
        try {
            tskCase.getBlackboardArtifactTagsByTagName(tagName, tags);        
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get blackboard artifact tags from the current case", ex);                    
        }
    }

    /**
     * Gets blackboard artifact tags for a particular blackboard artifact.
     * @param [in] artifact The blackboard artifact of interest.
     * @param [out] tags A list, possibly empty, of the tags that have been applied to the artifact.
     * @throws TskCoreException 
     */
    public void getBlackboardArtifactTagsByArtifact(BlackboardArtifact artifact, List<BlackboardArtifactTag> tags) {        
        try {
            tskCase.getBlackboardArtifactTagsByArtifact(artifact, tags);        
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get blackboard artifact tags from the current case", ex);                    
        }
    }
        
    @Override
    public void close() throws IOException {  
        saveTagNamesToTagsSettings();            
    }    
}
