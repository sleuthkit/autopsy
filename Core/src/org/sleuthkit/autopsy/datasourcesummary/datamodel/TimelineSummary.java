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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.joda.time.Interval;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TimelineEvent;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineFilter.RootFilter;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import java.util.function.Supplier;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Provides data source summary information pertaining to Timeline data.
 */
public class TimelineSummary {

    /**
     * A function for obtaining a Timeline RootFilter filtered to the specific
     * data source.
     */
    public interface DataSourceFilterFunction {

        /**
         * Obtains a Timeline RootFilter filtered to the specific data source.
         *
         * @param dataSource The data source.
         * @return The timeline root filter.
         * @throws SleuthkitCaseProviderException
         * @throws TskCoreException
         */
        RootFilter apply(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException;
    }

    private static final long DAY_SECS = 24 * 60 * 60;
    private static final Set<TimelineEventType> FILE_SYSTEM_EVENTS
            = new HashSet<>(Arrays.asList(
                    TimelineEventType.FILE_MODIFIED,
                    TimelineEventType.FILE_ACCESSED,
                    TimelineEventType.FILE_CREATED,
                    TimelineEventType.FILE_CHANGED));

    private final SleuthkitCaseProvider caseProvider;
    private final Supplier<TimeZone> timeZoneProvider;
    private final DataSourceFilterFunction filterFunction;

    /**
     * Default constructor.
     */
    public TimelineSummary() {
        this(SleuthkitCaseProvider.DEFAULT,
                () -> TimeZone.getTimeZone(UserPreferences.getTimeZoneForDisplays()),
                (ds) -> TimelineDataSourceUtils.getInstance().getDataSourceFilter(ds));
    }

    /**
     * Construct object with given SleuthkitCaseProvider
     *
     * @param caseProvider SleuthkitCaseProvider provider; cannot be null.
     * @param timeZoneProvider The timezone provider; cannot be null.
     * @param filterFunction Provides the default root filter function filtered
     * to the data source; cannot be null.
     */
    public TimelineSummary(SleuthkitCaseProvider caseProvider, Supplier<TimeZone> timeZoneProvider, DataSourceFilterFunction filterFunction) {
        this.caseProvider = caseProvider;
        this.timeZoneProvider = timeZoneProvider;
        this.filterFunction = filterFunction;
    }

    /**
     * Retrieves timeline summary data.
     *
     * @param dataSource    The data source for which timeline data will be
     *                      retrieved.
     * @param recentDaysNum The maximum number of most recent days' activity to
     *                      include.
     *
     * @return The retrieved data.
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public TimelineSummaryData getTimelineSummaryData(DataSource dataSource, int recentDaysNum) throws SleuthkitCaseProviderException, TskCoreException {
        TimeZone timeZone = this.timeZoneProvider.get();
        TimelineManager timelineManager = this.caseProvider.get().getTimelineManager();

        // get a mapping of days from epoch to the activity for that day
        Map<Long, DailyActivityAmount> dateCounts = getTimelineEventsByDay(dataSource, timelineManager, timeZone);

        // get minimum and maximum usage date by iterating through 
        Long minDay = null;
        Long maxDay = null;
        for (long daysFromEpoch : dateCounts.keySet()) {
            minDay = (minDay == null) ? daysFromEpoch : Math.min(minDay, daysFromEpoch);
            maxDay = (maxDay == null) ? daysFromEpoch : Math.max(maxDay, daysFromEpoch);
        }

        // if no min date or max date, no usage; return null.
        if (minDay == null || maxDay == null) {
            return null;
        }

        Date minDate = new Date(minDay * 1000 * DAY_SECS);
        Date maxDate = new Date(maxDay * 1000 * DAY_SECS);

        // The minimum recent day will be within recentDaysNum from the maximum day 
        // (+1 since maxDay included) or the minimum day of activity
        long minRecentDay = Math.max(maxDay - recentDaysNum + 1, minDay);

        // get most recent days activity
        List<DailyActivityAmount> mostRecentActivityAmt = getMostRecentActivityAmounts(dateCounts, minRecentDay, maxDay);

        return new TimelineSummaryData(minDate, maxDate, mostRecentActivityAmt, dataSource);
    }

    /**
     * Given activity by day, converts to most recent days' activity handling
     * empty values.
     *
     * @param dateCounts   The day from epoch mapped to activity amounts for
     *                     that day.
     * @param minRecentDay The minimum recent day in days from epoch.
     * @param maxDay       The maximum recent day in days from epoch;
     *
     * @return The most recent daily activity amounts.
     */
    private List<DailyActivityAmount> getMostRecentActivityAmounts(Map<Long, DailyActivityAmount> dateCounts, long minRecentDay, long maxDay) {
        List<DailyActivityAmount> mostRecentActivityAmt = new ArrayList<>();

        for (long curRecentDay = minRecentDay; curRecentDay <= maxDay; curRecentDay++) {
            DailyActivityAmount prevCounts = dateCounts.get(curRecentDay);
            DailyActivityAmount countsHandleNotFound = prevCounts != null
                    ? prevCounts
                    : new DailyActivityAmount(new Date(curRecentDay * DAY_SECS * 1000), 0, 0);

            mostRecentActivityAmt.add(countsHandleNotFound);
        }
        return mostRecentActivityAmt;
    }

    /**
     * Fetches timeline events per day for a particular data source.
     *
     * @param dataSource      The data source.
     * @param timelineManager The timeline manager to use while fetching the
     *                        data.
     * @param timeZone        The time zone to use to determine which day
     *                        activity belongs.
     *
     * @return A Map mapping days from epoch to the activity for that day.
     *
     * @throws TskCoreException
     */
    private Map<Long, DailyActivityAmount> getTimelineEventsByDay(DataSource dataSource, TimelineManager timelineManager, TimeZone timeZone)
            throws TskCoreException, SleuthkitCaseProviderException {
        RootFilter rootFilter = this.filterFunction.apply(dataSource);

        // get events for data source
        long curRunTime = System.currentTimeMillis();
        List<TimelineEvent> events = timelineManager.getEvents(new Interval(1, curRunTime), rootFilter);

        // get counts of events per day (left is file system events, right is everything else)
        Map<Long, DailyActivityAmount> dateCounts = new HashMap<>();
        for (TimelineEvent evt : events) {
            long curSecondsFromEpoch = evt.getTime();
            long curDaysFromEpoch = Instant.ofEpochMilli(curSecondsFromEpoch * 1000)
                    .atZone(timeZone.toZoneId())
                    .toLocalDate()
                    .toEpochDay();

            DailyActivityAmount prevAmt = dateCounts.get(curDaysFromEpoch);
            long prevFileEvtCount = prevAmt == null ? 0 : prevAmt.getFileActivityCount();
            long prevArtifactEvtCount = prevAmt == null ? 0 : prevAmt.getArtifactActivityCount();
            Date thisDay = prevAmt == null ? new Date(curDaysFromEpoch * 1000 * DAY_SECS) : prevAmt.getDay();

            boolean isFileEvt = FILE_SYSTEM_EVENTS.contains(evt.getEventType());
            long curFileEvtCount = prevFileEvtCount + (isFileEvt ? 1 : 0);
            long curArtifactEvtCount = prevArtifactEvtCount + (isFileEvt ? 0 : 1);

            dateCounts.put(curDaysFromEpoch, new DailyActivityAmount(thisDay, curFileEvtCount, curArtifactEvtCount));
        }

        return dateCounts;
    }

    /**
     * All the data to be represented in the timeline summary tab.
     */
    public static class TimelineSummaryData {

        private final Date minDate;
        private final Date maxDate;
        private final List<DailyActivityAmount> histogramActivity;
        private final DataSource dataSource;

        /**
         * Main constructor.
         *
         * @param minDate            Earliest usage date recorded for the data
         *                           source.
         * @param maxDate            Latest usage date recorded for the data
         *                           source.
         * @param recentDaysActivity A list of activity prior to and including
         *                           max date sorted by min to max date.
         * @param dataSource         The data source for which this data
         *                           applies. the latest usage date by day.
         */
        TimelineSummaryData(Date minDate, Date maxDate, List<DailyActivityAmount> recentDaysActivity, DataSource dataSource) {
            this.minDate = minDate;
            this.maxDate = maxDate;
            this.histogramActivity = (recentDaysActivity == null) ? Collections.emptyList() : Collections.unmodifiableList(recentDaysActivity);
            this.dataSource = dataSource;
        }

        /**
         * @return Earliest usage date recorded for the data source.
         */
        public Date getMinDate() {
            return minDate;
        }

        /**
         * @return Latest usage date recorded for the data source.
         */
        public Date getMaxDate() {
            return maxDate;
        }

        /**
         * @return A list of activity prior to and including the latest usage
         *         date by day sorted min to max date.
         */
        public List<DailyActivityAmount> getMostRecentDaysActivity() {
            return histogramActivity;
        }

        /**
         * @return The data source that this data applies to.
         */
        public DataSource getDataSource() {
            return dataSource;
        }
    }

    /**
     * Represents the amount of usage based on timeline events for a day.
     */
    public static class DailyActivityAmount {

        private final Date day;
        private final long fileActivityCount;
        private final long artifactActivityCount;

        /**
         * Main constructor.
         *
         * @param day                   The day for which activity is being
         *                              measured.
         * @param fileActivityCount     The amount of file activity timeline
         *                              events.
         * @param artifactActivityCount The amount of artifact timeline events.
         */
        DailyActivityAmount(Date day, long fileActivityCount, long artifactActivityCount) {
            this.day = day;
            this.fileActivityCount = fileActivityCount;
            this.artifactActivityCount = artifactActivityCount;
        }

        /**
         * @return The day for which activity is being measured.
         */
        public Date getDay() {
            return day;
        }

        /**
         * @return The amount of file activity timeline events.
         */
        public long getFileActivityCount() {
            return fileActivityCount;
        }

        /**
         * @return The amount of artifact timeline events.
         */
        public long getArtifactActivityCount() {
            return artifactActivityCount;
        }
    }

    /**
     * Creates a DateFormat formatter that uses UTC for time zone.
     *
     * @param formatString The date format string.
     * @return The data format.
     */
    public static DateFormat getUtcFormat(String formatString) {
        return new SimpleDateFormat(formatString, Locale.getDefault());
    }
    
    /**
     * Formats a date using a DateFormat. In the event that the date is null,
     * returns a null string.
     *
     * @param date      The date to format.
     * @param formatter The DateFormat to use to format the date.
     *
     * @return The formatted string generated from the formatter or null if the
     *         date is null.
     */
    public static String formatDate(Date date, DateFormat formatter) {
        return date == null ? null : formatter.format(date);
    }       
}
