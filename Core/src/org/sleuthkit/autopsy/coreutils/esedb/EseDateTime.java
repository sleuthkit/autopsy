/*
 * Autopsy Forensic Browser
 *
 * Copyright 2026 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils.esedb;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Decoding of the 8 byte values stored in DateTime columns. ESE defines a
 * DateTime as an OLE automation date (a double counting days since
 * 1899-12-30), but several Windows databases store a FILETIME in the same
 * column type instead, so both readings are tried.
 */
public final class EseDateTime {

    private static final LocalDateTime OLE_EPOCH = LocalDateTime.of(1899, 12, 30, 0, 0);
    private static final long FILETIME_EPOCH_OFFSET_SECONDS = 11644473600L;
    private static final int PLAUSIBLE_FROM_YEAR = 1980;
    private static final int PLAUSIBLE_TO_YEAR = 2100;

    private EseDateTime() {
    }

    /**
     * Decodes the raw 8 bytes of a DateTime column.
     *
     * @param rawValue The 8 bytes, as a little-endian 64-bit integer.
     *
     * @return The time, if the bytes read as a plausible OLE date (first) or
     *         FILETIME (second); empty for 0 and for values that give no
     *         plausible date either way.
     */
    public static Optional<Instant> decode(long rawValue) {
        if (rawValue == 0) {
            return Optional.empty();
        }
        Optional<Instant> ole = fromOleDate(Double.longBitsToDouble(rawValue));
        if (ole.isPresent()) {
            return ole;
        }
        return fromFileTime(rawValue);
    }

    /**
     * @param days Days since 1899-12-30, fractional part = time of day.
     */
    public static Optional<Instant> fromOleDate(double days) {
        if (Double.isNaN(days) || Double.isInfinite(days) || Math.abs(days) > 100_000) {
            return Optional.empty();
        }
        long wholeDays = (long) days;
        double fraction = Math.abs(days - wholeDays);
        long nanos = Math.round(fraction * 86_400_000_000_000.0);
        Instant instant = OLE_EPOCH.plusDays(wholeDays).plusNanos(nanos).toInstant(ZoneOffset.UTC);
        return plausible(instant);
    }

    /**
     * @param fileTime 100 ns intervals since 1601-01-01 UTC.
     */
    public static Optional<Instant> fromFileTime(long fileTime) {
        if (fileTime <= 0) {
            return Optional.empty();
        }
        long seconds = fileTime / 10_000_000L - FILETIME_EPOCH_OFFSET_SECONDS;
        long nanos = (fileTime % 10_000_000L) * 100;
        return plausible(Instant.ofEpochSecond(seconds, nanos));
    }

    private static Optional<Instant> plausible(Instant instant) {
        int year = instant.atOffset(ZoneOffset.UTC).getYear();
        return (year >= PLAUSIBLE_FROM_YEAR && year <= PLAUSIBLE_TO_YEAR) ? Optional.of(instant) : Optional.empty();
    }
}
