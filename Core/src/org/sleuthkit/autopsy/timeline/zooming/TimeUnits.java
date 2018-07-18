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
package org.sleuthkit.autopsy.timeline.zooming;

import java.time.temporal.ChronoUnit;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.Minutes;
import org.joda.time.Months;
import org.joda.time.Period;
import org.joda.time.ReadablePeriod;
import org.joda.time.Seconds;
import org.joda.time.Years;

/**
 * Predefined units of time for use in choosing axis labels and sub intervals.
 */
public enum TimeUnits {

    FOREVER(null, ChronoUnit.FOREVER),
    YEARS(Years.ONE, ChronoUnit.YEARS),
    MONTHS(Months.ONE, ChronoUnit.MONTHS),
    DAYS(Days.ONE, ChronoUnit.DAYS),
    HOURS(Hours.ONE, ChronoUnit.HOURS),
    MINUTES(Minutes.ONE, ChronoUnit.MINUTES),
    SECONDS(Seconds.ONE, ChronoUnit.SECONDS);

    private final Period period;
    private final ChronoUnit chronoUnit;

    private TimeUnits(ReadablePeriod period, ChronoUnit chronoUnit) {
        this.period = period.toPeriod();
        this.chronoUnit = chronoUnit;
    }

    public Period toUnitPeriod() {
        return period;
    }

    public ChronoUnit toChronoUnit() {
        return chronoUnit;
    }

    public String getDisplayName() {
        return StringUtils.capitalize(toString().toLowerCase());
    }
}
