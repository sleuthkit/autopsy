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

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.Interval;
import org.joda.time.Minutes;
import org.joda.time.Months;
import org.joda.time.Seconds;
import org.joda.time.Years;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
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
     * The number of periods we are going to divide the interval into.
     */
    private final int numberOfBlocks;

    /**
     * A DateTimeFormatter corresponding to the block size for the tick marks on
     * the date axis of a graph.
     */
    private final DateTimeFormatter tickFormatter;

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


   
    private RangeDivision(Interval timeRange, int periodsInRange, TimeUnits periodSize, DateTimeFormatter tickformatter, long lowerBound, long upperBound) {
        this.numberOfBlocks = periodsInRange;
        this.periodSize = periodSize;
        this.tickFormatter = tickformatter;
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
    public static RangeDivision getRangeDivisionInfo(Interval timeRange, DateTimeZone timeZone) {
        //Check from largest to smallest unit

        //TODO: make this more generic... reduce code duplication -jm
        DateTimeFieldType timeUnit;
        final DateTime startWithZone = timeRange.getStart().withZone(timeZone);
        final DateTime endWithZone = timeRange.getEnd().withZone(timeZone);

        if (Years.yearsIn(timeRange).isGreaterThan(Years.THREE)) {
            timeUnit = DateTimeFieldType.year();
            long lower = startWithZone.property(timeUnit).roundFloorCopy().getMillis();
            long upper = endWithZone.property(timeUnit).roundCeilingCopy().getMillis();
            return new RangeDivision(timeRange, Years.yearsIn(timeRange).get(timeUnit.getDurationType()) + 1, TimeUnits.YEARS, ISODateTimeFormat.year(), lower, upper);
        } else if (Months.monthsIn(timeRange).isGreaterThan(Months.THREE)) {
            timeUnit = DateTimeFieldType.monthOfYear();
            long lower = startWithZone.property(timeUnit).roundFloorCopy().getMillis();
            long upper = endWithZone.property(timeUnit).roundCeilingCopy().getMillis();
            return new RangeDivision(timeRange, Months.monthsIn(timeRange).getMonths() + 1, TimeUnits.MONTHS, DateTimeFormat.forPattern("YYYY'-'MMMM"), lower, upper); // NON-NLS
        } else if (Days.daysIn(timeRange).isGreaterThan(Days.THREE)) {
            timeUnit = DateTimeFieldType.dayOfMonth();
            long lower = startWithZone.property(timeUnit).roundFloorCopy().getMillis();
            long upper = endWithZone.property(timeUnit).roundCeilingCopy().getMillis();
            return new RangeDivision(timeRange, Days.daysIn(timeRange).getDays() + 1, TimeUnits.DAYS, DateTimeFormat.forPattern("YYYY'-'MMMM'-'dd"), lower, upper); // NON-NLS
        } else if (Hours.hoursIn(timeRange).isGreaterThan(Hours.THREE)) {
            timeUnit = DateTimeFieldType.hourOfDay();
            long lower = startWithZone.property(timeUnit).roundFloorCopy().getMillis();
            long upper = endWithZone.property(timeUnit).roundCeilingCopy().getMillis();
            return new RangeDivision(timeRange, Hours.hoursIn(timeRange).getHours() + 1, TimeUnits.HOURS, DateTimeFormat.forPattern("YYYY'-'MMMM'-'dd HH"), lower, upper); // NON-NLS
        } else if (Minutes.minutesIn(timeRange).isGreaterThan(Minutes.THREE)) {
            timeUnit = DateTimeFieldType.minuteOfHour();
            long lower = startWithZone.property(timeUnit).roundFloorCopy().getMillis();
            long upper = endWithZone.property(timeUnit).roundCeilingCopy().getMillis();
            return new RangeDivision(timeRange, Minutes.minutesIn(timeRange).getMinutes() + 1, TimeUnits.MINUTES, DateTimeFormat.forPattern("YYYY'-'MMMM'-'dd HH':'mm"), lower, upper); // NON-NLS
        } else {
            timeUnit = DateTimeFieldType.secondOfMinute();
            long lower = startWithZone.property(timeUnit).roundFloorCopy().getMillis();
            long upper = endWithZone.property(timeUnit).roundCeilingCopy().getMillis();
            return new RangeDivision(timeRange, Seconds.secondsIn(timeRange).getSeconds() + 1, TimeUnits.SECONDS, DateTimeFormat.forPattern("YYYY'-'MMMM'-'dd HH':'mm':'ss"), lower, upper); // NON-NLS
        }
    }
    
     public Interval getTimeRange() {
        return timeRange;
    }

    public DateTimeFormatter getTickFormatter() {
        return tickFormatter;
    }

    public int getPeriodsInRange() {
        return numberOfBlocks;
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

    public String formatForTick(Interval interval) {
        return interval.getStart().toString(tickFormatter);
    }
}
