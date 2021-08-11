/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultCellModel;

/**
 * This class provides utilities for representing storage size in most relevant
 * units (i.e. bytes, megabytes, etc.).
 */
public final class SizeRepresentationUtil {

    private static final int SIZE_CONVERSION_CONSTANT = 1000;
    private static final DecimalFormat APPROXIMATE_SIZE_FORMAT = new DecimalFormat("#.##");

    /**
     * A size unit corresponding to orders of magnitude of bytes (kilobyte, gigabytes, etc.).
     */
    @NbBundle.Messages({
        "SizeRepresentationUtil_units_bytes=bytes",
        "SizeRepresentationUtil_units_kilobytes=KB",
        "SizeRepresentationUtil_units_megabytes=MB",
        "SizeRepresentationUtil_units_gigabytes=GB",
        "SizeRepresentationUtil_units_terabytes=TB",
        "SizeRepresentationUtil_units_petabytes=PB"
    })
    enum SizeUnit {
        BYTES(Bundle.SizeRepresentationUtil_units_bytes(), 0),
        KB(Bundle.SizeRepresentationUtil_units_kilobytes(), 1),
        MB(Bundle.SizeRepresentationUtil_units_megabytes(), 2),
        GB(Bundle.SizeRepresentationUtil_units_gigabytes(), 3),
        TB(Bundle.SizeRepresentationUtil_units_terabytes(), 4),
        PB(Bundle.SizeRepresentationUtil_units_petabytes(), 5);

        private final String suffix;
        private final long divisor;

        /**
         * Main constructor.
         * @param suffix The string suffix to use for size unit.
         * @param power The power of 1000 of bytes for this size unit.
         */
        SizeUnit(String suffix, int power) {
            this.suffix = suffix;
            this.divisor = (long) Math.pow(SIZE_CONVERSION_CONSTANT, power);
        }

        /**
         * @return The string suffix to use for size unit.
         */
        public String getSuffix() {
            return suffix;
        }

        /**
         * @return The divisor to convert from bytes to this unit.
         */
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
    static String getSizeString(Long size) {
        return getSizeString(size, APPROXIMATE_SIZE_FORMAT, true);
    }

    /**
     * Determines the relevant size unit that should be used for a particular size.
     * @param size The size in bytes.
     * @return The relevant size unit.
     */
    static SizeUnit getSizeUnit(Long size) {
        if (size == null) {
            return SizeUnit.values()[0];
        }
        
        for (SizeUnit unit : SizeUnit.values()) {
            long result = size / unit.getDivisor();
            if (result < SIZE_CONVERSION_CONSTANT) {
                return unit;
            }
        }
        
        return SizeUnit.values()[SizeUnit.values().length - 1];
    }

    /**
     * Get a long size in bytes as a string formatted to be read by users.
     *
     * @param size Long value representing a size in byte.s
     * @param format The means of formatting the number.
     * @param showFullSize Optionally show the number of bytes in the
     * datasource.
     *
     * @return Return a string formatted with a user friendly version of the size
     * as a string, returns empty String when provided empty size.
     */
    static String getSizeString(Long size, DecimalFormat format, boolean showFullSize) {
        if (size == null) {
            return "";
        }

        SizeUnit sizeUnit = getSizeUnit(size);
        if (sizeUnit == null) {
            sizeUnit = SizeUnit.BYTES;
        }
        
        String closestUnitSize = String.format("%s %s", 
                format.format(((double) size) / sizeUnit.getDivisor()), sizeUnit.getSuffix());
        
        String fullSize = String.format("%d %s", size, SizeUnit.BYTES.getSuffix());
        if (sizeUnit.equals(SizeUnit.BYTES)) {
            return fullSize;
        } else if (showFullSize) {
            return String.format("%s (%s)", closestUnitSize, fullSize);
        } else {
            return closestUnitSize;
        }
    }
    
    /**
     * Returns a default cell model using size units.
     * @param bytes The number of bytes.
     * @return The default cell model.
     */
    static DefaultCellModel<?> getBytesCell(Long bytes) {
        if (bytes == null) {
            return new DefaultCellModel<>("");
        } else {
            return new DefaultCellModel<>(bytes, SizeRepresentationUtil::getSizeString);
        }
    }

    private SizeRepresentationUtil() {
    }
}
