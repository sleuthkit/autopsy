/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * Persists Image Gallery preference to a per user .properties file
 */
public class ImageGalleryPreferences {

    /**
     * NBPreferences object used to persist settings
     */
    private static final Preferences preferences = NbPreferences.forModule(ImageGalleryPreferences.class);

    /**
     * key for the listening enabled for new cases setting
     */
    private static final String ENABLED_BY_DEFAULT = "enabled_by_default"; //NON-NLS
    private static final String GROUP_CATEGORIZATION_WARNING_DISABLED = "group_categorization_warning_disabled"; //NON-NLS
    private static final String MULTI_USER_CASE_INFO_DIALOG_DISABLED = "multi_user_case_info_dialog_disabled"; //NON-NLS

    /**
     * Return setting of whether Image Analyzer should be automatically enabled
     * when a new case is created. Note that the current case may have a
     * different setting.
     *
     * @return true if new cases should have image analyzer enabled.
     */
    public static boolean isEnabledByDefault() {
        return preferences.getBoolean(ENABLED_BY_DEFAULT, true);
    }

    public static void setEnabledByDefault(boolean b) {
        preferences.putBoolean(ENABLED_BY_DEFAULT, b);
    }

    /**
     * Return whether the warning about overwriting categories when acting on an
     * entire group is disabled.
     *
     * @return true if the warning is disabled.
     */
    public static boolean isGroupCategorizationWarningDisabled() {
        final boolean aBoolean = preferences.getBoolean(GROUP_CATEGORIZATION_WARNING_DISABLED, false);
        return aBoolean;
    }

    public static void setGroupCategorizationWarningDisabled(boolean b) {
        preferences.putBoolean(GROUP_CATEGORIZATION_WARNING_DISABLED, b);
    }

    /**
     * Return whether the dialog describing multi user case updating is
     * disabled.
     *
     * @return true if the dialog is disabled.
     */
    public static boolean isMultiUserCaseInfoDialogDisabled() {
        final boolean aBoolean = preferences.getBoolean(MULTI_USER_CASE_INFO_DIALOG_DISABLED, false);
        return aBoolean;
    }

    public static void setMultiUserCaseInfoDialogDisabled(boolean b) {
        preferences.putBoolean(MULTI_USER_CASE_INFO_DIALOG_DISABLED, b);
    }

    static void addChangeListener(PreferenceChangeListener l) {
        preferences.addPreferenceChangeListener(l);
    }

    static void removeChangeListener(PreferenceChangeListener l) {
        preferences.removePreferenceChangeListener(l);
    }

    private ImageGalleryPreferences() {
    }
}
