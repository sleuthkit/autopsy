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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.scene.layout.Priority;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.Score.Significance;

/**
 * Key for accessing data about files with a score from the DAO.
 */
public class ScoreViewSearchParams {

    private static final String TYPE_ID = "FILE_VIEWS_SCORE";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    private final Set<Pair<Significance, Priority>> scoreFilters;
    private final Long dataSourceId;

    public ScoreViewSearchParams(Collection<Score> scoreFilters, Long dataSourceId) {
        this(
                
                scoreFilters == null 
                        ? Collections.emptySet() : 
                        scoreFilters.stream().map(s -> Pair.of(s.getSignificance(), s.getPriority())).collect(Collectors.toSet()),
                dataSourceId
        );
    }
    
    public ScoreViewSearchParams(Set<Pair<Significance, Priority>> scoreFilters, Long dataSourceId) {
            this.scoreFilters = scoreFilters == null ? Collections.emptySet() : scoreFilters;
        this.dataSourceId = dataSourceId;
    }

    public Collection<Score> getScoreFilters() {
        List<Score> scores = scoreFilters.stream().map(pr -> new Score(pr.getLeft(), pr.getRight())).collect(Collectors.toList());
        return scores;
    }
    
    public Set<Pair<Significance, Priority>> getScoreItemsFilters() {
        return scoreFilters;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }


}
