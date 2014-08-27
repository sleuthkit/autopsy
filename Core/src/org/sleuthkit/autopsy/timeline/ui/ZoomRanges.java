package org.sleuthkit.autopsy.timeline.ui;

import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.Minutes;
import org.joda.time.Months;
import org.joda.time.ReadablePeriod;
import org.joda.time.Weeks;
import org.joda.time.Years;

/**
 *
 */
public enum ZoomRanges {

    ONE_MINUTE("One Minute", Minutes.ONE),
    FIFTEEN_MINUTES("Fifteen Minutes", Minutes.minutes(15)),
    ONE_HOUR("One Hour", Hours.ONE),
    SIX_HOURS("Six Hours", Hours.SIX), 
    TWELVE_HOURS("Twelve Hours", Hours.hours(12)),
    ONE_DAY("One Day", Days.ONE),
    THREE_DAYS("Three Days", Days.THREE),
    ONE_WEEK("One Week", Weeks.ONE),
    TWO_WEEK("Two Weeks", Weeks.TWO), 
    ONE_MONTH("One Month", Months.ONE),
    THREE_MONTHS("Three Months", Months.THREE), 
    SIX_MONTHS("Six Months", Months.SIX),
    ONE_YEAR("One Year", Years.ONE), 
    THREE_YEARS("Three Years", Years.THREE), 
    FIVE_YEARS("Five Years", Years.years(5)), 
    TEN_YEARS("Ten Years", Years.years(10)), 
    ALL("All", Minutes.ONE);

    private ZoomRanges(String displayName, ReadablePeriod period) {
        this.displayName = displayName;
        this.period = period;
    }

    
    private String displayName;
    private ReadablePeriod period;

    String getDisplayName() {
        return displayName;
    }

    ReadablePeriod getPeriod() {
        return period;
    }

}
