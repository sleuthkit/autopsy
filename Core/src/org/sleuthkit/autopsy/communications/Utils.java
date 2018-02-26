/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.datamodel.Account;

/**
 * Utility class with helpers for dealing with accounts.
 */
class Utils {

    private Utils() {
    }

    static ZoneId getUserPreferredZoneId() {
        ZoneId zone = UserPreferences.displayTimesInLocalTime() ? ZoneOffset.systemDefault() : ZoneOffset.UTC;
        return zone;
    }

    /**
     * Get the path of the icon for the given Account Type.
     *
     * @return The path of the icon for the given Account Type.
     */
    static final String getIconFilePath(Account.Type type) {
        return Accounts.getIconFilePath(type);
    }

}
