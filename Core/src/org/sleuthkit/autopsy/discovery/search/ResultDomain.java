/*
 * Autopsy
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

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Container for domains that holds all necessary data for grouping and sorting.
 */
public class ResultDomain extends Result {
    private final String domain;
    private final Long activityStart;
    private final Long activityEnd;
    private final Long totalVisits;
    private final Long visitsInLast60;
    private final Long filesDownloaded;
    
    private final Content dataSource;
    private final long dataSourceId;
    
    /**
     * Create a ResultDomain from a String.
     *
     * @param domain The domain the result is being created from.
     */
    ResultDomain(String domain, Long activityStart, Long activityEnd, Long totalVisits,
            Long visitsInLast60, Long filesDownloaded, Content dataSource) {
        this.domain = domain;
        this.dataSource = dataSource;
        this.dataSourceId = dataSource.getId();
        this.activityStart = activityStart;
        this.activityEnd = activityEnd;
        this.totalVisits = totalVisits;
        this.visitsInLast60 = visitsInLast60;
        this.filesDownloaded = filesDownloaded;
        
        this.setFrequency(SearchData.Frequency.UNKNOWN);
    }
    
    public String getDomain() {
        return this.domain;
    }

    public Long getActivityStart() {
        return activityStart;
    }

    public Long getActivityEnd() {
        return activityEnd;
    }
    
    public Long getTotalVisits() {
        return totalVisits;
    }

    public Long getVisitsInLast60() {
        return visitsInLast60;
    }

    public Long getFilesDownloaded() {
        return filesDownloaded;
    }

    @Override
    public long getDataSourceObjectId() {
        return this.dataSourceId;
    }

    @Override
    public Content getDataSource() throws TskCoreException {
        return this.dataSource;
    }

    @Override
    public TskData.FileKnown getKnown() {
        return TskData.FileKnown.UNKNOWN;
    }

    @Override
    public SearchData.Type getType() {
        return SearchData.Type.DOMAIN;
    }
    
    @Override
    public String toString() {
        return "[domain=" + this.domain + ", data_source=" + this.dataSourceId + ", start="
                + this.activityStart + ", end=" + this.activityEnd + ", totalVisits=" + this.totalVisits + ", visitsLast60=" 
                + this.visitsInLast60 + ", downloads=" + this.filesDownloaded + ", frequency=" 
                + this.getFrequency() + "]";
    }
}
