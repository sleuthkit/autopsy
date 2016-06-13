package org.sleuthkit.autopsy.timeline.ui;

import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.Minutes;
import org.joda.time.Months;
import org.joda.time.ReadablePeriod;
import org.joda.time.Weeks;
import org.joda.time.Years;
import org.openide.util.NbBundle;

/**
 *
 */
public enum ZoomRanges {

    ONE_MINUTE(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.onemin.text"), Minutes.ONE),
    FIFTEEN_MINUTES(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.fifteenmin.text"), Minutes.minutes(15)),
    ONE_HOUR(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.onehour.text"), Hours.ONE),
    SIX_HOURS(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.sixhours.text"), Hours.SIX),
    TWELVE_HOURS(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.twelvehours.text"), Hours.hours(12)),
    ONE_DAY(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.oneday.text"), Days.ONE),
    THREE_DAYS(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.threedays.text"), Days.THREE),
    ONE_WEEK(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.oneweek.text"), Weeks.ONE),
    TWO_WEEK(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.twoweeks.text"), Weeks.TWO),
    ONE_MONTH(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.onemonth.text"), Months.ONE),
    THREE_MONTHS(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.threemonths.text"), Months.THREE),
    SIX_MONTHS(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.sixmonths.text"), Months.SIX),
    ONE_YEAR(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.oneyear.text"), Years.ONE),
    THREE_YEARS(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.threeyears.text"), Years.THREE),
    FIVE_YEARS(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.fiveyears.text"), Years.years(5)),
    TEN_YEARS(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.tenyears.text"), Years.years(10)),
    ALL(NbBundle.getMessage(ZoomRanges.class, "Timeline.ui.ZoomRanges.all.text"), Years.years(1_000_000));

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
