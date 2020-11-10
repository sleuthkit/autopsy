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

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultUpdateGovernor;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.timeline.utils.FilterUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TimelineEvent;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineFilter;
import org.sleuthkit.datamodel.TimelineFilter.DataSourcesFilter;
import org.sleuthkit.datamodel.TimelineFilter.EventTypeFilter;
import org.sleuthkit.datamodel.TimelineFilter.HashHitsFilter;
import org.sleuthkit.datamodel.TimelineFilter.HideKnownFilter;
import org.sleuthkit.datamodel.TimelineFilter.RootFilter;
import org.sleuthkit.datamodel.TimelineFilter.TagsFilter;
import org.sleuthkit.datamodel.TimelineFilter.TextFilter;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides data source summary information pertaining to Timeline data.
 */
public class TimelineSummary implements DefaultUpdateGovernor {
    private static final long DAY_SECS = 24 * 60 * 60;
    
        
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS = new HashSet<>(
        Arrays.asList(IngestManager.IngestJobEvent.COMPLETED, IngestManager.IngestJobEvent.CANCELLED));

    
    private static final Set<TimelineEventType> FILE_SYSTEM_EVENTS = 
            new HashSet<>(Arrays.asList(
                    TimelineEventType.FILE_MODIFIED, 
                    TimelineEventType.FILE_ACCESSED, 
                    TimelineEventType.FILE_CREATED, 
                    TimelineEventType.FILE_CHANGED));
    
    
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

    
    @Override
    public boolean isRefreshRequired(ModuleContentEvent evt) {
        return true;
    }

    @Override
    public boolean isRefreshRequired(AbstractFile file) {
        return true;
    }

    @Override
    public boolean isRefreshRequired(IngestManager.IngestJobEvent evt) {
        return (evt != null && INGEST_JOB_EVENTS.contains(evt));
    }

    @Override
    public Set<IngestManager.IngestJobEvent> getIngestJobEventUpdates() {
        return INGEST_JOB_EVENTS;
    }
    


    private static Long getMinOrNull(Long first, Long second) {
        if (first == null) {
            return second;
        } else if (second == null) {
            return first;
        } else {
            return Math.min(first, second);
        }
    }

    private static Long getMaxOrNull(Long first, Long second) {
        if (first == null) {
            return second;
        } else if (second == null) {
            return first;
        } else {
            return Math.max(first, second);
        }
    }

    public TimelineSummaryData getData(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        TimelineManager timelineManager = this.provider.get().getTimelineManager();

        DataSourcesFilter dataSourceFilter = new DataSourcesFilter();
        dataSourceFilter.addSubFilter(new TimelineFilter.DataSourceFilter(dataSource.getName(), dataSource.getId()));

        // TODO check that this isn't filtering more than it should
        RootFilter dataSourceRootFilter = new RootFilter(
                new HideKnownFilter(),
                new TagsFilter(),
                new HashHitsFilter(),
                new TextFilter(),
                new EventTypeFilter(TimelineEventType.ROOT_EVENT_TYPE),
                new DataSourcesFilter(),
                FilterUtils.createDefaultFileTypesFilter(),
                Collections.emptySet());

        // get events for data source
        long curRunTime = System.currentTimeMillis();
        List<TimelineEvent> events = timelineManager.getEvents(new Interval(0, curRunTime), dataSourceRootFilter);

        // get counts of events per day (left is file system events, right is everything else)
        Map<Long, Pair<Integer, Integer>> dateCounts = events.stream().collect(Collectors.toMap(
                (evt) -> evt.getTime() / DAY_SECS,
                (evt) -> FILE_SYSTEM_EVENTS.contains(evt.getEventType()) ? Pair.of(1, 0) : Pair.of(0, 1),
                (count1, count2) -> Pair.of(count1.getLeft() + count2.getLeft(), count1.getRight() + count2.getRight())));

        // get minimum and maximum usage date
        Pair<Long, Long> minMax = dateCounts.keySet().stream().reduce(
                Pair.of((Long) null, (Long) null),
                (curMinMax, thisDay) -> Pair.of(getMinOrNull(curMinMax.getLeft(), thisDay), getMaxOrNull(curMinMax.getRight(), thisDay)),
                (minMax1, minMax2) -> Pair.of(getMinOrNull(minMax1.getLeft(), minMax2.getLeft()), getMaxOrNull(minMax1.getRight(), minMax2.getRight())));

        Long minDay = minMax.getLeft();
        Long maxDay = minMax.getRight();

        // if no min date or max date, no usage; return null.
        if (minDay == null || maxDay == null) {
            return null;
        }

        Date minDate = new Date(minDay * 1000);
        Date maxDate = new Date(maxDay * 1000);

        List<DailyActivityAmount> mostRecentActivityAmt = dateCounts.entrySet().stream()
                // reverse sort to get latest activity
                .sorted((e1, e2) -> -Long.compare(e1.getKey(), e2.getKey()))
                // get last 30 days
                .limit(30)
                // convert to object to return
                .map(entry -> new DailyActivityAmount(new Date(entry.getKey() * 1000), entry.getValue().getLeft(), entry.getValue().getRight()))
                // create list
                .collect(Collectors.toList());

        // get in ascending order
        Collections.reverse(mostRecentActivityAmt);

        return new TimelineSummaryData(minDate, maxDate, mostRecentActivityAmt);
    }

    public static class TimelineSummaryData {

        private final Date minDate;
        private final Date maxDate;
        private final List<DailyActivityAmount> histogramActivity;

        TimelineSummaryData(Date minDate, Date maxDate, List<DailyActivityAmount> recentDaysActivity) {
            this.minDate = minDate;
            this.maxDate = maxDate;
            this.histogramActivity = (recentDaysActivity == null) ? Collections.emptyList() : Collections.unmodifiableList(recentDaysActivity);
        }

        public Date getMinDate() {
            return minDate;
        }

        public Date getMaxDate() {
            return maxDate;
        }

        public List<DailyActivityAmount> getMostRecentDaysActivity() {
            return histogramActivity;
        }
    }

    public static class DailyActivityAmount {

        private final Date day;
        private final int fileActivityCount;
        private final int artifactActivityCount;

        public DailyActivityAmount(Date day, int fileActivityCount, int artifactActivityCount) {
            this.day = day;
            this.fileActivityCount = fileActivityCount;
            this.artifactActivityCount = artifactActivityCount;
        }

        public Date getDay() {
            return day;
        }

        public int getFileActivityCount() {
            return fileActivityCount;
        }

        public int getArtifactActivityCount() {
            return artifactActivityCount;
        }
        
        
    }
}
