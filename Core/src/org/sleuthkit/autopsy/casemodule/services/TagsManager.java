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
 * manages the creation, updating, and deletion of tags applied to Content and 
 * BlackboardArtifacts objects by users. 
 */
public class TagsManager implements Closeable {
    private static final String TAGS_SETTINGS_FILE_NAME = "tags";
    private static final String TAG_NAMES_SETTING_KEY = "tagNames";    
    private final SleuthkitCase tskCase;    
    private final HashMap<String, TagName> tagNames = new HashMap<>();
    
    TagsManager(SleuthkitCase tskCase) {
        this.tskCase = tskCase;
        loadTagNamesFromTagSettings();
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
     * RJCTODO: Discard or properly comment 
     */
    public boolean tagNameExists(String tagDisplayName) {
        return tagNames.containsKey(tagDisplayName);
    }
                    
    /**
     * RJCTODO: Discard or properly comment 
     */
    public TagName getTagName(String tagDisplayName) {
        if (!tagNames.containsKey(tagDisplayName)) {
            // RJCTODO: Throw exception
        }
        
        return tagNames.get(tagDisplayName);
    }
    
    /**
     * Adds a new tag name to the current case and to the tags settings file.
     * @param displayName The display name for the new tag name.
     * @return A TagName object representing the new tag name on success, null on failure.  
     * @throws TskCoreException 
     */
    public TagName addTagName(String displayName) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, "", TagName.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag name to the current case and to the tags settings file.
     * @param displayName The display name for the new tag name.
     * @param description The description for the new tag name.
     * @return A TagName object representing the new tag name on success, null on failure.  
     * @throws TskCoreException 
     */
    public TagName addTagName(String displayName, String description) throws TagNameAlreadyExistsException, TskCoreException {
        return addTagName(displayName, description, TagName.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag name to the current case and to the tags settings file.
     * @param displayName The display name for the new tag name.
     * @param description The description for the new tag name.
     * @param color The HTML color to associate with the new tag name.
     * @return A TagName object representing the new tag name.  
     * @throws TskCoreException 
     */
    public synchronized TagName addTagName(String displayName, String description, TagName.HTML_COLOR color) throws TagNameAlreadyExistsException, TskCoreException {
        if (tagNames.containsKey(displayName)) {
            throw new TagNameAlreadyExistsException();
        }
        
        TagName newTagName = new TagName(displayName, description, color);
        tskCase.addTagName(newTagName); 
        tagNames.put(newTagName.getDisplayName(), newTagName);
        saveTagNamesToTagsSettings();
        return newTagName;
    }
    
    public class TagNameAlreadyExistsException extends Exception {
    }
    
    /**
     * Tags a Content object.
     * @param content The Content to tag.
     * @param tagName The type of tag to add.
     * @throws TskCoreException 
     */
    public void addContentTag(Content content, TagName tagName) throws TskCoreException {
        addContentTag(content, tagName, "", 0, content.getSize() - 1);
    }    
    
    /**
     * Tags a Content object.
     * @param content The Content to tag.
     * @param tagName The name to use for the tag.
     * @param comment A comment to store with the tag.
     * @throws TskCoreException 
     */
    public void addContentTag(Content content, TagName tagName, String comment) throws TskCoreException {
        addContentTag(content, tagName, comment, 0, content.getSize() - 1);
    }    
    
    /**
     * Tags a Content object or a portion of a content object.
     * @param content The Content to tag.
     * @param tagName The name to use for the tag.
     * @param comment A comment to store with the tag.
     * @param beginByteOffset Designates the beginning of a tagged extent. 
     * @param endByteOffset Designates the end of a tagged extent.
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
     * @param tag The tag to delete.
     * @throws TskCoreException 
     */
    public void deleteContentTag(ContentTag tag) throws TskCoreException {
        tskCase.deleteContentTag(tag);
    }
    
    /**
     * Tags a BlackboardArtifact object.
     * @param artifact The BlackboardArtifact to tag.
     * @param tagName The name to use for the tag.
     * @throws TskCoreException 
     */
    public void addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName) throws TskCoreException {
        addBlackboardArtifactTag(artifact, tagName, "");   
    }
        
    /**
     * Tags a BlackboardArtifact object.
     * @param artifact The BlackboardArtifact to tag.
     * @param tagName The name to use for the tag.
     * @param comment A comment to store with the tag.
     * @throws TskCoreException 
     */
    public void addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName, String comment) throws TskCoreException {
        tskCase.addBlackboardArtifactTag(new BlackboardArtifactTag(artifact, tagName, comment));       
    }

    void deleteBlackboardArtifactTag(BlackboardArtifactTag tag) throws TskCoreException {
        tskCase.deleteBlackboardArtifactTag(tag);
    }
    
    /**
     * RJCTODO
     * @param tagName
     * @return 
     */
    public void getContentTags(TagName tagName, List<ContentTag> tags) {
        try {
            tskCase.getContentTagsByTagName(tagName, tags);        
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get content tags from the current case", ex);                    
        }
    }
    
    /**
     * RJCTODO
     * @param tagName
     * @return 
     */
    public void getBlackboardArtifactTags(TagName tagName, List<BlackboardArtifactTag> tags) {
        try {
            tskCase.getBlackboardArtifactTagsByTagName(tagName, tags);        
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get blackboard artifact tags from the current case", ex);                    
        }
    }
    
    @Override
    public void close() throws IOException {  
        saveTagNamesToTagsSettings();            
    }
    
    private void loadTagNamesFromTagSettings() {
        // Get any tag names already defined for the current case.
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
        
        // Read the saved tag names, if any, from the tags settings file and 
        // add them to the current case if they haven't already been added, e.g, 
        // when the case was last opened.
        String setting = ModuleSettings.getConfigSetting(TAGS_SETTINGS_FILE_NAME, TAG_NAMES_SETTING_KEY);
        if (null != setting && !setting.isEmpty()) {                
            // Read the tag types setting and break in into tag type tuples.
            List<String> tagNameTuples = Arrays.asList(setting.split(";"));                        
            
            // Parse each tuple and add the tag types to the current case, one
            // at a time to gracefully discard any duplicates or corrupt tuples.
            for (String tagNameTuple : tagNameTuples) {                
                String[] tagNameAttributes = tagNameTuple.split(",");
                if (!tagNames.containsKey(tagNameAttributes[0])) {
                    TagName tagName = new TagName(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.getColorByName(tagNameAttributes[2]));
                    try {
                        tskCase.addTagName(tagName);
                        tagNames.put(tagName.getDisplayName(),tagName);
                    }
                    catch(TskCoreException ex) {
                        Logger.getLogger(TagsManager.class.getName()).log(Level.WARNING, "Failed to add saved " + tagName.getDisplayName() + " tag name to the current case", ex);            
                    }            
                }
            }

            saveTagNamesToTagsSettings();            
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

            ModuleSettings.setConfigSetting(TAGS_SETTINGS_FILE_NAME, TAG_NAMES_SETTING_KEY, setting.toString());            
        }
    }    
}
