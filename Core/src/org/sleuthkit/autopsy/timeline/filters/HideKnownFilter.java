/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
    @NbBundle.Messages("hideKnownFilter.displayName.text=Hide Known Files")
    public String getDisplayName() {
        return Bundle.hideKnownFilter_displayName_text();
    }

    public HideKnownFilter() {
        super();
        selectedProperty().set(false);
    }

    @Override
    public HideKnownFilter copyOf() {
        HideKnownFilter hideKnownFilter = new HideKnownFilter();
        hideKnownFilter.setSelected(isSelected());
        hideKnownFilter.setDisabled(isDisabled());
        return hideKnownFilter;
    }

    @Override
    public int hashCode() {
        return 7;
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

        return isSelected() == other.isSelected();
    }
}
