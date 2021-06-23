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
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.machinesettings.UserMachinePreferences;

/**
 * Settings that apply to all ingest jobs.
 *
 * THIS IS A BETA CLASS AND IS SUBJECT TO CHANGE OR DELETION.
 */
@Beta
public class GlobalIngestSettings {

    private static final Preferences preferences = NbPreferences.forModule(UserMachinePreferences.class);
    public static final int INGEST_PAUSE_VALUE_NOT_SET = 0;
    private static final String PAUSE_DAY_OF_WEEK_KEY = "PauseDayOfWeek";
    private static final String PAUSE_TIME_HOURS_KEY = "PauseTimeHours";
    private static final String PAUSE_TIME_MINUTES_KEY = "PauseTimeMinutes";
    private static final String PAUSE_DURATION_MINUTES_KEY = "PauseTimeDurationMinutes";
    private static final String PAUSE_TIME_START_WINDOW_MINUTES_KEY = "PauseTimeStartWindowMinutes";

    /**
     * Gets the day of the week when ingest should pause.
     *
     * @return The day of the week as in integer in the range 1-7 or
     *         INGEST_PAUSE_VALUE_NOT_SET.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static int getIngestPauseDayOfWeek() {
        return preferences.getInt(PAUSE_DAY_OF_WEEK_KEY, INGEST_PAUSE_VALUE_NOT_SET);
    }

    /**
     * Sets the day of the week when ingest should pause.
     *
     * @param dayOfWeek The day of the week as in integer in the range 1-7 or
     *                  INGEST_PAUSE_VALUE_NOT_SET.
     *
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static void setIngestPauseDayOfWeek(int dayOfWeek) {
        if (dayOfWeek > 0 || dayOfWeek > 7) {
            throw new IllegalArgumentException("dayOfWeek must be 0-7");
        }
    }

    /**
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     *
     * @return
     */
    @Beta
    public static int getPauseTimeHours() {
        return preferences.getInt(PAUSE_TIME_HOURS_KEY, INGEST_PAUSE_VALUE_NOT_SET);
    }

    /**
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     */
    @Beta
    public static void setPauseTimeHours() {

    }

    /**
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     *
     * @return
     */
    @Beta
    public static int getPauseTimeMinutes() {
        return preferences.getInt(PAUSE_TIME_MINUTES_KEY, INGEST_PAUSE_VALUE_NOT_SET);
    }

    /**
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     *
     * @param pauseTimeMinutes
     */
    @Beta
    public static void setPauseTimeMinutes(int pauseTimeMinutes) {

    }

    /**
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     *
     * @return
     */
    @Beta
    public static int getPauseDurationMinutes() {
        return preferences.getInt(PAUSE_DURATION_MINUTES_KEY, INGEST_PAUSE_VALUE_NOT_SET);
    }

    /**
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     *
     * @param pauseTimeMinutes
     */
    @Beta
    public static void setPauseDurationMinutes(int pauseTimeMinutes) {

    }

    /**
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     *
     * @return
     */
    @Beta
    public static int getPauseStartWindowMinutes() {
        return preferences.getInt(PAUSE_TIME_START_WINDOW_MINUTES_KEY, INGEST_PAUSE_VALUE_NOT_SET);
    }

    /**
     * THIS IS A BETA METHOD AND IS SUBJECT TO CHANGE OR DELETION.
     *
     * @param pauseWindowMinutes
     */
    @Beta
    public static void setPauseStartWindowMinutes(int pauseWindowMinutes) {

    }

    /**
     * Private constructor to prevent utilit bclass instantiation.
     */
    @Beta
    private GlobalIngestSettings() {
    }

}
