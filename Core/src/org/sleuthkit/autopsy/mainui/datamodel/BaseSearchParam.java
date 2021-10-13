/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.Objects;

/**
 * Base implementation of search parameters to provide to a DAO.
 */
public class BaseSearchParam implements SearchParam {

    private final long startItem;
    private final Long maxResultsCount;

    /**
     * Constructor that gets all results.
     */
    public BaseSearchParam() {
        this(0, null);
    }

    public BaseSearchParam(long startItem, Long maxResultsCount) {
        this.startItem = startItem;
        this.maxResultsCount = maxResultsCount;
    }

    @Override
    public long getStartItem() {
        return startItem;
    }

    @Override
    public Long getMaxResultsCount() {
        return maxResultsCount;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 19 * hash + (int) (this.startItem ^ (this.startItem >>> 32));
        hash = 19 * hash + Objects.hashCode(this.maxResultsCount);
        return hash;
    }

    /**
     * Meant to be called from a subclass to determine if fields are equivalent.
     *
     * @param obj The object to compare.
     *
     * @return True if fields are equal.
     */
    public boolean equalFields(BaseSearchParam obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (this.startItem != obj.startItem) {
            return false;
        }
        if (!Objects.equals(this.maxResultsCount, obj.maxResultsCount)) {
            return false;
        }
        return true;
    }
}
