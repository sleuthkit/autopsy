/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.filtering.datamodel;

/**
 * A FilterState implementation for DescriptionFilters.
 */
public class DescriptionFilterState extends AbstractFilterState<DescriptionFilter> {

    public DescriptionFilterState(DescriptionFilter filter) {
        this(filter, false);
    }

    public DescriptionFilterState(DescriptionFilter filter, boolean selected) {
        super(filter, selected);
    }

    @Override
    public String getDisplayName() {
        return getFilter().getDescription();
    }

    @Override
    public DescriptionFilterState copyOf() {
        DescriptionFilterState copy = new DescriptionFilterState(getFilter());
        copy.setSelected(isSelected());
        copy.setDisabled(isDisabled());
        return copy;
    }

    @Override
    public String toString() {
        return "DescriptionFilterState{"
               + " filter=" + getFilter().toString()
               + ", selected=" + isSelected()
               + ", disabled=" + isDisabled()
               + ", activeProp=" + isActive() + '}'; //NON-NLS
    }
}
