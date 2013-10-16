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
package org.sleuthkit.autopsy.datamodel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

public class Tags {

    private static final Logger logger = Logger.getLogger(Tags.class.getName());
    public static final String BOOKMARK_TAG_NAME = "Bookmark";
    private static final String EMPTY_COMMENT = "";
    
    /**
     * Create a tag for a file with TSK_TAG_NAME as tagName.
     *
     * @param file to create tag for
     * @param tagName TSK_TAG_NAME
     * @param comment the tag comment, or null if not present
     */
    public static void createTag(AbstractFile file, String tagName, String comment) {
        try {
            final BlackboardArtifact bookArt = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE);
            List<BlackboardAttribute> attrs = new ArrayList<>();


            BlackboardAttribute attr1 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID(),
                    "", tagName);
            attrs.add(attr1);

            if (comment != null && !comment.isEmpty()) {
                BlackboardAttribute attr2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(),
                        "", comment);
                attrs.add(attr2);
            }
            bookArt.addAttributes(attrs);            
        } 
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to create tag for " + file.getName(), ex);
        }
    }

    /**
     * Create a tag for an artifact with TSK_TAG_NAME as tagName.
     *
     * @param artifact to create tag for
     * @param tagName TSK_TAG_NAME
     * @param comment the tag comment or null if not present
     */
    public static void createTag(BlackboardArtifact artifact, String tagName, String comment) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();

            AbstractFile file = skCase.getAbstractFileById(artifact.getObjectID());
            final BlackboardArtifact bookArt = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT);
            List<BlackboardAttribute> attrs = new ArrayList<>();


            BlackboardAttribute attr1 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID(),
                    "", tagName);

            if (comment != null && !comment.isEmpty()) {
                BlackboardAttribute attr2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(),
                        "", comment);
                attrs.add(attr2);
            }

            BlackboardAttribute attr3 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID(),
                    "", artifact.getArtifactID());
            attrs.add(attr1);

            attrs.add(attr3);
            bookArt.addAttributes(attrs);                 
        } 
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to create tag for artifact " + artifact.getArtifactID(), ex);
        }
    }

    /**
     * Create a bookmark tag for a file.
     *
     * @param file to create bookmark tag for
     * @param comment the bookmark comment
     */
    public static void createBookmark(AbstractFile file, String comment) {
        createTag(file, Tags.BOOKMARK_TAG_NAME, comment);
    }

    /**
     * Create a bookmark tag for an artifact.
     *
     * @param artifact to create bookmark tag for
     * @param comment the bookmark comment
     */
    public static void createBookmark(BlackboardArtifact artifact, String comment) {
        createTag(artifact, Tags.BOOKMARK_TAG_NAME, comment);
    }

    /**
     * Get a list of all the bookmarks.
     *
     * @return a list of all bookmark artifacts
     */
    static List<BlackboardArtifact> getBookmarks() {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();
            return skCase.getBlackboardArtifacts(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME, Tags.BOOKMARK_TAG_NAME);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get list of artifacts from the case", ex);
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of all the unique tag names associated with the current case plus any
     * tag names stored in the application settings file.
     *
     * @return A collection of tag names.
     */
    public static TreeSet<String> getAllTagNames() {
        // Use a TreeSet<> so the union of the tag names from the two sources will be sorted.
        TreeSet<String> tagNames = getTagNamesFromCurrentCase();
        
        // Make sure the book mark tag is always included.
        tagNames.add(BOOKMARK_TAG_NAME);
                        
        return tagNames;
    }
        
    /**
     * Get a list of all the unique tag names associated with the current case. 
     * Uses a custom query for speed when dealing with thousands of tags.
     *
     * @return A collection of tag names.
     */
    @SuppressWarnings("deprecation")
    public static TreeSet<String> getTagNamesFromCurrentCase() {
        TreeSet<String> tagNames = new TreeSet<>();
        
        ResultSet rs = null;
        SleuthkitCase skCase = null;
        try {
            skCase = Case.getCurrentCase().getSleuthkitCase();
            rs = skCase.runQuery("SELECT value_text"
                    + " FROM blackboard_attributes"
                    + " WHERE attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID()
                    + " GROUP BY value_text"
                    + " ORDER BY value_text");
            while (rs.next()) {
                tagNames.add(rs.getString("value_text"));
            }
        } 
        catch (IllegalStateException ex) {
            // Case.getCurrentCase() throws IllegalStateException if there is no current autopsy case.
        }
        catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to query the blackboard for tag names", ex);
        } 
        finally {
            if (null != skCase && null != rs) {
                try {
                    skCase.closeRunQuery(rs);
                } catch (SQLException ex) {
                    logger.log(Level.SEVERE, "Failed to close the query for blackboard for tag names", ex);
                }
            }
        }
        
        // Make sure the book mark tag is always included.
        tagNames.add(BOOKMARK_TAG_NAME);
                
        return tagNames;
    }

    /**
     * Get the tag comment for a specified tag.
     *
     * @param tagArtifactId artifact id of the tag
     * @return the tag comment
     */
    static String getCommentFromTag(long tagArtifactId) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();

            BlackboardArtifact artifact = skCase.getBlackboardArtifact(tagArtifactId);
            if (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                    || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                List<BlackboardAttribute> attributes = artifact.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID()) {
                        return att.getValueString();
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get artifact " + tagArtifactId + " from case", ex);
        }

        return EMPTY_COMMENT;
    }

    /**
     * Get the artifact for a result tag.
     *
     * @param tagArtifactId artifact id of the tag
     * @return the tag's artifact
     */
    static BlackboardArtifact getArtifactFromTag(long tagArtifactId) {
        try {
            Case currentCase = Case.getCurrentCase();
            SleuthkitCase skCase = currentCase.getSleuthkitCase();

            BlackboardArtifact artifact = skCase.getBlackboardArtifact(tagArtifactId);
            if (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID()
                    || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                List<BlackboardAttribute> attributes = artifact.getAttributes();
                for (BlackboardAttribute att : attributes) {
                    if (att.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()) {
                        return skCase.getBlackboardArtifact(att.getValueLong());
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get artifact " + tagArtifactId + " from case.");
        }

        return null;
    }

    /**
     * Looks up the tag names associated with either a tagged artifact or a tag artifact.
     * 
     * @param artifact The artifact
     * @return A set of unique tag names
     */
    public static HashSet<String> getUniqueTagNamesForArtifact(BlackboardArtifact artifact) {
        return getUniqueTagNamesForArtifact(artifact.getArtifactID(), artifact.getArtifactTypeID());
    }    

    /**
     * Looks up the tag names associated with either a tagged artifact or a tag artifact.
     * 
     * @param artifactID The ID of the artifact
     * @param artifactTypeID The ID of the artifact type
     * @return A set of unique tag names
     */
    public static HashSet<String> getUniqueTagNamesForArtifact(long artifactID, int artifactTypeID) {
        HashSet<String> tagNames = new HashSet<>();
        
        try {
            ArrayList<Long> tagArtifactIDs = new ArrayList<>();
            if (artifactTypeID == ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID() ||
                artifactTypeID == ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
                tagArtifactIDs.add(artifactID);
            } else {
                List<BlackboardArtifact> tags = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifacts(ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT, artifactID);
                for (BlackboardArtifact tag : tags) {
                    tagArtifactIDs.add(tag.getArtifactID());
                }
            }

            for (Long tagArtifactID : tagArtifactIDs) {
                String whereClause = "WHERE artifact_id = " + tagArtifactID + " AND attribute_type_id = " + ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID();
                List<BlackboardAttribute> attributes = Case.getCurrentCase().getSleuthkitCase().getMatchingAttributes(whereClause);
                for (BlackboardAttribute attr : attributes) {
                    tagNames.add(attr.getValueString());
                }
            }
        } 
        catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for artifact " + artifactID, ex);
        }
        
        return tagNames;
    }    
}
