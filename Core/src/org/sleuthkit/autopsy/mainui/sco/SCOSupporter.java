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
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.Tag;

/**
 * An interface to be implemented by nodes that support the SCO columns.
 */
public interface SCOSupporter {

    @NbBundle.Messages({"SCOSupporter_nodescription_text=no description",
        "SCOSupporter.valueLoading=value loading"})
    static final String NO_DESCR = Bundle.SCOSupporter_nodescription_text();

    /**
     * Return the content object for this SCOSupporter.
     *
     * @return A content object.
     */
    default Optional<Content> getContent() {
        return Optional.empty();
    }

    /**
     * Returns a list of all Tags that are associated with the node content.
     *
     * @return A list of Tags.
     */
    default Optional<List<Tag>> getAllTagsFromDatabase() {
        return Optional.empty();
    }

    /**
     * Update the sheet with the updated SCO columns.
     *
     * @param newProps
     */
    void updateSheet(List<NodeProperty<?>> newProps);

    /**
     * Returns Score property for the content.
     *
     * @return
     */
    @NbBundle.Messages({
        "# {0} - significanceDisplayName",
        "SCOSupporter_getScorePropertyAndDescription_description=Has an {0} analysis result score"
    })
    default Pair<Score, String> getScorePropertyAndDescription() throws TskCoreException {
        Score score = Score.SCORE_UNKNOWN;
        Optional<Content> optional = getContent();
        if (optional.isPresent()) {
            Content content = optional.get();
            score = content.getAggregateScore();
        }

        String significanceDisplay = score.getSignificance().getDisplayName();
        String description = Bundle.SCOSupporter_getScorePropertyAndDescription_description(significanceDisplay);
        return Pair.of(score, description);
    }

    /**
     * Returns comment property for the node.
     *
     * Default implementation is a null implementation.
     *
     * @param tags       The list of tags.
     * @param attributes The list of correlation attribute instances.
     *
     * @return Comment property for the underlying content of the node.
     */
    default DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
        return DataResultViewerTable.HasCommentStatus.NO_COMMENT;
    }

    /**
     * Returns occurrences/count property for the node.
     *
     * Default implementation is a null implementation.
     *
     * @param attribute          The correlation attribute for which data will
     *                           be retrieved.
     * @param defaultDescription A description to use when none is determined by
     *                           the getCountPropertyAndDescription method.
     *
     * @return count property for the underlying content of the node.
     */
    default Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attribute, String defaultDescription) {
        return Pair.of(-1L, NO_DESCR);
    }
}
