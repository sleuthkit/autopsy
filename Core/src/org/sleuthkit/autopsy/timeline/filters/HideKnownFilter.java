/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.filters;

import org.openide.util.NbBundle;

/**
 * Filter to hide known files
 */
public class HideKnownFilter extends AbstractFilter {

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(this.getClass(), "hideKnownFilter.displayName.text");
    }

    public HideKnownFilter() {
        super();
        getActiveProperty().set(false);
    }

    @Override
    public HideKnownFilter copyOf() {
        HideKnownFilter hideKnownFilter = new HideKnownFilter();
        hideKnownFilter.setActive(isActive());
        hideKnownFilter.setDisabled(isDisabled());
        return hideKnownFilter;
    }

    @Override
    public String getHTMLReportString() {
        return "hide known" + getStringCheckBox();// NON-NLS
    }

    @Override
    public int hashCode() {
        int hash = 7;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HideKnownFilter other = (HideKnownFilter) obj;

        return isActive() == other.isActive();

    }

}
