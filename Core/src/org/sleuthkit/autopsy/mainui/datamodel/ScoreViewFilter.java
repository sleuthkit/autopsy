/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.Score.Priority;
import org.sleuthkit.datamodel.Score.Significance;

/**
 *
 * Filters for the score view
 */
@Messages({
    "ScoreViewFilter_bad_name=Bad Items",
    "ScoreViewFilter_suspicious_name=Suspicious Items",})
public enum ScoreViewFilter {
    BAD(Arrays.asList(
            new Score(Significance.NOTABLE, Priority.NORMAL),
            new Score(Significance.NOTABLE, Priority.OVERRIDE)),
            1,
            Bundle.ScoreViewFilter_bad_name()),
    SUSPICIOUS(Arrays.asList(
            new Score(Significance.LIKELY_NOTABLE, Priority.NORMAL),
            new Score(Significance.LIKELY_NOTABLE, Priority.OVERRIDE)),
            2,
            Bundle.ScoreViewFilter_suspicious_name());

    private final Collection<Score> scores;
    private final String displayName;
    private final int id;

    private ScoreViewFilter(Collection<Score> scores, int id, String displayName) {
        this.scores = scores == null ? Collections.emptyList() : Collections.unmodifiableCollection(scores);
        this.id = id;
        this.displayName = displayName;
    }

    public Collection<Score> getScores() {
        return scores;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getId() {
        return id;
    }

}
