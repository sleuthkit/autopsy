/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import com.google.common.annotations.Beta;
import java.time.DayOfWeek;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * Settings that allow ingest jobs in progress to be paused weekly at a
 * specified time for a specified duration. Because ingest job execution is a
 * variable time activity, it is also possible to specify a window after the
 * specified pause time in which the pause should still occur.
 *
 * THIS IS A BETA CLASS AND IS SUBJECT TO CHANGE OR DELETION.
 */
@Beta
public class ScheduledIngestPauseSettings {

    /*
     * These properties are stored in the core.properties file in the user's
     * config\Preferences\org\sleuthkit\autopsy directory.
     */
    private static final Preferences preferences = NbPreferences.forModule(ScheduledIngestPauseSettings.class);
    private static final String PAUSE_ENABLED_KEY = "IngestPauseEnabled";
    private static final boolean DEFAULT_ENABLED_VALUE = false;
    private static final String PAUSE_DAY_OF_WEEK_KEY = "IngestPauseDayOfWeek";
    private static final String PAUSE_TIME_HOUR_KEY = "IngestPauseTimeHour";
    private static final String PAUSE_TIME_MINUTES_KEY = "IngestPauseTimeMinutes";
    private static final String PAUSE_DURATION_MINUTES_KEY = "IngestPauseDurationMinutes";
    private static final int DEFAULT_TIME_VALUE = 0;
    private static final int DEFAULT_PAUSE_DURATION_VALUE = 60;
    
    /**
     * Gets whether or not a scheduled ingest pause is enabled.
     *
     * @return True or false. The default value is false.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    public static boolean getPauseEnabled() {
        return preferences.getBoolean(PAUSE_ENABLED_KEY, DEFAULT_ENABLED_VALUE);
    }

    /**
     * Sets whether or not a scheduled ingest pause is enabled.
     *
     * @param enabled True or false.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    public static void setPauseEnabled(boolean enabled) {
        preferences.putBoolean(PAUSE_ENABLED_KEY, enabled);
    }

    /**
     * Gets the day of the week when ingest should pause.
     *
     * @return The day of the week. The default value is Sunday.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static DayOfWeek getPauseDayOfWeek() {
        int dayOfWeek = preferences.getInt(PAUSE_DAY_OF_WEEK_KEY, DayOfWeek.SUNDAY.getValue());
        return DayOfWeek.of(dayOfWeek);
    }

    /**
     * Sets the day of the week when ingest should pause.
     *
     * @param dayOfWeek The day of the week as an integer in the range 1-7.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static void setPauseDayOfWeek(DayOfWeek dayOfWeek) {
        preferences.putInt(PAUSE_DAY_OF_WEEK_KEY, dayOfWeek.getValue());
    }

    /**
     * Gets the hour of the time of day when ingest should pause.
     *
     * @return The hour as an integer in the range of 0-23. The default value is
     *         zero.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static int getPauseStartTimeHour() {
        return preferences.getInt(PAUSE_TIME_HOUR_KEY, DEFAULT_TIME_VALUE);
    }

    /**
     * Sets the hour of the time of day when ingest should pause.
     *
     * @param hour The hour of the time of day as an integer in the range of
     *             0-23.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static void setPauseStartTimeHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be 0-23");
        }
        preferences.putInt(PAUSE_TIME_HOUR_KEY, hour);
    }

    /**
     * Gets the minutes of the time of day when ingest should pause.
     *
     * @return The minutes of the time of day as an integer in the range of
     *         0-59. The default value is zero.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static int getPauseStartTimeMinute() {
        return preferences.getInt(PAUSE_TIME_MINUTES_KEY, DEFAULT_TIME_VALUE);
    }

    /**
     * Sets the minutes of the time of day when ingest should pause.
     *
     * @param timeInMinutes The minutes of the time of day as an integer in the
     *                      range of 0-59.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static void setPauseStartTimeMinute(int timeInMinutes) {
        if (timeInMinutes < 0 || timeInMinutes > 59) {
            throw new IllegalArgumentException("timeInMinutes must be 0-59");
        }
        preferences.putInt(PAUSE_TIME_MINUTES_KEY, timeInMinutes);
    }

    /**
     * Gets the duration of the ingest pause in minutes.
     *
     * @return The duration in minutes. The default value is 60.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static int getPauseDurationMinutes() {
        return preferences.getInt(PAUSE_DURATION_MINUTES_KEY, DEFAULT_PAUSE_DURATION_VALUE);
    }

    /**
     * Sets the duration of the ingest pause in minutes.
     *
     * @param durationInMinutes The duration in minutes.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static void setPauseDurationMinutes(int durationInMinutes) {
        preferences.putInt(PAUSE_DURATION_MINUTES_KEY, durationInMinutes);
    }

    /**
     * Private constructor to prevent utilit bclass instantiation.
     */
    @Beta
    private ScheduledIngestPauseSettings() {
    }

}
