/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.utils;

import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.Interval;
import org.joda.time.Minutes;
import org.joda.time.Months;
import org.joda.time.Years;
import org.joda.time.format.DateTimeFormatter;
import org.sleuthkit.autopsy.timeline.zooming.TimeUnits;

/**
 * Bundles up the results of analyzing a time range for the appropriate
 * TimeUnits to use to visualize it. Partly, this class exists so I don't have
 * to have more member variables in other places , and partly because I can only
 * return a single value from a function. This might only be a temporary design
 * but is working well for now.
 */
final public class RangeDivision {

    /**
     * The size of the periods we should divide the interval into.
     */
    private final TimeUnits periodSize;

    /**
     * An adjusted lower bound for the range such that it lines up with a period
     * boundary before or at the start of the timerange
     */
    private final long lowerBound;

    /**
     * An adjusted upper bound for the range such that it lines up with a period
     * boundary at or after the end of the timerange
     */
    private final long upperBound;

    /**
     * The time range this RangeDivision describes
     */
    private final Interval timeRange;

    private RangeDivision(Interval timeRange, TimeUnits periodSize, long lowerBound, long upperBound) {
        this.periodSize = periodSize;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.timeRange = timeRange;
    }

    /**
     * Static factory method.
     *
     * Determine the period size, number of periods, whole period bounds, and
     * formatters to use to visualize the given timerange.
     *
     * @param timeRange
     * @param timeZone
     *
     * @return
     */
    public static RangeDivision getRangeDivision(Interval timeRange, DateTimeZone timeZone) {
        //Check from largest to smallest unit

        //TODO: make this more generic... reduce code duplication -jm
        TimeUnits timeUnit;
        final DateTime startWithZone = timeRange.getStart().withZone(timeZone);
        final DateTime endWithZone = timeRange.getEnd().withZone(timeZone);
        long lower;
        long upper;
        if (Years.yearsIn(timeRange).isGreaterThan(Years.THREE)) {
            timeUnit = TimeUnits.YEARS;
        } else if (Months.monthsIn(timeRange).isGreaterThan(Months.THREE)) {
            timeUnit = TimeUnits.MONTHS;
        } else if (Days.daysIn(timeRange).isGreaterThan(Days.THREE)) {
            timeUnit = TimeUnits.DAYS;
        } else if (Hours.hoursIn(timeRange).isGreaterThan(Hours.THREE)) {
            timeUnit = TimeUnits.HOURS;
        } else if (Minutes.minutesIn(timeRange).isGreaterThan(Minutes.THREE)) {
            timeUnit = TimeUnits.MINUTES;
        } else {
            timeUnit = TimeUnits.SECONDS;
        }
        lower = timeUnit.propertyOf(startWithZone).roundFloorCopy().getMillis();
        upper = timeUnit.propertyOf(endWithZone).roundCeilingCopy().getMillis();
        return new RangeDivision(timeRange, timeUnit, lower, upper); // NON-NLS
    }

    public Interval getOriginalTimeRange() {
        return timeRange;
    }

    /** Get a DateTimeFormatter corresponding to the block size for the tick
     * marks on the date axis of a graph.
     *
     * @return a DateTimeFormatter
     */
    public DateTimeFormatter getTickFormatter() {
        return periodSize.getTickFormatter();
    }

    public TimeUnits getPeriodSize() {
        return periodSize;
    }

    public long getUpperBound() {
        return upperBound;
    }

    public long getLowerBound() {
        return lowerBound;
    }

    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    synchronized public List<Interval> getIntervals(DateTimeZone tz) {

        ArrayList<Interval> intervals = new ArrayList<>();
        //extend range to block bounderies (ie day, month, year)
        final Interval range = new Interval(new DateTime(lowerBound, tz), new DateTime(upperBound, tz));

        DateTime start = range.getStart();
        while (range.contains(start)) {
            //increment for next iteration
            DateTime end = start.plus(getPeriodSize().toUnitPeriod());
            final Interval interval = new Interval(start, end);
            intervals.add(interval);
            start = end;
        }

        return intervals;
    }

}
