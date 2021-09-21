/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.datamodel.TimeUtilities;

/**
 * Utility methods for workig with time zones.
 */
public class TimeZoneUtils {

    /**
     * Converts a Java timezone id to a coded string with only alphanumeric
     * characters. Example: "America/New_York" is converted to "EST5EDT" by this
     * method.
     *
     * @param timeZoneId The time zone id.
     *
     * @return The converted time zone string.
     */
    public static String convertToAlphaNumericFormat(String timeZoneId) {

        java.util.TimeZone zone = java.util.TimeZone.getTimeZone(timeZoneId);
        int offset = zone.getRawOffset() / 1000;
        int hour = offset / 3600;
        int min = Math.abs((offset % 3600) / 60);

        DateFormat dfm = new SimpleDateFormat("z");
        dfm.setTimeZone(zone);
        boolean hasDaylight = zone.useDaylightTime();
        String first = dfm.format(new GregorianCalendar(2010, 1, 1).getTime()).substring(0, 3);
        String second = dfm.format(new GregorianCalendar(2011, 6, 6).getTime()).substring(0, 3);
        int mid = hour * -1;
        String result = first + Integer.toString(mid);
        if (min != 0) {
            result = result + ":" + Integer.toString(min);
        }
        if (hasDaylight) {
            result += second;
        }

        return result;
    }

    /**
     * Generate a time zone string containing the GMT offset and ID.
     *
     * @param timeZone The time zone.
     *
     * @return The time zone string.
     */
    public static String createTimeZoneString(TimeZone timeZone) {
        int offset = timeZone.getRawOffset() / 1000;
        int hour = offset / 3600;
        int minutes = Math.abs((offset % 3600) / 60);

        return String.format("(GMT%+d:%02d) %s", hour, minutes, timeZone.getID()); //NON-NLS
    }

    /**
     * Generates a list of time zones.
     */
    public static List<String> createTimeZoneList() {
        /*
         * Create a list of time zones.
         */
        List<TimeZone> timeZoneList = new ArrayList<>();

        String[] ids = SimpleTimeZone.getAvailableIDs();
        for (String id : ids) {
            /*
             * DateFormat dfm = new SimpleDateFormat("z");
             * dfm.setTimeZone(zone); boolean hasDaylight =
             * zone.useDaylightTime(); String first = dfm.format(new Date(2010,
             * 1, 1)); String second = dfm.format(new Date(2011, 6, 6)); int mid
             * = hour * -1; String result = first + Integer.toString(mid);
             * if(hasDaylight){ result = result + second; }
             * timeZoneComboBox.addItem(item + " (" + result + ")");
             */
            timeZoneList.add(TimeZone.getTimeZone(id));
        }

        /*
         * Sort the list of time zones first by offset, then by ID.
         */
        Collections.sort(timeZoneList, new Comparator<TimeZone>() {
            @Override
            public int compare(TimeZone o1, TimeZone o2) {
                int offsetDelta = Integer.compare(o1.getRawOffset(), o2.getRawOffset());

                if (offsetDelta == 0) {
                    return o1.getID().compareToIgnoreCase(o2.getID());
                }

                return offsetDelta;
            }
        });

        /*
         * Create a list of Strings encompassing both the GMT offset and the
         * time zone ID.
         */
        List<String> outputList = new ArrayList<>();

        for (TimeZone timeZone : timeZoneList) {
            outputList.add(createTimeZoneString(timeZone));
        }

        return outputList;
    }

    /**
     * Returns the time formatted in the user selected time zone.
     *
     * @param epochTime
     *
     * @return
     */
    public static String getFormattedTime(long epochTime) {
        return TimeUtilities.epochToTime(epochTime, getTimeZone());
    }

    /**
     * Returns the formatted time in the user selected time zone in ISO8601
     * format.
     *
     * @param epochTime Seconds from java epoch
     *
     * @return Formatted date time string in ISO8601
     */
    public static String getFormattedTimeISO8601(long epochTime) {
        return TimeUtilities.epochToTimeISO8601(epochTime, getTimeZone());
    }

    /**
     * Returns the user preferred timezone.
     *
     * @return TimeZone to use when formatting time values.
     */
    public static TimeZone getTimeZone() {
        if (UserPreferences.displayTimesInLocalTime()) {
            return TimeZone.getDefault();
        }

        return TimeZone.getTimeZone(UserPreferences.getTimeZoneForDisplays());
    }

    /**
     * Prevents instantiation.
     */
    private TimeZoneUtils() {

    }
}
