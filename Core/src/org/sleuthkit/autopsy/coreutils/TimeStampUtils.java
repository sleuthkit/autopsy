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
package org.sleuthkit.autopsy.coreutils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class offers utility functions to identify and process time stamped folders.
 */
public final class TimeStampUtils {

    // Pattern to identify whether case name contains a generated time stamp.
    // Sample case name with time stamp: Case 1_2015_02_02_12_10_31 for case "Case 1"
    private static final Pattern timeStampPattern = Pattern.compile("\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}$");
    private static final int LENGTH_OF_DATE_TIME_STAMP = 20; // length of the above time stamp

    /**
     * Checks whether a string ends with a time stamp defined by pattern.
     *
     * @param inputString Input string
     * @return true if string ends with a time stamp, false otherwise.
     */
    public static boolean endsWithTimeStamp(String inputString) {
        Matcher m = timeStampPattern.matcher(inputString);
        return m.find();
    }
    
    /**
     * Returns length of time stamp string.
     *
     * @return length of time stamp string.
     */
    public static int getTimeStampLength() {
        return LENGTH_OF_DATE_TIME_STAMP;
    }    
}
