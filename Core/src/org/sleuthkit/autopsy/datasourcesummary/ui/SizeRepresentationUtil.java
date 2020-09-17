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

    private static List<String> UNITS = Arrays.asList(
            Bundle.SizeRepresentationUtil_units_bytes(),
            Bundle.SizeRepresentationUtil_units_kilobytes(),
            Bundle.SizeRepresentationUtil_units_megabytes(),
            Bundle.SizeRepresentationUtil_units_gigabytes(),
            Bundle.SizeRepresentationUtil_units_terabytes(),
            Bundle.SizeRepresentationUtil_units_petabytes()
    );

    /**
     * Get a long size in bytes as a string formated to be read by users.
     *
     * @param size Long value representing a size in bytes.
     *
     * @return Return a string formated with a user friendly version of the size
     *         as a string, returns empty String when provided empty size.
     */
    public static String getSizeString(Long size) {
        return getSizeString(size, APPROXIMATE_SIZE_FORMAT, true);
    }

    /**
     * Get a long size in bytes as a string formated to be read by users.
     *
     * @param size         Long value representing a size in byte.s
     * @param format       The means of formatting the number.
     * @param showFullSize Optionally show the number of bytes in the
     *                     datasource.
     *
     * @return Return a string formated with a user friendly version of the size
     *         as a string, returns empty String when provided empty size.
     */
    @NbBundle.Messages({
        "SizeRepresentationUtil_units_bytes= bytes",
        "SizeRepresentationUtil_units_kilobytes= kB",
        "SizeRepresentationUtil_units_megabytes= MB",
        "SizeRepresentationUtil_units_gigabytes= GB",
        "SizeRepresentationUtil_units_terabytes= TB",
        "SizeRepresentationUtil_units_petabytes= PB"
    })
    public static String getSizeString(Long size, DecimalFormat format, boolean showFullSize) {
        if (size == null) {
            return "";
        }
        double approximateSize = size;
        int unitsIndex = 0;
        for (; unitsIndex < UNITS.size(); unitsIndex++) {
            if (approximateSize < SIZE_CONVERSION_CONSTANT) {
                break;
            } else {
                approximateSize /= SIZE_CONVERSION_CONSTANT;
            }
        }

        String fullSize = size + UNITS.get(0);
        String closestUnitSize = format.format(approximateSize) + UNITS.get(unitsIndex);

        if (unitsIndex == 0) {
            return fullSize;
        } else if (showFullSize) {
            return String.format("%s (%s)", closestUnitSize, fullSize);
        } else {
            return closestUnitSize;
        }
    }

    private SizeRepresentationUtil() {
    }
}
