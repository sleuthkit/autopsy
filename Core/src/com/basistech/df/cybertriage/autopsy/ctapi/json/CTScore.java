/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2014 - 2016 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
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
