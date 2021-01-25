/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import org.sleuthkit.datamodel.CommunicationsUtils;
import org.sleuthkit.datamodel.InvalidAccountIDException;

/**
 * Common utility methods shared among all XRY parser implementations.
 */
final class XRYUtils {

    // Pattern is in reverse due to a Java 8 bug, see calculateSecondsSinceEpoch()
    // function for more details.
    private static final DateTimeFormatter DATE_TIME_PARSER
            = DateTimeFormatter.ofPattern("[(XXX) ][O ][(O) ]a h:m:s M/d/y");

    private static final String DEVICE_LOCALE = "(device)";
    private static final String NETWORK_LOCALE = "(network)";

    public static boolean isPhoneValid(String phoneNumber) {
        try {
            CommunicationsUtils.normalizePhoneNum(phoneNumber);
            return true;
        } catch (InvalidAccountIDException ex) {
            return false;
        }
    }

    public static boolean isEmailValid(String email) {
        try {
            CommunicationsUtils.normalizeEmailAddress(email);
            return true;
        } catch (InvalidAccountIDException ex) {
            return false;
        }
    }

    /**
     * Parses the date time value and calculates seconds since epoch.
     *
     * @param dateTime
     * @return
     */
    public static long calculateSecondsSinceEpoch(String dateTime) {
        String dateTimeWithoutLocale = removeDateTimeLocale(dateTime).trim();
        /**
         * The format of time in XRY reports is of the form:
         *
         * 1/3/1990 1:23:54 AM UTC+4
         *
         * In our current version of Java (openjdk-1.8.0.222), there is a bug
         * with having the timezone offset (UTC+4 or GMT-7, for example) at the
         * end of the date time input. This is fixed in later versions of the
         * JDK (9 and beyond). https://bugs.openjdk.java.net/browse/JDK-8154050
         * Rather than update the JDK to accommodate this, the components of the
         * date time string are reversed:
         *
         * UTC+4 AM 1:23:54 1/3/1990
         *
         * The java time package will correctly parse this date time format.
         */
        String reversedDateTime = reverseOrderOfDateTimeComponents(dateTimeWithoutLocale);
        /**
         * Furthermore, the DateTimeFormatter's timezone offset letter ('O')
         * does not recognize UTC but recognizes GMT. According to
         * https://en.wikipedia.org/wiki/Coordinated_Universal_Time, GMT only
         * differs from UTC by at most 1 second and so substitution will only
         * introduce a trivial amount of error.
         */
        String reversedDateTimeWithGMT = reversedDateTime.replace("UTC", "GMT");
        TemporalAccessor result = DATE_TIME_PARSER.parseBest(reversedDateTimeWithGMT,
                ZonedDateTime::from,
                LocalDateTime::from,
                OffsetDateTime::from);
        //Query for the ZoneID
        if (result.query(TemporalQueries.zoneId()) == null) {
            //If none, assumed GMT+0.
            return ZonedDateTime.of(LocalDateTime.from(result),
                    ZoneId.of("GMT")).toEpochSecond();
        } else {
            return Instant.from(result).getEpochSecond();
        }
    }

    /**
     * Reverses the order of the date time components.
     *
     * Example: 1/3/1990 1:23:54 AM UTC+4 becomes UTC+4 AM 1:23:54 1/3/1990
     *
     * @param dateTime
     * @return
     */
    private static String reverseOrderOfDateTimeComponents(String dateTime) {
        StringBuilder reversedDateTime = new StringBuilder(dateTime.length());
        String[] dateTimeComponents = dateTime.split(" ");
        for (String component : dateTimeComponents) {
            reversedDateTime.insert(0, " ").insert(0, component);
        }
        return reversedDateTime.toString().trim();
    }

    /**
     * Removes the locale from the date time value.
     *
     * Locale in this case being (Device) or (Network).
     *
     * @param dateTime XRY datetime value to be sanitized.
     * @return A purer date time value.
     */
    private static String removeDateTimeLocale(String dateTime) {
        String result = dateTime;
        int deviceIndex = result.toLowerCase().indexOf(DEVICE_LOCALE);
        if (deviceIndex != -1) {
            result = result.substring(0, deviceIndex);
        }
        int networkIndex = result.toLowerCase().indexOf(NETWORK_LOCALE);
        if (networkIndex != -1) {
            result = result.substring(0, networkIndex);
        }
        return result;
    }

    private XRYUtils() {

    }
}
