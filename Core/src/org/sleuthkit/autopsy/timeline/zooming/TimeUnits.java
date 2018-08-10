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
import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.Days;
import org.joda.time.Hours;
import org.joda.time.Minutes;
import org.joda.time.Months;
import org.joda.time.Period;
import org.joda.time.ReadablePeriod;
import org.joda.time.Seconds;
import org.joda.time.Years;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Predefined units of time for use in choosing axis labels and sub intervals.
 */
public enum TimeUnits {

    FOREVER(null, null, ChronoUnit.FOREVER, null),
    YEARS(DateTimeFieldType.year(), Years.ONE, ChronoUnit.YEARS, ISODateTimeFormat.year()),
    MONTHS(DateTimeFieldType.monthOfYear(), Months.ONE, ChronoUnit.MONTHS, DateTimeFormat.forPattern("YYYY'-'MMMM")),
    DAYS(DateTimeFieldType.dayOfMonth(), Days.ONE, ChronoUnit.DAYS, DateTimeFormat.forPattern("YYYY'-'MMMM'-'dd")),
    HOURS(DateTimeFieldType.hourOfDay(), Hours.ONE, ChronoUnit.HOURS, DateTimeFormat.forPattern("YYYY'-'MMMM'-'dd HH")),
    MINUTES(DateTimeFieldType.minuteOfHour(), Minutes.ONE, ChronoUnit.MINUTES, DateTimeFormat.forPattern("YYYY'-'MMMM'-'dd HH':'mm")),
    SECONDS(DateTimeFieldType.secondOfMinute(), Seconds.ONE, ChronoUnit.SECONDS, DateTimeFormat.forPattern("YYYY'-'MMMM'-'dd HH':'mm':'ss"));

    private final DateTimeFieldType fieldType;
    private final Period period;
    private final ChronoUnit chronoUnit;
    private final DateTimeFormatter tickFormatter;

    public DateTimeFormatter getTickFormatter() {
        return tickFormatter;
    }

    private TimeUnits(DateTimeFieldType fieldType, ReadablePeriod period, ChronoUnit chronoUnit, DateTimeFormatter tickFormatter) {
        this.fieldType = fieldType;
        if (period != null) {
            this.period = period.toPeriod();
        } else {
            this.period = null;
        }
        this.chronoUnit = chronoUnit;
        this.tickFormatter = tickFormatter;
    }

    public DateTime.Property propertyOf(DateTime dateTime) {
        return dateTime.property(fieldType);
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
