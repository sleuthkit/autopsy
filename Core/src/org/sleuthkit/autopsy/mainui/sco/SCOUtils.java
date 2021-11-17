/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.sco;

import java.util.List;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.nodes.sco.Bundle;
import org.sleuthkit.datamodel.Tag;

/**
 *
 * A utility class to unify the SCO columns across the nodes\DAOs.
 */
public class SCOUtils {

    private static final Logger logger = Logger.getLogger(SCOUtils.class.getName());

    @NbBundle.Messages({
        "SCOUtils_columnKeys_score_name=S",
        "SCOUtils_columnKeys_comment_name=C",
        "SCOUtils_columnKeys_occurrences_name=O",
        "# {0} - occurrenceCount",
        "# {1} - attributeType",
        "SCOUtils_createSheet_count_description=There were {0} datasource(s) found with occurrences of the correlation value of type {1}",
        "SCOUtils_createSheet_count_noCorrelationValues_description=Unable to find other occurrences because no value exists for the available correlation property"
    })

    public final static String SCORE_COLUMN_NAME = Bundle.SCOUtils_columnKeys_score_name();
    public final static String COMMENT_COLUMN_NAME = Bundle.SCOUtils_columnKeys_comment_name();
    public final static String OCCURANCES_COLUMN_NAME = Bundle.SCOUtils_columnKeys_occurrences_name();

    /**
     * Private constructor for utility class.
     */
    private SCOUtils() {
    }

    public static Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attributeInstance, String defaultDescription) {
        Long count = -1L;
        String description = defaultDescription;
        try {
            if (attributeInstance != null && StringUtils.isNotBlank(attributeInstance.getCorrelationValue())) {
                count = CentralRepository.getInstance().getCountCasesWithOtherInstances(attributeInstance);
                description = Bundle.SCOUtils_createSheet_count_description(count, attributeInstance.getCorrelationType().getDisplayName());
            } else if (attributeInstance != null) {
                description = Bundle.SCOUtils_createSheet_count_noCorrelationValues_description();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, String.format("Error getting count of data sources with %s correlation attribute %s", attributeInstance.getCorrelationType().getDisplayName(), attributeInstance.getCorrelationValue()), ex);
        } catch (CorrelationAttributeNormalizationException ex) {
            logger.log(Level.WARNING, String.format("Unable to normalize %s correlation attribute %s", attributeInstance.getCorrelationType().getDisplayName(), attributeInstance.getCorrelationValue()), ex);
        }
        return Pair.of(count, description);
    }

    /**
     * Returns comment property for the node.
     *
     * @param tags       The list of tags.
     * @param attributes The list of correlation attribute instances.
     *
     * @return Comment property for the underlying content of the node.
     */
    public static DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
        /*
         * Has a tag with a comment been applied to the artifact or its source
         * content?
         */
        DataResultViewerTable.HasCommentStatus status = tags.size() > 0 ? DataResultViewerTable.HasCommentStatus.TAG_NO_COMMENT : DataResultViewerTable.HasCommentStatus.NO_COMMENT;
        for (Tag tag : tags) {
            if (!StringUtils.isBlank(tag.getComment())) {
                status = DataResultViewerTable.HasCommentStatus.TAG_COMMENT;
                break;
            }
        }
        /*
         * Is there a comment in the CR for anything that matches the value and
         * type of the specified attributes.
         */
        try {
            if (CentralRepoDbUtil.commentExistsOnAttributes(attributes)) {
                if (status == DataResultViewerTable.HasCommentStatus.TAG_COMMENT) {
                    status = DataResultViewerTable.HasCommentStatus.CR_AND_TAG_COMMENTS;
                } else {
                    status = DataResultViewerTable.HasCommentStatus.CR_COMMENT;
                }
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Attempted to Query CR for presence of comments in a Blackboard Artifact node and was unable to perform query, comment column will only reflect caseDB", ex);
        }
        return status;
    }
}
