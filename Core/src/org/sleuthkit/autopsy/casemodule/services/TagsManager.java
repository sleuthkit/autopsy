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
 * A per case instance of this class functions as an Autopsy service that 
 * manages the creation, updating, and deletion of tags applied to content and 
 * blackboard artifacts by users. 
 */
public class TagsManager implements Closeable {
    private static final String TAGS_SETTINGS_NAME = "Tags";
    private static final String TAG_NAMES_SETTING_KEY = "TagNames"; 
    private static final TagName[] predefinedTagNames = new TagName[]{new TagName("Bookmark", "", TagName.HTML_COLOR.NONE)};
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
     * @param [out] A list, possibly empty, of TagName data transfer objects (DTOs). 
     * @throws TskCoreException 
     */
    public synchronized void getAllTagNames(List<TagName> tagNames) throws TskCoreException {    
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        tagNames.clear();
        tskCase.getAllTagNames(tagNames);
    }

    /**
     * Gets a list of all tag names currently used for tagging content or 
     * blackboard artifacts.
     * @param [out] A list, possibly empty, of TagName data transfer objects (DTOs). 
     * @throws TskCoreException 
     */
    public synchronized void getTagNamesInUse(List<TagName> tagNames) throws TskCoreException {    
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        tagNames.clear();
        tskCase.getTagNamesInUse(tagNames);
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
        TagName newTagName = new TagName(displayName, description, color);
        tskCase.addTagName(newTagName); 

        // Add the tag name to the tags settings.
        uniqueTagNames.put(newTagName.getDisplayName(), newTagName);
        saveTagNamesToTagsSettings();        

        return newTagName;
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
     * Tags a content object or a section of a content object.
     * @param [in] content The content to tag.
     * @param [in] tagName The name to use for the tag.
     * @param [in] comment A comment to store with the tag.
     * @param [in] beginByteOffset Designates the beginning of a tagged section. 
     * @param [in] endByteOffset Designates the end of a tagged section.
     * @throws IllegalArgumentException, TskCoreException 
     */
    public synchronized void addContentTag(Content content, TagName tagName, String comment, long beginByteOffset, long endByteOffset) throws IllegalArgumentException, TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        if (beginByteOffset < 0 || beginByteOffset > content.getSize() - 1) {
            throw new IllegalArgumentException("beginByteOffset = " + beginByteOffset + " out of content size range (0 - " + (content.getSize() - 1) + ")");            
        }

        if (endByteOffset < 0 || endByteOffset > content.getSize() - 1) {
            throw new IllegalArgumentException("endByteOffset = " + endByteOffset + " out of content size range (0 - " + (content.getSize() - 1) + ")");            
        }
                
        if (endByteOffset < beginByteOffset) {
            throw new IllegalArgumentException("endByteOffset < beginByteOffset");            
        }
            
        tskCase.addContentTag(new ContentTag(content, tagName, comment, beginByteOffset, endByteOffset));
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
     * @param [out] tags A list, possibly empty, of content tags.
     * @throws TskCoreException 
     */
    public void getAllContentTags(List<ContentTag> tags) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        tskCase.getAllContentTags(tags);        
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
    public synchronized void getContentTagsByTagName(TagName tagName, List<ContentTag> tags) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        tskCase.getContentTagsByTagName(tagName, tags);        
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
    public synchronized void addBlackboardArtifactTag(BlackboardArtifact artifact, TagName tagName, String comment) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        tskCase.addBlackboardArtifactTag(new BlackboardArtifactTag(artifact, tskCase.getContentById(artifact.getObjectID()), tagName, comment));       
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
     * @param [out] tags A list, possibly empty, of blackboard artifact tags.
     * @throws TskCoreException 
     */
    public void getAllBlackboardArtifactTags(List<BlackboardArtifactTag> tags) throws TskCoreException {
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }

        tskCase.getAllBlackboardArtifactTags(tags);        
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
     * @return A list, possibly empty, of the content tags with the specified tag name.
     * @throws TskCoreException 
     */
    public synchronized void getBlackboardArtifactTagsByTagName(TagName tagName, List<BlackboardArtifactTag> tags) throws TskCoreException {        
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        tskCase.getBlackboardArtifactTagsByTagName(tagName, tags);        
    }

    /**
     * Gets blackboard artifact tags for a particular blackboard artifact.
     * @param [in] artifact The blackboard artifact of interest.
     * @param [out] tags A list, possibly empty, of the tags that have been applied to the artifact.
     * @throws TskCoreException 
     */
    public synchronized void getBlackboardArtifactTagsByArtifact(BlackboardArtifact artifact, List<BlackboardArtifactTag> tags) throws TskCoreException {        
        // @@@ This is a work around to be removed when database access on the EDT is correctly synchronized.
        if (!tagNamesInitialized) {
            getExistingTagNames();
        }
        
        tskCase.getBlackboardArtifactTagsByArtifact(artifact, tags);        
    }
        
    @Override
    public void close() throws IOException {  
        saveTagNamesToTagsSettings();            
    }    
    
    private void addTagName(TagName tagName, String errorMessage) {
        try {
            tskCase.addTagName(tagName);
            uniqueTagNames.put(tagName.getDisplayName(), tagName);
        }
        catch(TskCoreException ex) {
            Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, errorMessage, ex);            
        }                                
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
            ArrayList<TagName> currentTagNames = new ArrayList<>();
            tskCase.getAllTagNames(currentTagNames);
            for (TagName tagName : currentTagNames) {
                uniqueTagNames.put(tagName.getDisplayName(), tagName);
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
                if (!uniqueTagNames.containsKey(tagNameAttributes[0])) {
                    TagName tagName = new TagName(tagNameAttributes[0], tagNameAttributes[1], TagName.HTML_COLOR.getColorByName(tagNameAttributes[2]));
                    addTagName(tagName, "Failed to add " + tagName.getDisplayName() + " tag name from tag settings to the current case");
                }
            }
        }                                            
    }

    private void getPredefinedTagNames() {
        for (TagName tagName : predefinedTagNames) {
            if (!uniqueTagNames.containsKey(tagName.getDisplayName())) {
                addTagName(tagName, "Failed to add predefined " + tagName.getDisplayName() + " tag name to the current case");
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
