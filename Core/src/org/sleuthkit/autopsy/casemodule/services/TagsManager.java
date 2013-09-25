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
import java.util.logging.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagType;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A singleton instance of this class functions as an Autopsy service that 
 * manages the creation, updating, and deletion of tags applied to Content and 
 * BlackboardArtifacts objects by users.
 */
public class TagsManager implements Closeable {
    private static final String TAGS_SETTINGS_FILE_NAME = "tags";
    private static final String TAG_TYPES_SETTING_KEY = "tagTypes";    
    private final SleuthkitCase tskCase;    
    private final HashMap<String, TagType> tagTypes = new HashMap<>();
    
    TagsManager(SleuthkitCase tskCase) {
        this.tskCase = tskCase;
        loadTagTypesFromTagSettings();
    }
          
    private void loadTagTypesFromTagSettings() {
        // Get any tag types already added to the current case.
        try {
            List<TagType> currentTagTypes = tskCase.getTagTypes();
            for (TagType tagType : currentTagTypes) {
                tagTypes.put(tagType.getDisplayName(), tagType);
            }
        }
        catch (TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag types from the current case", ex);                    
        }
        
        // Read the saved tag types, if any, from the tags settings file and 
        // add them to the current case if they haven't already been added, e.g, 
        // when the case was last opened.
        String setting = ModuleSettings.getConfigSetting(TAGS_SETTINGS_FILE_NAME, TAG_TYPES_SETTING_KEY);
        if (null != setting && !setting.isEmpty()) {                
            // Read the tag types setting and break in into tag type tuples.
            List<String> tagTypeTuples = Arrays.asList(setting.split(";"));                        
            
            // Parse each tuple and add the tag types to the current case, one
            // at a time to gracefully discard any duplicates or corrupt tuples.
            for (String tagTypeTuple : tagTypeTuples) {                
                String[] tagTypeAttributes = tagTypeTuple.split(",");
                if (!tagTypes.containsKey(tagTypeAttributes[0])) {
                    TagType tagType = new TagType(tagTypeAttributes[0], tagTypeAttributes[1], TagType.HTML_COLOR.getColorByName(tagTypeAttributes[2]));
                    try {
                        tskCase.addTagType(tagType);
                        tagTypes.put(tagType.getDisplayName(),tagType);
                    }
                    catch(TskCoreException ex) {
                        Logger.getLogger(TagsManager.class.getName()).log(Level.WARNING, "Failed to add saved " + tagType.getDisplayName() + " tag type to the current case", ex);            
                    }            
                }
            }

            saveTagTypesToTagsSettings();            
        }                                    
    }
    
    private void saveTagTypesToTagsSettings() {
        if (!tagTypes.isEmpty()) {
            StringBuilder setting = new StringBuilder();
            for (TagType tagType : tagTypes.values()) {
                if (setting.length() != 0) {
                    setting.append(";");
                }
                setting.append(tagType.getDisplayName()).append(",");
                setting.append(tagType.getDescription()).append(",");
                setting.append(tagType.getColor().name());
            }

            ModuleSettings.setConfigSetting(TAGS_SETTINGS_FILE_NAME, TAG_TYPES_SETTING_KEY, setting.toString());            
        }
    }
    
    /**
     * Gets a list of all tag types currently available for tagging content or 
     * blackboard artifacts.
     * @return A list, possibly empty, of TagType data transfer objects (DTOs). 
     * @throws TskCoreException 
     */
    public List<TagType> getTagTypes() throws TskCoreException {    
        return tskCase.getTagTypes();
    }

    /**
     * Adds a new tag type to the current case and to the tags settings file.
     * @param displayName The display name for the new tag type.
     * @return A TagType object representing the new type on success, null on failure.  
     * @throws TskCoreException 
     */
    public TagType addTagType(String displayName) throws TagTypeAlreadyExistsException, TskCoreException {
        return addTagType(displayName, "", TagType.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag type to the current case and to the tags settings file.
     * @param displayName The display name for the new tag type.
     * @param description The description for the new tag type.
     * @return A TagType object representing the new type on success, null on failure.  
     * @throws TskCoreException 
     */
    public TagType addTagType(String displayName, String description) throws TagTypeAlreadyExistsException, TskCoreException {
        return addTagType(displayName, description, TagType.HTML_COLOR.NONE);
    }

    /**
     * Adds a new tag type to the current case and to the tags settings file.
     * @param displayName The display name for the new tag type.
     * @param description The description for the new tag type.
     * @param color The HTML color to associate with the new tag type.
     * @return A TagType object representing the new type.  
     * @throws TskCoreException 
     */
    public synchronized TagType addTagType(String displayName, String description, TagType.HTML_COLOR color) throws TagTypeAlreadyExistsException, TskCoreException {
        if (tagTypes.containsKey(displayName)) {
            throw new TagTypeAlreadyExistsException();
        }
        
        TagType newTagType = new TagType(displayName, description, color);
        tskCase.addTagType(newTagType); 
        tagTypes.put(newTagType.getDisplayName(), newTagType);
        saveTagTypesToTagsSettings();
        return newTagType;
    }
    
    public class TagTypeAlreadyExistsException extends Exception {
    }
    
    /**
     * Tags a Content object.
     * @param content The Content to tag.
     * @param tagType The type of tag to add.
     * @throws TskCoreException 
     */
    public void addContentTag(Content content, TagType tagType) throws TskCoreException {
        addContentTag(content, tagType, "", 0, content.getSize());
    }    
    
    /**
     * Tags a Content object.
     * @param content The Content to tag.
     * @param tagType The type of tag to add.
     * @param comment A comment to store with the tag.
     * @throws TskCoreException 
     */
    public void addContentTag(Content content, TagType tagType, String comment) throws TskCoreException {
        addContentTag(content, tagType, comment, 0, content.getSize() - 1);
    }    
    
    /**
     * Tags a Content object or a portion of a content object.
     * @param content The Content to tag.
     * @param tagType The type of tag to add.
     * @param comment A comment to store with the tag.
     * @param beginByteOffset Designates the beginning of a tagged extent. 
     * @param endByteOffset Designates the end of a tagged extent.
     * @throws TskCoreException 
     */
    public void addContentTag(Content content, TagType tagType, String comment, long beginByteOffset, long endByteOffset) throws IllegalArgumentException, TskCoreException {
        if (beginByteOffset < 0) {
            throw new IllegalArgumentException("Content extent incorrect: beginByteOffset < 0");            
        }
            
        if (endByteOffset <= beginByteOffset) {
            throw new IllegalArgumentException("Content extent incorrect: endByteOffset <= beginByteOffset");            
        }
            
        if (endByteOffset > content.getSize() - 1) {
            throw new IllegalArgumentException("Content extent incorrect: endByteOffset exceeds content size");            
        }
        
        tskCase.addContentTag(new ContentTag(content, tagType, comment, beginByteOffset, endByteOffset));
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
     * @param tagType The type of tag to add.
     * @throws TskCoreException 
     */
    public void addBlackboardArtifactTag(BlackboardArtifact artifact, TagType tagType) throws TskCoreException {
        addBlackboardArtifactTag(artifact, tagType, "");   
    }
        
    /**
     * Tags a BlackboardArtifact object.
     * @param artifact The BlackboardArtifact to tag.
     * @param tagType The type of tag to add.
     * @param comment A comment to store with the tag.
     * @throws TskCoreException 
     */
    public void addBlackboardArtifactTag(BlackboardArtifact artifact, TagType tagType, String comment) throws TskCoreException {
        tskCase.addBlackboardArtifactTag(new BlackboardArtifactTag(artifact, tagType, comment));       
    }

    void deleteBlackboardArtifactTag(BlackboardArtifactTag tag) throws TskCoreException {
        tskCase.deleteBlackboardArtifactTag(tag);
    }
    
    @Override
    public void close() throws IOException {  
        saveTagTypesToTagsSettings();            
    }
}
