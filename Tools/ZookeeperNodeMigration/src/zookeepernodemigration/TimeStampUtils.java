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
package zookeepernodemigration;

import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for working with time stamps of the form
 * 'yyyy_MM_dd_HH_mm_ss'.
 */
final class TimeStampUtils {

    /*
     * Sample time stamp suffix: 2015_02_02_12_10_31
     */
    private static final Pattern TIME_STAMP_PATTERN = Pattern.compile("\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}$");
    private static final int LENGTH_OF_DATE_TIME_STAMP = 20; // length of the above time stamp

    /**
     * Checks whether a string ends with a time stamp.
     *
     * @param inputString The string to check.
     *
     * @return True or false.
     */
    static boolean endsWithTimeStamp(String inputString) {
        Matcher m = TIME_STAMP_PATTERN.matcher(inputString);
        return m.find();
    }

    /**
     * Gets the fixed length of the time stamp suffix.
     *
     * @return The length.
     */
    static int getTimeStampLength() {
        return LENGTH_OF_DATE_TIME_STAMP;
    }

    /**
     * Removes the time stamp suffix from a string, if present.
     *
     * @param inputString The string to trim.
     *
     * @return The trimmed string.
     */
    static String removeTimeStamp(String inputString) {
        String trimmedString = inputString;
        if (inputString != null && endsWithTimeStamp(inputString)) {
            trimmedString = inputString.substring(0, inputString.length() - getTimeStampLength());
        }
        return trimmedString;
    }

    /**
     * Gets the time stamp suffix from a string, if present.
     *
     * @param inputString the name to check for a timestamp
     *
     * @return The time stamp, may be the empty.
     */
    static String getTimeStampOnly(String inputString) {
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
