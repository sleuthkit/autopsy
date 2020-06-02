/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import java.time.Instant;

/**
 * Utility class that can be used by UI nodes to reduce the number of
 * potentially expensive UI refresh events.
 */
class RefreshThrottler {

    // The last time a refresh was performed.
    private Instant lastRefreshTime;
    private static final long MIN_SECONDS_BETWEEN_RERFESH = 5;

    RefreshThrottler() {
        // Initialize to EPOCH to guarantee the first refresh
        lastRefreshTime = Instant.EPOCH;
    }

    /**
     * @return true if a refresh is due, false otherwise
     */
    boolean isRefreshDue() {
        return Instant.now().isAfter(lastRefreshTime.plusSeconds(MIN_SECONDS_BETWEEN_RERFESH));
    }

    /**
     * Update the last time a refresh was performed.
     * @param refreshTime The last time a refresh was performed.
     */
    void setLastRefreshTime(Instant refreshTime) {
        lastRefreshTime = refreshTime;
    }
}
