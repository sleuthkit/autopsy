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
package com.basistech.df.cybertriage.autopsy.ctapi.json;

import com.google.common.base.MoreObjects;
import static com.google.common.base.Preconditions.checkArgument;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.Score.Priority;
import org.sleuthkit.datamodel.Score.Significance;

/**
 *
 * Score class represents a conclusion and the relative confidence in the conclusion about 
 * a subject. A subject may be an Item, a category/analysis result etc. 
 * @since 1.7.0
 * 
 */
public enum CTScore {
    
    /*
    Enum names without method defaults to AUTO
    NOTABLE -> NOTABLE
    */
    
    // Unknown None
    UNKNOWN(new Score(Significance.UNKNOWN, Priority.NORMAL)),
    // GOOD_MEDIUM
    LIKELY_NONE(new Score(Significance.LIKELY_NONE, Priority.NORMAL)),
    // SUSPICIOUS_HIGH / BAD_MEDIUM
    LIKELY_NOTABLE(new Score(Significance.LIKELY_NOTABLE, Priority.NORMAL)),
    // GOOD_HIGH
    NONE(new Score(Significance.NONE, Priority.NORMAL)),
    // BAD_HIGH
    NOTABLE(new Score(Significance.NOTABLE, Priority.NORMAL)),
    // SUSPICIOUS (User flagged)
    LIKELY_NOTABLE_MANUAL(new Score(Significance.LIKELY_NOTABLE, Priority.OVERRIDE)),
    // Good (User flagged)
    NONE_MANUAL(new Score(Significance.NONE, Priority.OVERRIDE)),
    // Bad (User flagged)
    NOTABLE_MANUAL(new Score(Significance.NOTABLE, Priority.OVERRIDE));
    
    
    private final Score tskScore;

    /**
     * Create a CTScore instance based on score
     * @param tskScore
     */
    private CTScore(Score tskScore) {
        
        checkArgument(tskScore.getSignificance() == Significance.UNKNOWN ? tskScore.getPriority() == Priority.NORMAL : true, "Unknown Conclusions expects no (NORMAL) priority");
        this.tskScore = tskScore;
    }
    
    public Score getTskCore() {
        return tskScore;
    }
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("Method Category", tskScore.getPriority())
                .add("Significance", tskScore.getSignificance()).toString();
    }
    
}
