/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.util.Date;
import java.util.Locale;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/*
 * Formatter to wrap another formatter and prepend a timestampe to each
 * formatted string Not currently used.
 */
class TimestampingFormatter extends Formatter {

    Formatter original;
    DateFormat timestampFormat = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.US);
    String lineSeparator = System.getProperty("line.separator");

    TimestampingFormatter(Formatter original) {
        this.original = original;
    }

    @Override
    public String format(LogRecord record) {
        long millis = record.getMillis();
        String timestamp = timestampFormat.format(new Date(millis));

        return timestamp + lineSeparator + original.format(record);
    }
}
