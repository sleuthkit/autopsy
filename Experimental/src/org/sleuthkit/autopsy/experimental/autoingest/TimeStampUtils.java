/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for working with strings with the time-stamp suffixes used by
 * auto ingest.
 */
public final class TimeStampUtils {

    /*
     * Sample time stamp suffix: 2015_02_02_12_10_31
     */
    private static final Pattern timeStampPattern = Pattern.compile("\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}$");
    private static final int LENGTH_OF_DATE_TIME_STAMP = 20; // length of the above time stamp
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

    /**
     * Checks whether a string ends with an auto ingest time stamp.
     *
     * @param inputString The string to check.
     *
     * @return True or false.
     */
    public static boolean endsWithTimeStamp(String inputString) {
        Matcher m = timeStampPattern.matcher(inputString);
        return m.find();
    }

    /**
     * Gets the fixed length of the auto-ingest time stamp suffix.
     *
     * @return The length.
     */
    public static int getTimeStampLength() {
        return LENGTH_OF_DATE_TIME_STAMP;
    }

    /**
     * Creates an auto ingest time stamp suffix using the current time.
     *
     * @return The suffix.
     */
    public static String createTimeStamp() {
        return dateFormat.format(Calendar.getInstance().getTime());
    }

    /**
     * Removes an auto ingest timestamp suffix, if it present.
     *
     * @param inputString The string to trim.
     *
     * @return The trimmed string.
     */
    public static String removeTimeStamp(String inputString) {
        String trimmedString = inputString;
        if (inputString != null && endsWithTimeStamp(inputString)) {
            trimmedString = inputString.substring(0, inputString.length() - getTimeStampLength());
        }
        return trimmedString;
    }

    /**
     * Gets the auto ingest time stamp suffix from a string, if it is present.
     *
     * @param inputString the name to check for a timestamp
     *
     * @return The time stamp, may be the empty.
     */
    public static String getTimeStampOnly(String inputString) {
        String timeStamp = "";
        if (inputString != null && endsWithTimeStamp(inputString)) {
            timeStamp = inputString.substring(inputString.length() - getTimeStampLength(), inputString.length());
        }
        return timeStamp;
    }

    /*
     * Private contructor to prevent instantiation.
     */
    private TimeStampUtils() {
    }
}
