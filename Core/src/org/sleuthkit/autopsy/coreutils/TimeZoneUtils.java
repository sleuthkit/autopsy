/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.GregorianCalendar;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

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
        int min = (offset % 3600) / 60;

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
     * @param timeZoneId The time zone ID.
     * 
     * @return The time zone string.
     */
    public static String createTimeZoneString(String timeZoneId) {
        TimeZone zone = TimeZone.getTimeZone(timeZoneId);
        int offset = zone.getRawOffset() / 1000;
        int hour = offset / 3600;
        int minutes = (offset % 3600) / 60;
        
        return String.format("(GMT%+d:%02d) %s", hour, minutes, timeZoneId); //NON-NLS
    }

    /**
     * Generates a list of time zones.
     */
    public static List<String> createTimeZoneList() {
        List<String> timeZoneList = new ArrayList<>();
        
        // load and add all timezone
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
            timeZoneList.add(createTimeZoneString(id));
        }
        
        return timeZoneList;
    }

    /**
     * Prevents instantiation.
     */
    private TimeZoneUtils() {

    }
}
