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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.util.Date;
import java.util.List;
import static java.util.Locale.filter;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TimelineEvent;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides data source summary information pertaining to Timeline data.
 */
public class TimelineSummary {

    private final SleuthkitCaseProvider provider;

    /**
     * Default constructor.
     */
    public TimelineSummary() {
        this(SleuthkitCaseProvider.DEFAULT);
    }

    /**
     * Construct object with given SleuthkitCaseProvider
     *
     * @param provider SleuthkitCaseProvider provider, cannot be null.
     */
    public TimelineSummary(SleuthkitCaseProvider provider) {
        this.provider = provider;
    }
    
    private Date getDateOrNull(Long secsFromEpoch) {
        if (secsFromEpoch == null || secsFromEpoch == 0) {
            return null;
        }
        
        return new Date(secsFromEpoch * 1000);
    }
    
    
    
    
    private void getData(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        TimelineManager timelineManager = this.provider.get().getTimelineManager();
        
        
        
        List<TimelineEvent> events = timelineManager.getEvents(new Interval(0, System.currentTimeMillis()), new RootTimelineFilter);
    }
    

    
    public static class TimelineSummaryData {
        private final DataSource dataSource;
        private final Date minDate;
        private final Date maxDate;
        private final List<DailyActivityAmount> histogramActivity;
        
        
    }
    
    
    
    public static class DailyActivityAmount {
        private final Date day;
        private final long magnitude;
    }
}
