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
package org.sleuthkit.autopsy.discovery.search;

import java.util.Objects;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Requests a thumbnail to be generated for a given Case, domain and size.
 * IconSize should be a value obtained from ImageUtils.
 */
public class DomainSearchThumbnailRequest {

    private final SleuthkitCase sleuthkitCase;
    private final String domain;
    private final int iconSize;

    /**
     * Construct a new DomainSearchThumbnailRequest.
     *
     * @param sleuthkitCase The case database for this thumbnail request.
     * @param domain        The domain name for this thumbnail request.
     * @param iconSize      The size of icon that this thumbnail request should
     *                      retrieve.
     */
    public DomainSearchThumbnailRequest(SleuthkitCase sleuthkitCase,
            String domain, int iconSize) {
        this.sleuthkitCase = sleuthkitCase;
        this.domain = domain;
        this.iconSize = iconSize;
    }

    /**
     * Get the case database for this thumbnail request.
     *
     * @return The case database for this thumbnail request.
     */
    public SleuthkitCase getSleuthkitCase() {
        return sleuthkitCase;
    }

    /**
     * Get the domain name for this thumbnail request.
     *
     * @return The domain name for this thumbnail request.
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Get the size of icon that this thumbnail request should retrieve.
     *
     * @return The size of icon that this thumbnail request should retrieve.
     */
    public int getIconSize() {
        return iconSize;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof DomainSearchThumbnailRequest)) {
            return false;
        }

        DomainSearchThumbnailRequest otherRequest = (DomainSearchThumbnailRequest) other;
        return this.sleuthkitCase == otherRequest.getSleuthkitCase()
                && this.domain.equals(otherRequest.getDomain())
                && this.iconSize == otherRequest.getIconSize();
    }

    @Override
    public int hashCode() {
        return 79 * 5 + Objects.hash(this.domain, this.iconSize);
    }
}
