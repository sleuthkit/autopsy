/*
* Autopsy Forensic Browser
*
* Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.services;

import java.util.Objects;

/**
 * Because the DTO TagName constructor can not be called outside of its class
 * package, UserTagName is used to keep track of user tag names while
 * preserving properties that will potentially be implemented in the future
 * (tag name description and tag name color).
 */
class UserTagName implements Comparable<UserTagName> {

    private final String displayName;
    private final String description;
    private final String colorName;

    UserTagName(String displayName, String description, String colorName) {
        this.displayName = displayName;
        this.description = description;
        this.colorName = colorName;
    }

    String getDisplayName() {
        return displayName;
    }

    String getDescription() {
        return description;
    }

    String getColorName() {
        return colorName;
    }

    @Override
    public int compareTo(UserTagName other) {
        return this.getDisplayName().toLowerCase().compareTo(other.getDisplayName().toLowerCase());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + Objects.hashCode(this.displayName);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UserTagName)) {
            return false;
        }
        UserTagName thatTagName = (UserTagName) obj;
        return this.getDisplayName().equals(thatTagName.getDisplayName());
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * @return A string representation of the tag name in the format that is
     *         used by the properties file.
     */
    public String toSettingsFormat() {
        return displayName + "," + description + "," + colorName;
    }
}
