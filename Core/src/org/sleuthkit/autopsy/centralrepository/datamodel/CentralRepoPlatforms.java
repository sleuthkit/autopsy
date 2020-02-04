/*
 * Central Repository
 *
 * Copyright 2015-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 *
 */
public enum CentralRepoPlatforms {
    DISABLED("Disabled", true),
    SQLITE("SQLite", false),
    POSTGRESQL("PostgreSQL", false);

    private final String platformName;
    private Boolean selected;

    CentralRepoPlatforms(String name, Boolean selected) {
        this.platformName = name;
        this.selected = selected;
        loadSettings();
    }

    /**
     * Load the selectedPlatform boolean from the config file, if it is set.
     */
    private void loadSettings() {
        String selectedPlatformString = ModuleSettings.getConfigSetting("CentralRepository", "db.selectedPlatform"); // NON-NLS

        if (null != selectedPlatformString) {
            selected = this.toString().equalsIgnoreCase(selectedPlatformString);
        } else if (this == DISABLED) {
            selected = true;
        }
    }

    @Override
    public String toString() {
        return platformName;
    }

    private void setSelected(Boolean selected) {
        this.selected = selected;
    }

    public Boolean isSelected() {
        return selected;
    }

    public static CentralRepoPlatforms fromString(String pName) {
        if (null == pName) {
            return DISABLED;
        }

        for (CentralRepoPlatforms p : CentralRepoPlatforms.values()) {
            if (p.toString().equalsIgnoreCase(pName)) {
                return p;
            }
        }
        return DISABLED;
    }

    /**
     * Save the selected platform to the config file.
     */
    public static void saveSelectedPlatform() {
        CentralRepoPlatforms selectedPlatform = DISABLED;
        for (CentralRepoPlatforms p : CentralRepoPlatforms.values()) {
            if (p.isSelected()) {
                selectedPlatform = p;
            }
        }
        ModuleSettings.setConfigSetting("CentralRepository", "db.selectedPlatform", selectedPlatform.name()); // NON-NLS
    }

    /**
     * Set the selected db platform. Other platforms will be set as not
     * selected.
     *
     * @param platformString The name of the selected platform.
     */
    public static void setSelectedPlatform(String platformString) {
        CentralRepoPlatforms pSelected = CentralRepoPlatforms.fromString(platformString);
        for (CentralRepoPlatforms p : CentralRepoPlatforms.values()) {
            p.setSelected(p == pSelected);
        }
    }

    /**
     * Get the selected platform.
     *
     * @return The selected platform, or if not platform is selected, default to
     *         DISABLED.
     */
    public static CentralRepoPlatforms getSelectedPlatform() {
        for (CentralRepoPlatforms p : CentralRepoPlatforms.values()) {
            if (p.isSelected()) {
                return p;
            }
        }
        return DISABLED;
    }
}
