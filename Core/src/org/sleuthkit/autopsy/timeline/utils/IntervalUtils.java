/*
 * Sleuth Kit Data Model
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

import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Collection;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.ReadablePeriod;
import org.sleuthkit.autopsy.timeline.zooming.TimeUnits;

/**
 *
 */
public final class IntervalUtils {

    private IntervalUtils() {
    }

    static public Interval getSpanningInterval(Collection<DateTime> times) {
        Interval trange = null;
        for (DateTime t : times) {
            if (trange == null) {
                trange = new Interval(t.getMillis(), t.getMillis() + 1000, DateTimeZone.UTC);
            } else {
                trange = extendInterval(trange, t.getMillis());
            }
        }
        return trange;
    }

    static public Interval span(Interval range, final Interval range2) {
        return new Interval(Math.min(range.getStartMillis(), range2.getStartMillis()), Math.max(range.getEndMillis(), range2.getEndMillis()), DateTimeZone.UTC);
    }

    static public Interval extendInterval(Interval range, final Long eventTime) {
        return new Interval(Math.min(range.getStartMillis(), eventTime), Math.max(range.getEndMillis(), eventTime + 1), DateTimeZone.UTC);
    }

    public static DateTime middleOf(Interval interval) {
        return new DateTime((interval.getStartMillis() + interval.getEndMillis()) / 2);
    }

    public static Interval getAdjustedInterval(Interval oldInterval, TimeUnits requestedUnit) {
        return getIntervalAround(middleOf(oldInterval), requestedUnit.toUnitPeriod());
    }

    static public Interval getIntervalAround(DateTime aroundInstant, ReadablePeriod period) {
        DateTime start = aroundInstant.minus(period);
        DateTime end = aroundInstant.plus(period);
        Interval range = new Interval(start, end);
        DateTime middleOf = IntervalUtils.middleOf(range);
        long halfRange = range.toDurationMillis() / 4;
        return new Interval(middleOf.minus(halfRange), middleOf.plus(halfRange));
    }

    static public Interval getIntervalAround(Instant aroundInstant, TemporalAmount temporalAmount) {
        long start = aroundInstant.minus(temporalAmount).toEpochMilli();
        long end = aroundInstant.plusMillis(1).plus(temporalAmount).toEpochMilli();
        return new Interval(start, Math.max(start + 1, end));
    }

    /**
     * Get an interval the length of the given period, centered around the
     * center of the given interval.
     *
     * @param interval The interval whose center will be the center of the new
     *                 interval.
     * @param period   The length of the new interval
     *
     * @return An interval the length of the given period, centered around the
     *         center of the given interval.
     */
    static public Interval getIntervalAroundMiddle(Interval interval, ReadablePeriod period) {
        return getIntervalAround(middleOf(interval), period);
    }
}
