/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-18 Basis Technology Corp.
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

    private final String displayName;
    private final ReadablePeriod period;

    private ZoomRanges(String displayName, ReadablePeriod period) {
        this.displayName = displayName;
        this.period = period;
    }

    String getDisplayName() {
        return displayName;
    }

    ReadablePeriod getPeriod() {
        return period;
    }
}
