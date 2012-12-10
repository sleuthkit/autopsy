/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

public class Tags {
    private static final Logger logger = Logger.getLogger(Tags.class.getName());
    
    /**
     * Create a tag for a file with TSK_TAG_NAME as tagName.
     * @param file to create tag for
     * @param tagName TSK_TAG_NAME
     */
    static void createTag(AbstractFile file, String tagName, String comment) {
        try {
            final BlackboardArtifact bookArt = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
            List<BlackboardAttribute> attrs = new ArrayList<BlackboardAttribute>();


            BlackboardAttribute attr1 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID(),
                    "", tagName);
            BlackboardAttribute attr2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(),
                    "", comment);
            attrs.add(attr1);
            attrs.add(attr2);
            bookArt.addAttributes(attrs);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to create tag for " + file.getName());
        }
    }
    
    /**
     * Create a tag for an artifact with TSK_TAG_NAME as tagName.
     * @param artifact to create tag for
     * @param tagName TSK_TAG_NAME
     */
    static void createTag(BlackboardArtifact artifact, String tagName, String comment) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();
            
            AbstractFile file = skCase.getAbstractFileById(artifact.getObjectID());
            final BlackboardArtifact bookArt = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
            List<BlackboardAttribute> attrs = new ArrayList<BlackboardAttribute>();


            BlackboardAttribute attr1 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID(),
                    "", tagName);
            BlackboardAttribute attr2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(),
                    "", comment);
            BlackboardAttribute attr3 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID(),
                    "", artifact.getArtifactID());
            attrs.add(attr1);
            attrs.add(attr2);
            attrs.add(attr3);
            bookArt.addAttributes(attrs);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to create tag for artifact " + artifact.getArtifactID());
        }
    }
    
    /**
     * Create a bookmark tag for a file.
     * @param file to create bookmark tag for
     */
    public static void createBookmark(AbstractFile file, String comment) {
        createTag(file, Bookmarks.BOOKMARK_TAG_NAME, comment);
    }
    
    /**
     * Create a bookmark tag for an artifact.
     * @param artifact to create bookmark tag for
     */
    public static void createBookmark(BlackboardArtifact artifact, String comment) {
        createTag(artifact, Bookmarks.BOOKMARK_TAG_NAME, comment);
    }
    
    /**
     * Get a list of all the bookmarks.
     * @return a list of all bookmark artifacts
     */
    static List<BlackboardArtifact> getBookmarks() {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();            
            return skCase.getBlackboardArtifacts(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME, Bookmarks.BOOKMARK_TAG_NAME);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get list of artifacts from the case.");
        }
        return new ArrayList<BlackboardArtifact>();
    }
    
    /**
     * Get a list of all the tag names.
     * @return a list of all tag names.
     */
    public static String[] getTagNames() {
        Set<String> names = new HashSet<String>();
        //List<String> names = new ArrayList<String>();
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();
            List<BlackboardArtifact> fileTags = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
            List<BlackboardArtifact> artifactTags = skCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
            fileTags.addAll(artifactTags);
            
            for(BlackboardArtifact artifact : fileTags) {
                List<BlackboardAttribute> attributes = artifact.getAttributes();
                for(BlackboardAttribute att : attributes) {
                    if(att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID()) {
                        names.add(att.getValueString());
                        break;
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get list of artifacts from the case.");
        }
        
        return names.toArray(new String[0]);
    }
    
    /**
     * Get all the tags with a specified name.
     * @param name of the requested tags
     * @return a list of all tag artifacts with the given name
     */
    public static List<BlackboardArtifact> getTagsByName(String name) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();
            return skCase.getBlackboardArtifacts(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME, name);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get list of artifacts from the case.");
        }
        
        return new ArrayList<BlackboardArtifact>();
    }
    
    /**
     * Get the tag comment for a specified tag.
     * @param tagArtifactId artifact id of the tag
     * @return the tag comment
     */
    static String getCommentFromTag(long tagArtifactId) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();
            
            BlackboardArtifact artifact = skCase.getBlackboardArtifact(tagArtifactId);
            if(artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID() ||
                    artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                List<BlackboardAttribute> attributes = artifact.getAttributes();
                for(BlackboardAttribute att : attributes) {
                    if(att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID()) {
                        return att.getValueString();
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get artifact " + tagArtifactId + " from case.");
        }
        
        return null;
    }
    
    /**
     * Get the artifact for a result tag.
     * @param tagArtifactId artifact id of the tag
     * @return the tag's artifact
     */
    static BlackboardArtifact getArtifactFromTag(long tagArtifactId) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();
            
            BlackboardArtifact artifact = skCase.getBlackboardArtifact(tagArtifactId);
            if(artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID() ||
                    artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                List<BlackboardAttribute> attributes = artifact.getAttributes();
                for(BlackboardAttribute att : attributes) {
                    if(att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()) {
                        return skCase.getBlackboardArtifact(att.getValueLong());
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get artifact " + tagArtifactId + " from case.");
        }
        
        return null;
    }
}
