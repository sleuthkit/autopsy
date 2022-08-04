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
class SearchParams<T> {
    private final T paramData;
    private final long startItem;
    private final Long maxResultsCount;

    public SearchParams(T paramData) {
        this(paramData, 0, null);
    }
    
    public SearchParams(T paramData, long startItem, Long maxResultsCount) {
        this.paramData = paramData;
        this.startItem = startItem;
        this.maxResultsCount = maxResultsCount;
    }

    public T getParamData() {
        return paramData;
    }

    public long getStartItem() {
        return startItem;
    }

    public Long getMaxResultsCount() {
        return maxResultsCount;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + Objects.hashCode(this.paramData);
        hash = 41 * hash + (int) (this.startItem ^ (this.startItem >>> 32));
        hash = 41 * hash + Objects.hashCode(this.maxResultsCount);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SearchParams<?> other = (SearchParams<?>) obj;
        if (this.startItem != other.startItem) {
            return false;
        }
        if (!Objects.equals(this.paramData, other.paramData)) {
            return false;
        }
        if (!Objects.equals(this.maxResultsCount, other.maxResultsCount)) {
            return false;
        }
        return true;
    }

    
    
}
