/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.search;

import org.sleuthkit.autopsy.discovery.search.SearchData.Type;
import org.sleuthkit.datamodel.AbstractFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import static org.sleuthkit.autopsy.discovery.search.SearchData.Type.OTHER;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Container for files that holds all necessary data for grouping and sorting.
 */
public class ResultFile extends Result {

    private final static Logger logger = Logger.getLogger(ResultFile.class.getName());
    private final List<String> keywordListNames;
    private final List<String> hashSetNames;
    private final List<String> interestingSetNames;
    private final List<String> objectDetectedNames;
    private final List<AbstractFile> instances = new ArrayList<>();
    private Score currentScore = Score.SCORE_UNKNOWN;
    private String scoreDescription = null;
    private boolean deleted = false;
    private Type fileType;

    /**
     * Create a ResultFile from an AbstractFile
     *
     * @param abstractFile
     */
    public ResultFile(AbstractFile abstractFile) {
        try {
            //call get uniquePath to cache the path
            abstractFile.getUniquePath();
        } catch (TskCoreException ignored) {
            //path wasnt cached will likely be called on EDT later JIRA-5972
        }
        //store the file the ResultFile was created for as the first value in the instances list
        instances.add(abstractFile);
        if (abstractFile.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            deleted = true;
        }
        updateScoreAndDescription(abstractFile);
        keywordListNames = new ArrayList<>();
        hashSetNames = new ArrayList<>();
        interestingSetNames = new ArrayList<>();
        objectDetectedNames = new ArrayList<>();
        fileType = fromMIMEtype(abstractFile.getMIMEType());
    }

    /**
     * Add an AbstractFile to the list of files which are instances of this
     * file.
     *
     * @param duplicate The abstract file to add as a duplicate.
     */
    public void addDuplicate(AbstractFile duplicate) {
        if (deleted && !duplicate.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            deleted = false;
        }
        if (fileType == Type.OTHER) {
            fileType = fromMIMEtype(duplicate.getMIMEType());
        }
        updateScoreAndDescription(duplicate);
        try {
            //call get uniquePath to cache the path
            duplicate.getUniquePath();
        } catch (TskCoreException ignored) {
            //path wasnt cached will likely be called on EDT later JIRA-5972
        }
        instances.add(duplicate);
    }

    /**
     * Get the aggregate score of this ResultFile. Calculated as the highest
     * score among all instances it represents.
     *
     * @return The score of this ResultFile.
     */
    public Score getScore() {
        return currentScore;
    }

    /**
     * Get the description for the score assigned to this item.
     *
     * @return The score description of this ResultFile.
     */
    public String getScoreDescription() {
        return scoreDescription;
    }

    /**
     * Get the aggregate deleted status of this ResultFile. A file is identified
     * as deleted if all instances of it are deleted.
     *
     * @return The deleted status of this ResultFile.
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Get the list of AbstractFiles which have been identified as instances of
     * this file.
     *
     * @return The list of AbstractFiles which have been identified as instances
     *         of this file.
     */
    public List<AbstractFile> getAllInstances() {
        return Collections.unmodifiableList(instances);
    }

    /**
     * Get the file type.
     *
     * @return The FileType enum.
     */
    public Type getFileType() {
        return fileType;
    }

    /**
     * Add a keyword list name that matched this file.
     *
     * @param keywordListName
     */
    public void addKeywordListName(String keywordListName) {
        if (!keywordListNames.contains(keywordListName)) {
            keywordListNames.add(keywordListName);
        }

        // Sort the list so the getKeywordListNames() will be consistent regardless of the order added
        Collections.sort(keywordListNames);
    }

    /**
     * Get the keyword list names for this file
     *
     * @return the keyword list names that matched this file.
     */
    public List<String> getKeywordListNames() {
        return Collections.unmodifiableList(keywordListNames);
    }

    /**
     * Add a hash set name that matched this file.
     *
     * @param hashSetName
     */
    public void addHashSetName(String hashSetName) {
        if (!hashSetNames.contains(hashSetName)) {
            hashSetNames.add(hashSetName);
        }

        // Sort the list so the getHashHitNames() will be consistent regardless of the order added
        Collections.sort(hashSetNames);
    }

    /**
     * Get the hash set names for this file
     *
     * @return The hash set names that matched this file.
     */
    public List<String> getHashSetNames() {
        return Collections.unmodifiableList(hashSetNames);
    }

    /**
     * Add an interesting file set name that matched this file.
     *
     * @param interestingSetName
     */
    public void addInterestingSetName(String interestingSetName) {
        if (!interestingSetNames.contains(interestingSetName)) {
            interestingSetNames.add(interestingSetName);
        }

        // Sort the list so the getInterestingSetNames() will be consistent regardless of the order added
        Collections.sort(interestingSetNames);
    }

    /**
     * Get the interesting item set names for this file
     *
     * @return the interesting item set names that matched this file.
     */
    public List<String> getInterestingSetNames() {
        return Collections.unmodifiableList(interestingSetNames);
    }

    /**
     * Add an object detected in this file.
     *
     * @param objectDetectedName
     */
    public void addObjectDetectedName(String objectDetectedName) {
        if (!objectDetectedNames.contains(objectDetectedName)) {
            objectDetectedNames.add(objectDetectedName);
        }

        // Sort the list so the getObjectDetectedNames() will be consistent regardless of the order added
        Collections.sort(objectDetectedNames);
    }

    /**
     * Get the objects detected for this file
     *
     * @return the objects detected in this file.
     */
    public List<String> getObjectDetectedNames() {
        return Collections.unmodifiableList(objectDetectedNames);
    }

    /**
     * Get the AbstractFile
     *
     * @return the AbstractFile object
     */
    public AbstractFile getFirstInstance() {
        return instances.get(0);
    }

    @Override
    public String toString() {
        return getFirstInstance().getName() + "(" + getFirstInstance().getId() + ") - "
                + getFirstInstance().getSize() + ", " + getFirstInstance().getParentPath() + ", "
                + getFirstInstance().getDataSourceObjectId() + ", " + getFrequency().toString() + ", "
                + String.join(",", keywordListNames) + ", " + getFirstInstance().getMIMEType();
    }

    @Override
    public int hashCode() {
        if (StringUtils.isBlank(this.getFirstInstance().getMd5Hash()) 
                || HashUtility.isNoDataMd5(this.getFirstInstance().getMd5Hash())) {
            return super.hashCode();
        } else {
            //if the file has a valid MD5 use the hashcode of the MD5 for deduping files with the same MD5
            return this.getFirstInstance().getMd5Hash().hashCode();
        }

    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ResultFile)
                || StringUtils.isBlank(this.getFirstInstance().getMd5Hash()) 
                || HashUtility.isNoDataMd5(this.getFirstInstance().getMd5Hash())) {
            return super.equals(obj);
        } else {
            //if the file has a valid MD5 compare use the MD5 for equality check
            return this.getFirstInstance().getMd5Hash().equals(((ResultFile) obj).getFirstInstance().getMd5Hash());
        }
    }

    
    @NbBundle.Messages({
        "# {0} - significanceDisplayName",
        "ResultFile_updateScoreAndDescription_description=Has an {0} analysis result score"
    })
    private void updateScoreAndDescription(AbstractFile file) {
        Score score = Score.SCORE_UNKNOWN;
        try {
            score = Case.getCurrentCaseThrows().getSleuthkitCase().getScoringManager().getAggregateScore(file.getId());
        } catch (NoCurrentCaseException | TskCoreException ex) {
            
        }
        
        this.currentScore = score;
        String significanceDisplay = score.getSignificance().getDisplayName();
        this.scoreDescription =  Bundle.ResultFile_updateScoreAndDescription_description(significanceDisplay);
    }

    /**
     * Get the enum matching the given MIME type.
     *
     * @param mimeType The MIME type for the file.
     *
     * @return the corresponding enum (will be OTHER if no types matched)
     */
    public static Type fromMIMEtype(String mimeType) {
        for (Type type : Type.values()) {
            if (type.getMediaTypes().contains(mimeType)) {
                return type;
            }
        }
        return OTHER;
    }

    @Override
    public long getDataSourceObjectId() {
        return getFirstInstance().getDataSourceObjectId();
    }

    @Override
    public Content getDataSource() throws TskCoreException {
        return getFirstInstance().getDataSource();
    }

    @Override
    public TskData.FileKnown getKnown() {
        return getFirstInstance().getKnown();
    }

    @Override
    public Type getType() {
        return fileType;
    }
}
