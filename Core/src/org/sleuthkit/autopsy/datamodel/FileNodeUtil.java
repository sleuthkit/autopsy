/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.texttranslation.TranslationException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Utility class for getting common data about an AbstractFile, such as content tags
 * correlation attributes, content paths and SCO values, to name a few.
 */
class FileNodeUtil {
    
    private static final String NO_TRANSLATION = "";
    private static final Logger logger = Logger.getLogger(FileNodeUtil.class.getName());
    
    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.count.displayName=O",
        "AbstractAbstractFileNode.createSheet.count.noCentralRepo.description=Central repository was not enabled when this column was populated",
        "AbstractAbstractFileNode.createSheet.count.hashLookupNotRun.description=Hash lookup had not been run on this file when the column was populated",
        "# {0} - occuranceCount",
        "AbstractAbstractFileNode.createSheet.count.description=There were {0} datasource(s) found with occurances of the correlation value"})
    static Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attribute) {
        Long count = -1L;  //The column renderer will not display negative values, negative value used when count unavailble to preserve sorting
        String description = Bundle.AbstractAbstractFileNode_createSheet_count_noCentralRepo_description();
        try {
            //don't perform the query if there is no correlation value
            if (attribute != null && StringUtils.isNotBlank(attribute.getCorrelationValue())) {
                count = EamDb.getInstance().getCountUniqueCaseDataSourceTuplesHavingTypeValue(attribute.getCorrelationType(), attribute.getCorrelationValue());
                description = Bundle.AbstractAbstractFileNode_createSheet_count_description(count);
            } else if (attribute != null) {
                description = Bundle.AbstractAbstractFileNode_createSheet_count_hashLookupNotRun_description();
            }
        } catch (EamDbException ex) {
            logger.log(Level.WARNING, "Error getting count of datasources with correlation attribute", ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, "Unable to normalize data to get count of datasources with correlation attribute", ex);
        }

        return Pair.of(count, description);
    }
    
    
    /**
     * Used by subclasses of AbstractAbstractFileNode to add the Score property
     * to their sheets.
     *
     * @param sheetSet the modifiable Sheet.Set returned by
     *                 Sheet.get(Sheet.PROPERTIES)
     * @param tags     the list of tags associated with the file
     */
    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.score.displayName=S",
        "AbstractAbstractFileNode.createSheet.notableFile.description=File recognized as notable.",
        "AbstractAbstractFileNode.createSheet.interestingResult.description=File has interesting result associated with it.",
        "AbstractAbstractFileNode.createSheet.taggedFile.description=File has been tagged.",
        "AbstractAbstractFileNode.createSheet.notableTaggedFile.description=File tagged with notable tag.",
        "AbstractAbstractFileNode.createSheet.noScore.description=No score"})
    static Pair<DataResultViewerTable.Score, String> getScorePropertyAndDescription(AbstractFile content, List<ContentTag> tags) {
        DataResultViewerTable.Score score = DataResultViewerTable.Score.NO_SCORE;
        String description = "";
        if (content.getKnown() == TskData.FileKnown.BAD) {
            score = DataResultViewerTable.Score.NOTABLE_SCORE;
            description = Bundle.AbstractAbstractFileNode_createSheet_notableFile_description();
        }
        try {
            if (score == DataResultViewerTable.Score.NO_SCORE && !content.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT).isEmpty()) {
                score = DataResultViewerTable.Score.INTERESTING_SCORE;
                description = Bundle.AbstractAbstractFileNode_createSheet_interestingResult_description();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting artifacts for file: " + content.getName(), ex);
        }
        if (tags.size() > 0 && (score == DataResultViewerTable.Score.NO_SCORE || score == DataResultViewerTable.Score.INTERESTING_SCORE)) {
            score = DataResultViewerTable.Score.INTERESTING_SCORE;
            description = Bundle.AbstractAbstractFileNode_createSheet_taggedFile_description();
            for (ContentTag tag : tags) {
                if (tag.getName().getKnownStatus() == TskData.FileKnown.BAD) {
                    score = DataResultViewerTable.Score.NOTABLE_SCORE;
                    description = Bundle.AbstractAbstractFileNode_createSheet_notableTaggedFile_description();
                    break;
                }
            }
        }
        return Pair.of(score, description);
    }
    
        /**
     * Used by subclasses of AbstractAbstractFileNode to add the comment
     * property to their sheets.
     *
     * @param sheetSet  the modifiable Sheet.Set returned by
     *                  Sheet.get(Sheet.PROPERTIES)
     * @param tags      the list of tags associated with the file
     * @param attribute the correlation attribute associated with this file,
     *                  null if central repo is not enabled
     */
    @NbBundle.Messages({
        "AbstractAbstractFileNode.createSheet.comment.displayName=C"})
    static DataResultViewerTable.HasCommentStatus getCommentProperty(List<ContentTag> tags, CorrelationAttributeInstance attribute) {

        DataResultViewerTable.HasCommentStatus status = tags.size() > 0 ? DataResultViewerTable.HasCommentStatus.TAG_NO_COMMENT : DataResultViewerTable.HasCommentStatus.NO_COMMENT;

        for (ContentTag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
                //if the tag is null or empty or contains just white space it will indicate there is not a comment
                status = DataResultViewerTable.HasCommentStatus.TAG_COMMENT;
                break;
            }
        }
        if (attribute != null && !StringUtils.isBlank(attribute.getComment())) {
            if (status == DataResultViewerTable.HasCommentStatus.TAG_COMMENT) {
                status = DataResultViewerTable.HasCommentStatus.CR_AND_TAG_COMMENTS;
            } else {
                status = DataResultViewerTable.HasCommentStatus.CR_COMMENT;
            }
        }
        return status;
    }
    
        /**
     * Attempts translation of the content name being passed in.
     *
     * @return The file names translation.
     */
    static String getTranslatedFileName(AbstractFile content) {
        //If already in complete English, don't translate.
        if (content.getName().matches("^\\p{ASCII}+$")) {
            return NO_TRANSLATION;
        }

        TextTranslationService tts = TextTranslationService.getInstance();
        if (tts.hasProvider()) {
            //Seperate out the base and ext from the contents file name.
            String base = FilenameUtils.getBaseName(content.getName());

            try {
                String translation = tts.translate(base);
                String ext = FilenameUtils.getExtension(content.getName());

                //If we have no extension, then we shouldn't add the .
                String extensionDelimiter = (ext.isEmpty()) ? "" : ".";

                //Talk directly to this nodes pcl, fire an update when the translation
                //is complete. 
                if (!translation.isEmpty()) {
                    return translation + extensionDelimiter + ext;
                }
            } catch (NoServiceProviderException noServiceEx) {
                logger.log(Level.WARNING, "Translate unsuccessful because no TextTranslator "
                        + "implementation was provided.", noServiceEx);
            } catch (TranslationException noTranslationEx) {
                logger.log(Level.WARNING, "Could not successfully translate file name "
                        + content.getName(), noTranslationEx);
            }
        }

        return NO_TRANSLATION;
    }
    
    /**
     * Get all tags from the case database that are associated with the file
     *
     * @return a list of tags that are associated with the file
     */
    static List<ContentTag> getContentTagsFromDatabase(AbstractFile content) {
        List<ContentTag> tags = new ArrayList<>();
        try {
            tags.addAll(Case.getCurrentCaseThrows().getServices().getTagsManager().getContentTagsByContent(content));
        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get tags for content " + content.getName(), ex);
        }
        return tags;
    }

    static  CorrelationAttributeInstance getCorrelationAttributeInstance(AbstractFile content) {
        CorrelationAttributeInstance attribute = null;
        if (EamDbUtil.useCentralRepo()) {
            attribute = EamArtifactUtil.getInstanceFromContent(content);
        }
        return attribute;
    }
    
    static String getContentPath(AbstractFile file) {
        try {
            return file.getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Except while calling Content.getUniquePath() on " + file, ex); //NON-NLS
            return "";            //NON-NLS
        }
    }

    static String getContentDisplayName(AbstractFile file) {
        String name = file.getName();
        switch (name) {
            case "..":
                return DirectoryNode.DOTDOTDIR;

            case ".":
                return DirectoryNode.DOTDIR;
            default:
                return name;
        }
    }
}
