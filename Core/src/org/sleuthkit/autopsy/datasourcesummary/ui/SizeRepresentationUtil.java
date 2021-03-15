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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import org.openide.util.NbBundle;

/**
 * This class provides utilities for representing storage size in most relevant
 * units (i.e. bytes, megabytes, etc.).
 */
public final class SizeRepresentationUtil {

    private static final int SIZE_CONVERSION_CONSTANT = 1000;
    private static final DecimalFormat APPROXIMATE_SIZE_FORMAT = new DecimalFormat("#.##");

    // based on https://www.mrexcel.com/board/threads/how-do-i-format-cells-to-show-gb-mb-kb.140135/
    @NbBundle.Messages({
        "SizeRepresentationUtil_units_bytes=bytes",
        "SizeRepresentationUtil_units_kilobytes=KB",
        "SizeRepresentationUtil_units_megabytes=MB",
        "SizeRepresentationUtil_units_gigabytes=GB",
        "SizeRepresentationUtil_units_terabytes=TB",
        "SizeRepresentationUtil_units_petabytes=PB"
    })
    public enum SizeUnit {
        B(Bundle.SizeRepresentationUtil_units_bytes(), "#", 0),
        KB(Bundle.SizeRepresentationUtil_units_kilobytes(), "#,##0.0,", 1),
        MB(Bundle.SizeRepresentationUtil_units_megabytes(), "#,##0.0,,", 2),
        GB(Bundle.SizeRepresentationUtil_units_gigabytes(), "#,##0.0,,,", 3),
        TB(Bundle.SizeRepresentationUtil_units_terabytes(), "#,##0.0,,,,", 4),
        PB(Bundle.SizeRepresentationUtil_units_petabytes(), "#,##0.0,,,,,", 5);

        private final String suffix;
        private final String excelFormatString;
        private final long divisor;

        SizeUnit(String suffix, String excelFormatString, int power) {
            this.suffix = suffix;
            this.excelFormatString = String.format("%s \"%s\"", excelFormatString, suffix);
            this.divisor = (long) Math.pow(SIZE_CONVERSION_CONSTANT, power);
        }

        public String getSuffix() {
            return suffix;
        }

        public String getExcelFormatString() {
            return excelFormatString;
        }

        public long getDivisor() {
            return divisor;
        }
    }

    /**
     * Get a long size in bytes as a string formated to be read by users.
     *
     * @param size Long value representing a size in bytes.
     *
     * @return Return a string formated with a user friendly version of the size
     * as a string, returns empty String when provided empty size.
     */
    public static String getSizeString(Long size) {
        return getSizeString(size, APPROXIMATE_SIZE_FORMAT, true);
    }

    public static SizeUnit getSizeUnit(Long size) {
        if (size == null) {
            return SizeUnit.values()[0];
        }
        
        for (int unitsIndex = 0; unitsIndex < SizeUnit.values().length; unitsIndex++) {
            SizeUnit unit = SizeUnit.values()[unitsIndex];
            long result = size / unit.getDivisor();
            if (result < SIZE_CONVERSION_CONSTANT) {
                return unit;
            }
        }
        
        return SizeUnit.values()[SizeUnit.values().length - 1];
    }

    /**
     * Get a long size in bytes as a string formated to be read by users.
     *
     * @param size Long value representing a size in byte.s
     * @param format The means of formatting the number.
     * @param showFullSize Optionally show the number of bytes in the
     * datasource.
     *
     * @return Return a string formated with a user friendly version of the size
     * as a string, returns empty String when provided empty size.
     */
    public static String getSizeString(Long size, DecimalFormat format, boolean showFullSize) {
        if (size == null) {
            return "";
        }

        SizeUnit sizeUnit = getSizeUnit(size);
        
        if (sizeUnit == null || sizeUnit.equals(SizeUnit.B)) {
            return String.format("%d %s", size, SizeUnit.B.getSuffix());
        } else if (showFullSize) {
            return String.format("%s (%s)", closestUnitSize, fullSize);
        } else {
            return closestUnitSize;
        }
    }

    private SizeRepresentationUtil() {
    }
}
